package com.failureludo.data.online

enum class RoomStatus { WAITING, IN_PROGRESS, FINISHED }

data class RoomPlayer(
    val uid: String,
    val name: String,
    val platform: String,  // "android" or "web"
    val color: String,     // "RED", "BLUE", "YELLOW", "GREEN" — assigned at game start
    val isHost: Boolean
)

data class GameRoom(
    val id: String,        // Firestore document ID
    val roomCode: String,
    val status: RoomStatus,
    val maxPlayers: Int,
    val players: List<RoomPlayer>,
    val hostUid: String,
    val createdAt: Long = 0L
) {
    val isFull: Boolean get() = players.size >= maxPlayers
    val hasMinPlayers: Boolean get() = players.size >= 2
}
