package com.failureludo.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.failureludo.data.auth.AuthRepository
import com.failureludo.data.online.GameRoom
import com.failureludo.data.online.OnlineGameRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class LobbyState {
    object Idle : LobbyState()
    object Loading : LobbyState()
    data class RoomReady(val room: GameRoom) : LobbyState()
}

class OnlineLobbyViewModel(application: Application) : AndroidViewModel(application) {

    private val authRepo = AuthRepository(application)
    private val onlineRepo = OnlineGameRepository()

    private val _state = MutableStateFlow<LobbyState>(LobbyState.Idle)
    val state: StateFlow<LobbyState> = _state.asStateFlow()

    private val _errors = MutableSharedFlow<String>()
    val errors: SharedFlow<String> = _errors.asSharedFlow()

    fun createRoom(maxPlayers: Int) {
        val profile = authRepo.currentProfile ?: return
        _state.value = LobbyState.Loading
        viewModelScope.launch {
            onlineRepo.createRoom(profile, maxPlayers).fold(
                onSuccess = { _state.value = LobbyState.RoomReady(it) },
                onFailure = {
                    _state.value = LobbyState.Idle
                    _errors.emit("Could not create room. Check your connection.")
                }
            )
        }
    }

    fun joinRoom(code: String) {
        val profile = authRepo.currentProfile ?: return
        if (code.isBlank()) {
            viewModelScope.launch { _errors.emit("Enter a room code.") }
            return
        }
        _state.value = LobbyState.Loading
        viewModelScope.launch {
            onlineRepo.joinRoom(code, profile).fold(
                onSuccess = { _state.value = LobbyState.RoomReady(it) },
                onFailure = {
                    _state.value = LobbyState.Idle
                    _errors.emit(it.message ?: "Could not join room.")
                }
            )
        }
    }

    fun resetState() {
        _state.value = LobbyState.Idle
    }
}
