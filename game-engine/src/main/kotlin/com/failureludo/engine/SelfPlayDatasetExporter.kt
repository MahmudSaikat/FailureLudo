package com.failureludo.engine

import java.io.File

data class SelfPlayDatasetExportConfig(
    val includeLegalMoveOptions: Boolean = true,
    val includeStateEventLogTypes: Boolean = false
)

object SelfPlayDatasetExporter {

    fun exportEpisodesToJsonl(
        episodes: List<SelfPlayEpisode>,
        outputFile: File,
        config: SelfPlayDatasetExportConfig = SelfPlayDatasetExportConfig()
    ): Int {
        require(episodes.isNotEmpty()) { "At least one episode is required for export." }

        outputFile.parentFile?.mkdirs()

        var written = 0
        outputFile.bufferedWriter(Charsets.UTF_8).use { writer ->
            episodes.forEachIndexed { episodeIndex, episode ->
                val lines = episodeToJsonlLines(
                    episode = episode,
                    episodeIndex = episodeIndex,
                    config = config
                )
                lines.forEach { line ->
                    writer.appendLine(line)
                    written += 1
                }
            }
        }

        return written
    }

    fun episodeToJsonlLines(
        episode: SelfPlayEpisode,
        episodeIndex: Int = 0,
        config: SelfPlayDatasetExportConfig = SelfPlayDatasetExportConfig()
    ): List<String> {
        val samples = SelfPlayRunner.toTrainingSamples(episode)

        return episode.steps.zip(samples).map { (step, sample) ->
            val legalMoveInfo = if (config.includeLegalMoveOptions) {
                legalMoveInfoForStep(step)
            } else {
                LegalMoveInfo(emptyList(), chosenIndex = null)
            }

            buildJsonObject {
                field("version", "1")
                field("episodeIndex", episodeIndex.toString())
                field("mode", jsonString(episode.mode.name))
                field("terminatedByPlyLimit", jsonBoolean(episode.terminatedByPlyLimit))
                field("ply", step.ply.toString())
                field("actorId", step.action.actorId.value.toString())
                field("diceValue", step.action.diceValue.toString())
                field("actionType", jsonString(actionType(step.action)))
                field("action", actionToJson(step.action))
                field("outcome", sample.outcomeFromActorPerspective.toString())
                field("state", stateToJson(step.before, config))

                if (config.includeLegalMoveOptions) {
                    field("chosenMoveIndex", legalMoveInfo.chosenIndex?.toString() ?: "null")
                    field(
                        "legalMoveOptions",
                        jsonArray(
                            legalMoveInfo.options.map { option ->
                                buildJsonObject {
                                    field("movingPlayerId", option.movingPlayerId.toString())
                                    field("pieceId", option.pieceId.toString())
                                    field("deferHomeEntry", jsonBoolean(option.deferHomeEntry))
                                }
                            }
                        )
                    )
                }
            }
        }
    }

    private fun legalMoveInfoForStep(step: SelfPlayStep): LegalMoveInfo {
        val action = step.action as? SelfPlayAction.Move ?: return LegalMoveInfo(emptyList(), null)

        val rolled = GameEngine.rollDice(step.before, forcedDiceValue = step.action.diceValue)
        if (rolled.turnPhase != TurnPhase.WAITING_FOR_PIECE_SELECTION) {
            return LegalMoveInfo(emptyList(), null)
        }

        val dice = rolled.lastDice?.value ?: return LegalMoveInfo(emptyList(), null)
        val options = mutableListOf<LegalMoveOption>()
        rolled.movablePieces.forEach { piece ->
            val movingPlayerId = playerIdForColor(rolled.players, piece.color).value
            options += LegalMoveOption(
                movingPlayerId = movingPlayerId,
                pieceId = piece.id,
                deferHomeEntry = false
            )

            val canDefer = GameRules.wouldEnterHomePath(
                piece = piece,
                diceValue = dice,
                color = piece.color,
                players = rolled.players,
                mode = rolled.mode
            )
            if (canDefer) {
                options += LegalMoveOption(
                    movingPlayerId = movingPlayerId,
                    pieceId = piece.id,
                    deferHomeEntry = true
                )
            }
        }

        val chosenIndex = options.indexOfFirst { option ->
            option.movingPlayerId == action.movingPlayerId.value &&
                option.pieceId == action.pieceId &&
                option.deferHomeEntry == action.deferHomeEntry
        }.takeIf { it >= 0 }

        return LegalMoveInfo(options = options, chosenIndex = chosenIndex)
    }

    private fun actionType(action: SelfPlayAction): String = when (action) {
        is SelfPlayAction.Move -> "MOVE"
        is SelfPlayAction.RollOnly -> "ROLL_ONLY"
    }

    private fun actionToJson(action: SelfPlayAction): String {
        return when (action) {
            is SelfPlayAction.Move -> buildJsonObject {
                field("type", jsonString("MOVE"))
                field("movingPlayerId", action.movingPlayerId.value.toString())
                field("pieceId", action.pieceId.toString())
                field("deferHomeEntry", jsonBoolean(action.deferHomeEntry))
            }

            is SelfPlayAction.RollOnly -> buildJsonObject {
                field("type", jsonString("ROLL_ONLY"))
                field("reason", jsonString(action.reason.name))
            }
        }
    }

    private fun stateToJson(state: GameState, config: SelfPlayDatasetExportConfig): String {
        val playersJson = jsonArray(
            state.players.sortedBy { it.id.value }.map { player ->
                buildJsonObject {
                    field("id", player.id.value.toString())
                    field("color", jsonString(player.color.name))
                    field("type", jsonString(player.type.name))
                    field("isActive", jsonBoolean(player.isActive))
                    field(
                        "pieces",
                        jsonArray(
                            player.pieces.sortedBy { it.id }.map { piece ->
                                buildJsonObject {
                                    field("id", piece.id.toString())
                                    field("position", piecePositionToJson(piece.position))
                                    field("lastMovedAt", piece.lastMovedAt.toString())
                                }
                            }
                        )
                    )
                }
            }
        )

        val diceByPlayerJson = jsonArray(
            state.diceByPlayer.entries
                .sortedBy { it.key.value }
                .map { (playerId, value) ->
                    buildJsonObject {
                        field("playerId", playerId.value.toString())
                        field("dice", value?.toString() ?: "null")
                    }
                }
        )

        val enteredFlagsJson = jsonArray(
            state.hasEnteredBoardAtLeastOnce.entries
                .sortedBy { it.key.value }
                .map { (playerId, entered) ->
                    buildJsonObject {
                        field("playerId", playerId.value.toString())
                        field("entered", jsonBoolean(entered))
                    }
                }
        )

        val movableJson = jsonArray(
            state.movablePieces.map { piece ->
                buildJsonObject {
                    field("color", jsonString(piece.color.name))
                    field("pieceId", piece.id.toString())
                }
            }
        )

        return buildJsonObject {
            field("moveCounter", state.moveCounter.toString())
            field("currentPlayerId", state.currentPlayer.id.value.toString())
            field("currentPlayerIndex", state.currentPlayerIndex.toString())
            field("turnPhase", jsonString(state.turnPhase.name))
            field("mode", jsonString(state.mode.name))

            val lastDice = state.lastDice
            if (lastDice == null) {
                field("lastDice", "null")
            } else {
                field(
                    "lastDice",
                    buildJsonObject {
                        field("value", lastDice.value.toString())
                        field("rollCount", lastDice.rollCount.toString())
                    }
                )
            }

            field(
                "sharedTeamDiceEnabled",
                jsonArray(state.sharedTeamDiceEnabled.sorted().map { it.toString() })
            )
            field("winners", jsonArray((state.winners ?: emptyList()).map { it.value.toString() }))
            field("players", playersJson)
            field("diceByPlayer", diceByPlayerJson)
            field("enteredBoardFlags", enteredFlagsJson)
            field("movablePieces", movableJson)

            if (config.includeStateEventLogTypes) {
                field(
                    "eventLogTypes",
                    jsonArray(state.eventLog.map { event -> jsonString(event::class.simpleName ?: "Unknown") })
                )
            }
        }
    }

    private fun piecePositionToJson(position: PiecePosition): String {
        return when (position) {
            PiecePosition.HomeBase -> buildJsonObject { field("type", jsonString("HOME_BASE")) }
            is PiecePosition.MainTrack -> buildJsonObject {
                field("type", jsonString("MAIN_TRACK"))
                field("index", position.index.toString())
            }

            is PiecePosition.HomeColumn -> buildJsonObject {
                field("type", jsonString("HOME_COLUMN"))
                field("step", position.step.toString())
            }

            PiecePosition.Finished -> buildJsonObject { field("type", jsonString("FINISHED")) }
        }
    }

    private fun playerIdForColor(players: List<Player>, color: PlayerColor): PlayerId {
        return players.firstOrNull { it.color == color }?.id
            ?: error("Player not found for color $color.")
    }

    private fun buildJsonObject(builder: JsonObjectBuilder.() -> Unit): String {
        val b = JsonObjectBuilder()
        b.builder()
        return "{${b.fields.joinToString(",")}}"
    }

    private fun jsonArray(items: List<String>): String = "[${items.joinToString(",")}]"

    private fun jsonString(value: String): String = "\"${escapeJson(value)}\""

    private fun jsonBoolean(value: Boolean): String = if (value) "true" else "false"

    private fun escapeJson(value: String): String {
        val out = StringBuilder(value.length + 8)
        value.forEach { ch ->
            when (ch) {
                '\\' -> out.append("\\\\")
                '"' -> out.append("\\\"")
                '\n' -> out.append("\\n")
                '\r' -> out.append("\\r")
                '\t' -> out.append("\\t")
                else -> out.append(ch)
            }
        }
        return out.toString()
    }

    private data class LegalMoveInfo(
        val options: List<LegalMoveOption>,
        val chosenIndex: Int?
    )

    private data class LegalMoveOption(
        val movingPlayerId: Int,
        val pieceId: Int,
        val deferHomeEntry: Boolean
    )

    private class JsonObjectBuilder {
        val fields = mutableListOf<String>()

        fun field(name: String, rawValue: String) {
            fields += "\"${escapeJson(name)}\":$rawValue"
        }
    }
}