package com.failureludo.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.rememberTextMeasurer
import com.failureludo.engine.Piece
import com.failureludo.engine.PiecePosition
import com.failureludo.engine.PlayerColor
import com.failureludo.ui.screens.playerColor
import com.failureludo.ui.screens.playerColorLight
import com.failureludo.ui.theme.*

private const val GRID = 15

@Composable
fun LudoBoardCanvas(
    allPieces: Map<PlayerColor, List<Piece>>,
    movablePieceIds: Set<Pair<PlayerColor, Int>>,  // (color, pieceId) pairs that can move
    onPieceTapped: (Piece) -> Unit,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()

    Canvas(
        modifier = modifier.pointerInput(allPieces, movablePieceIds) {
            detectTapGestures { tapOffset ->
                val cellSize = size.width / GRID.toFloat()
                val col = (tapOffset.x / cellSize).toInt().coerceIn(0, GRID - 1)
                val row = (tapOffset.y / cellSize).toInt().coerceIn(0, GRID - 1)

                // Find if any movable piece lives in this cell
                for ((color, pieces) in allPieces) {
                    for (piece in pieces) {
                        val cell = BoardCoordinates.cellFor(piece) ?: continue
                        if (cell.first == row && cell.second == col &&
                            (color to piece.id) in movablePieceIds
                        ) {
                            onPieceTapped(piece)
                            return@detectTapGestures
                        }
                    }
                }
            }
        }
    ) {
        val cellSize = size.width / GRID.toFloat()
        drawBoard(cellSize)
        drawHomeYards(cellSize)
        drawHomeColumns(cellSize)
        drawCenter(cellSize)
        drawAllPieces(allPieces, movablePieceIds, cellSize, textMeasurer)
    }
}

// ── Board drawing ─────────────────────────────────────────────────────────────

private fun DrawScope.drawBoard(cellSize: Float) {
    // Background
    drawRect(color = BoardWhite)

    // Draw all 225 cells
    for (row in 0 until GRID) {
        for (col in 0 until GRID) {
            val color = cellColor(row, col)
            drawRoundRect(
                color       = color,
                topLeft     = Offset(col * cellSize, row * cellSize),
                size        = Size(cellSize, cellSize),
                cornerRadius = CornerRadius(2f)
            )
            // Cell border
            drawRect(
                color   = Color.Black.copy(alpha = 0.10f),
                topLeft = Offset(col * cellSize, row * cellSize),
                size    = Size(cellSize, cellSize),
                style   = Stroke(width = 1f)
            )
        }
    }

    // Safe-square star marker
    BoardCoordinates.MAIN_TRACK.forEachIndexed { index, (row, col) ->
        if (index in com.failureludo.engine.Board.SAFE_SQUARES) {
            drawSafeStar(col * cellSize, row * cellSize, cellSize)
        }
    }
}

private fun DrawScope.drawHomeYards(cellSize: Float) {
    PlayerColor.entries.forEach { color ->
        val yardTopLeft = yardTopLeft(color)
        // Large coloured yard area: 6x6 minus the track border
        drawRoundRect(
            color       = playerColor(color).copy(alpha = 0.85f),
            topLeft     = Offset(yardTopLeft.second * cellSize, yardTopLeft.first * cellSize),
            size        = Size(6 * cellSize, 6 * cellSize),
            cornerRadius = CornerRadius(8f)
        )
        // Inner white circle "plate" area: 4x4
        val innerOffset = Offset(
            (yardTopLeft.second + 1) * cellSize,
            (yardTopLeft.first + 1) * cellSize
        )
        drawRoundRect(
            color       = Color.White.copy(alpha = 0.9f),
            topLeft     = innerOffset,
            size        = Size(4 * cellSize, 4 * cellSize),
            cornerRadius = CornerRadius(6f)
        )
    }
}

private fun DrawScope.drawHomeColumns(cellSize: Float) {
    PlayerColor.entries.forEach { color ->
        val cells = BoardCoordinates.HOME_COLUMNS[color] ?: return@forEach
        cells.forEach { (row, col) ->
            drawRoundRect(
                color       = playerColor(color).copy(alpha = 0.7f),
                topLeft     = Offset(col * cellSize + 2f, row * cellSize + 2f),
                size        = Size(cellSize - 4f, cellSize - 4f),
                cornerRadius = CornerRadius(4f)
            )
        }
    }
}

private fun DrawScope.drawCenter(cellSize: Float) {
    val (row, col) = BoardCoordinates.CENTER
    val cx = (col + 0.5f) * cellSize
    val cy = (row + 0.5f) * cellSize
    val half = cellSize * 1.5f  // center spans 3 rows/cols (rows/cols 6-8 center)

    // Draw 4 coloured triangles pointing inward
    // Simplified: draw 4 coloured quadrants
    val colors = listOf(LudoRed, LudoBlue, LudoYellow, LudoGreen)
    // Top-left = Red, Top-right = Blue, Bottom-right = Yellow, Bottom-left = Green
    val quads = listOf(
        Triple(LudoRed,    Offset(cx - half, cy - half), Size(half, half)),
        Triple(LudoBlue,   Offset(cx,        cy - half), Size(half, half)),
        Triple(LudoYellow, Offset(cx,        cy),        Size(half, half)),
        Triple(LudoGreen,  Offset(cx - half, cy),        Size(half, half))
    )
    quads.forEach { (clr, offset, size) ->
        drawRect(color = clr.copy(alpha = 0.8f), topLeft = offset, size = size)
    }
    // White circle in the very center
    drawCircle(color = Color.White, radius = cellSize * 0.8f, center = Offset(cx, cy))
    // Star in center
    drawCircle(color = Secondary.copy(alpha = 0.5f), radius = cellSize * 0.5f, center = Offset(cx, cy))
}

private fun DrawScope.drawSafeStar(x: Float, y: Float, cellSize: Float) {
    val cx = x + cellSize / 2
    val cy = y + cellSize / 2
    drawCircle(color = Color.Gray.copy(alpha = 0.25f), radius = cellSize * 0.35f, center = Offset(cx, cy))
}

// ── Piece drawing ─────────────────────────────────────────────────────────────

private fun DrawScope.drawAllPieces(
    allPieces: Map<PlayerColor, List<Piece>>,
    movable: Set<Pair<PlayerColor, Int>>,
    cellSize: Float,
    textMeasurer: TextMeasurer
) {
    // Group pieces by cell to handle stacking
    val cellMap = mutableMapOf<Pair<Int, Int>, MutableList<Piece>>()
    for ((_, pieces) in allPieces) {
        for (piece in pieces) {
            val cell = BoardCoordinates.cellFor(piece) ?: continue
            cellMap.getOrPut(cell) { mutableListOf() }.add(piece)
        }
    }

    cellMap.forEach { (cell, pieces) ->
        val (row, col) = cell
        val baseCx = (col + 0.5f) * cellSize
        val baseCy = (row + 0.5f) * cellSize

        pieces.forEachIndexed { i, piece ->
            val isMovable = (piece.color to piece.id) in movable
            // Slight offset for stacked pieces
            val offset = when (pieces.size) {
                1 -> Offset(0f, 0f)
                2 -> if (i == 0) Offset(-cellSize * 0.2f, 0f) else Offset(cellSize * 0.2f, 0f)
                3 -> listOf(
                    Offset(-cellSize * 0.2f, cellSize * 0.15f),
                    Offset(cellSize * 0.2f, cellSize * 0.15f),
                    Offset(0f, -cellSize * 0.2f)
                )[i]
                else -> listOf(
                    Offset(-cellSize * 0.18f, -cellSize * 0.18f),
                    Offset(cellSize * 0.18f, -cellSize * 0.18f),
                    Offset(-cellSize * 0.18f, cellSize * 0.18f),
                    Offset(cellSize * 0.18f, cellSize * 0.18f)
                ).getOrElse(i) { Offset.Zero }
            }
            val cx = baseCx + offset.x
            val cy = baseCy + offset.y
            val radius = cellSize * 0.32f

            // Glow if movable
            if (isMovable) {
                drawCircle(
                    color  = Color.White.copy(alpha = 0.7f),
                    radius = radius + 5f,
                    center = Offset(cx, cy)
                )
            }

            // Piece body
            drawCircle(color = playerColor(piece.color), radius = radius, center = Offset(cx, cy))
            drawCircle(
                color  = Color.Black.copy(alpha = 0.3f),
                radius = radius,
                center = Offset(cx, cy),
                style  = Stroke(width = 2f)
            )
            // Inner highlight
            drawCircle(
                color  = Color.White.copy(alpha = 0.4f),
                radius = radius * 0.5f,
                center = Offset(cx - radius * 0.2f, cy - radius * 0.2f)
            )

            // Pulsing border for movable pieces
            if (isMovable) {
                drawCircle(
                    color  = Color.White,
                    radius = radius + 3f,
                    center = Offset(cx, cy),
                    style  = Stroke(width = 3f)
                )
            }
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

/** Returns the background colour to paint for a given (row, col) cell. */
private fun cellColor(row: Int, col: Int): Color {
    // Home yards
    if (row in 0..5 && col in 0..5)   return LudoRedLight
    if (row in 0..5 && col in 9..14)  return LudoBlueLight
    if (row in 9..14 && col in 9..14) return LudoYellowLight
    if (row in 9..14 && col in 0..5)  return LudoGreenLight

    // Center 3x3 region (rows 6-8, cols 6-8) — drawn separately
    if (row in 6..8 && col in 6..8) return Color.Transparent

    // Main track and home columns: white
    return BoardWhite
}

private fun yardTopLeft(color: PlayerColor): Pair<Int, Int> = when (color) {
    PlayerColor.RED    -> 0 to 0
    PlayerColor.BLUE   -> 0 to 9
    PlayerColor.YELLOW -> 9 to 9
    PlayerColor.GREEN  -> 9 to 0
}
