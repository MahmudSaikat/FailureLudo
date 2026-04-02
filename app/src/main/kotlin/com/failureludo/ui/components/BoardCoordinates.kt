package com.failureludo.ui.components

import com.failureludo.engine.Piece
import com.failureludo.engine.PiecePosition
import com.failureludo.engine.PlayerColor

/**
 * Maps every board game-position to a (row, col) cell on the 15×15 visual grid.
 *
 * Grid is 0-indexed, rows and cols 0..14.
 * Center cell (finished) = (7, 7).
 */
object BoardCoordinates {

    /** 52 main-track positions → (row, col). */
    val MAIN_TRACK: List<Pair<Int, Int>> = listOf(
        // ──── Red side (positions 0-4): row 6, cols 1-5 ────
        6 to 1, 6 to 2, 6 to 3, 6 to 4, 6 to 5,
        // ──── Up col 6 (positions 5-10): rows 5-0 ────
        5 to 6, 4 to 6, 3 to 6, 2 to 6, 1 to 6, 0 to 6,
        // ──── Top row (positions 11-12): row 0, cols 7-8 ────
        0 to 7, 0 to 8,
        // ──── Down col 8 (positions 13-17): rows 1-5 ────
        1 to 8, 2 to 8, 3 to 8, 4 to 8, 5 to 8,
        // ──── Blue side (positions 18-23): row 6, cols 9-14 ────
        6 to 9, 6 to 10, 6 to 11, 6 to 12, 6 to 13, 6 to 14,
        // ──── Right col 14 (positions 24-25): rows 7-8 ────
        7 to 14, 8 to 14,
        // ──── Yellow side (positions 26-30): row 8, cols 13-9 ────
        8 to 13, 8 to 12, 8 to 11, 8 to 10, 8 to 9,
        // ──── Down col 8 (positions 31-36): rows 9-14 ────
        9 to 8, 10 to 8, 11 to 8, 12 to 8, 13 to 8, 14 to 8,
        // ──── Bottom row (positions 37-38): row 14, cols 7-6 ────
        14 to 7, 14 to 6,
        // ──── Green side (positions 39-43): row 13→9, col 6 ────
        13 to 6, 12 to 6, 11 to 6, 10 to 6, 9 to 6,
        // ──── Left row 8 (positions 44-49): row 8, cols 5-0 ────
        8 to 5, 8 to 4, 8 to 3, 8 to 2, 8 to 1, 8 to 0,
        // ──── Left col 0 (positions 50-51): rows 7-6 ────
        7 to 0, 6 to 0
    )

    /** Home column cells for each colour, step 1-5 → (row, col). */
    val HOME_COLUMNS: Map<PlayerColor, List<Pair<Int, Int>>> = mapOf(
        PlayerColor.RED    to listOf(7 to 1, 7 to 2, 7 to 3, 7 to 4, 7 to 5),  // row 7, L→R
        PlayerColor.BLUE   to listOf(1 to 7, 2 to 7, 3 to 7, 4 to 7, 5 to 7),  // col 7, T→B
        PlayerColor.YELLOW to listOf(7 to 13, 7 to 12, 7 to 11, 7 to 10, 7 to 9), // row 7, R→L
        PlayerColor.GREEN  to listOf(13 to 7, 12 to 7, 11 to 7, 10 to 7, 9 to 7)  // col 7, B→T
    )

    /** The 4 yard spots for each colour's home-base pieces (where un-entered pieces sit). */
    val HOME_YARD_SPOTS: Map<PlayerColor, List<Pair<Int, Int>>> = mapOf(
        PlayerColor.RED    to listOf(1 to 1, 1 to 4, 4 to 1, 4 to 4),
        PlayerColor.BLUE   to listOf(1 to 10, 1 to 13, 4 to 10, 4 to 13),
        PlayerColor.YELLOW to listOf(10 to 10, 10 to 13, 13 to 10, 13 to 13),
        PlayerColor.GREEN  to listOf(10 to 1, 10 to 4, 13 to 1, 13 to 4)
    )

    val CENTER: Pair<Int, Int> = 7 to 7

    /** Resolves a [piece]'s (row, col) on the visual grid. */
    fun cellFor(piece: Piece): Pair<Int, Int>? = when (val pos = piece.position) {
        is PiecePosition.HomeBase    -> HOME_YARD_SPOTS[piece.color]?.getOrNull(piece.id)
        is PiecePosition.MainTrack   -> MAIN_TRACK.getOrNull(pos.index)
        is PiecePosition.HomeColumn  -> HOME_COLUMNS[piece.color]?.getOrNull(pos.step - 1)
        PiecePosition.Finished       -> CENTER
    }
}
