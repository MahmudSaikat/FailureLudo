package com.failureludo.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StackedMoveSelectionTest {

    @Test
    fun `triple stack keeps top single movable on odd dice`() {
        val players = buildPlayersWithRedTripleStack(mainIndex = 5)
        val red = players.first { it.color == PlayerColor.RED }
        val topSingle = red.pieces.first { it.id == 2 }
        val pairPiece = red.pieces.first { it.id == 0 }

        assertTrue(
            GameRules.canMove(
                piece = topSingle,
                diceValue = 3,
                player = red,
                allPlayers = players,
                mode = GameMode.FREE_FOR_ALL
            )
        )

        assertFalse(
            GameRules.canMove(
                piece = pairPiece,
                diceValue = 3,
                player = red,
                allPlayers = players,
                mode = GameMode.FREE_FOR_ALL
            )
        )
    }

    @Test
    fun `triple stack allows pair movement on even dice`() {
        val players = buildPlayersWithRedTripleStack(mainIndex = 5)
        val red = players.first { it.color == PlayerColor.RED }

        val movableOnEven = GameRules.movablePieces(
            player = red,
            diceValue = 4,
            allPlayers = players,
            mode = GameMode.FREE_FOR_ALL
        )

        assertEquals(setOf(0, 1, 2), movableOnEven.map { it.id }.toSet())
    }

    @Test
    fun `selecting pair vs single moves different refs from triple stack`() {
        val players = buildPlayersWithRedTripleStack(mainIndex = 5)
        val red = players.first { it.color == PlayerColor.RED }
        val pairRepresentative = red.pieces.first { it.id == 0 }
        val topSingle = red.pieces.first { it.id == 2 }

        val pairMovedPlayers = GameRules.applyMove(
            piece = pairRepresentative,
            diceValue = 4,
            color = PlayerColor.RED,
            players = players,
            mode = GameMode.FREE_FOR_ALL,
            movedAt = 100
        )

        val redAfterPairMove = pairMovedPlayers.first { it.color == PlayerColor.RED }
        assertEquals(PiecePosition.MainTrack(7), redAfterPairMove.pieces.first { it.id == 0 }.position)
        assertEquals(PiecePosition.MainTrack(7), redAfterPairMove.pieces.first { it.id == 1 }.position)
        assertEquals(PiecePosition.MainTrack(5), redAfterPairMove.pieces.first { it.id == 2 }.position)

        val singleMovedPlayers = GameRules.applyMove(
            piece = topSingle,
            diceValue = 4,
            color = PlayerColor.RED,
            players = players,
            mode = GameMode.FREE_FOR_ALL,
            movedAt = 101
        )

        val redAfterSingleMove = singleMovedPlayers.first { it.color == PlayerColor.RED }
        assertEquals(PiecePosition.MainTrack(5), redAfterSingleMove.pieces.first { it.id == 0 }.position)
        assertEquals(PiecePosition.MainTrack(5), redAfterSingleMove.pieces.first { it.id == 1 }.position)
        assertEquals(PiecePosition.MainTrack(9), redAfterSingleMove.pieces.first { it.id == 2 }.position)
    }

    private fun buildPlayersWithRedTripleStack(mainIndex: Int): List<Player> {
        val redPieces = listOf(
            Piece(id = 0, color = PlayerColor.RED, position = PiecePosition.MainTrack(mainIndex), lastMovedAt = 10),
            Piece(id = 1, color = PlayerColor.RED, position = PiecePosition.MainTrack(mainIndex), lastMovedAt = 20),
            Piece(id = 2, color = PlayerColor.RED, position = PiecePosition.MainTrack(mainIndex), lastMovedAt = 30),
            Piece(id = 3, color = PlayerColor.RED, position = PiecePosition.HomeBase)
        )

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
                pieces = List(4) { id -> Piece(id = id, color = PlayerColor.BLUE) },
                isActive = true
            ),
            Player(
                id = PlayerId(3),
                color = PlayerColor.YELLOW,
                name = "Yellow",
                pieces = List(4) { id -> Piece(id = id, color = PlayerColor.YELLOW) },
                isActive = false
            ),
            Player(
                id = PlayerId(4),
                color = PlayerColor.GREEN,
                name = "Green",
                pieces = List(4) { id -> Piece(id = id, color = PlayerColor.GREEN) },
                isActive = false
            )
        )
    }
}
