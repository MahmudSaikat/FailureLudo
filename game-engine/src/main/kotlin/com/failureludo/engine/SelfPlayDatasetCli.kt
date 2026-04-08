package com.failureludo.engine

import java.io.File

fun main(args: Array<String>) {
    val options = parseCliOptions(args)

    val episodeCount = options["episodes"]?.toIntOrNull() ?: 10_000
    val maxPly = options["maxPly"]?.toIntOrNull() ?: 3_000
    val seed = options["seed"]?.toLongOrNull()
    val outputPath = options["output"] ?: "build/self-play/self_play_dataset.jsonl"
    val mode = options["mode"]
        ?.let { raw -> runCatching { GameMode.valueOf(raw.uppercase()) }.getOrNull() }
        ?: GameMode.FREE_FOR_ALL
    val activeColors = parseActiveColors(options["activeColors"], mode)
    val basePolicy = when (options["policy"]?.lowercase()) {
        "random" -> RandomSelfPlayPolicy
        else -> HeuristicSelfPlayPolicy
    }
    val explorationEpsilon = options["explorationEpsilon"]?.toDoubleOrNull() ?: 0.0
    require(explorationEpsilon in 0.0..1.0) {
        "explorationEpsilon must be in [0.0, 1.0]."
    }
    val policy = if (explorationEpsilon > 0.0) {
        EpsilonExplorationSelfPlayPolicy(basePolicy, explorationEpsilon)
    } else {
        basePolicy
    }

    val startedAt = System.currentTimeMillis()
    val outputFile = File(outputPath)
    outputFile.parentFile?.mkdirs()

    var written = 0
    outputFile.bufferedWriter(Charsets.UTF_8).use { writer ->
        repeat(episodeCount) { episodeIndex ->
            val episode = SelfPlayRunner.playEpisode(
                config = SelfPlayConfig(
                    activeColors = activeColors,
                    mode = mode,
                    maxPly = maxPly,
                    seed = seed?.plus(episodeIndex)
                ),
                defaultPolicy = policy
            )

            val lines = SelfPlayDatasetExporter.episodeToJsonlLines(
                episode = episode,
                episodeIndex = episodeIndex,
                config = SelfPlayDatasetExportConfig(includeLegalMoveOptions = true)
            )
            lines.forEach { line ->
                writer.appendLine(line)
                written += 1
            }
        }
    }

    val elapsedMs = System.currentTimeMillis() - startedAt
    println(
        "Generated $episodeCount episodes ($written samples) to ${outputFile.absolutePath} in ${elapsedMs}ms " +
            "(policy=${options["policy"] ?: "heuristic"}, explorationEpsilon=$explorationEpsilon)."
    )
}

private class EpsilonExplorationSelfPlayPolicy(
    private val basePolicy: SelfPlayPolicy,
    private val epsilon: Double
) : SelfPlayPolicy {

    override fun chooseDice(state: GameState, random: kotlin.random.Random): Int {
        if (random.nextDouble() < epsilon) {
            return random.nextInt(from = 1, until = 7)
        }
        return basePolicy.chooseDice(state, random)
    }

    override fun chooseMove(stateAfterRoll: GameState, random: kotlin.random.Random): SelfPlayMoveDecision {
        if (stateAfterRoll.turnPhase != TurnPhase.WAITING_FOR_PIECE_SELECTION || stateAfterRoll.movablePieces.isEmpty()) {
            return basePolicy.chooseMove(stateAfterRoll, random)
        }

        if (random.nextDouble() >= epsilon) {
            return basePolicy.chooseMove(stateAfterRoll, random)
        }

        val piece = stateAfterRoll.movablePieces[random.nextInt(stateAfterRoll.movablePieces.size)]
        val diceValue = stateAfterRoll.lastDice?.value
        val canDefer = if (diceValue == null) {
            false
        } else {
            GameRules.wouldEnterHomePath(
                piece = piece,
                diceValue = diceValue,
                color = piece.color,
                players = stateAfterRoll.players,
                mode = stateAfterRoll.mode
            )
        }
        val deferHomeEntry = canDefer && random.nextBoolean()
        return SelfPlayMoveDecision(piece = piece, deferHomeEntry = deferHomeEntry)
    }
}

private fun parseCliOptions(args: Array<String>): Map<String, String> {
    if (args.isEmpty()) return emptyMap()

    val options = linkedMapOf<String, String>()
    var index = 0
    while (index < args.size) {
        val token = args[index]
        if (!token.startsWith("--")) {
            index += 1
            continue
        }

        val key = token.removePrefix("--")
        val value = args.getOrNull(index + 1)
        if (value != null && !value.startsWith("--")) {
            options[key] = value
            index += 2
        } else {
            options[key] = "true"
            index += 1
        }
    }
    return options
}

private fun parseActiveColors(raw: String?, mode: GameMode): List<PlayerColor> {
    if (mode == GameMode.TEAM) return PlayerColor.entries

    if (raw.isNullOrBlank()) {
        return listOf(PlayerColor.RED, PlayerColor.BLUE, PlayerColor.YELLOW, PlayerColor.GREEN)
    }

    val parsed = raw
        .split(',')
        .mapNotNull { token ->
            runCatching { PlayerColor.valueOf(token.trim().uppercase()) }.getOrNull()
        }
        .distinct()

    return if (parsed.size in 2..4) parsed else listOf(PlayerColor.RED, PlayerColor.BLUE)
}