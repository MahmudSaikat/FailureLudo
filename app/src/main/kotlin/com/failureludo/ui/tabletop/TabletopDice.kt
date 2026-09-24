package com.failureludo.ui.tabletop

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.*

private data class Point3(val x: Float, val y: Float, val z: Float) {
    operator fun plus(p: Point3) = Point3(x+p.x, y+p.y, z+p.z)
    operator fun times(s: Float) = Point3(x*s, y*s, z*s)
}
private data class DieFace(val value: Int, val normal: Point3, val u: Point3, val v: Point3)
private val faces = listOf(
    DieFace(1, Point3(0f,0f,1f), Point3(1f,0f,0f), Point3(0f,1f,0f)),
    DieFace(6, Point3(0f,0f,-1f), Point3(-1f,0f,0f), Point3(0f,1f,0f)),
    DieFace(3, Point3(1f,0f,0f), Point3(0f,0f,-1f), Point3(0f,1f,0f)),
    DieFace(4, Point3(-1f,0f,0f), Point3(0f,0f,1f), Point3(0f,1f,0f)),
    DieFace(2, Point3(0f,-1f,0f), Point3(1f,0f,0f), Point3(0f,0f,1f)),
    DieFace(5, Point3(0f,1f,0f), Point3(1f,0f,0f), Point3(0f,0f,-1f))
)

/** One fixed cube, not unrelated pip frames. Opposite faces always sum to seven. */
@Composable
fun TabletopDice(value: Int?, rollId: Long, rolling: Boolean, reducedMotion: Boolean,
    enabled: Boolean, onRoll: () -> Unit, modifier: Modifier = Modifier) {
    val phase = remember { Animatable(1f) }
    var startX by remember { mutableFloatStateOf(0f) }
    var startY by remember { mutableFloatStateOf(0f) }
    var endX by remember { mutableFloatStateOf(0f) }
    var endY by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(rollId, value) {
        startX = endX % 360f
        startY = endY % 360f
        val target = dieOrientation(value ?: 1)
        val direction = if (rollId % 2L == 0L) 1 else -1
        endX = target.first - 10f + 720f * direction
        endY = target.second + 14f + 360f * direction
        if (rolling && !reducedMotion) {
            phase.snapTo(0f)
            phase.animateTo(1f, tween(600, easing = LinearEasing))
        } else phase.snapTo(1f)
    }
    Canvas(modifier.size(88.dp)
        .semantics { contentDescription = if (rolling) "Rolling dice" else if (enabled) "Roll dice" else "Dice: ${value ?: "not rolled"}" }
        .clickable(enabled = enabled, role = Role.Button, onClickLabel = "Roll dice", onClick = onRoll)) {
        val t = phase.value
        val spin = (t / .78f).coerceIn(0f, 1f)
        val ease = 1f - (1f-spin).pow(2)
        val wobble = if (t > .78f && t < 1f) sin((t-.78f)/.22f * PI * 2).toFloat() * (1f-t) * 32 else 0f
        val xAngle = (startX + (endX-startX)*ease + wobble) * PI.toFloat()/180
        val yAngle = (startY + (endY-startY)*ease) * PI.toFloat()/180
        val lift = if (t < .78f) sin(t/.78f * PI).toFloat() * size.height*.13f else 0f
        val mid = center - Offset(0f, lift)
        val scale = size.minDimension * .29f
        drawOval(Color.Black.copy(alpha=.24f), Offset(center.x-scale, center.y+scale*.78f), Size(scale*2.1f, scale*.42f))
        fun rotate(p: Point3): Point3 {
            val yy = p.y*cos(xAngle)-p.z*sin(xAngle)
            val zz = p.y*sin(xAngle)+p.z*cos(xAngle)
            return Point3(p.x*cos(yAngle)+zz*sin(yAngle), yy, -p.x*sin(yAngle)+zz*cos(yAngle))
        }
        fun project(p: Point3): Offset {
            val r = rotate(p)
            val perspective = 5f/(5f-r.z)
            return mid+Offset(r.x*scale*perspective, r.y*scale*perspective)
        }
        fun polygon(points: List<Point3>): Path = Path().apply {
            points.forEachIndexed { i,p -> val q=project(p); if(i==0) moveTo(q.x,q.y) else lineTo(q.x,q.y) }; close()
        }
        fun roundedFace(face: DieFace, extent: Float, radius: Float): Path {
            fun point(u: Float, v: Float) = project(face.normal + face.u*u + face.v*v)
            return Path().apply {
                val first = point(-extent+radius, -extent)
                moveTo(first.x, first.y)
                val edges = listOf(
                    floatArrayOf(extent-radius,-extent, extent,-extent, extent,-extent+radius),
                    floatArrayOf(extent,extent-radius, extent,extent, extent-radius,extent),
                    floatArrayOf(-extent+radius,extent, -extent,extent, -extent,extent-radius),
                    floatArrayOf(-extent,-extent+radius, -extent,-extent, -extent+radius,-extent))
                edges.forEach { edge ->
                    val a=point(edge[0],edge[1]); val b=point(edge[2],edge[3]); val d=point(edge[4],edge[5])
                    lineTo(a.x,a.y); quadraticTo(b.x,b.y,d.x,d.y)
                }
                close()
            }
        }
        faces.filter { rotate(it.normal).z > .01f }.sortedBy { rotate(it.normal).z }.forEach { face ->
            val n = rotate(face.normal)
            val light = (.72f + n.z*.22f - n.y*.06f - n.x*.04f).coerceIn(.55f,1f)
            val outer = roundedFace(face, 1f, .15f)
            drawPath(outer, Color(.85f*light,.85f*light,.79f*light))
            val inner = roundedFace(face, .9f, .12f)
            drawPath(inner, Brush.linearGradient(listOf(Color(light,light,.97f*light),Color(.91f*light,.90f*light,.84f*light))))
            drawPath(outer, Color.White.copy(alpha=.45f), style=Stroke(size.width*.007f))
            val dots = when(face.value) {
                1 -> listOf(0f to 0f)
                2 -> listOf(-.48f to -.48f, .48f to .48f)
                3 -> listOf(-.48f to -.48f, 0f to 0f, .48f to .48f)
                4 -> listOf(-.48f to -.48f, -.48f to .48f, .48f to -.48f, .48f to .48f)
                5 -> listOf(-.48f to -.48f, -.48f to .48f, 0f to 0f, .48f to -.48f, .48f to .48f)
                else -> listOf(-.48f to -.48f, -.48f to 0f, -.48f to .48f, .48f to -.48f, .48f to 0f, .48f to .48f)
            }
            dots.forEach { (u,v) ->
                val dot = polygon(List(20) { i ->
                    val a=i*PI.toFloat()*2/20
                    face.normal + face.u*(u+cos(a)*.13f) + face.v*(v+sin(a)*.13f)
                })
                drawPath(dot, TabletopStyle.Ink)
            }
        }
    }
}

internal fun dieOrientation(value: Int): Pair<Float, Float> = when(value) {
    2 -> -90f to 0f
    3 -> 0f to -90f
    4 -> 0f to 90f
    5 -> 90f to 0f
    6 -> 0f to 180f
    else -> 0f to 0f
}
