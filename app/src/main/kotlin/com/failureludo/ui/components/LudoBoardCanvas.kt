package com.failureludo.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.failureludo.engine.Piece
import com.failureludo.engine.PiecePosition
import com.failureludo.engine.PlayerColor
import com.failureludo.ui.screens.playerColor
import com.failureludo.ui.screens.playerColorLight
import com.failureludo.ui.theme.*
import kotlin.math.max

private const val GRID = 15
private const val TAP_DIAGNOSTICS_ENABLED = false
private const val TAP_DIAGNOSTICS_TAG = "LudoBoardTap"
private const val USE_CELL_ONLY_TAP_SELECTION = false
private const val MIN_EFFECTIVE_TOUCH_TARGET_DP = 48f
private const val HIT_RADIUS_MULTIPLIER = 1.35f
private const val MAX_EFFECTIVE_HIT_RADIUS_CELL_MULTIPLIER = 0.95f

private const val PAWN_RADIUS_SINGLE_MULTIPLIER = 0.36f
private const val PAWN_RADIUS_DOUBLE_MULTIPLIER = 0.33f
private const val PAWN_RADIUS_TRIPLE_MULTIPLIER = 0.31f
private const val PAWN_RADIUS_QUAD_MULTIPLIER = 0.29f

private const val STACK_OFFSET_WIDE_MULTIPLIER = 0.22f
private const val STACK_OFFSET_TRIAD_VERTICAL_MULTIPLIER = 0.17f
private const val MAX_OVERFLOW_PER_AXIS_MULTIPLIER = 0.18f

private val ENTRY_DIRECTIONS = mapOf(
    PlayerColor.RED to (1f to 0f),
    PlayerColor.BLUE to (0f to 1f),
    PlayerColor.YELLOW to (-1f to 0f),
    PlayerColor.GREEN to (0f to -1f)
)

private val MAIN_TRACK_INDEX_BY_CELL: Map<Pair<Int, Int>, Int> =
    BoardCoordinates.MAIN_TRACK.withIndex().associate { it.value to it.index }

private val HOME_YARD_CELL_TO_COLOR: Map<Pair<Int, Int>, PlayerColor> =
    BoardCoordinates.HOME_YARD_SPOTS
        .flatMap { (color, cells) -> cells.map { cell -> cell to color } }
        .toMap()

private val HOME_COLUMN_CELL_TO_COLOR: Map<Pair<Int, Int>, PlayerColor> =
    BoardCoordinates.HOME_COLUMNS
        .flatMap { (color, cells) -> cells.map { cell -> cell to color } }
        .toMap()

data class TappedCellPieces(
    val allPieces: List<Piece>,
    val movablePieces: List<Piece>,
    val preferredPiece: Piece? = null
)

internal data class PieceLayout(
    val piece: Piece,
    val cell: Pair<Int, Int>,
    val center: Offset,
    val radius: Float,
    val isMovable: Boolean
)

internal data class HitCandidate(
    val layout: PieceLayout,
    val distance: Float
)

private val HitCandidateSelectionComparator = compareBy<HitCandidate>(
    { it.distance },
    { -it.layout.piece.lastMovedAt },
    { it.layout.piece.id },
    { it.layout.piece.color.ordinal }
)

private val PieceSelectionComparator = compareBy<Piece>(
    { it.color.ordinal },
    { it.id }
)

private inline fun debugTapDiagnostics(message: () -> String) {
    if (TAP_DIAGNOSTICS_ENABLED) {
        android.util.Log.d(TAP_DIAGNOSTICS_TAG, message())
    }
}

internal fun selectBestHitCandidate(candidates: Sequence<HitCandidate>): HitCandidate? {
    return candidates.minWithOrNull(HitCandidateSelectionComparator)
}

internal fun rankHitCandidatesForSelection(candidates: List<HitCandidate>): List<HitCandidate> {
    return candidates.sortedWith(HitCandidateSelectionComparator)
}

private fun sortedPieces(layouts: List<PieceLayout>): List<Piece> {
    return layouts
        .map { it.piece }
        .sortedWith(PieceSelectionComparator)
}

internal fun resolveTappedCellPieces(
    tapOffset: Offset,
    pieceLayouts: List<PieceLayout>,
    cellSize: Float,
    minTouchRadiusPx: Float
): TappedCellPieces? {
    val anchor = selectBestHitCandidate(
        pieceLayouts
            .asSequence()
            .filter { it.isMovable }
            .mapNotNull { layout ->
                val distance = (tapOffset - layout.center).getDistance()
                val hitRadius = effectiveHitRadius(
                    visualRadius = layout.radius,
                    cellSize = cellSize,
                    minTouchRadiusPx = minTouchRadiusPx
                )
                if (distance <= hitRadius) HitCandidate(layout, distance) else null
            }
    )?.layout

    if (anchor != null) {
        val anchorCellLayouts = pieceLayouts.filter { it.cell == anchor.cell }
        val movableAnchorCellLayouts = anchorCellLayouts.filter { it.isMovable }
        if (movableAnchorCellLayouts.isNotEmpty()) {
            return TappedCellPieces(
                allPieces = sortedPieces(anchorCellLayouts),
                movablePieces = sortedPieces(movableAnchorCellLayouts),
                preferredPiece = anchor.piece
            )
        }
    }

    val fallbackCol = (tapOffset.x / cellSize).toInt().coerceIn(0, GRID - 1)
    val fallbackRow = (tapOffset.y / cellSize).toInt().coerceIn(0, GRID - 1)
    val fallbackCellLayouts = pieceLayouts.filter {
        it.cell.first == fallbackRow && it.cell.second == fallbackCol
    }
    val fallbackMovableCellLayouts = fallbackCellLayouts.filter { it.isMovable }
    if (fallbackMovableCellLayouts.isEmpty()) {
        return null
    }

    return TappedCellPieces(
        allPieces = sortedPieces(fallbackCellLayouts),
        movablePieces = sortedPieces(fallbackMovableCellLayouts)
    )
}

internal fun resolveCellOnlyTappedCellPieces(
    tapOffset: Offset,
    pieceLayouts: List<PieceLayout>,
    cellSize: Float
): TappedCellPieces? {
    val fallbackCol = (tapOffset.x / cellSize).toInt().coerceIn(0, GRID - 1)
    val fallbackRow = (tapOffset.y / cellSize).toInt().coerceIn(0, GRID - 1)
    val fallbackCellLayouts = pieceLayouts.filter {
        it.cell.first == fallbackRow && it.cell.second == fallbackCol
    }
    val fallbackMovableCellLayouts = fallbackCellLayouts.filter { it.isMovable }
    if (fallbackMovableCellLayouts.isEmpty()) {
        return null
    }

    return TappedCellPieces(
        allPieces = sortedPieces(fallbackCellLayouts),
        movablePieces = sortedPieces(fallbackMovableCellLayouts)
    )
}

@Composable
fun LudoBoardCanvas(
    allPieces: Map<PlayerColor, List<Piece>>,
    movablePieceIds: Set<Pair<PlayerColor, Int>>,  // (color, pieceId) pairs that can move
    animatedPieceCells: Map<Pair<PlayerColor, Int>, Pair<Int, Int>> = emptyMap(),
    playerPalette: Map<PlayerColor, Color> = emptyMap(),
    onCellPiecesTapped: (TappedCellPieces) -> Unit,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()

    Canvas(
        modifier = modifier.pointerInput(allPieces, movablePieceIds, animatedPieceCells) {
            detectTapGestures { tapOffset ->
                val cellSize = size.width / GRID.toFloat()
                val minTouchRadiusPx = (MIN_EFFECTIVE_TOUCH_TARGET_DP.dp.toPx() / 2f)

                val pieceLayouts = buildPieceLayouts(
                    allPieces = allPieces,
                    movablePieceIds = movablePieceIds,
                    animatedPieceCells = animatedPieceCells,
                    cellSize = cellSize,
                    boardSize = size.width.toFloat()
                )
                debugTapDiagnostics {
                    "tap=(${tapOffset.x.format(1)}, ${tapOffset.y.format(1)}) cellSize=${cellSize.format(2)} " +
                        "pieces=${pieceLayouts.size} movable=${pieceLayouts.count { it.isMovable }}"
                }

                val tappedPieces = if (USE_CELL_ONLY_TAP_SELECTION) {
                    resolveCellOnlyTappedCellPieces(
                        tapOffset = tapOffset,
                        pieceLayouts = pieceLayouts,
                        cellSize = cellSize
                    )
                } else {
                    resolveTappedCellPieces(
                        tapOffset = tapOffset,
                        pieceLayouts = pieceLayouts,
                        cellSize = cellSize,
                        minTouchRadiusPx = minTouchRadiusPx
                    )
                }

                if (tappedPieces != null) {
                    val preferred = tappedPieces.preferredPiece
                    if (preferred != null) {
                        debugTapDiagnostics {
                            "anchor=${preferred.color}:${preferred.id}"
                        }
                    } else {
                        val fallbackCol = (tapOffset.x / cellSize).toInt().coerceIn(0, GRID - 1)
                        val fallbackRow = (tapOffset.y / cellSize).toInt().coerceIn(0, GRID - 1)
                        debugTapDiagnostics {
                            "fallbackCell=($fallbackRow,$fallbackCol) options=${tappedPieces.movablePieces.size}"
                        }
                    }
                    onCellPiecesTapped(tappedPieces)
                }
            }
        }
    ) {
        val cellSize = size.width / GRID.toFloat()
        drawBoard(cellSize, playerPalette)
        drawDirectionMarkers(cellSize, playerPalette)
        drawHomeYards(cellSize, playerPalette)
        drawHomeColumns(cellSize, playerPalette)
        drawCenter(cellSize, playerPalette)
        drawAllPieces(allPieces, movablePieceIds, animatedPieceCells, cellSize, textMeasurer, playerPalette)
    }
}

// ── Board drawing ─────────────────────────────────────────────────────────────

private fun DrawScope.drawBoard(cellSize: Float, playerPalette: Map<PlayerColor, Color>) {
    // Background
    drawRect(color = BoardWhite.copy(alpha = 0.74f))

    // Draw all 225 cells
    for (row in 0 until GRID) {
        for (col in 0 until GRID) {
            val color = cellColor(row, col, playerPalette)
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

    // Safe-square marker
    BoardCoordinates.MAIN_TRACK.forEachIndexed { index, (row, col) ->
        if (index in com.failureludo.engine.Board.SAFE_SQUARES) {
            drawSafeStar(
                x = col * cellSize,
                y = row * cellSize,
                cellSize = cellSize,
                color = safeSquareColor(index, playerPalette)
            )
        }
    }
}

private fun DrawScope.drawDirectionMarkers(cellSize: Float, playerPalette: Map<PlayerColor, Color>) {
    PlayerColor.entries.forEach { color ->
        val entryIndex = color.entryPosition
        val entryCell = BoardCoordinates.MAIN_TRACK.getOrNull(entryIndex) ?: return@forEach
        val entryDir = ENTRY_DIRECTIONS[color] ?: (0f to 0f)

        drawArrowOnCell(
            row = entryCell.first,
            col = entryCell.second,
            dx = entryDir.first,
            dy = entryDir.second,
            cellSize = cellSize,
            color = playerColor(color, playerPalette).copy(alpha = 0.9f)
        )

        val homeEntryCell = BoardCoordinates.HOME_COLUMNS[color]?.firstOrNull() ?: return@forEach
        drawArrowOnCell(
            row = homeEntryCell.first,
            col = homeEntryCell.second,
            dx = entryDir.first,
            dy = entryDir.second,
            cellSize = cellSize,
            color = playerColor(color, playerPalette).copy(alpha = 0.9f)
        )
    }
}

private fun DrawScope.drawHomeYards(cellSize: Float, playerPalette: Map<PlayerColor, Color>) {
    PlayerColor.entries.forEach { color ->
        val yardTopLeft = yardTopLeft(color)
        // Large coloured yard area: 6x6 minus the track border
        drawRoundRect(
            color       = playerColor(color, playerPalette).copy(alpha = 0.85f),
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

private fun DrawScope.drawHomeColumns(cellSize: Float, playerPalette: Map<PlayerColor, Color>) {
    PlayerColor.entries.forEach { color ->
        val cells = BoardCoordinates.HOME_COLUMNS[color] ?: return@forEach
        cells.forEach { (row, col) ->
            drawRoundRect(
                color       = playerColor(color, playerPalette).copy(alpha = 0.7f),
                topLeft     = Offset(col * cellSize + 2f, row * cellSize + 2f),
                size        = Size(cellSize - 4f, cellSize - 4f),
                cornerRadius = CornerRadius(4f)
            )
        }
    }
}

private fun DrawScope.drawCenter(cellSize: Float, playerPalette: Map<PlayerColor, Color>) {
    val (row, col) = BoardCoordinates.CENTER
    val cx = (col + 0.5f) * cellSize
    val cy = (row + 0.5f) * cellSize
    val half = cellSize * 1.5f  // center spans 3 rows/cols (rows/cols 6-8 center)

    // Draw 4 coloured triangles pointing inward
    // Simplified: draw 4 coloured quadrants
    val quads = listOf(
        Triple(playerColor(PlayerColor.RED, playerPalette), Offset(cx - half, cy - half), Size(half, half)),
        Triple(playerColor(PlayerColor.BLUE, playerPalette), Offset(cx, cy - half), Size(half, half)),
        Triple(playerColor(PlayerColor.YELLOW, playerPalette), Offset(cx, cy), Size(half, half)),
        Triple(playerColor(PlayerColor.GREEN, playerPalette), Offset(cx - half, cy), Size(half, half))
    )
    quads.forEach { (clr, offset, size) ->
        drawRect(color = clr.copy(alpha = 0.8f), topLeft = offset, size = size)
    }
    // White circle in the very center
    drawCircle(color = Color.White, radius = cellSize * 0.8f, center = Offset(cx, cy))
    // Star in center
    drawCircle(color = Secondary.copy(alpha = 0.5f), radius = cellSize * 0.5f, center = Offset(cx, cy))
}

private fun DrawScope.drawSafeStar(x: Float, y: Float, cellSize: Float, color: Color) {
    val cx = x + cellSize / 2
    val cy = y + cellSize / 2

    drawCircle(
        color = color.copy(alpha = 0.20f),
        radius = cellSize * 0.35f,
        center = Offset(cx, cy)
    )
    drawCircle(
        color = color.copy(alpha = 0.85f),
        radius = cellSize * 0.35f,
        center = Offset(cx, cy),
        style = Stroke(width = cellSize * 0.06f)
    )

    val markerRadius = cellSize * 0.12f
    drawCircle(
        color = Color.White.copy(alpha = 0.95f),
        radius = markerRadius,
        center = Offset(cx, cy)
    )
}

private fun DrawScope.drawArrowOnCell(
    row: Int,
    col: Int,
    dx: Float,
    dy: Float,
    cellSize: Float,
    color: Color
) {
    val centerX = (col + 0.5f) * cellSize
    val centerY = (row + 0.5f) * cellSize
    val length = cellSize * 0.26f
    val width = cellSize * 0.12f

    val tip = Offset(centerX + dx * length, centerY + dy * length)
    val base = Offset(centerX - dx * length * 0.5f, centerY - dy * length * 0.5f)

    val perpendicular = Offset(-dy * width, dx * width)
    val p1 = Offset(base.x + perpendicular.x, base.y + perpendicular.y)
    val p2 = Offset(base.x - perpendicular.x, base.y - perpendicular.y)

    val path = Path().apply {
        moveTo(tip.x, tip.y)
        lineTo(p1.x, p1.y)
        lineTo(p2.x, p2.y)
        close()
    }

    drawPath(path = path, color = Color.White.copy(alpha = 0.95f))
    drawPath(path = path, color = color.copy(alpha = 0.85f), style = Stroke(width = cellSize * 0.04f))
}

// ── Piece drawing ─────────────────────────────────────────────────────────────

private fun DrawScope.drawAllPieces(
    allPieces: Map<PlayerColor, List<Piece>>,
    movable: Set<Pair<PlayerColor, Int>>,
    animatedPieceCells: Map<Pair<PlayerColor, Int>, Pair<Int, Int>>,
    cellSize: Float,
    textMeasurer: TextMeasurer,
    playerPalette: Map<PlayerColor, Color>
) {
    val pieceLayouts = buildPieceLayouts(
        allPieces = allPieces,
        movablePieceIds = movable,
        animatedPieceCells = animatedPieceCells,
        cellSize = cellSize,
        boardSize = size.width
    )
    val cellMap = pieceLayouts.groupBy { it.cell }

    cellMap.forEach { (cell, layouts) ->
        val (_, col) = cell
        val baseCx = (col + 0.5f) * cellSize
        val baseCy = (cell.first + 0.5f) * cellSize
        val stackCount = layouts.size
        val drawingOrder = layouts.sortedWith(
            compareBy<PieceLayout>(
                { it.isMovable },
                { it.piece.lastMovedAt },
                { it.piece.id },
                { it.piece.color.ordinal }
            )
        )

        drawingOrder.forEach { layout ->
            val piece = layout.piece
            val isMovable = layout.isMovable
            val cx = layout.center.x
            val cy = layout.center.y
            val radius = layout.radius
            val outlineWidth = max(1.5f, cellSize * 0.05f)
            val rotation = pawnRotationForCell(layout.cell)
            rotate(degrees = rotation, pivot = Offset(cx, cy)) {
                // Standardized selectable ring + glow
                if (isMovable) {
                    drawCircle(
                        color  = Color.White.copy(alpha = 0.22f),
                        radius = radius + cellSize * 0.22f,
                        center = Offset(cx, cy)
                    )
                    drawCircle(
                        color  = Color.White.copy(alpha = 0.95f),
                        radius = radius + cellSize * 0.12f,
                        center = Offset(cx, cy),
                        style  = Stroke(width = cellSize * 0.05f)
                    )
                }

                // Pawn silhouette for small-size readability: base + head
                drawCircle(
                    color = Color.Black.copy(alpha = 0.16f),
                    radius = radius * 0.78f,
                    center = Offset(cx, cy + radius * 0.48f)
                )

                drawCircle(
                    color = playerColor(piece.color, playerPalette),
                    radius = radius * 0.62f,
                    center = Offset(cx, cy + radius * 0.22f)
                )

                drawCircle(
                    color = playerColor(piece.color, playerPalette),
                    radius = radius * 0.56f,
                    center = Offset(cx, cy - radius * 0.38f)
                )

                drawCircle(
                    color  = Color.Black.copy(alpha = 0.3f),
                    radius = radius * 0.62f,
                    center = Offset(cx, cy + radius * 0.22f),
                    style  = Stroke(width = outlineWidth)
                )

                drawCircle(
                    color  = Color.Black.copy(alpha = 0.3f),
                    radius = radius * 0.56f,
                    center = Offset(cx, cy - radius * 0.38f),
                    style  = Stroke(width = outlineWidth)
                )

                // Head highlight
                drawCircle(
                    color  = Color.White.copy(alpha = 0.4f),
                    radius = radius * 0.22f,
                    center = Offset(cx - radius * 0.16f, cy - radius * 0.54f)
                )
            }
        }

        if (stackCount > 1) {
            val badgeCenter = Offset(
                x = baseCx + cellSize * 0.34f,
                y = baseCy - cellSize * 0.34f
            )
            val badgeRadius = cellSize * 0.16f

            drawCircle(
                color = Color.White.copy(alpha = 0.95f),
                radius = badgeRadius,
                center = badgeCenter
            )
            drawCircle(
                color = Color.Black.copy(alpha = 0.5f),
                radius = badgeRadius,
                center = badgeCenter,
                style = Stroke(width = cellSize * 0.03f)
            )

            val markerCount = stackCount.coerceIn(2, 4)
            val dotRadius = cellSize * 0.026f
            val gap = cellSize * 0.07f
            val startX = badgeCenter.x - (gap * (markerCount - 1) / 2f)

            repeat(markerCount) { markerIndex ->
                drawCircle(
                    color = Color.Black.copy(alpha = 0.85f),
                    radius = dotRadius,
                    center = Offset(startX + markerIndex * gap, badgeCenter.y)
                )
            }
        }
    }
}

private fun Float.format(scale: Int): String {
    return java.lang.String.format(java.util.Locale.US, "%.${scale}f", this)
}

private fun buildPieceLayouts(
    allPieces: Map<PlayerColor, List<Piece>>,
    movablePieceIds: Set<Pair<PlayerColor, Int>>,
    animatedPieceCells: Map<Pair<PlayerColor, Int>, Pair<Int, Int>>,
    cellSize: Float,
    boardSize: Float
): List<PieceLayout> {
    val cellMap = mutableMapOf<Pair<Int, Int>, MutableList<Piece>>()
    for ((_, pieces) in allPieces) {
        for (piece in pieces) {
            val cell = animatedPieceCells[piece.color to piece.id] ?: BoardCoordinates.cellFor(piece) ?: continue
            cellMap.getOrPut(cell) { mutableListOf() }.add(piece)
        }
    }

    val layouts = mutableListOf<PieceLayout>()
    cellMap.forEach { (cell, rawPieces) ->
        val pieces = rawPieces.sortedWith(compareBy<Piece>({ it.color.ordinal }, { it.id }))
        val stackCount = pieces.size
        val radius = pieceRadiusForStack(stackCount, cellSize)
        val baseCenter = Offset(
            x = (cell.second + 0.5f) * cellSize,
            y = (cell.first + 0.5f) * cellSize
        )

        pieces.forEachIndexed { index, piece ->
            val rawOffset = stackOffsetFor(index, stackCount, cellSize)
            val boundedOffset = clampOffsetToOverflow(rawOffset, radius, cellSize)
            val boundedCenter = clampCenterToBoard(
                center = baseCenter + boundedOffset,
                radius = radius,
                boardSize = boardSize
            )

            layouts += PieceLayout(
                piece = piece,
                cell = cell,
                center = boundedCenter,
                radius = radius,
                isMovable = (piece.color to piece.id) in movablePieceIds
            )
        }
    }

    return layouts
}

internal fun pieceRadiusForStack(stackCount: Int, cellSize: Float): Float {
    return when {
        stackCount >= 4 -> cellSize * PAWN_RADIUS_QUAD_MULTIPLIER
        stackCount == 3 -> cellSize * PAWN_RADIUS_TRIPLE_MULTIPLIER
        stackCount == 2 -> cellSize * PAWN_RADIUS_DOUBLE_MULTIPLIER
        else -> cellSize * PAWN_RADIUS_SINGLE_MULTIPLIER
    }
}

internal fun stackOffsetFor(index: Int, stackCount: Int, cellSize: Float): Offset {
    return when (stackCount) {
        1 -> Offset.Zero
        2 -> if (index == 0) {
            Offset(-cellSize * STACK_OFFSET_WIDE_MULTIPLIER, 0f)
        } else {
            Offset(cellSize * STACK_OFFSET_WIDE_MULTIPLIER, 0f)
        }

        3 -> listOf(
            Offset(-cellSize * STACK_OFFSET_WIDE_MULTIPLIER, cellSize * STACK_OFFSET_TRIAD_VERTICAL_MULTIPLIER),
            Offset(cellSize * STACK_OFFSET_WIDE_MULTIPLIER, cellSize * STACK_OFFSET_TRIAD_VERTICAL_MULTIPLIER),
            Offset(0f, -cellSize * STACK_OFFSET_WIDE_MULTIPLIER)
        ).getOrElse(index) { Offset.Zero }

        else -> listOf(
            Offset(-cellSize * STACK_OFFSET_WIDE_MULTIPLIER, -cellSize * STACK_OFFSET_WIDE_MULTIPLIER),
            Offset(cellSize * STACK_OFFSET_WIDE_MULTIPLIER, -cellSize * STACK_OFFSET_WIDE_MULTIPLIER),
            Offset(-cellSize * STACK_OFFSET_WIDE_MULTIPLIER, cellSize * STACK_OFFSET_WIDE_MULTIPLIER),
            Offset(cellSize * STACK_OFFSET_WIDE_MULTIPLIER, cellSize * STACK_OFFSET_WIDE_MULTIPLIER)
        ).getOrElse(index) { Offset.Zero }
    }
}

internal fun clampOffsetToOverflow(offset: Offset, radius: Float, cellSize: Float): Offset {
    val maxCenterOffset = ((cellSize * 0.5f) + (cellSize * MAX_OVERFLOW_PER_AXIS_MULTIPLIER) - radius)
        .coerceAtLeast(0f)
    return Offset(
        x = offset.x.coerceIn(-maxCenterOffset, maxCenterOffset),
        y = offset.y.coerceIn(-maxCenterOffset, maxCenterOffset)
    )
}

private fun clampCenterToBoard(center: Offset, radius: Float, boardSize: Float): Offset {
    val minCoord = radius
    val maxCoord = (boardSize - radius).coerceAtLeast(minCoord)
    return Offset(
        x = center.x.coerceIn(minCoord, maxCoord),
        y = center.y.coerceIn(minCoord, maxCoord)
    )
}

internal fun effectiveHitRadius(
    visualRadius: Float,
    cellSize: Float,
    minTouchRadiusPx: Float
): Float {
    val boostedRadius = max(visualRadius * HIT_RADIUS_MULTIPLIER, minTouchRadiusPx)
    val cappedRadius = cellSize * MAX_EFFECTIVE_HIT_RADIUS_CELL_MULTIPLIER
    return boostedRadius.coerceAtMost(cappedRadius)
}

// ── Helpers ───────────────────────────────────────────────────────────────────

/** Returns the background colour to paint for a given (row, col) cell. */
private fun cellColor(row: Int, col: Int, playerPalette: Map<PlayerColor, Color>): Color {
    // Home yards
    if (row in 0..5 && col in 0..5)   return playerColorLight(PlayerColor.RED, playerPalette)
    if (row in 0..5 && col in 9..14)  return playerColorLight(PlayerColor.BLUE, playerPalette)
    if (row in 9..14 && col in 9..14) return playerColorLight(PlayerColor.YELLOW, playerPalette)
    if (row in 9..14 && col in 0..5)  return playerColorLight(PlayerColor.GREEN, playerPalette)

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

private fun safeSquareColor(index: Int, playerPalette: Map<PlayerColor, Color>): Color {
    return when (index) {
        0, 8 -> playerColor(PlayerColor.RED, playerPalette)
        13, 21 -> playerColor(PlayerColor.BLUE, playerPalette)
        26, 34 -> playerColor(PlayerColor.YELLOW, playerPalette)
        39, 47 -> playerColor(PlayerColor.GREEN, playerPalette)
        else -> Color.Gray
    }
}

private fun pawnRotationForCell(cell: Pair<Int, Int>): Float {
    val territory = territoryColorForCell(cell) ?: return 0f
    val direction = ENTRY_DIRECTIONS[territory] ?: return 0f
    return rotationForDirection(direction)
}

private fun rotationForDirection(direction: Pair<Float, Float>): Float {
    return when (direction) {
        0f to -1f -> 0f
        1f to 0f -> 90f
        0f to 1f -> 180f
        -1f to 0f -> 270f
        else -> 0f
    }
}

private fun territoryColorForCell(cell: Pair<Int, Int>): PlayerColor? {
    HOME_YARD_CELL_TO_COLOR[cell]?.let { return it }
    HOME_COLUMN_CELL_TO_COLOR[cell]?.let { return it }
    MAIN_TRACK_INDEX_BY_CELL[cell]?.let { index ->
        return territoryColorForMainTrackIndex(index)
    }
    return null
}

private fun territoryColorForMainTrackIndex(index: Int): PlayerColor {
    return when (index) {
        in 0..12 -> PlayerColor.RED
        in 13..25 -> PlayerColor.BLUE
        in 26..38 -> PlayerColor.YELLOW
        else -> PlayerColor.GREEN
    }
}
