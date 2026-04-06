package com.failureludo.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeterministicReplaySupportTest {

    @Test
    fun forcedRollUsesProvidedDiceValue() {
        val state = GameEngine.newGame(
            activeColors = listOf(PlayerColor.RED, PlayerColor.BLUE)
        )

        val rolled = GameEngine.rollDice(state, forcedDiceValue = 6)

        assertEquals(6, rolled.lastDice?.value)
        assertEquals(TurnPhase.WAITING_FOR_PIECE_SELECTION, rolled.turnPhase)
        assertTrue(rolled.movablePieces.isNotEmpty())
    }

    @Test
    fun deterministicTurnMovesSpecifiedPiece() {
        val state = GameEngine.newGame(
            activeColors = listOf(PlayerColor.RED, PlayerColor.BLUE)
        )

        val afterMove = GameEngine.applyDeterministicTurn(
            state,
            DeterministicTurnInput(
                actorId = PlayerId(1),
                movingPlayerId = PlayerId(1),
                pieceId = 0,
                diceValue = 6
            )
        )

        val red = afterMove.players.first { it.id == PlayerId(1) }
        val movedPiece = red.pieces.first { it.id == 0 }

        assertTrue(movedPiece.position is PiecePosition.MainTrack)
        assertEquals(1L, afterMove.moveCounter)
        assertEquals(PlayerId(1), afterMove.currentPlayer.id) // extra roll after six
        assertEquals(TurnPhase.WAITING_FOR_ROLL, afterMove.turnPhase)
    }

    @Test
    fun deterministicTurnCanMoveTeammatePieceWhenSharedDiceUnlocked() {
        val players = listOf(
            Player(
                id = PlayerId(1),
                color = PlayerColor.RED,
                name = "Red",
                pieces = listOf(
                    Piece(id = 0, color = PlayerColor.RED, position = PiecePosition.MainTrack(5)),
                    Piece(id = 1, color = PlayerColor.RED),
                    Piece(id = 2, color = PlayerColor.RED),
                    Piece(id = 3, color = PlayerColor.RED)
                ),
                isActive = true
            ),
            Player(
                id = PlayerId(2),
                color = PlayerColor.BLUE,
                name = "Blue",
                pieces = List(4) { id -> Piece(id = id, color = PlayerColor.BLUE) },
                isActive = true
            ),
            Player(
                id = PlayerId(3),
                color = PlayerColor.YELLOW,
                name = "Yellow",
                pieces = listOf(
                    Piece(id = 0, color = PlayerColor.YELLOW, position = PiecePosition.MainTrack(10)),
                    Piece(id = 1, color = PlayerColor.YELLOW),
                    Piece(id = 2, color = PlayerColor.YELLOW),
                    Piece(id = 3, color = PlayerColor.YELLOW)
                ),
                isActive = true
            ),
            Player(
                id = PlayerId(4),
                color = PlayerColor.GREEN,
                name = "Green",
                pieces = List(4) { id -> Piece(id = id, color = PlayerColor.GREEN) },
                isActive = true
            )
        )

        val state = GameState(
            players = players,
            mode = GameMode.TEAM,
            currentPlayerIndex = 0,
            turnPhase = TurnPhase.WAITING_FOR_ROLL,
            hasEnteredBoardAtLeastOnce = mapOf(
                PlayerId(1) to true,
                PlayerId(2) to false,
                PlayerId(3) to true,
                PlayerId(4) to false
            ),
            sharedTeamDiceEnabled = setOf(PlayerColor.RED.teamIndex)
        )

        val afterMove = GameEngine.applyDeterministicTurn(
            state,
            DeterministicTurnInput(
                actorId = PlayerId(1),
                movingPlayerId = PlayerId(3),
                pieceId = 0,
                diceValue = 2
            )
        )

        val yellowPiece = afterMove.players
            .first { it.id == PlayerId(3) }
            .pieces
            .first { it.id == 0 }

        assertEquals(PiecePosition.MainTrack(12), yellowPiece.position)
        assertEquals(1L, afterMove.moveCounter)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rollOnlyRejectsWhenMovablePiecesExist() {
        val state = GameEngine.newGame(
            activeColors = listOf(PlayerColor.RED, PlayerColor.BLUE)
        )

        GameEngine.applyDeterministicRollOnly(
            state = state,
            actorId = PlayerId(1),
            diceValue = 6
        )
    }

    @Test
    fun rollOnlyAdvancesTurnWhenNoMoves() {
        val state = GameEngine.newGame(
            activeColors = listOf(PlayerColor.RED, PlayerColor.BLUE)
        )

        val next = GameEngine.applyDeterministicRollOnly(
            state = state,
            actorId = PlayerId(1),
            diceValue = 1
        )

        assertEquals(PlayerId(2), next.currentPlayer.id)
        assertEquals(TurnPhase.WAITING_FOR_ROLL, next.turnPhase)
        assertTrue(next.eventLog.last() is GameEvent.TurnSkipped)
    }
}
