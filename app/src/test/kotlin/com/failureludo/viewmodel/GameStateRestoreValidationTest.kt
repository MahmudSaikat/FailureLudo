package com.failureludo.viewmodel

import com.failureludo.engine.GameEngine
import com.failureludo.engine.Piece
import com.failureludo.engine.PiecePosition
import com.failureludo.engine.PlayerColor
import com.failureludo.engine.PlayerType
import com.failureludo.engine.TurnPhase
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameStateRestoreValidationTest {

    @Test
    fun newGameState_isRestorable() {
        val state = GameEngine.newGame(
            activeColors = listOf(PlayerColor.RED, PlayerColor.BLUE),
            playerTypes = mapOf(
                PlayerColor.RED to PlayerType.HUMAN,
                PlayerColor.BLUE to PlayerType.HUMAN
            )
        )

        assertTrue(isRestorableGameState(state))
    }

    @Test
    fun rejectsSnapshotWhenCurrentPlayerIndexOutOfBounds() {
        val state = GameEngine.newGame(
            activeColors = listOf(PlayerColor.RED, PlayerColor.BLUE)
        ).copy(currentPlayerIndex = 99)

        assertFalse(isRestorableGameState(state))
    }

    @Test
    fun rejectsSnapshotWhenDiceByPlayerKeysAreInvalid() {
        val base = GameEngine.newGame(
            activeColors = listOf(PlayerColor.RED, PlayerColor.BLUE)
        )
        val broken = base.copy(
            diceByPlayer = emptyMap()
        )

        assertFalse(isRestorableGameState(broken))
    }

    @Test
    fun rejectsWaitingForSelectionWithoutLegalMovablePieces() {
        val base = GameEngine.newGame(
            activeColors = listOf(PlayerColor.RED, PlayerColor.BLUE)
        )

        val impossiblePiece = Piece(
            id = 0,
            color = base.currentPlayer.color,
            position = PiecePosition.MainTrack(0),
            lastMovedAt = 1L
        )

        val broken = base.copy(
            turnPhase = TurnPhase.WAITING_FOR_PIECE_SELECTION,
            lastDice = com.failureludo.engine.DiceResult(value = 1),
            movablePieces = listOf(impossiblePiece)
        )

        assertFalse(isRestorableGameState(broken))
    }

    @Test
    fun rejectsNoMovesPhaseWithoutDiceSnapshot() {
        val base = GameEngine.newGame(
            activeColors = listOf(PlayerColor.RED, PlayerColor.BLUE)
        )

        val broken = base.copy(
            turnPhase = TurnPhase.NO_MOVES_AVAILABLE,
            lastDice = null,
            movablePieces = emptyList()
        )

        assertFalse(isRestorableGameState(broken))
    }
}
