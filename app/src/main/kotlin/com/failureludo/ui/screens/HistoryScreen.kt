package com.failureludo.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.failureludo.data.history.FlnGameStatus
import com.failureludo.data.history.GameHistoryImportResult
import com.failureludo.data.history.GameHistoryRecord
import com.failureludo.data.history.GameHistoryRecordKind
import com.failureludo.ui.theme.Background
import com.failureludo.ui.theme.OnPrimary
import com.failureludo.ui.theme.Primary
import com.failureludo.viewmodel.GameViewModel
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: GameViewModel,
    onBack: () -> Unit,
    onOpenGame: () -> Unit
) {
    val records by viewModel.historyRecords.collectAsState()
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var pendingExportGameId by remember { mutableStateOf<String?>(null) }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) {
            errorMessage = "Import canceled."
            return@rememberLauncherForActivityResult
        }

        viewModel.importFlnFromUri(uri) { result ->
            errorMessage = importResultMessage(result)
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        val gameId = pendingExportGameId
        pendingExportGameId = null

        if (uri == null || gameId == null) {
            errorMessage = "Export canceled."
            return@rememberLauncherForActivityResult
        }

        viewModel.exportHistoryRecordToUri(gameId, uri) { exported ->
            errorMessage = if (exported) {
                "FLN exported successfully."
            } else {
                "Could not export this game record."
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.refreshHistoryRecords()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Game History") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = OnPrimary)
                    }
                },
                actions = {
                    TextButton(onClick = { importLauncher.launch(arrayOf("text/plain", "application/octet-stream")) }) {
                        Text("Import", color = OnPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Primary,
                    titleContentColor = OnPrimary
                )
            )
        },
        containerColor = Background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (errorMessage != null) {
                Text(
                    text = errorMessage.orEmpty(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            if (records.isEmpty()) {
                Text(
                    text = "No saved games yet.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                )
                Text(
                    text = "Start and play games to build your replay/archive list.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.60f)
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(records, key = { it.gameId }) { record ->
                        HistoryRecordCard(
                            record = record,
                            onOpen = {
                                val openAction: (String, (Boolean) -> Unit) -> Unit = if (record.status == FlnGameStatus.FINISHED) {
                                    viewModel::openHistoryRecordForReplay
                                } else {
                                    viewModel::openHistoryRecord
                                }

                                openAction(record.gameId) { opened ->
                                    if (opened) {
                                        errorMessage = null
                                        onOpenGame()
                                    } else {
                                        errorMessage = "Could not open this game record."
                                    }
                                }
                            },
                            onReplay = {
                                viewModel.openHistoryRecordForReplay(record.gameId) { opened ->
                                    if (opened) {
                                        errorMessage = null
                                        onOpenGame()
                                    } else {
                                        errorMessage = "Could not open replay for this game."
                                    }
                                }
                            },
                            onExport = {
                                pendingExportGameId = record.gameId
                                exportLauncher.launch("failureludo-${record.gameId.take(8)}.fln")
                            },
                            onDelete = {
                                viewModel.deleteHistoryRecord(record.gameId)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryRecordCard(
    record: GameHistoryRecord,
    onOpen: () -> Unit,
    onReplay: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit
) {
    val statusLabel = when (record.kind) {
        GameHistoryRecordKind.PLAYABLE -> record.status?.name ?: "PLAYABLE"
        GameHistoryRecordKind.UNSUPPORTED -> "UNSUPPORTED"
    }

    val openLabel = if (record.status == FlnGameStatus.ACTIVE) {
        "Resume"
    } else if (record.status == FlnGameStatus.FINISHED) {
        "Analyze"
    } else {
        "Open"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "${record.gameId.take(12)}...",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = "Status: $statusLabel",
                style = MaterialTheme.typography.bodyMedium
            )

            Text(
                text = "Players: ${record.playerNames.joinToString(", ")}",
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = "Updated: ${formatEpoch(record.updatedAtEpochMs)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )

            if (record.kind == GameHistoryRecordKind.PLAYABLE) {
                val moveCount = record.playableDocument?.moves?.size ?: 0
                Text(
                    text = "Moves: $moveCount",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }

            if (record.kind == GameHistoryRecordKind.UNSUPPORTED && record.unsupportedDetectedVersion != null) {
                Text(
                    text = "Detected FLN version: ${record.unsupportedDetectedVersion}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (record.kind == GameHistoryRecordKind.PLAYABLE) {
                    Button(
                        onClick = onOpen,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(openLabel)
                    }

                    OutlinedButton(
                        onClick = onReplay,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Replay")
                    }

                    TextButton(
                        onClick = onDelete,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Delete")
                    }
                } else {
                    OutlinedButton(
                        onClick = {},
                        enabled = false,
                        modifier = Modifier.weight(2f)
                    ) {
                        Text("Unsupported")
                    }

                    OutlinedButton(
                        onClick = onExport,
                        enabled = false,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Export")
                    }

                    TextButton(
                        onClick = onDelete,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Delete")
                    }
                }
            }

            if (record.kind == GameHistoryRecordKind.PLAYABLE) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onExport,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Export")
                    }
                }
            }
        }
    }
}

private fun importResultMessage(result: GameHistoryImportResult): String {
    return when (result) {
        is GameHistoryImportResult.Imported -> {
            "Imported game ${result.record.gameId.take(8)}..."
        }

        is GameHistoryImportResult.Unsupported -> {
            "Imported as unsupported legacy record (${result.record.unsupportedDetectedVersion})."
        }

        is GameHistoryImportResult.Invalid -> {
            "Import failed: ${result.reason}"
        }
    }
}

private fun formatEpoch(epochMs: Long): String {
    val formatter = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
    return formatter.format(Date(epochMs))
}
