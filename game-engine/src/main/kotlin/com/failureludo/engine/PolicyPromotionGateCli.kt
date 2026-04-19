package com.failureludo.engine

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.io.path.copyTo

fun main(args: Array<String>) {
    val options = parseCliOptions(args)

    val episodes = options["episodes"]?.toIntOrNull() ?: 400
    val maxPly = options["maxPly"]?.toIntOrNull() ?: 3_000
    val seed = options["seed"]?.toLongOrNull()
    val seedList = parseSeedList(options["seedList"])
    val mode = options["mode"]
        ?.let { raw -> runCatching { GameMode.valueOf(raw.uppercase()) }.getOrNull() }
        ?: GameMode.FREE_FOR_ALL
    val activeColors = parseActiveColors(options["activeColors"], mode)

    val challengerPolicyName = options["challengerPolicy"] ?: "heuristic"
    val baselinePolicyName = options["baselinePolicy"] ?: "random"
    val challengerModelPath = options["challengerModel"]
    val baselineModelPath = options["baselineModel"]

    val promotionThreshold = options["promotionThreshold"]?.toDoubleOrNull() ?: 0.55
    val minimumDecidedGames = options["minimumDecidedGames"]?.toIntOrNull() ?: 100
    val maxP95DecisionMs = options["maxP95DecisionMs"]?.toDoubleOrNull()
    val summaryOutput = options["summaryOutput"] ?: "build/self-play/policy_promotion_gate_summary.json"
    val promoteTo = options["promoteTo"]
    val failOnGate = options["failOnGate"]?.lowercase()?.let { token -> token == "true" } ?: true
    val inlineProgress = options["progressInline"]?.lowercase() != "false"

    val challengerPolicyBase = loadPolicy(
        policyName = challengerPolicyName,
        modelPath = challengerModelPath,
        role = "challenger"
    )
    val challengerLatencyRecorder = DecisionLatencyRecorder()
    val challengerPolicy = ProfilingSelfPlayPolicy(challengerPolicyBase, challengerLatencyRecorder)
    val baselinePolicy = loadPolicy(
        policyName = baselinePolicyName,
        modelPath = baselineModelPath,
        role = "baseline"
    )

    val arenaProgressStepPercent = if (inlineProgress) 1 else 10
    val evaluationSeedCount = if (seedList.isNotEmpty()) seedList.size else 1
    val totalEvaluationEpisodes = episodes * evaluationSeedCount
    var completedEvaluationEpisodes = 0

    fun onArenaProgress(seedLabel: String, seedCompletedEpisodes: Int, seedTotalEpisodes: Int) {
        val absoluteCompleted = completedEvaluationEpisodes + seedCompletedEpisodes
        val percent = if (totalEvaluationEpisodes <= 0) 100 else {
            (absoluteCompleted * 100) / totalEvaluationEpisodes
        }
        val message =
            "Arena progress $percent% " +
                "($absoluteCompleted/$totalEvaluationEpisodes episodes, $seedLabel: " +
                "$seedCompletedEpisodes/$seedTotalEpisodes)"
        if (inlineProgress) {
            print("\r$message")
            System.out.flush()
        } else {
            println(message)
        }
    }

    val seededSummaries = if (seedList.isNotEmpty()) {
        seedList.map { runSeed ->
            println("Arena seed start seed=$runSeed")
            val summary = PolicyArenaEvaluator.evaluate(
                challengerPolicy = challengerPolicy,
                baselinePolicy = baselinePolicy,
                config = PolicyArenaConfig(
                    episodes = episodes,
                    maxPly = maxPly,
                    seed = runSeed,
                    mode = mode,
                    activeColors = activeColors
                ),
                onProgress = { seedCompletedEpisodes, seedTotalEpisodes ->
                    onArenaProgress("seed=$runSeed", seedCompletedEpisodes, seedTotalEpisodes)
                },
                progressStepPercent = arenaProgressStepPercent
            )
            if (inlineProgress) {
                println()
            }
            completedEvaluationEpisodes += episodes
            SeededArenaSummary(seed = runSeed, summary = summary)
        }
    } else {
        println("Arena seed start seed=${seed ?: "none"}")
        val summary = PolicyArenaEvaluator.evaluate(
            challengerPolicy = challengerPolicy,
            baselinePolicy = baselinePolicy,
            config = PolicyArenaConfig(
                episodes = episodes,
                maxPly = maxPly,
                seed = seed,
                mode = mode,
                activeColors = activeColors
            ),
            onProgress = { seedCompletedEpisodes, seedTotalEpisodes ->
                onArenaProgress("seed=${seed ?: "none"}", seedCompletedEpisodes, seedTotalEpisodes)
            },
            progressStepPercent = arenaProgressStepPercent
        )
        if (inlineProgress) {
            println()
        }
        completedEvaluationEpisodes += episodes
        listOf(SeededArenaSummary(seed = seed, summary = summary))
    }

    val summary = PolicyArenaAggregation.combine(seededSummaries)
    val challengerLatencySummary = challengerLatencyRecorder.snapshot()

    val winRateDecision = PolicyPromotionGate.evaluate(
        summary = summary,
        config = PromotionGateConfig(
            promotionThreshold = promotionThreshold,
            minimumDecidedGames = minimumDecidedGames
        )
    )

    val latencyDecision = evaluateLatencyGate(
        latencySummary = challengerLatencySummary,
        maxP95DecisionMs = maxP95DecisionMs
    )

    val gateDecision = mergeGateDecisions(listOf(winRateDecision, latencyDecision))
    val gatePassed = gateDecision.gatePassed

    val promotedPath = maybePromoteModel(
        gatePassed = gatePassed,
        challengerPolicyName = challengerPolicyName,
        challengerModelPath = challengerModelPath,
        promoteTo = promoteTo
    )

    writeSummary(
        outputPath = summaryOutput,
        summary = summary,
        gatePassed = gatePassed,
        promotionThreshold = promotionThreshold,
        minimumDecidedGames = minimumDecidedGames,
        challengerPolicyName = challengerPolicyName,
        baselinePolicyName = baselinePolicyName,
        challengerModelPath = challengerModelPath,
        baselineModelPath = baselineModelPath,
        promotedPath = promotedPath,
        decisionReason = gateDecision.reason,
        seededSummaries = seededSummaries,
        latencySummary = challengerLatencySummary,
        maxP95DecisionMs = maxP95DecisionMs
    )

    println(
        "Gate result: passed=$gatePassed " +
            "challengerWinRateDecided=${summary.challengerWinRateDecided} " +
            "decidedGames=${summary.decidedGames}/${summary.episodes}"
    )

    if (!gatePassed && failOnGate) {
        error(
            "Promotion gate failed: challenger win rate ${summary.challengerWinRateDecided} " +
                "over ${summary.decidedGames} decided games did not meet threshold " +
                "$promotionThreshold with minimum decided games $minimumDecidedGames. " +
                "Reason: ${gateDecision.reason}"
        )
    }
}

private fun loadPolicy(policyName: String, modelPath: String?, role: String): SelfPlayPolicy {
    return when (policyName.lowercase()) {
        "heuristic" -> HeuristicSelfPlayPolicy
        "random" -> RandomSelfPlayPolicy
        "json", "model" -> {
            require(!modelPath.isNullOrBlank()) {
                "--${role}Model is required when --${role}Policy is set to json/model."
            }
            JsonModelSelfPlayPolicy.fromFile(File(modelPath))
        }

        else -> {
            error("Unsupported $role policy '$policyName'. Supported: heuristic, random, json.")
        }
    }
}

private fun maybePromoteModel(
    gatePassed: Boolean,
    challengerPolicyName: String,
    challengerModelPath: String?,
    promoteTo: String?
): String? {
    if (!gatePassed) return null
    if (promoteTo.isNullOrBlank()) return null
    if (challengerPolicyName.lowercase() !in setOf("json", "model")) return null
    if (challengerModelPath.isNullOrBlank()) return null

    val source = File(challengerModelPath)
    require(source.exists()) {
        "Cannot promote model because source does not exist: ${source.absolutePath}"
    }

    val target = File(promoteTo)
    target.parentFile?.mkdirs()
    source.toPath().copyTo(target.toPath(), overwrite = true)
    return target.absolutePath
}

private fun writeSummary(
    outputPath: String,
    summary: PolicyArenaSummary,
    gatePassed: Boolean,
    promotionThreshold: Double,
    minimumDecidedGames: Int,
    challengerPolicyName: String,
    baselinePolicyName: String,
    challengerModelPath: String?,
    baselineModelPath: String?,
    promotedPath: String?,
    decisionReason: String,
    seededSummaries: List<SeededArenaSummary>,
    latencySummary: DecisionLatencySummary,
    maxP95DecisionMs: Double?
) {
    val seedRunsJson = JSONArray(
        seededSummaries.map { run ->
            JSONObject()
                .put("seed", run.seed)
                .put("episodes", run.summary.episodes)
                .put("challengerWins", run.summary.challengerWins)
                .put("baselineWins", run.summary.baselineWins)
                .put("draws", run.summary.draws)
                .put("decidedGames", run.summary.decidedGames)
                .put("challengerWinRateDecided", run.summary.challengerWinRateDecided)
        }
    )

    val json = JSONObject()
        .put("gatePassed", gatePassed)
        .put("decisionReason", decisionReason)
        .put("promotionThreshold", promotionThreshold)
        .put("minimumDecidedGames", minimumDecidedGames)
        .put("challengerPolicy", challengerPolicyName)
        .put("baselinePolicy", baselinePolicyName)
        .put("challengerModel", challengerModelPath)
        .put("baselineModel", baselineModelPath)
        .put("promotedTo", promotedPath)
        .put("maxP95DecisionMs", maxP95DecisionMs)
        .put("challengerMoveLatency", JSONObject()
            .put("sampleCount", latencySummary.sampleCount)
            .put("averageMs", latencySummary.averageMs)
            .put("p50Ms", latencySummary.p50Ms)
            .put("p95Ms", latencySummary.p95Ms)
            .put("p99Ms", latencySummary.p99Ms)
            .put("maxMs", latencySummary.maxMs)
        )
        .put("seedRuns", seedRunsJson)
        .put("summary", JSONObject()
            .put("episodes", summary.episodes)
            .put("challengerWins", summary.challengerWins)
            .put("baselineWins", summary.baselineWins)
            .put("draws", summary.draws)
            .put("terminatedByPlyLimit", summary.terminatedByPlyLimit)
            .put("averagePly", summary.averagePly)
            .put("decidedGames", summary.decidedGames)
            .put("challengerWinRate", summary.challengerWinRate)
            .put("baselineWinRate", summary.baselineWinRate)
            .put("drawRate", summary.drawRate)
            .put("challengerWinRateDecided", summary.challengerWinRateDecided)
        )

    val outFile = File(outputPath)
    outFile.parentFile?.mkdirs()
    outFile.writeText(json.toString(2), Charsets.UTF_8)
}

private fun parseSeedList(raw: String?): List<Long> {
    if (raw.isNullOrBlank()) return emptyList()
    return raw.split(',')
        .mapNotNull { token -> token.trim().toLongOrNull() }
        .distinct()
}

private fun evaluateLatencyGate(
    latencySummary: DecisionLatencySummary,
    maxP95DecisionMs: Double?
): PromotionGateDecision {
    if (maxP95DecisionMs == null) {
        return PromotionGateDecision(
            gatePassed = true,
            reason = "Latency gate not configured."
        )
    }

    if (latencySummary.sampleCount == 0) {
        return PromotionGateDecision(
            gatePassed = false,
            reason = "Latency gate failed: no challenger move latency samples were recorded."
        )
    }

    if (latencySummary.p95Ms > maxP95DecisionMs) {
        return PromotionGateDecision(
            gatePassed = false,
            reason = "Latency gate failed: challenger p95 ${latencySummary.p95Ms}ms " +
                "exceeds threshold ${maxP95DecisionMs}ms."
        )
    }

    return PromotionGateDecision(
        gatePassed = true,
        reason = "Latency gate passed."
    )
}

private fun mergeGateDecisions(decisions: List<PromotionGateDecision>): PromotionGateDecision {
    require(decisions.isNotEmpty()) { "At least one gate decision is required." }

    val failures = decisions.filter { decision -> !decision.gatePassed }
    return if (failures.isEmpty()) {
        PromotionGateDecision(
            gatePassed = true,
            reason = "All gate checks passed."
        )
    } else {
        PromotionGateDecision(
            gatePassed = false,
            reason = failures.joinToString(" | ") { it.reason }
        )
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