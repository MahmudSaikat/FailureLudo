package com.failureludo.engine

/**
 * Standalone heuristic bot strategy used by gameplay automation and self-play fallbacks.
 *
 * Bot move selection is intentionally tactical + safety-aware:
 * - Prioritizes immediate wins, finishing, and captures (extra-roll value).
 * - Strongly values activating new pieces from HomeBase on a 6 early on.
 * - Prefers landing on safe squares / home column, and avoids leaving them.
 * - Penalizes moves that increase capture risk before the bot's next turn.
 * - Rewards longer-horizon board-control positioning.
 */
data class HeuristicBotMove(
    val piece: Piece,
    val deferHomeEntry: Boolean = false
)

object HeuristicBotMoveSelector {

    /**
     * Chooses the next bot move for the current state.
     *
     * Current heuristic bots do not use optional home-entry deferral.
     */
    fun chooseMove(state: GameState): HeuristicBotMove? {
        if (state.turnPhase != TurnPhase.WAITING_FOR_PIECE_SELECTION) return null
        if (state.movablePieces.isEmpty()) return null
        return HeuristicBotMove(piece = choosePiece(state), deferHomeEntry = false)
    }

    /** Chooses the piece to move for the current player. */
    fun choosePiece(state: GameState): Piece {
        val movable = state.movablePieces
        if (movable.isEmpty()) {
            return state.currentPlayer.pieces.first()
        }

        val diceValue = state.lastDice?.value ?: return movable.first()
        val mode = state.mode

        val actor = state.currentPlayer
        val actorSideKey = sideKey(actor.color, mode)
        val sidePiecesBefore = piecesForSide(state.players, actorSideKey, mode)
        val activeBefore = sidePiecesBefore.count { it.isActive }
        val safeActiveBefore = sidePiecesBefore.count { it.isActive && isSafePosition(it.position) }
        val finishedBefore = sidePiecesBefore.count { it.isFinished }
        val entryAvailable = movable.any { it.position is PiecePosition.HomeBase }

        val utilityBefore = sideUtility(sidePiecesBefore)

        var bestPiece: Piece = movable.first()
        var bestScore = Float.NEGATIVE_INFINITY

        movable.forEach { piece ->
            val destination = GameRules.computeDestination(
                piece = piece,
                diceValue = diceValue,
                color = piece.color,
                players = state.players,
                mode = mode,
                deferHomeEntry = false
            ) ?: return@forEach

            val captureTargets = GameRules.captureTargets(
                piece = piece,
                diceValue = diceValue,
                movingColor = piece.color,
                players = state.players,
                mode = mode,
                deferHomeEntry = false
            )
            val captureCount = captureTargets.size
            var captureValue = 0f
            captureTargets.forEach { target ->
                val targetPiece = state.players
                    .firstOrNull { player -> player.color == target.color }
                    ?.pieces
                    ?.firstOrNull { p -> p.id == target.pieceId }
                    ?: return@forEach
                captureValue += pieceProgress(targetPiece.position, target.color).toFloat()
            }

            val entersBoard = piece.position is PiecePosition.HomeBase && destination is PiecePosition.MainTrack
            val finishesPiece = destination is PiecePosition.Finished
            val safeLandingBonus = safeLandingBonus(destination)

            val beforeProgress = pieceProgress(piece.position, piece.color)
            val afterProgress = pieceProgress(destination, piece.color)
            val progressGain = (afterProgress - beforeProgress).coerceAtLeast(0)
            val scaledProgressGain = if (progressGain == 0) {
                0f
            } else {
                progressGain.toFloat() / kotlin.math.sqrt((beforeProgress + 1).toFloat())
            }

            val leavingSafeToDanger = isSafePosition(piece.position) && isCapturableMainTrack(destination)
            val afterState = runCatching {
                GameEngine.selectPiece(state = state, piece = piece, deferHomeEntry = false)
            }.getOrNull() ?: return@forEach

            val winsGame = afterState.isGameOver
            val grantsExtraRoll = !winsGame &&
                afterState.turnPhase == TurnPhase.WAITING_FOR_ROLL &&
                afterState.currentPlayer.id == actor.id

            val sidePiecesAfter = piecesForSide(afterState.players, actorSideKey, mode)
            val activeAfter = sidePiecesAfter.count { it.isActive }
            val safeActiveAfter = sidePiecesAfter.count { it.isActive && isSafePosition(it.position) }
            val utilityAfter = sideUtility(sidePiecesAfter)

            val safeDelta = safeActiveAfter - safeActiveBefore
            val utilityDelta = utilityAfter - utilityBefore

            val threatSummary = if (!winsGame && !grantsExtraRoll && afterState.turnPhase == TurnPhase.WAITING_FOR_ROLL) {
                evaluateThreatUntilActorTurn(
                    stateWaitingForRoll = afterState,
                    actorId = actor.id,
                    vulnerableSideKey = actorSideKey
                )
            } else SideThreatSummary.EMPTY

            val capturePressure = if (!winsGame) {
                capturePressureForPiece(
                    state = afterState,
                    pieceColor = piece.color,
                    pieceId = piece.id
                )
            } else {
                0f
            }

            val futurePressure = if (!winsGame) {
                futureCapturePotentialForPiece(
                    state = afterState,
                    pieceColor = piece.color,
                    pieceId = piece.id,
                    actorSideKey = actorSideKey
                )
            } else {
                0f
            }

            var score = 0f

            if (winsGame) score += 100_000f
            if (finishesPiece) score += 900f
            score += captureCount * 190f
            score += captureValue * 6.5f
            if (grantsExtraRoll) score += 130f

            if (entersBoard) {
                score += entryBonus(activeBefore = activeBefore, finishedBefore = finishedBefore)
                score += 40f
            } else if (entryAvailable && captureCount == 0 && !finishesPiece) {
                val entryMissPenalty = when {
                    activeBefore <= 1 -> 70f
                    activeBefore == 2 -> 35f
                    else -> 0f
                }
                score -= entryMissPenalty
            }

            score += safeLandingBonus
            if (safeDelta > 0) score += safeDelta * 28f

            if (leavingSafeToDanger) {
                val mitigation = if (captureCount > 0 || finishesPiece) 0.25f else 1f
                score -= 95f * mitigation
            }

            score += scaledProgressGain * 24f
            score += utilityDelta * 52f

            (destination as? PiecePosition.MainTrack)?.index?.let { destIndex ->
                val ownAtDest = sidePiecesAfter.count { candidate ->
                    val pos = candidate.position as? PiecePosition.MainTrack
                    pos?.index == destIndex
                }
                if (ownAtDest >= 2) {
                    if (Board.isSafeSquare(destIndex)) {
                        score += 38f
                    } else {
                        score += 18f
                        score += 52f
                        if (ownAtDest >= 3) score += 18f
                    }
                }

                if (hasEnemyDoubleAtIndex(
                        players = afterState.players,
                        index = destIndex,
                        actorSideKey = actorSideKey,
                        mode = mode
                    )
                ) {
                    score -= 65f
                }
            }

            score -= threatSummary.expectedProgressLoss * 11f
            score -= threatSummary.threatenedPieceCount * 22f

            val pressureMultiplier = if (grantsExtraRoll) 1.20f else 1.0f
            score += capturePressure * 2.4f * pressureMultiplier
            score += futurePressure * 1.35f

            if (activeAfter > activeBefore) {
                score += (activeAfter - activeBefore) * 55f
            }

            if (score > bestScore) {
                bestScore = score
                bestPiece = piece
            }
        }

        return bestPiece
    }

    private data class PieceKey(val color: PlayerColor, val pieceId: Int)

    private data class SideThreatSummary(
        val expectedProgressLoss: Float,
        val threatenedPieceCount: Int,
        val threatenedDiceCounts: Map<PieceKey, Int>
    ) {
        companion object {
            val EMPTY = SideThreatSummary(
                expectedProgressLoss = 0f,
                threatenedPieceCount = 0,
                threatenedDiceCounts = emptyMap()
            )
        }
    }

    private fun sideKey(color: PlayerColor, mode: GameMode): Int =
        if (mode == GameMode.TEAM) color.teamIndex else color.ordinal

    private fun piecesForSide(players: List<Player>, sideKey: Int, mode: GameMode): List<Piece> {
        return players
            .asSequence()
            .filter { it.isActive }
            .filter { sideKey(it.color, mode) == sideKey }
            .flatMap { it.pieces.asSequence() }
            .toList()
    }

    private fun entryBonus(activeBefore: Int, finishedBefore: Int): Float {
        val base = when (activeBefore) {
            0 -> 240f
            1 -> 180f
            2 -> 115f
            3 -> 60f
            else -> 0f
        }

        val finishFactor = when {
            finishedBefore >= 2 -> 0.45f
            finishedBefore == 1 -> 0.70f
            else -> 1.0f
        }
        return base * finishFactor
    }

    private fun sideUtility(pieces: List<Piece>): Float {
        var sum = 0.0
        for (piece in pieces) {
            val progress = pieceProgress(piece.position, piece.color)
            sum += kotlin.math.sqrt(progress.toDouble())
        }
        return sum.toFloat()
    }

    private fun safeLandingBonus(destination: PiecePosition): Float {
        return when (destination) {
            is PiecePosition.MainTrack -> if (Board.isSafeSquare(destination.index)) 75f else 0f
            is PiecePosition.HomeColumn -> 95f
            PiecePosition.Finished -> 120f
            PiecePosition.HomeBase -> 0f
        }
    }

    private fun isCapturableMainTrack(position: PiecePosition): Boolean {
        val main = position as? PiecePosition.MainTrack ?: return false
        return !Board.isSafeSquare(main.index)
    }

    private fun isSafePosition(position: PiecePosition): Boolean {
        return when (position) {
            is PiecePosition.MainTrack -> Board.isSafeSquare(position.index)
            is PiecePosition.HomeColumn -> true
            PiecePosition.Finished -> true
            PiecePosition.HomeBase -> true
        }
    }

    private fun hasEnemyDoubleAtIndex(
        players: List<Player>,
        index: Int,
        actorSideKey: Int,
        mode: GameMode
    ): Boolean {
        if (Board.isSafeSquare(index)) return false

        val countsBySide = mutableMapOf<Int, Int>()
        players
            .asSequence()
            .filter { it.isActive }
            .forEach { player ->
                val side = sideKey(player.color, mode)
                player.pieces.forEach { piece ->
                    val pos = piece.position as? PiecePosition.MainTrack ?: return@forEach
                    if (pos.index == index) {
                        countsBySide[side] = (countsBySide[side] ?: 0) + 1
                    }
                }
            }

        return countsBySide.any { (side, count) -> side != actorSideKey && count >= 2 }
    }

    private fun evaluateThreatUntilActorTurn(
        stateWaitingForRoll: GameState,
        actorId: PlayerId,
        vulnerableSideKey: Int
    ): SideThreatSummary {
        if (stateWaitingForRoll.turnPhase != TurnPhase.WAITING_FOR_ROLL) {
            return SideThreatSummary.EMPTY
        }

        val players = stateWaitingForRoll.players
        val mode = stateWaitingForRoll.mode
        val actorIndex = players.indexOfFirst { it.id == actorId }
        if (actorIndex < 0) {
            return SideThreatSummary.EMPTY
        }

        val vulnerablePieces = players
            .asSequence()
            .filter { it.isActive }
            .filter { sideKey(it.color, mode) == vulnerableSideKey }
            .flatMap { it.pieces.asSequence() }
            .filter { it.isActive }
            .filter { piece ->
                val pos = piece.position as? PiecePosition.MainTrack
                pos != null && !Board.isSafeSquare(pos.index)
            }
            .toList()

        if (vulnerablePieces.isEmpty()) {
            return SideThreatSummary.EMPTY
        }

        val survivalProb = vulnerablePieces.associate { piece ->
            PieceKey(piece.color, piece.id) to 1.0f
        }.toMutableMap()

        var index = stateWaitingForRoll.currentPlayerIndex
        var turnsAway = 0
        val maxSteps = players.count { it.isActive }.coerceAtLeast(2) + 2
        repeat(maxSteps) {
            if (index == actorIndex) return@repeat

            val rollingPlayer = players[index]
            val rollingSideKey = sideKey(rollingPlayer.color, mode)
            if (rollingPlayer.isActive && rollingSideKey != vulnerableSideKey) {
                val discount = turnRiskDiscount(turnsAway)
                val threatenedDiceCounts = threatenedDiceCountsForRollingPlayer(
                    stateWaitingForRoll = stateWaitingForRoll,
                    rollingPlayer = rollingPlayer,
                    vulnerableSideKey = vulnerableSideKey
                )

                vulnerablePieces.forEach { piece ->
                    val key = PieceKey(piece.color, piece.id)
                    val diceCount = threatenedDiceCounts[key] ?: 0
                    if (diceCount <= 0) return@forEach

                    val pCapture = ((diceCount / 6f) * discount).coerceIn(0f, 1f)
                    survivalProb[key] = (survivalProb[key] ?: 1f) * (1f - pCapture)
                }
            }

            index = GameRules.nextPlayerIndex(index, players)
            turnsAway += 1
        }

        var expectedLoss = 0f
        var threatenedCount = 0
        vulnerablePieces.forEach { piece ->
            val key = PieceKey(piece.color, piece.id)
            val survive = survivalProb[key] ?: 1f
            val pCaptured = (1f - survive).coerceIn(0f, 1f)
            if (pCaptured > 0f) {
                threatenedCount += 1
                val progress = pieceProgress(piece.position, piece.color)
                expectedLoss += progress.toFloat() * pCaptured
            }
        }

        return SideThreatSummary(
            expectedProgressLoss = expectedLoss,
            threatenedPieceCount = threatenedCount,
            threatenedDiceCounts = emptyMap()
        )
    }

    private fun threatenedDiceCountsForRollingPlayer(
        stateWaitingForRoll: GameState,
        rollingPlayer: Player,
        vulnerableSideKey: Int
    ): Map<PieceKey, Int> {
        val players = stateWaitingForRoll.players
        val mode = stateWaitingForRoll.mode
        val sharedTeamDiceEnabled = stateWaitingForRoll.sharedTeamDiceEnabled

        val threatenedDiceCounts = mutableMapOf<PieceKey, Int>()

        for (diceValue in 1..6) {
            val movable = GameRules.movablePiecesForTurn(
                currentPlayer = rollingPlayer,
                diceValue = diceValue,
                allPlayers = players,
                mode = mode,
                sharedTeamDiceEnabled = sharedTeamDiceEnabled
            )

            val threatenedThisDice = mutableSetOf<PieceKey>()
            movable.forEach { attacker ->
                val targets = GameRules.captureTargets(
                    piece = attacker,
                    diceValue = diceValue,
                    movingColor = attacker.color,
                    players = players,
                    mode = mode,
                    deferHomeEntry = false
                )

                targets.forEach { target ->
                    if (sideKey(target.color, mode) == vulnerableSideKey) {
                        threatenedThisDice += PieceKey(color = target.color, pieceId = target.pieceId)
                    }
                }
            }

            threatenedThisDice.forEach { key ->
                threatenedDiceCounts[key] = (threatenedDiceCounts[key] ?: 0) + 1
            }
        }

        return threatenedDiceCounts
    }

    private fun turnRiskDiscount(turnsAway: Int): Float {
        return when (turnsAway) {
            0 -> 1.0f
            1 -> 0.86f
            2 -> 0.74f
            else -> 0.62f
        }
    }

    private fun futureCapturePotentialForPiece(
        state: GameState,
        pieceColor: PlayerColor,
        pieceId: Int,
        actorSideKey: Int
    ): Float {
        val piece = state.players
            .firstOrNull { it.color == pieceColor }
            ?.pieces
            ?.firstOrNull { it.id == pieceId }
            ?: return 0f

        val mainPos = piece.position as? PiecePosition.MainTrack ?: return 0f
        val mode = state.mode

        val ourIndex = mainPos.index
        val safeMultiplier = if (Board.isSafeSquare(ourIndex)) 1.25f else 1.0f

        var sum = 0f
        state.players
            .asSequence()
            .filter { it.isActive }
            .filter { sideKey(it.color, mode) != actorSideKey }
            .flatMap { it.pieces.asSequence().map { p -> it.color to p } }
            .forEach { (enemyColor, enemyPiece) ->
                val enemyPos = enemyPiece.position as? PiecePosition.MainTrack ?: return@forEach
                if (Board.isSafeSquare(enemyPos.index)) return@forEach

                val dist = forwardDistance(ourIndex, enemyPos.index)
                if (dist == 0) return@forEach
                val weight = when {
                    dist <= 6 -> 1.0f
                    dist <= 12 -> 0.45f
                    dist <= 18 -> 0.22f
                    else -> 0f
                }
                if (weight <= 0f) return@forEach

                val progress = pieceProgress(enemyPiece.position, enemyColor).toFloat()
                val value = 14f + (progress * 0.85f)
                sum += value * weight
            }

        return sum * safeMultiplier
    }

    private fun forwardDistance(fromIndex: Int, toIndex: Int): Int {
        return (toIndex - fromIndex + Board.MAIN_TRACK_SIZE) % Board.MAIN_TRACK_SIZE
    }

    private fun capturePressureForPiece(
        state: GameState,
        pieceColor: PlayerColor,
        pieceId: Int
    ): Float {
        val piece = state.players
            .firstOrNull { it.color == pieceColor }
            ?.pieces
            ?.firstOrNull { it.id == pieceId }
            ?: return 0f

        val mainPos = piece.position as? PiecePosition.MainTrack ?: return 0f
        val mode = state.mode

        var sum = 0f
        for (diceValue in 1..6) {
            val targets = GameRules.captureTargets(
                piece = piece,
                diceValue = diceValue,
                movingColor = piece.color,
                players = state.players,
                mode = mode,
                deferHomeEntry = false
            )
            if (targets.isEmpty()) continue

            var bestTargetProgress = 0
            targets.forEach { target ->
                val targetPiece = state.players
                    .firstOrNull { it.color == target.color }
                    ?.pieces
                    ?.firstOrNull { it.id == target.pieceId }
                    ?: return@forEach
                val progress = pieceProgress(targetPiece.position, target.color)
                if (progress > bestTargetProgress) bestTargetProgress = progress
            }

            val value = 28f + (bestTargetProgress.toFloat() * 1.1f) + (targets.size * 10f)
            sum += value
        }

        val average = sum / 6f
        val safeMultiplier = if (Board.isSafeSquare(mainPos.index)) 1.35f else 1.0f
        return average * safeMultiplier
    }

    private fun pieceProgress(position: PiecePosition, color: PlayerColor): Int = when (position) {
        is PiecePosition.HomeBase -> 0
        is PiecePosition.MainTrack -> Board.relativePosition(color, position.index) + 1
        is PiecePosition.HomeColumn -> Board.MAIN_TRACK_SIZE + position.step
        PiecePosition.Finished -> Board.MAIN_TRACK_SIZE + Board.HOME_COLUMN_STEPS + 1
    }
}
