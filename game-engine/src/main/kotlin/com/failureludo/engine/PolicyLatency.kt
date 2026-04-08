package com.failureludo.engine

import kotlin.math.ceil

data class DecisionLatencySummary(
    val sampleCount: Int,
    val averageMs: Double,
    val p50Ms: Double,
    val p95Ms: Double,
    val p99Ms: Double,
    val maxMs: Double
)

class DecisionLatencyRecorder {
    private val samplesNanos = mutableListOf<Long>()

    fun record(nanos: Long) {
        if (nanos >= 0L) {
            samplesNanos += nanos
        }
    }

    fun snapshot(): DecisionLatencySummary {
        if (samplesNanos.isEmpty()) {
            return DecisionLatencySummary(
                sampleCount = 0,
                averageMs = 0.0,
                p50Ms = 0.0,
                p95Ms = 0.0,
                p99Ms = 0.0,
                maxMs = 0.0
            )
        }

        val sorted = samplesNanos.sorted()
        val averageMs = sorted.average().toMillis()

        return DecisionLatencySummary(
            sampleCount = sorted.size,
            averageMs = averageMs,
            p50Ms = percentile(sorted, 0.50),
            p95Ms = percentile(sorted, 0.95),
            p99Ms = percentile(sorted, 0.99),
            maxMs = sorted.last().toMillis()
        )
    }

    private fun percentile(sortedNanos: List<Long>, percentile: Double): Double {
        if (sortedNanos.isEmpty()) return 0.0
        val rank = ceil(percentile * sortedNanos.size.toDouble()).toInt().coerceAtLeast(1)
        val index = (rank - 1).coerceIn(0, sortedNanos.lastIndex)
        return sortedNanos[index].toMillis()
    }

    private fun Number.toMillis(): Double = this.toDouble() / 1_000_000.0
}

class ProfilingSelfPlayPolicy(
    private val delegate: SelfPlayPolicy,
    private val recorder: DecisionLatencyRecorder
) : SelfPlayPolicy {

    override fun chooseDice(state: GameState, random: kotlin.random.Random): Int {
        return delegate.chooseDice(state, random)
    }

    override fun chooseMove(stateAfterRoll: GameState, random: kotlin.random.Random): SelfPlayMoveDecision {
        val started = System.nanoTime()
        return try {
            delegate.chooseMove(stateAfterRoll, random)
        } finally {
            recorder.record(System.nanoTime() - started)
        }
    }
}