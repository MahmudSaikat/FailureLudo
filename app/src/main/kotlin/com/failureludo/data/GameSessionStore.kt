package com.failureludo.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.failureludo.engine.DiceResult
import com.failureludo.engine.GameEvent
import com.failureludo.engine.GameMode
import com.failureludo.engine.GameState
import com.failureludo.engine.Piece
import com.failureludo.engine.PiecePosition
import com.failureludo.engine.Player
import com.failureludo.engine.PlayerColor
import com.failureludo.engine.PlayerType
import com.failureludo.engine.TurnPhase
import com.failureludo.viewmodel.SetupState
import com.failureludo.viewmodel.defaultPlayerColors
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

private val Context.gameSessionDataStore by preferencesDataStore(name = "game_session")

class GameSessionStore(private val context: Context) {

    private object Keys {
        val GAME_STATE_JSON = stringPreferencesKey("game_state_json")
        val GAME_STATE_JSON_BACKUP = stringPreferencesKey("game_state_json_backup")
        val SETUP_STATE_JSON = stringPreferencesKey("setup_state_json")
    }

    suspend fun saveSetupState(setupState: SetupState) {
        context.gameSessionDataStore.edit { prefs ->
            prefs[Keys.SETUP_STATE_JSON] = setupStateToJson(setupState).toString()
        }
    }

    suspend fun loadSetupState(): SetupState? {
        val prefs = context.gameSessionDataStore.data
            .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
            .first()

        val raw = prefs[Keys.SETUP_STATE_JSON] ?: return null
        return runCatching { setupStateFromJson(JSONObject(raw)) }.getOrNull()
    }

    suspend fun saveGameState(gameState: GameState?) {
        context.gameSessionDataStore.edit { prefs ->
            if (gameState == null) {
                prefs.remove(Keys.GAME_STATE_JSON)
                prefs.remove(Keys.GAME_STATE_JSON_BACKUP)
                return@edit
            }

            val current = prefs[Keys.GAME_STATE_JSON]
            if (current != null) {
                prefs[Keys.GAME_STATE_JSON_BACKUP] = current
            }

            prefs[Keys.GAME_STATE_JSON] = gameStateToJson(gameState).toString()
        }
    }

    suspend fun loadGameState(): GameState? {
        val prefs = context.gameSessionDataStore.data
            .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
            .first()

        val primaryRaw = prefs[Keys.GAME_STATE_JSON]
        val primary = primaryRaw?.let { runCatching { gameStateFromJson(JSONObject(it)) }.getOrNull() }
        if (primary != null) return primary

        val backupRaw = prefs[Keys.GAME_STATE_JSON_BACKUP]
        return backupRaw?.let { runCatching { gameStateFromJson(JSONObject(it)) }.getOrNull() }
    }

    private fun setupStateToJson(state: SetupState): JSONObject {
        val playerTypesObj = JSONObject()
        state.playerTypes.forEach { (color, type) ->
            playerTypesObj.put(color.name, type.name)
        }

        val playerNamesObj = JSONObject()
        state.playerNames.forEach { (color, name) ->
            playerNamesObj.put(color.name, name)
        }

        val playerColorsObj = JSONObject()
        state.playerColors.forEach { (color, value) ->
            playerColorsObj.put(color.name, value.toArgb())
        }

        return JSONObject()
            .put("activeColors", JSONArray(state.activeColors.map { it.name }))
            .put("playerTypes", playerTypesObj)
            .put("playerNames", playerNamesObj)
            .put("playerColors", playerColorsObj)
            .put("mode", state.mode.name)
    }

    private fun setupStateFromJson(json: JSONObject): SetupState {
        val activeColors = json.getJSONArray("activeColors").toStringList().map { PlayerColor.valueOf(it) }

        val playerTypesObj = json.getJSONObject("playerTypes")
        val playerTypes = PlayerColor.entries.associateWith { color ->
            if (playerTypesObj.has(color.name)) PlayerType.valueOf(playerTypesObj.getString(color.name))
            else PlayerType.HUMAN
        }

        val playerNamesObj = json.getJSONObject("playerNames")
        val playerNames = PlayerColor.entries.associateWith { color ->
            if (playerNamesObj.has(color.name)) playerNamesObj.getString(color.name)
            else color.displayName
        }

        val defaultColors = defaultPlayerColors()
        val playerColorsObj = json.optJSONObject("playerColors")
        val playerColors = PlayerColor.entries.associateWith { color ->
            if (playerColorsObj != null && playerColorsObj.has(color.name)) {
                Color(playerColorsObj.getInt(color.name))
            } else {
                defaultColors[color] ?: Color.Unspecified
            }
        }

        val mode = GameMode.valueOf(json.getString("mode"))

        return SetupState(
            activeColors = activeColors,
            playerTypes = playerTypes,
            playerNames = playerNames,
            playerColors = playerColors,
            mode = mode
        )
    }

    private fun gameStateToJson(state: GameState): JSONObject {
        return JSONObject()
            .put("players", JSONArray(state.players.map { playerToJson(it) }))
            .put("mode", state.mode.name)
            .put("moveCounter", state.moveCounter)
            .put("currentPlayerIndex", state.currentPlayerIndex)
            .put("turnPhase", state.turnPhase.name)
            .put("lastDice", state.lastDice?.let { diceToJson(it) } ?: JSONObject.NULL)
            .put("diceByPlayer", diceByPlayerToJson(state.diceByPlayer))
            .put("movablePieces", JSONArray(state.movablePieces.map { pieceToJson(it) }))
            .put("winners", JSONArray((state.winners ?: emptyList()).map { it.name }))
            .put("eventLog", JSONArray(state.eventLog.map { eventToJson(it) }))
    }

    private fun gameStateFromJson(json: JSONObject): GameState {
        val players = json.getJSONArray("players").toObjectList { obj -> playerFromJson(obj) }
        val mode = GameMode.valueOf(json.getString("mode"))
        val moveCounter = if (json.has("moveCounter")) json.getLong("moveCounter") else 0L
        val currentPlayerIndex = json.getInt("currentPlayerIndex")
        val turnPhase = TurnPhase.valueOf(json.getString("turnPhase"))

        val lastDice = if (json.isNull("lastDice")) null
        else diceFromJson(json.getJSONObject("lastDice"))

        val diceByPlayer = diceByPlayerFromJson(json.getJSONObject("diceByPlayer"))
        val movablePieces = json.getJSONArray("movablePieces").toObjectList { obj -> pieceFromJson(obj) }
        val winnersRaw = json.getJSONArray("winners").toStringList()
        val winners = winnersRaw.map { PlayerColor.valueOf(it) }.takeIf { it.isNotEmpty() }
        val eventLog = json.getJSONArray("eventLog").toObjectList { obj -> eventFromJson(obj) }

        return GameState(
            players = players,
            mode = mode,
            moveCounter = moveCounter,
            currentPlayerIndex = currentPlayerIndex,
            turnPhase = turnPhase,
            lastDice = lastDice,
            diceByPlayer = diceByPlayer,
            movablePieces = movablePieces,
            winners = winners,
            eventLog = eventLog
        )
    }

    private fun playerToJson(player: Player): JSONObject {
        return JSONObject()
            .put("color", player.color.name)
            .put("name", player.name)
            .put("type", player.type.name)
            .put("isActive", player.isActive)
            .put("pieces", JSONArray(player.pieces.map { pieceToJson(it) }))
    }

    private fun playerFromJson(json: JSONObject): Player {
        return Player(
            color = PlayerColor.valueOf(json.getString("color")),
            name = json.getString("name"),
            type = PlayerType.valueOf(json.getString("type")),
            pieces = json.getJSONArray("pieces").toObjectList { pieceFromJson(it) },
            isActive = json.getBoolean("isActive")
        )
    }

    private fun pieceToJson(piece: Piece): JSONObject {
        return JSONObject()
            .put("id", piece.id)
            .put("color", piece.color.name)
            .put("position", piecePositionToJson(piece.position))
            .put("lastMovedAt", piece.lastMovedAt)
    }

    private fun pieceFromJson(json: JSONObject): Piece {
        return Piece(
            id = json.getInt("id"),
            color = PlayerColor.valueOf(json.getString("color")),
            position = piecePositionFromJson(json.getJSONObject("position")),
            lastMovedAt = if (json.has("lastMovedAt")) json.getLong("lastMovedAt") else 0L
        )
    }

    private fun diceToJson(dice: DiceResult): JSONObject {
        return JSONObject()
            .put("value", dice.value)
            .put("rollCount", dice.rollCount)
    }

    private fun diceFromJson(json: JSONObject): DiceResult {
        return DiceResult(
            value = json.getInt("value"),
            rollCount = json.getInt("rollCount")
        )
    }

    private fun piecePositionToJson(position: PiecePosition): JSONObject {
        return when (position) {
            is PiecePosition.HomeBase -> JSONObject().put("type", "HOME_BASE")
            is PiecePosition.MainTrack -> JSONObject().put("type", "MAIN_TRACK").put("index", position.index)
            is PiecePosition.HomeColumn -> JSONObject().put("type", "HOME_COLUMN").put("step", position.step)
            is PiecePosition.Finished -> JSONObject().put("type", "FINISHED")
        }
    }

    private fun piecePositionFromJson(json: JSONObject): PiecePosition {
        return when (json.getString("type")) {
            "HOME_BASE" -> PiecePosition.HomeBase
            "MAIN_TRACK" -> PiecePosition.MainTrack(json.getInt("index"))
            "HOME_COLUMN" -> PiecePosition.HomeColumn(json.getInt("step"))
            "FINISHED" -> PiecePosition.Finished
            else -> PiecePosition.HomeBase
        }
    }

    private fun diceByPlayerToJson(diceByPlayer: Map<PlayerColor, Int?>): JSONObject {
        val obj = JSONObject()
        PlayerColor.entries.forEach { color ->
            val value = diceByPlayer[color]
            obj.put(color.name, value ?: JSONObject.NULL)
        }
        return obj
    }

    private fun diceByPlayerFromJson(json: JSONObject): Map<PlayerColor, Int?> {
        return PlayerColor.entries.associateWith { color ->
            if (!json.has(color.name) || json.isNull(color.name)) null
            else json.getInt(color.name)
        }
    }

    private fun eventToJson(event: GameEvent): JSONObject {
        return when (event) {
            is GameEvent.PieceMoved -> JSONObject()
                .put("type", "PIECE_MOVED")
                .put("color", event.color.name)
                .put("pieceId", event.pieceId)

            is GameEvent.PieceEnteredBoard -> JSONObject()
                .put("type", "PIECE_ENTERED")
                .put("color", event.color.name)
                .put("pieceId", event.pieceId)

            is GameEvent.PieceCaptured -> JSONObject()
                .put("type", "PIECE_CAPTURED")
                .put("capturedColor", event.capturedColor.name)
                .put("byColor", event.byColor.name)

            is GameEvent.PieceFinished -> JSONObject()
                .put("type", "PIECE_FINISHED")
                .put("color", event.color.name)
                .put("pieceId", event.pieceId)

            is GameEvent.PlayerWon -> JSONObject()
                .put("type", "PLAYER_WON")
                .put("colors", JSONArray(event.colors.map { it.name }))

            is GameEvent.ExtraRollGranted -> JSONObject()
                .put("type", "EXTRA_ROLL")
                .put("color", event.color.name)
                .put("reason", event.reason)

            is GameEvent.TurnSkipped -> JSONObject()
                .put("type", "TURN_SKIPPED")
                .put("color", event.color.name)

            is GameEvent.ConsecutiveSixesForfeit -> JSONObject()
                .put("type", "THREE_SIX_FORFEIT")
                .put("color", event.color.name)
        }
    }

    private fun eventFromJson(json: JSONObject): GameEvent {
        return when (json.getString("type")) {
            "PIECE_MOVED" -> GameEvent.PieceMoved(
                color = PlayerColor.valueOf(json.getString("color")),
                pieceId = json.getInt("pieceId")
            )

            "PIECE_ENTERED" -> GameEvent.PieceEnteredBoard(
                color = PlayerColor.valueOf(json.getString("color")),
                pieceId = json.getInt("pieceId")
            )

            "PIECE_CAPTURED" -> GameEvent.PieceCaptured(
                capturedColor = PlayerColor.valueOf(json.getString("capturedColor")),
                byColor = PlayerColor.valueOf(json.getString("byColor"))
            )

            "PIECE_FINISHED" -> GameEvent.PieceFinished(
                color = PlayerColor.valueOf(json.getString("color")),
                pieceId = json.getInt("pieceId")
            )

            "PLAYER_WON" -> {
                val colors = json.getJSONArray("colors").toStringList().map { PlayerColor.valueOf(it) }
                GameEvent.PlayerWon(colors)
            }

            "EXTRA_ROLL" -> GameEvent.ExtraRollGranted(
                color = PlayerColor.valueOf(json.getString("color")),
                reason = json.getString("reason")
            )

            "TURN_SKIPPED" -> GameEvent.TurnSkipped(
                color = PlayerColor.valueOf(json.getString("color"))
            )

            "THREE_SIX_FORFEIT" -> GameEvent.ConsecutiveSixesForfeit(
                color = PlayerColor.valueOf(json.getString("color"))
            )

            else -> GameEvent.TurnSkipped(PlayerColor.RED)
        }
    }

    private inline fun <T> JSONArray.toObjectList(block: (JSONObject) -> T): List<T> {
        val result = mutableListOf<T>()
        for (i in 0 until length()) {
            result += block(getJSONObject(i))
        }
        return result
    }

    private fun JSONArray.toStringList(): List<String> {
        val result = mutableListOf<String>()
        for (i in 0 until length()) {
            result += getString(i)
        }
        return result
    }
}
