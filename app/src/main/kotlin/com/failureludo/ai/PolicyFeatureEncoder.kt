package com.failureludo.ai

import com.failureludo.engine.GameMode
import com.failureludo.engine.GameState
import com.failureludo.engine.Piece
import com.failureludo.engine.PiecePosition
import com.failureludo.engine.PlayerId
import com.failureludo.engine.PlayerType
import kotlin.math.max
import kotlin.math.min

internal object PolicyFeatureEncoder {
    const val STATE_DIM = 156
    const val CANDIDATE_DIM = 9

    private val playerIds = intArrayOf(1, 2, 3, 4)
    private val pieceIds = intArrayOf(0, 1, 2, 3)

    private val turnPhases = arrayOf(
        "WAITING_FOR_ROLL",
        "WAITING_FOR_PIECE_SELECTION",
        "NO_MOVES_AVAILABLE",
        "GAME_OVER"
    )

    private val modes = arrayOf("FREE_FOR_ALL", "TEAM")

    private val positionTypes = arrayOf(
        "HOME_BASE",
        "MAIN_TRACK",
        "HOME_COLUMN",
        "FINISHED"
    )

    fun encodeState(state: GameState): FloatArray {
        val features = ArrayList<Float>(STATE_DIM)

        val moveCounter = state.moveCounter.toFloat()
        features += min(moveCounter / 3000f, 1f)

        val currentPlayerId = state.currentPlayer.id.value
        addOneHot(features, currentPlayerId.toString(), playerIds.map { it.toString() }.toTypedArray())
        addOneHot(features, state.turnPhase.name, turnPhases)
        addOneHot(features, state.mode.name, modes)

        val lastDice = state.lastDice
        if (lastDice != null) {
            features += 1f
            features += (lastDice.value.toFloat() / 6f)
            features += (lastDice.rollCount.toFloat() / 3f)
        } else {
            features += 0f
            features += 0f
            features += 0f
        }

        features += if (state.sharedTeamDiceEnabled.contains(0)) 1f else 0f
        features += if (state.sharedTeamDiceEnabled.contains(1)) 1f else 0f

        val winners = state.winners?.map { it.value }?.toSet().orEmpty()
        playerIds.forEach { pid ->
            features += if (winners.contains(pid)) 1f else 0f
        }

        playerIds.forEach { pid ->
            val diceValue = state.diceByPlayer[PlayerId(pid)]
            features += if (diceValue != null) 1f else 0f
            features += if (diceValue != null) (diceValue.toFloat() / 6f) else 0f
        }

        playerIds.forEach { pid ->
            val entered = state.hasEnteredBoardAtLeastOnce[PlayerId(pid)] == true
            features += if (entered) 1f else 0f
        }

        val playersById = state.players.associateBy { it.id.value }
        val maxMoveCounter = max(1f, moveCounter)

        playerIds.forEach { pid ->
            val player = playersById[pid]
            features += if (player?.isActive == true) 1f else 0f

            val type = player?.type ?: PlayerType.HUMAN
            features += if (type == PlayerType.HUMAN) 1f else 0f
            features += if (type == PlayerType.BOT) 1f else 0f

            val piecesById = player?.pieces?.associateBy { it.id }.orEmpty()
            pieceIds.forEach { pieceId ->
                val piece = piecesById[pieceId]
                val position = piece?.position ?: PiecePosition.HomeBase
                addPositionEncoding(features, position)

                val lastMovedAt = piece?.lastMovedAt?.toFloat() ?: 0f
                features += min(lastMovedAt / maxMoveCounter, 1f)
            }
        }

        return features.toFloatArray().also { encoded ->
            require(encoded.size == STATE_DIM) {
                "Unexpected state feature size ${encoded.size}, expected $STATE_DIM."
            }
        }
    }

    fun encodeCandidate(state: GameState, decision: BotMoveDecision): FloatArray {
        val movingPlayerId = state.players
            .firstOrNull { player -> player.color == decision.pieceColor }
            ?.id
            ?.value
            ?: 1

        val features = ArrayList<Float>(CANDIDATE_DIM)
        playerIds.forEach { pid ->
            features += if (movingPlayerId == pid) 1f else 0f
        }

        pieceIds.forEach { pieceId ->
            features += if (decision.pieceId == pieceId) 1f else 0f
        }

        features += if (decision.deferHomeEntry) 1f else 0f

        return features.toFloatArray().also { encoded ->
            require(encoded.size == CANDIDATE_DIM) {
                "Unexpected candidate feature size ${encoded.size}, expected $CANDIDATE_DIM."
            }
        }
    }

    private fun addOneHot(features: MutableList<Float>, value: String, choices: Array<String>) {
        choices.forEach { option ->
            features += if (value == option) 1f else 0f
        }
    }

    private fun addPositionEncoding(features: MutableList<Float>, position: PiecePosition) {
        val type = when (position) {
            PiecePosition.HomeBase -> "HOME_BASE"
            is PiecePosition.MainTrack -> "MAIN_TRACK"
            is PiecePosition.HomeColumn -> "HOME_COLUMN"
            PiecePosition.Finished -> "FINISHED"
        }

        addOneHot(features, type, positionTypes)
        features += if (position is PiecePosition.MainTrack) {
            position.index.toFloat() / 51f
        } else {
            0f
        }
        features += if (position is PiecePosition.HomeColumn) {
            position.step.toFloat() / 5f
        } else {
            0f
        }
    }
}