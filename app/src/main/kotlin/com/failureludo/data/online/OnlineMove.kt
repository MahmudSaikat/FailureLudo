package com.failureludo.data.online

/** One complete turn: dice roll + optional piece selection. */
data class OnlineMove(
    val index: Int,           // sequential turn index (0-based), used as Firestore doc ID
    val actorId: Int,         // PlayerId.value of the player whose turn it was
    val movingPlayerId: Int,  // PlayerId.value of the piece owner (same as actorId in FFA)
    val diceValue: Int,
    val pieceId: Int,         // -1 = no movable pieces (auto-advance) or consecutive-6s forfeit
    val deferHomeEntry: Boolean = false
)
