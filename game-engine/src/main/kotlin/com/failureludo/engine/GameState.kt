package com.failureludo.engine

/** Represents a single dice roll result with any bonus context. */
data class DiceResult(
    val value: Int,
    val rollCount: Int = 1  // consecutive rolls in a single turn (three 6s = forfeit)
)

/** Phase within a turn. */
enum class TurnPhase {
    /** The current player must roll the dice. */
    WAITING_FOR_ROLL,
    /** Dice rolled; player must tap a piece to move (if any movable). */
    WAITING_FOR_PIECE_SELECTION,
    /** No movable pieces; turn will auto-advance. */
    NO_MOVES_AVAILABLE,
    /** Game is over. */
    GAME_OVER
}

/**
 * Complete, immutable snapshot of a game in progress.
 *
 * Every action produces a NEW [GameState]; the engine never mutates in place,
 * making it trivial to serialize, replay, and later sync over a network.
 */
data class GameState(
    val players: List<Player>,
    val mode: GameMode,
    val currentPlayerIndex: Int = 0,
    val turnPhase: TurnPhase = TurnPhase.WAITING_FOR_ROLL,
    val lastDice: DiceResult? = null,
    val movablePieces: List<Piece> = emptyList(),
    val winners: List<PlayerColor>? = null,
    /** History of significant events for display (e.g. "Red captured Green"). */
    val eventLog: List<GameEvent> = emptyList()
) {
    val currentPlayer: Player get() = players[currentPlayerIndex]
    val isGameOver: Boolean   get() = turnPhase == TurnPhase.GAME_OVER
}

/** A notable event that happened during the game. */
sealed class GameEvent {
    data class PieceMoved(val color: PlayerColor, val pieceId: Int) : GameEvent()
    data class PieceEnteredBoard(val color: PlayerColor, val pieceId: Int) : GameEvent()
    data class PieceCaptured(val capturedColor: PlayerColor, val byColor: PlayerColor) : GameEvent()
    data class PieceFinished(val color: PlayerColor, val pieceId: Int) : GameEvent()
    data class PlayerWon(val colors: List<PlayerColor>) : GameEvent()
    data class ExtraRollGranted(val color: PlayerColor, val reason: String) : GameEvent()
    data class TurnSkipped(val color: PlayerColor) : GameEvent()
    data class ConsecutiveSixesForfeit(val color: PlayerColor) : GameEvent()
}
