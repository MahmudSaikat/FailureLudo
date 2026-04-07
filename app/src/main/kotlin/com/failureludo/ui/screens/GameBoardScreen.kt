package com.failureludo.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.failureludo.R
import com.failureludo.data.FeedbackSettings
import com.failureludo.engine.*
import com.failureludo.feedback.FeedbackEvent
import com.failureludo.feedback.GameFeedbackManager
import com.failureludo.ui.components.BoardCoordinates
import com.failureludo.ui.components.DiceView
import com.failureludo.ui.components.LudoBoardCanvas
import com.failureludo.ui.components.TappedCellPieces
import com.failureludo.ui.theme.*
import com.failureludo.viewmodel.GameViewModel
import com.failureludo.viewmodel.ReplayUiState
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

internal data class BoardLayoutSizing(
    val diceSize: Dp,
    val railHeight: Dp,
    val boardSize: Dp
)

private const val PIECE_MOVE_STEP_DELAY_MS = 130L
private const val PIECE_MOVE_SETTLE_DELAY_MS = 120L
private const val PIECE_MOVE_BREATH_PERIOD_MS = 240
private const val PIECE_MOVE_BREATH_MIN_SCALE = 0.90f
private const val PIECE_MOVE_BREATH_MAX_SCALE = 1.30f
private const val CAPTURE_RETURN_ACCELERATION_THRESHOLD = 8
private const val PLAY_AREA_BACKGROUND_IMAGE_ALPHA = 0.34f
private const val PLAY_AREA_BACKGROUND_OVERLAY_ALPHA = 0.10f

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
    val setup by viewModel.setupState.collectAsState()
    val feedbackSettings by viewModel.feedbackSettings.collectAsState()
    val pendingHomeEntryChoicePiece by viewModel.pendingHomeEntryChoicePiece.collectAsState()
    val canUndo by viewModel.canUndo.collectAsState()
    val canRedo by viewModel.canRedo.collectAsState()
    val replayUiState by viewModel.replayUiState.collectAsState()
    val isTurnTransitionLocked by viewModel.isTurnTransitionLocked.collectAsState()
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val feedbackManager = remember(context) { GameFeedbackManager(context) }

    DisposableEffect(feedbackManager) {
        onDispose {
            feedbackManager.release()
        }
    }

    // Wire the game-over callback once
    LaunchedEffect(Unit) {
        viewModel.onGameOver = onGameOver
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

    var previousDiceSignature by remember { mutableStateOf<String?>(null) }
    var previousEventSize by remember { mutableIntStateOf(0) }
    var captureFxTrigger by remember { mutableIntStateOf(0) }
    var captureFxColor by remember { mutableStateOf(Color.White) }
    var finishFxTrigger by remember { mutableIntStateOf(0) }
    var finishFxColor by remember { mutableStateOf(Color.White) }
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
    val movementBreathTransition = rememberInfiniteTransition(label = "piece_move_breath")
    val movementBreathPhase by movementBreathTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = PIECE_MOVE_BREATH_PERIOD_MS,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "piece_move_breath_phase"
    )
    val movingPieceScale = if (isMovementAnimationActive) {
        val sineInput = movementBreathPhase * (2f * PI.toFloat())
        val normalizedWave = ((sin(sineInput.toDouble()) + 1.0) / 2.0).toFloat()
        PIECE_MOVE_BREATH_MIN_SCALE +
            ((PIECE_MOVE_BREATH_MAX_SCALE - PIECE_MOVE_BREATH_MIN_SCALE) * normalizedWave)
    } else {
        1f
    }
    val isTurnInputBlocked = isMovementAnimationActive || isTurnTransitionLocked

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
                    precomputedAnimationPaths.forEach { (key, cells) ->
                        val cell = cells.getOrNull(stepIndex) ?: cells.last()
                        animatedPieceCells[key] = cell
                    }

                    if (stepIndex in 1 until movingPieceStepCount) {
                        val isCaptureLandingStep = hasCaptureDuringAnimation &&
                            stepIndex == movingPieceStepCount - 1
                        if (isCaptureLandingStep) {
                            feedbackManager.emitSound(FeedbackEvent.CAPTURE, latestFeedbackSettings)
                        } else {
                            feedbackManager.emitSound(FeedbackEvent.PIECE_MOVE, latestFeedbackSettings)
                        }
                    }

                    if (stepIndex < maxSteps - 1) {
                        kotlinx.coroutines.delay(PIECE_MOVE_STEP_DELAY_MS)
                    }
                }
                if (maxSteps > 0) {
                    kotlinx.coroutines.delay(PIECE_MOVE_SETTLE_DELAY_MS)
                }
                animatedPieceCells.clear()
            }
        } else if (previousMoveCounter >= 0L && gameState.moveCounter != previousMoveCounter) {
            animatedPieceCells.clear()
        }

        previousPiecePositions = currentPositions
        previousMoveCounter = gameState.moveCounter
    }

    LaunchedEffect(gameState, feedbackSettings) {
        val diceSignature = gameState.lastDice?.let {
            "${gameState.currentPlayer.id.value}-${it.value}-${it.rollCount}-${gameState.turnPhase.name}"
        }
        if (diceSignature != null && diceSignature != previousDiceSignature) {
            feedbackManager.emitSound(FeedbackEvent.DICE_ROLL, feedbackSettings)
            if (feedbackSettings.hapticsEnabled) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        }
        previousDiceSignature = diceSignature

        if (gameState.eventLog.size > previousEventSize) {
            var playedCaptureInBatch = false
            gameState.eventLog.subList(previousEventSize, gameState.eventLog.size).forEach { event ->
                when (event) {
                    is GameEvent.PieceCaptured -> {
                        val shouldSequenceCaptureAudio = hasCaptureDuringAnimation && movingPieceStepCount > 1
                        if (!shouldSequenceCaptureAudio && !playedCaptureInBatch) {
                            feedbackManager.emitSound(FeedbackEvent.CAPTURE, feedbackSettings)
                            playedCaptureInBatch = true
                        }
                        captureFxColor = playerColor(event.byColor, setup.playerColors)
                        captureFxTrigger += 1
                        if (feedbackSettings.hapticsEnabled) {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                    }

                    is GameEvent.PieceFinished -> {
                        feedbackManager.emitSound(FeedbackEvent.PIECE_FINISH, feedbackSettings)
                        finishFxColor = playerColor(event.color, setup.playerColors)
                        finishFxTrigger += 1
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
                        description = "Stay on the main track for another full round.",
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

    Box(modifier = Modifier.fillMaxSize()) {
        SplitEdgePlayBackground(
            modifier = Modifier.fillMaxSize()
        )

        Box(
            modifier = Modifier
                .matchParentSize()
                .background(Color.White.copy(alpha = PLAY_AREA_BACKGROUND_OVERLAY_ALPHA))
        )

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            if (replayUiState.isReplayMode) {
                                "Ludo Replay"
                            } else {
                                "Ludo"
                            }
                        )
                    },
                    actions = {
                        IconButton(onClick = { showFeedbackDialog = true }) {
                            Icon(Icons.Default.Settings, contentDescription = "Feedback Settings", tint = OnPrimary)
                        }
                        IconButton(onClick = { showQuitDialog = true }) {
                            Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Quit", tint = OnPrimary)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Primary, titleContentColor = OnPrimary
                    )
                )
            },
            containerColor = Color.Transparent
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

            TurnIndicatorRow(gameState, setup.playerColors)

            if (replayUiState.isReplayMode) {
                ReplayControlsRow(
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
                )
            } else {
                MoveHistoryControlsRow(
                    canUndo = canUndo && !isTurnInputBlocked,
                    canRedo = canRedo && !isTurnInputBlocked,
                    onUndo = viewModel::undoLastAction,
                    onRedo = viewModel::redoLastAction
                )
            }

            val movableSet = gameState.movablePieces
                .map { it.color to it.id }.toSet()
            val piecesMap = gameState.players.associate { p -> p.color to p.pieces }

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                val layoutSizing = computeBoardLayoutSizing(
                    maxWidth = maxWidth,
                    maxHeight = maxHeight
                )
                val diceSize = layoutSizing.diceSize
                val railHeight = layoutSizing.railHeight
                val boardSize = layoutSizing.boardSize

                val playersByColor = gameState.players.associateBy { it.color }

                OutsideBoardPlayBackground(
                    boardSize = boardSize,
                    modifier = Modifier.matchParentSize()
                )

                Column(
                    modifier = Modifier.height(boardSize + railHeight * 2),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier
                            .width(boardSize)
                            .height(railHeight),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        playersByColor[PlayerColor.RED]?.let { player ->
                            SideRailDice(
                                player = player,
                                diceValue = gameState.diceByPlayer[player.id],
                                isCurrent = player.id == gameState.currentPlayer.id,
                                isRollable = player.id == gameState.currentPlayer.id &&
                                    !replayUiState.isReplayMode &&
                                    gameState.turnPhase == TurnPhase.WAITING_FOR_ROLL &&
                                    player.type == com.failureludo.engine.PlayerType.HUMAN &&
                                    !isTurnInputBlocked,
                                onRoll = {
                                    if (!replayUiState.isReplayMode && !isTurnInputBlocked) {
                                        viewModel.rollDice()
                                    }
                                },
                                size = diceSize
                            )
                        }
                        playersByColor[PlayerColor.BLUE]?.let { player ->
                            SideRailDice(
                                player = player,
                                diceValue = gameState.diceByPlayer[player.id],
                                isCurrent = player.id == gameState.currentPlayer.id,
                                isRollable = player.id == gameState.currentPlayer.id &&
                                    !replayUiState.isReplayMode &&
                                    gameState.turnPhase == TurnPhase.WAITING_FOR_ROLL &&
                                    player.type == com.failureludo.engine.PlayerType.HUMAN &&
                                    !isTurnInputBlocked,
                                onRoll = {
                                    if (!replayUiState.isReplayMode && !isTurnInputBlocked) {
                                        viewModel.rollDice()
                                    }
                                },
                                size = diceSize
                            )
                        }
                    }

                    Box(
                        modifier = Modifier.size(boardSize),
                        contentAlignment = Alignment.Center
                    ) {
                        LudoBoardCanvas(
                            allPieces = piecesMap,
                            movablePieceIds = movableSet,
                            animatedPieceCells = renderedAnimatedCells,
                            movingPieceScale = movingPieceScale,
                            playerPalette = setup.playerColors,
                            onCellPiecesTapped = { tapped ->
                                if (!replayUiState.isReplayMode && renderedAnimatedCells.isEmpty()) {
                                    val decision = resolveStackTapDecision(tapped)
                                    when {
                                        decision.autoPiece != null -> viewModel.selectPiece(decision.autoPiece)
                                        decision.options.isNotEmpty() -> pendingStackChoice = StackMoveChoiceState(decision.options)
                                    }
                                }
                            },
                            modifier = Modifier.matchParentSize()
                        )

                        CaptureBurstOverlay(
                            trigger = captureFxTrigger,
                            tint = captureFxColor,
                            modifier = Modifier.matchParentSize()
                        )

                        FinishConfettiOverlay(
                            trigger = finishFxTrigger,
                            tint = finishFxColor,
                            modifier = Modifier.matchParentSize()
                        )

                        BoardSeatNamesOverlay(
                            playersByColor = playersByColor,
                            currentPlayerId = gameState.currentPlayer.id,
                            playerColors = setup.playerColors,
                            modifier = Modifier.matchParentSize()
                        )
                    }

                    Row(
                        modifier = Modifier
                            .width(boardSize)
                            .height(railHeight),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        playersByColor[PlayerColor.GREEN]?.let { player ->
                            SideRailDice(
                                player = player,
                                diceValue = gameState.diceByPlayer[player.id],
                                isCurrent = player.id == gameState.currentPlayer.id,
                                isRollable = player.id == gameState.currentPlayer.id &&
                                    !replayUiState.isReplayMode &&
                                    gameState.turnPhase == TurnPhase.WAITING_FOR_ROLL &&
                                    player.type == com.failureludo.engine.PlayerType.HUMAN &&
                                    !isTurnInputBlocked,
                                onRoll = {
                                    if (!replayUiState.isReplayMode && !isTurnInputBlocked) {
                                        viewModel.rollDice()
                                    }
                                },
                                size = diceSize
                            )
                        }
                        playersByColor[PlayerColor.YELLOW]?.let { player ->
                            SideRailDice(
                                player = player,
                                diceValue = gameState.diceByPlayer[player.id],
                                isCurrent = player.id == gameState.currentPlayer.id,
                                isRollable = player.id == gameState.currentPlayer.id &&
                                    !replayUiState.isReplayMode &&
                                    gameState.turnPhase == TurnPhase.WAITING_FOR_ROLL &&
                                    player.type == com.failureludo.engine.PlayerType.HUMAN &&
                                    !isTurnInputBlocked,
                                onRoll = {
                                    if (!replayUiState.isReplayMode && !isTurnInputBlocked) {
                                        viewModel.rollDice()
                                    }
                                },
                                size = diceSize
                            )
                        }
                    }
                }
            }
            }
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

internal fun resolveStackTapDecision(tapped: TappedCellPieces): StackTapDecision {
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

    fun defaultAutoPiece(): Piece = preferredMovablePiece ?: movablePieces.last()

    val mainIndex = (allPieces.firstOrNull()?.position as? PiecePosition.MainTrack)?.index
        ?: return StackTapDecision(autoPiece = defaultAutoPiece())
    val sameMainCell = allPieces.all { (it.position as? PiecePosition.MainTrack)?.index == mainIndex }
    if (!sameMainCell) return StackTapDecision(autoPiece = defaultAutoPiece())

    // Safe squares should never open chooser for multi-stack taps.
    if (Board.isSafeSquare(mainIndex)) {
        return StackTapDecision(autoPiece = defaultAutoPiece())
    }

    val sameColor = allPieces.all { it.color == allPieces.first().color }
    if (!sameColor) {
        return StackTapDecision(autoPiece = defaultAutoPiece())
    }

    val color = allPieces.first().color
    val isLockedCell = mainIndex != Board.HOME_COLUMN_ENTRY.getValue(color)
    if (!isLockedCell) {
        return StackTapDecision(autoPiece = defaultAutoPiece())
    }

    // 3-piece non-safe stack: explicit choice between top single and locked pair when both are legal.
    if (allPieces.size == 3) {
        val topSingle = allPieces.maxWithOrNull(compareBy<Piece>({ it.lastMovedAt }, { it.id })) ?: return StackTapDecision(autoPiece = defaultAutoPiece())
        val pairPieces = allPieces.filter { it != topSingle }
        val topSingleLegal = movablePieces.any { it.id == topSingle.id && it.color == topSingle.color }
        val pairRepresentative = pairPieces.firstOrNull { candidate ->
            movablePieces.any { it.id == candidate.id && it.color == candidate.color }
        }

        if (topSingleLegal && pairRepresentative != null) {
            return StackTapDecision(
                options = listOf(
                    StackMoveOption(label = "Move single", piece = topSingle, tint = playerColor(topSingle.color)),
                    StackMoveOption(label = "Move pair", piece = pairRepresentative, tint = playerColor(pairRepresentative.color))
                )
            )
        }

        if (topSingleLegal) return StackTapDecision(autoPiece = topSingle)
        if (pairRepresentative != null) return StackTapDecision(autoPiece = pairRepresentative)
    }

    // 4-piece same-color stack rule is intentionally deferred. Keep deterministic auto-choice for now.
    return StackTapDecision(autoPiece = defaultAutoPiece())
}

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

        val pathCells = computePieceAnimationCells(
            color = key.first,
            pieceId = key.second,
            start = startPosition,
            end = endPosition
        )
        if (pathCells.size <= 1) return@mapNotNull null

        val acceleratedCaptureCells = accelerateCapturedReturnPath(pathCells)

        val delayedPath = if (captureLeadFrames > 0) {
            List(captureLeadFrames) { acceleratedCaptureCells.first() } + acceleratedCaptureCells
        } else {
            acceleratedCaptureCells
        }

        key to delayedPath
    }

    return PieceAnimationPlan(
        paths = movedPaths + capturedPaths,
        movingPieceStepCount = movedStepCount,
        hasCapture = capturedPaths.isNotEmpty()
    )
}

private fun accelerateCapturedReturnPath(pathCells: List<Pair<Int, Int>>): List<Pair<Int, Int>> {
    if (pathCells.size < CAPTURE_RETURN_ACCELERATION_THRESHOLD) return pathCells

    val accelerated = mutableListOf<Pair<Int, Int>>()
    accelerated += pathCells.first()

    var index = 1
    while (index < pathCells.lastIndex) {
        accelerated += pathCells[index]
        index += 2
    }

    if (accelerated.last() != pathCells.last()) {
        accelerated += pathCells.last()
    }

    return accelerated
}

@Composable
private fun HomeEntryOptionPreviewCard(
    title: String,
    description: String,
    tint: Color,
    enterHomePath: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(
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
private fun TurnIndicatorRow(state: GameState, playerColors: Map<PlayerColor, Color>) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        state.players.filter { it.isActive }.forEach { player ->
            val isCurrent = player.id == state.currentPlayer.id
            PlayerChip(player = player, isActive = isCurrent, playerColors = playerColors)
        }
    }
}

@Composable
private fun MoveHistoryControlsRow(
    canUndo: Boolean,
    canRedo: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedButton(
            onClick = onUndo,
            enabled = canUndo,
            modifier = Modifier.weight(1f)
        ) {
            Text("Undo")
        }

        OutlinedButton(
            onClick = onRedo,
            enabled = canRedo,
            modifier = Modifier.weight(1f)
        ) {
            Text("Redo")
        }
    }
}

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
private fun PlayerChip(
    player: com.failureludo.engine.Player,
    isActive: Boolean,
    playerColors: Map<PlayerColor, Color>
) {
    val selectedColor = playerColor(player.color, playerColors)
    val bg = if (isActive) selectedColor else selectedColor.copy(0.25f)

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .semantics {
                contentDescription = buildString {
                    append(player.name)
                    append(if (isActive) ", current turn" else ", waiting")
                    if (player.finishedPieceCount > 0) {
                        append(", ${player.finishedPieceCount} finished pawns")
                    }
                }
            }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            player.name,
            style = MaterialTheme.typography.labelLarge,
            color = if (isActive) Color.White else Color.White.copy(0.7f),
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        // Finished piece count
        if (player.finishedPieceCount > 0) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(0.3f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    player.finishedPieceCount.toString(),
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontSize = 10.sp
                    ),
                    color = Color.White
                )
            }
        }
    }
}

@Composable
private fun SideRailDice(
    player: com.failureludo.engine.Player,
    diceValue: Int?,
    isCurrent: Boolean,
    isRollable: Boolean,
    onRoll: () -> Unit,
    size: androidx.compose.ui.unit.Dp
) {
    Column(
        modifier = Modifier.semantics {
            val turnState = if (isCurrent) "current turn" else "waiting"
            val diceLabel = diceValue?.toString() ?: "not rolled"
            contentDescription = "${player.name}, $turnState, dice $diceLabel"
        },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(contentAlignment = Alignment.TopCenter) {
            DiceView(
                diceValue = diceValue,
                isRollable = isRollable,
                isCurrentTurn = isCurrent,
                onRoll = onRoll,
                size = size,
                contentDescription = if (isRollable) {
                    "Roll dice for ${player.name}"
                } else {
                    "Dice for ${player.name}"
                }
            )
        }
    }
}

@Composable
private fun BoardSeatNamesOverlay(
    playersByColor: Map<PlayerColor, com.failureludo.engine.Player>,
    currentPlayerId: PlayerId,
    playerColors: Map<PlayerColor, Color>,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        PlayerColor.entries.forEach { color ->
            val player = playersByColor[color] ?: return@forEach
            val isCurrent = player.id == currentPlayerId

            val alignment = when (color) {
                PlayerColor.RED -> Alignment.TopStart
                PlayerColor.BLUE -> Alignment.TopEnd
                PlayerColor.YELLOW -> Alignment.BottomEnd
                PlayerColor.GREEN -> Alignment.BottomStart
            }

            SeatCornerNameBadge(
                player = player,
                isCurrent = isCurrent,
                tint = playerColor(color, playerColors),
                modifier = Modifier
                    .align(alignment)
                    .padding(8.dp)
            )
        }
    }
}

@Composable
private fun OutsideBoardPlayBackground(
    boardSize: Dp,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        // Keep the square board zone clean so the board rendering is unchanged.
        Box(
            modifier = Modifier
                .size(boardSize)
                .align(Alignment.Center)
                .background(Background)
        )
    }
}

@Composable
private fun SplitEdgePlayBackground(
    modifier: Modifier = Modifier
) {
    val backgroundImage = ImageBitmap.imageResource(id = R.drawable.ludo_board_background)

    Canvas(modifier = modifier) {
        val srcHalfHeight = (backgroundImage.height / 2).coerceAtLeast(1)
        val srcSize = IntSize(backgroundImage.width, srcHalfHeight)
        val dstWidth = size.width.roundToInt().coerceAtLeast(1)
        val dstHalfHeight = (srcHalfHeight * (size.width / backgroundImage.width.toFloat()))
            .roundToInt()
            .coerceAtLeast(1)

        drawImage(
            image = backgroundImage,
            srcOffset = IntOffset(0, 0),
            srcSize = srcSize,
            dstOffset = IntOffset(0, 0),
            dstSize = IntSize(dstWidth, dstHalfHeight),
            alpha = PLAY_AREA_BACKGROUND_IMAGE_ALPHA
        )

        drawImage(
            image = backgroundImage,
            srcOffset = IntOffset(0, backgroundImage.height - srcHalfHeight),
            srcSize = srcSize,
            dstOffset = IntOffset(0, (size.height - dstHalfHeight).roundToInt()),
            dstSize = IntSize(dstWidth, dstHalfHeight),
            alpha = PLAY_AREA_BACKGROUND_IMAGE_ALPHA
        )
    }
}

@Composable
private fun SeatCornerNameBadge(
    player: com.failureludo.engine.Player,
    isCurrent: Boolean,
    tint: Color,
    modifier: Modifier = Modifier
) {
    val badgeBackground = if (isCurrent) {
        tint.copy(alpha = 0.86f)
    } else {
        tint.copy(alpha = 0.62f)
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(badgeBackground)
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .semantics {
                contentDescription = buildString {
                    append(player.name)
                    append(if (isCurrent) ", current turn" else ", waiting")
                }
            }
    ) {
        Text(
            text = player.name,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun CaptureBurstOverlay(
    trigger: Int,
    tint: Color,
    modifier: Modifier = Modifier
) {
    val progress = remember { Animatable(1f) }

    LaunchedEffect(trigger) {
        if (trigger <= 0) return@LaunchedEffect
        progress.snapTo(0f)
        progress.animateTo(1f, animationSpec = tween(durationMillis = 420))
    }

    val p = progress.value
    val alpha = (1f - p).coerceIn(0f, 1f)
    if (alpha <= 0.01f) return

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val ringRadius = size.minDimension * (0.12f + p * 0.22f)

            drawCircle(
                color = tint.copy(alpha = 0.22f * alpha),
                radius = ringRadius,
                center = center
            )
            drawCircle(
                color = tint.copy(alpha = 0.9f * alpha),
                radius = ringRadius,
                center = center,
                style = Stroke(width = size.minDimension * 0.012f)
            )
        }

        Text(
            text = "💥",
            style = MaterialTheme.typography.headlineMedium,
            fontSize = (30f * (1f + p * 0.22f)).sp,
            color = Color.White.copy(alpha = alpha)
        )
    }
}

@Composable
private fun FinishConfettiOverlay(
    trigger: Int,
    tint: Color,
    modifier: Modifier = Modifier
) {
    val progress = remember { Animatable(1f) }

    LaunchedEffect(trigger) {
        if (trigger <= 0) return@LaunchedEffect
        progress.snapTo(0f)
        progress.animateTo(1f, animationSpec = tween(durationMillis = 700))
    }

    val p = progress.value
    val alpha = (1f - p).coerceIn(0f, 1f)
    if (alpha <= 0.01f) return

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val centerX = center.x
            val centerY = center.y
            val minDim = size.minDimension

            repeat(12) { index ->
                val angle = (2f * PI.toFloat() * index) / 12f
                val distance = minDim * (0.12f + p * 0.32f)
                val dotX = centerX + cos(angle) * distance
                val dotY = centerY + sin(angle) * distance

                drawCircle(
                    color = tint.copy(alpha = alpha),
                    radius = minDim * (0.012f - (p * 0.004f)).coerceAtLeast(minDim * 0.006f),
                    center = Offset(dotX, dotY)
                )
            }
        }

        Text(
            text = "✨",
            style = MaterialTheme.typography.headlineMedium,
            fontSize = (26f * (1f + p * 0.16f)).sp,
            color = Color.White.copy(alpha = alpha)
        )
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
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                    Text("Music")
                    Switch(
                        checked = settings.musicEnabled,
                        onCheckedChange = { enabled ->
                            onSettingsChange(settings.copy(musicEnabled = enabled))
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
