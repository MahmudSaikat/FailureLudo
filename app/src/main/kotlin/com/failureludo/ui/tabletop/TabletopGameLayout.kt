package com.failureludo.ui.tabletop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.failureludo.engine.*

@Composable
fun TabletopGameLayout(
    state: GameState, palette: Map<PlayerColor, Color>, diceValue: Int?, rollId: Long,
    rolling: Boolean, reducedMotion: Boolean, canRoll: Boolean, inputBlocked: Boolean,
    onRoll: () -> Unit, board: @Composable (Modifier) -> Unit, modifier: Modifier = Modifier
) {
    val tint = palette[state.currentPlayer.color] ?: TabletopStyle.Gold
    val status = when {
        rolling -> "Rolling…"
        inputBlocked -> "Moving…"
        state.turnPhase == TurnPhase.GAME_OVER -> "Game complete"
        state.turnPhase == TurnPhase.NO_MOVES_AVAILABLE -> "No legal moves · next turn"
        state.currentPlayer.type == PlayerType.BOT -> "Thinking…"
        state.turnPhase == TurnPhase.WAITING_FOR_PIECE_SELECTION -> "Choose a marked pawn"
        else -> "Tap the die to roll"
    }
    val players = state.players.associateBy { it.color }
    @Composable fun Rail(colors: List<PlayerColor>, modifier: Modifier = Modifier) {
        Row(modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            colors.forEach { color ->
                players[color]?.let { player -> PlayerPanel(player, player.id == state.currentPlayer.id,
                    palette[color] ?: Color.Gray, state.mode, Modifier.weight(1f)) }
            }
        }
    }
    @Composable fun Controls(modifier: Modifier = Modifier) {
        Surface(modifier.fillMaxWidth(), color=TabletopStyle.Panel, shape=RoundedCornerShape(24.dp)) {
            Row(Modifier.padding(horizontal=16.dp, vertical=8.dp), verticalAlignment=Alignment.CenterVertically,
                horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                Box(Modifier.border(1.dp,tint.copy(alpha=.6f),RoundedCornerShape(20.dp)).padding(2.dp)) {
                    TabletopDice(diceValue, rollId, rolling, reducedMotion, canRoll, onRoll)
                }
                Column(Modifier.weight(1f), verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    Text(if(rolling) "DICE IN PLAY" else "CURRENT TURN", color=TabletopStyle.Muted,
                        fontSize=10.sp, letterSpacing=1.8.sp, fontWeight=FontWeight.Bold)
                    Text(state.currentPlayer.name, color=TabletopStyle.Paper, fontSize=19.sp,
                        fontWeight=FontWeight.Bold, maxLines=1, overflow=TextOverflow.Ellipsis)
                    Text(status, color=TabletopStyle.Muted, fontSize=12.sp)
                }
            }
        }
    }
    @Composable fun BoardFrame(boardModifier: Modifier) {
        Surface(boardModifier, color=Color(0xFFE2D8C4), shape=RoundedCornerShape(18.dp), shadowElevation=12.dp) {
            Box(Modifier.padding(7.dp).clip(RoundedCornerShape(12.dp))) { board(Modifier.fillMaxSize()) }
        }
    }
    BoxWithConstraints(modifier.padding(horizontal=16.dp, vertical=8.dp), contentAlignment=Alignment.Center) {
        if(maxWidth > maxHeight * 1.25f) {
            val boardSide = minOf(maxHeight, maxWidth * .58f)
            Row(verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.spacedBy(20.dp)) {
                BoardFrame(Modifier.size(boardSide))
                Column(Modifier.weight(1f), verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    Rail(listOf(PlayerColor.RED,PlayerColor.BLUE))
                    Rail(listOf(PlayerColor.GREEN,PlayerColor.YELLOW))
                    Controls()
                }
            }
        } else {
            val boardSide = minOf(maxWidth, (maxHeight-242.dp).coerceAtLeast(100.dp))
            Column(horizontalAlignment=Alignment.CenterHorizontally, verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Rail(listOf(PlayerColor.RED,PlayerColor.BLUE))
                BoardFrame(Modifier.size(boardSide))
                Rail(listOf(PlayerColor.GREEN,PlayerColor.YELLOW))
                Controls()
            }
        }
    }
}

@Composable
private fun PlayerPanel(player: Player, active: Boolean, tint: Color, mode: GameMode, modifier: Modifier) {
    val edge = if(active && player.isActive) TabletopStyle.Gold else Color.White.copy(alpha=.10f)
    Row(modifier.heightIn(min=52.dp).border(1.dp,edge,RoundedCornerShape(14.dp))
        .background(if(active && player.isActive) TabletopStyle.Panel else Color.Transparent,RoundedCornerShape(14.dp))
        .padding(horizontal=10.dp,vertical=8.dp), verticalAlignment=Alignment.CenterVertically,
        horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        Canvas(Modifier.size(24.dp)) { drawIdentity(center,size.width*.29f,if(player.isActive) tint else TabletopStyle.Muted.copy(alpha=.35f),player.color.ordinal) }
        Column(Modifier.weight(1f)) {
            Text(if(player.isActive) player.name else "Empty seat",color=if(player.isActive) TabletopStyle.Paper else TabletopStyle.Muted,
                fontSize=12.sp,fontWeight=FontWeight.SemiBold,maxLines=1,overflow=TextOverflow.Ellipsis)
            val detail = if(!player.isActive) "—" else buildString {
                append(if(active) "Playing" else if(player.type==PlayerType.BOT) "Bot" else "Human")
                append(" · ${player.finishedPieceCount}/4")
                if(mode==GameMode.TEAM) append(" · T${player.color.teamIndex+1}")
            }
            Text(detail,color=TabletopStyle.Muted,fontSize=10.sp,maxLines=1)
        }
    }
}
