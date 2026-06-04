package com.failureludo.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Web
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.failureludo.data.online.RoomPlayer
import com.failureludo.engine.GameState
import com.failureludo.engine.Piece
import com.failureludo.engine.PlayerColor
import com.failureludo.engine.TurnPhase
import com.failureludo.ui.components.DiceView
import com.failureludo.ui.components.LudoBoardCanvas
import com.failureludo.ui.components.TappedCellPieces
import com.failureludo.ui.theme.*
import com.failureludo.viewmodel.OnlineGameUiState
import com.failureludo.viewmodel.OnlineGameViewModel
import kotlinx.coroutines.flow.collectLatest

private val colorDots = mapOf(
    PlayerColor.RED    to LudoRed,
    PlayerColor.BLUE   to LudoBlue,
    PlayerColor.YELLOW to LudoYellow,
    PlayerColor.GREEN  to LudoGreen
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnlineGameBoardScreen(
    roomId: String,
    viewModel: OnlineGameViewModel,
    onGameOver: () -> Unit,
    onQuit: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showQuitDialog by remember { mutableStateOf(false) }

    LaunchedEffect(roomId) { viewModel.initGame(roomId) }

    LaunchedEffect(uiState.gameState?.isGameOver) {
        if (uiState.gameState?.isGameOver == true) onGameOver()
    }

    LaunchedEffect(Unit) {
        viewModel.errors.collectLatest { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Online Game") },
                navigationIcon = {
                    IconButton(onClick = { showQuitDialog = true }) {
                        Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Quit")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Primary,
                    titleContentColor = OnPrimary,
                    navigationIconContentColor = OnPrimary
                )
            )
        },
        containerColor = Background
    ) { padding ->
        val gameState = uiState.gameState

        if (gameState == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Primary)
            }
        } else {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                val sizing = computeBoardLayoutSizing(maxWidth, maxHeight)

                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {

                    // ── Opponent rail ─────────────────────────────────────────
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(sizing.railHeight),
                        contentAlignment = Alignment.Center
                    ) {
                        OpponentRail(
                            gameState   = gameState,
                            myColor     = uiState.myColor,
                            roomPlayers = uiState.roomPlayers
                        )
                    }

                    // ── Board ─────────────────────────────────────────────────
                    val allPieces = gameState.players.associate { it.color to it.pieces }
                    val movablePieceIds: Set<Pair<PlayerColor, Int>> =
                        if (uiState.canSelectPiece) {
                            gameState.movablePieces.map { it.color to it.id }.toSet()
                        } else emptySet()

                    LudoBoardCanvas(
                        allPieces       = allPieces,
                        movablePieceIds = movablePieceIds,
                        onCellPiecesTapped = { tapped ->
                            if (uiState.canSelectPiece) viewModel.onPieceTapped(tapped)
                        },
                        modifier = Modifier.size(sizing.boardSize)
                    )

                    // ── Local player rail ─────────────────────────────────────
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(sizing.railHeight),
                        contentAlignment = Alignment.Center
                    ) {
                        LocalPlayerRail(
                            gameState   = gameState,
                            uiState     = uiState,
                            onRoll      = { viewModel.rollDice() }
                        )
                    }
                }
            }
        }
    }

    if (showQuitDialog) {
        AlertDialog(
            onDismissRequest = { showQuitDialog = false },
            title = { Text("Leave game?") },
            text  = { Text("Your opponent will continue without you.") },
            confirmButton = {
                TextButton(onClick = { showQuitDialog = false; onQuit() }) {
                    Text("Leave", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showQuitDialog = false }) { Text("Stay") }
            }
        )
    }
}

// ── Sub-composables ───────────────────────────────────────────────────────────

@Composable
private fun OpponentRail(
    gameState: GameState,
    myColor: PlayerColor?,
    roomPlayers: List<RoomPlayer>
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        gameState.players
            .filter { it.isActive && it.color != myColor }
            .forEach { player ->
                val roomPlayer = roomPlayers.find { it.color == player.color.name }
                val isCurrentTurn = gameState.currentPlayer.color == player.color
                val dotColor = colorDots[player.color] ?: Color.Gray

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .background(
                            color = if (isCurrentTurn) dotColor.copy(alpha = 0.15f) else Color.Transparent,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(dotColor)
                    )
                    Icon(
                        imageVector = if (roomPlayer?.platform == "android") Icons.Default.PhoneAndroid else Icons.Default.Web,
                        contentDescription = null,
                        tint = Secondary,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = player.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isCurrentTurn) OnSurface else Secondary,
                        fontWeight = if (isCurrentTurn) FontWeight.Bold else FontWeight.Normal
                    )
                    if (isCurrentTurn) {
                        val diceVal = gameState.lastDice?.value
                        if (diceVal != null) {
                            Text(
                                text = "[$diceVal]",
                                style = MaterialTheme.typography.labelSmall,
                                color = dotColor,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
    }
}

@Composable
private fun LocalPlayerRail(
    gameState: GameState,
    uiState: OnlineGameUiState,
    onRoll: () -> Unit
) {
    val myColor = uiState.myColor
    val myPlayer = gameState.players.find { it.color == myColor }
    val dotColor = colorDots[myColor] ?: Color.Gray
    val isMyTurn = uiState.isMyTurn
    val diceValue = if (isMyTurn) gameState.lastDice?.value else null

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Player info
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Text(
                text = myPlayer?.name ?: "You",
                style = MaterialTheme.typography.labelMedium,
                color = OnSurface,
                fontWeight = FontWeight.SemiBold
            )
        }

        // Turn status
        Text(
            text = when {
                gameState.isGameOver -> "Game over"
                isMyTurn && gameState.turnPhase == TurnPhase.WAITING_FOR_ROLL -> "Your turn — roll!"
                isMyTurn && gameState.turnPhase == TurnPhase.WAITING_FOR_PIECE_SELECTION -> "Pick a piece"
                else -> "Waiting…"
            },
            style = MaterialTheme.typography.labelSmall,
            color = if (isMyTurn) Primary else Secondary,
            textAlign = TextAlign.Center
        )

        // Dice
        DiceView(
            diceValue       = diceValue,
            isRollable      = uiState.canRoll,
            isCurrentTurn   = isMyTurn,
            onRoll          = onRoll,
            size            = 48.dp,
            contentDescription = "Dice"
        )
    }
}
