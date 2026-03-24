package com.failureludo.viewmodel

import com.failureludo.engine.GameMode
import com.failureludo.engine.PlayerColor
import com.failureludo.engine.PlayerType

/**
 * UI state for the Game Setup screen.
 * Captured before starting a game so it can be replayed.
 */
data class SetupState(
    val activeColors: List<PlayerColor> = listOf(
        PlayerColor.RED, PlayerColor.BLUE, PlayerColor.YELLOW, PlayerColor.GREEN
    ),
    val playerTypes: Map<PlayerColor, PlayerType> = PlayerColor.entries.associateWith { PlayerType.HUMAN },
    val playerNames: Map<PlayerColor, String> = PlayerColor.entries.associateWith { it.displayName },
    val mode: GameMode = GameMode.FREE_FOR_ALL
)
