package com.failureludo.ui.components

import androidx.compose.ui.geometry.Offset
import com.failureludo.engine.Piece
import com.failureludo.engine.PiecePosition
import com.failureludo.engine.PlayerColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LudoBoardCanvasGeometryTest {

    @Test
    fun pieceRadiusForStack_usesLargerVisualScale() {
        val cellSize = 24f

        val single = pieceRadiusForStack(stackCount = 1, cellSize = cellSize)
        val double = pieceRadiusForStack(stackCount = 2, cellSize = cellSize)
        val triple = pieceRadiusForStack(stackCount = 3, cellSize = cellSize)
        val quad = pieceRadiusForStack(stackCount = 4, cellSize = cellSize)

        assertTrue(single > cellSize * 0.30f)
        assertTrue(double > cellSize * 0.28f)
        assertTrue(triple > cellSize * 0.26f)
        assertTrue(quad > cellSize * 0.24f)

        assertTrue(single > double)
        assertTrue(double > triple)
        assertTrue(triple > quad)
    }

    @Test
    fun clampOffsetToOverflow_capsAxisOffset() {
        val cellSize = 24f
        val radius = pieceRadiusForStack(stackCount = 1, cellSize = cellSize)
        val unclamped = Offset(20f, -20f)

        val clamped = clampOffsetToOverflow(unclamped, radius, cellSize)
        val expectedMaxOffset = ((cellSize * 0.5f) + (cellSize * 0.18f) - radius)
            .coerceAtLeast(0f)

        assertEquals(expectedMaxOffset, clamped.x, 0.0001f)
        assertEquals(-expectedMaxOffset, clamped.y, 0.0001f)
    }

    @Test
    fun effectiveHitRadius_respectsTouchMinimumAndCellCap() {
        val capped = effectiveHitRadius(
            visualRadius = 6f,
            cellSize = 24f,
            minTouchRadiusPx = 24f
        )
        assertEquals(24f * 0.95f, capped, 0.0001f)

        val boosted = effectiveHitRadius(
            visualRadius = 20f,
            cellSize = 40f,
            minTouchRadiusPx = 24f
        )
        assertEquals(20f * 1.35f, boosted, 0.0001f)
    }

    @Test
    fun stackOffsetFor_staysWithinExpectedSpread() {
        val cellSize = 30f

        val quadOffsets = listOf(
            stackOffsetFor(index = 0, stackCount = 4, cellSize = cellSize),
            stackOffsetFor(index = 1, stackCount = 4, cellSize = cellSize),
            stackOffsetFor(index = 2, stackCount = 4, cellSize = cellSize),
            stackOffsetFor(index = 3, stackCount = 4, cellSize = cellSize)
        )

        val maxAbsX = quadOffsets.maxOf { kotlin.math.abs(it.x) }
        val maxAbsY = quadOffsets.maxOf { kotlin.math.abs(it.y) }

        assertEquals(cellSize * 0.22f, maxAbsX, 0.0001f)
        assertEquals(cellSize * 0.22f, maxAbsY, 0.0001f)
    }

    @Test
    fun rankHitCandidatesForSelection_prioritizesDistanceThenRecencyThenId() {
        val pieceNearOld = Piece(
            id = 2,
            color = PlayerColor.RED,
            position = PiecePosition.MainTrack(5),
            lastMovedAt = 10L
        )
        val pieceNearRecent = Piece(
            id = 1,
            color = PlayerColor.BLUE,
            position = PiecePosition.MainTrack(5),
            lastMovedAt = 50L
        )
        val pieceFarRecent = Piece(
            id = 0,
            color = PlayerColor.GREEN,
            position = PiecePosition.MainTrack(5),
            lastMovedAt = 100L
        )

        val ranked = rankHitCandidatesForSelection(
            listOf(
                HitCandidate(
                    layout = PieceLayout(pieceNearOld, 6 to 6, Offset(10f, 10f), 8f, true),
                    distance = 6f
                ),
                HitCandidate(
                    layout = PieceLayout(pieceNearRecent, 6 to 6, Offset(10f, 10f), 8f, true),
                    distance = 6f
                ),
                HitCandidate(
                    layout = PieceLayout(pieceFarRecent, 6 to 6, Offset(10f, 10f), 8f, true),
                    distance = 9f
                )
            )
        )

        assertEquals(pieceNearRecent, ranked[0].layout.piece)
        assertEquals(pieceNearOld, ranked[1].layout.piece)
        assertEquals(pieceFarRecent, ranked[2].layout.piece)
    }

    @Test
    fun selectBestHitCandidate_matchesRankingRules() {
        val pieceA = Piece(
            id = 2,
            color = PlayerColor.RED,
            position = PiecePosition.MainTrack(8),
            lastMovedAt = 10L
        )
        val pieceB = Piece(
            id = 1,
            color = PlayerColor.BLUE,
            position = PiecePosition.MainTrack(8),
            lastMovedAt = 30L
        )

        val best = selectBestHitCandidate(
            sequenceOf(
                HitCandidate(
                    layout = PieceLayout(pieceA, 5 to 5, Offset(8f, 8f), 8f, true),
                    distance = 7f
                ),
                HitCandidate(
                    layout = PieceLayout(pieceB, 5 to 5, Offset(8f, 8f), 8f, true),
                    distance = 7f
                )
            )
        )

        assertEquals(pieceB, best?.layout?.piece)
    }
}
