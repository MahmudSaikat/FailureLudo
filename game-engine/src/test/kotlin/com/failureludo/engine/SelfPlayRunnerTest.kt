package com.failureludo.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SelfPlayRunnerTest {

    @Test
    fun playEpisodeBuildsConsistentTransitionChain() {
        val config = SelfPlayConfig(
            activeColors = listOf(PlayerColor.RED, PlayerColor.BLUE, PlayerColor.YELLOW, PlayerColor.GREEN),
            mode = GameMode.FREE_FOR_ALL,
            maxPly = 500,
            seed = 11L
        )

        val episode = SelfPlayRunner.playEpisode(
            config = config,
            defaultPolicy = RandomSelfPlayPolicy
        )

        assertTrue(episode.steps.isNotEmpty())

        episode.steps.forEachIndexed { index, step ->
            assertEquals(index + 1, step.ply)
            assertEquals(TurnPhase.WAITING_FOR_ROLL, step.before.turnPhase)
            assertTrue(step.action.diceValue in 1..6)
            if (index > 0) {
                assertEquals(episode.steps[index - 1].after, step.before)
            }
        }

        if (episode.terminatedByPlyLimit) {
            assertEquals(config.maxPly, episode.steps.size)
        } else {
            assertTrue(episode.finalState.isGameOver)
            assertFalse(episode.finalState.winners.isNullOrEmpty())
        }
    }

    @Test
    fun playEpisodesWithSameSeedIsReproducible() {
        val config = SelfPlayConfig(
            activeColors = listOf(PlayerColor.RED, PlayerColor.BLUE),
            maxPly = 200,
            seed = 99L
        )

        val firstRun = SelfPlayRunner.playEpisodes(
            episodeCount = 3,
            config = config,
            defaultPolicy = HeuristicSelfPlayPolicy
        )

        val secondRun = SelfPlayRunner.playEpisodes(
            episodeCount = 3,
            config = config,
            defaultPolicy = HeuristicSelfPlayPolicy
        )

        val firstActions = firstRun.map { episode -> episode.steps.map { step -> step.action } }
        val secondActions = secondRun.map { episode -> episode.steps.map { step -> step.action } }

        assertEquals(firstActions, secondActions)
    }

    @Test
    fun trainingSamplesMatchStepCountAndBoundedOutcomes() {
        val episode = SelfPlayRunner.playEpisode(
            config = SelfPlayConfig(
                activeColors = listOf(PlayerColor.RED, PlayerColor.BLUE),
                maxPly = 40,
                seed = 7L
            ),
            defaultPolicy = RandomSelfPlayPolicy
        )

        val samples = SelfPlayRunner.toTrainingSamples(episode)

        assertEquals(episode.steps.size, samples.size)
        assertTrue(samples.all { it.outcomeFromActorPerspective in -1..1 })

        if (episode.terminatedByPlyLimit) {
            assertTrue(samples.all { it.outcomeFromActorPerspective == 0 })
        }
    }
}