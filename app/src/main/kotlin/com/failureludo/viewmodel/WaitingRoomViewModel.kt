package com.failureludo.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.failureludo.data.auth.AuthRepository
import com.failureludo.data.online.GameRoom
import com.failureludo.data.online.OnlineGameRepository
import com.failureludo.data.online.RoomStatus
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class WaitingRoomState {
    object Loading : WaitingRoomState()
    data class Waiting(val room: GameRoom, val currentUid: String) : WaitingRoomState()
    data class GameStarting(val roomId: String) : WaitingRoomState()
    object Disbanded : WaitingRoomState()
}

class WaitingRoomViewModel(application: Application) : AndroidViewModel(application) {

    private val authRepo = AuthRepository(application)
    private val onlineRepo = OnlineGameRepository()

    private val _state = MutableStateFlow<WaitingRoomState>(WaitingRoomState.Loading)
    val state: StateFlow<WaitingRoomState> = _state.asStateFlow()

    private val _errors = MutableSharedFlow<String>()
    val errors: SharedFlow<String> = _errors.asSharedFlow()

    private var currentRoomId: String? = null

    fun init(roomId: String) {
        if (currentRoomId == roomId) return
        currentRoomId = roomId
        val uid = authRepo.currentProfile?.uid ?: return

        viewModelScope.launch {
            onlineRepo.listenToRoom(roomId).collect { room ->
                when {
                    room == null -> _state.value = WaitingRoomState.Disbanded
                    room.status == RoomStatus.IN_PROGRESS -> _state.value = WaitingRoomState.GameStarting(roomId)
                    else -> _state.value = WaitingRoomState.Waiting(room, uid)
                }
            }
        }
    }

    fun startGame() {
        val roomId = currentRoomId ?: return
        val uid = authRepo.currentProfile?.uid ?: return
        viewModelScope.launch {
            onlineRepo.startGame(roomId, uid).onFailure {
                _errors.emit("Could not start game. Are you the host?")
            }
        }
    }

    fun leaveRoom() {
        val roomId = currentRoomId ?: return
        val uid = authRepo.currentProfile?.uid ?: return
        viewModelScope.launch { onlineRepo.leaveRoom(roomId, uid) }
    }
}
