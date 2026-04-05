package com.failureludo.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TeamDiceSharingRuleTest {

    @Test
    fun `before unlock teammates cannot use each others dice`() {
        val players = buildTeamPlayers(
            redPieces = listOf(
                Piece(id = 0, color = PlayerColor.RED, position = PiecePosition.MainTrack(5), lastMovedAt = 10),
                Piece(id = 1, color = PlayerColor.RED),
                Piece(id = 2, color = PlayerColor.RED),
                Piece(id = 3, color = PlayerColor.RED)
            ),
            yellowPieces = listOf(
                Piece(id = 0, color = PlayerColor.YELLOW, position = PiecePosition.MainTrack(10), lastMovedAt = 20),
                Piece(id = 1, color = PlayerColor.YELLOW),
                Piece(id = 2, color = PlayerColor.YELLOW),
                Piece(id = 3, color = PlayerColor.YELLOW)
            )
        )

        val red = players.first { it.color == PlayerColor.RED }
        val movable = GameRules.movablePiecesForTurn(
            currentPlayer = red,
            diceValue = 2,
            allPlayers = players,
            mode = GameMode.TEAM,
            sharedTeamDiceEnabled = emptySet()
        )

        assertTrue(movable.isNotEmpty())
        assertTrue(movable.all { it.color == PlayerColor.RED })
        assertEquals(setOf(0), movable.map { it.id }.toSet())
    }

    @Test
    fun `after unlock teammates can use shared dice for both colors`() {
        val players = buildTeamPlayers(
            redPieces = listOf(
                Piece(id = 0, color = PlayerColor.RED, position = PiecePosition.MainTrack(5), lastMovedAt = 10),
                Piece(id = 1, color = PlayerColor.RED),
                Piece(id = 2, color = PlayerColor.RED),
                Piece(id = 3, color = PlayerColor.RED)
            ),
            yellowPieces = listOf(
                Piece(id = 0, color = PlayerColor.YELLOW, position = PiecePosition.MainTrack(10), lastMovedAt = 20),
                Piece(id = 1, color = PlayerColor.YELLOW),
                Piece(id = 2, color = PlayerColor.YELLOW),
                Piece(id = 3, color = PlayerColor.YELLOW)
            )
        )

        val red = players.first { it.color == PlayerColor.RED }
        val movable = GameRules.movablePiecesForTurn(
            currentPlayer = red,
            diceValue = 2,
            allPlayers = players,
            mode = GameMode.TEAM,
            sharedTeamDiceEnabled = setOf(PlayerColor.RED.teamIndex)
        )

        assertTrue(movable.any { it.color == PlayerColor.RED })
        assertTrue(movable.any { it.color == PlayerColor.YELLOW })
    }

    @Test
    fun `team dice unlocks from subsequent turn after both teammates have entered once`() {
        val players = buildTeamPlayers(
            redPieces = listOf(
                Piece(id = 0, color = PlayerColor.RED, position = PiecePosition.MainTrack(5), lastMovedAt = 10),
                Piece(id = 1, color = PlayerColor.RED),
                Piece(id = 2, color = PlayerColor.RED),
                Piece(id = 3, color = PlayerColor.RED)
            ),
            yellowPieces = listOf(
                Piece(id = 0, color = PlayerColor.YELLOW),
                Piece(id = 1, color = PlayerColor.YELLOW),
                Piece(id = 2, color = PlayerColor.YELLOW),
                Piece(id = 3, color = PlayerColor.YELLOW)
            )
        )

        val yellowPlayer = players.first { it.color == PlayerColor.YELLOW }
        val yellowHomePiece = yellowPlayer.pieces.first { it.id == 0 }

        val enteredFlags = mapOf(
            PlayerId(1) to true,
            PlayerId(2) to false,
            PlayerId(3) to false,
            PlayerId(4) to false
        )

        val state = GameState(
            players = players,
            mode = GameMode.TEAM,
            currentPlayerIndex = players.indexOfFirst { it.color == PlayerColor.YELLOW },
            turnPhase = TurnPhase.WAITING_FOR_PIECE_SELECTION,
            lastDice = DiceResult(value = 6, rollCount = 1),
            movablePieces = listOf(yellowHomePiece),
            hasEnteredBoardAtLeastOnce = enteredFlags,
            sharedTeamDiceEnabled = emptySet()
        )

        val afterEntry = GameEngine.selectPiece(state, yellowHomePiece)

        assertEquals(TurnPhase.WAITING_FOR_ROLL, afterEntry.turnPhase)
        assertTrue(afterEntry.hasEnteredBoardAtLeastOnce[PlayerId(3)] == true)
        assertFalse(afterEntry.sharedTeamDiceEnabled.contains(PlayerColor.RED.teamIndex))

        val afterTurnAdvance = GameEngine.advanceNoMoves(
            afterEntry.copy(turnPhase = TurnPhase.NO_MOVES_AVAILABLE)
        )

        assertTrue(afterTurnAdvance.sharedTeamDiceEnabled.contains(PlayerColor.RED.teamIndex))
    }

    private fun buildTeamPlayers(
        redPieces: List<Piece>,
        yellowPieces: List<Piece>
    ): List<Player> {
        val bluePieces = List(4) { id -> Piece(id = id, color = PlayerColor.BLUE) }
        val greenPieces = List(4) { id -> Piece(id = id, color = PlayerColor.GREEN) }

        return listOf(
            Player(
                id = PlayerId(1),
                color = PlayerColor.RED,
                name = "Red",
                pieces = redPieces,
                isActive = true
            ),
            Player(
                id = PlayerId(2),
                color = PlayerColor.BLUE,
                name = "Blue",
                pieces = bluePieces,
                isActive = true
            ),
            Player(
                id = PlayerId(3),
                color = PlayerColor.YELLOW,
                name = "Yellow",
                pieces = yellowPieces,
                isActive = true
            ),
            Player(
                id = PlayerId(4),
                color = PlayerColor.GREEN,
                name = "Green",
                pieces = greenPieces,
                isActive = true
            )
        )
    }
}
