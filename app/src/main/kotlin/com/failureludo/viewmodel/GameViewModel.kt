package com.failureludo.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.failureludo.data.FeedbackSettings
import com.failureludo.data.GamePreferencesStore
import com.failureludo.data.GameSessionStore
import com.failureludo.engine.Board
import com.failureludo.engine.GameEngine
import com.failureludo.engine.GameMode
import com.failureludo.engine.GameRules
import com.failureludo.engine.GameState
import com.failureludo.engine.Piece
import com.failureludo.engine.PiecePosition
import com.failureludo.engine.PlayerColor
import com.failureludo.engine.PlayerType
import com.failureludo.engine.TurnPhase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class GameViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val UNDO_HISTORY_LIMIT = 12
    }

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
            try {
                val restoredSetup = sessionStore.loadSetupState()
                val restoredGame = sessionStore.loadGameState()

                restoredSetup?.let {
                    _setupState.value = restoredSetup
                }

                val validRestoredGame = restoredGame?.takeIf(::isRestorableGameStateSafely)
                if (validRestoredGame != null) {
                    _gameState.value = validRestoredGame
                    checkForBotTurn()
                } else if (restoredGame != null) {
                    // Drop corrupt/incomplete snapshots so Resume is hidden instead of crashing.
                    clearSavedGameSnapshotSafely()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                // Any unexpected restore failure should fail closed, not crash app startup.
                clearSavedGameSnapshotSafely()
            } finally {
                _isSessionRestored.value = true
            }
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

    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()

    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    private val undoStack = ArrayDeque<GameState>()
    private val redoStack = ArrayDeque<GameState>()

    private var noMovesJob: Job? = null
    private var botRollJob: Job? = null
    private var botSelectJob: Job? = null
    private var singleMoveAssistJob: Job? = null

    private val _pendingHomeEntryChoicePiece = MutableStateFlow<Piece?>(null)
    val pendingHomeEntryChoicePiece: StateFlow<Piece?> = _pendingHomeEntryChoicePiece.asStateFlow()

    val hasActiveGame: Boolean
        get() = _gameState.value?.let { !it.isGameOver } ?: false

    /** Callback invoked by UI when game transitions to GAME_OVER. */
    var onGameOver: (() -> Unit)? = null

    // ── Actions ───────────────────────────────────────────────────────────────

    fun startGame() {
        clearPendingAutomationJobs()
        clearHistory()
        _pendingHomeEntryChoicePiece.value = null
        val setup = _setupState.value
        val activeColors = if (setup.mode == com.failureludo.engine.GameMode.TEAM) {
            PlayerColor.entries
        } else {
            setup.activeColors
        }

        val newState = GameEngine.newGame(
            activeColors = activeColors,
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

    fun undoLastAction() {
        val currentState = _gameState.value ?: return
        val previousState = undoStack.removeLastOrNull() ?: return

        clearPendingAutomationJobs()
        redoStack.addLast(currentState)
        updateHistoryFlags()
        setGameState(previousState, recordHistory = false, clearRedo = false)
        checkForBotTurn()
    }

    fun redoLastAction() {
        val nextState = redoStack.removeLastOrNull() ?: return

        val currentState = _gameState.value
        if (currentState != null) {
            undoStack.addLast(currentState)
            trimUndoHistoryIfNeeded()
        }

        clearPendingAutomationJobs()
        updateHistoryFlags()
        setGameState(nextState, recordHistory = false, clearRedo = false)
        checkForBotTurn()
    }

    fun rollDice() {
        clearPendingAutomationJobs()
        val state = _gameState.value ?: return
        if (state.turnPhase != TurnPhase.WAITING_FOR_ROLL) return

        val newState = GameEngine.rollDice(state)
        setGameState(newState)

        when (newState.turnPhase) {
            TurnPhase.NO_MOVES_AVAILABLE -> {
                scheduleNoMovesAdvance()
            }
            TurnPhase.WAITING_FOR_PIECE_SELECTION -> {
                // If current player is a bot, auto-select
                if (newState.currentPlayer.type == PlayerType.BOT) {
                    scheduleBotSelectPiece()
                } else {
                    scheduleSingleMoveAssist(newState)
                }
            }
            else -> {}
        }
    }

    fun selectPiece(piece: Piece) {
        val state = _gameState.value ?: return
        if (state.turnPhase != TurnPhase.WAITING_FOR_PIECE_SELECTION) return

        val diceValue = state.lastDice?.value ?: run {
            // Recover gracefully if persisted phase and dice are out of sync.
            setGameState(
                state.copy(
                    turnPhase = TurnPhase.WAITING_FOR_ROLL,
                    movablePieces = emptyList()
                ),
                recordHistory = false,
                clearRedo = false
            )
            return
        }

        val isHumanTurn = state.currentPlayer.type == PlayerType.HUMAN
        val shouldPromptForHomeEntryChoice = isHumanTurn &&
            GameRules.wouldEnterHomePath(
                piece,
                diceValue,
                piece.color,
                state.players,
                state.mode
            )

        if (shouldPromptForHomeEntryChoice) {
            _pendingHomeEntryChoicePiece.value = piece
            return
        }

        applyPieceSelection(piece, deferHomeEntry = false)
    }

    fun resolveHomeEntryChoice(enterHomePath: Boolean) {
        val piece = _pendingHomeEntryChoicePiece.value ?: return
        _pendingHomeEntryChoicePiece.value = null
        applyPieceSelection(piece, deferHomeEntry = !enterHomePath)
    }

    fun dismissHomeEntryChoice() {
        _pendingHomeEntryChoicePiece.value = null
    }

    fun quickMoveSinglePiece() {
        val state = _gameState.value ?: return
        if (state.turnPhase != TurnPhase.WAITING_FOR_PIECE_SELECTION) return
        if (state.currentPlayer.type != PlayerType.HUMAN) return

        val singlePiece = state.movablePieces.singleOrNull() ?: return
        selectPiece(singlePiece)
    }

    // ── Private ───────────────────────────────────────────────────────────────

    private fun applyPieceSelection(piece: Piece, deferHomeEntry: Boolean) {
        clearPendingAutomationJobs()
        val state = _gameState.value ?: return
        if (state.turnPhase != TurnPhase.WAITING_FOR_PIECE_SELECTION) return
        if (piece !in state.movablePieces) return

        val newState = GameEngine.selectPiece(state, piece, deferHomeEntry)
        setGameState(newState)

        if (newState.isGameOver) {
            onGameOver?.invoke()
            return
        }

        // If turn changed to a bot, trigger bot's dice roll
        checkForBotTurn()
    }

    private fun handleNoMoves() {
        val state = _gameState.value ?: return
        if (state.turnPhase != TurnPhase.NO_MOVES_AVAILABLE) return

        val newState = GameEngine.advanceNoMoves(state)
        setGameState(newState)
        checkForBotTurn()
    }

    private fun setGameState(newState: GameState?) {
        setGameState(newState, recordHistory = true, clearRedo = true)
    }

    private fun setGameState(
        newState: GameState?,
        recordHistory: Boolean,
        clearRedo: Boolean
    ) {
        clearPendingAutomationJobs()

        val currentState = _gameState.value
        if (recordHistory && currentState != null && newState != null && currentState != newState) {
            undoStack.addLast(currentState)
            trimUndoHistoryIfNeeded()
            if (clearRedo) {
                redoStack.clear()
            }
            updateHistoryFlags()
        } else if (recordHistory && clearRedo && redoStack.isNotEmpty()) {
            redoStack.clear()
            updateHistoryFlags()
        }

        val pendingPiece = _pendingHomeEntryChoicePiece.value
        if (pendingPiece != null) {
            val shouldKeepPending = newState != null &&
                newState.turnPhase == TurnPhase.WAITING_FOR_PIECE_SELECTION &&
                pendingPiece in newState.movablePieces &&
                newState.currentPlayer.type == PlayerType.HUMAN

            if (!shouldKeepPending) {
                _pendingHomeEntryChoicePiece.value = null
            }
        }

        _gameState.value = newState
        updateHistoryFlags()
        viewModelScope.launch {
            sessionStore.saveGameState(newState)
        }
    }

    private fun checkForBotTurn() {
        val state = _gameState.value ?: return
        if (state.turnPhase == TurnPhase.WAITING_FOR_ROLL &&
            state.currentPlayer.type == PlayerType.BOT
        ) {
            botRollJob = viewModelScope.launch {
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

    private fun scheduleNoMovesAdvance() {
        noMovesJob = viewModelScope.launch {
            delay(1_200)
            handleNoMoves()
        }
    }

    private fun scheduleBotSelectPiece() {
        botSelectJob = viewModelScope.launch {
            delay(600)
            botSelectPiece()
        }
    }

    private fun scheduleSingleMoveAssist(state: GameState) {
        val singlePiece = singleMoveAssistPieceCandidate(
            state = state,
            settings = _feedbackSettings.value
        ) ?: return

        singleMoveAssistJob = viewModelScope.launch {
            delay(260)
            selectPiece(singlePiece)
        }
    }

    private fun clearPendingAutomationJobs() {
        noMovesJob?.cancel()
        botRollJob?.cancel()
        botSelectJob?.cancel()
        singleMoveAssistJob?.cancel()
        noMovesJob = null
        botRollJob = null
        botSelectJob = null
        singleMoveAssistJob = null
    }

    private fun trimUndoHistoryIfNeeded() {
        while (undoStack.size > UNDO_HISTORY_LIMIT) {
            undoStack.removeFirstOrNull()
        }
    }

    private fun clearHistory() {
        undoStack.clear()
        redoStack.clear()
        updateHistoryFlags()
    }

    private fun updateHistoryFlags() {
        _canUndo.value = undoStack.isNotEmpty()
        _canRedo.value = redoStack.isNotEmpty()
    }

    private suspend fun clearSavedGameSnapshotSafely() {
        runCatching { sessionStore.saveGameState(null) }
    }

}

internal fun singleMoveAssistPieceCandidate(
    state: GameState,
    settings: FeedbackSettings
): Piece? {
    if (!settings.singleMoveAssistEnabled) return null
    if (state.turnPhase != TurnPhase.WAITING_FOR_PIECE_SELECTION) return null
    if (state.currentPlayer.type != PlayerType.HUMAN) return null
    return state.movablePieces.singleOrNull()
}

internal fun isRestorableGameStateSafely(
    state: GameState,
    validator: (GameState) -> Boolean = ::isRestorableGameState
): Boolean = runCatching {
    validator(state)
}.getOrDefault(false)

internal fun isRestorableGameState(state: GameState): Boolean {
    if (state.players.size != PlayerColor.entries.size) return false
    if (state.currentPlayerIndex !in state.players.indices) return false
    if (state.players.map { it.color }.toSet().size != PlayerColor.entries.size) return false

    val playerIds = state.players.map { it.id }
    if (playerIds.distinct().size != state.players.size) return false
    if (state.diceByPlayer.keys != playerIds.toSet()) return false
    if (state.hasEnteredBoardAtLeastOnce.keys != playerIds.toSet()) return false
    if (state.sharedTeamDiceEnabled.any { it !in 0..1 }) return false

    val activePlayers = state.players.filter { it.isActive }
    if (state.mode == GameMode.TEAM) {
        if (activePlayers.size != PlayerColor.entries.size) return false
    } else if (activePlayers.size !in 2..PlayerColor.entries.size) {
        return false
    }

    val current = state.players[state.currentPlayerIndex]
    if (!current.isActive) return false

    val diceSnapshot = state.lastDice
    if (diceSnapshot != null) {
        if (diceSnapshot.value !in 1..6) return false
        if (diceSnapshot.rollCount !in 1..3) return false
    }

    state.players.forEach { player ->
        if (player.pieces.size != 4) return false

        val pieceIds = player.pieces.map { it.id }
        if (pieceIds.distinct().size != player.pieces.size) return false
        if (pieceIds.any { it !in 0..3 }) return false

        player.pieces.forEach { piece ->
            if (piece.color != player.color) return false
            when (val position = piece.position) {
                is PiecePosition.MainTrack -> if (position.index !in 0 until Board.MAIN_TRACK_SIZE) return false
                is PiecePosition.HomeColumn -> if (position.step !in 1..Board.HOME_COLUMN_STEPS) return false
                PiecePosition.HomeBase,
                PiecePosition.Finished -> Unit
            }
        }
    }

    val winnersSnapshot = state.winners
    val activePlayerIds = activePlayers.map { it.id }.toSet()
    if (winnersSnapshot != null && winnersSnapshot.any { it !in activePlayerIds }) return false

    when (state.turnPhase) {
        TurnPhase.WAITING_FOR_ROLL -> {
            if (state.movablePieces.isNotEmpty()) return false
        }
        TurnPhase.WAITING_FOR_PIECE_SELECTION -> {
            val dice = diceSnapshot ?: return false
            val legalMoves = GameRules.movablePiecesForTurn(
                currentPlayer = current,
                diceValue = dice.value,
                allPlayers = state.players,
                mode = state.mode,
                sharedTeamDiceEnabled = state.sharedTeamDiceEnabled
            )
            if (legalMoves.isEmpty()) return false
            if (state.movablePieces.isEmpty()) return false
            val legalSet = legalMoves.toSet()
            if (state.movablePieces.any { it !in legalSet }) return false
        }
        TurnPhase.NO_MOVES_AVAILABLE -> {
            if (diceSnapshot == null) return false
            if (state.movablePieces.isNotEmpty()) return false
        }
        TurnPhase.GAME_OVER -> {
            if (winnersSnapshot.isNullOrEmpty()) return false
        }
    }

    return true
}
