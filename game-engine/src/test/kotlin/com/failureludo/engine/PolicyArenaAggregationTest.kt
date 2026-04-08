package com.failureludo.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class PolicyArenaAggregationTest {

    @Test
    fun combineSingleSeedReturnsSameSummary() {
        val summary = PolicyArenaSummary(
            episodes = 10,
            challengerWins = 6,
            baselineWins = 3,
            draws = 1,
            terminatedByPlyLimit = 1,
            averagePly = 250.0
        )

        val combined = PolicyArenaAggregation.combine(
            listOf(SeededArenaSummary(seed = 42L, summary = summary))
        )

        assertEquals(summary, combined)
    }

    @Test
    fun combineMultipleSeedsUsesWeightedAveragePlyAndSummedCounts() {
        val first = PolicyArenaSummary(
            episodes = 10,
            challengerWins = 6,
            baselineWins = 4,
            draws = 0,
            terminatedByPlyLimit = 0,
            averagePly = 200.0
        )
        val second = PolicyArenaSummary(
            episodes = 30,
            challengerWins = 12,
            baselineWins = 18,
            draws = 0,
            terminatedByPlyLimit = 0,
            averagePly = 400.0
        )

        val combined = PolicyArenaAggregation.combine(
            listOf(
                SeededArenaSummary(seed = 1L, summary = first),
                SeededArenaSummary(seed = 2L, summary = second)
            )
        )

        assertEquals(40, combined.episodes)
        assertEquals(18, combined.challengerWins)
        assertEquals(22, combined.baselineWins)
        assertEquals(0, combined.draws)
        assertEquals(0, combined.terminatedByPlyLimit)
        assertEquals(350.0, combined.averagePly, 1e-6)
    }
}