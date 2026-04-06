package com.failureludo.data.history

interface GameHistoryRepository {
    suspend fun loadArchive(): GameHistoryArchive
    suspend fun listRecords(): List<GameHistoryRecord>
    suspend fun findRecord(gameId: String): GameHistoryRecord?
    suspend fun upsertPlayable(document: FlnDocument)
    suspend fun importFln(text: String): GameHistoryImportResult
    suspend fun exportPlayableFln(gameId: String): String?
    suspend fun delete(gameId: String)
    suspend fun clearAll()
}

class LocalGameHistoryRepository(
    private val store: GameHistoryStore
) : GameHistoryRepository {

    override suspend fun loadArchive(): GameHistoryArchive = store.loadArchive()

    override suspend fun listRecords(): List<GameHistoryRecord> = store.listRecords()

    override suspend fun findRecord(gameId: String): GameHistoryRecord? = store.findRecord(gameId)

    override suspend fun upsertPlayable(document: FlnDocument) = store.upsertPlayable(document)

    override suspend fun importFln(text: String): GameHistoryImportResult = store.importFln(text)

    override suspend fun exportPlayableFln(gameId: String): String? = store.exportPlayableFln(gameId)

    override suspend fun delete(gameId: String) = store.delete(gameId)

    override suspend fun clearAll() = store.clearAll()
}
