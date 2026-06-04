package com.failureludo.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.failureludo.data.auth.AuthRepository
import com.failureludo.data.auth.CancelledException
import com.failureludo.data.auth.UserProfile
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class AuthState {
    object Loading : AuthState()
    object SignedOut : AuthState()
    data class SignedIn(val profile: UserProfile) : AuthState()
}

class AuthViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = AuthRepository(application)

    private val _authState = MutableStateFlow<AuthState>(
        if (repo.currentProfile != null) AuthState.SignedIn(repo.currentProfile!!)
        else AuthState.Loading
    )
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _errors = MutableSharedFlow<String>()
    val errors: SharedFlow<String> = _errors.asSharedFlow()

    init {
        viewModelScope.launch {
            repo.authStateFlow.collect { profile ->
                _authState.value = if (profile != null) AuthState.SignedIn(profile)
                else AuthState.SignedOut
            }
        }
    }

    fun signInWithGoogle(activityContext: Context) {
        _authState.value = AuthState.Loading
        viewModelScope.launch {
            repo.signInWithGoogle(activityContext).fold(
                onSuccess = { /* authStateFlow will update _authState */ },
                onFailure = { e ->
                    _authState.value = AuthState.SignedOut
                    if (e !is CancelledException) {
                        _errors.emit("Google sign-in failed. Please try again.")
                    }
                }
            )
        }
    }

    fun signInAsGuest() {
        _authState.value = AuthState.Loading
        viewModelScope.launch {
            repo.signInAsGuest().fold(
                onSuccess = { /* authStateFlow will update _authState */ },
                onFailure = {
                    _authState.value = AuthState.SignedOut
                    _errors.emit("Could not start guest session. Please try again.")
                }
            )
        }
    }

    fun signOut() {
        viewModelScope.launch { repo.signOut() }
    }
}
