package com.failureludo.data.online

import com.failureludo.data.auth.UserProfile
import com.failureludo.engine.PlayerColor
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class OnlineGameRepository {

    private val firestore = Firebase.firestore
    private val rooms = firestore.collection("rooms")

    // ── Room creation ─────────────────────────────────────────────────────────

    suspend fun createRoom(host: UserProfile, maxPlayers: Int): Result<GameRoom> {
        return try {
            val code = generateUniqueCode()
            val hostPlayerData = playerMap(host, color = "", isHost = true)
            val roomData = hashMapOf(
                "roomCode"   to code,
                "status"     to RoomStatus.WAITING.name,
                "maxPlayers" to maxPlayers,
                "hostUid"    to host.uid,
                "players"    to listOf(hostPlayerData),
                "moves"      to emptyList<Any>(),
                "createdAt"  to FieldValue.serverTimestamp()
            )
            val ref = rooms.add(roomData).await()
            Result.success(
                GameRoom(
                    id = ref.id,
                    roomCode = code,
                    status = RoomStatus.WAITING,
                    maxPlayers = maxPlayers,
                    players = listOf(RoomPlayer(host.uid, host.name, "android", "", true)),
                    hostUid = host.uid
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── Room joining ──────────────────────────────────────────────────────────

    suspend fun joinRoom(code: String, player: UserProfile): Result<GameRoom> {
        return try {
            // Query by code, filter status client-side to avoid a composite index requirement
            val snapshot = rooms
                .whereEqualTo("roomCode", code.uppercase().trim())
                .limit(5)
                .get()
                .await()

            val doc = snapshot.documents.find { it.getString("status") == RoomStatus.WAITING.name }
                ?: return Result.failure(Exception("Room not found or already started."))

            val roomId = doc.id
            val maxPlayers = (doc.getLong("maxPlayers") ?: 4).toInt()

            // Atomic join via transaction
            firestore.runTransaction { tx ->
                val roomRef = rooms.document(roomId)
                val fresh = tx.get(roomRef)
                val currentPlayers = fresh.get("players") as? List<*> ?: emptyList<Any>()
                if (currentPlayers.size >= maxPlayers) throw Exception("Room is full.")
                val alreadyJoined = currentPlayers.any { (it as? Map<*, *>)?.get("uid") == player.uid }
                if (!alreadyJoined) {
                    tx.update(roomRef, "players", FieldValue.arrayUnion(playerMap(player, "", false)))
                }
            }.await()

            // Fetch updated room to return
            val updatedDoc = rooms.document(roomId).get().await()
            Result.success(docToRoom(roomId, updatedDoc.data ?: emptyMap()))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── Real-time listener ────────────────────────────────────────────────────

    fun listenToRoom(roomId: String): Flow<GameRoom?> = callbackFlow {
        val ref = rooms.document(roomId)
        val registration: ListenerRegistration = ref.addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(null)
                return@addSnapshotListener
            }
            val data = snapshot?.data
            trySend(if (data != null) docToRoom(roomId, data) else null)
        }
        awaitClose { registration.remove() }
    }

    // ── Game start ────────────────────────────────────────────────────────────

    /** Host calls this. Assigns colors to players in join order and sets status to IN_PROGRESS. */
    suspend fun startGame(roomId: String, hostUid: String): Result<Unit> {
        return try {
            firestore.runTransaction { tx ->
                val ref = rooms.document(roomId)
                val snapshot = tx.get(ref)
                if (snapshot.getString("hostUid") != hostUid) {
                    throw FirebaseFirestoreException(
                        "Only the host can start the game.",
                        FirebaseFirestoreException.Code.PERMISSION_DENIED
                    )
                }
                @Suppress("UNCHECKED_CAST")
                val rawPlayers = snapshot.get("players") as? List<Map<String, Any>> ?: emptyList()
                val colors = PlayerColor.entries.map { it.name }
                val updatedPlayers = rawPlayers.mapIndexed { index, p ->
                    p.toMutableMap().apply { put("color", colors.getOrElse(index) { "" }) }
                }
                tx.update(ref, mapOf("players" to updatedPlayers, "status" to RoomStatus.IN_PROGRESS.name))
            }.await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── Leave room ────────────────────────────────────────────────────────────

    suspend fun leaveRoom(roomId: String, uid: String) {
        try {
            val ref = rooms.document(roomId)
            firestore.runTransaction { tx ->
                val snapshot = tx.get(ref)
                @Suppress("UNCHECKED_CAST")
                val players = snapshot.get("players") as? List<Map<String, Any>> ?: return@runTransaction
                val remaining = players.filter { it["uid"] != uid }

                if (remaining.isEmpty()) {
                    // No one left — delete the room
                    tx.delete(ref)
                } else {
                    val update = mutableMapOf<String, Any>("players" to remaining)
                    // If the host left, promote the next player
                    if (snapshot.getString("hostUid") == uid) {
                        update["hostUid"] = remaining.first()["uid"] as String
                        update["players"] = remaining.mapIndexed { i, p ->
                            p.toMutableMap().apply { put("isHost", i == 0) }
                        }
                    }
                    tx.update(ref, update)
                }
            }.await()
        } catch (_: Exception) { /* best-effort */ }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun playerMap(profile: UserProfile, color: String, isHost: Boolean): Map<String, Any> =
        mapOf(
            "uid"      to profile.uid,
            "name"     to profile.name,
            "platform" to "android",
            "color"    to color,
            "isHost"   to isHost
        )

    @Suppress("UNCHECKED_CAST")
    private fun docToRoom(id: String, data: Map<String, Any>): GameRoom {
        val rawPlayers = data["players"] as? List<Map<String, Any>> ?: emptyList()
        val players = rawPlayers.map { p ->
            RoomPlayer(
                uid      = p["uid"] as? String ?: "",
                name     = p["name"] as? String ?: "Player",
                platform = p["platform"] as? String ?: "android",
                color    = p["color"] as? String ?: "",
                isHost   = p["isHost"] as? Boolean ?: false
            )
        }
        val status = try { RoomStatus.valueOf(data["status"] as? String ?: "") }
        catch (_: Exception) { RoomStatus.WAITING }

        return GameRoom(
            id         = id,
            roomCode   = data["roomCode"] as? String ?: "",
            status     = status,
            maxPlayers = (data["maxPlayers"] as? Long)?.toInt() ?: 4,
            players    = players,
            hostUid    = data["hostUid"] as? String ?: "",
            createdAt  = (data["createdAt"] as? Long) ?: 0L
        )
    }

    // ── Move sync (Phase 4) ───────────────────────────────────────────────────

    suspend fun fetchRoom(roomId: String): Result<GameRoom> {
        return try {
            val doc = rooms.document(roomId).get().await()
            val data = doc.data ?: return Result.failure(Exception("Room not found"))
            Result.success(docToRoom(roomId, data))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun writeMove(roomId: String, move: OnlineMove): Result<Unit> {
        return try {
            val data = mapOf(
                "index"          to move.index,
                "actorId"        to move.actorId,
                "movingPlayerId" to move.movingPlayerId,
                "diceValue"      to move.diceValue,
                "pieceId"        to move.pieceId,
                "deferHomeEntry" to move.deferHomeEntry
            )
            rooms.document(roomId)
                .collection("moves")
                .document(move.index.toString())
                .set(data)
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun listenToMoves(roomId: String): Flow<List<OnlineMove>> = callbackFlow {
        val reg = rooms.document(roomId)
            .collection("moves")
            .orderBy("index")
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                val moves = snapshot.documents.mapNotNull { doc ->
                    val d = doc.data ?: return@mapNotNull null
                    OnlineMove(
                        index          = (d["index"] as? Long)?.toInt() ?: return@mapNotNull null,
                        actorId        = (d["actorId"] as? Long)?.toInt() ?: return@mapNotNull null,
                        movingPlayerId = (d["movingPlayerId"] as? Long)?.toInt() ?: return@mapNotNull null,
                        diceValue      = (d["diceValue"] as? Long)?.toInt() ?: return@mapNotNull null,
                        pieceId        = (d["pieceId"] as? Long)?.toInt() ?: return@mapNotNull null,
                        deferHomeEntry = d["deferHomeEntry"] as? Boolean ?: false
                    )
                }
                trySend(moves)
            }
        awaitClose { reg.remove() }
    }

    /** Generates a 6-char code using unambiguous characters, retrying until unique. */
    private suspend fun generateUniqueCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        repeat(10) {
            val code = (1..6).map { chars.random() }.joinToString("")
            val exists = rooms
                .whereEqualTo("roomCode", code)
                .whereEqualTo("status", RoomStatus.WAITING.name)
                .limit(1)
                .get()
                .await()
                .isEmpty
            if (exists) return code
        }
        // Fallback: use timestamp-based suffix — collision astronomically unlikely
        return "R${System.currentTimeMillis().toString().takeLast(5)}"
    }
}
