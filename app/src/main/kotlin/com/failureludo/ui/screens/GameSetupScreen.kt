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
import com.failureludo.viewmodel.SetupState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameSetupScreen(
    viewModel: GameViewModel,
    onStartGame: () -> Unit,
    onBack: () -> Unit
) {
    val setup by viewModel.setupState.collectAsState()

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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {

            // Game Mode selector
            GameModeSection(
                selected = setup.mode,
                onSelect = { viewModel.updateSetup(setup.copy(mode = it)) }
            )

            // Player count (number of active seats)
            if (setup.mode == GameMode.FREE_FOR_ALL) {
                PlayerCountSection(
                    activeColors = setup.activeColors,
                    onColorsChange = { newColors ->
                        viewModel.updateSetup(setup.copy(activeColors = newColors))
                    }
                )
            }

            PlayerColorSection(
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

            // Per-player configuration
            Text(
                "Players",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Primary
            )

            val colorsToShow = if (setup.mode == GameMode.TEAM)
                PlayerColor.entries
            else
                setup.activeColors

            colorsToShow.forEach { color ->
                val isActive = color in setup.activeColors
                PlayerRow(
                    color      = color,
                    name       = setup.playerNames[color] ?: color.displayName,
                    type       = setup.playerTypes[color] ?: PlayerType.HUMAN,
                    isActive   = isActive,
                    onTypeToggle = {
                        val newTypes = setup.playerTypes.toMutableMap()
                        newTypes[color] = if (newTypes[color] == PlayerType.HUMAN) PlayerType.BOT else PlayerType.HUMAN
                        viewModel.updateSetup(setup.copy(playerTypes = newTypes))
                    },
                    onNameChange = { newName ->
                        val newNames = setup.playerNames.toMutableMap()
                        newNames[color] = newName
                        viewModel.updateSetup(setup.copy(playerNames = newNames))
                    }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    viewModel.startGame()
                    onStartGame()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Primary),
                enabled = setup.activeColors.size >= 2
            ) {
                Text("Start Game", style = MaterialTheme.typography.titleMedium, color = OnPrimary)
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
                        color.displayName.first().toString(),
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

        PlayerColor.entries.forEach { seat ->
            val selected = playerColors[seat] ?: playerColor(seat)
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
                        text = "${seat.displayName} Player",
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
    isActive: Boolean,
    onTypeToggle: () -> Unit,
    onNameChange: (String) -> Unit
) {
    var editing by remember { mutableStateOf(false) }

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
                    color.displayName.first().toString(),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelLarge
                )
            }

            // Name (editable)
            if (editing) {
                OutlinedTextField(
                    value         = name,
                    onValueChange = onNameChange,
                    modifier      = Modifier.weight(1f),
                    singleLine    = true,
                    label         = { Text("Name") }
                )
            } else {
                Text(
                    text     = name,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { editing = true },
                    style    = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
            }

            // Human / Bot toggle
            IconButton(onClick = onTypeToggle) {
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
