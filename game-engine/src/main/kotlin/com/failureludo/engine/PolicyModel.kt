package com.failureludo.engine

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.math.max
import kotlin.math.min

internal data class DenseLayer(
    val weight: Array<FloatArray>,
    val bias: FloatArray
) {
    val outDim: Int
        get() = weight.size
    val inDim: Int
        get() = if (weight.isEmpty()) 0 else weight[0].size
}

internal class JsonPolicyModel private constructor(
    val stateDim: Int,
    val candidateDim: Int,
    val hiddenDim: Int,
    private val stateLinear1: DenseLayer,
    private val stateLinear2: DenseLayer,
    private val candidateLinear1: DenseLayer,
    private val candidateLinear2: DenseLayer
) {

    init {
        require(stateLinear1.inDim == stateDim)
        require(stateLinear1.outDim == hiddenDim)
        require(stateLinear2.inDim == hiddenDim)
        require(stateLinear2.outDim == hiddenDim)
        require(candidateLinear1.inDim == hiddenDim + candidateDim)
        require(candidateLinear1.outDim == hiddenDim)
        require(candidateLinear2.inDim == hiddenDim)
        require(candidateLinear2.outDim == 1)
    }

    fun scoreCandidates(stateFeatures: FloatArray, candidateFeatures: List<FloatArray>): FloatArray {
        require(stateFeatures.size == stateDim) {
            "State feature size mismatch: got ${stateFeatures.size}, expected $stateDim."
        }
        require(candidateFeatures.isNotEmpty()) { "At least one candidate is required." }
        require(candidateFeatures.all { it.size == candidateDim }) {
            "Candidate feature size mismatch: expected $candidateDim."
        }

        val encoded1 = relu(dense(stateLinear1, stateFeatures))
        val encoded2 = relu(dense(stateLinear2, encoded1))

        return FloatArray(candidateFeatures.size) { index ->
            val candidate = candidateFeatures[index]
            val merged = FloatArray(hiddenDim + candidateDim)
            System.arraycopy(encoded2, 0, merged, 0, hiddenDim)
            System.arraycopy(candidate, 0, merged, hiddenDim, candidateDim)

            val hidden = relu(dense(candidateLinear1, merged))
            dense(candidateLinear2, hidden)[0]
        }
    }

    private fun dense(layer: DenseLayer, input: FloatArray): FloatArray {
        val out = FloatArray(layer.outDim)
        for (row in 0 until layer.outDim) {
            var acc = layer.bias[row]
            val weights = layer.weight[row]
            for (col in input.indices) {
                acc += weights[col] * input[col]
            }
            out[row] = acc
        }
        return out
    }

    private fun relu(values: FloatArray): FloatArray {
        return FloatArray(values.size) { index -> max(0f, values[index]) }
    }

    companion object {
        private const val FORMAT_V1 = "failureludo-policy-mlp-v1"

        fun fromJson(raw: String): JsonPolicyModel {
            val root = JSONObject(raw)
            require(root.getString("format") == FORMAT_V1) {
                "Unsupported model format."
            }

            val stateDim = root.getInt("state_dim")
            val candidateDim = root.getInt("candidate_dim")
            val hiddenDim = root.getInt("hidden_dim")

            val stateEncoder = root.getJSONObject("state_encoder")
            val candidateHead = root.getJSONObject("candidate_head")

            val stateLinear1 = parseDenseLayer(stateEncoder.getJSONObject("linear1"))
            val stateLinear2 = parseDenseLayer(stateEncoder.getJSONObject("linear2"))
            val candidateLinear1 = parseDenseLayer(candidateHead.getJSONObject("linear1"))
            val candidateLinear2 = parseDenseLayer(candidateHead.getJSONObject("linear2"))

            return JsonPolicyModel(
                stateDim = stateDim,
                candidateDim = candidateDim,
                hiddenDim = hiddenDim,
                stateLinear1 = stateLinear1,
                stateLinear2 = stateLinear2,
                candidateLinear1 = candidateLinear1,
                candidateLinear2 = candidateLinear2
            )
        }

        private fun parseDenseLayer(json: JSONObject): DenseLayer {
            val weight = json.getJSONArray("weight").toMatrix()
            val bias = json.getJSONArray("bias").toFloatArray()
            require(weight.size == bias.size) {
                "Dense layer weight/bias shape mismatch."
            }
            return DenseLayer(weight = weight, bias = bias)
        }

        private fun JSONArray.toMatrix(): Array<FloatArray> {
            val rows = Array(length()) { rowIndex ->
                getJSONArray(rowIndex).toFloatArray()
            }
            if (rows.isNotEmpty()) {
                val cols = rows.first().size
                require(rows.all { it.size == cols }) {
                    "Matrix rows must have equal width."
                }
            }
            return rows
        }

        private fun JSONArray.toFloatArray(): FloatArray {
            return FloatArray(length()) { index -> getDouble(index).toFloat() }
        }
    }
}

internal data class PolicyMoveOption(
    val movingPlayerId: Int,
    val pieceId: Int,
    val pieceColor: PlayerColor,
    val deferHomeEntry: Boolean
)

internal object PolicyFeatureEncoder {
    const val STATE_DIM = 156
    const val CANDIDATE_DIM = 9

    private val playerIds = intArrayOf(1, 2, 3, 4)
    private val pieceIds = intArrayOf(0, 1, 2, 3)

    private val turnPhases = arrayOf(
        "WAITING_FOR_ROLL",
        "WAITING_FOR_PIECE_SELECTION",
        "NO_MOVES_AVAILABLE",
        "GAME_OVER"
    )

    private val modes = arrayOf("FREE_FOR_ALL", "TEAM")

    private val positionTypes = arrayOf(
        "HOME_BASE",
        "MAIN_TRACK",
        "HOME_COLUMN",
        "FINISHED"
    )

    fun encodeState(state: GameState): FloatArray {
        val features = ArrayList<Float>(STATE_DIM)

        val moveCounter = state.moveCounter.toFloat()
        features += min(moveCounter / 3000f, 1f)

        val currentPlayerId = state.currentPlayer.id.value
        addOneHot(features, currentPlayerId.toString(), playerIds.map { it.toString() }.toTypedArray())
        addOneHot(features, state.turnPhase.name, turnPhases)
        addOneHot(features, state.mode.name, modes)

        val lastDice = state.lastDice
        if (lastDice != null) {
            features += 1f
            features += (lastDice.value.toFloat() / 6f)
            features += (lastDice.rollCount.toFloat() / 3f)
        } else {
            features += 0f
            features += 0f
            features += 0f
        }

        features += if (state.sharedTeamDiceEnabled.contains(0)) 1f else 0f
        features += if (state.sharedTeamDiceEnabled.contains(1)) 1f else 0f

        val winners = state.winners?.map { it.value }?.toSet().orEmpty()
        playerIds.forEach { pid ->
            features += if (winners.contains(pid)) 1f else 0f
        }

        playerIds.forEach { pid ->
            val diceValue = state.diceByPlayer[PlayerId(pid)]
            features += if (diceValue != null) 1f else 0f
            features += if (diceValue != null) (diceValue.toFloat() / 6f) else 0f
        }

        playerIds.forEach { pid ->
            val entered = state.hasEnteredBoardAtLeastOnce[PlayerId(pid)] == true
            features += if (entered) 1f else 0f
        }

        val playersById = state.players.associateBy { it.id.value }
        val maxMoveCounter = max(1f, moveCounter)

        playerIds.forEach { pid ->
            val player = playersById[pid]
            features += if (player?.isActive == true) 1f else 0f

            val type = player?.type ?: PlayerType.HUMAN
            features += if (type == PlayerType.HUMAN) 1f else 0f
            features += if (type == PlayerType.BOT) 1f else 0f

            val piecesById = player?.pieces?.associateBy { it.id }.orEmpty()
            pieceIds.forEach { pieceId ->
                val piece = piecesById[pieceId]
                val position = piece?.position ?: PiecePosition.HomeBase
                addPositionEncoding(features, position)

                val lastMovedAt = piece?.lastMovedAt?.toFloat() ?: 0f
                features += min(lastMovedAt / maxMoveCounter, 1f)
            }
        }

        return features.toFloatArray().also { encoded ->
            require(encoded.size == STATE_DIM) {
                "Unexpected state feature size ${encoded.size}, expected $STATE_DIM."
            }
        }
    }

    fun encodeCandidate(option: PolicyMoveOption): FloatArray {
        val features = ArrayList<Float>(CANDIDATE_DIM)
        playerIds.forEach { pid ->
            features += if (option.movingPlayerId == pid) 1f else 0f
        }

        pieceIds.forEach { pieceId ->
            features += if (option.pieceId == pieceId) 1f else 0f
        }

        features += if (option.deferHomeEntry) 1f else 0f

        return features.toFloatArray().also { encoded ->
            require(encoded.size == CANDIDATE_DIM) {
                "Unexpected candidate feature size ${encoded.size}, expected $CANDIDATE_DIM."
            }
        }
    }

    private fun addOneHot(features: MutableList<Float>, value: String, choices: Array<String>) {
        choices.forEach { option ->
            features += if (value == option) 1f else 0f
        }
    }

    private fun addPositionEncoding(features: MutableList<Float>, position: PiecePosition) {
        val type = when (position) {
            PiecePosition.HomeBase -> "HOME_BASE"
            is PiecePosition.MainTrack -> "MAIN_TRACK"
            is PiecePosition.HomeColumn -> "HOME_COLUMN"
            PiecePosition.Finished -> "FINISHED"
        }

        addOneHot(features, type, positionTypes)
        features += if (position is PiecePosition.MainTrack) {
            position.index.toFloat() / 51f
        } else {
            0f
        }
        features += if (position is PiecePosition.HomeColumn) {
            position.step.toFloat() / 5f
        } else {
            0f
        }
    }
}

internal class JsonModelSelfPlayPolicy(
    private val model: JsonPolicyModel,
    private val fallback: SelfPlayPolicy = HeuristicSelfPlayPolicy
) : SelfPlayPolicy {

    override fun chooseDice(state: GameState, random: kotlin.random.Random): Int {
        return fallback.chooseDice(state, random)
    }

    override fun chooseMove(stateAfterRoll: GameState, random: kotlin.random.Random): SelfPlayMoveDecision {
        if (stateAfterRoll.turnPhase != TurnPhase.WAITING_FOR_PIECE_SELECTION) {
            return fallback.chooseMove(stateAfterRoll, random)
        }
        if (stateAfterRoll.movablePieces.isEmpty()) {
            return fallback.chooseMove(stateAfterRoll, random)
        }

        val options = legalPolicyMoveOptions(stateAfterRoll)
        if (options.isEmpty()) {
            return fallback.chooseMove(stateAfterRoll, random)
        }

        val stateFeatures = PolicyFeatureEncoder.encodeState(stateAfterRoll)
        val candidateFeatures = options.map { option ->
            PolicyFeatureEncoder.encodeCandidate(option)
        }

        val scores = runCatching {
            model.scoreCandidates(stateFeatures, candidateFeatures)
        }.getOrNull()

        if (scores == null || scores.size != options.size) {
            return fallback.chooseMove(stateAfterRoll, random)
        }

        var bestIndex = 0
        var bestScore = scores[0]
        for (index in 1 until scores.size) {
            if (scores[index] > bestScore) {
                bestScore = scores[index]
                bestIndex = index
            }
        }

        val selected = options[bestIndex]
        val piece = stateAfterRoll.movablePieces.firstOrNull { candidate ->
            candidate.id == selected.pieceId && candidate.color == selected.pieceColor
        } ?: return fallback.chooseMove(stateAfterRoll, random)

        return SelfPlayMoveDecision(piece = piece, deferHomeEntry = selected.deferHomeEntry)
    }

    companion object {
        fun fromJson(raw: String, fallback: SelfPlayPolicy = HeuristicSelfPlayPolicy): JsonModelSelfPlayPolicy {
            return JsonModelSelfPlayPolicy(model = JsonPolicyModel.fromJson(raw), fallback = fallback)
        }

        fun fromFile(file: File, fallback: SelfPlayPolicy = HeuristicSelfPlayPolicy): JsonModelSelfPlayPolicy {
            val raw = file.bufferedReader(Charsets.UTF_8).use { it.readText() }
            return fromJson(raw = raw, fallback = fallback)
        }
    }
}

private fun legalPolicyMoveOptions(state: GameState): List<PolicyMoveOption> {
    val diceValue = state.lastDice?.value ?: return emptyList()
    return state.movablePieces.flatMap { piece ->
        val movingPlayerId = state.players.firstOrNull { it.color == piece.color }?.id?.value
            ?: state.currentPlayer.id.value
        val canDefer = GameRules.wouldEnterHomePath(
            piece = piece,
            diceValue = diceValue,
            color = piece.color,
            players = state.players,
            mode = state.mode
        )

        val options = mutableListOf(
            PolicyMoveOption(
                movingPlayerId = movingPlayerId,
                pieceId = piece.id,
                pieceColor = piece.color,
                deferHomeEntry = false
            )
        )
        if (canDefer) {
            options += PolicyMoveOption(
                movingPlayerId = movingPlayerId,
                pieceId = piece.id,
                pieceColor = piece.color,
                deferHomeEntry = true
            )
        }
        options
    }.distinct()
}