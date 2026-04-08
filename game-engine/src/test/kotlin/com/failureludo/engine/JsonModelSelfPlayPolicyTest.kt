package com.failureludo.engine

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Test

class JsonModelSelfPlayPolicyTest {

    @Test
    fun choosesPreferredPieceFromModelScores() {
        val stateAfterRoll = GameEngine.rollDice(
            GameEngine.newGame(activeColors = listOf(PlayerColor.RED, PlayerColor.BLUE)),
            forcedDiceValue = 6
        )

        val policy = JsonModelSelfPlayPolicy.fromJson(
            raw = buildPreferenceModelJson(preferredPieceId = 0)
        )

        val decision = policy.chooseMove(stateAfterRoll, Random(7))

        assertEquals(0, decision.piece.id)
        assertEquals(PlayerColor.RED, decision.piece.color)
    }

    @Test
    fun fallsBackWhenModelCandidateDimensionDoesNotMatchRuntimeEncoding() {
        val stateAfterRoll = GameEngine.rollDice(
            GameEngine.newGame(activeColors = listOf(PlayerColor.RED, PlayerColor.BLUE)),
            forcedDiceValue = 6
        )

        val fallback = object : SelfPlayPolicy {
            override fun chooseDice(state: GameState, random: Random): Int = 1

            override fun chooseMove(stateAfterRoll: GameState, random: Random): SelfPlayMoveDecision {
                return SelfPlayMoveDecision(piece = stateAfterRoll.movablePieces.last(), deferHomeEntry = false)
            }
        }

        val mismatchedModel = JsonPolicyModel.fromJson(
            buildPreferenceModelJson(
                preferredPieceId = 0,
                candidateDim = PolicyFeatureEncoder.CANDIDATE_DIM + 1
            )
        )
        val policy = JsonModelSelfPlayPolicy(model = mismatchedModel, fallback = fallback)

        val decision = policy.chooseMove(stateAfterRoll, Random(11))

        assertEquals(stateAfterRoll.movablePieces.last().id, decision.piece.id)
        assertEquals(stateAfterRoll.movablePieces.last().color, decision.piece.color)
    }

    private fun buildPreferenceModelJson(
        preferredPieceId: Int,
        candidateDim: Int = PolicyFeatureEncoder.CANDIDATE_DIM
    ): String {
        val stateDim = PolicyFeatureEncoder.STATE_DIM
        val hiddenDim = 2

        val stateLinear1Weight = Array(hiddenDim) { FloatArray(stateDim) { 0f } }
        val stateLinear2Weight = Array(hiddenDim) { FloatArray(hiddenDim) { 0f } }
        val stateLinear1Bias = FloatArray(hiddenDim) { 0f }
        val stateLinear2Bias = FloatArray(hiddenDim) { 0f }

        val candidateLinear1Weight = Array(hiddenDim) { FloatArray(hiddenDim + candidateDim) { 0f } }
        val candidateLinear1Bias = FloatArray(hiddenDim) { 0f }
        val pieceFeatureOffset = hiddenDim + 4 + preferredPieceId
        if (pieceFeatureOffset < candidateLinear1Weight[0].size) {
            candidateLinear1Weight[0][pieceFeatureOffset] = 3f
        }

        val candidateLinear2Weight = arrayOf(floatArrayOf(1f, 0f))
        val candidateLinear2Bias = floatArrayOf(0f)

        return """
            {
              "format": "failureludo-policy-mlp-v1",
              "state_dim": $stateDim,
              "candidate_dim": $candidateDim,
              "hidden_dim": $hiddenDim,
              "state_encoder": {
                "linear1": {
                  "weight": ${matrixToJson(stateLinear1Weight)},
                  "bias": ${vectorToJson(stateLinear1Bias)}
                },
                "linear2": {
                  "weight": ${matrixToJson(stateLinear2Weight)},
                  "bias": ${vectorToJson(stateLinear2Bias)}
                }
              },
              "candidate_head": {
                "linear1": {
                  "weight": ${matrixToJson(candidateLinear1Weight)},
                  "bias": ${vectorToJson(candidateLinear1Bias)}
                },
                "linear2": {
                  "weight": ${matrixToJson(candidateLinear2Weight)},
                  "bias": ${vectorToJson(candidateLinear2Bias)}
                }
              }
            }
        """.trimIndent()
    }

    private fun matrixToJson(matrix: Array<FloatArray>): String {
        return matrix.joinToString(prefix = "[", postfix = "]") { row ->
            vectorToJson(row)
        }
    }

    private fun vectorToJson(vector: FloatArray): String {
        return vector.joinToString(prefix = "[", postfix = "]") { value ->
            value.toString()
        }
    }
}