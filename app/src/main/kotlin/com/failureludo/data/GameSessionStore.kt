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
import com.failureludo.engine.GameRules
import com.failureludo.engine.GameState
import com.failureludo.engine.Piece
import com.failureludo.engine.PiecePosition
import com.failureludo.engine.Player
import com.failureludo.engine.PlayerColor
import com.failureludo.engine.PlayerId
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

    companion object {
        private const val SESSION_SCHEMA_VERSION = 3
        private val LEGACY_ONE_BASED_PIECE_IDS = setOf(1, 2, 3, 4)
    }

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
            .put("schemaVersion", SESSION_SCHEMA_VERSION)
            .put("activeColors", JSONArray(state.activeColors.map { it.name }))
            .put("playerTypes", playerTypesObj)
            .put("playerNames", playerNamesObj)
            .put("playerColors", playerColorsObj)
            .put("mode", state.mode.name)
    }

    private fun setupStateFromJson(json: JSONObject): SetupState {
        val schemaVersion = json.optInt("schemaVersion", 0)
        require(schemaVersion == SESSION_SCHEMA_VERSION) {
            "Unsupported setup schema version: $schemaVersion"
        }

        val activeColors = json.getJSONArray("activeColors").toStringList().map { PlayerColor.valueOf(it) }

        val playerTypesObj = json.getJSONObject("playerTypes")
        val playerTypes = PlayerColor.entries.associateWith { color ->
            if (playerTypesObj.has(color.name)) PlayerType.valueOf(playerTypesObj.getString(color.name))
            else PlayerType.HUMAN
        }

        val playerNamesObj = json.getJSONObject("playerNames")
        val playerNames = PlayerColor.entries.associateWith { color ->
            if (playerNamesObj.has(color.name)) playerNamesObj.getString(color.name)
            else "Player-${color.ordinal + 1}"
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
            .put("schemaVersion", SESSION_SCHEMA_VERSION)
            .put("players", JSONArray(state.players.map { playerToJson(it) }))
            .put("mode", state.mode.name)
            .put("moveCounter", state.moveCounter)
            .put("currentPlayerIndex", state.currentPlayerIndex)
            .put("turnPhase", state.turnPhase.name)
            .put("lastDice", state.lastDice?.let { diceToJson(it) } ?: JSONObject.NULL)
            .put("diceByPlayer", diceByPlayerToJson(state.diceByPlayer))
            .put("movablePieces", JSONArray(state.movablePieces.map { pieceToJson(it) }))
                .put("winners", JSONArray((state.winners ?: emptyList()).map { it.value }))
            .put("eventLog", JSONArray(state.eventLog.map { eventToJson(it) }))
    }

    private fun gameStateFromJson(json: JSONObject): GameState {
        val schemaVersion = json.optInt("schemaVersion", 0)
        require(schemaVersion == SESSION_SCHEMA_VERSION) {
            "Unsupported game schema version: $schemaVersion"
        }

        val players = json.getJSONArray("players").toObjectList { obj -> playerFromJson(obj) }
        val mode = GameMode.valueOf(json.getString("mode"))
        val moveCounter = if (json.has("moveCounter")) json.getLong("moveCounter") else 0L
        val currentPlayerIndex = json.getInt("currentPlayerIndex")
        val turnPhase = TurnPhase.valueOf(json.getString("turnPhase"))

        val lastDice = if (json.isNull("lastDice")) null
        else diceFromJson(json.getJSONObject("lastDice"))

        val diceByPlayer = diceByPlayerFromJson(json.getJSONObject("diceByPlayer"))
        val rawMovablePieces = json.getJSONArray("movablePieces").toObjectList { obj -> pieceFromJson(obj) }
        val movablePieces = when {
            turnPhase == TurnPhase.WAITING_FOR_PIECE_SELECTION &&
                lastDice != null &&
                currentPlayerIndex in players.indices -> {
                GameRules.movablePieces(
                    player = players[currentPlayerIndex],
                    diceValue = lastDice.value,
                    allPlayers = players,
                    mode = mode
                )
            }
            turnPhase == TurnPhase.WAITING_FOR_PIECE_SELECTION -> rawMovablePieces
            else -> emptyList()
        }
        val winnersRaw = json.getJSONArray("winners").toIntList()
        val winners = winnersRaw.map { PlayerId(it) }.takeIf { it.isNotEmpty() }
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
            .put("id", player.id.value)
            .put("color", player.color.name)
            .put("name", player.name)
            .put("type", player.type.name)
            .put("isActive", player.isActive)
            .put("pieces", JSONArray(player.pieces.map { pieceToJson(it) }))
    }

    private fun playerFromJson(json: JSONObject): Player {
        val color = PlayerColor.valueOf(json.getString("color"))
        val id = if (json.has("id")) {
            PlayerId(json.getInt("id"))
        } else {
            PlayerId(color.ordinal + 1)
        }

        val pieces = normalizeLegacyPieceIds(
            pieces = json.getJSONArray("pieces").toObjectList { pieceFromJson(it) }
        )

        return Player(
            id = id,
            color = color,
            name = json.getString("name"),
            type = PlayerType.valueOf(json.getString("type")),
            pieces = pieces,
            isActive = json.getBoolean("isActive")
        )
    }

    private fun normalizeLegacyPieceIds(pieces: List<Piece>): List<Piece> {
        val ids = pieces.map { it.id }.toSet()
        return if (ids == LEGACY_ONE_BASED_PIECE_IDS) {
            pieces.map { piece -> piece.copy(id = piece.id - 1) }
        } else {
            pieces
        }
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

    private fun diceByPlayerToJson(diceByPlayer: Map<PlayerId, Int?>): JSONObject {
        val obj = JSONObject()
        (1..4).forEach { idValue ->
            val id = PlayerId(idValue)
            val value = diceByPlayer[id]
            obj.put(idValue.toString(), value ?: JSONObject.NULL)
        }
        return obj
    }

    private fun diceByPlayerFromJson(json: JSONObject): Map<PlayerId, Int?> {
        return (1..4).associate { idValue ->
            val key = idValue.toString()
            val value = if (!json.has(key) || json.isNull(key)) null else json.getInt(key)
            PlayerId(idValue) to value
        }
    }

    private fun eventToJson(event: GameEvent): JSONObject {
        return when (event) {
            is GameEvent.PieceMoved -> JSONObject()
                .put("type", "PIECE_MOVED")
                .put("playerId", event.playerId.value)
                .put("color", event.color.name)
                .put("pieceId", event.pieceId)

            is GameEvent.PieceEnteredBoard -> JSONObject()
                .put("type", "PIECE_ENTERED")
                .put("playerId", event.playerId.value)
                .put("color", event.color.name)
                .put("pieceId", event.pieceId)

            is GameEvent.PieceCaptured -> JSONObject()
                .put("type", "PIECE_CAPTURED")
                .put("capturedPlayerId", event.capturedPlayerId.value)
                .put("capturedColor", event.capturedColor.name)
                .put("byPlayerId", event.byPlayerId.value)
                .put("byColor", event.byColor.name)

            is GameEvent.PieceFinished -> JSONObject()
                .put("type", "PIECE_FINISHED")
                .put("playerId", event.playerId.value)
                .put("color", event.color.name)
                .put("pieceId", event.pieceId)

            is GameEvent.PlayerWon -> JSONObject()
                .put("type", "PLAYER_WON")
                .put("playerIds", JSONArray(event.playerIds.map { it.value }))

            is GameEvent.ExtraRollGranted -> JSONObject()
                .put("type", "EXTRA_ROLL")
                .put("playerId", event.playerId.value)
                .put("color", event.color.name)
                .put("reason", event.reason)

            is GameEvent.TurnSkipped -> JSONObject()
                .put("type", "TURN_SKIPPED")
                .put("playerId", event.playerId.value)
                .put("color", event.color.name)

            is GameEvent.ConsecutiveSixesForfeit -> JSONObject()
                .put("type", "THREE_SIX_FORFEIT")
                .put("playerId", event.playerId.value)
                .put("color", event.color.name)
        }
    }

    private fun eventFromJson(json: JSONObject): GameEvent {
        return when (json.getString("type")) {
            "PIECE_MOVED" -> GameEvent.PieceMoved(
                playerId = PlayerId(json.getInt("playerId")),
                color = PlayerColor.valueOf(json.getString("color")),
                pieceId = json.getInt("pieceId")
            )

            "PIECE_ENTERED" -> GameEvent.PieceEnteredBoard(
                playerId = PlayerId(json.getInt("playerId")),
                color = PlayerColor.valueOf(json.getString("color")),
                pieceId = json.getInt("pieceId")
            )

            "PIECE_CAPTURED" -> GameEvent.PieceCaptured(
                capturedPlayerId = PlayerId(json.getInt("capturedPlayerId")),
                capturedColor = PlayerColor.valueOf(json.getString("capturedColor")),
                byPlayerId = PlayerId(json.getInt("byPlayerId")),
                byColor = PlayerColor.valueOf(json.getString("byColor"))
            )

            "PIECE_FINISHED" -> GameEvent.PieceFinished(
                playerId = PlayerId(json.getInt("playerId")),
                color = PlayerColor.valueOf(json.getString("color")),
                pieceId = json.getInt("pieceId")
            )

            "PLAYER_WON" -> {
                val playerIds = json.getJSONArray("playerIds").toIntList().map { PlayerId(it) }
                GameEvent.PlayerWon(playerIds)
            }

            "EXTRA_ROLL" -> GameEvent.ExtraRollGranted(
                playerId = PlayerId(json.getInt("playerId")),
                color = PlayerColor.valueOf(json.getString("color")),
                reason = json.getString("reason")
            )

            "TURN_SKIPPED" -> GameEvent.TurnSkipped(
                playerId = PlayerId(json.getInt("playerId")),
                color = PlayerColor.valueOf(json.getString("color"))
            )

            "THREE_SIX_FORFEIT" -> GameEvent.ConsecutiveSixesForfeit(
                playerId = PlayerId(json.getInt("playerId")),
                color = PlayerColor.valueOf(json.getString("color"))
            )

            else -> GameEvent.TurnSkipped(playerId = PlayerId(1), color = PlayerColor.RED)
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

    private fun JSONArray.toIntList(): List<Int> {
        val result = mutableListOf<Int>()
        for (i in 0 until length()) {
            result += getInt(i)
        }
        return result
    }
}
