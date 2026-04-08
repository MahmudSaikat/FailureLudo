package com.failureludo.engine

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PolicyLatencyTest {

    @Test
    fun recorderComputesSummaryStatistics() {
        val recorder = DecisionLatencyRecorder()
        recorder.record(1_000_000)  // 1ms
        recorder.record(2_000_000)  // 2ms
        recorder.record(3_000_000)  // 3ms
        recorder.record(4_000_000)  // 4ms

        val summary = recorder.snapshot()

        assertEquals(4, summary.sampleCount)
        assertEquals(2.5, summary.averageMs, 1e-6)
        assertEquals(2.0, summary.p50Ms, 1e-6)
        assertEquals(4.0, summary.p95Ms, 1e-6)
        assertEquals(4.0, summary.p99Ms, 1e-6)
        assertEquals(4.0, summary.maxMs, 1e-6)
    }

    @Test
    fun profilingPolicyRecordsChooseMoveLatencySamples() {
        val recorder = DecisionLatencyRecorder()
        val delegate = object : SelfPlayPolicy {
            override fun chooseDice(state: GameState, random: Random): Int = 6

            override fun chooseMove(stateAfterRoll: GameState, random: Random): SelfPlayMoveDecision {
                // Add deterministic, tiny work to avoid zero-duration timing artifacts.
                var sink = 0
                repeat(1_000) { sink += it }
                val piece = stateAfterRoll.movablePieces.first()
                return SelfPlayMoveDecision(piece = piece, deferHomeEntry = false)
            }
        }

        val policy = ProfilingSelfPlayPolicy(delegate, recorder)
        val state = GameEngine.rollDice(
            GameEngine.newGame(activeColors = listOf(PlayerColor.RED, PlayerColor.BLUE)),
            forcedDiceValue = 6
        )

        repeat(5) {
            policy.chooseMove(state, Random(42 + it))
        }

        val summary = recorder.snapshot()
        assertEquals(5, summary.sampleCount)
        assertTrue(summary.averageMs >= 0.0)
        assertTrue(summary.maxMs >= summary.p95Ms)
    }
}