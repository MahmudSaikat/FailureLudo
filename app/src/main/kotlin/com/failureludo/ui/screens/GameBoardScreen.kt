package com.failureludo.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import android.content.res.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.failureludo.ui.tabletop.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.failureludo.data.FeedbackSettings
import com.failureludo.engine.*
import com.failureludo.feedback.FeedbackEvent
import com.failureludo.feedback.GameFeedbackManager
import com.failureludo.ui.components.BoardCoordinates
import com.failureludo.ui.components.TappedCellPieces
import com.failureludo.ui.theme.*
import com.failureludo.viewmodel.GameViewModel
import com.failureludo.viewmodel.ReplayUiState
import kotlin.math.roundToInt

internal data class BoardLayoutSizing(
    val diceSize: Dp,
    val railHeight: Dp,
    val boardSize: Dp
)


private data class PieceAnimationPlan(
    val paths: List<Pair<Pair<PlayerColor, Int>, List<Pair<Int, Int>>>> = emptyList(),
    val movingPieceStepCount: Int = 0,
    val hasCapture: Boolean = false
)

internal fun computeBoardLayoutSizing(maxWidth: Dp, maxHeight: Dp): BoardLayoutSizing {
    val boardFloorByWidthClass = when {
        maxWidth < 600.dp -> 220.dp
        maxWidth < 840.dp -> 260.dp
        else -> 320.dp
    }
    val dicePadding = 24.dp
    val diceMin = 18.dp
    val diceMax = 56.dp
    val diceSizeByWidth = (maxWidth * 0.12f).coerceIn(42.dp, diceMax)
    val diceSizeByDesiredBoard = (((maxHeight - boardFloorByWidthClass) / 2f) - dicePadding)
        .coerceIn(diceMin, diceMax)
    val diceSizeByAbsoluteHeight = ((maxHeight / 2f) - dicePadding)
        .coerceIn(diceMin, diceMax)
    val diceSize = minOf(diceSizeByWidth, diceSizeByDesiredBoard, diceSizeByAbsoluteHeight)
    val railHeight = diceSize + dicePadding
    val boardHeightBudget = (maxHeight - railHeight * 2).coerceAtLeast(0.dp)
    val boardSize = minOf(maxWidth, boardHeightBudget)

    return BoardLayoutSizing(
        diceSize = diceSize,
        railHeight = railHeight,
        boardSize = boardSize
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameBoardScreen(
    viewModel: GameViewModel,
    onGameOver: () -> Unit,
    onQuit: () -> Unit
) {
    val state by viewModel.gameState.collectAsState()
    val presentedRoll by viewModel.presentedRoll.collectAsState()
    val isDiceRolling by viewModel.isDiceRolling.collectAsState()
    var finishRequested by remember { mutableStateOf(false) }
    val setup by viewModel.setupState.collectAsState()
    val feedbackSettings by viewModel.feedbackSettings.collectAsState()
    val pendingHomeEntryChoicePiece by viewModel.pendingHomeEntryChoicePiece.collectAsState()
    val canUndo by viewModel.canUndo.collectAsState()
    val canRedo by viewModel.canRedo.collectAsState()
    val replayUiState by viewModel.replayUiState.collectAsState()
    val isTurnTransitionLocked by viewModel.isTurnTransitionLocked.collectAsState()
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val feedbackManager = remember(context) { GameFeedbackManager(context, soundPrefix = "tabletop_") }

    DisposableEffect(feedbackManager) {
        onDispose {
            feedbackManager.release()
        }
    }

    // Wire the game-over callback once
    LaunchedEffect(Unit) {
        viewModel.onGameOver = { finishRequested = true }
    }

    if (state == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val gameState = state!!
    val latestFeedbackSettings by rememberUpdatedState(feedbackSettings)

    var showQuitDialog by remember { mutableStateOf(false) }
    var showFeedbackDialog by remember { mutableStateOf(false) }
    var pendingStackChoice by remember { mutableStateOf<StackMoveChoiceState?>(null) }

    val animationFromCells = remember { mutableStateMapOf<Pair<PlayerColor, Int>, Pair<Int, Int>>() }
    val movementProgress = remember { Animatable(1f) }
    var previousEventSize by remember { mutableIntStateOf(gameState.eventLog.size) }
    var replayAutoplayEnabled by remember { mutableStateOf(false) }
    var replaySpeedIndex by remember { mutableIntStateOf(0) }
    val animatedPieceCells = remember { mutableStateMapOf<Pair<PlayerColor, Int>, Pair<Int, Int>>() }
    var previousPiecePositions by remember { mutableStateOf<Map<Pair<PlayerColor, Int>, PiecePosition>?>(null) }
    var previousMoveCounter by remember { mutableLongStateOf(-1L) }

    val replaySpeedSettings = remember {
        listOf(
            1_100L to "1x",
            760L to "1.5x",
            430L to "2.5x"
        )
    }
    val replayDelayMs = replaySpeedSettings[replaySpeedIndex].first
    val replaySpeedLabel = replaySpeedSettings[replaySpeedIndex].second

    val precomputedAnimationPlan = remember(
        gameState.players,
        gameState.moveCounter,
        previousPiecePositions,
        previousMoveCounter
    ) {
        buildAnimationPlan(
            state = gameState,
            previousPositions = previousPiecePositions,
            previousMoveCounter = previousMoveCounter
        )
    }

    val precomputedAnimationPaths = precomputedAnimationPlan.paths
    val movingPieceStepCount = precomputedAnimationPlan.movingPieceStepCount
    val hasCaptureDuringAnimation = precomputedAnimationPlan.hasCapture
    val isMovementAnimationActive = precomputedAnimationPaths.isNotEmpty() || animatedPieceCells.isNotEmpty()
    val isTurnInputBlocked = isMovementAnimationActive || isTurnTransitionLocked || isDiceRolling
    BackHandler { showQuitDialog = true }
    LaunchedEffect(finishRequested, isMovementAnimationActive) {
        if (finishRequested && !isMovementAnimationActive) onGameOver()
    }
    DisposableEffect(viewModel) {
        onDispose { viewModel.onGameOver = null; viewModel.updateMovementAnimationState(false) }
    }

    val firstFrameAnimationCells = remember(precomputedAnimationPaths) {
        precomputedAnimationPaths.associate { (key, cells) -> key to cells.first() }
    }

    val renderedAnimatedCells: Map<Pair<PlayerColor, Int>, Pair<Int, Int>> =
        if (animatedPieceCells.isNotEmpty()) animatedPieceCells else firstFrameAnimationCells

    LaunchedEffect(gameState.players, gameState.moveCounter) {
        val currentPositions = extractPiecePositions(gameState)
        val previousPositions = previousPiecePositions
        val isForwardMove = previousMoveCounter >= 0L && gameState.moveCounter > previousMoveCounter

        if (previousPositions != null && isForwardMove) {
            if (precomputedAnimationPaths.isNotEmpty()) {
                animatedPieceCells.clear()
                val maxSteps = precomputedAnimationPaths.maxOf { (_, cells) -> cells.size }
                for (stepIndex in 0 until maxSteps) {
                    animationFromCells.clear()
                    precomputedAnimationPaths.forEach { (key, cells) ->
                        animationFromCells[key] = cells.getOrNull((stepIndex - 1).coerceAtLeast(0)) ?: cells.last()
                        animatedPieceCells[key] = cells.getOrNull(stepIndex) ?: cells.last()
                    }
                    if (stepIndex > 0) {
                        movementProgress.snapTo(0f)
                        movementProgress.animateTo(1f, tween(
                            durationMillis = if (latestFeedbackSettings.reducedMotion) 1 else
                                if (hasCaptureDuringAnimation && stepIndex >= movingPieceStepCount) 280 else 115,
                            easing = LinearEasing))
                    }
                    if (stepIndex in 1 until movingPieceStepCount) {
                        val landingCapture = hasCaptureDuringAnimation && stepIndex == movingPieceStepCount - 1
                        feedbackManager.emitSound(if (landingCapture) FeedbackEvent.CAPTURE else FeedbackEvent.PIECE_MOVE,
                            latestFeedbackSettings)
                        if (landingCapture && latestFeedbackSettings.hapticsEnabled) {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                    }
                }
                kotlinx.coroutines.delay(if (latestFeedbackSettings.reducedMotion) 1 else 90)
                animationFromCells.clear()
                animatedPieceCells.clear()
            }
        } else if (previousMoveCounter >= 0L && gameState.moveCounter != previousMoveCounter) {
            animatedPieceCells.clear()
        }

        previousPiecePositions = currentPositions
        previousMoveCounter = gameState.moveCounter
    }

    LaunchedEffect(presentedRoll?.id) {
        if (isDiceRolling && presentedRoll != null) {
            feedbackManager.emitSound(FeedbackEvent.DICE_ROLL, latestFeedbackSettings)
            kotlinx.coroutines.delay(if (latestFeedbackSettings.reducedMotion) 100 else 460)
            if (latestFeedbackSettings.hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    LaunchedEffect(gameState.eventLog.size) {
        if (gameState.eventLog.size > previousEventSize && !replayUiState.isReplayMode) {
            val motionSteps = precomputedAnimationPaths.maxOfOrNull { it.second.size - 1 } ?: 0
            if (motionSteps > 0) {
                val returnSteps = if (hasCaptureDuringAnimation) (motionSteps - (movingPieceStepCount - 1)).coerceAtLeast(0) else 0
                kotlinx.coroutines.delay(if (latestFeedbackSettings.reducedMotion) motionSteps.toLong()
                    else (motionSteps - returnSteps) * 115L + returnSteps * 280L)
            }
            else if (isDiceRolling) kotlinx.coroutines.delay(if (latestFeedbackSettings.reducedMotion) 140 else 640)

            var playedCaptureInBatch = false
            gameState.eventLog.subList(previousEventSize, gameState.eventLog.size).forEach { event ->
                when (event) {
                    is GameEvent.PieceCaptured -> {
                        val shouldSequenceCaptureAudio = hasCaptureDuringAnimation && movingPieceStepCount > 1
                        if (!shouldSequenceCaptureAudio && !playedCaptureInBatch) {
                            feedbackManager.emitSound(FeedbackEvent.CAPTURE, feedbackSettings)
                            playedCaptureInBatch = true
                        }
                        if (!shouldSequenceCaptureAudio && feedbackSettings.hapticsEnabled) {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                    }

                    is GameEvent.PieceFinished -> {
                        feedbackManager.emitSound(FeedbackEvent.PIECE_FINISH, feedbackSettings)
                    }

                    is GameEvent.ExtraRollGranted -> {
                        feedbackManager.emitSound(FeedbackEvent.EXTRA_ROLL, feedbackSettings)
                    }

                    is GameEvent.TurnSkipped,
                    is GameEvent.ConsecutiveSixesForfeit -> {
                        feedbackManager.emitSound(FeedbackEvent.TURN_SKIP, feedbackSettings)
                    }

                    is GameEvent.PlayerWon -> {
                        feedbackManager.emitSound(FeedbackEvent.WIN, feedbackSettings)
                        if (feedbackSettings.hapticsEnabled) {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                    }

                    is GameEvent.PieceMoved,
                    is GameEvent.PieceEnteredBoard -> Unit
                }
            }
        }
        previousEventSize = gameState.eventLog.size
    }

    LaunchedEffect(gameState.turnPhase, gameState.currentPlayer.id, gameState.moveCounter) {
        if (gameState.turnPhase != TurnPhase.WAITING_FOR_PIECE_SELECTION) {
            pendingStackChoice = null
        }
    }

    LaunchedEffect(replayUiState.isReplayMode) {
        if (!replayUiState.isReplayMode) {
            replayAutoplayEnabled = false
        }
    }

    LaunchedEffect(replayUiState.isReplayMode, isMovementAnimationActive) {
        if (replayUiState.isReplayMode) {
            viewModel.updateMovementAnimationState(isActive = false)
        } else {
            viewModel.updateMovementAnimationState(isActive = isMovementAnimationActive)
        }
    }

    LaunchedEffect(
        replayUiState.isReplayMode,
        replayUiState.currentPly,
        replayUiState.totalPly,
        replayAutoplayEnabled,
        isMovementAnimationActive,
        replayDelayMs
    ) {
        if (!replayUiState.isReplayMode) return@LaunchedEffect
        if (!replayAutoplayEnabled) return@LaunchedEffect

        if (replayUiState.currentPly >= replayUiState.totalPly) {
            replayAutoplayEnabled = false
            return@LaunchedEffect
        }

        if (isMovementAnimationActive) return@LaunchedEffect

        kotlinx.coroutines.delay(replayDelayMs)

        if (replayAutoplayEnabled && !isMovementAnimationActive) {
            viewModel.replayStepForward()
        }
    }

    if (showQuitDialog) {
        AlertDialog(
            onDismissRequest = { showQuitDialog = false },
            title   = { Text(if (replayUiState.isReplayMode) "Exit Replay?" else "Quit Game?") },
            text    = {
                Text(
                    if (replayUiState.isReplayMode) {
                        "Replay progress will be closed and you will return to home."
                    } else {
                        "Your current game will be lost."
                    }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showQuitDialog = false
                        if (replayUiState.isReplayMode) {
                            viewModel.exitReplayMode()
                        }
                        onQuit()
                    }
                ) {
                    Text(if (replayUiState.isReplayMode) "Exit" else "Quit")
                }
            },
            dismissButton = {
                TextButton(onClick = { showQuitDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showFeedbackDialog) {
        FeedbackSettingsDialog(
            settings = feedbackSettings,
            onSettingsChange = viewModel::updateFeedbackSettings,
            onTestCaptureSound = {
                feedbackManager.emitSound(FeedbackEvent.CAPTURE, feedbackSettings)
                if (feedbackSettings.hapticsEnabled) {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                }
            },
            onDismiss = { showFeedbackDialog = false }
        )
    }

    if (pendingHomeEntryChoicePiece != null) {
        val previewTint = playerColor(pendingHomeEntryChoicePiece!!.color, setup.playerColors)
        val canCirculate = GameRules.canDeferHomeEntry(
            pendingHomeEntryChoicePiece!!, gameState.lastDice!!.value,
            pendingHomeEntryChoicePiece!!.color, gameState.players, gameState.mode
        )
        AlertDialog(
            onDismissRequest = { viewModel.dismissHomeEntryChoice() },
            title = { Text("Choose Pawn Path") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("This move can enter the finishing path. Choose how this pawn should continue.")

                    HomeEntryOptionPreviewCard(
                        title = "Enter Finish",
                        description = "Turn into home column and progress toward the center.",
                        tint = previewTint,
                        enterHomePath = true,
                        onClick = { viewModel.resolveHomeEntryChoice(enterHomePath = true) }
                    )

                    HomeEntryOptionPreviewCard(
                        title = "Keep Circulating",
                        description = if (canCirculate) "Stay on the main track for another full round."
                            else "Blocked by a pair or the three-pawn limit.",
                        enabled = canCirculate,
                        tint = previewTint,
                        enterHomePath = false,
                        onClick = { viewModel.resolveHomeEntryChoice(enterHomePath = false) }
                    )
                }
            },
            confirmButton = {}
        )
    }

    pendingStackChoice?.let { choiceState ->
        AlertDialog(
            onDismissRequest = { pendingStackChoice = null },
            title = { Text("Choose Pawn Move") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Select which move to play from this stack.")
                    choiceState.options.forEach { option ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = option.tint.copy(alpha = 0.15f),
                            tonalElevation = 1.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(18.dp)
                                            .clip(CircleShape)
                                            .background(option.tint)
                                    )
                                    Text(option.label, style = MaterialTheme.typography.bodyLarge)
                                }
                                TextButton(
                                    onClick = {
                                        pendingStackChoice = null
                                        viewModel.selectPiece(option.piece)
                                    }
                                ) { Text("Play") }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { pendingStackChoice = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    val keepRollOwner = !replayUiState.isReplayMode &&
        (isDiceRolling || isMovementAnimationActive || isTurnTransitionLocked)
    val displayState = if (keepRollOwner && presentedRoll != null) {
        val index = gameState.players.indexOfFirst { it.id == presentedRoll!!.playerId }
        if (index >= 0) gameState.copy(currentPlayerIndex = index) else gameState
    } else gameState
    val cp = displayState.currentPlayer
    val canRoll = !replayUiState.isReplayMode && !isTurnInputBlocked &&
        gameState.turnPhase == TurnPhase.WAITING_FOR_ROLL && cp.type == PlayerType.HUMAN
    val movableSet = if (!isTurnInputBlocked && !replayUiState.isReplayMode && cp.type == PlayerType.HUMAN)
        gameState.movablePieces.map { it.color to it.id }.toSet() else emptySet()

    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    @Composable fun HistoryActions() {
        TextButton(onClick=viewModel::undoLastAction, enabled=canUndo && !isTurnInputBlocked,
            colors=ButtonDefaults.textButtonColors(contentColor=TabletopStyle.Paper, disabledContentColor=TabletopStyle.Muted.copy(alpha=.35f))) { Text("Undo") }
        TextButton(onClick=viewModel::redoLastAction, enabled=canRedo && !isTurnInputBlocked,
            colors=ButtonDefaults.textButtonColors(contentColor=TabletopStyle.Paper, disabledContentColor=TabletopStyle.Muted.copy(alpha=.35f))) { Text("Redo") }
    }
    Scaffold(
        containerColor = TabletopStyle.Ink,
        topBar = {
            TopAppBar(
                title = { Column {
                    Text(if (replayUiState.isReplayMode) "LUDO / REPLAY" else "LUDO", fontWeight=FontWeight.ExtraBold, letterSpacing=3.sp)
                    Text(if(gameState.mode == GameMode.TEAM) "LOCAL TABLE · TEAMS" else "LOCAL TABLE · ${gameState.players.count { it.isActive }} PLAYERS",
                        fontSize=10.sp, letterSpacing=1.5.sp, color=TabletopStyle.Muted)
                } },
                actions = {
                    if (landscape && !replayUiState.isReplayMode) HistoryActions()
                    IconButton(onClick = { showFeedbackDialog = true }) {
                        Icon(Icons.Default.Settings, "Settings", tint=TabletopStyle.Paper)
                    }
                    IconButton(onClick = { showQuitDialog = true }) {
                        Icon(Icons.AutoMirrored.Filled.ExitToApp, "Leave table", tint=TabletopStyle.Paper)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor=TabletopStyle.Ink, titleContentColor=TabletopStyle.Paper)
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).background(Brush.verticalGradient(listOf(TabletopStyle.Ink, Color(0xFF193E3F))))) {
            if (replayUiState.isReplayMode) {
                Surface(color=TabletopStyle.Paper) { ReplayControlsRow(
                    replayUiState = replayUiState,
                    isMovementAnimationActive = isMovementAnimationActive,
                    isAutoplayEnabled = replayAutoplayEnabled,
                    replaySpeedLabel = replaySpeedLabel,
                    onToggleAutoplay = {
                        if (replayUiState.currentPly >= replayUiState.totalPly && !replayAutoplayEnabled) {
                            replayAutoplayEnabled = false
                        } else {
                            replayAutoplayEnabled = !replayAutoplayEnabled
                        }
                    },
                    onCycleSpeed = {
                        replaySpeedIndex = (replaySpeedIndex + 1) % replaySpeedSettings.size
                    },
                    onJumpToStart = {
                        replayAutoplayEnabled = false
                        viewModel.replayJumpToStart()
                    },
                    onStepBackward = {
                        replayAutoplayEnabled = false
                        viewModel.replayStepBackward()
                    },
                    onStepForward = {
                        replayAutoplayEnabled = false
                        viewModel.replayStepForward()
                    },
                    onJumpToEnd = {
                        replayAutoplayEnabled = false
                        viewModel.replayJumpToEnd()
                    },
                    onScrubToPly = { targetPly ->
                        replayAutoplayEnabled = false
                        viewModel.replayJumpToPly(targetPly)
                    }
                ) }
            } else if (!landscape) {
                Row(Modifier.fillMaxWidth().padding(horizontal=16.dp), horizontalArrangement=Arrangement.SpaceBetween,
                    verticalAlignment=Alignment.CenterVertically) {
                    Text("FAILURE EDITION", color=TabletopStyle.Gold, fontSize=10.sp, letterSpacing=2.sp)
                    Row { HistoryActions() }
                }
            }
            TabletopGameLayout(
                state=displayState, palette=setup.playerColors,
                diceValue=if(isDiceRolling) presentedRoll?.value else gameState.diceByPlayer[cp.id],
                rollId=presentedRoll?.id ?: 0L, rolling=isDiceRolling,
                reducedMotion=feedbackSettings.reducedMotion, canRoll=canRoll,
                inputBlocked=isMovementAnimationActive || isTurnTransitionLocked,
                onRoll=viewModel::rollDice, modifier=Modifier.fillMaxWidth().weight(1f),
                board = { boardModifier ->
                    TabletopBoard(
                        pieces=gameState.players.filter { it.isActive }.associate { it.color to it.pieces },
                        movable=movableSet, palette=setup.playerColors,
                        animatedCells=renderedAnimatedCells, fromCells=animationFromCells,
                        progress=movementProgress.value,
                        onTap={ tapped ->
                            if (!isTurnInputBlocked && !replayUiState.isReplayMode) {
                                val decision = resolveStackTapDecision(tapped, gameState.mode)
                                when {
                                    decision.autoPiece != null -> viewModel.selectPiece(decision.autoPiece)
                                    decision.options.isNotEmpty() -> pendingStackChoice = StackMoveChoiceState(decision.options)
                                }
                            }
                        }, modifier=boardModifier
                    )
                }
            )
        }
    }
}

internal data class StackMoveOption(
    val label: String,
    val piece: Piece,
    val tint: Color
)

private data class StackMoveChoiceState(
    val options: List<StackMoveOption>
)

internal data class StackTapDecision(
    val autoPiece: Piece? = null,
    val options: List<StackMoveOption> = emptyList()
)

private enum class StackMoveMeaningType {
    SINGLE,
    PAIR
}

private data class StackMoveMeaning(
    val type: StackMoveMeaningType,
    val colors: Set<PlayerColor>
)

private data class StackPieceRef(
    val color: PlayerColor,
    val pieceId: Int
)

internal fun resolveStackTapDecision(tapped: TappedCellPieces, mode: GameMode): StackTapDecision {
    val allPieces = tapped.allPieces
        .distinctBy { it.color to it.id }
        .sortedWith(compareBy<Piece>({ it.lastMovedAt }, { it.id }))
    val movablePieces = tapped.movablePieces
        .distinctBy { it.color to it.id }
        .sortedWith(compareBy<Piece>({ it.lastMovedAt }, { it.id }))
    val preferredMovablePiece = tapped.preferredPiece?.let { preferred ->
        movablePieces.firstOrNull { it.color == preferred.color && it.id == preferred.id }
    }

    if (movablePieces.isEmpty()) return StackTapDecision()
    if (movablePieces.size == 1) return StackTapDecision(autoPiece = movablePieces.first())

    fun chooseRepresentative(candidates: List<Piece>): Piece {
        return preferredMovablePiece?.takeIf { preferred ->
            candidates.any { it.color == preferred.color && it.id == preferred.id }
        } ?: candidates.last()
    }

    val mainIndex = (allPieces.firstOrNull()?.position as? PiecePosition.MainTrack)?.index
        ?: return StackTapDecision(autoPiece = chooseRepresentative(movablePieces))
    val sameMainCell = allPieces.all { (it.position as? PiecePosition.MainTrack)?.index == mainIndex }
    if (!sameMainCell) return StackTapDecision(autoPiece = chooseRepresentative(movablePieces))

    val actingSideKey = sideKey(movablePieces.first().color, mode)
    val sameSideStack = allPieces.filter { sideKey(it.color, mode) == actingSideKey }
    val pairRefs = doubleComponentRefsForTapStack(sameSideStack)
    val pairLocked = isPairLockedForTapStack(mainIndex, pairRefs)
    val pairColors = pairRefs.map { it.color }.toSet()

    val groupedChoices = linkedMapOf<StackMoveMeaning, MutableList<Piece>>()
    movablePieces.forEach { piece ->
        val meaning = if (pairLocked && piece.toStackRef() in pairRefs) {
            StackMoveMeaning(
                type = StackMoveMeaningType.PAIR,
                colors = pairColors
            )
        } else {
            StackMoveMeaning(
                type = StackMoveMeaningType.SINGLE,
                colors = setOf(piece.color)
            )
        }

        groupedChoices.getOrPut(meaning) { mutableListOf() }.add(piece)
    }

    if (groupedChoices.isEmpty()) {
        return StackTapDecision(autoPiece = chooseRepresentative(movablePieces))
    }

    if (groupedChoices.size == 1) {
        return StackTapDecision(autoPiece = chooseRepresentative(groupedChoices.values.first()))
    }

    val singleOptionColors = groupedChoices.keys
        .filter { it.type == StackMoveMeaningType.SINGLE }
        .flatMap { it.colors }
        .toSet()
    val shouldUseColorLabelsForSingles = singleOptionColors.size > 1

    val options = groupedChoices.map { (meaning, candidates) ->
        val representative = chooseRepresentative(candidates)
        val label = when (meaning.type) {
            StackMoveMeaningType.PAIR -> "Move pair"
            StackMoveMeaningType.SINGLE -> {
                if (shouldUseColorLabelsForSingles) {
                    "Move ${representative.color.displayName}"
                } else {
                    "Move single"
                }
            }
        }

        StackMoveOption(
            label = label,
            piece = representative,
            tint = playerColor(representative.color)
        )
    }

    return StackTapDecision(options = options)
}

private fun Piece.toStackRef(): StackPieceRef = StackPieceRef(color = color, pieceId = id)

private fun sideKey(color: PlayerColor, mode: GameMode): Int =
    if (mode == GameMode.TEAM) color.teamIndex else color.ordinal

private fun doubleComponentRefsForTapStack(stack: List<Piece>): Set<StackPieceRef> =
    GameRules.pairMembers(stack).map { (color, id) -> StackPieceRef(color, id) }.toSet()

private fun isPairLockedForTapStack(mainIndex: Int, pairRefs: Set<StackPieceRef>): Boolean =
    pairRefs.isNotEmpty() && !Board.isSafeSquare(mainIndex)


private fun extractPiecePositions(state: GameState): Map<Pair<PlayerColor, Int>, PiecePosition> {
    return state.players
        .flatMap { player ->
            player.pieces.map { piece -> (player.color to piece.id) to piece.position }
        }
        .toMap()
}

private fun buildAnimationPlan(
    state: GameState,
    previousPositions: Map<Pair<PlayerColor, Int>, PiecePosition>?,
    previousMoveCounter: Long
): PieceAnimationPlan {
    if (previousPositions == null) return PieceAnimationPlan()
    if (previousMoveCounter < 0L || state.moveCounter <= previousMoveCounter) return PieceAnimationPlan()

    val movedPieces = state.players
        .flatMap { player ->
            player.pieces
                .filter { it.lastMovedAt == state.moveCounter }
                .map { piece -> player.color to piece }
        }

    val movedPaths = movedPieces.mapNotNull { (color, piece) ->
        val key = color to piece.id
        val startPosition = previousPositions[key] ?: return@mapNotNull null
        val pathCells = computePieceAnimationCells(
            color = color,
            pieceId = piece.id,
            start = startPosition,
            end = piece.position
        )
        if (pathCells.size <= 1) return@mapNotNull null
        key to pathCells
    }

    val movedStepCount = movedPaths.maxOfOrNull { (_, cells) -> cells.size } ?: 0

    val movedKeys = movedPieces.map { (color, piece) -> color to piece.id }.toSet()
    val currentPositions = extractPiecePositions(state)
    val captureLeadFrames = (movedPaths.maxOfOrNull { (_, cells) -> cells.size } ?: 1) - 1

    val capturedPaths = currentPositions.mapNotNull { (key, endPosition) ->
        if (key in movedKeys) return@mapNotNull null

        val startPosition = previousPositions[key] ?: return@mapNotNull null
        if (endPosition !is PiecePosition.HomeBase || startPosition is PiecePosition.HomeBase) {
            return@mapNotNull null
        }

        // A captured piece returns directly to its dock after contact, not around the track.
        val returnCells = listOfNotNull(
            pieceCell(key.first, key.second, startPosition),
            pieceCell(key.first, key.second, endPosition)
        )
        if (returnCells.size != 2) return@mapNotNull null

        val delayedPath = if (captureLeadFrames > 0) {
            List(captureLeadFrames) { returnCells.first() } + returnCells
        } else {
            returnCells
        }

        key to delayedPath
    }

    return PieceAnimationPlan(
        paths = movedPaths + capturedPaths,
        movingPieceStepCount = movedStepCount,
        hasCapture = capturedPaths.isNotEmpty()
    )
}

@Composable
private fun HomeEntryOptionPreviewCard(
    title: String,
    description: String,
    tint: Color,
    enterHomePath: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClickLabel = title,
                onClick = onClick
            )
            .background(tint.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HomeEntryPathMiniPreview(
            tint = tint,
            enterHomePath = enterHomePath,
            modifier = Modifier
                .width(76.dp)
                .height(42.dp)
        )

        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = tint.copy(alpha = 0.95f)
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = OnSurface.copy(alpha = 0.80f)
            )
        }
    }
}

@Composable
private fun HomeEntryPathMiniPreview(
    tint: Color,
    enterHomePath: Boolean,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val lineWidth = size.minDimension * 0.14f
        val baseY = size.height * 0.72f
        val start = Offset(size.width * 0.10f, baseY)

        if (enterHomePath) {
            val laneTurn = Offset(size.width * 0.58f, baseY)
            val finish = Offset(laneTurn.x, size.height * 0.20f)

            drawLine(
                color = tint.copy(alpha = 0.85f),
                start = start,
                end = laneTurn,
                strokeWidth = lineWidth,
                cap = StrokeCap.Round
            )
            drawLine(
                color = tint.copy(alpha = 0.85f),
                start = laneTurn,
                end = finish,
                strokeWidth = lineWidth,
                cap = StrokeCap.Round
            )

            drawCircle(color = tint.copy(alpha = 0.95f), radius = lineWidth * 0.45f, center = start)
            drawCircle(color = tint.copy(alpha = 0.98f), radius = lineWidth * 0.58f, center = finish)
            drawCircle(color = Color.White.copy(alpha = 0.9f), radius = lineWidth * 0.22f, center = finish)
        } else {
            val end = Offset(size.width * 0.90f, baseY)

            drawLine(
                color = tint.copy(alpha = 0.85f),
                start = start,
                end = end,
                strokeWidth = lineWidth,
                cap = StrokeCap.Round
            )

            drawArc(
                color = tint.copy(alpha = 0.72f),
                startAngle = 210f,
                sweepAngle = 290f,
                useCenter = false,
                topLeft = Offset(size.width * 0.52f, size.height * 0.18f),
                size = Size(size.width * 0.34f, size.height * 0.50f),
                style = Stroke(width = lineWidth * 0.7f, cap = StrokeCap.Round)
            )

            drawCircle(color = tint.copy(alpha = 0.95f), radius = lineWidth * 0.45f, center = start)
            drawCircle(color = tint.copy(alpha = 0.95f), radius = lineWidth * 0.45f, center = end)
        }
    }
}

private fun computePieceAnimationCells(
    color: PlayerColor,
    pieceId: Int,
    start: PiecePosition,
    end: PiecePosition
): List<Pair<Int, Int>> {
    if (start == end) {
        return pieceCell(color, pieceId, end)?.let { listOf(it) } ?: emptyList()
    }

    val steps = if (end is PiecePosition.HomeBase && start !is PiecePosition.HomeBase) {
        computeReverseStepsToHome(color, start)
    } else {
        computeForwardAnimationSteps(color, start, end)
    }

    if (steps.isEmpty()) {
        return pieceCell(color, pieceId, end)?.let { listOf(it) } ?: emptyList()
    }

    return steps.mapNotNull { position -> pieceCell(color, pieceId, position) }
}

private fun computeForwardAnimationSteps(
    color: PlayerColor,
    start: PiecePosition,
    end: PiecePosition
): List<PiecePosition> {
    val steps = mutableListOf<PiecePosition>()
    var cursor = start
    steps += cursor

    var guard = Board.MAIN_TRACK_SIZE + Board.HOME_COLUMN_STEPS + 8
    while (cursor != end && guard > 0) {
        cursor = nextAnimationStep(color, cursor, end)
        steps += cursor
        guard -= 1
    }

    return if (cursor == end) steps else emptyList()
}

private fun computeReverseStepsToHome(
    color: PlayerColor,
    start: PiecePosition
): List<PiecePosition> {
    val steps = mutableListOf<PiecePosition>()
    var cursor = start
    steps += cursor

    var guard = Board.MAIN_TRACK_SIZE + Board.HOME_COLUMN_STEPS + 8
    while (cursor !is PiecePosition.HomeBase && guard > 0) {
        cursor = previousAnimationStep(color, cursor)
        steps += cursor
        guard -= 1
    }

    return if (cursor is PiecePosition.HomeBase) steps else emptyList()
}

private fun previousAnimationStep(
    color: PlayerColor,
    current: PiecePosition
): PiecePosition {
    return when (current) {
        PiecePosition.HomeBase -> PiecePosition.HomeBase
        is PiecePosition.MainTrack -> {
            val entry = Board.ENTRY_POSITIONS.getValue(color)
            if (current.index == entry) {
                PiecePosition.HomeBase
            } else {
                PiecePosition.MainTrack((current.index - 1 + Board.MAIN_TRACK_SIZE) % Board.MAIN_TRACK_SIZE)
            }
        }
        is PiecePosition.HomeColumn -> {
            if (current.step <= 1) PiecePosition.MainTrack(Board.HOME_COLUMN_ENTRY.getValue(color))
            else PiecePosition.HomeColumn(current.step - 1)
        }
        PiecePosition.Finished -> PiecePosition.HomeColumn(Board.HOME_COLUMN_STEPS)
    }
}

private fun nextAnimationStep(
    color: PlayerColor,
    current: PiecePosition,
    target: PiecePosition
): PiecePosition {
    return when (current) {
        PiecePosition.HomeBase -> PiecePosition.MainTrack(Board.ENTRY_POSITIONS.getValue(color))
        is PiecePosition.MainTrack -> {
            val homeEntry = Board.HOME_COLUMN_ENTRY.getValue(color)
            if (current.index == homeEntry && target !is PiecePosition.MainTrack) {
                PiecePosition.HomeColumn(1)
            } else {
                PiecePosition.MainTrack((current.index + 1) % Board.MAIN_TRACK_SIZE)
            }
        }
        is PiecePosition.HomeColumn -> {
            if (current.step >= Board.HOME_COLUMN_STEPS) PiecePosition.Finished
            else PiecePosition.HomeColumn(current.step + 1)
        }
        PiecePosition.Finished -> PiecePosition.Finished
    }
}

private fun pieceCell(color: PlayerColor, pieceId: Int, position: PiecePosition): Pair<Int, Int>? {
    return BoardCoordinates.cellFor(
        Piece(
            id = pieceId,
            color = color,
            position = position
        )
    )
}

// ── Sub-components ─────────────────────────────────────────────────────────────

@Composable
private fun ReplayControlsRow(
    replayUiState: ReplayUiState,
    isMovementAnimationActive: Boolean,
    isAutoplayEnabled: Boolean,
    replaySpeedLabel: String,
    onToggleAutoplay: () -> Unit,
    onCycleSpeed: () -> Unit,
    onJumpToStart: () -> Unit,
    onStepBackward: () -> Unit,
    onStepForward: () -> Unit,
    onJumpToEnd: () -> Unit,
    onScrubToPly: (Int) -> Unit
) {
    var scrubValue by remember(replayUiState.currentPly, replayUiState.totalPly) {
        mutableFloatStateOf(replayUiState.currentPly.toFloat())
    }

    val controlsEnabled = !isMovementAnimationActive
    val currentPly = replayUiState.currentPly
    val totalPly = replayUiState.totalPly

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Replay move $currentPly / $totalPly",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(
                    onClick = onCycleSpeed,
                    enabled = controlsEnabled
                ) {
                    Text("Speed $replaySpeedLabel")
                }

                Button(
                    onClick = onToggleAutoplay,
                    enabled = controlsEnabled && (isAutoplayEnabled || replayUiState.canStepForward)
                ) {
                    Text(if (isAutoplayEnabled) "Pause" else "Play")
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = onJumpToStart,
                enabled = controlsEnabled && replayUiState.canStepBackward,
                modifier = Modifier.weight(1f)
            ) {
                Text("|<")
            }

            OutlinedButton(
                onClick = onStepBackward,
                enabled = controlsEnabled && replayUiState.canStepBackward,
                modifier = Modifier.weight(1f)
            ) {
                Text("<")
            }

            OutlinedButton(
                onClick = onStepForward,
                enabled = controlsEnabled && replayUiState.canStepForward,
                modifier = Modifier.weight(1f)
            ) {
                Text(">")
            }

            OutlinedButton(
                onClick = onJumpToEnd,
                enabled = controlsEnabled && replayUiState.canStepForward,
                modifier = Modifier.weight(1f)
            ) {
                Text(">|")
            }
        }

        if (totalPly > 0) {
            Slider(
                value = scrubValue,
                onValueChange = { scrubValue = it },
                onValueChangeFinished = {
                    onScrubToPly(scrubValue.roundToInt())
                },
                valueRange = 0f..totalPly.toFloat(),
                enabled = controlsEnabled,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun FeedbackSettingsDialog(
    settings: FeedbackSettings,
    onSettingsChange: (FeedbackSettings) -> Unit,
    onTestCaptureSound: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Game Feedback") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Sound effects")
                    Switch(
                        checked = settings.soundEnabled,
                        onCheckedChange = { enabled ->
                            onSettingsChange(settings.copy(soundEnabled = enabled))
                        }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Reduced motion")
                    Switch(
                        checked = settings.reducedMotion,
                        onCheckedChange = { enabled ->
                            onSettingsChange(settings.copy(reducedMotion = enabled))
                        }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Haptics")
                    Switch(
                        checked = settings.hapticsEnabled,
                        onCheckedChange = { enabled ->
                            onSettingsChange(settings.copy(hapticsEnabled = enabled))
                        }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Auto-select single move")
                    Switch(
                        checked = settings.singleMoveAssistEnabled,
                        onCheckedChange = { enabled ->
                            onSettingsChange(settings.copy(singleMoveAssistEnabled = enabled))
                        }
                    )
                }

                Text("Master volume: ${(settings.masterVolume * 100f).toInt()}%")
                Slider(
                    value = settings.masterVolume,
                    onValueChange = { value ->
                        onSettingsChange(settings.copy(masterVolume = value.coerceIn(0f, 1f)))
                    },
                    valueRange = 0f..1f
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(
                        onClick = onTestCaptureSound,
                        enabled = settings.soundEnabled
                    ) {
                        Text("Test capture sound")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}
