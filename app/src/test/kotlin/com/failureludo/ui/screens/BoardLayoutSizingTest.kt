package com.failureludo.ui.screens

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Test

class BoardLayoutSizingTest {

    @Test
    fun computeBoardLayoutSizing_compactPhone_keepsBoardAndRailsWithinHeight() {
        val sizing = computeBoardLayoutSizing(
            maxWidth = 360.dp,
            maxHeight = 640.dp
        )

        assertTrue(sizing.boardSize > 0.dp)
        assertTrue(sizing.boardSize <= 360.dp)
        assertTrue(sizing.boardSize + sizing.railHeight * 2 <= 640.dp)
    }

    @Test
    fun computeBoardLayoutSizing_constrainedHeight_neverOverflows() {
        val sizing = computeBoardLayoutSizing(
            maxWidth = 360.dp,
            maxHeight = 260.dp
        )

        assertTrue(sizing.boardSize >= 0.dp)
        assertTrue(sizing.boardSize + sizing.railHeight * 2 <= 260.dp)
        assertTrue(sizing.diceSize >= 18.dp)
    }

    @Test
    fun computeBoardLayoutSizing_largeWidth_stillBoundedByHeightBudget() {
        val sizing = computeBoardLayoutSizing(
            maxWidth = 900.dp,
            maxHeight = 700.dp
        )

        assertTrue(sizing.boardSize <= 900.dp)
        assertTrue(sizing.boardSize + sizing.railHeight * 2 <= 700.dp)
    }
}
