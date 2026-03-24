package com.failureludo.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun DiceView(
    diceValue: Int?,
    isRollable: Boolean,
    onRoll: () -> Unit,
    size: Dp = 72.dp,
    modifier: Modifier = Modifier
) {
    val scale by animateFloatAsState(
        targetValue  = if (isRollable) 1f else 0.95f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label        = "dice_scale"
    )

    Box(
        modifier = modifier
            .size(size)
            .scale(scale)
            .clip(RoundedCornerShape(14.dp))
            .background(if (isRollable) Color.White else Color.White.copy(alpha = 0.6f))
            .border(
                width  = if (isRollable) 3.dp else 2.dp,
                color  = if (isRollable) Color.Black else Color.Gray.copy(alpha = 0.4f),
                shape  = RoundedCornerShape(14.dp)
            )
            .then(if (isRollable) Modifier.clickable(onClick = onRoll) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        if (diceValue == null) {
            // Show "?" when waiting for roll
            Text(
                text       = "?",
                fontSize   = (size.value * 0.5f).sp,
                fontWeight = FontWeight.Bold,
                color      = if (isRollable) Color.Black else Color.Gray
            )
        } else {
            DiceFace(value = diceValue, size = size)
        }
    }
}

@Composable
private fun DiceFace(value: Int, size: Dp) {
    val dotSize = (size.value * 0.14f).dp

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding((size.value * 0.12f).dp)
    ) {
        val dots = dotPositions(value)
        dots.forEach { (xFrac, yFrac) ->
            Box(
                modifier = Modifier
                    .size(dotSize)
                    .offset(
                        x = (size.value * xFrac * 0.76f - dotSize.value / 2).dp,
                        y = (size.value * yFrac * 0.76f - dotSize.value / 2).dp
                    )
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(Color.Black)
            )
        }
    }
}

/**
 * Returns (xFraction 0..1, yFraction 0..1) positions for each dot on the face.
 */
private fun dotPositions(value: Int): List<Pair<Float, Float>> = when (value) {
    1 -> listOf(0.5f to 0.5f)
    2 -> listOf(0.25f to 0.25f, 0.75f to 0.75f)
    3 -> listOf(0.25f to 0.25f, 0.5f to 0.5f, 0.75f to 0.75f)
    4 -> listOf(0.25f to 0.25f, 0.75f to 0.25f, 0.25f to 0.75f, 0.75f to 0.75f)
    5 -> listOf(0.25f to 0.25f, 0.75f to 0.25f, 0.5f to 0.5f, 0.25f to 0.75f, 0.75f to 0.75f)
    6 -> listOf(
        0.25f to 0.2f, 0.75f to 0.2f,
        0.25f to 0.5f, 0.75f to 0.5f,
        0.25f to 0.8f, 0.75f to 0.8f
    )
    else -> emptyList()
}
