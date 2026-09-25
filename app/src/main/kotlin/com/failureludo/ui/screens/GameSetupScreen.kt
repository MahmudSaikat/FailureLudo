package com.failureludo.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.failureludo.engine.GameMode
import com.failureludo.engine.PlayerColor
import com.failureludo.engine.PlayerType
import com.failureludo.ui.theme.*
import com.failureludo.viewmodel.defaultPlayerColors
import com.failureludo.viewmodel.GameViewModel
import com.failureludo.viewmodel.quickGameSetup

private const val MAX_PLAYER_NAME_LENGTH = 18

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun GameSetupScreen(
    viewModel: GameViewModel,
    onStartGame: () -> Unit,
    onBack: () -> Unit
) {
    val setup by viewModel.setupState.collectAsState()
    var customGame by rememberSaveable { mutableStateOf(false) }
    var playerCount by rememberSaveable { mutableIntStateOf(2) }
    var showColors by rememberSaveable { mutableStateOf(false) }
    val colorsToShow = if (setup.mode == GameMode.TEAM) PlayerColor.entries else setup.activeColors

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New game") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        containerColor = Background,
        bottomBar = {
            Surface {
                Button(
                    onClick = {
                        viewModel.updateSetup(if (customGame) setup else quickGameSetup(playerCount))
                        viewModel.startGame()
                        onStartGame()
                    },
                    enabled = !customGame || colorsToShow.size >= 2,
                    modifier = Modifier.navigationBarsPadding().imePadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth().heightIn(min = 52.dp)
                ) { Text("Start game") }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).gardenBackground()
                .verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilterChip(selected = !customGame, onClick = { customGame = false },
                    label = { Text("Quick game") })
                FilterChip(selected = customGame, onClick = { customGame = true },
                    label = { Text("Custom game") })
            }
            if (!customGame) {
                Text("Play together on this phone", style = MaterialTheme.typography.titleLarge)
                Text("How many players?")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    (2..4).forEach { count ->
                        FilterChip(selected = playerCount == count, onClick = { playerCount = count },
                            label = { Text("$count players") })
                    }
                }
                Text(if (playerCount == 2) "Two people, opposite corners." else "$playerCount people, each playing for themselves.")
                Text("Ready to play with the usual colors. For names, teams or computer opponents, choose Custom game.",
                    style = MaterialTheme.typography.bodyMedium)
            } else {
                Text("Make it your game", style = MaterialTheme.typography.titleLarge)
                GameModeSection(selected = setup.mode, onSelect = { mode ->
                    viewModel.updateSetup(setup.copy(mode = mode,
                        activeColors = if (mode == GameMode.TEAM) PlayerColor.entries else setup.activeColors))
                })
                if (setup.mode == GameMode.FREE_FOR_ALL) {
                    PlayerCountSection(setup.activeColors) {
                        viewModel.updateSetup(setup.copy(activeColors = it))
                    }
                } else {
                    Text("Opposite corners are teammates: top left + bottom right, top right + bottom left.")
                }
                Text("Players", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                colorsToShow.forEach { color ->
                    PlayerRow(
                        color = color,
                        name = setup.playerNames[color].orEmpty(),
                        type = setup.playerTypes[color] ?: PlayerType.HUMAN,
                        onTypeChange = { type ->
                            viewModel.updateSetup(setup.copy(playerTypes = setup.playerTypes + (color to type)))
                        },
                        onNameChange = { name ->
                            viewModel.updateSetup(setup.copy(playerNames = setup.playerNames + (color to name)))
                        }
                    )
                }
                TextButton(onClick = { showColors = !showColors }) {
                    Text(if (showColors) "Hide player colors" else "Change player colors")
                }
                if (showColors) PlayerColorSection(
                    seats = colorsToShow, playerNames = setup.playerNames, playerColors = setup.playerColors,
                    onColorChange = { seat, selected ->
                        val updated = setup.playerColors.toMutableMap()
                        updated.entries.firstOrNull { it.key != seat && it.value == selected }?.key?.let {
                            updated[it] = updated[seat] ?: playerColor(seat)
                        }
                        updated[seat] = selected
                        viewModel.updateSetup(setup.copy(playerColors = updated))
                    },
                    onResetDefaults = { viewModel.updateSetup(setup.copy(playerColors = defaultPlayerColors())) }
                )
            }
        }
    }
}

// ── Sub-components ─────────────────────────────────────────────────────────────

@Composable
private fun GameModeSection(selected: GameMode, onSelect: (GameMode) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Mode", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Primary)
        Column {
            GameMode.entries.forEach { mode ->
                val label = when (mode) {
                    GameMode.FREE_FOR_ALL -> "Everyone for themselves"
                    GameMode.TEAM        -> "Team (2 vs 2)"
                }
                FilterChip(
                    selected = selected == mode,
                    onClick  = { onSelect(mode) },
                    label    = { Text(label) },
                    colors   = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Primary,
                        selectedLabelColor     = OnPrimary
                    )
                )
            }
        }
    }
}

@Composable
private fun PlayerCountSection(
    activeColors: List<PlayerColor>,
    onColorsChange: (List<PlayerColor>) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Choose seats (${activeColors.size} players)", style = MaterialTheme.typography.titleMedium)
        listOf(listOf(PlayerColor.RED, PlayerColor.BLUE), listOf(PlayerColor.GREEN, PlayerColor.YELLOW)).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { seat ->
                    FilterChip(
                        modifier = Modifier.weight(1f),
                        selected = seat in activeColors,
                        onClick = {
                            val next = if (seat in activeColors) {
                                if (activeColors.size > 2) activeColors - seat else activeColors
                            } else activeColors + seat
                            onColorsChange(next.sortedBy { it.ordinal })
                        },
                        label = { Text(seatLabel(seat)) }
                    )
                }
            }
        }
        Text("Choose at least 2 seats. Side-by-side play is available here.", style = MaterialTheme.typography.bodySmall)
    }
}

private fun seatLabel(seat: PlayerColor): String = when (seat) {
    PlayerColor.RED -> "Top left"
    PlayerColor.BLUE -> "Top right"
    PlayerColor.YELLOW -> "Bottom right"
    PlayerColor.GREEN -> "Bottom left"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlayerColorSection(
    seats: List<PlayerColor>,
    playerNames: Map<PlayerColor, String>,
    playerColors: Map<PlayerColor, Color>,
    onColorChange: (PlayerColor, Color) -> Unit,
    onResetDefaults: () -> Unit
) {
    val selectableColors = remember {
        listOf(
            Color.hsv(0f, 0.78f, 0.86f),
            Color.hsv(30f, 0.80f, 0.90f),
            Color.hsv(50f, 0.75f, 0.92f),
            Color.hsv(85f, 0.70f, 0.84f),
            Color.hsv(120f, 0.70f, 0.82f),
            Color.hsv(165f, 0.75f, 0.78f),
            Color.hsv(200f, 0.75f, 0.90f),
            Color.hsv(235f, 0.73f, 0.88f),
            Color.hsv(275f, 0.70f, 0.84f),
            Color.hsv(310f, 0.70f, 0.84f),
            Color.hsv(340f, 0.72f, 0.88f),
            Color.hsv(15f, 0.55f, 0.72f)
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Player Colors",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Primary
            )
            TextButton(onClick = onResetDefaults) {
                Text("Reset Defaults")
            }
        }

        seats.forEach { seat ->
            val selected = playerColors[seat] ?: playerColor(seat)
            val displayName = playerNames[seat]?.takeIf { it.isNotBlank() } ?: "Player-${seat.ordinal + 1}"
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(selected)
                            .border(1.dp, Color.Black.copy(alpha = 0.25f), CircleShape)
                    )
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = OnSurface
                    )
                }

                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    selectableColors.forEach { option ->
                        val isSelected = option == selected
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(option)
                                .border(
                                    width = if (isSelected) 3.dp else 1.dp,
                                    color = if (isSelected) Primary else Color.Black.copy(alpha = 0.2f),
                                    shape = CircleShape
                                )
                                .clickable { onColorChange(seat, option) }
                        )
                    }
                }
            }
        }

        Text(
            "Selecting an already-used color swaps it between players.",
            style = MaterialTheme.typography.bodySmall,
            color = OnSurface.copy(alpha = 0.65f)
        )
    }
}

@Composable
private fun PlayerRow(
    color: PlayerColor,
    name: String,
    type: PlayerType,
    onTypeChange: (PlayerType) -> Unit,
    onNameChange: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(14.dp),
        colors   = CardDefaults.cardColors(
            containerColor = playerColor(color).copy(alpha = 0.12f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(seatLabel(color), style = MaterialTheme.typography.titleSmall)
            OutlinedTextField(
                value = name,
                onValueChange = { onNameChange(it.take(MAX_PLAYER_NAME_LENGTH)) },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                label = { Text("Name (optional)") },
                placeholder = { Text("Player-${color.ordinal + 1}") }
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilterChip(selected = type == PlayerType.HUMAN,
                    onClick = { onTypeChange(PlayerType.HUMAN) }, label = { Text("Person") })
                FilterChip(selected = type == PlayerType.BOT,
                    onClick = { onTypeChange(PlayerType.BOT) }, label = { Text("Computer") })
            }
        }
    }
}

// ── Helpers ────────────────────────────────────────────────────────────────────

fun playerColor(color: PlayerColor, palette: Map<PlayerColor, Color>? = null): Color {
    val custom = palette?.get(color)
    if (custom != null) return custom

    return when (color) {
        PlayerColor.RED    -> LudoRed
        PlayerColor.BLUE   -> LudoBlue
        PlayerColor.YELLOW -> LudoYellow
        PlayerColor.GREEN  -> LudoGreen
    }
}

fun playerColorLight(color: PlayerColor, palette: Map<PlayerColor, Color>? = null): Color {
    if (palette == null) {
        return when (color) {
            PlayerColor.RED    -> LudoRedLight
            PlayerColor.BLUE   -> LudoBlueLight
            PlayerColor.YELLOW -> LudoYellowLight
            PlayerColor.GREEN  -> LudoGreenLight
        }
    }

    return lerp(playerColor(color, palette), Color.White, 0.72f)
}
