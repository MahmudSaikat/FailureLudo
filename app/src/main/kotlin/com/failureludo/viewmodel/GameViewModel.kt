package com.failureludo.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.failureludo.engine.GameEngine
import com.failureludo.engine.GameState
import com.failureludo.engine.Piece
import com.failureludo.engine.PlayerType
import com.failureludo.engine.TurnPhase
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class GameViewModel : ViewModel() {

    // ── Setup ─────────────────────────────────────────────────────────────────

    private val _setupState = MutableStateFlow(SetupState())
    val setupState: StateFlow<SetupState> = _setupState.asStateFlow()

    fun updateSetup(newSetup: SetupState) {
        _setupState.value = newSetup
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
        _gameState.value = newState
        checkForBotTurn()
    }

    fun replayWithSameSetup() {
        startGame()
    }

    fun rollDice() {
        val state = _gameState.value ?: return
        if (state.turnPhase != TurnPhase.WAITING_FOR_ROLL) return

        val newState = GameEngine.rollDice(state)
        _gameState.value = newState

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
        _gameState.value = newState

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
        _gameState.value = newState
        checkForBotTurn()
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
