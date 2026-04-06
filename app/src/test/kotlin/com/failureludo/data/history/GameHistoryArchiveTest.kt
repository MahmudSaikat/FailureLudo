package com.failureludo.data.history

import com.failureludo.engine.PlayerId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameHistoryArchiveTest {

    @Test
    fun importSuccessUpsertsPlayableRecord() {
        val archive = GameHistoryArchive()
        val fln = sampleFln(
            gameId = "g-1",
            status = "ACTIVE",
            updatedAt = 2000L,
            moveLines = listOf(
                "1 P1 D6 M0"
            )
        )

        val (updated, result) = archive.importFln(fln, importedAtEpochMs = 2500L)

        assertTrue(result is GameHistoryImportResult.Imported)
        val record = (result as GameHistoryImportResult.Imported).record
        assertEquals(GameHistoryRecordKind.PLAYABLE, record.kind)
        assertEquals("g-1", record.gameId)
        assertEquals(2500L, record.updatedAtEpochMs)
        assertNotNull(record.playableDocument)
        assertEquals(1, updated.records.size)
    }

    @Test
    fun importUnsupportedStoresReadOnlyRecord() {
        val archive = GameHistoryArchive()
        val fln = sampleFln(
            gameId = "g-v2",
            version = "2.0",
            status = "FINISHED",
            updatedAt = 2000L,
            moveLines = listOf("1 P1 D6 M0")
        )

        val (updated, result) = archive.importFln(fln, importedAtEpochMs = 3333L)

        assertTrue(result is GameHistoryImportResult.Unsupported)
        val record = (result as GameHistoryImportResult.Unsupported).record
        assertEquals(GameHistoryRecordKind.UNSUPPORTED, record.kind)
        assertEquals("2.0", record.unsupportedDetectedVersion)
        assertEquals("g-v2", record.gameId)
        assertEquals(1, updated.records.size)
    }

    @Test
    fun pruningKeepsActiveAndDropsOldestFinishedFirst() {
        val base = GameHistoryArchive(maxStoredGames = 2)

        val withActive = base.upsertPlayable(document("active", FlnGameStatus.ACTIVE, updatedAt = 1000L))
        val withFinished1 = withActive.upsertPlayable(document("finished-1", FlnGameStatus.FINISHED, updatedAt = 2000L))
        val withFinished2 = withFinished1.upsertPlayable(document("finished-2", FlnGameStatus.FINISHED, updatedAt = 3000L))

        assertEquals(2, withFinished2.records.size)
        assertNotNull(withFinished2.findRecord("active"))
        assertNull(withFinished2.findRecord("finished-1"))
        assertNotNull(withFinished2.findRecord("finished-2"))
    }

    @Test
    fun exportPlayableFlnReturnsTextForV1Document() {
        val archive = GameHistoryArchive()
            .upsertPlayable(document("g-export", FlnGameStatus.FINISHED, updatedAt = 5000L))

        val exported = archive.exportPlayableFln("g-export")

        assertNotNull(exported)
        assertTrue(exported!!.contains("[FlnVersion \"1.0\"]"))
        assertTrue(exported.contains("[GameId \"g-export\"]"))
    }

    @Test
    fun archiveCanContainPlayableAndUnsupportedEntriesTogether() {
        val playable = document("g-play", FlnGameStatus.ACTIVE, updatedAt = 1234L)

        val withPlayable = GameHistoryArchive().upsertPlayable(playable)
        val unsupportedFln = sampleFln(
            gameId = "g-legacy",
            version = "2.0",
            status = "FINISHED",
            updatedAt = 1400L,
            moveLines = listOf("1 P1 D6 M0")
        )

        val (updated, importResult) = withPlayable.importFln(
            unsupportedFln,
            importedAtEpochMs = 1500L
        )

        assertTrue(importResult is GameHistoryImportResult.Unsupported)
        assertEquals(2, updated.records.size)
        assertEquals(GameHistoryRecordKind.PLAYABLE, updated.findRecord("g-play")?.kind)
        assertEquals(GameHistoryRecordKind.UNSUPPORTED, updated.findRecord("g-legacy")?.kind)
        assertEquals("2.0", updated.findRecord("g-legacy")?.unsupportedDetectedVersion)
    }

    private fun document(gameId: String, status: FlnGameStatus, updatedAt: Long): FlnDocument {
        return FlnDocument(
            flnVersion = "1.0",
            rulesetVersion = "2026.04",
            gameId = gameId,
            status = status,
            createdAtEpochMs = 1000L,
            updatedAtEpochMs = updatedAt,
            players = listOf(
                FlnPlayer(PlayerId(1), "Player-1"),
                FlnPlayer(PlayerId(2), "Player-2")
            ),
            moves = listOf(
                FlnMove(ply = 1, actorId = PlayerId(1), diceValue = 6, pieceId = 0)
            ),
            tags = mapOf("AppVersion" to "1.0.6")
        )
    }

    private fun sampleFln(
        gameId: String,
        version: String = "1.0",
        status: String,
        updatedAt: Long,
        moveLines: List<String>
    ): String {
        val body = moveLines.joinToString("\n")
        return """
            [Format "FLN"]
            [FlnVersion "$version"]
            [RulesetVersion "2026.04"]
            [GameId "$gameId"]
            [Status "$status"]
            [CreatedAt "1000"]
            [UpdatedAt "$updatedAt"]
            [Player "1:Player-1"]
            [Player "2:Player-2"]

            $body
        """.trimIndent()
    }
}
