package com.failureludo.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SmartToy
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.failureludo.ui.tabletop.drawIdentity
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
            if (!customGame) {
                Text("Players", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    (2..4).forEach { count ->
                        FilterChip(selected = playerCount == count, onClick = { playerCount = count },
                            label = { Text("$count players") })
                    }
                }
                SeatPreview(quickGameSetup(playerCount).activeColors, defaultPlayerColors(), false)
                OutlinedButton(onClick = {
                    viewModel.updateSetup(setup.copy(mode = GameMode.FREE_FOR_ALL,
                        activeColors = quickGameSetup(playerCount).activeColors))
                    customGame = true
                }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text("More options")
                }
            } else {
                TextButton(onClick = { customGame = false }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Quick setup")
                }
                GameModeSection(selected = setup.mode, onSelect = { mode ->
                    viewModel.updateSetup(setup.copy(mode = mode))
                })
                if (setup.mode == GameMode.FREE_FOR_ALL) {
                    PlayerCountSection(setup.activeColors, setup.playerColors) {
                        viewModel.updateSetup(setup.copy(activeColors = it))
                    }
                } else {
                    SeatPreview(colorsToShow, setup.playerColors, true)
                }
                Text("Players", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                colorsToShow.forEach { color ->
                    PlayerRow(
                        color = color,
                        tint = playerColor(color, setup.playerColors),
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
                    Text(if (showColors) "Hide colors" else "Colors")
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

@Composable
private fun PlayerMarker(seat: PlayerColor, tint: Color) {
    Canvas(Modifier.size(24.dp)) { drawIdentity(center, size.minDimension * .38f, tint, seat.ordinal) }
}

@Composable
private fun SeatPreview(seats: List<PlayerColor>, palette: Map<PlayerColor, Color>, teams: Boolean) {
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Surface)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(listOf(PlayerColor.RED, PlayerColor.BLUE), listOf(PlayerColor.GREEN, PlayerColor.YELLOW)).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { seat ->
                        val active = seat in seats
                        val tint = playerColor(seat, palette)
                        Column(Modifier.weight(1f).clip(RoundedCornerShape(16.dp))
                            .background(if (active) tint.copy(alpha = .12f) else Background)
                            .padding(12.dp)
                            .semantics { contentDescription = "${seatLabel(seat)}, ${if (active) "playing" else "empty"}" },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            PlayerMarker(seat, if (active) tint else OnSurface.copy(alpha = .2f))
                            Text(if (!active) "—" else if (teams) {
                                if (seat == PlayerColor.RED || seat == PlayerColor.YELLOW) "Team 1" else "Team 2"
                            } else "${seats.indexOf(seat) + 1}", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
    }
}

// ── Sub-components ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GameModeSection(selected: GameMode, onSelect: (GameMode) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Mode", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Primary)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GameMode.entries.forEach { mode ->
                val label = when (mode) {
                    GameMode.FREE_FOR_ALL -> "Single"
                    GameMode.TEAM        -> "Team"
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
    palette: Map<PlayerColor, Color>,
    onColorsChange: (List<PlayerColor>) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Seats (${activeColors.size})", style = MaterialTheme.typography.titleMedium)
        listOf(listOf(PlayerColor.RED, PlayerColor.BLUE), listOf(PlayerColor.GREEN, PlayerColor.YELLOW)).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { seat ->
                    FilterChip(
                        modifier = Modifier.weight(1f),
                        leadingIcon = { PlayerMarker(seat, playerColor(seat, palette)) },
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
        Text("Pick 2–4 seats", style = MaterialTheme.typography.bodySmall)
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
            "Red" to LudoRed, "Blue" to LudoBlue,
            "Yellow" to LudoYellow, "Green" to LudoGreen,
            "Scarlet" to Color.hsv(0f, .78f, .86f),
            "Orange" to Color.hsv(30f, .80f, .90f),
            "Gold" to Color.hsv(50f, .75f, .92f),
            "Lime" to Color.hsv(85f, .70f, .84f),
            "Bright green" to Color.hsv(120f, .70f, .82f),
            "Teal" to Color.hsv(165f, .75f, .78f),
            "Sky blue" to Color.hsv(200f, .75f, .90f),
            "Indigo" to Color.hsv(235f, .73f, .88f),
            "Purple" to Color.hsv(275f, .70f, .84f),
            "Pink" to Color.hsv(310f, .70f, .84f),
            "Rose" to Color.hsv(340f, .72f, .88f),
            "Brown" to Color.hsv(15f, .55f, .72f)
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Colors",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Primary
            )
            TextButton(onClick = onResetDefaults) {
                Text("Reset")
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
                    selectableColors.forEach { (label, option) ->
                        val isSelected = option == selected
                        Box(
                            modifier = Modifier.size(48.dp)
                                .semantics { contentDescription = "$displayName, $label" }
                                .selectable(selected = isSelected, role = Role.RadioButton,
                                    onClick = { onColorChange(seat, option) }),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(Modifier.size(32.dp).clip(CircleShape).background(option)
                                .border(1.dp, OnSurface.copy(alpha = .3f), CircleShape),
                                contentAlignment = Alignment.Center) {
                                if (isSelected) Icon(Icons.Default.Check, contentDescription = null,
                                    tint = OnSurface, modifier = Modifier.size(24.dp)
                                        .background(Color.White, CircleShape).padding(2.dp))
                            }
                        }
                    }
                }
            }
        }

        Text(
            "Picking a used color swaps it.",
            style = MaterialTheme.typography.bodySmall,
            color = OnSurface.copy(alpha = 0.65f)
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlayerRow(
    color: PlayerColor,
    tint: Color,
    name: String,
    type: PlayerType,
    onTypeChange: (PlayerType) -> Unit,
    onNameChange: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(14.dp),
        colors   = CardDefaults.cardColors(
            containerColor = tint.copy(alpha = 0.12f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PlayerMarker(color, tint)
                Text(seatLabel(color), style = MaterialTheme.typography.titleSmall)
            }
            OutlinedTextField(
                value = name,
                onValueChange = { onNameChange(it.take(MAX_PLAYER_NAME_LENGTH)) },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                label = { Text("Name") },
                placeholder = { Text("Player-${color.ordinal + 1}") }
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilterChip(selected = type == PlayerType.HUMAN,
                    leadingIcon = { Icon(Icons.Default.Person, null, Modifier.size(20.dp)) },
                    onClick = { onTypeChange(PlayerType.HUMAN) }, label = { Text("Person") })
                FilterChip(selected = type == PlayerType.BOT,
                    leadingIcon = { Icon(Icons.Default.SmartToy, null, Modifier.size(20.dp)) },
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
