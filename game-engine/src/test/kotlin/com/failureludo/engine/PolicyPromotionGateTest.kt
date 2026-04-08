package com.failureludo.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PolicyPromotionGateTest {

    @Test
    fun failsWhenDecidedGamesBelowMinimum() {
        val summary = PolicyArenaSummary(
            episodes = 50,
            challengerWins = 20,
            baselineWins = 20,
            draws = 10,
            terminatedByPlyLimit = 10,
            averagePly = 350.0
        )

        val decision = PolicyPromotionGate.evaluate(
            summary = summary,
            config = PromotionGateConfig(
                promotionThreshold = 0.55,
                minimumDecidedGames = 45
            )
        )

        assertFalse(decision.gatePassed)
        assertTrue(decision.reason.contains("Insufficient decided games"))
    }

    @Test
    fun failsWhenWinRateBelowThreshold() {
        val summary = PolicyArenaSummary(
            episodes = 100,
            challengerWins = 52,
            baselineWins = 48,
            draws = 0,
            terminatedByPlyLimit = 0,
            averagePly = 400.0
        )

        val decision = PolicyPromotionGate.evaluate(
            summary = summary,
            config = PromotionGateConfig(
                promotionThreshold = 0.6,
                minimumDecidedGames = 100
            )
        )

        assertFalse(decision.gatePassed)
        assertTrue(decision.reason.contains("below threshold"))
    }

    @Test
    fun passesWhenBothMinimumAndThresholdAreSatisfied() {
        val summary = PolicyArenaSummary(
            episodes = 100,
            challengerWins = 70,
            baselineWins = 30,
            draws = 0,
            terminatedByPlyLimit = 0,
            averagePly = 410.0
        )

        val decision = PolicyPromotionGate.evaluate(
            summary = summary,
            config = PromotionGateConfig(
                promotionThreshold = 0.6,
                minimumDecidedGames = 100
            )
        )

        assertTrue(decision.gatePassed)
        assertTrue(decision.reason.contains("satisfied"))
    }
}