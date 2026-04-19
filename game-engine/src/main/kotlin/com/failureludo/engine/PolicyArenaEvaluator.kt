package com.failureludo.engine

data class PolicyArenaConfig(
    val episodes: Int = 400,
    val maxPly: Int = 3_000,
    val seed: Long? = null,
    val mode: GameMode = GameMode.FREE_FOR_ALL,
    val activeColors: List<PlayerColor> = listOf(
        PlayerColor.RED,
        PlayerColor.BLUE,
        PlayerColor.YELLOW,
        PlayerColor.GREEN
    )
)

data class PolicyArenaSummary(
    val episodes: Int,
    val challengerWins: Int,
    val baselineWins: Int,
    val draws: Int,
    val terminatedByPlyLimit: Int,
    val averagePly: Double
) {
    val decidedGames: Int
        get() = challengerWins + baselineWins
    val challengerWinRate: Double
        get() = if (episodes == 0) 0.0 else challengerWins.toDouble() / episodes.toDouble()
    val baselineWinRate: Double
        get() = if (episodes == 0) 0.0 else baselineWins.toDouble() / episodes.toDouble()
    val drawRate: Double
        get() = if (episodes == 0) 0.0 else draws.toDouble() / episodes.toDouble()
    val challengerWinRateDecided: Double
        get() = if (decidedGames == 0) 0.0 else challengerWins.toDouble() / decidedGames.toDouble()
}

private enum class EpisodeOutcome {
    CHALLENGER,
    BASELINE,
    DRAW
}

object PolicyArenaEvaluator {

    fun evaluate(
        challengerPolicy: SelfPlayPolicy,
        baselinePolicy: SelfPlayPolicy,
        config: PolicyArenaConfig = PolicyArenaConfig(),
        onProgress: ((completedEpisodes: Int, totalEpisodes: Int) -> Unit)? = null,
        progressStepPercent: Int = 10
    ): PolicyArenaSummary {
        require(config.episodes > 0) { "episodes must be > 0." }
        require(config.maxPly > 0) { "maxPly must be > 0." }

        val activeColors = normalizedActiveColors(config)
        val effectiveProgressStepPercent = progressStepPercent.coerceIn(1, 100)
        var nextProgressPercent = effectiveProgressStepPercent
        var challengerWins = 0
        var baselineWins = 0
        var draws = 0
        var terminatedByPlyLimit = 0
        var totalPly = 0L

        repeat(config.episodes) { episodeIndex ->
            val challengerPlayers = challengerPlayerIdsForEpisode(
                episodeIndex = episodeIndex,
                mode = config.mode,
                activeColors = activeColors
            )

            val policiesByPlayerId = activeColors.associate { color ->
                val id = PlayerId(color.ordinal + 1)
                id to if (id in challengerPlayers) challengerPolicy else baselinePolicy
            }

            val episode = SelfPlayRunner.playEpisode(
                config = SelfPlayConfig(
                    activeColors = activeColors,
                    mode = config.mode,
                    maxPly = config.maxPly,
                    seed = config.seed?.plus(episodeIndex)
                ),
                defaultPolicy = baselinePolicy,
                policiesByPlayerId = policiesByPlayerId
            )

            totalPly += episode.steps.size.toLong()
            if (episode.terminatedByPlyLimit) {
                terminatedByPlyLimit += 1
            }

            when (episodeOutcome(episode, challengerPlayers)) {
                EpisodeOutcome.CHALLENGER -> challengerWins += 1
                EpisodeOutcome.BASELINE -> baselineWins += 1
                EpisodeOutcome.DRAW -> draws += 1
            }

            val completedEpisodes = episodeIndex + 1
            val percent = (completedEpisodes * 100) / config.episodes
            if ((percent >= nextProgressPercent || completedEpisodes == config.episodes) && onProgress != null) {
                onProgress(completedEpisodes, config.episodes)
                while (nextProgressPercent <= percent) {
                    nextProgressPercent += effectiveProgressStepPercent
                }
            }
        }

        return PolicyArenaSummary(
            episodes = config.episodes,
            challengerWins = challengerWins,
            baselineWins = baselineWins,
            draws = draws,
            terminatedByPlyLimit = terminatedByPlyLimit,
            averagePly = totalPly.toDouble() / config.episodes.toDouble()
        )
    }

    private fun normalizedActiveColors(config: PolicyArenaConfig): List<PlayerColor> {
        return when (config.mode) {
            GameMode.FREE_FOR_ALL -> {
                val unique = config.activeColors.distinct()
                require(unique.size in 2..4) {
                    "FREE_FOR_ALL requires 2 to 4 active colors."
                }
                unique
            }

            GameMode.TEAM -> {
                PlayerColor.entries
            }
        }
    }

    internal fun challengerPlayerIdsForEpisode(
        episodeIndex: Int,
        mode: GameMode,
        activeColors: List<PlayerColor>
    ): Set<PlayerId> {
        return when (mode) {
            GameMode.FREE_FOR_ALL -> {
                val n = activeColors.size
                val maxSeats = (n + 1) / 2
                val minSeats = n / 2
                val challengerSeatCount = if (n % 2 == 0) {
                    maxSeats
                } else {
                    if (episodeIndex % 2 == 0) maxSeats else minSeats
                }

                val start = episodeIndex % n
                (0 until challengerSeatCount)
                    .map { offset ->
                        val color = activeColors[(start + offset) % n]
                        PlayerId(color.ordinal + 1)
                    }
                    .toSet()
            }

            GameMode.TEAM -> {
                val challengerTeam = episodeIndex % 2
                activeColors
                    .filter { color -> color.teamIndex == challengerTeam }
                    .map { color -> PlayerId(color.ordinal + 1) }
                    .toSet()
            }
        }
    }

    private fun episodeOutcome(episode: SelfPlayEpisode, challengerPlayers: Set<PlayerId>): EpisodeOutcome {
        if (episode.terminatedByPlyLimit) {
            return EpisodeOutcome.DRAW
        }

        val winners = episode.winners.orEmpty()
        if (winners.isEmpty()) {
            return EpisodeOutcome.DRAW
        }

        return if (winners.any { winner -> winner in challengerPlayers }) {
            EpisodeOutcome.CHALLENGER
        } else {
            EpisodeOutcome.BASELINE
        }
    }
}