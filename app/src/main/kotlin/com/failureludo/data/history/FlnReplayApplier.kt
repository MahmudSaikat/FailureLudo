package com.failureludo.data.history

import com.failureludo.engine.DeterministicTurnInput
import com.failureludo.engine.GameEngine
import com.failureludo.engine.GameState

sealed interface FlnReplayResult {
    data class Success(val finalState: GameState) : FlnReplayResult

    data class Failure(
        val ply: Int,
        val reason: String
    ) : FlnReplayResult
}

object FlnReplayApplier {

    fun replayMoves(initialState: GameState, moves: List<FlnMove>): FlnReplayResult {
        var state = initialState
        for (move in moves.sortedBy { it.ply }) {
            state = try {
                if (move.rollOnlyReason != null) {
                    GameEngine.applyDeterministicRollOnly(
                        state = state,
                        actorId = move.actorId,
                        diceValue = move.diceValue
                    )
                } else {
                    val pieceId = move.pieceId ?: throw IllegalArgumentException(
                        "Move entry at ply ${move.ply} is missing pieceId."
                    )
                    GameEngine.applyDeterministicTurn(
                        state,
                        DeterministicTurnInput(
                            actorId = move.actorId,
                            movingPlayerId = move.movingPlayerId,
                            pieceId = pieceId,
                            diceValue = move.diceValue,
                            deferHomeEntry = move.choice == FlnMoveChoice.DEFER_HOME_ENTRY
                        )
                    )
                }
            } catch (error: IllegalArgumentException) {
                return FlnReplayResult.Failure(
                    ply = move.ply,
                    reason = error.message ?: "Invalid deterministic move."
                )
            } catch (error: IllegalStateException) {
                return FlnReplayResult.Failure(
                    ply = move.ply,
                    reason = error.message ?: "Replay state transition failed."
                )
            }
        }

        return FlnReplayResult.Success(finalState = state)
    }
}
