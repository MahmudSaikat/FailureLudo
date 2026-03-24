package com.failureludo.engine

/**
 * Immutable board constants.
 *
 * The main track has 52 cells (indices 0-51) shared by all players.
 * Each player enters at their own ENTRY_POSITIONS index and travels clockwise.
 * The HOME_COLUMN_ENTRY is the last main-track index a piece visits before
 * branching into the colour-specific home column (5 steps → Finished).
 *
 * Safe squares: pieces on these cells CANNOT be captured.
 *   • All four entry squares (starting squares of each colour)
 *   • The four "starred" squares just before each home-column entrance
 */
object Board {

    const val MAIN_TRACK_SIZE = 52
    const val HOME_COLUMN_STEPS = 5  // steps 1-5; step 6 would be Finished

    /** Main-track index where each colour enters the board. */
    val ENTRY_POSITIONS: Map<PlayerColor, Int> = mapOf(
        PlayerColor.RED    to 0,
        PlayerColor.BLUE   to 13,
        PlayerColor.YELLOW to 26,
        PlayerColor.GREEN  to 39
    )

    /**
     * Main-track index that is the LAST cell before the home column.
     * A piece on this cell with a die value of 1 moves to HomeColumn(1);
     * a die value of 2 → HomeColumn(2), etc. — if it fits within 5 steps.
     */
    val HOME_COLUMN_ENTRY: Map<PlayerColor, Int> = mapOf(
        PlayerColor.RED    to 50,
        PlayerColor.BLUE   to 11,
        PlayerColor.YELLOW to 24,
        PlayerColor.GREEN  to 37
    )

    /**
     * Safe squares on the main track (0-51).
     * Pieces here cannot be captured by opponents.
     */
    val SAFE_SQUARES: Set<Int> = setOf(
        0, 8,   // RED   entry + starred
        13, 21, // BLUE  entry + starred
        26, 34, // YELLOW entry + starred
        39, 47  // GREEN entry + starred
    )

    /**
     * Given a [color]'s [relative] travel distance from entry (0 = entry square),
     * returns the absolute main-track index.
     */
    fun absolutePosition(color: PlayerColor, relative: Int): Int =
        (ENTRY_POSITIONS.getValue(color) + relative) % MAIN_TRACK_SIZE

    /**
     * Given a piece's current absolute [mainTrackIndex] and its [color],
     * returns how many steps it has travelled from its entry point.
     */
    fun relativePosition(color: PlayerColor, mainTrackIndex: Int): Int {
        val entry = ENTRY_POSITIONS.getValue(color)
        return (mainTrackIndex - entry + MAIN_TRACK_SIZE) % MAIN_TRACK_SIZE
    }

    /**
     * Returns how many more main-track steps remain before a piece of [color]
     * reaches the home-column entry (i.e., how many steps until the branch point).
     * A piece exactly on HOME_COLUMN_ENTRY has 0 remaining main-track steps.
     */
    fun stepsToHomeColumnEntry(color: PlayerColor, currentAbsoluteIndex: Int): Int {
        val entryRelative = 0
        val maxRelative = relativePosition(color, HOME_COLUMN_ENTRY.getValue(color))
        val currentRelative = relativePosition(color, currentAbsoluteIndex)
        return (maxRelative - currentRelative + MAIN_TRACK_SIZE) % MAIN_TRACK_SIZE
    }

    fun isSafeSquare(index: Int): Boolean = index in SAFE_SQUARES
}
