package com.failureludo.engine

/**
 * A single playing piece.
 *
 * @param id         0-3 (the four pieces of a player)
 * @param color      Which player owns this piece
 * @param position   Current position on the board
 */
data class Piece(
    val id: Int,
    val color: PlayerColor,
    val position: PiecePosition = PiecePosition.HomeBase
) {
    val isAtHome: Boolean   get() = position is PiecePosition.HomeBase
    val isFinished: Boolean get() = position is PiecePosition.Finished
    val isActive: Boolean   get() = !isAtHome && !isFinished
}
