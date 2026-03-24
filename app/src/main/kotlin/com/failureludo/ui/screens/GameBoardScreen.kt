package com.failureludo.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.failureludo.engine.*
import com.failureludo.ui.components.DiceView
import com.failureludo.ui.components.LudoBoardCanvas
import com.failureludo.ui.theme.*
import com.failureludo.viewmodel.GameViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameBoardScreen(
    viewModel: GameViewModel,
    onGameOver: () -> Unit,
    onQuit: () -> Unit
) {
    val state by viewModel.gameState.collectAsState()

    // Wire the game-over callback once
    LaunchedEffect(Unit) {
        viewModel.onGameOver = onGameOver
    }

    if (state == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val gameState = state!!

    var showQuitDialog by remember { mutableStateOf(false) }

    if (showQuitDialog) {
        AlertDialog(
            onDismissRequest = { showQuitDialog = false },
            title   = { Text("Quit Game?") },
            text    = { Text("Your current game will be lost.") },
            confirmButton = {
                TextButton(onClick = { showQuitDialog = false; onQuit() }) { Text("Quit") }
            },
            dismissButton = {
                TextButton(onClick = { showQuitDialog = false }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ludo") },
                actions = {
                    IconButton(onClick = { showQuitDialog = true }) {
                        Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Quit", tint = OnPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Primary, titleContentColor = OnPrimary
                )
            )
        },
        containerColor = Background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // Player turn indicator row
            TurnIndicatorRow(gameState)

            // Board - takes as much space as possible (square)
            val movableSet = gameState.movablePieces
                .map { it.color to it.id }.toSet()

            val piecesMap = gameState.players.associate { p -> p.color to p.pieces }

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                val boardSize = minOf(maxWidth, maxHeight)
                LudoBoardCanvas(
                    allPieces        = piecesMap,
                    movablePieceIds  = movableSet,
                    onPieceTapped    = { piece -> viewModel.selectPiece(piece) },
                    modifier         = Modifier.size(boardSize)
                )
            }

            // Bottom panel: status message + dice
            BottomPanel(
                gameState = gameState,
                onRoll    = { viewModel.rollDice() }
            )
        }
    }
}

// ── Sub-components ─────────────────────────────────────────────────────────────

@Composable
private fun TurnIndicatorRow(state: GameState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        state.players.filter { it.isActive }.forEach { player ->
            val isCurrent = player.color == state.currentPlayer.color
            PlayerChip(player = player, isActive = isCurrent)
        }
    }
}

@Composable
private fun PlayerChip(player: com.failureludo.engine.Player, isActive: Boolean) {
    val bg     = if (isActive) playerColor(player.color) else playerColor(player.color).copy(0.25f)
    val border = if (isActive) 2.dp else 0.dp

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            player.name,
            style = MaterialTheme.typography.labelLarge,
            color = if (isActive) Color.White else Color.White.copy(0.7f),
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
        )
        // Finished piece count
        if (player.finishedPieceCount > 0) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(0.3f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    player.finishedPieceCount.toString(),
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontSize = 10.sp
                    ),
                    color = Color.White
                )
            }
        }
    }
}

@Composable
private fun BottomPanel(gameState: GameState, onRoll: () -> Unit) {
    val isHumanTurn = gameState.currentPlayer.type == com.failureludo.engine.PlayerType.HUMAN
    val canRoll     = gameState.turnPhase == TurnPhase.WAITING_FOR_ROLL && isHumanTurn

    val statusText = when (gameState.turnPhase) {
        TurnPhase.WAITING_FOR_ROLL -> "${gameState.currentPlayer.name}'s turn — tap dice to roll"
        TurnPhase.WAITING_FOR_PIECE_SELECTION -> "Tap a glowing piece to move"
        TurnPhase.NO_MOVES_AVAILABLE -> "No moves — skipping turn…"
        TurnPhase.GAME_OVER -> "Game Over!"
    }

    // Last event from log
    val lastEvent = gameState.eventLog.lastOrNull()
    val eventText: String? = when (lastEvent) {
        is GameEvent.PieceCaptured      -> "💥 ${lastEvent.byColor.displayName} captured ${lastEvent.capturedColor.displayName}!"
        is GameEvent.PieceFinished      -> "🏠 ${lastEvent.color.displayName}'s piece finished!"
        is GameEvent.ExtraRollGranted   -> "🎲 Extra roll! (${lastEvent.reason})"
        is GameEvent.ConsecutiveSixesForfeit -> "🚫 Three 6s — turn forfeited!"
        is GameEvent.TurnSkipped        -> "⏭ ${lastEvent.color.displayName} skipped (no moves)"
        else -> null
    }

    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(containerColor = Surface),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            DiceView(
                diceValue  = gameState.lastDice?.value,
                isRollable = canRoll,
                onRoll     = onRoll,
                size       = 72.dp
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    statusText,
                    style     = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color     = OnSurface
                )
                if (eventText != null) {
                    Text(
                        eventText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Primary
                    )
                }
            }
        }
    }
}
