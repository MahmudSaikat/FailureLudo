package com.failureludo.engine

/**
 * The four player colors in Ludo, corresponding to the four board positions.
 * Order matches clockwise from top-right: RED (top-left), BLUE (top-right),
 * YELLOW (bottom-right), GREEN (bottom-left).
 */
enum class PlayerColor(
    val displayName: String,
    /** Index 0-51 on the main track where this player's piece enters the board. */
    val entryPosition: Int,
    /** Main track index BEFORE entering the home column (inclusive). */
    val homeColumnEntry: Int
) {
    RED(    displayName = "Red",    entryPosition = 0,  homeColumnEntry = 50),
    BLUE(   displayName = "Blue",   entryPosition = 13, homeColumnEntry = 11),
    YELLOW( displayName = "Yellow", entryPosition = 26, homeColumnEntry = 24),
    GREEN(  displayName = "Green",  entryPosition = 39, homeColumnEntry = 37);

    /** The team this color belongs to in team mode (RED-YELLOW vs BLUE-GREEN). */
    val teamIndex: Int get() = when (this) {
        RED, YELLOW -> 0
        BLUE, GREEN -> 1
    }
}
