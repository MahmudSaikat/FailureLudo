package com.failureludo.viewmodel

import com.failureludo.engine.GameEngine
import com.failureludo.engine.DiceResult
import com.failureludo.engine.PlayerColor
import com.failureludo.engine.PlayerType
import com.failureludo.engine.TurnPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TurnAutomationDecisionTest {

    @Test
    fun returnsBotRollWhenBotIsWaitingForRoll() {
        val state = GameEngine.newGame(
            activeColors = listOf(PlayerColor.RED, PlayerColor.BLUE),
            playerTypes = mapOf(
                PlayerColor.RED to PlayerType.BOT,
                PlayerColor.BLUE to PlayerType.HUMAN
            )
        ).copy(turnPhase = TurnPhase.WAITING_FOR_ROLL)

        assertEquals(TurnAutomationAction.BOT_ROLL, nextTurnAutomationAction(state))
    }

    @Test
    fun returnsBotSelectWhenBotIsWaitingForPieceSelection() {
        val state = GameEngine.newGame(
            activeColors = listOf(PlayerColor.RED, PlayerColor.BLUE),
            playerTypes = mapOf(
                PlayerColor.RED to PlayerType.BOT,
                PlayerColor.BLUE to PlayerType.HUMAN
            )
        ).copy(turnPhase = TurnPhase.WAITING_FOR_PIECE_SELECTION)

        assertEquals(TurnAutomationAction.BOT_SELECT, nextTurnAutomationAction(state))
    }

    @Test
    fun returnsNoMovesAdvanceForNoMovesPhase() {
        val state = GameEngine.newGame(
            activeColors = listOf(PlayerColor.RED, PlayerColor.BLUE)
        ).copy(turnPhase = TurnPhase.NO_MOVES_AVAILABLE)

        assertEquals(TurnAutomationAction.NO_MOVES_ADVANCE, nextTurnAutomationAction(state))
    }

    @Test
    fun returnsNoneForHumanRollPhase() {
        val state = GameEngine.newGame(
            activeColors = listOf(PlayerColor.RED, PlayerColor.BLUE),
            playerTypes = mapOf(
                PlayerColor.RED to PlayerType.HUMAN,
                PlayerColor.BLUE to PlayerType.BOT
            )
        ).copy(turnPhase = TurnPhase.WAITING_FOR_ROLL)

        assertEquals(TurnAutomationAction.NONE, nextTurnAutomationAction(state))
    }

    @Test
    fun normalizesSelectionPhaseWithoutMovablePiecesToNoMoves() {
        val base = GameEngine.newGame(
            activeColors = listOf(PlayerColor.RED, PlayerColor.BLUE),
            playerTypes = mapOf(PlayerColor.RED to PlayerType.BOT)
        )

        val broken = base.copy(
            turnPhase = TurnPhase.WAITING_FOR_PIECE_SELECTION,
            lastDice = DiceResult(value = 4),
            movablePieces = emptyList()
        )

        val normalized = normalizeAutomationSelectionPhaseState(broken)

        assertEquals(TurnPhase.NO_MOVES_AVAILABLE, normalized.turnPhase)
        assertTrue(normalized.movablePieces.isEmpty())
    }

    @Test
    fun normalizesSelectionPhaseWithoutDiceToWaitingForRoll() {
        val base = GameEngine.newGame(
            activeColors = listOf(PlayerColor.RED, PlayerColor.BLUE),
            playerTypes = mapOf(PlayerColor.RED to PlayerType.BOT)
        )

        val broken = base.copy(
            turnPhase = TurnPhase.WAITING_FOR_PIECE_SELECTION,
            lastDice = null,
            movablePieces = listOf(base.currentPlayer.pieces.first())
        )

        val normalized = normalizeAutomationSelectionPhaseState(broken)

        assertEquals(TurnPhase.WAITING_FOR_ROLL, normalized.turnPhase)
        assertNull(normalized.lastDice)
        assertTrue(normalized.movablePieces.isEmpty())
    }
}
