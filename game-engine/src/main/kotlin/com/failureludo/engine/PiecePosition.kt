package com.failureludo.engine

/**
 * Represents where a single piece is on the board.
 *
 * Main track has 52 cells (0-51).
 * Home column has 5 cells (step 1-5); step 6 means the piece has reached the center = Finished.
 */
sealed class PiecePosition {
    /** Piece is in the home base yard, not yet on the board. */
    object HomeBase : PiecePosition()

    /** Piece is on the shared 52-cell main track. [index] is 0-51. */
    data class MainTrack(val index: Int) : PiecePosition()

    /** Piece is in its colour-specific home column. [step] is 1-5. */
    data class HomeColumn(val step: Int) : PiecePosition()

    /** Piece has reached the center — fully finished. */
    object Finished : PiecePosition()
}
