package com.failureludo.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import com.failureludo.ui.theme.gardenBackground
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.failureludo.ui.tabletop.*
import com.failureludo.ui.theme.*

@Composable
fun HomeScreen(onNewGame: () -> Unit, onResume: () -> Unit, onHistory: () -> Unit,
    hasActiveGame: Boolean, hasHistoryRecords: Boolean, isSessionRestored: Boolean) {
    Box(Modifier.fillMaxSize().gardenBackground(dark = true)
        .safeDrawingPadding(), contentAlignment=Alignment.Center) {
        Column(Modifier.widthIn(max=420.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(32.dp),
            horizontalAlignment=Alignment.CenterHorizontally, verticalArrangement=Arrangement.spacedBy(18.dp)) {
            Text("A LITTLE PLAY, A LOT OF JOY",color=TabletopStyle.Gold,fontSize=11.sp,letterSpacing=3.sp)
            TabletopDice(5,0L,false,false,false,{ },Modifier.size(100.dp))
            Text("LUDO",color=TabletopStyle.Paper,fontSize=64.sp,fontWeight=FontWeight.Black,letterSpacing=8.sp)
            Text("Failure Edition",color=TabletopStyle.Muted,fontSize=18.sp)
            Spacer(Modifier.height(24.dp))
            if(!isSessionRestored) CircularProgressIndicator(color=TabletopStyle.Gold)
            else {
                if(hasActiveGame) Button(onClick=onResume,modifier=Modifier.fillMaxWidth().height(56.dp),
                    shape=RoundedCornerShape(16.dp),colors=ButtonDefaults.buttonColors(containerColor=TabletopStyle.Gold,contentColor=TabletopStyle.Ink)) {
                    Text("Resume your game",fontWeight=FontWeight.Bold)
                }
                Button(onClick=onNewGame,modifier=Modifier.fillMaxWidth().height(56.dp),shape=RoundedCornerShape(16.dp),
                    colors=ButtonDefaults.buttonColors(containerColor=TabletopStyle.Paper,contentColor=TabletopStyle.Ink)) {
                    Text("New game",fontWeight=FontWeight.Bold)
                }
                OutlinedButton(onClick=onHistory, enabled=hasHistoryRecords,
                    modifier=Modifier.fillMaxWidth().height(52.dp), shape=RoundedCornerShape(16.dp),
                    colors=ButtonDefaults.outlinedButtonColors(contentColor=TabletopStyle.Paper,
                        disabledContentColor=TabletopStyle.Muted.copy(alpha=.45f))) {
                    Text(if (hasHistoryRecords) "Your games & replays" else "No saved games yet")
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement=Arrangement.spacedBy(18.dp)) {
                listOf(LudoRed,LudoBlue,LudoYellow,LudoGreen).forEachIndexed { index,tint ->
                    Canvas(Modifier.size(18.dp)) { drawIdentity(center,size.width*.32f,tint,index) }
                }
            }
            Text("Pass & play  ·  Play against bots",color=TabletopStyle.Muted,fontSize=12.sp)
        }
    }
}
