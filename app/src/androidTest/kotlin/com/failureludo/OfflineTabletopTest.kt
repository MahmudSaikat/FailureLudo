package com.failureludo

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test

/** Exercises real navigation and the roll gate, without authentication or a backend. */
class OfflineTabletopTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun localGameOpensAndRollSettlesWithoutAuthentication() {
        compose.runOnIdle { compose.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
        compose.waitUntil(5_000) {
            compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
        }
        compose.waitUntil(15_000) {
            compose.onAllNodesWithText("New game").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("New game").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithText("Free for All").performClick()
        // Existing emulator preferences may contain bot seats from earlier play.
        while (compose.onAllNodes(hasContentDescription("to human", substring = true)).fetchSemanticsNodes().isNotEmpty()) {
            compose.onAllNodes(hasContentDescription("to human", substring = true))[0].performClick()
        }
        compose.onNodeWithText("Next").performClick()
        compose.onNodeWithText("Start Game").performClick()
        compose.onNodeWithContentDescription("Roll dice").assertIsDisplayed().performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasContentDescription("Dice:", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Ludo board.", substring = true).assertIsDisplayed()
        compose.runOnIdle { compose.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        compose.waitUntil(5_000) {
            compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        }
        compose.onNodeWithContentDescription("Ludo board.", substring = true).assertIsDisplayed()
        compose.onNodeWithText("CURRENT TURN").assertIsDisplayed()
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("Reduced motion").assertIsDisplayed()
    }
}
