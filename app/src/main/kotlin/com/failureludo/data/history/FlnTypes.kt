package com.failureludo.data.history

import com.failureludo.engine.PlayerId

const val FLN_FORMAT = "FLN"

enum class FlnGameStatus {
    ACTIVE,
    FINISHED
}

enum class FlnMoveChoice {
    ENTER_HOME_PATH,
    DEFER_HOME_ENTRY
}

enum class FlnRollOnlyReason {
    NO_MOVES,
    THREE_SIX_FORFEIT
}

data class FlnPlayer(
    val id: PlayerId,
    val name: String
)

data class FlnMove(
    val ply: Int,
    val actorId: PlayerId,
    val movingPlayerId: PlayerId = actorId,
    val diceValue: Int,
    val pieceId: Int? = null,
    val choice: FlnMoveChoice? = null,
    val rollOnlyReason: FlnRollOnlyReason? = null
)

data class FlnDocument(
    val format: String = FLN_FORMAT,
    val flnVersion: String,
    val rulesetVersion: String,
    val gameId: String,
    val status: FlnGameStatus,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val players: List<FlnPlayer>,
    val moves: List<FlnMove>,
    val tags: Map<String, String> = emptyMap()
) {
    val majorVersion: Int
        get() = flnVersion.substringBefore('.').toIntOrNull() ?: -1
}

data class FlnMetadataPreview(
    val format: String?,
    val flnVersion: String?,
    val rulesetVersion: String?,
    val gameId: String?,
    val status: String?,
    val createdAt: String?,
    val updatedAt: String?,
    val players: List<String>
)

sealed interface FlnParseResult {
    data class Success(val document: FlnDocument) : FlnParseResult

    data class UnsupportedVersion(
        val detectedVersion: String,
        val supportedMajorVersions: Set<Int>,
        val metadata: FlnMetadataPreview
    ) : FlnParseResult

    data class InvalidFormat(val reason: String) : FlnParseResult
}
