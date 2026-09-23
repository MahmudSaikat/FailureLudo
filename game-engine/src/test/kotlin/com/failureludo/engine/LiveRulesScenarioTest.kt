package com.failureludo.engine

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** The same explicit rule examples run in ludo-web/tests/live-rules.test.cjs. */
@RunWith(Parameterized::class)
class LiveRulesScenarioTest(private val name: String, private val scenario: JSONObject) {
    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun scenarios(): Collection<Array<Any>> {
            val raw = requireNotNull(LiveRulesScenarioTest::class.java.getResource("/live-rule-scenarios.json")).readText()
            val cases = JSONArray(raw)
            return (0 until cases.length()).map { i ->
                val obj = cases.getJSONObject(i)
                arrayOf(obj.getString("name"), obj)
            }
        }
    }

    @Test
    fun agreedRuleScenario() {
        val mode = GameMode.valueOf(scenario.optString("mode", "FREE_FOR_ALL"))
        var state = GameEngine.newGame(PlayerColor.entries, mode = mode).copy(
            moveCounter = 100,
            currentPlayerIndex = PlayerColor.valueOf(scenario.optString("currentColor", "RED")).ordinal,
            sharedTeamDiceEnabled = scenario.optJSONArray("shared").items().map { (it as Number).toInt() }.toSet()
        )
        val entered = scenario.optJSONArray("entered").items().map { it.toString() }.toSet()
        state = state.copy(hasEnteredBoardAtLeastOnce = state.players.associate { it.id to (it.color.name in entered) })
        scenario.getJSONArray("pieces").objects().forEach { setup ->
            state = state.copy(players = state.players.map { owner ->
                if (owner.color.name != setup.getString("color")) owner else owner.copy(pieces = owner.pieces.map { pawn ->
                    if (pawn.id != setup.getInt("id")) pawn else pawn.copy(
                        position = position(setup.getString("position")),
                        lastMovedAt = setup.optLong("lastMovedAt", pawn.id.toLong()),
                        pairKey = setup.optString("pairKey").takeIf { it.isNotEmpty() }
                    )
                })
            })
        }
        checkExpected(state, scenario.optJSONObject("initialExpect"))
        scenario.getJSONArray("steps").objects().forEachIndexed { index, step ->
            try {
                fun apply(): GameState = when {
                    step.has("roll") -> GameEngine.rollDice(state, step.getInt("roll"))
                    step.has("move") -> GameEngine.selectPiece(state, piece(state, step.getString("move")), step.optBoolean("defer"))
                    step.optBoolean("skip") -> GameEngine.advanceNoMoves(state)
                    else -> error("Unknown scenario action")
                }
                if (step.optBoolean("expectError")) {
                    var rejected = false
                    try { apply() } catch (_: IllegalArgumentException) { rejected = true }
                    assertTrue("Illegal move was accepted", rejected)
                } else state = apply()
                checkExpected(state, step.optJSONObject("expect"))
            } catch (error: Throwable) {
                throw AssertionError("$name, step $index: $step", error)
            }
        }
    }

    private fun checkExpected(state: GameState, expected: JSONObject?) {
        if (expected == null) return
        if (expected.has("phase")) assertEquals(expected.getString("phase"), state.turnPhase.name)
        if (expected.has("current")) assertEquals(expected.getString("current"), state.currentPlayer.color.name)
        if (expected.has("count")) assertEquals(expected.getInt("count"), state.lastDice!!.rollCount)
        if (expected.has("shared")) assertEquals(expected.getJSONArray("shared").items().map { (it as Number).toInt() }.toSet(), state.sharedTeamDiceEnabled)
        val movable = state.movablePieces.map { "${it.color.name}:${it.id}" }.toSet()
        expected.optJSONArray("movable").items().forEach { assertTrue("Expected movable $it", it in movable) }
        expected.optJSONArray("notMovable").items().forEach { assertFalse("Unexpected movable $it", it in movable) }
        expected.optJSONObject("positions")?.let { positions -> positions.keys().forEach { ref ->
            assertEquals(ref, position(positions.getString(ref)), piece(state, ref).position)
        } }
        expected.optJSONObject("pairs")?.let { pairs -> pairs.keys().forEach { ref ->
            assertEquals(ref, if (pairs.isNull(ref)) null else pairs.getString(ref), piece(state, ref).pairKey)
        } }
        expected.optJSONArray("legal").objects().forEach { option ->
            val pawn = piece(state, option.getString("piece"))
            val owner = state.players.first { it.color == pawn.color }
            assertEquals("Legal option $option", option.getBoolean("result"), GameRules.canMove(
                pawn, option.getInt("dice"), owner, state.players, state.mode, option.optBoolean("defer")
            ))
        }
    }

    private fun piece(state: GameState, ref: String): Piece {
        val (color, id) = ref.split(":")
        return state.players.first { it.color.name == color }.pieces.first { it.id == id.toInt() }
    }

    private fun position(value: String): PiecePosition = when {
        value == "BASE" -> PiecePosition.HomeBase
        value == "FINISHED" -> PiecePosition.Finished
        value.startsWith("T") -> PiecePosition.MainTrack(value.drop(1).toInt())
        value.startsWith("H") -> PiecePosition.HomeColumn(value.drop(1).toInt())
        else -> error("Unknown position $value")
    }

    private fun JSONArray?.items(): List<Any> = if (this == null) emptyList() else (0 until length()).map { get(it) }
    private fun JSONArray?.objects(): List<JSONObject> = items().map { it as JSONObject }
}
