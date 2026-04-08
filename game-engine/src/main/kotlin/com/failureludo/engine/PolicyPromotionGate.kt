package com.failureludo.engine

data class PromotionGateConfig(
    val promotionThreshold: Double,
    val minimumDecidedGames: Int
)

data class PromotionGateDecision(
    val gatePassed: Boolean,
    val reason: String
)

object PolicyPromotionGate {

    fun evaluate(summary: PolicyArenaSummary, config: PromotionGateConfig): PromotionGateDecision {
        require(config.promotionThreshold in 0.0..1.0) {
            "promotionThreshold must be in [0.0, 1.0]."
        }
        require(config.minimumDecidedGames >= 0) {
            "minimumDecidedGames must be >= 0."
        }

        if (summary.decidedGames < config.minimumDecidedGames) {
            return PromotionGateDecision(
                gatePassed = false,
                reason = "Insufficient decided games: ${summary.decidedGames} < ${config.minimumDecidedGames}."
            )
        }

        if (summary.challengerWinRateDecided < config.promotionThreshold) {
            return PromotionGateDecision(
                gatePassed = false,
                reason = "Challenger decided-game win rate ${summary.challengerWinRateDecided} " +
                    "is below threshold ${config.promotionThreshold}."
            )
        }

        return PromotionGateDecision(
            gatePassed = true,
            reason = "Threshold and minimum decided games satisfied."
        )
    }
}