package com.failureludo.ai

import org.junit.Assert.assertTrue
import org.junit.Test

class JsonPolicyModelTest {

    @Test
    fun parsesAndScoresCandidates() {
        val json = """
            {
              "format": "failureludo-policy-mlp-v1",
              "state_dim": 2,
              "candidate_dim": 2,
              "hidden_dim": 2,
              "state_encoder": {
                "linear1": {
                  "weight": [[1.0, 0.0], [0.0, 1.0]],
                  "bias": [0.0, 0.0]
                },
                "linear2": {
                  "weight": [[1.0, 0.0], [0.0, 1.0]],
                  "bias": [0.0, 0.0]
                }
              },
              "candidate_head": {
                "linear1": {
                  "weight": [[0.0, 0.0, 1.0, 0.0], [0.0, 0.0, 0.0, 1.0]],
                  "bias": [0.0, 0.0]
                },
                "linear2": {
                  "weight": [[2.0, 1.0]],
                  "bias": [0.0]
                }
              }
            }
        """.trimIndent()

        val model = JsonPolicyModel.fromJson(json)
        val scores = model.scoreCandidates(
            stateFeatures = floatArrayOf(0.2f, 0.7f),
            candidateFeatures = listOf(
                floatArrayOf(1f, 0f),
                floatArrayOf(0f, 1f)
            )
        )

        assertTrue(scores.size == 2)
        assertTrue(scores[0] > scores[1])
    }
}