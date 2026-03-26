package com.failureludo.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.failureludo.data.FeedbackSettings
import com.failureludo.engine.*
import com.failureludo.feedback.FeedbackEvent
import com.failureludo.feedback.GameFeedbackManager
import com.failureludo.ui.components.DiceView
import com.failureludo.ui.components.LudoBoardCanvas
import com.failureludo.ui.theme.*
import com.failureludo.viewmodel.GameViewModel
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

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
    val haptics = LocalHapticFeedback.current
    val feedbackManager = remember { GameFeedbackManager() }

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

    var showQuitDialog by remember { mutableStateOf(false) }
    var showFeedbackDialog by remember { mutableStateOf(false) }

    var previousDiceSignature by remember { mutableStateOf<String?>(null) }
    var previousEventSize by remember { mutableIntStateOf(0) }
    var captureFxTrigger by remember { mutableIntStateOf(0) }
    var captureFxColor by remember { mutableStateOf(Color.White) }
    var finishFxTrigger by remember { mutableIntStateOf(0) }
    var finishFxColor by remember { mutableStateOf(Color.White) }
    var extraRollBadgeColor by remember { mutableStateOf<PlayerColor?>(null) }
    var extraRollBadgeToken by remember { mutableIntStateOf(0) }

    LaunchedEffect(gameState, feedbackSettings) {
        val diceSignature = gameState.lastDice?.let {
            "${gameState.currentPlayer.color.name}-${it.value}-${it.rollCount}-${gameState.turnPhase.name}"
        }
        if (diceSignature != null && diceSignature != previousDiceSignature) {
            feedbackManager.emitSound(FeedbackEvent.DICE_ROLL, feedbackSettings)
            if (feedbackSettings.hapticsEnabled) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        }
        previousDiceSignature = diceSignature

        if (gameState.eventLog.size > previousEventSize) {
            gameState.eventLog.subList(previousEventSize, gameState.eventLog.size).forEach { event ->
                when (event) {
                    is GameEvent.PieceCaptured -> {
                        feedbackManager.emitSound(FeedbackEvent.CAPTURE, feedbackSettings)
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
                        extraRollBadgeColor = event.color
                        val token = extraRollBadgeToken + 1
                        extraRollBadgeToken = token
                        launch {
                            kotlinx.coroutines.delay(1200)
                            if (extraRollBadgeToken == token) {
                                extraRollBadgeColor = null
                            }
                        }
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
                    is GameEvent.PieceEnteredBoard -> {
                        feedbackManager.emitSound(FeedbackEvent.PIECE_MOVE, feedbackSettings)
                    }
                }
            }
        }
        previousEventSize = gameState.eventLog.size
    }

    if (showQuitDialog) {
        AlertDialog(
            onDismissRequest = { showQuitDialog = false },
            title   = { Text("Quit Game?") },
            text    = { Text("Your current game will be lost.") },
            confirmButton = {
                TextButton(onClick = { showQuitDialog = false; onQuit() }) { Text("Quit") }
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
            onDismiss = { showFeedbackDialog = false }
        )
    }

    if (pendingHomeEntryChoicePiece != null) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissHomeEntryChoice() },
            title = { Text("Choose Pawn Path") },
            text = { Text("This move can enter the finishing path. Do you want to enter now or continue circulating on the main track?") },
            confirmButton = {
                TextButton(onClick = { viewModel.resolveHomeEntryChoice(enterHomePath = true) }) {
                    Text("Enter Finish")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.resolveHomeEntryChoice(enterHomePath = false) }) {
                    Text("Keep Circulating")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ludo") },
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
        containerColor = Background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // Player turn indicator row
            TurnIndicatorRow(gameState, setup.playerColors)

            // Board - takes as much space as possible (square)
            val movableSet = gameState.movablePieces
                .map { it.color to it.id }.toSet()

            val piecesMap = gameState.players.associate { p -> p.color to p.pieces }

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                val diceSize = (maxWidth * 0.12f).coerceIn(42.dp, 56.dp)
                val railHeight = diceSize + 24.dp
                val boardSize = minOf(
                    maxWidth,
                    (maxHeight - railHeight * 2).coerceAtLeast(140.dp)
                )

                val playersByColor = gameState.players.associateBy { it.color }

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
                                diceValue = gameState.diceByPlayer[player.color],
                                isCurrent = player.color == gameState.currentPlayer.color,
                                isRollable = player.color == gameState.currentPlayer.color &&
                                    gameState.turnPhase == TurnPhase.WAITING_FOR_ROLL &&
                                    player.type == com.failureludo.engine.PlayerType.HUMAN,
                                showExtraRollBadge = extraRollBadgeColor == player.color,
                                onRoll = { viewModel.rollDice() },
                                size = diceSize
                            )
                        }
                        playersByColor[PlayerColor.BLUE]?.let { player ->
                            SideRailDice(
                                player = player,
                                diceValue = gameState.diceByPlayer[player.color],
                                isCurrent = player.color == gameState.currentPlayer.color,
                                isRollable = player.color == gameState.currentPlayer.color &&
                                    gameState.turnPhase == TurnPhase.WAITING_FOR_ROLL &&
                                    player.type == com.failureludo.engine.PlayerType.HUMAN,
                                showExtraRollBadge = extraRollBadgeColor == player.color,
                                onRoll = { viewModel.rollDice() },
                                size = diceSize
                            )
                        }
                    }

                    Box(
                        modifier = Modifier.size(boardSize),
                        contentAlignment = Alignment.Center
                    ) {
                        LudoBoardCanvas(
                            allPieces        = piecesMap,
                            movablePieceIds  = movableSet,
                            playerPalette    = setup.playerColors,
                            onPieceTapped    = { piece -> viewModel.selectPiece(piece) },
                            modifier         = Modifier.matchParentSize()
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
                                diceValue = gameState.diceByPlayer[player.color],
                                isCurrent = player.color == gameState.currentPlayer.color,
                                isRollable = player.color == gameState.currentPlayer.color &&
                                    gameState.turnPhase == TurnPhase.WAITING_FOR_ROLL &&
                                    player.type == com.failureludo.engine.PlayerType.HUMAN,
                                showExtraRollBadge = extraRollBadgeColor == player.color,
                                onRoll = { viewModel.rollDice() },
                                size = diceSize
                            )
                        }
                        playersByColor[PlayerColor.YELLOW]?.let { player ->
                            SideRailDice(
                                player = player,
                                diceValue = gameState.diceByPlayer[player.color],
                                isCurrent = player.color == gameState.currentPlayer.color,
                                isRollable = player.color == gameState.currentPlayer.color &&
                                    gameState.turnPhase == TurnPhase.WAITING_FOR_ROLL &&
                                    player.type == com.failureludo.engine.PlayerType.HUMAN,
                                showExtraRollBadge = extraRollBadgeColor == player.color,
                                onRoll = { viewModel.rollDice() },
                                size = diceSize
                            )
                        }
                    }
                }
            }

        }
    }
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
            val isCurrent = player.color == state.currentPlayer.color
            PlayerChip(player = player, isActive = isCurrent, playerColors = playerColors)
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
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            player.name,
            style = MaterialTheme.typography.labelLarge,
            color = if (isActive) Color.White else Color.White.copy(0.7f),
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
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
    showExtraRollBadge: Boolean,
    onRoll: () -> Unit,
    size: androidx.compose.ui.unit.Dp
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(contentAlignment = Alignment.TopCenter) {
            DiceView(
                diceValue = diceValue,
                isRollable = isRollable,
                isCurrentTurn = isCurrent,
                onRoll = onRoll,
                size = size
            )

            if (showExtraRollBadge) {
                ExtraRollBadge()
            }
        }
        Text(
            text = player.color.displayName,
            style = MaterialTheme.typography.labelSmall,
            color = if (isCurrent) Primary else OnSurface.copy(alpha = 0.7f),
            maxLines = 1
        )
    }
}

@Composable
private fun ExtraRollBadge() {
    Surface(
        modifier = Modifier.offset(y = (-6).dp),
        shape = RoundedCornerShape(10.dp),
        color = Secondary,
        tonalElevation = 2.dp
    ) {
        Text(
            text = "EXTRA",
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = OnPrimary,
            fontWeight = FontWeight.Bold
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

                Text("Master volume: ${(settings.masterVolume * 100f).toInt()}%")
                Slider(
                    value = settings.masterVolume,
                    onValueChange = { value ->
                        onSettingsChange(settings.copy(masterVolume = value.coerceIn(0f, 1f)))
                    },
                    valueRange = 0f..1f
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}
