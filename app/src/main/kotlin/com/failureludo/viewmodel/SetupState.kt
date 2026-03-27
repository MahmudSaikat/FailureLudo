package com.failureludo.viewmodel

import com.failureludo.engine.GameMode
import com.failureludo.engine.PlayerColor
import com.failureludo.engine.PlayerType
import androidx.compose.ui.graphics.Color
import com.failureludo.ui.theme.LudoBlue
import com.failureludo.ui.theme.LudoGreen
import com.failureludo.ui.theme.LudoRed
import com.failureludo.ui.theme.LudoYellow

/**
 * UI state for the Game Setup screen.
 * Captured before starting a game so it can be replayed.
 */
data class SetupState(
    val activeColors: List<PlayerColor> = listOf(
        PlayerColor.RED, PlayerColor.BLUE, PlayerColor.YELLOW, PlayerColor.GREEN
    ),
    val playerTypes: Map<PlayerColor, PlayerType> = PlayerColor.entries.associateWith { PlayerType.HUMAN },
    val playerNames: Map<PlayerColor, String> = defaultPlayerNames(),
    val playerColors: Map<PlayerColor, Color> = defaultPlayerColors(),
    val mode: GameMode = GameMode.FREE_FOR_ALL
)

fun defaultPlayerNames(): Map<PlayerColor, String> = mapOf(
    PlayerColor.RED to "Player-1",
    PlayerColor.BLUE to "Player-2",
    PlayerColor.YELLOW to "Player-3",
    PlayerColor.GREEN to "Player-4"
)

fun defaultPlayerColors(): Map<PlayerColor, Color> = mapOf(
    PlayerColor.RED to LudoRed,
    PlayerColor.BLUE to LudoBlue,
    PlayerColor.YELLOW to LudoYellow,
    PlayerColor.GREEN to LudoGreen
)
