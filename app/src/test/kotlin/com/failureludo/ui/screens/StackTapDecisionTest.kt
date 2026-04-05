package com.failureludo.ui.screens

import com.failureludo.engine.Piece
import com.failureludo.engine.PiecePosition
import com.failureludo.engine.PlayerColor
import com.failureludo.ui.components.TappedCellPieces
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StackTapDecisionTest {

    @Test
    fun safeSquare_prefersPreferredMovablePiece() {
        val preferred = Piece(
            id = 0,
            color = PlayerColor.RED,
            position = PiecePosition.MainTrack(0), // safe square
            lastMovedAt = 10L
        )
        val other = Piece(
            id = 1,
            color = PlayerColor.RED,
            position = PiecePosition.MainTrack(0),
            lastMovedAt = 20L
        )

        val decision = resolveStackTapDecision(
            TappedCellPieces(
                allPieces = listOf(preferred, other),
                movablePieces = listOf(preferred, other),
                preferredPiece = preferred
            )
        )

        assertEquals(preferred, decision.autoPiece)
        assertTrue(decision.options.isEmpty())
    }

    @Test
    fun mixedColorStack_prefersPreferredPieceWithoutChooser() {
        val redPiece = Piece(
            id = 0,
            color = PlayerColor.RED,
            position = PiecePosition.MainTrack(5),
            lastMovedAt = 10L
        )
        val bluePiece = Piece(
            id = 0,
            color = PlayerColor.BLUE,
            position = PiecePosition.MainTrack(5),
            lastMovedAt = 50L
        )

        val decision = resolveStackTapDecision(
            TappedCellPieces(
                allPieces = listOf(redPiece, bluePiece),
                movablePieces = listOf(redPiece, bluePiece),
                preferredPiece = bluePiece
            )
        )

        assertEquals(bluePiece, decision.autoPiece)
        assertTrue(decision.options.isEmpty())
    }

    @Test
    fun threePieceLockedStack_returnsSingleAndPairChoices() {
        val pairA = Piece(
            id = 0,
            color = PlayerColor.RED,
            position = PiecePosition.MainTrack(10),
            lastMovedAt = 10L
        )
        val pairB = Piece(
            id = 1,
            color = PlayerColor.RED,
            position = PiecePosition.MainTrack(10),
            lastMovedAt = 11L
        )
        val topSingle = Piece(
            id = 2,
            color = PlayerColor.RED,
            position = PiecePosition.MainTrack(10),
            lastMovedAt = 50L
        )

        val decision = resolveStackTapDecision(
            TappedCellPieces(
                allPieces = listOf(pairA, pairB, topSingle),
                movablePieces = listOf(pairA, pairB, topSingle),
                preferredPiece = pairA
            )
        )

        assertNull(decision.autoPiece)
        assertEquals(2, decision.options.size)
        assertTrue(decision.options.any { it.label == "Move single" && it.piece == topSingle })
        assertTrue(decision.options.any { it.label == "Move pair" && (it.piece == pairA || it.piece == pairB) })
    }
}
