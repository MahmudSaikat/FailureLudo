package com.failureludo.ai

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.max

internal data class DenseLayer(
    val weight: Array<FloatArray>,
    val bias: FloatArray
) {
    val outDim: Int get() = weight.size
    val inDim: Int get() = if (weight.isEmpty()) 0 else weight[0].size
}

internal class JsonPolicyModel private constructor(
    val stateDim: Int,
    val candidateDim: Int,
    val hiddenDim: Int,
    private val stateLinear1: DenseLayer,
    private val stateLinear2: DenseLayer,
    private val candidateLinear1: DenseLayer,
    private val candidateLinear2: DenseLayer
) {

    init {
        require(stateLinear1.inDim == stateDim)
        require(stateLinear1.outDim == hiddenDim)
        require(stateLinear2.inDim == hiddenDim)
        require(stateLinear2.outDim == hiddenDim)
        require(candidateLinear1.inDim == hiddenDim + candidateDim)
        require(candidateLinear1.outDim == hiddenDim)
        require(candidateLinear2.inDim == hiddenDim)
        require(candidateLinear2.outDim == 1)
    }

    fun scoreCandidates(stateFeatures: FloatArray, candidateFeatures: List<FloatArray>): FloatArray {
        require(stateFeatures.size == stateDim)
        require(candidateFeatures.isNotEmpty())
        require(candidateFeatures.all { it.size == candidateDim })

        val encoded1 = relu(dense(stateLinear1, stateFeatures))
        val encoded2 = relu(dense(stateLinear2, encoded1))

        return FloatArray(candidateFeatures.size) { index ->
            val candidate = candidateFeatures[index]
            val merged = FloatArray(hiddenDim + candidateDim)
            System.arraycopy(encoded2, 0, merged, 0, hiddenDim)
            System.arraycopy(candidate, 0, merged, hiddenDim, candidateDim)

            val hidden = relu(dense(candidateLinear1, merged))
            dense(candidateLinear2, hidden)[0]
        }
    }

    private fun dense(layer: DenseLayer, input: FloatArray): FloatArray {
        val out = FloatArray(layer.outDim)
        for (row in 0 until layer.outDim) {
            var acc = layer.bias[row]
            val weights = layer.weight[row]
            for (col in input.indices) {
                acc += weights[col] * input[col]
            }
            out[row] = acc
        }
        return out
    }

    private fun relu(values: FloatArray): FloatArray {
        return FloatArray(values.size) { index -> max(0f, values[index]) }
    }

    companion object {
        private const val FORMAT_V1 = "failureludo-policy-mlp-v1"

        fun fromJson(raw: String): JsonPolicyModel {
            val root = JSONObject(raw)
            require(root.getString("format") == FORMAT_V1) {
                "Unsupported model format."
            }

            val stateDim = root.getInt("state_dim")
            val candidateDim = root.getInt("candidate_dim")
            val hiddenDim = root.getInt("hidden_dim")

            val stateEncoder = root.getJSONObject("state_encoder")
            val candidateHead = root.getJSONObject("candidate_head")

            val stateLinear1 = parseDenseLayer(stateEncoder.getJSONObject("linear1"))
            val stateLinear2 = parseDenseLayer(stateEncoder.getJSONObject("linear2"))
            val candidateLinear1 = parseDenseLayer(candidateHead.getJSONObject("linear1"))
            val candidateLinear2 = parseDenseLayer(candidateHead.getJSONObject("linear2"))

            return JsonPolicyModel(
                stateDim = stateDim,
                candidateDim = candidateDim,
                hiddenDim = hiddenDim,
                stateLinear1 = stateLinear1,
                stateLinear2 = stateLinear2,
                candidateLinear1 = candidateLinear1,
                candidateLinear2 = candidateLinear2
            )
        }

        private fun parseDenseLayer(json: JSONObject): DenseLayer {
            val weight = json.getJSONArray("weight").toMatrix()
            val bias = json.getJSONArray("bias").toFloatArray()
            require(weight.size == bias.size) {
                "Dense layer weight/bias shape mismatch."
            }
            return DenseLayer(weight = weight, bias = bias)
        }

        private fun JSONArray.toMatrix(): Array<FloatArray> {
            val rows = Array(length()) { rowIndex ->
                getJSONArray(rowIndex).toFloatArray()
            }
            if (rows.isNotEmpty()) {
                val cols = rows.first().size
                require(rows.all { it.size == cols }) {
                    "Matrix rows must have equal width."
                }
            }
            return rows
        }

        private fun JSONArray.toFloatArray(): FloatArray {
            return FloatArray(length()) { index -> getDouble(index).toFloat() }
        }
    }
}