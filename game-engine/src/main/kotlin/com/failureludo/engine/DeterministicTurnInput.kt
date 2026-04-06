package com.failureludo.engine

/**
 * Deterministic turn payload used for replay/import pathways.
 *
 * [actorId] is the player whose turn it is.
 * [movingPlayerId] can differ in TEAM shared-dice situations.
 */
data class DeterministicTurnInput(
    val actorId: PlayerId,
    val movingPlayerId: PlayerId = actorId,
    val pieceId: Int,
    val diceValue: Int,
    val deferHomeEntry: Boolean = false
)
