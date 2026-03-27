package com.failureludo.engine

/** Whether a player seat is controlled by a human or the CPU. */
enum class PlayerType { HUMAN, BOT }

/**
 * A player in the game.
 *
 * @param id         Stable internal player identity (1..4)
 * @param color      Identifies this player on the board
 * @param name       Display name (e.g. "Player 1", "Alice")
 * @param type       Human or bot
 * @param pieces     The four pieces belonging to this player
 * @param isActive   False when the seat is empty (3-player game: one seat left empty)
 */
data class Player(
    val id: PlayerId,
    val color: PlayerColor,
    val name: String,
    val type: PlayerType = PlayerType.HUMAN,
    val pieces: List<Piece> = List(4) { id -> Piece(id = id, color = color) },
    val isActive: Boolean = true
) {
    val finishedPieceCount: Int get() = pieces.count { it.isFinished }
    val hasFinished: Boolean    get() = pieces.all { it.isFinished }
    val activePieces: List<Piece> get() = pieces.filter { it.isActive }
    val homePieces: List<Piece>   get() = pieces.filter { it.isAtHome }
}
