package com.failureludo.ai

import com.failureludo.engine.GameEngine
import com.failureludo.engine.DiceResult
import com.failureludo.engine.PiecePosition
import com.failureludo.engine.PlayerColor
import com.failureludo.engine.TurnPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BotPolicyEngineTest {

    @Test
    fun modelFirstSelectsHighestScoredCandidate() {
        val state = GameEngine.rollDice(
            GameEngine.newGame(activeColors = listOf(PlayerColor.RED, PlayerColor.BLUE)),
            forcedDiceValue = 6
        )

        val scorer = OfflineModelMoveScorer { _, candidates ->
            FloatArray(candidates.size) { index -> index.toFloat() }
        }

        val engine = ModelFirstBotPolicyEngine(
            scorer = scorer,
            fallback = HeuristicBotPolicyEngine
        )

        val decision = requireNotNull(engine.chooseMove(state))

        val expected = state.movablePieces.last()
        assertEquals(expected.id, decision.pieceId)
        assertEquals(expected.color, decision.pieceColor)
    }

    @Test
    fun modelFirstFallsBackWhenScoresAreInvalid() {
        val state = GameEngine.rollDice(
            GameEngine.newGame(activeColors = listOf(PlayerColor.RED, PlayerColor.BLUE)),
            forcedDiceValue = 6
        )

        val invalidScorer = OfflineModelMoveScorer { _, _ -> floatArrayOf(1f) }
        val engine = ModelFirstBotPolicyEngine(
            scorer = invalidScorer,
            fallback = HeuristicBotPolicyEngine
        )

        val decision = engine.chooseMove(state)
        assertNotNull(decision)
        assertTrue(state.movablePieces.any { it.id == decision?.pieceId && it.color == decision.pieceColor })
    }

    @Test
    fun chooseMoveReturnsNullOutsideSelectionPhase() {
        val state = GameEngine.newGame(activeColors = listOf(PlayerColor.RED, PlayerColor.BLUE)).copy(
            turnPhase = TurnPhase.WAITING_FOR_ROLL
        )

        val decision = HeuristicBotPolicyEngine.chooseMove(state)
        assertNull(decision)
    }

    @Test
    fun resolveDecisionPieceOrNullReturnsMatchingPiece() {
        val state = GameEngine.rollDice(
            GameEngine.newGame(activeColors = listOf(PlayerColor.RED, PlayerColor.BLUE)),
            forcedDiceValue = 6
        )
        val piece = state.movablePieces.first()

        val decision = BotMoveDecision(
            pieceId = piece.id,
            pieceColor = piece.color,
            deferHomeEntry = false
        )

        val resolved = resolveDecisionPieceOrNull(state, decision)
        assertEquals(piece, resolved)
    }

    @Test
    fun legalCandidatesIncludeDeferOptionWhenEnteringHomePathIsPossible() {
        val base = GameEngine.newGame(activeColors = listOf(PlayerColor.RED, PlayerColor.BLUE))
        val red = base.players.first { it.color == PlayerColor.RED }
        val redPiece = red.pieces.first { it.id == 0 }.copy(
            position = PiecePosition.MainTrack(50),
            lastMovedAt = 10L
        )

        val patchedPlayers = base.players.map { player ->
            if (player.color == PlayerColor.RED) {
                player.copy(pieces = player.pieces.map { piece -> if (piece.id == 0) redPiece else piece })
            } else {
                player
            }
        }

        val state = base.copy(
            players = patchedPlayers,
            turnPhase = TurnPhase.WAITING_FOR_PIECE_SELECTION,
            lastDice = DiceResult(value = 2, rollCount = 1),
            movablePieces = listOf(redPiece)
        )

        val candidates = legalBotMoveCandidates(state)

        assertTrue(candidates.any { !it.deferHomeEntry })
        assertTrue(candidates.any { it.deferHomeEntry })

        val decision = HeuristicBotPolicyEngine.chooseMove(state)
        assertNotNull(decision)
        assertTrue(candidates.contains(decision))
    }
}