package com.failureludo.data.history

import com.failureludo.engine.PlayerId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FlnParserRegistryTest {

    @Test
    fun parsesValidV1Document() {
        val text = """
            [Format "FLN"]
            [FlnVersion "1.0"]
            [RulesetVersion "2026.04"]
            [GameId "g-001"]
            [Status "ACTIVE"]
            [CreatedAt "1712360000000"]
            [UpdatedAt "1712360001000"]
            [Player "1:Player-1"]
            [Player "2:Player-2"]
            [Player "3:Player-3"]

            1 P1 D6 M0
            2 P1 D2 M0 H:DEFER
            3 P2 O3 D6 M1 H:ENTER
            4 P3 D1 X:SKIP
        """.trimIndent()

        val result = FlnParserRegistry.default().parse(text)

        assertTrue(result is FlnParseResult.Success)
        val document = (result as FlnParseResult.Success).document
        assertEquals("1.0", document.flnVersion)
        assertEquals("2026.04", document.rulesetVersion)
        assertEquals(FlnGameStatus.ACTIVE, document.status)
        assertEquals(4, document.moves.size)
        assertEquals(FlnMoveChoice.DEFER_HOME_ENTRY, document.moves[1].choice)
        assertEquals(FlnMoveChoice.ENTER_HOME_PATH, document.moves[2].choice)
        assertEquals(PlayerId(3), document.moves[2].movingPlayerId)
        assertEquals(FlnRollOnlyReason.NO_MOVES, document.moves[3].rollOnlyReason)
        assertEquals(null, document.moves[3].pieceId)
    }

    @Test
    fun returnsUnsupportedVersionWithMetadataPreview() {
        val text = """
            [Format "FLN"]
            [FlnVersion "2.0"]
            [RulesetVersion "2027.01"]
            [GameId "g-unsupported"]
            [Status "FINISHED"]
            [CreatedAt "1712360000000"]
            [UpdatedAt "1712360001000"]
            [Player "1:Alpha"]
            [Player "2:Beta"]

            1 P1 D6 M0
        """.trimIndent()

        val result = FlnParserRegistry.default().parse(text)

        assertTrue(result is FlnParseResult.UnsupportedVersion)
        val unsupported = result as FlnParseResult.UnsupportedVersion
        assertEquals("2.0", unsupported.detectedVersion)
        assertEquals(setOf(1), unsupported.supportedMajorVersions)
        assertEquals("g-unsupported", unsupported.metadata.gameId)
        assertEquals("FINISHED", unsupported.metadata.status)
    }

    @Test
    fun rejectsOutOfOrderPlySequence() {
        val text = """
            [Format "FLN"]
            [FlnVersion "1.0"]
            [RulesetVersion "2026.04"]
            [GameId "g-order"]
            [Status "ACTIVE"]
            [CreatedAt "1712360000000"]
            [UpdatedAt "1712360001000"]
            [Player "1:Player-1"]
            [Player "2:Player-2"]

            1 P1 D6 M0
            3 P2 D1 M0
        """.trimIndent()

        val result = FlnParserRegistry.default().parse(text)

        assertTrue(result is FlnParseResult.InvalidFormat)
        val invalid = result as FlnParseResult.InvalidFormat
        assertTrue(invalid.reason.contains("Move order mismatch"))
    }

    @Test
    fun serializesAndParsesRoundTrip() {
        val document = FlnDocument(
            flnVersion = "1.0",
            rulesetVersion = "2026.04",
            gameId = "g-roundtrip",
            status = FlnGameStatus.FINISHED,
            createdAtEpochMs = 1712360000000,
            updatedAtEpochMs = 1712360009000,
            players = listOf(
                FlnPlayer(PlayerId(1), "Alpha"),
                FlnPlayer(PlayerId(2), "Beta")
            ),
            moves = listOf(
                FlnMove(ply = 1, actorId = PlayerId(1), diceValue = 6, pieceId = 0),
                FlnMove(ply = 2, actorId = PlayerId(1), diceValue = 2, pieceId = 0, choice = FlnMoveChoice.DEFER_HOME_ENTRY),
                FlnMove(
                    ply = 3,
                    actorId = PlayerId(2),
                    movingPlayerId = PlayerId(1),
                    diceValue = 6,
                    pieceId = 1,
                    choice = FlnMoveChoice.ENTER_HOME_PATH
                ),
                FlnMove(
                    ply = 4,
                    actorId = PlayerId(2),
                    diceValue = 1,
                    rollOnlyReason = FlnRollOnlyReason.NO_MOVES
                )
            ),
            tags = mapOf("AppVersion" to "1.0.6")
        )

        val codec = FlnV1Codec()
        val serialized = codec.serialize(document)
        val parsed = FlnParserRegistry.default().parse(serialized)

        assertTrue(parsed is FlnParseResult.Success)
        val parsedDocument = (parsed as FlnParseResult.Success).document
        assertEquals(document.gameId, parsedDocument.gameId)
        assertEquals(document.status, parsedDocument.status)
        assertEquals(document.players, parsedDocument.players)
        assertEquals(document.moves, parsedDocument.moves)
        assertEquals("1.0.6", parsedDocument.tags["AppVersion"])
    }
}
