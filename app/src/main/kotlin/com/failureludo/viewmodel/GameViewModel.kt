package com.failureludo.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.failureludo.data.FeedbackSettings
import com.failureludo.data.GamePreferencesStore
import com.failureludo.data.GameSessionStore
import com.failureludo.data.history.FlnDocument
import com.failureludo.data.history.FlnGameStatus
import com.failureludo.data.history.FlnMove
import com.failureludo.data.history.FlnMoveRecorder
import com.failureludo.data.history.FlnPlayer
import com.failureludo.data.history.FlnReplayApplier
import com.failureludo.data.history.FlnReplayResult
import com.failureludo.data.history.GameHistoryImportResult
import com.failureludo.data.history.GameHistoryRecord
import com.failureludo.data.history.GameHistoryRepository
import com.failureludo.data.history.GameHistoryStore
import com.failureludo.data.history.LocalGameHistoryRepository
import com.failureludo.engine.Board
import com.failureludo.engine.GameEngine
import com.failureludo.engine.GameMode
import com.failureludo.engine.GameRules
import com.failureludo.engine.GameState
import com.failureludo.engine.Piece
import com.failureludo.engine.PiecePosition
import com.failureludo.engine.PlayerColor
import com.failureludo.engine.PlayerId
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
import java.util.UUID

class GameViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val UNDO_HISTORY_LIMIT = 12
        private const val FLN_VERSION = "1.0"
        private const val RULESET_VERSION = "2026.04"
        private const val TURN_TRANSITION_PADDING_MS = 280L
        private const val BOT_ROLL_DELAY_MS = 320L
    }

    private val sessionStore = GameSessionStore(application.applicationContext)
    private val preferencesStore = GamePreferencesStore(application.applicationContext)
    private val historyRepository: GameHistoryRepository =
        LocalGameHistoryRepository(GameHistoryStore(application.applicationContext))

    // ── Setup ─────────────────────────────────────────────────────────────────

    private val _setupState = MutableStateFlow(SetupState())
    val setupState: StateFlow<SetupState> = _setupState.asStateFlow()

    private val _feedbackSettings = MutableStateFlow(FeedbackSettings())
    val feedbackSettings: StateFlow<FeedbackSettings> = _feedbackSettings.asStateFlow()

    private val _isSessionRestored = MutableStateFlow(false)
    val isSessionRestored: StateFlow<Boolean> = _isSessionRestored.asStateFlow()

    private val _historyRecords = MutableStateFlow<List<GameHistoryRecord>>(emptyList())
    val historyRecords: StateFlow<List<GameHistoryRecord>> = _historyRecords.asStateFlow()

    private val _replayUiState = MutableStateFlow(ReplayUiState())
    val replayUiState: StateFlow<ReplayUiState> = _replayUiState.asStateFlow()

    private var currentHistoryDocument: FlnDocument? = null
    private var currentHistoryGameId: String? = null
    private var skipNextHistoryPersistence: Boolean = false
    private var replayBaseState: GameState? = null
    private var replayMoves: List<FlnMove> = emptyList()

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
                refreshHistoryRecordsInternal()
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
    private var turnTransitionReleaseJob: Job? = null
    private var isBoardMovementAnimationActive: Boolean = false

    private val _isTurnTransitionLocked = MutableStateFlow(false)
    val isTurnTransitionLocked: StateFlow<Boolean> = _isTurnTransitionLocked.asStateFlow()

    private val _pendingHomeEntryChoicePiece = MutableStateFlow<Piece?>(null)
    val pendingHomeEntryChoicePiece: StateFlow<Piece?> = _pendingHomeEntryChoicePiece.asStateFlow()

    val hasActiveGame: Boolean
        get() = if (_replayUiState.value.isReplayMode) {
            false
        } else {
            _gameState.value?.let { !it.isGameOver } ?: false
        }

    /** Callback invoked by UI when game transitions to GAME_OVER. */
    var onGameOver: (() -> Unit)? = null

    // ── Actions ───────────────────────────────────────────────────────────────

    fun startGame() {
        clearPendingAutomationJobs()
        resetTurnTransitionLock()
        clearReplayModeInternal()
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

        val createdAt = System.currentTimeMillis()
        val document = createInitialHistoryDocument(
            setup = setup,
            state = newState,
            createdAtEpochMs = createdAt,
            gameId = UUID.randomUUID().toString()
        )
        currentHistoryGameId = document.gameId
        currentHistoryDocument = document
        skipNextHistoryPersistence = true

        setGameState(newState)
        viewModelScope.launch {
            sessionStore.saveSetupState(setup)
            historyRepository.upsertPlayable(document)
            refreshHistoryRecordsInternal()
        }
        checkForBotTurn()
    }

    fun replayWithSameSetup() {
        startGame()
    }

    fun undoLastAction() {
        if (isReplayModeActive()) return
        val currentState = _gameState.value ?: return
        val previousState = undoStack.removeLastOrNull() ?: return

        clearPendingAutomationJobs()
        resetTurnTransitionLock()
        redoStack.addLast(currentState)
        updateHistoryFlags()
        setGameState(previousState, recordHistory = false, clearRedo = false)
        checkForBotTurn()
    }

    fun redoLastAction() {
        if (isReplayModeActive()) return
        val nextState = redoStack.removeLastOrNull() ?: return

        val currentState = _gameState.value
        if (currentState != null) {
            undoStack.addLast(currentState)
            trimUndoHistoryIfNeeded()
        }

        clearPendingAutomationJobs()
        resetTurnTransitionLock()
        updateHistoryFlags()
        setGameState(nextState, recordHistory = false, clearRedo = false)
        checkForBotTurn()
    }

    fun updateMovementAnimationState(isActive: Boolean) {
        if (isBoardMovementAnimationActive == isActive) return
        isBoardMovementAnimationActive = isActive

        if (isActive) {
            turnTransitionReleaseJob?.cancel()
            turnTransitionReleaseJob = null
            return
        }

        if (_isTurnTransitionLocked.value) {
            scheduleTurnTransitionUnlock()
        }
    }

    fun rollDice() {
        if (isReplayModeActive()) return
        if (_isTurnTransitionLocked.value) return
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
        if (isReplayModeActive()) return
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
        if (isReplayModeActive()) return
        val piece = _pendingHomeEntryChoicePiece.value ?: return
        _pendingHomeEntryChoicePiece.value = null
        applyPieceSelection(piece, deferHomeEntry = !enterHomePath)
    }

    fun dismissHomeEntryChoice() {
        if (isReplayModeActive()) return
        _pendingHomeEntryChoicePiece.value = null
    }

    fun quickMoveSinglePiece() {
        if (isReplayModeActive()) return
        val state = _gameState.value ?: return
        if (state.turnPhase != TurnPhase.WAITING_FOR_PIECE_SELECTION) return
        if (state.currentPlayer.type != PlayerType.HUMAN) return

        val singlePiece = state.movablePieces.singleOrNull() ?: return
        selectPiece(singlePiece)
    }

    fun refreshHistoryRecords() {
        viewModelScope.launch {
            refreshHistoryRecordsInternal()
        }
    }

    fun deleteHistoryRecord(gameId: String) {
        viewModelScope.launch {
            historyRepository.delete(gameId)
            if (currentHistoryGameId == gameId) {
                currentHistoryGameId = null
                currentHistoryDocument = null
                clearReplayModeInternal()
            }
            refreshHistoryRecordsInternal()
        }
    }

    fun exportHistoryRecord(gameId: String, onResult: (String?) -> Unit = {}) {
        viewModelScope.launch {
            val exported = historyRepository.exportPlayableFln(gameId)
            onResult(exported)
        }
    }

    fun exportHistoryRecordToUri(gameId: String, uri: Uri, onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val exported = historyRepository.exportPlayableFln(gameId)
            val success = if (exported == null) {
                false
            } else {
                writeTextToUri(uri, exported)
            }
            onResult(success)
        }
    }

    fun importFlnText(text: String, onResult: (GameHistoryImportResult) -> Unit = {}) {
        viewModelScope.launch {
            val result = historyRepository.importFln(text)
            refreshHistoryRecordsInternal()
            onResult(result)
        }
    }

    fun importFlnFromUri(uri: Uri, onResult: (GameHistoryImportResult) -> Unit = {}) {
        viewModelScope.launch {
            val text = readTextFromUri(uri)
            if (text.isNullOrBlank()) {
                onResult(GameHistoryImportResult.Invalid("Selected file was empty or unreadable."))
                return@launch
            }

            val result = historyRepository.importFln(text)
            refreshHistoryRecordsInternal()
            onResult(result)
        }
    }

    fun openHistoryRecord(gameId: String, onResult: (Boolean) -> Unit = {}) {
        openHistoryRecordInternal(
            gameId = gameId,
            mode = HistoryOpenMode.RESUME,
            onResult = onResult
        )
    }

    fun openHistoryRecordForReplay(gameId: String, onResult: (Boolean) -> Unit = {}) {
        openHistoryRecordInternal(
            gameId = gameId,
            mode = HistoryOpenMode.REPLAY,
            onResult = onResult
        )
    }

    fun replayStepBackward() {
        val replay = _replayUiState.value
        if (!replay.isReplayMode) return
        if (replay.currentPly <= 0) return
        applyReplayCursor(replay.currentPly - 1)
    }

    fun replayStepForward() {
        val replay = _replayUiState.value
        if (!replay.isReplayMode) return
        if (replay.currentPly >= replay.totalPly) return
        applyReplayCursor(replay.currentPly + 1)
    }

    fun replayJumpToStart() {
        val replay = _replayUiState.value
        if (!replay.isReplayMode) return
        applyReplayCursor(0)
    }

    fun replayJumpToEnd() {
        val replay = _replayUiState.value
        if (!replay.isReplayMode) return
        applyReplayCursor(replay.totalPly)
    }

    fun replayJumpToPly(targetPly: Int) {
        val replay = _replayUiState.value
        if (!replay.isReplayMode) return
        applyReplayCursor(targetPly)
    }

    fun exitReplayMode() {
        clearReplayModeInternal()
    }

    // ── Private ───────────────────────────────────────────────────────────────

    private fun applyPieceSelection(piece: Piece, deferHomeEntry: Boolean) {
        if (isReplayModeActive()) return
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

        if (newState.moveCounter > state.moveCounter) {
            beginTurnTransitionLock()
        }

        // If turn changed to a bot, trigger bot's dice roll
        checkForBotTurn()
    }

    private fun handleNoMoves() {
        if (isReplayModeActive()) return
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
            if (recordHistory) {
                persistHistoryTransition(
                    previousState = currentState,
                    newState = newState
                )
            }
        }
    }

    private fun checkForBotTurn() {
        if (isReplayModeActive()) return
        if (_isTurnTransitionLocked.value) return
        val state = _gameState.value ?: return
        if (state.turnPhase == TurnPhase.WAITING_FOR_ROLL &&
            state.currentPlayer.type == PlayerType.BOT
        ) {
            botRollJob = viewModelScope.launch {
                delay(BOT_ROLL_DELAY_MS)
                botRollDice()
            }
        }
    }

    private fun beginTurnTransitionLock() {
        _isTurnTransitionLocked.value = true
        turnTransitionReleaseJob?.cancel()
        turnTransitionReleaseJob = null
        if (!isBoardMovementAnimationActive) {
            scheduleTurnTransitionUnlock()
        }
    }

    private fun scheduleTurnTransitionUnlock() {
        turnTransitionReleaseJob?.cancel()
        turnTransitionReleaseJob = viewModelScope.launch {
            delay(TURN_TRANSITION_PADDING_MS)
            if (!_isTurnTransitionLocked.value) return@launch
            if (isBoardMovementAnimationActive) return@launch
            _isTurnTransitionLocked.value = false
            checkForBotTurn()
        }
    }

    private fun resetTurnTransitionLock() {
        _isTurnTransitionLocked.value = false
        turnTransitionReleaseJob?.cancel()
        turnTransitionReleaseJob = null
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

    private suspend fun refreshHistoryRecordsInternal() {
        val records = historyRepository.listRecords()
            .sortedByDescending { it.updatedAtEpochMs }
        _historyRecords.value = records
    }

    private fun openHistoryRecordInternal(
        gameId: String,
        mode: HistoryOpenMode,
        onResult: (Boolean) -> Unit
    ) {
        viewModelScope.launch {
            val record = historyRepository.findRecord(gameId)
            val document = record?.playableDocument
            if (document == null) {
                onResult(false)
                return@launch
            }

            val restoredSetup = setupStateFromDocument(document)
            val initialState = initialStateFromSetup(restoredSetup)
            val restoredState = when (mode) {
                HistoryOpenMode.RESUME -> restoreStateFromDocument(document, restoredSetup)
                HistoryOpenMode.REPLAY -> initialState
            }

            if (restoredState == null) {
                onResult(false)
                return@launch
            }

            clearPendingAutomationJobs()
            resetTurnTransitionLock()
            clearHistory()
            _pendingHomeEntryChoicePiece.value = null
            _setupState.value = restoredSetup

            currentHistoryGameId = document.gameId
            currentHistoryDocument = document
            skipNextHistoryPersistence = true

            if (mode == HistoryOpenMode.REPLAY) {
                replayBaseState = initialState
                replayMoves = document.moves
                updateReplayUiState(
                    gameId = document.gameId,
                    currentPly = 0,
                    totalPly = replayMoves.size
                )
            } else {
                clearReplayModeInternal()
            }

            setGameState(
                restoredState,
                recordHistory = false,
                clearRedo = true
            )
            sessionStore.saveSetupState(restoredSetup)
            if (mode == HistoryOpenMode.RESUME) {
                checkForBotTurn()
            }
            onResult(true)
        }
    }

    private fun createInitialHistoryDocument(
        setup: SetupState,
        state: GameState,
        createdAtEpochMs: Long,
        gameId: String
    ): FlnDocument {
        val activePlayers = state.players.filter { it.isActive }
        val tags = mutableMapOf<String, String>()
        tags["Mode"] = setup.mode.name
        tags["ActivePlayerIds"] = activePlayers.joinToString(",") { it.id.value.toString() }
        state.players.forEach { player ->
            tags["PlayerType.${player.id.value}"] = player.type.name
        }

        return FlnDocument(
            flnVersion = FLN_VERSION,
            rulesetVersion = RULESET_VERSION,
            gameId = gameId,
            status = FlnGameStatus.ACTIVE,
            createdAtEpochMs = createdAtEpochMs,
            updatedAtEpochMs = createdAtEpochMs,
            players = activePlayers.map { player ->
                FlnPlayer(id = player.id, name = player.name)
            },
            moves = emptyList(),
            tags = tags
        )
    }

    private suspend fun persistHistoryTransition(previousState: GameState?, newState: GameState?) {
        if (skipNextHistoryPersistence) {
            skipNextHistoryPersistence = false
            return
        }

        val currentDocument = currentHistoryDocument ?: return
        if (previousState == null || newState == null || previousState == newState) return

        val nextPly = currentDocument.moves.size + 1
        val derivedMove = FlnMoveRecorder.deriveMove(
            before = previousState,
            after = newState,
            ply = nextPly
        )

        val updatedStatus = if (newState.isGameOver) FlnGameStatus.FINISHED else FlnGameStatus.ACTIVE
        val updatedAt = System.currentTimeMillis()

        val nextDocument = when {
            derivedMove != null -> currentDocument.copy(
                moves = currentDocument.moves + derivedMove,
                status = updatedStatus,
                updatedAtEpochMs = updatedAt
            )

            currentDocument.status != updatedStatus -> currentDocument.copy(
                status = updatedStatus,
                updatedAtEpochMs = updatedAt
            )

            else -> currentDocument
        }

        if (nextDocument != currentDocument) {
            currentHistoryDocument = nextDocument
            currentHistoryGameId = nextDocument.gameId
            historyRepository.upsertPlayable(nextDocument)
            refreshHistoryRecordsInternal()
        }
    }

    private fun setupStateFromDocument(document: FlnDocument): SetupState {
        val currentSetup = _setupState.value
        val defaultNames = defaultPlayerNames().toMutableMap()
        val activeColors = mutableListOf<PlayerColor>()

        document.players.forEach { player ->
            val color = playerColorFromId(player.id) ?: return@forEach
            activeColors += color
            defaultNames[color] = player.name
        }

        val mode = document.tags["Mode"]
            ?.let { runCatching { GameMode.valueOf(it) }.getOrNull() }
            ?: GameMode.FREE_FOR_ALL

        val playerTypes = PlayerColor.entries.associateWith { color ->
            val id = PlayerId(color.ordinal + 1)
            val tagKey = "PlayerType.${id.value}"
            document.tags[tagKey]
                ?.let { raw -> runCatching { PlayerType.valueOf(raw) }.getOrNull() }
                ?: PlayerType.HUMAN
        }

        val normalizedActiveColors = when {
            mode == GameMode.TEAM -> PlayerColor.entries
            activeColors.isEmpty() -> currentSetup.activeColors
            else -> activeColors.distinct()
        }

        return SetupState(
            activeColors = normalizedActiveColors,
            playerTypes = playerTypes,
            playerNames = defaultNames,
            playerColors = currentSetup.playerColors,
            mode = mode
        )
    }

    private fun restoreStateFromDocument(document: FlnDocument, setup: SetupState): GameState? {
        val initial = initialStateFromSetup(setup)

        return when (val replay = FlnReplayApplier.replayMoves(initial, document.moves)) {
            is FlnReplayResult.Success -> replay.finalState
            is FlnReplayResult.Failure -> null
        }
    }

    private fun initialStateFromSetup(setup: SetupState): GameState {
        val activeColors = if (setup.mode == GameMode.TEAM) {
            PlayerColor.entries
        } else {
            setup.activeColors
        }

        return GameEngine.newGame(
            activeColors = activeColors,
            playerTypes = setup.playerTypes,
            playerNames = setup.playerNames,
            mode = setup.mode
        )
    }

    private fun applyReplayCursor(targetPly: Int) {
        val replay = _replayUiState.value
        if (!replay.isReplayMode) return

        val baseState = replayBaseState ?: return
        val boundedTarget = targetPly.coerceIn(0, replayMoves.size)

        val replayedState = if (boundedTarget == 0) {
            baseState
        } else {
            when (val replayResult = FlnReplayApplier.replayMoves(baseState, replayMoves.take(boundedTarget))) {
                is FlnReplayResult.Success -> replayResult.finalState
                is FlnReplayResult.Failure -> return
            }
        }

        setGameState(
            replayedState,
            recordHistory = false,
            clearRedo = true
        )

        updateReplayUiState(
            gameId = replay.gameId,
            currentPly = boundedTarget,
            totalPly = replayMoves.size
        )
    }

    private fun updateReplayUiState(gameId: String?, currentPly: Int, totalPly: Int) {
        val boundedTotal = totalPly.coerceAtLeast(0)
        val boundedCurrent = currentPly.coerceIn(0, boundedTotal)
        _replayUiState.value = ReplayUiState(
            isReplayMode = true,
            gameId = gameId,
            currentPly = boundedCurrent,
            totalPly = boundedTotal
        )
    }

    private fun clearReplayModeInternal() {
        replayBaseState = null
        replayMoves = emptyList()
        _replayUiState.value = ReplayUiState()
    }

    private fun isReplayModeActive(): Boolean = _replayUiState.value.isReplayMode

    private fun readTextFromUri(uri: Uri): String? {
        val resolver = getApplication<Application>().contentResolver
        return runCatching {
            resolver.openInputStream(uri)
                ?.bufferedReader(Charsets.UTF_8)
                ?.use { it.readText() }
        }.getOrNull()
    }

    private fun writeTextToUri(uri: Uri, text: String): Boolean {
        val resolver = getApplication<Application>().contentResolver
        return runCatching {
            resolver.openOutputStream(uri)
                ?.bufferedWriter(Charsets.UTF_8)
                ?.use { it.write(text) } != null
        }.getOrDefault(false)
    }

    private fun playerColorFromId(playerId: PlayerId): PlayerColor? {
        return PlayerColor.entries.firstOrNull { color -> color.ordinal + 1 == playerId.value }
    }

    private suspend fun clearSavedGameSnapshotSafely() {
        runCatching { sessionStore.saveGameState(null) }
    }

    private enum class HistoryOpenMode {
        RESUME,
        REPLAY
    }

    override fun onCleared() {
        clearPendingAutomationJobs()
        resetTurnTransitionLock()
        super.onCleared()
    }

}

data class ReplayUiState(
    val isReplayMode: Boolean = false,
    val gameId: String? = null,
    val currentPly: Int = 0,
    val totalPly: Int = 0
) {
    val canStepBackward: Boolean
        get() = isReplayMode && currentPly > 0

    val canStepForward: Boolean
        get() = isReplayMode && currentPly < totalPly
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
