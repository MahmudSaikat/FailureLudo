package com.failureludo.ai

import com.failureludo.engine.GameEngine
import com.failureludo.engine.PlayerColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PolicyFeatureEncoderTest {

    @Test
    fun encodeStateProducesExpectedDimension() {
        val state = GameEngine.newGame(
            activeColors = listOf(PlayerColor.RED, PlayerColor.BLUE)
        )

        val features = PolicyFeatureEncoder.encodeState(state)

        assertEquals(PolicyFeatureEncoder.STATE_DIM, features.size)
    }

    @Test
    fun encodeCandidateProducesExpectedDimension() {
        val rolled = GameEngine.rollDice(
            GameEngine.newGame(activeColors = listOf(PlayerColor.RED, PlayerColor.BLUE)),
            forcedDiceValue = 6
        )
        val candidate = legalBotMoveCandidates(rolled).first()

        val features = PolicyFeatureEncoder.encodeCandidate(rolled, candidate)

        assertEquals(PolicyFeatureEncoder.CANDIDATE_DIM, features.size)
    }

    @Test
    fun deferFlagChangesCandidateEncoding() {
        val rolled = GameEngine.rollDice(
            GameEngine.newGame(activeColors = listOf(PlayerColor.RED, PlayerColor.BLUE)),
            forcedDiceValue = 6
        )
        val baseline = legalBotMoveCandidates(rolled).first()

        val withoutDefer = PolicyFeatureEncoder.encodeCandidate(
            rolled,
            baseline.copy(deferHomeEntry = false)
        )
        val withDefer = PolicyFeatureEncoder.encodeCandidate(
            rolled,
            baseline.copy(deferHomeEntry = true)
        )

        assertNotEquals(withoutDefer.last(), withDefer.last())
        assertTrue(withoutDefer.last() == 0f)
        assertTrue(withDefer.last() == 1f)
    }
}