package com.failureludo.data.history

import com.failureludo.engine.Board
import com.failureludo.engine.DiceResult
import com.failureludo.engine.GameEngine
import com.failureludo.engine.GameRules
import com.failureludo.engine.PiecePosition
import com.failureludo.engine.PlayerColor
import com.failureludo.engine.TurnPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FlnMoveRecorderTest {

    @Test
    fun deriveMoveReturnsNullWhenNoCommittedMoveOccurred() {
        val state = GameEngine.newGame(
            activeColors = listOf(PlayerColor.RED, PlayerColor.BLUE)
        )

        val noMove = FlnMoveRecorder.deriveMove(state, state, ply = 1)

        assertNull(noMove)
    }

    @Test
    fun deriveMoveCapturesBasicMoveData() {
        val start = GameEngine.newGame(
            activeColors = listOf(PlayerColor.RED, PlayerColor.BLUE)
        )
        val rolled = GameEngine.rollDice(start, forcedDiceValue = 6)
        val piece = rolled.movablePieces.first { it.id == 0 && it.color == PlayerColor.RED }
        val after = GameEngine.selectPiece(rolled, piece)

        val move = FlnMoveRecorder.deriveMove(rolled, after, ply = 9)

        assertEquals(9, move?.ply)
        assertEquals(rolled.currentPlayer.id, move?.actorId)
        assertEquals(rolled.currentPlayer.id, move?.movingPlayerId)
        assertEquals(6, move?.diceValue)
        assertEquals(0, move?.pieceId)
        assertNull(move?.choice)
    }

    @Test
    fun deriveMoveMarksDeferChoiceWhenPieceStayedOnMainTrack() {
        val homeEntryIndex = Board.HOME_COLUMN_ENTRY.getValue(PlayerColor.RED)
        val base = GameEngine.newGame(
            activeColors = listOf(PlayerColor.RED, PlayerColor.BLUE)
        )

        val players = base.players.map { player ->
            if (player.color != PlayerColor.RED) return@map player
            player.copy(
                pieces = player.pieces.map { piece ->
                    if (piece.id == 0) piece.copy(position = PiecePosition.MainTrack(homeEntryIndex))
                    else piece
                }
            )
        }

        val before = base.copy(
            players = players,
            turnPhase = TurnPhase.WAITING_FOR_PIECE_SELECTION,
            lastDice = DiceResult(2, 1),
            movablePieces = GameRules.movablePiecesForTurn(
                currentPlayer = players[base.currentPlayerIndex],
                diceValue = 2,
                allPlayers = players,
                mode = base.mode,
                sharedTeamDiceEnabled = base.sharedTeamDiceEnabled
            )
        )

        val piece = before.movablePieces.first { it.color == PlayerColor.RED && it.id == 0 }
        val after = GameEngine.selectPiece(before, piece, deferHomeEntry = true)

        val move = FlnMoveRecorder.deriveMove(before, after, ply = 3)

        assertEquals(FlnMoveChoice.DEFER_HOME_ENTRY, move?.choice)
        val redPieceAfter = after.players
            .first { it.color == PlayerColor.RED }
            .pieces
            .first { it.id == 0 }
        assertTrue(redPieceAfter.position is PiecePosition.MainTrack)
    }

    @Test
    fun deriveMoveMarksEnterChoiceWhenPieceEnteredHomePath() {
        val homeEntryIndex = Board.HOME_COLUMN_ENTRY.getValue(PlayerColor.RED)
        val base = GameEngine.newGame(
            activeColors = listOf(PlayerColor.RED, PlayerColor.BLUE)
        )

        val players = base.players.map { player ->
            if (player.color != PlayerColor.RED) return@map player
            player.copy(
                pieces = player.pieces.map { piece ->
                    if (piece.id == 0) piece.copy(position = PiecePosition.MainTrack(homeEntryIndex))
                    else piece
                }
            )
        }

        val before = base.copy(
            players = players,
            turnPhase = TurnPhase.WAITING_FOR_PIECE_SELECTION,
            lastDice = DiceResult(2, 1),
            movablePieces = GameRules.movablePiecesForTurn(
                currentPlayer = players[base.currentPlayerIndex],
                diceValue = 2,
                allPlayers = players,
                mode = base.mode,
                sharedTeamDiceEnabled = base.sharedTeamDiceEnabled
            )
        )

        val piece = before.movablePieces.first { it.color == PlayerColor.RED && it.id == 0 }
        val after = GameEngine.selectPiece(before, piece, deferHomeEntry = false)

        val move = FlnMoveRecorder.deriveMove(before, after, ply = 4)

        assertEquals(FlnMoveChoice.ENTER_HOME_PATH, move?.choice)
        val redPieceAfter = after.players
            .first { it.color == PlayerColor.RED }
            .pieces
            .first { it.id == 0 }
        assertTrue(redPieceAfter.position !is PiecePosition.MainTrack)
    }

    @Test
    fun deriveMoveCapturesRollOnlyNoMovesEntry() {
        val start = GameEngine.newGame(
            activeColors = listOf(PlayerColor.RED, PlayerColor.BLUE)
        )

        val noMoves = GameEngine.rollDice(start, forcedDiceValue = 1)
        val afterSkip = GameEngine.advanceNoMoves(noMoves)

        val move = FlnMoveRecorder.deriveMove(noMoves, afterSkip, ply = 12)

        assertEquals(12, move?.ply)
        assertEquals(noMoves.currentPlayer.id, move?.actorId)
        assertEquals(1, move?.diceValue)
        assertEquals(FlnRollOnlyReason.NO_MOVES, move?.rollOnlyReason)
        assertNull(move?.pieceId)
    }

    @Test
    fun deriveMoveCapturesThreeSixesForfeitEntry() {
        val start = GameEngine.newGame(
            activeColors = listOf(PlayerColor.RED, PlayerColor.BLUE)
        )

        val before = start.copy(
            turnPhase = TurnPhase.WAITING_FOR_ROLL,
            lastDice = DiceResult(value = 6, rollCount = 2)
        )

        val after = GameEngine.rollDice(before, forcedDiceValue = 6)
        val move = FlnMoveRecorder.deriveMove(before, after, ply = 20)

        assertEquals(20, move?.ply)
        assertEquals(PlayerColor.RED.ordinal + 1, move?.actorId?.value)
        assertEquals(6, move?.diceValue)
        assertEquals(FlnRollOnlyReason.THREE_SIX_FORFEIT, move?.rollOnlyReason)
        assertNull(move?.pieceId)
    }
}
