package com.failureludo.ai

import com.failureludo.engine.GameRules
import com.failureludo.engine.GameState
import com.failureludo.engine.HeuristicBotMoveSelector
import com.failureludo.engine.Piece
import com.failureludo.engine.PlayerColor
import com.failureludo.engine.TurnPhase

data class BotMoveDecision(
    val pieceId: Int,
    val pieceColor: PlayerColor,
    val deferHomeEntry: Boolean = false
)

interface BotPolicyEngine {
    fun chooseMove(state: GameState): BotMoveDecision?
}

fun interface OfflineModelMoveScorer {
    fun scoreCandidates(state: GameState, candidates: List<BotMoveDecision>): FloatArray?
}

object NoModelMoveScorer : OfflineModelMoveScorer {
    override fun scoreCandidates(state: GameState, candidates: List<BotMoveDecision>): FloatArray? = null
}

object HeuristicBotPolicyEngine : BotPolicyEngine {
    override fun chooseMove(state: GameState): BotMoveDecision? {
        val move = HeuristicBotMoveSelector.chooseMove(state) ?: return null
        return BotMoveDecision(
            pieceId = move.piece.id,
            pieceColor = move.piece.color,
            deferHomeEntry = move.deferHomeEntry
        )
    }
}

class ModelFirstBotPolicyEngine(
    private val scorer: OfflineModelMoveScorer,
    private val fallback: BotPolicyEngine = HeuristicBotPolicyEngine
) : BotPolicyEngine {

    override fun chooseMove(state: GameState): BotMoveDecision? {
        if (state.turnPhase != TurnPhase.WAITING_FOR_PIECE_SELECTION) return null
        if (state.movablePieces.isEmpty()) return null

        val candidates = legalBotMoveCandidates(state)
        if (candidates.isEmpty()) {
            return fallback.chooseMove(state)
        }

        val scores = scorer.scoreCandidates(state, candidates)
        if (scores == null || scores.size != candidates.size) {
            return fallback.chooseMove(state)
        }

        var bestIndex = 0
        var bestScore = scores[0]
        for (index in 1 until scores.size) {
            if (scores[index] > bestScore) {
                bestScore = scores[index]
                bestIndex = index
            }
        }

        return candidates[bestIndex]
    }
}

internal fun legalBotMoveCandidates(state: GameState): List<BotMoveDecision> {
    val diceValue = state.lastDice?.value ?: return emptyList()

    return state.movablePieces.flatMap { piece ->
        val canDefer = GameRules.wouldEnterHomePath(
            piece = piece,
            diceValue = diceValue,
            color = piece.color,
            players = state.players,
            mode = state.mode
        )

        val options = mutableListOf(
            BotMoveDecision(
                pieceId = piece.id,
                pieceColor = piece.color,
                deferHomeEntry = false
            )
        )
        if (canDefer) {
            options += BotMoveDecision(
                pieceId = piece.id,
                pieceColor = piece.color,
                deferHomeEntry = true
            )
        }
        options
    }.distinct()
}

internal fun resolveDecisionPieceOrNull(state: GameState, decision: BotMoveDecision): Piece? {
    return state.movablePieces.firstOrNull { piece ->
        piece.id == decision.pieceId && piece.color == decision.pieceColor
    }
}