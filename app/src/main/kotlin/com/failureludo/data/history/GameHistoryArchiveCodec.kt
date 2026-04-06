package com.failureludo.data.history

import com.failureludo.engine.PlayerId
import org.json.JSONArray
import org.json.JSONObject

object GameHistoryArchiveCodec {

    private const val SCHEMA_VERSION = 1

    fun toJson(archive: GameHistoryArchive): String {
        val root = JSONObject()
            .put("schemaVersion", SCHEMA_VERSION)
            .put("maxStoredGames", archive.maxStoredGames)
            .put("records", JSONArray(archive.records.map { recordToJson(it) }))

        return root.toString()
    }

    fun fromJson(raw: String): GameHistoryArchive {
        val root = JSONObject(raw)
        val schemaVersion = root.optInt("schemaVersion", 0)
        require(schemaVersion == SCHEMA_VERSION) {
            "Unsupported history schema version: $schemaVersion"
        }

        val maxStoredGames = root.optInt("maxStoredGames", 50)
        val records = root.optJSONArray("records")
            ?.toObjectList { recordFromJson(it) }
            .orEmpty()

        return GameHistoryArchive(
            records = records,
            maxStoredGames = maxStoredGames
        )
    }

    private fun recordToJson(record: GameHistoryRecord): JSONObject {
        return JSONObject()
            .put("gameId", record.gameId)
            .put("kind", record.kind.name)
            .put("createdAtEpochMs", record.createdAtEpochMs)
            .put("updatedAtEpochMs", record.updatedAtEpochMs)
            .put("flnVersion", record.flnVersion ?: JSONObject.NULL)
            .put("rulesetVersion", record.rulesetVersion ?: JSONObject.NULL)
            .put("status", record.status?.name ?: JSONObject.NULL)
            .put("playerNames", JSONArray(record.playerNames))
            .put("playableDocument", record.playableDocument?.let(::documentToJson) ?: JSONObject.NULL)
            .put("unsupportedDetectedVersion", record.unsupportedDetectedVersion ?: JSONObject.NULL)
            .put("unsupportedMetadata", record.unsupportedMetadata?.let(::metadataToJson) ?: JSONObject.NULL)
    }

    private fun recordFromJson(json: JSONObject): GameHistoryRecord {
        return GameHistoryRecord(
            gameId = json.getString("gameId"),
            kind = GameHistoryRecordKind.valueOf(json.getString("kind")),
            createdAtEpochMs = json.getLong("createdAtEpochMs"),
            updatedAtEpochMs = json.getLong("updatedAtEpochMs"),
            flnVersion = json.optStringOrNull("flnVersion"),
            rulesetVersion = json.optStringOrNull("rulesetVersion"),
            status = json.optStringOrNull("status")?.let { raw ->
                runCatching { FlnGameStatus.valueOf(raw) }.getOrNull()
            },
            playerNames = json.optJSONArray("playerNames")?.toStringList().orEmpty(),
            playableDocument = json.optJSONObject("playableDocument")?.let(::documentFromJson),
            unsupportedDetectedVersion = json.optStringOrNull("unsupportedDetectedVersion"),
            unsupportedMetadata = json.optJSONObject("unsupportedMetadata")?.let(::metadataFromJson)
        )
    }

    private fun documentToJson(document: FlnDocument): JSONObject {
        return JSONObject()
            .put("format", document.format)
            .put("flnVersion", document.flnVersion)
            .put("rulesetVersion", document.rulesetVersion)
            .put("gameId", document.gameId)
            .put("status", document.status.name)
            .put("createdAtEpochMs", document.createdAtEpochMs)
            .put("updatedAtEpochMs", document.updatedAtEpochMs)
            .put("players", JSONArray(document.players.map { playerToJson(it) }))
            .put("moves", JSONArray(document.moves.map { moveToJson(it) }))
            .put("tags", JSONObject(document.tags))
    }

    private fun documentFromJson(json: JSONObject): FlnDocument {
        val tagsObject = json.optJSONObject("tags") ?: JSONObject()
        val tags = tagsObject.keys().asSequence().associateWith { key -> tagsObject.getString(key) }

        return FlnDocument(
            format = json.optString("format", FLN_FORMAT),
            flnVersion = json.getString("flnVersion"),
            rulesetVersion = json.getString("rulesetVersion"),
            gameId = json.getString("gameId"),
            status = FlnGameStatus.valueOf(json.getString("status")),
            createdAtEpochMs = json.getLong("createdAtEpochMs"),
            updatedAtEpochMs = json.getLong("updatedAtEpochMs"),
            players = json.getJSONArray("players").toObjectList { playerFromJson(it) },
            moves = json.getJSONArray("moves").toObjectList { moveFromJson(it) },
            tags = tags
        )
    }

    private fun playerToJson(player: FlnPlayer): JSONObject {
        return JSONObject()
            .put("id", player.id.value)
            .put("name", player.name)
    }

    private fun playerFromJson(json: JSONObject): FlnPlayer {
        return FlnPlayer(
            id = PlayerId(json.getInt("id")),
            name = json.getString("name")
        )
    }

    private fun moveToJson(move: FlnMove): JSONObject {
        return JSONObject()
            .put("ply", move.ply)
            .put("actorId", move.actorId.value)
            .put("movingPlayerId", move.movingPlayerId.value)
            .put("diceValue", move.diceValue)
            .put("pieceId", move.pieceId ?: JSONObject.NULL)
            .put("choice", move.choice?.name ?: JSONObject.NULL)
            .put("rollOnlyReason", move.rollOnlyReason?.name ?: JSONObject.NULL)
    }

    private fun moveFromJson(json: JSONObject): FlnMove {
        val choice = json.optStringOrNull("choice")?.let { raw ->
            runCatching { FlnMoveChoice.valueOf(raw) }.getOrNull()
        }
        val rollOnlyReason = json.optStringOrNull("rollOnlyReason")?.let { raw ->
            runCatching { FlnRollOnlyReason.valueOf(raw) }.getOrNull()
        }

        return FlnMove(
            ply = json.getInt("ply"),
            actorId = PlayerId(json.getInt("actorId")),
            movingPlayerId = PlayerId(json.optInt("movingPlayerId", json.getInt("actorId"))),
            diceValue = json.getInt("diceValue"),
            pieceId = json.optIntOrNull("pieceId"),
            choice = choice,
            rollOnlyReason = rollOnlyReason
        )
    }

    private fun metadataToJson(metadata: FlnMetadataPreview): JSONObject {
        return JSONObject()
            .put("format", metadata.format ?: JSONObject.NULL)
            .put("flnVersion", metadata.flnVersion ?: JSONObject.NULL)
            .put("rulesetVersion", metadata.rulesetVersion ?: JSONObject.NULL)
            .put("gameId", metadata.gameId ?: JSONObject.NULL)
            .put("status", metadata.status ?: JSONObject.NULL)
            .put("createdAt", metadata.createdAt ?: JSONObject.NULL)
            .put("updatedAt", metadata.updatedAt ?: JSONObject.NULL)
            .put("players", JSONArray(metadata.players))
    }

    private fun metadataFromJson(json: JSONObject): FlnMetadataPreview {
        return FlnMetadataPreview(
            format = json.optStringOrNull("format"),
            flnVersion = json.optStringOrNull("flnVersion"),
            rulesetVersion = json.optStringOrNull("rulesetVersion"),
            gameId = json.optStringOrNull("gameId"),
            status = json.optStringOrNull("status"),
            createdAt = json.optStringOrNull("createdAt"),
            updatedAt = json.optStringOrNull("updatedAt"),
            players = json.optJSONArray("players")?.toStringList().orEmpty()
        )
    }

    private inline fun <T> JSONArray.toObjectList(block: (JSONObject) -> T): List<T> {
        val result = mutableListOf<T>()
        for (index in 0 until length()) {
            result += block(getJSONObject(index))
        }
        return result
    }

    private fun JSONArray.toStringList(): List<String> {
        val result = mutableListOf<String>()
        for (index in 0 until length()) {
            result += getString(index)
        }
        return result
    }

    private fun JSONObject.optStringOrNull(key: String): String? {
        if (!has(key) || isNull(key)) return null
        return optString(key).takeIf { it.isNotBlank() }
    }

    private fun JSONObject.optIntOrNull(key: String): Int? {
        if (!has(key) || isNull(key)) return null
        return getInt(key)
    }
}
