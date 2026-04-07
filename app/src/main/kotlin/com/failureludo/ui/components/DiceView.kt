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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.failureludo.ui.theme.Primary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun DiceView(
    diceValue: Int?,
    isRollable: Boolean,
    isCurrentTurn: Boolean,
    onRoll: () -> Unit,
    size: Dp = 72.dp,
    contentDescription: String = "Dice",
    modifier: Modifier = Modifier
) {
    val isInactive = !isCurrentTurn
    val scope = rememberCoroutineScope()

    val baseScale by animateFloatAsState(
        targetValue  = if (isCurrentTurn) 1f else 0.92f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label        = "dice_scale"
    )

    val rollScale = remember { Animatable(1f) }
    val rollRotation = remember { Animatable(0f) }
    val rollTiltX = remember { Animatable(0f) }
    val rollTiltY = remember { Animatable(0f) }
    var displayedValue by remember { mutableStateOf(diceValue) }

    LaunchedEffect(diceValue) {
        if (diceValue == null) return@LaunchedEffect

        val spinTurns = 1 + (diceValue % 2)
        val spinDirection = if (diceValue % 2 == 0) 1f else -1f
        val spinAngle = 360f * spinTurns * spinDirection
        val spinDuration = 220 + (spinTurns * 70)
        val tiltBase = 18f + (diceValue * 2f)
        val tiltDirection = if (diceValue % 2 == 0) -1f else 1f
        val tiltTargetX = tiltBase * tiltDirection
        val tiltTargetY = tiltBase * -tiltDirection

        val scrambleFaces = listOf(1, 2, 3, 4, 5, 6).filter { it != diceValue }
        val scrambleStepMillis = 50
        val scrambleDuration = (spinDuration - scrambleStepMillis).coerceAtLeast(scrambleStepMillis)
        val scrambleSteps = (scrambleDuration / scrambleStepMillis).coerceAtLeast(1)

        rollScale.snapTo(0.85f)
        rollRotation.snapTo(0f)
        rollTiltX.snapTo(0f)
        rollTiltY.snapTo(0f)
        displayedValue = scrambleFaces.firstOrNull() ?: diceValue

        kotlinx.coroutines.coroutineScope {
            launch {
                repeat(scrambleSteps) { index ->
                    val face = scrambleFaces[index % scrambleFaces.size]
                    displayedValue = face
                    delay(scrambleStepMillis.toLong())
                }
                displayedValue = diceValue
            }
            launch {
                rollScale.animateTo(
                    targetValue = 1.08f,
                    animationSpec = tween(durationMillis = 120)
                )
                rollScale.animateTo(
                    targetValue = 1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                )
            }
            launch {
                rollRotation.animateTo(
                    targetValue = spinAngle,
                    animationSpec = tween(
                        durationMillis = spinDuration,
                        easing = FastOutSlowInEasing
                    )
                )
                rollRotation.snapTo(spinAngle % 360f)
            }
            launch {
                rollTiltX.animateTo(
                    targetValue = tiltTargetX,
                    animationSpec = tween(
                        durationMillis = (spinDuration * 0.6f).toInt(),
                        easing = FastOutSlowInEasing
                    )
                )
                rollTiltX.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(
                        durationMillis = (spinDuration * 0.4f).toInt(),
                        easing = FastOutSlowInEasing
                    )
                )
            }
            launch {
                rollTiltY.animateTo(
                    targetValue = tiltTargetY,
                    animationSpec = tween(
                        durationMillis = (spinDuration * 0.5f).toInt(),
                        easing = FastOutSlowInEasing
                    )
                )
                rollTiltY.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(
                        durationMillis = (spinDuration * 0.5f).toInt(),
                        easing = FastOutSlowInEasing
                    )
                )
            }
        }
    }

    val pulseEnabled = isCurrentTurn
    val pulseTransition = rememberInfiniteTransition(label = "dice_turn_pulse")
    val pulseScale by pulseTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (pulseEnabled) 1.09f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dice_pulse_scale"
    )
    val glowAlpha by pulseTransition.animateFloat(
        initialValue = 0.14f,
        targetValue = if (pulseEnabled) 0.36f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dice_glow_alpha"
    )

    Box(
        modifier = modifier.size(size + 14.dp),
        contentAlignment = Alignment.Center
    ) {
        if (pulseEnabled) {
            Box(
                modifier = Modifier
                    .size(size + 16.dp)
                    .scale(pulseScale)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Primary.copy(alpha = glowAlpha * 0.45f))
            )

            Box(
                modifier = Modifier
                    .size(size + 8.dp)
                    .scale((pulseScale + 1f) / 2f)
                    .clip(RoundedCornerShape(18.dp))
                    .border(
                        width = 3.dp,
                        color = Primary.copy(alpha = glowAlpha),
                        shape = RoundedCornerShape(18.dp)
                    )
            )
        }

        Box(
            modifier = Modifier
                .size(size)
                .scale(baseScale * rollScale.value)
                .graphicsLayer {
                    rotationZ = rollRotation.value
                    rotationX = rollTiltX.value
                    rotationY = rollTiltY.value
                    cameraDistance = 12f * density
                }
                .alpha(if (isInactive) 0.48f else 1f)
                .clip(RoundedCornerShape(14.dp))
                .background(if (isInactive) Color(0xFFD0D0D0) else Color.White)
                .border(
                    width  = if (isCurrentTurn) 3.dp else 2.dp,
                    color  = if (isCurrentTurn) Primary.copy(alpha = 0.95f) else Color(0xFF7D7D7D),
                    shape  = RoundedCornerShape(14.dp)
                )
                .semantics {
                    this.contentDescription = contentDescription
                    if (isRollable) {
                        role = Role.Button
                    }
                }
                .then(
                    if (isRollable) {
                        Modifier.clickable {
                            // Immediate visual confirmation that the tap was registered.
                            scope.launch {
                                rollScale.snapTo(0.86f)
                                rollRotation.snapTo(-14f)

                                rollScale.animateTo(
                                    targetValue = 1.14f,
                                    animationSpec = tween(durationMillis = 95)
                                )
                                rollRotation.animateTo(
                                    targetValue = 16f,
                                    animationSpec = tween(durationMillis = 95)
                                )

                                rollScale.animateTo(
                                    targetValue = 1f,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessMedium
                                    )
                                )
                                rollRotation.animateTo(
                                    targetValue = 0f,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessLow
                                    )
                                )
                            }
                            onRoll()
                        }
                    } else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
        if (diceValue == null) {
            // Show "?" when waiting for roll
            Text(
                text       = "?",
                fontSize   = (size.value * 0.5f).sp,
                fontWeight = FontWeight.Bold,
                color      = if (isInactive) Color(0xFF6F6F6F) else Color.Black
            )
        } else {
            DiceFace(
                value = displayedValue ?: diceValue,
                size = size,
                dotColor = if (isInactive) Color(0xFF5F5F5F) else Color.Black
            )
        }
        }
    }
}

@Composable
private fun DiceFace(value: Int, size: Dp, dotColor: Color) {
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
                    .background(dotColor)
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
