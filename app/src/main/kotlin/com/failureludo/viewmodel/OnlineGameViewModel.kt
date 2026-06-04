package com.failureludo.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.failureludo.data.auth.AuthRepository
import com.failureludo.data.online.OnlineGameRepository
import com.failureludo.data.online.OnlineMove
import com.failureludo.data.online.RoomPlayer
import com.failureludo.engine.DeterministicTurnInput
import com.failureludo.engine.GameEngine
import com.failureludo.engine.GameMode
import com.failureludo.engine.GameState
import com.failureludo.engine.PlayerColor
import com.failureludo.engine.PlayerId
import com.failureludo.engine.PlayerType
import com.failureludo.engine.TurnPhase
import com.failureludo.ui.components.TappedCellPieces
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OnlineGameUiState(
    val gameState: GameState? = null,
    val myColor: PlayerColor? = null,
    val roomPlayers: List<RoomPlayer> = emptyList(),
    val isSubmitting: Boolean = false
) {
    val isMyTurn: Boolean
        get() = gameState != null && gameState.currentPlayer.color == myColor && !gameState.isGameOver
    val canRoll: Boolean
        get() = isMyTurn && gameState?.turnPhase == TurnPhase.WAITING_FOR_ROLL && !isSubmitting
    val canSelectPiece: Boolean
        get() = isMyTurn && gameState?.turnPhase == TurnPhase.WAITING_FOR_PIECE_SELECTION && !isSubmitting
}

class OnlineGameViewModel(application: Application) : AndroidViewModel(application) {

    private val authRepo = AuthRepository(application)
    private val onlineRepo = OnlineGameRepository()

    private val _uiState = MutableStateFlow(OnlineGameUiState())
    val uiState: StateFlow<OnlineGameUiState> = _uiState.asStateFlow()

    private val _errors = MutableSharedFlow<String>()
    val errors: SharedFlow<String> = _errors.asSharedFlow()

    private var roomId: String? = null
    private var localGameState: GameState? = null
    private var appliedMoveCount = 0
    private var noMovesJob: Job? = null

    fun initGame(newRoomId: String) {
        if (roomId == newRoomId) return
        roomId = newRoomId

        viewModelScope.launch {
            val room = onlineRepo.fetchRoom(newRoomId).getOrElse {
                _errors.emit("Could not load game. Check your connection.")
                return@launch
            }

            val myUid = authRepo.currentProfile?.uid
            val myRoomPlayer = room.players.find { it.uid == myUid }
            val myColor = myRoomPlayer?.color
                ?.let { runCatching { PlayerColor.valueOf(it) }.getOrNull() }

            // Build initial game state — same on all clients
            val activeColors = room.players
                .mapNotNull { runCatching { PlayerColor.valueOf(it.color) }.getOrNull() }
                .sortedBy { it.ordinal }

            val playerNames = room.players
                .mapNotNull { p ->
                    val color = runCatching { PlayerColor.valueOf(p.color) }.getOrNull() ?: return@mapNotNull null
                    color to p.name
                }
                .toMap()

            val initialState = GameEngine.newGame(
                activeColors = activeColors,
                playerTypes  = activeColors.associateWith { PlayerType.HUMAN },
                playerNames  = playerNames,
                mode         = GameMode.FREE_FOR_ALL
            )

            localGameState = initialState
            _uiState.value = OnlineGameUiState(
                gameState   = initialState,
                myColor     = myColor,
                roomPlayers = room.players
            )

            // Listen to moves from Firestore and apply opponent moves
            launch {
                onlineRepo.listenToMoves(newRoomId).collect { moves ->
                    applyIncomingMoves(moves)
                }
            }
        }
    }

    // ── Local player actions ──────────────────────────────────────────────────

    fun rollDice() {
        val state = localGameState ?: return
        val myColor = _uiState.value.myColor ?: return
        if (!_uiState.value.canRoll) return

        val diceValue = (1..6).random()
        val rolledState = GameEngine.rollDice(state, diceValue)
        localGameState = rolledState
        _uiState.update { it.copy(gameState = rolledState, isSubmitting = true) }

        when (rolledState.turnPhase) {
            TurnPhase.WAITING_FOR_PIECE_SELECTION -> {
                // Let user tap a piece
                _uiState.update { it.copy(isSubmitting = false) }
            }
            TurnPhase.NO_MOVES_AVAILABLE -> {
                noMovesJob = viewModelScope.launch {
                    delay(1_200L)
                    val finalState = GameEngine.advanceNoMoves(rolledState)
                    submitAndApplyLocally(
                        move = buildMove(state, diceValue, pieceId = -1),
                        finalState = finalState
                    )
                }
            }
            TurnPhase.WAITING_FOR_ROLL -> {
                // Consecutive-sixes forfeit — engine already advanced the turn
                submitAndApplyLocally(
                    move = buildMove(state, diceValue, pieceId = -1),
                    finalState = rolledState
                )
            }
            else -> {}
        }
    }

    fun onPieceTapped(tapped: TappedCellPieces) {
        val state = localGameState ?: return
        if (!_uiState.value.canSelectPiece) return

        val piece = tapped.preferredPiece
            ?: tapped.movablePieces.firstOrNull()
            ?: return
        if (piece !in state.movablePieces) return

        val diceValue = state.lastDice?.value ?: return
        val finalState = GameEngine.selectPiece(state, piece)

        submitAndApplyLocally(
            move = buildMove(state, diceValue, pieceId = piece.id),
            finalState = finalState
        )
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    /**
     * Applies the move locally (optimistically) and writes it to Firestore.
     * Increments [appliedMoveCount] BEFORE the write so the Firestore echo is skipped.
     */
    private fun submitAndApplyLocally(move: OnlineMove, finalState: GameState) {
        noMovesJob?.cancel()
        appliedMoveCount++
        localGameState = finalState
        _uiState.update { it.copy(gameState = finalState, isSubmitting = false) }

        val currentRoomId = roomId ?: return
        viewModelScope.launch {
            onlineRepo.writeMove(currentRoomId, move).onFailure {
                _errors.emit("Move could not be submitted. Check your connection.")
            }
        }
    }

    /**
     * Applies moves received from Firestore that have not yet been applied locally.
     * Moves already applied locally (index < appliedMoveCount) are skipped.
     */
    private fun applyIncomingMoves(moves: List<OnlineMove>) {
        val newMoves = moves
            .filter { it.index >= appliedMoveCount }
            .sortedBy { it.index }

        if (newMoves.isEmpty()) return

        var state = localGameState ?: return

        for (move in newMoves) {
            state = try {
                applyMoveToState(state, move)
            } catch (e: Exception) {
                viewModelScope.launch { _errors.emit("Game sync error: ${e.message}") }
                return
            }
            appliedMoveCount++
        }

        localGameState = state
        _uiState.update { it.copy(gameState = state) }
    }

    private fun applyMoveToState(state: GameState, move: OnlineMove): GameState {
        val actorId = PlayerId(move.actorId)
        return if (move.pieceId == -1) {
            GameEngine.applyDeterministicRollOnly(state, actorId, move.diceValue)
        } else {
            val input = DeterministicTurnInput(
                actorId        = actorId,
                movingPlayerId = PlayerId(move.movingPlayerId),
                pieceId        = move.pieceId,
                diceValue      = move.diceValue,
                deferHomeEntry = move.deferHomeEntry
            )
            GameEngine.applyDeterministicTurn(state, input)
        }
    }

    private fun buildMove(state: GameState, diceValue: Int, pieceId: Int) = OnlineMove(
        index          = appliedMoveCount,
        actorId        = state.currentPlayer.id.value,
        movingPlayerId = state.currentPlayer.id.value,
        diceValue      = diceValue,
        pieceId        = pieceId
    )

    override fun onCleared() {
        super.onCleared()
        noMovesJob?.cancel()
    }
}
