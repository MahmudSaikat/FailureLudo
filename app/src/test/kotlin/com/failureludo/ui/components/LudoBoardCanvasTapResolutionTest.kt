package com.failureludo.ui.components

import androidx.compose.ui.geometry.Offset
import com.failureludo.engine.Piece
import com.failureludo.engine.PiecePosition
import com.failureludo.engine.PlayerColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class LudoBoardCanvasTapResolutionTest {

    @Test
    fun cellOnlyRollbackSelection_usesTappedCellInsteadOfNearestCandidate() {
        val left = piece(
            id = 0,
            color = PlayerColor.RED,
            position = PiecePosition.MainTrack(1)
        )
        val right = piece(
            id = 1,
            color = PlayerColor.BLUE,
            position = PiecePosition.MainTrack(2)
        )

        val layouts = listOf(
            PieceLayout(
                piece = left,
                cell = 7 to 7,
                center = Offset(180f, 180f),
                radius = 8f,
                isMovable = true
            ),
            PieceLayout(
                piece = right,
                cell = 7 to 8,
                center = Offset(204f, 180f),
                radius = 8f,
                isMovable = true
            )
        )

        val result = resolveCellOnlyTappedCellPieces(
            tapOffset = Offset(191f, 180f),
            pieceLayouts = layouts,
            cellSize = 24f
        )

        assertNotNull(result)
        assertNull(result?.preferredPiece)
        assertEquals(listOf(left), result?.allPieces)
        assertEquals(listOf(left), result?.movablePieces)
    }

    @Test
    fun adjacentBoundaryTap_prefersNearestMovableCandidate() {
        val left = piece(
            id = 0,
            color = PlayerColor.RED,
            position = PiecePosition.MainTrack(1)
        )
        val right = piece(
            id = 1,
            color = PlayerColor.BLUE,
            position = PiecePosition.MainTrack(2)
        )

        val layouts = listOf(
            PieceLayout(
                piece = left,
                cell = 7 to 7,
                center = Offset(180f, 180f),
                radius = 8f,
                isMovable = true
            ),
            PieceLayout(
                piece = right,
                cell = 7 to 8,
                center = Offset(204f, 180f),
                radius = 8f,
                isMovable = true
            )
        )

        val result = resolveTappedCellPieces(
            tapOffset = Offset(193f, 180f),
            pieceLayouts = layouts,
            cellSize = 24f,
            minTouchRadiusPx = 14f
        )

        assertNotNull(result)
        assertEquals(right, result?.preferredPiece)
        assertEquals(listOf(right), result?.allPieces)
        assertEquals(listOf(right), result?.movablePieces)
    }

    @Test
    fun denseStackTap_returnsSameCellPiecesWithPreferredPiece() {
        val p0 = piece(id = 0, color = PlayerColor.RED, position = PiecePosition.MainTrack(10), lastMovedAt = 1L)
        val p1 = piece(id = 1, color = PlayerColor.RED, position = PiecePosition.MainTrack(10), lastMovedAt = 2L)
        val p2 = piece(id = 2, color = PlayerColor.RED, position = PiecePosition.MainTrack(10), lastMovedAt = 3L)

        val layouts = listOf(
            PieceLayout(
                piece = p0,
                cell = 5 to 5,
                center = Offset(132f, 132f),
                radius = 8f,
                isMovable = true
            ),
            PieceLayout(
                piece = p1,
                cell = 5 to 5,
                center = Offset(136f, 126f),
                radius = 8f,
                isMovable = true
            ),
            PieceLayout(
                piece = p2,
                cell = 5 to 5,
                center = Offset(128f, 136f),
                radius = 8f,
                isMovable = true
            )
        )

        val result = resolveTappedCellPieces(
            tapOffset = Offset(136f, 126f),
            pieceLayouts = layouts,
            cellSize = 24f,
            minTouchRadiusPx = 12f
        )

        assertNotNull(result)
        assertEquals(p1, result?.preferredPiece)
        assertEquals(listOf(p0, p1, p2), result?.allPieces)
        assertEquals(listOf(p0, p1, p2), result?.movablePieces)
    }

    @Test
    fun fallbackCellSelection_isUsedWhenNoCandidateIsWithinHitRadius() {
        val piece = piece(
            id = 0,
            color = PlayerColor.GREEN,
            position = PiecePosition.MainTrack(20)
        )

        val layouts = listOf(
            PieceLayout(
                piece = piece,
                cell = 2 to 2,
                center = Offset(60f, 60f),
                radius = 8f,
                isMovable = true
            )
        )

        val result = resolveTappedCellPieces(
            tapOffset = Offset(71f, 71f),
            pieceLayouts = layouts,
            cellSize = 24f,
            minTouchRadiusPx = 5f
        )

        assertNotNull(result)
        assertNull(result?.preferredPiece)
        assertEquals(listOf(piece), result?.allPieces)
        assertEquals(listOf(piece), result?.movablePieces)
    }

    @Test
    fun returnsNullWhenTappedCellHasNoMovablePieces() {
        val piece = piece(
            id = 0,
            color = PlayerColor.YELLOW,
            position = PiecePosition.MainTrack(30)
        )

        val layouts = listOf(
            PieceLayout(
                piece = piece,
                cell = 3 to 3,
                center = Offset(84f, 84f),
                radius = 8f,
                isMovable = false
            )
        )

        val result = resolveTappedCellPieces(
            tapOffset = Offset(83f, 83f),
            pieceLayouts = layouts,
            cellSize = 24f,
            minTouchRadiusPx = 14f
        )

        assertNull(result)
    }

    private fun piece(
        id: Int,
        color: PlayerColor,
        position: PiecePosition,
        lastMovedAt: Long = 0L
    ): Piece {
        return Piece(
            id = id,
            color = color,
            position = position,
            lastMovedAt = lastMovedAt
        )
    }
}
