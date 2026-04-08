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
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.failureludo.engine.GameMode
import com.failureludo.engine.PlayerColor
import com.failureludo.engine.PlayerType
import com.failureludo.ui.theme.*
import com.failureludo.viewmodel.BotBehaviorMode
import com.failureludo.viewmodel.defaultPlayerColors
import com.failureludo.viewmodel.GameViewModel
import com.failureludo.viewmodel.SetupState

private const val MAX_PLAYER_NAME_LENGTH = 18

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameSetupScreen(
    viewModel: GameViewModel,
    onStartGame: () -> Unit,
    onBack: () -> Unit
) {
    val setup by viewModel.setupState.collectAsState()
    var setupStep by rememberSaveable { mutableIntStateOf(0) }

    val colorsToShow = if (setup.mode == GameMode.TEAM) {
        PlayerColor.entries
    } else {
        setup.activeColors
    }

    val canProceedFromNames = colorsToShow.all { color ->
        !(setup.playerNames[color].isNullOrBlank())
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Game Setup") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Primary, titleContentColor = OnPrimary, navigationIconContentColor = OnPrimary)
            )
        },
        containerColor = Background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            GameModeSection(
                selected = setup.mode,
                onSelect = { selectedMode ->
                    val nextSetup = if (selectedMode == GameMode.TEAM) {
                        setup.copy(mode = selectedMode, activeColors = PlayerColor.entries)
                    } else {
                        setup.copy(mode = selectedMode)
                    }
                    viewModel.updateSetup(nextSetup)
                }
            )

            BotBehaviorSection(
                selected = setup.botBehaviorMode,
                onSelect = { selectedMode ->
                    viewModel.updateSetup(setup.copy(botBehaviorMode = selectedMode))
                }
            )

            if (setup.mode == GameMode.FREE_FOR_ALL) {
                PlayerCountSection(
                    activeColors = setup.activeColors,
                    onColorsChange = { newColors ->
                        viewModel.updateSetup(setup.copy(activeColors = newColors))
                    }
                )
            }

            HorizontalDivider(color = OnSurface.copy(alpha = 0.14f))

            Text(
                text = if (setupStep == 0) {
                    "Step 1 of 2: Set player names"
                } else {
                    "Step 2 of 2: Choose player colors"
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Primary
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (setupStep == 0) {
                    colorsToShow.forEach { color ->
                        PlayerRow(
                            color = color,
                            name = setup.playerNames[color] ?: "Player-${color.ordinal + 1}",
                            type = setup.playerTypes[color] ?: PlayerType.HUMAN,
                            onTypeToggle = {
                                val newTypes = setup.playerTypes.toMutableMap()
                                newTypes[color] = if (newTypes[color] == PlayerType.HUMAN) PlayerType.BOT else PlayerType.HUMAN
                                viewModel.updateSetup(setup.copy(playerTypes = newTypes))
                            },
                            onNameChange = { newName ->
                                val newNames = setup.playerNames.toMutableMap()
                                newNames[color] = newName.take(MAX_PLAYER_NAME_LENGTH)
                                viewModel.updateSetup(setup.copy(playerNames = newNames))
                            }
                        )
                    }
                } else {
                    PlayerColorSection(
                        seats = colorsToShow,
                        playerNames = setup.playerNames,
                        playerColors = setup.playerColors,
                        onColorChange = { playerColor, selectedColor ->
                            val updated = setup.playerColors.toMutableMap()
                            val swappedSeat = updated.entries.firstOrNull {
                                it.key != playerColor && it.value == selectedColor
                            }?.key

                            if (swappedSeat != null) {
                                updated[swappedSeat] = updated[playerColor] ?: selectedColor
                            }

                            updated[playerColor] = selectedColor
                            viewModel.updateSetup(setup.copy(playerColors = updated))
                        },
                        onResetDefaults = {
                            viewModel.updateSetup(setup.copy(playerColors = defaultPlayerColors()))
                        }
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = {
                        if (setupStep == 0) onBack() else setupStep = 0
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(if (setupStep == 0) "Back" else "Previous")
                }

                if (setupStep == 0) {
                    Button(
                        onClick = { setupStep = 1 },
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        enabled = canProceedFromNames && setup.activeColors.size >= 2
                    ) {
                        Text("Next")
                    }
                } else {
                    Button(
                        onClick = {
                            viewModel.startGame()
                            onStartGame()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Primary),
                        enabled = setup.activeColors.size >= 2
                    ) {
                        Text("Start Game", style = MaterialTheme.typography.titleMedium, color = OnPrimary)
                    }
                }
            }
        }
    }
}

// ── Sub-components ─────────────────────────────────────────────────────────────

@Composable
private fun GameModeSection(selected: GameMode, onSelect: (GameMode) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Mode", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Primary)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GameMode.entries.forEach { mode ->
                val label = when (mode) {
                    GameMode.FREE_FOR_ALL -> "Free for All"
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
private fun BotBehaviorSection(
    selected: BotBehaviorMode,
    onSelect: (BotBehaviorMode) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Bot Behavior",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Primary
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FilterChip(
                selected = selected == BotBehaviorMode.AI_UNDER_DEVELOPMENT,
                onClick = { onSelect(BotBehaviorMode.AI_UNDER_DEVELOPMENT) },
                label = { Text("AI (under development)") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Primary,
                    selectedLabelColor = OnPrimary
                )
            )
            FilterChip(
                selected = selected == BotBehaviorMode.HEURISTIC,
                onClick = { onSelect(BotBehaviorMode.HEURISTIC) },
                label = { Text("Heuristic") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Primary,
                    selectedLabelColor = OnPrimary
                )
            )
        }
        Text(
            "Applies to all players set as bot.",
            style = MaterialTheme.typography.bodySmall,
            color = OnSurface.copy(alpha = 0.65f)
        )
    }
}

@Composable
private fun PlayerCountSection(
    activeColors: List<PlayerColor>,
    onColorsChange: (List<PlayerColor>) -> Unit
) {
    val allColors = PlayerColor.entries
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Active Players (${activeColors.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Primary)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            allColors.forEach { color ->
                val isActive = color in activeColors
                val bgColor  = playerColor(color).copy(alpha = if (isActive) 1f else 0.25f)
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(bgColor)
                        .border(
                            width = if (isActive) 3.dp else 1.dp,
                            color = if (isActive) Color.Black.copy(0.4f) else Color.Transparent,
                            shape = CircleShape
                        )
                        .clickable {
                            val newList = activeColors.toMutableList()
                            if (isActive) {
                                if (newList.size > 2) newList.remove(color)
                            } else {
                                newList.add(color)
                            }
                            onColorsChange(newList)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        (color.ordinal + 1).toString(),
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        Text(
            "Tap to toggle. Minimum 2 players.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
    }
}

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

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
    onTypeToggle: () -> Unit,
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Colour circle
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(playerColor(color)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    (color.ordinal + 1).toString(),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelLarge
                )
            }

            OutlinedTextField(
                value = name,
                onValueChange = { onNameChange(it.take(MAX_PLAYER_NAME_LENGTH)) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                label = { Text("Name") },
                placeholder = { Text("Player-${color.ordinal + 1}") }
            )

            // Human / Bot toggle
            val effectiveName = name.ifBlank { "Player-${color.ordinal + 1}" }
            IconButton(
                onClick = onTypeToggle,
                modifier = Modifier.semantics {
                    contentDescription = if (type == PlayerType.HUMAN) {
                        "Switch $effectiveName to bot"
                    } else {
                        "Switch $effectiveName to human"
                    }
                }
            ) {
                Icon(
                    imageVector = if (type == PlayerType.HUMAN) Icons.Default.Person else Icons.Default.SmartToy,
                    contentDescription = type.name,
                    tint = if (type == PlayerType.BOT) Primary else MaterialTheme.colorScheme.onSurface
                )
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
