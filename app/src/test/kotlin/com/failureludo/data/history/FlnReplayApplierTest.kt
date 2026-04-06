package com.failureludo.data.history

import com.failureludo.engine.GameEngine
import com.failureludo.engine.PlayerColor
import com.failureludo.engine.PlayerId
import com.failureludo.engine.PiecePosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FlnReplayApplierTest {

    @Test
    fun replayMovesReturnsFinalStateForValidMoveStream() {
        val initial = GameEngine.newGame(
            activeColors = listOf(PlayerColor.RED, PlayerColor.BLUE)
        )

        val result = FlnReplayApplier.replayMoves(
            initialState = initial,
            moves = listOf(
                FlnMove(ply = 1, actorId = PlayerId(1), diceValue = 6, pieceId = 0),
                FlnMove(ply = 2, actorId = PlayerId(1), diceValue = 1, pieceId = 0)
            )
        )

        assertTrue(result is FlnReplayResult.Success)
        val finalState = (result as FlnReplayResult.Success).finalState

        val redPiece0 = finalState.players
            .first { it.id == PlayerId(1) }
            .pieces
            .first { it.id == 0 }

        assertEquals(PiecePosition.MainTrack(1), redPiece0.position)
        assertEquals(PlayerId(2), finalState.currentPlayer.id)
        assertEquals(2L, finalState.moveCounter)
    }

    @Test
    fun replayMovesReturnsFailureForActorMismatch() {
        val initial = GameEngine.newGame(
            activeColors = listOf(PlayerColor.RED, PlayerColor.BLUE)
        )

        val result = FlnReplayApplier.replayMoves(
            initialState = initial,
            moves = listOf(
                FlnMove(ply = 1, actorId = PlayerId(2), diceValue = 6, pieceId = 0)
            )
        )

        assertTrue(result is FlnReplayResult.Failure)
        val failure = result as FlnReplayResult.Failure
        assertEquals(1, failure.ply)
        assertTrue(failure.reason.contains("not the current player"))
    }

    @Test
    fun replayMovesSupportsRollOnlySkipEntries() {
        val initial = GameEngine.newGame(
            activeColors = listOf(PlayerColor.RED, PlayerColor.BLUE)
        )

        val result = FlnReplayApplier.replayMoves(
            initialState = initial,
            moves = listOf(
                FlnMove(
                    ply = 1,
                    actorId = PlayerId(1),
                    diceValue = 1,
                    rollOnlyReason = FlnRollOnlyReason.NO_MOVES
                ),
                FlnMove(
                    ply = 2,
                    actorId = PlayerId(2),
                    diceValue = 6,
                    pieceId = 0
                )
            )
        )

        assertTrue(result is FlnReplayResult.Success)
        val finalState = (result as FlnReplayResult.Success).finalState
        assertEquals(PlayerId(2), finalState.currentPlayer.id)
        assertEquals(1L, finalState.moveCounter)
    }
}
