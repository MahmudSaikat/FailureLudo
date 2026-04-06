package com.failureludo.data.history

import java.util.UUID

private const val DEFAULT_MAX_STORED_GAMES = 50

enum class GameHistoryRecordKind {
    PLAYABLE,
    UNSUPPORTED
}

data class GameHistoryRecord(
    val gameId: String,
    val kind: GameHistoryRecordKind,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val flnVersion: String?,
    val rulesetVersion: String?,
    val status: FlnGameStatus?,
    val playerNames: List<String>,
    val playableDocument: FlnDocument? = null,
    val unsupportedDetectedVersion: String? = null,
    val unsupportedMetadata: FlnMetadataPreview? = null
) {
    val isActivePlayable: Boolean
        get() = kind == GameHistoryRecordKind.PLAYABLE && status == FlnGameStatus.ACTIVE
}

sealed interface GameHistoryImportResult {
    data class Imported(val record: GameHistoryRecord) : GameHistoryImportResult
    data class Unsupported(val record: GameHistoryRecord) : GameHistoryImportResult
    data class Invalid(val reason: String) : GameHistoryImportResult
}

data class GameHistoryArchive(
    val records: List<GameHistoryRecord> = emptyList(),
    val maxStoredGames: Int = DEFAULT_MAX_STORED_GAMES
) {

    fun upsertPlayable(document: FlnDocument): GameHistoryArchive {
        val record = GameHistoryRecord(
            gameId = document.gameId,
            kind = GameHistoryRecordKind.PLAYABLE,
            createdAtEpochMs = document.createdAtEpochMs,
            updatedAtEpochMs = document.updatedAtEpochMs,
            flnVersion = document.flnVersion,
            rulesetVersion = document.rulesetVersion,
            status = document.status,
            playerNames = document.players.map { it.name },
            playableDocument = document
        )

        val next = records.filterNot { it.gameId == document.gameId } + record
        return copy(records = prune(next))
    }

    fun delete(gameId: String): GameHistoryArchive {
        return copy(records = records.filterNot { it.gameId == gameId })
    }

    fun findRecord(gameId: String): GameHistoryRecord? {
        return records.firstOrNull { it.gameId == gameId }
    }

    fun exportPlayableFln(gameId: String): String? {
        val record = findRecord(gameId) ?: return null
        if (record.kind != GameHistoryRecordKind.PLAYABLE) return null

        val document = record.playableDocument ?: return null
        return when (document.majorVersion) {
            1 -> FlnV1Codec().serialize(document)
            else -> null
        }
    }

    fun importFln(
        text: String,
        parserRegistry: FlnParserRegistry = FlnParserRegistry.default(),
        importedAtEpochMs: Long = System.currentTimeMillis()
    ): Pair<GameHistoryArchive, GameHistoryImportResult> {
        return when (val parsed = parserRegistry.parse(text)) {
            is FlnParseResult.Success -> {
                val importedDoc = parsed.document.copy(
                    updatedAtEpochMs = maxOf(parsed.document.updatedAtEpochMs, importedAtEpochMs)
                )
                val nextArchive = upsertPlayable(importedDoc)
                val record = requireNotNull(nextArchive.findRecord(importedDoc.gameId))
                nextArchive to GameHistoryImportResult.Imported(record)
            }

            is FlnParseResult.UnsupportedVersion -> {
                val resolvedGameId = resolveUnsupportedGameId(
                    parsed.metadata.gameId,
                    importedAtEpochMs
                )
                val record = GameHistoryRecord(
                    gameId = resolvedGameId,
                    kind = GameHistoryRecordKind.UNSUPPORTED,
                    createdAtEpochMs = parsed.metadata.createdAt?.toLongOrNull() ?: importedAtEpochMs,
                    updatedAtEpochMs = parsed.metadata.updatedAt?.toLongOrNull() ?: importedAtEpochMs,
                    flnVersion = parsed.metadata.flnVersion,
                    rulesetVersion = parsed.metadata.rulesetVersion,
                    status = parsed.metadata.status?.let { raw ->
                        runCatching { FlnGameStatus.valueOf(raw) }.getOrNull()
                    },
                    playerNames = parsed.metadata.players,
                    unsupportedDetectedVersion = parsed.detectedVersion,
                    unsupportedMetadata = parsed.metadata
                )

                val next = records.filterNot { it.gameId == resolvedGameId } + record
                val nextArchive = copy(records = prune(next))
                val inserted = requireNotNull(nextArchive.findRecord(resolvedGameId))
                nextArchive to GameHistoryImportResult.Unsupported(inserted)
            }

            is FlnParseResult.InvalidFormat -> this to GameHistoryImportResult.Invalid(parsed.reason)
        }
    }

    private fun prune(input: List<GameHistoryRecord>): List<GameHistoryRecord> {
        val deduped = input
            .groupBy { it.gameId }
            .mapValues { (_, candidates) -> candidates.maxBy { it.updatedAtEpochMs } }
            .values
            .toList()

        val activePlayable = deduped
            .filter { it.isActivePlayable }
            .sortedByDescending { it.updatedAtEpochMs }

        val others = deduped
            .filterNot { it.isActivePlayable }
            .sortedByDescending { it.updatedAtEpochMs }

        if (activePlayable.size >= maxStoredGames) {
            // Never auto-delete ACTIVE games, even if they exceed the soft limit.
            return activePlayable
        }

        val remainingSlots = (maxStoredGames - activePlayable.size).coerceAtLeast(0)
        val keptOthers = others.take(remainingSlots)

        return (activePlayable + keptOthers)
            .sortedByDescending { it.updatedAtEpochMs }
    }

    private fun resolveUnsupportedGameId(metadataGameId: String?, importedAtEpochMs: Long): String {
        val candidate = metadataGameId?.takeIf { it.isNotBlank() }
            ?: "unsupported-$importedAtEpochMs"

        if (records.none { it.gameId == candidate }) {
            return candidate
        }

        while (true) {
            val generated = "unsupported-${UUID.randomUUID()}"
            if (records.none { it.gameId == generated }) {
                return generated
            }
        }
    }
}
