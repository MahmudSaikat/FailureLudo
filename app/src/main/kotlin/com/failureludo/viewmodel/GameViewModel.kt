package com.failureludo.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.failureludo.data.FeedbackSettings
import com.failureludo.data.GamePreferencesStore
import com.failureludo.data.GameSessionStore
import com.failureludo.engine.GameEngine
import com.failureludo.engine.GameState
import com.failureludo.engine.Piece
import com.failureludo.engine.PlayerType
import com.failureludo.engine.TurnPhase
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class GameViewModel(application: Application) : AndroidViewModel(application) {

    private val sessionStore = GameSessionStore(application.applicationContext)
    private val preferencesStore = GamePreferencesStore(application.applicationContext)

    // ── Setup ─────────────────────────────────────────────────────────────────

    private val _setupState = MutableStateFlow(SetupState())
    val setupState: StateFlow<SetupState> = _setupState.asStateFlow()

    private val _feedbackSettings = MutableStateFlow(FeedbackSettings())
    val feedbackSettings: StateFlow<FeedbackSettings> = _feedbackSettings.asStateFlow()

    private val _isSessionRestored = MutableStateFlow(false)
    val isSessionRestored: StateFlow<Boolean> = _isSessionRestored.asStateFlow()

    init {
        viewModelScope.launch {
            sessionStore.loadSetupState()?.let { restoredSetup ->
                _setupState.value = restoredSetup
            }
            sessionStore.loadGameState()?.let { restoredGame ->
                _gameState.value = restoredGame
            }
            _isSessionRestored.value = true
        }

        viewModelScope.launch {
            preferencesStore.feedbackSettings.collectLatest { settings ->
                _feedbackSettings.value = settings
            }
        }
    }

    fun updateSetup(newSetup: SetupState) {
        _setupState.value = newSetup
        viewModelScope.launch {
            sessionStore.saveSetupState(newSetup)
        }
    }

    fun updateFeedbackSettings(newSettings: FeedbackSettings) {
        _feedbackSettings.value = newSettings
        viewModelScope.launch {
            preferencesStore.updateFeedbackSettings(newSettings)
        }
    }

    // ── Game ──────────────────────────────────────────────────────────────────

    private val _gameState = MutableStateFlow<GameState?>(null)
    val gameState: StateFlow<GameState?> = _gameState.asStateFlow()

    val hasActiveGame: Boolean
        get() = _gameState.value?.let { !it.isGameOver } ?: false

    /** Callback invoked by UI when game transitions to GAME_OVER. */
    var onGameOver: (() -> Unit)? = null

    // ── Actions ───────────────────────────────────────────────────────────────

    fun startGame() {
        val setup = _setupState.value
        val newState = GameEngine.newGame(
            activeColors = setup.activeColors,
            playerTypes  = setup.playerTypes,
            playerNames  = setup.playerNames,
            mode         = setup.mode
        )
        setGameState(newState)
        viewModelScope.launch {
            sessionStore.saveSetupState(setup)
        }
        checkForBotTurn()
    }

    fun replayWithSameSetup() {
        startGame()
    }

    fun rollDice() {
        val state = _gameState.value ?: return
        if (state.turnPhase != TurnPhase.WAITING_FOR_ROLL) return

        val newState = GameEngine.rollDice(state)
        setGameState(newState)

        when (newState.turnPhase) {
            TurnPhase.NO_MOVES_AVAILABLE -> {
                viewModelScope.launch {
                    delay(1_200)
                    handleNoMoves()
                }
            }
            TurnPhase.WAITING_FOR_PIECE_SELECTION -> {
                // If current player is a bot, auto-select
                if (newState.currentPlayer.type == PlayerType.BOT) {
                    viewModelScope.launch {
                        delay(600)
                        botSelectPiece()
                    }
                }
            }
            else -> {}
        }
    }

    fun selectPiece(piece: Piece) {
        val state = _gameState.value ?: return
        if (state.turnPhase != TurnPhase.WAITING_FOR_PIECE_SELECTION) return

        val newState = GameEngine.selectPiece(state, piece)
        setGameState(newState)

        if (newState.isGameOver) {
            onGameOver?.invoke()
            return
        }

        // If turn changed to a bot, trigger bot's dice roll
        checkForBotTurn()
    }

    // ── Private ───────────────────────────────────────────────────────────────

    private fun handleNoMoves() {
        val state = _gameState.value ?: return
        if (state.turnPhase != TurnPhase.NO_MOVES_AVAILABLE) return

        val newState = GameEngine.advanceNoMoves(state)
        setGameState(newState)
        checkForBotTurn()
    }

    private fun setGameState(newState: GameState?) {
        _gameState.value = newState
        viewModelScope.launch {
            sessionStore.saveGameState(newState)
        }
    }

    private fun checkForBotTurn() {
        val state = _gameState.value ?: return
        if (state.turnPhase == TurnPhase.WAITING_FOR_ROLL &&
            state.currentPlayer.type == PlayerType.BOT
        ) {
            viewModelScope.launch {
                delay(800)
                botRollDice()
            }
        }
    }

    private fun botRollDice() {
        val state = _gameState.value ?: return
        if (state.currentPlayer.type != PlayerType.BOT) return
        rollDice()
    }

    private fun botSelectPiece() {
        val state = _gameState.value ?: return
        if (state.turnPhase != TurnPhase.WAITING_FOR_PIECE_SELECTION) return
        val piece = GameEngine.botChoosePiece(state)
        selectPiece(piece)
    }
}
