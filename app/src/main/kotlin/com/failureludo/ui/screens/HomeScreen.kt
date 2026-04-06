package com.failureludo.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.failureludo.ui.theme.*

@Composable
fun HomeScreen(
    onNewGame: () -> Unit,
    onResume: () -> Unit,
    onHistory: () -> Unit,
    hasActiveGame: Boolean,
    hasHistoryRecords: Boolean,
    isSessionRestored: Boolean
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.padding(32.dp)
        ) {

            // Title Section
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "🎲",
                    fontSize = 72.sp
                )
                Text(
                    text = "LUDO",
                    style = MaterialTheme.typography.displayLarge,
                    color = Primary,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 6.sp
                )
                Text(
                    text = "Failure Edition",
                    style = MaterialTheme.typography.titleMedium,
                    color = Secondary,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action Buttons
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (!isSessionRestored) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else if (hasActiveGame) {
                    Button(
                        onClick = onResume,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Primary)
                    ) {
                        Text(
                            text = "Resume Game",
                            style = MaterialTheme.typography.titleMedium,
                            color = OnPrimary
                        )
                    }

                    OutlinedButton(
                        onClick = onNewGame,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Primary)
                    ) {
                        Text(
                            text = "New Game",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }

                    if (hasHistoryRecords) {
                        OutlinedButton(
                            onClick = onHistory,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Primary)
                        ) {
                            Text(
                                text = "Game History",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                } else {
                    Button(
                        onClick = onNewGame,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Primary)
                    ) {
                        Text(
                            text = "New Game",
                            style = MaterialTheme.typography.titleMedium,
                            color = OnPrimary
                        )
                    }

                    if (hasHistoryRecords) {
                        OutlinedButton(
                            onClick = onHistory,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Primary)
                        ) {
                            Text(
                                text = "Game History",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Colour patch row (purely decorative)
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.clip(RoundedCornerShape(12.dp))
            ) {
                listOf(LudoRed, LudoBlue, LudoYellow, LudoGreen).forEach { color ->
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(color)
                    )
                }
            }
        }
    }
}
