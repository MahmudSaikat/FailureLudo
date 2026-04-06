package com.failureludo.data.history

import com.failureludo.engine.PlayerId

class FlnV1Codec : FlnParser {

    override val supportedMajorVersion: Int = 1

    private val moveRegex = Regex(
        "^(\\d+)\\s+P(\\d+)(?:\\s+O(\\d+))?\\s+D([1-6])\\s+M([0-3])(?:\\s+H:(ENTER|DEFER))?$"
    )
    private val rollOnlyRegex = Regex(
        "^(\\d+)\\s+P(\\d+)\\s+D([1-6])\\s+X:(SKIP|FORFEIT)$"
    )

    override fun parse(text: String): FlnParseResult {
        val headers = linkedMapOf<String, String>()
        val playerHeaders = mutableListOf<String>()
        val moveLines = mutableListOf<String>()

        var moveSectionStarted = false

        for ((lineIndex, rawLine) in text.lineSequence().withIndex()) {
            val line = rawLine.trim()
            if (line.isEmpty()) {
                if (!moveSectionStarted && (headers.isNotEmpty() || playerHeaders.isNotEmpty())) {
                    moveSectionStarted = true
                }
                continue
            }

            if (!moveSectionStarted && line.startsWith("[")) {
                val parsed = parseHeaderLine(line)
                    ?: return FlnParseResult.InvalidFormat("Invalid header at line ${lineIndex + 1}: $line")
                if (parsed.first == "Player") {
                    playerHeaders += parsed.second
                } else {
                    headers[parsed.first] = parsed.second
                }
                continue
            }

            moveSectionStarted = true
            moveLines += line
        }

        val format = headers["Format"] ?: FLN_FORMAT
        if (format != FLN_FORMAT) {
            return FlnParseResult.InvalidFormat("Expected format '$FLN_FORMAT' but found '$format'.")
        }

        val flnVersion = headers["FlnVersion"]
            ?: return FlnParseResult.InvalidFormat("Missing required header 'FlnVersion'.")

        val parsedMajor = flnVersion.substringBefore('.').toIntOrNull()
            ?: return FlnParseResult.InvalidFormat("Invalid FLN version '$flnVersion'.")

        if (parsedMajor != supportedMajorVersion) {
            return FlnParseResult.InvalidFormat(
                "FlnV1Codec cannot parse major version '$parsedMajor'."
            )
        }

        val rulesetVersion = headers["RulesetVersion"]
            ?: return FlnParseResult.InvalidFormat("Missing required header 'RulesetVersion'.")

        val gameId = headers["GameId"]
            ?.takeIf { it.isNotBlank() }
            ?: return FlnParseResult.InvalidFormat("Missing required header 'GameId'.")

        val status = parseStatus(headers["Status"])
            ?: return FlnParseResult.InvalidFormat("Missing or invalid header 'Status'.")

        val createdAt = headers["CreatedAt"]?.toLongOrNull()
            ?: return FlnParseResult.InvalidFormat("Missing or invalid header 'CreatedAt'.")

        val updatedAt = headers["UpdatedAt"]?.toLongOrNull()
            ?: return FlnParseResult.InvalidFormat("Missing or invalid header 'UpdatedAt'.")

        val players = parsePlayers(playerHeaders)
            ?: return FlnParseResult.InvalidFormat("Invalid 'Player' header entry.")

        if (players.size < 2) {
            return FlnParseResult.InvalidFormat("At least two players are required in FLN headers.")
        }

        val moves = mutableListOf<FlnMove>()
        moveLines.forEachIndexed { index, line ->
            val parsedMove = parseMoveLine(line)
                ?: return FlnParseResult.InvalidFormat("Invalid move line '${line}'.")

            val expectedPly = index + 1
            if (parsedMove.ply != expectedPly) {
                return FlnParseResult.InvalidFormat(
                    "Move order mismatch at ply ${parsedMove.ply}; expected $expectedPly."
                )
            }

            moves += parsedMove
        }

        val reserved = setOf(
            "Format",
            "FlnVersion",
            "RulesetVersion",
            "GameId",
            "Status",
            "CreatedAt",
            "UpdatedAt"
        )

        val tags = headers.filterKeys { it !in reserved }

        return FlnParseResult.Success(
            FlnDocument(
                format = format,
                flnVersion = flnVersion,
                rulesetVersion = rulesetVersion,
                gameId = gameId,
                status = status,
                createdAtEpochMs = createdAt,
                updatedAtEpochMs = updatedAt,
                players = players,
                moves = moves,
                tags = tags
            )
        )
    }

    fun serialize(document: FlnDocument): String {
        require(document.format == FLN_FORMAT) {
            "Only '$FLN_FORMAT' documents are supported."
        }

        val majorVersion = document.majorVersion
        require(majorVersion == supportedMajorVersion) {
            "FlnV1Codec can only serialize major version $supportedMajorVersion."
        }

        require(document.players.size >= 2) {
            "At least two players are required for serialization."
        }

        val sortedPlayers = document.players.sortedBy { it.id.value }
        val sortedMoves = document.moves.sortedBy { it.ply }

        return buildString {
            appendHeader("Format", document.format)
            appendHeader("FlnVersion", document.flnVersion)
            appendHeader("RulesetVersion", document.rulesetVersion)
            appendHeader("GameId", document.gameId)
            appendHeader("Status", document.status.name)
            appendHeader("CreatedAt", document.createdAtEpochMs.toString())
            appendHeader("UpdatedAt", document.updatedAtEpochMs.toString())
            sortedPlayers.forEach { player ->
                appendHeader("Player", "${player.id.value}:${player.name}")
            }
            document.tags.toSortedMap().forEach { (key, value) ->
                appendHeader(key, value)
            }

            append('\n')
            sortedMoves.forEach { move ->
                append(move.ply)
                append(" P")
                append(move.actorId.value)
                when (move.rollOnlyReason) {
                    FlnRollOnlyReason.NO_MOVES -> {
                        append(" D")
                        append(move.diceValue)
                        append(" X:SKIP")
                    }

                    FlnRollOnlyReason.THREE_SIX_FORFEIT -> {
                        append(" D")
                        append(move.diceValue)
                        append(" X:FORFEIT")
                    }

                    null -> {
                        if (move.movingPlayerId != move.actorId) {
                            append(" O")
                            append(move.movingPlayerId.value)
                        }

                        append(" D")
                        append(move.diceValue)

                        val pieceId = requireNotNull(move.pieceId) {
                            "Move entry requires pieceId when rollOnlyReason is null."
                        }
                        append(" M")
                        append(pieceId)
                        when (move.choice) {
                            FlnMoveChoice.ENTER_HOME_PATH -> append(" H:ENTER")
                            FlnMoveChoice.DEFER_HOME_ENTRY -> append(" H:DEFER")
                            null -> Unit
                        }
                    }
                }
                append('\n')
            }
        }
    }

    private fun StringBuilder.appendHeader(tag: String, value: String) {
        append('[')
        append(tag)
        append(" \"")
        append(encodeHeaderValue(value))
        append("\"]\n")
    }

    private fun parseStatus(rawStatus: String?): FlnGameStatus? {
        if (rawStatus == null) return null
        return runCatching { FlnGameStatus.valueOf(rawStatus) }.getOrNull()
    }

    private fun parsePlayers(values: List<String>): List<FlnPlayer>? {
        val players = values.map { raw ->
            val separatorIndex = raw.indexOf(':')
            if (separatorIndex <= 0 || separatorIndex == raw.lastIndex) {
                return null
            }

            val idValue = raw.substring(0, separatorIndex).trim().toIntOrNull()
                ?: return null
            val playerId = runCatching { PlayerId(idValue) }.getOrNull()
                ?: return null

            val playerName = raw.substring(separatorIndex + 1).trim()
            if (playerName.isEmpty()) return null

            FlnPlayer(id = playerId, name = playerName)
        }

        return if (players.map { it.id }.toSet().size != players.size) {
            null
        } else {
            players
        }
    }

    private fun parseMoveLine(line: String): FlnMove? {
        moveRegex.matchEntire(line)?.let { match ->
            val ply = match.groupValues[1].toIntOrNull() ?: return null
            val actor = match.groupValues[2].toIntOrNull() ?: return null
            val actorId = runCatching { PlayerId(actor) }.getOrNull() ?: return null
            val movingPlayerId = when (val ownerRaw = match.groupValues[3]) {
                "" -> actorId
                else -> {
                    val owner = ownerRaw.toIntOrNull() ?: return null
                    runCatching { PlayerId(owner) }.getOrNull() ?: return null
                }
            }
            val diceValue = match.groupValues[4].toIntOrNull() ?: return null
            val pieceId = match.groupValues[5].toIntOrNull() ?: return null
            val choice = when (match.groupValues[6]) {
                "ENTER" -> FlnMoveChoice.ENTER_HOME_PATH
                "DEFER" -> FlnMoveChoice.DEFER_HOME_ENTRY
                else -> null
            }

            return FlnMove(
                ply = ply,
                actorId = actorId,
                movingPlayerId = movingPlayerId,
                diceValue = diceValue,
                pieceId = pieceId,
                choice = choice
            )
        }

        rollOnlyRegex.matchEntire(line)?.let { match ->
            val ply = match.groupValues[1].toIntOrNull() ?: return null
            val actor = match.groupValues[2].toIntOrNull() ?: return null
            val actorId = runCatching { PlayerId(actor) }.getOrNull() ?: return null
            val diceValue = match.groupValues[3].toIntOrNull() ?: return null
            val reason = when (match.groupValues[4]) {
                "SKIP" -> FlnRollOnlyReason.NO_MOVES
                "FORFEIT" -> FlnRollOnlyReason.THREE_SIX_FORFEIT
                else -> return null
            }

            return FlnMove(
                ply = ply,
                actorId = actorId,
                movingPlayerId = actorId,
                diceValue = diceValue,
                pieceId = null,
                rollOnlyReason = reason
            )
        }

        return null
    }
}
