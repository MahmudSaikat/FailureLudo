package com.failureludo.data.history

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import java.io.IOException

private val Context.gameHistoryDataStore by preferencesDataStore(name = "game_history")

class GameHistoryStore(
    private val context: Context,
    private val parserRegistry: FlnParserRegistry = FlnParserRegistry.default()
) {

    private object Keys {
        val HISTORY_ARCHIVE_JSON = stringPreferencesKey("history_archive_json")
    }

    suspend fun loadArchive(): GameHistoryArchive {
        val prefs = context.gameHistoryDataStore.data
            .catch { error ->
                if (error is IOException) emit(emptyPreferences()) else throw error
            }
            .first()

        return decodeArchive(prefs[Keys.HISTORY_ARCHIVE_JSON])
    }

    suspend fun saveArchive(archive: GameHistoryArchive) {
        context.gameHistoryDataStore.edit { prefs ->
            prefs[Keys.HISTORY_ARCHIVE_JSON] = GameHistoryArchiveCodec.toJson(archive)
        }
    }

    suspend fun listRecords(): List<GameHistoryRecord> {
        return loadArchive().records
    }

    suspend fun findRecord(gameId: String): GameHistoryRecord? {
        return loadArchive().findRecord(gameId)
    }

    suspend fun upsertPlayable(document: FlnDocument) {
        context.gameHistoryDataStore.edit { prefs ->
            val current = decodeArchive(prefs[Keys.HISTORY_ARCHIVE_JSON])
            val updated = current.upsertPlayable(document)
            prefs[Keys.HISTORY_ARCHIVE_JSON] = GameHistoryArchiveCodec.toJson(updated)
        }
    }

    suspend fun importFln(text: String): GameHistoryImportResult {
        var importResult: GameHistoryImportResult? = null
        context.gameHistoryDataStore.edit { prefs ->
            val current = decodeArchive(prefs[Keys.HISTORY_ARCHIVE_JSON])
            val (updated, result) = current.importFln(text, parserRegistry)
            prefs[Keys.HISTORY_ARCHIVE_JSON] = GameHistoryArchiveCodec.toJson(updated)
            importResult = result
        }

        return requireNotNull(importResult)
    }

    suspend fun exportPlayableFln(gameId: String): String? {
        val archive = loadArchive()
        return archive.exportPlayableFln(gameId)
    }

    suspend fun delete(gameId: String) {
        context.gameHistoryDataStore.edit { prefs ->
            val current = decodeArchive(prefs[Keys.HISTORY_ARCHIVE_JSON])
            val updated = current.delete(gameId)
            prefs[Keys.HISTORY_ARCHIVE_JSON] = GameHistoryArchiveCodec.toJson(updated)
        }
    }

    suspend fun clearAll() {
        context.gameHistoryDataStore.edit { prefs ->
            prefs.remove(Keys.HISTORY_ARCHIVE_JSON)
        }
    }

    private fun decodeArchive(raw: String?): GameHistoryArchive {
        if (raw.isNullOrBlank()) return GameHistoryArchive()

        return runCatching {
            GameHistoryArchiveCodec.fromJson(raw)
        }.getOrElse {
            // Corrupt archive should fail closed and recover to an empty archive.
            GameHistoryArchive()
        }
    }
}
