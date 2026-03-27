package com.failureludo.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerIdentityMigrationTest {

    @Test
    fun `player id must be within 1 to 4`() {
        assertEquals(1, PlayerId(1).value)
        assertEquals(4, PlayerId(4).value)

        runCatching { PlayerId(0) }.exceptionOrNull()
            ?: error("Expected PlayerId(0) to throw")

        runCatching { PlayerId(5) }.exceptionOrNull()
            ?: error("Expected PlayerId(5) to throw")
    }

    @Test
    fun `new game initializes id keyed dice map for all players`() {
        val state = GameEngine.newGame(
            activeColors = listOf(
                PlayerColor.RED,
                PlayerColor.BLUE,
                PlayerColor.YELLOW,
                PlayerColor.GREEN
            ),
            mode = GameMode.FREE_FOR_ALL
        )

        val expectedKeys = setOf(PlayerId(1), PlayerId(2), PlayerId(3), PlayerId(4))

        assertEquals(expectedKeys, state.diceByPlayer.keys)
        assertTrue(state.diceByPlayer.values.all { it == null })
    }

    @Test
    fun `new game uses player placeholder names by default`() {
        val state = GameEngine.newGame(
            activeColors = listOf(
                PlayerColor.RED,
                PlayerColor.BLUE,
                PlayerColor.YELLOW,
                PlayerColor.GREEN
            ),
            mode = GameMode.FREE_FOR_ALL
        )

        val namesById = state.players.associate { it.id to it.name }

        assertEquals("Player-1", namesById[PlayerId(1)])
        assertEquals("Player-2", namesById[PlayerId(2)])
        assertEquals("Player-3", namesById[PlayerId(3)])
        assertEquals("Player-4", namesById[PlayerId(4)])
    }

    @Test
    fun `free for all winner is returned as player id`() {
        val state = GameEngine.newGame(
            activeColors = listOf(
                PlayerColor.RED,
                PlayerColor.BLUE,
                PlayerColor.YELLOW,
                PlayerColor.GREEN
            ),
            mode = GameMode.FREE_FOR_ALL
        )

        val redWinner = state.players.first { it.color == PlayerColor.RED }
            .copy(pieces = finishedPieces(PlayerColor.RED))

        val players = state.players.map { player ->
            if (player.color == PlayerColor.RED) redWinner else player
        }

        val winners = GameRules.checkWinner(players, GameMode.FREE_FOR_ALL)

        assertEquals(listOf(PlayerId(1)), winners)
    }

    @Test
    fun `team winner returns both player ids`() {
        val state = GameEngine.newGame(
            activeColors = listOf(
                PlayerColor.RED,
                PlayerColor.BLUE,
                PlayerColor.YELLOW,
                PlayerColor.GREEN
            ),
            mode = GameMode.TEAM
        )

        val players = state.players.map { player ->
            if (player.color == PlayerColor.RED || player.color == PlayerColor.YELLOW) {
                player.copy(pieces = finishedPieces(player.color))
            } else {
                player
            }
        }

        val winners = GameRules.checkWinner(players, GameMode.TEAM)

        assertEquals(listOf(PlayerId(1), PlayerId(3)), winners)
    }

    private fun finishedPieces(color: PlayerColor): List<Piece> =
        List(4) { id ->
            Piece(
                id = id,
                color = color,
                position = PiecePosition.Finished
            )
        }
}
