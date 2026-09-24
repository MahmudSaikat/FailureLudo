package com.failureludo.ui.theme

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate

/** Locally drawn botanical artwork: scales to every screen and needs no bitmap or frame clock. */
fun Modifier.gardenBackground(dark: Boolean = false): Modifier = drawBehind {
    val colors = if (dark) listOf(Color(0xFF352440), Color(0xFF62445F), Color(0xFF374762))
        else listOf(Color(0xFFF1E6FB), Color(0xFFFFEDF2), Color(0xFFFFEBD9))
    drawRect(Brush.linearGradient(colors))
    val unit = size.minDimension
    listOf(Offset(size.width * .95f, size.height * .12f),
        Offset(size.width * .02f, size.height * .82f)).forEachIndexed { index, origin ->
        val tint = if (dark) Color(0xFFECC3D9) else Color(0xFFAE7BAE)
        val radius = unit * .42f
        drawCircle(Brush.radialGradient(listOf(tint.copy(alpha = .15f), Color.Transparent),
            origin, radius), radius, origin)
        rotate(if (index == 0) -28f else 155f, origin) {
            drawLine(tint.copy(alpha = .20f), origin, origin + Offset(0f, unit * .62f),
                unit * .003f, StrokeCap.Round)
            repeat(7) { leaf ->
                val y = origin.y + leaf * unit * .085f
                val side = if (leaf % 2 == 0) -1f else 1f
                val at = Offset(origin.x + side * unit * .065f, y)
                rotate(side * 38f, at) {
                    drawOval(tint.copy(alpha = .12f), at - Offset(unit * .075f, unit * .025f),
                        Size(unit * .15f, unit * .05f))
                }
            }
        }
    }
    repeat(18) { i ->
        val at = Offset(size.width * ((i * 37 % 101) / 100f), size.height * ((i * 61 % 103) / 102f))
        drawCircle(Color.White.copy(alpha = if (dark) .12f else .5f), unit * .004f, at)
    }
}
