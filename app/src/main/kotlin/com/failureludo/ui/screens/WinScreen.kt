package com.failureludo.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.failureludo.engine.GameMode
import com.failureludo.engine.PlayerColor
import com.failureludo.ui.theme.*
import com.failureludo.viewmodel.GameViewModel

@Composable
fun WinScreen(
    viewModel: GameViewModel,
    onPlayAgain: () -> Unit,
    onMainMenu: () -> Unit
) {
    val state by viewModel.gameState.collectAsState()
    val setup by viewModel.setupState.collectAsState()
    val winners = state?.winners ?: return
    val winnerPlayers = state?.players
        ?.filter { it.isActive && it.id in winners }
        .orEmpty()

    val isTeamWin = state?.mode == GameMode.TEAM && winners.size > 1

    // Pulsing scale animation for the trophy
    val infiniteTransition = rememberInfiniteTransition(label = "win_pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue   = 1f,
        targetValue    = 1.12f,
        animationSpec  = infiniteRepeatable(
            animation  = tween(700, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "trophy_scale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.padding(32.dp)
        ) {

            // Trophy
            Text(text = "🏆", fontSize = (80 * scale).sp)

            // Win title
            Text(
                text  = if (isTeamWin) {
                    val teamNames = winnerPlayers.joinToString(" & ") { it.name }
                    if (teamNames.isBlank()) "Team Wins!" else "$teamNames Win!"
                } else {
                    "${winnerPlayers.firstOrNull()?.name ?: "Winner"} Wins!"
                },
                style = MaterialTheme.typography.headlineLarge,
                color = Primary,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            // Winner colour circles
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
            ) {
                winnerPlayers.forEach { winner ->
                    WinnerColorBadge(player = winner, playerColors = setup.playerColors)
                }
            }

            // Score summary
            state?.players?.filter { it.isActive }?.let { players ->
                Card(
                    modifier  = Modifier.fillMaxWidth(),
                    shape     = RoundedCornerShape(16.dp),
                    colors    = CardDefaults.cardColors(containerColor = Surface),
                    elevation = CardDefaults.cardElevation(4.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            "Final Scores",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Primary
                        )
                        players
                            .sortedByDescending { it.finishedPieceCount }
                            .forEach { player ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(playerColor(player.color, setup.playerColors)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            player.id.value.toString(),
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.labelLarge
                                        )
                                    }
                                    Text(
                                        player.name,
                                        modifier = Modifier.weight(1f),
                                        style = MaterialTheme.typography.bodyLarge,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    // Piece progress dots
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        repeat(4) { idx ->
                                            val finished = idx < player.finishedPieceCount
                                            Box(
                                                modifier = Modifier
                                                    .size(14.dp)
                                                    .clip(CircleShape)
                                                    .background(
                                                        if (finished) playerColor(player.color, setup.playerColors)
                                                        else playerColor(player.color, setup.playerColors).copy(0.2f)
                                                    )
                                            )
                                        }
                                    }
                                }
                            }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Buttons
            Button(
                onClick   = onPlayAgain,
                modifier  = Modifier.fillMaxWidth().height(56.dp),
                shape     = RoundedCornerShape(16.dp),
                colors    = ButtonDefaults.buttonColors(containerColor = Primary)
            ) {
                Text("Play Again", style = MaterialTheme.typography.titleMedium, color = OnPrimary)
            }

            OutlinedButton(
                onClick   = onMainMenu,
                modifier  = Modifier.fillMaxWidth().height(56.dp),
                shape     = RoundedCornerShape(16.dp),
                colors    = ButtonDefaults.outlinedButtonColors(contentColor = Primary)
            ) {
                Text("Main Menu", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun WinnerColorBadge(player: com.failureludo.engine.Player, playerColors: Map<PlayerColor, Color>) {
    val infiniteTransition = rememberInfiniteTransition(label = "badge_${player.id.value}")
    val scale by infiniteTransition.animateFloat(
        initialValue  = 1f,
        targetValue   = 1.15f,
        animationSpec = infiniteRepeatable(
            animation  = tween(600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "badge_scale_${player.id.value}"
    )

    Box(
        modifier = Modifier
            .size((56 * scale).dp)
            .clip(CircleShape)
            .background(playerColor(player.color, playerColors))
            .semantics {
                contentDescription = "Winner ${player.name}, player ${player.id.value}"
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text  = player.id.value.toString(),
            color = Color.White,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 24.sp
        )
    }
}
