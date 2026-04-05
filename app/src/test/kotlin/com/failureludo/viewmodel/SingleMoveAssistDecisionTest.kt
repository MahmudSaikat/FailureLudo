package com.failureludo.viewmodel

import com.failureludo.data.FeedbackSettings
import com.failureludo.engine.GameEngine
import com.failureludo.engine.GameState
import com.failureludo.engine.PlayerColor
import com.failureludo.engine.PlayerType
import com.failureludo.engine.TurnPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SingleMoveAssistDecisionTest {

    @Test
    fun returnsSinglePieceWhenEnabledAndHumanIsSelecting() {
        val state = stateForAssist(movableCount = 1)

        val candidate = singleMoveAssistPieceCandidate(
            state = state,
            settings = FeedbackSettings(singleMoveAssistEnabled = true)
        )

        assertEquals(state.movablePieces.single(), candidate)
    }

    @Test
    fun returnsNullWhenAssistDisabled() {
        val state = stateForAssist(movableCount = 1)

        val candidate = singleMoveAssistPieceCandidate(
            state = state,
            settings = FeedbackSettings(singleMoveAssistEnabled = false)
        )

        assertNull(candidate)
    }

    @Test
    fun returnsNullOutsidePieceSelectionPhase() {
        val state = stateForAssist(
            turnPhase = TurnPhase.WAITING_FOR_ROLL,
            movableCount = 1
        )

        val candidate = singleMoveAssistPieceCandidate(
            state = state,
            settings = FeedbackSettings(singleMoveAssistEnabled = true)
        )

        assertNull(candidate)
    }

    @Test
    fun returnsNullForBotTurns() {
        val state = stateForAssist(
            currentPlayerType = PlayerType.BOT,
            movableCount = 1
        )

        val candidate = singleMoveAssistPieceCandidate(
            state = state,
            settings = FeedbackSettings(singleMoveAssistEnabled = true)
        )

        assertNull(candidate)
    }

    @Test
    fun returnsNullWhenMultiplePiecesAreMovable() {
        val state = stateForAssist(movableCount = 2)

        val candidate = singleMoveAssistPieceCandidate(
            state = state,
            settings = FeedbackSettings(singleMoveAssistEnabled = true)
        )

        assertNull(candidate)
    }

    private fun stateForAssist(
        turnPhase: TurnPhase = TurnPhase.WAITING_FOR_PIECE_SELECTION,
        currentPlayerType: PlayerType = PlayerType.HUMAN,
        movableCount: Int
    ): GameState {
        val base = GameEngine.newGame(
            activeColors = listOf(PlayerColor.RED, PlayerColor.BLUE),
            playerTypes = mapOf(
                PlayerColor.RED to currentPlayerType,
                PlayerColor.BLUE to PlayerType.HUMAN
            )
        )
        val movable = base.currentPlayer.pieces.take(movableCount.coerceIn(0, 4))
        return base.copy(
            turnPhase = turnPhase,
            movablePieces = movable
        )
    }
}
