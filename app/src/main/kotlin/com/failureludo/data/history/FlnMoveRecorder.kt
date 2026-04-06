package com.failureludo.data.history

import com.failureludo.engine.GameEvent
import com.failureludo.engine.GameRules
import com.failureludo.engine.GameState
import com.failureludo.engine.PiecePosition

object FlnMoveRecorder {

    /**
     * Derives an FLN move from a before/after transition.
     *
     * Supports both piece-move entries and roll-only entries.
     */
    fun deriveMove(before: GameState, after: GameState, ply: Int): FlnMove? {
        return when {
            after.moveCounter == before.moveCounter + 1L -> derivePieceMove(before, after, ply)
            after.moveCounter == before.moveCounter -> deriveRollOnlyMove(before, after, ply)
            else -> null
        }
    }

    private fun derivePieceMove(before: GameState, after: GameState, ply: Int): FlnMove? {

        val diceValue = before.lastDice?.value ?: return null
        val newEvents = after.eventLog.drop(before.eventLog.size)
        val moveEvent = newEvents.firstOrNull {
            it is GameEvent.PieceMoved || it is GameEvent.PieceEnteredBoard
        } ?: return null

        val (movingPlayerId, pieceId) = when (moveEvent) {
            is GameEvent.PieceMoved -> moveEvent.playerId to moveEvent.pieceId
            is GameEvent.PieceEnteredBoard -> moveEvent.playerId to moveEvent.pieceId
            else -> return null
        }

        val movingPlayerBefore = before.players.firstOrNull { it.id == movingPlayerId } ?: return null
        val movingPieceBefore = movingPlayerBefore.pieces.firstOrNull { it.id == pieceId } ?: return null

        val movingPlayerAfter = after.players.firstOrNull { it.id == movingPlayerId } ?: return null
        val movingPieceAfter = movingPlayerAfter.pieces.firstOrNull { it.id == pieceId } ?: return null

        val canEnterHomePath = GameRules.wouldEnterHomePath(
            piece = movingPieceBefore,
            diceValue = diceValue,
            color = movingPlayerBefore.color,
            players = before.players,
            mode = before.mode
        )

        val choice = if (canEnterHomePath) {
            when (movingPieceAfter.position) {
                is PiecePosition.MainTrack -> FlnMoveChoice.DEFER_HOME_ENTRY
                else -> FlnMoveChoice.ENTER_HOME_PATH
            }
        } else {
            null
        }

        return FlnMove(
            ply = ply,
            actorId = before.currentPlayer.id,
            movingPlayerId = movingPlayerId,
            diceValue = diceValue,
            pieceId = pieceId,
            choice = choice
        )
    }

    private fun deriveRollOnlyMove(before: GameState, after: GameState, ply: Int): FlnMove? {
        val newEvents = after.eventLog.drop(before.eventLog.size)

        val skipEvent = newEvents.firstOrNull { it is GameEvent.TurnSkipped } as? GameEvent.TurnSkipped
        if (skipEvent != null) {
            val diceValue = before.lastDice?.value ?: return null
            return FlnMove(
                ply = ply,
                actorId = skipEvent.playerId,
                movingPlayerId = skipEvent.playerId,
                diceValue = diceValue,
                pieceId = null,
                rollOnlyReason = FlnRollOnlyReason.NO_MOVES
            )
        }

        val forfeitEvent = newEvents.firstOrNull {
            it is GameEvent.ConsecutiveSixesForfeit
        } as? GameEvent.ConsecutiveSixesForfeit

        if (forfeitEvent != null) {
            return FlnMove(
                ply = ply,
                actorId = forfeitEvent.playerId,
                movingPlayerId = forfeitEvent.playerId,
                diceValue = 6,
                pieceId = null,
                rollOnlyReason = FlnRollOnlyReason.THREE_SIX_FORFEIT
            )
        }

        return null
    }
}
