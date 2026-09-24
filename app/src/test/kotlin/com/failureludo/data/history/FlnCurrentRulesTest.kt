package com.failureludo.data.history

import com.failureludo.engine.*
import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

class FlnCurrentRulesTest {
    @Test fun recordedGamesRoundTripThroughTextAndReplayUnderCurrentRules() {
        for (mode in GameMode.entries) {
            val random = Random(923)
            val initial = GameEngine.newGame(activeColors = PlayerColor.entries, mode = mode)
            var state = initial
            val moves = mutableListOf<FlnMove>()
            repeat(1200) {
                if (state.isGameOver) return@repeat
                val rolled = GameEngine.rollDice(state, forcedDiceValue = random.nextInt(1, 7))
                val before: GameState
                val after: GameState
                when (rolled.turnPhase) {
                    TurnPhase.WAITING_FOR_PIECE_SELECTION -> {
                        before = rolled
                        val piece = rolled.movablePieces.random(random)
                        val defer = random.nextBoolean() && GameRules.canDeferHomeEntry(
                            piece, rolled.lastDice!!.value, piece.color, rolled.players, mode)
                        after = GameEngine.selectPiece(rolled, piece, deferHomeEntry = defer)
                    }
                    TurnPhase.NO_MOVES_AVAILABLE -> {
                        before = rolled
                        after = GameEngine.advanceNoMoves(rolled)
                    }
                    else -> { before = state; after = rolled }
                }
                moves += requireNotNull(FlnMoveRecorder.deriveMove(before, after, moves.size + 1))
                state = after
            }
            val document = FlnDocument(flnVersion = "1.0", rulesetVersion = "2026.09.23",
                gameId = "roundtrip-${mode.name}", status = FlnGameStatus.ACTIVE,
                createdAtEpochMs = 1, updatedAtEpochMs = 1,
                players = initial.players.map { FlnPlayer(it.id, it.name) }, moves = moves,
                tags = mapOf("Mode" to mode.name))
            val parsed = FlnParserRegistry.default().parse(FlnV1Codec().serialize(document))
            assertTrue(parsed is FlnParseResult.Success)
            val replay = FlnReplayApplier.replayMoves(initial, (parsed as FlnParseResult.Success).document.moves)
            assertTrue("$mode: $replay", replay is FlnReplayResult.Success)
            assertEquals(state, (replay as FlnReplayResult.Success).finalState)
        }
    }

    @Test fun replayRejectsWrongRollOnlyReason() {
        val initial = GameEngine.newGame(activeColors = listOf(PlayerColor.RED, PlayerColor.BLUE))
        val result = FlnReplayApplier.replayMoves(initial, listOf(FlnMove(
            ply = 1, actorId = initial.currentPlayer.id, diceValue = 1,
            rollOnlyReason = FlnRollOnlyReason.THREE_SIX_FORFEIT)))
        assertTrue(result is FlnReplayResult.Failure)
    }
}
