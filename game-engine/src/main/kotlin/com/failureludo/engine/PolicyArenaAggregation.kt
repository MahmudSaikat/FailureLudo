package com.failureludo.engine

data class SeededArenaSummary(
    val seed: Long?,
    val summary: PolicyArenaSummary
)

object PolicyArenaAggregation {

    fun combine(seedSummaries: List<SeededArenaSummary>): PolicyArenaSummary {
        require(seedSummaries.isNotEmpty()) {
            "At least one summary is required for aggregation."
        }

        val totalEpisodes = seedSummaries.sumOf { item -> item.summary.episodes }
        require(totalEpisodes > 0) {
            "Aggregated episodes must be > 0."
        }

        val challengerWins = seedSummaries.sumOf { item -> item.summary.challengerWins }
        val baselineWins = seedSummaries.sumOf { item -> item.summary.baselineWins }
        val draws = seedSummaries.sumOf { item -> item.summary.draws }
        val terminatedByPlyLimit = seedSummaries.sumOf { item -> item.summary.terminatedByPlyLimit }

        val weightedPlySum = seedSummaries.sumOf { item ->
            item.summary.averagePly * item.summary.episodes.toDouble()
        }

        return PolicyArenaSummary(
            episodes = totalEpisodes,
            challengerWins = challengerWins,
            baselineWins = baselineWins,
            draws = draws,
            terminatedByPlyLimit = terminatedByPlyLimit,
            averagePly = weightedPlySum / totalEpisodes.toDouble()
        )
    }
}