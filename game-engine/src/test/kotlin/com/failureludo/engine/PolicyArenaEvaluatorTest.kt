package com.failureludo.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PolicyArenaEvaluatorTest {

    @Test
    fun challengerSeatRotationIsBalancedAcrossEpisodesInFreeForAll() {
        assertSeatBalance(
            activeColors = listOf(PlayerColor.RED, PlayerColor.BLUE),
            episodes = 12,
            expectedMinPerColor = 6,
            expectedMaxPerColor = 6
        )

        assertSeatBalance(
            activeColors = listOf(PlayerColor.RED, PlayerColor.BLUE, PlayerColor.YELLOW),
            episodes = 18,
            expectedMinPerColor = 9,
            expectedMaxPerColor = 9
        )

        assertSeatBalance(
            activeColors = listOf(PlayerColor.RED, PlayerColor.BLUE, PlayerColor.YELLOW, PlayerColor.GREEN),
            episodes = 16,
            expectedMinPerColor = 8,
            expectedMaxPerColor = 8
        )
    }

    @Test
    fun evaluateReturnsConsistentAggregateCountsInFreeForAll() {
        val summary = PolicyArenaEvaluator.evaluate(
            challengerPolicy = HeuristicSelfPlayPolicy,
            baselinePolicy = RandomSelfPlayPolicy,
            config = PolicyArenaConfig(
                episodes = 8,
                maxPly = 120,
                seed = 42L,
                mode = GameMode.FREE_FOR_ALL,
                activeColors = listOf(PlayerColor.RED, PlayerColor.BLUE, PlayerColor.YELLOW)
            )
        )

        assertEquals(8, summary.episodes)
        assertEquals(summary.episodes, summary.challengerWins + summary.baselineWins + summary.draws)
        assertTrue(summary.challengerWinRate in 0.0..1.0)
        assertTrue(summary.baselineWinRate in 0.0..1.0)
        assertTrue(summary.drawRate in 0.0..1.0)
        assertTrue(summary.challengerWinRateDecided in 0.0..1.0)
    }

    @Test
    fun evaluateSupportsTeamMode() {
        val summary = PolicyArenaEvaluator.evaluate(
            challengerPolicy = HeuristicSelfPlayPolicy,
            baselinePolicy = RandomSelfPlayPolicy,
            config = PolicyArenaConfig(
                episodes = 6,
                maxPly = 120,
                seed = 9L,
                mode = GameMode.TEAM,
                activeColors = PlayerColor.entries
            )
        )

        assertEquals(6, summary.episodes)
        assertEquals(summary.episodes, summary.challengerWins + summary.baselineWins + summary.draws)
        assertTrue(summary.averagePly > 0.0)
    }

    private fun assertSeatBalance(
        activeColors: List<PlayerColor>,
        episodes: Int,
        expectedMinPerColor: Int,
        expectedMaxPerColor: Int
    ) {
        val seatCounts = activeColors.associateWith { 0 }.toMutableMap()
        repeat(episodes) { episodeIndex ->
            val challengerPlayers = PolicyArenaEvaluator.challengerPlayerIdsForEpisode(
                episodeIndex = episodeIndex,
                mode = GameMode.FREE_FOR_ALL,
                activeColors = activeColors
            )
            activeColors.forEach { color ->
                val playerId = PlayerId(color.ordinal + 1)
                if (playerId in challengerPlayers) {
                    seatCounts[color] = seatCounts.getValue(color) + 1
                }
            }
        }

        seatCounts.values.forEach { count ->
            assertTrue(count >= expectedMinPerColor)
            assertTrue(count <= expectedMaxPerColor)
        }
    }
}