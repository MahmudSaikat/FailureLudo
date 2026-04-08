package com.failureludo.ai

import android.content.Context
import com.failureludo.engine.GameState

class AssetJsonPolicyMoveScorer(
    private val context: Context,
    private val assetPath: String = "models/policy_baseline_v1.json"
) : OfflineModelMoveScorer {

    @Volatile
    private var loadAttempted = false

    @Volatile
    private var model: JsonPolicyModel? = null

    override fun scoreCandidates(state: GameState, candidates: List<BotMoveDecision>): FloatArray? {
        if (candidates.isEmpty()) return null

        val loadedModel = getModelOrNull() ?: return null
        val stateFeatures = PolicyFeatureEncoder.encodeState(state)
        val candidateFeatures = candidates.map { candidate ->
            PolicyFeatureEncoder.encodeCandidate(state, candidate)
        }

        return runCatching {
            loadedModel.scoreCandidates(stateFeatures, candidateFeatures)
        }.getOrNull()
    }

    private fun getModelOrNull(): JsonPolicyModel? {
        if (loadAttempted) return model

        synchronized(this) {
            if (!loadAttempted) {
                model = loadModelFromAssets()
                loadAttempted = true
            }
        }
        return model
    }

    private fun loadModelFromAssets(): JsonPolicyModel? {
        return runCatching {
            val raw = context.assets
                .open(assetPath)
                .bufferedReader(Charsets.UTF_8)
                .use { reader -> reader.readText() }
            JsonPolicyModel.fromJson(raw)
        }.getOrNull()
    }
}