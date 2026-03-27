package com.failureludo.engine

/**
 * Pure stateless functions that encode every Ludo rule.
 *
 * All functions take immutable inputs and return new state — no mutation.
 */
object GameRules {

    data class CaptureTarget(val color: PlayerColor, val pieceId: Int)
    private data class PieceRef(val color: PlayerColor, val pieceId: Int)
    private data class Occupant(val color: PlayerColor, val piece: Piece)

    /**
     * Returns the list of pieces that can legally move given [diceValue] for [player].
     * A piece with no legal destination is excluded.
     */
    fun movablePieces(
        player: Player,
        diceValue: Int,
        allPlayers: List<Player>,
        mode: GameMode = GameMode.FREE_FOR_ALL
    ): List<Piece> =
        player.pieces.filter { piece ->
            canMove(piece, diceValue, player, allPlayers, mode)
        }

    /** True if [piece] has at least one legal move for [diceValue]. */
    fun canMove(
        piece: Piece,
        diceValue: Int,
        player: Player,
        allPlayers: List<Player>,
        mode: GameMode = GameMode.FREE_FOR_ALL
    ): Boolean {
        if (piece.isFinished) return false

        val destination = computeDestination(piece, diceValue, player.color, allPlayers, mode) ?: return false

        if (piece.position is PiecePosition.MainTrack && !isMovingAsLockedDouble(piece, diceValue, player.color, allPlayers, mode)) {
            val effectiveDice = effectiveDiceValue(piece, diceValue, player.color, allPlayers, mode) ?: return false
            val blocked = crossesOpponentDoubleBarrier(
                currentIndex = piece.position.index,
                effectiveDice = effectiveDice,
                movingColor = player.color,
                destination = destination,
                players = allPlayers,
                mode = mode
            )
            if (blocked) return false
        }

        return true
    }

    /**
     * Applies a move: moves [piece] by [diceValue] for [color] within [players].
     * Handles: entering board, moving on track, entering home column, finishing,
     * and capturing opponents.
     *
     * @return New list of players with updated piece positions.
     */
    fun applyMove(
        piece: Piece,
        diceValue: Int,
        color: PlayerColor,
        players: List<Player>,
        mode: GameMode = GameMode.FREE_FOR_ALL,
        deferHomeEntry: Boolean = false,
        movedAt: Long = 0L
    ): List<Player> {
        val newPosition = computeDestination(piece, diceValue, color, players, mode, deferHomeEntry)
            ?: return players

        val movingRefs = movingPieceRefs(piece, diceValue, color, players, mode)

        val playersAfterMove = players.map { player ->
            player.copy(
                pieces = player.pieces.map { p ->
                    val ref = PieceRef(player.color, p.id)
                    if (ref in movingRefs) {
                        p.copy(position = newPosition, lastMovedAt = movedAt)
                    } else p
                }
            )
        }

        val captures = captureTargets(piece, diceValue, color, players, mode, deferHomeEntry)
        if (captures.isEmpty()) return playersAfterMove

        val captureMap = captures.groupBy { it.color }.mapValues { (_, value) -> value.map { it.pieceId }.toSet() }
        return playersAfterMove.map { player ->
            val capturedIds = captureMap[player.color] ?: return@map player
            player.copy(
                pieces = player.pieces.map { p ->
                    if (p.id in capturedIds) p.copy(position = PiecePosition.HomeBase) else p
                }
            )
        }
    }

    /**
     * Computes the destination [PiecePosition] for [piece] moved by [diceValue].
     * Returns null if the move is impossible (e.g. overshoots home column).
     */
    fun computeDestination(
        piece: Piece,
        diceValue: Int,
        color: PlayerColor,
        players: List<Player>,
        mode: GameMode = GameMode.FREE_FOR_ALL,
        deferHomeEntry: Boolean = false
    ): PiecePosition? {
        val effectiveDice = effectiveDiceValue(piece, diceValue, color, players, mode) ?: return null

        return when (piece.position) {
            is PiecePosition.HomeBase -> {
                if (effectiveDice == 6) PiecePosition.MainTrack(Board.ENTRY_POSITIONS.getValue(color))
                else null
            }
            is PiecePosition.MainTrack -> {
                val lockedRefs = lockedPairRefsForPiece(piece, color, players, mode)
                val mixedTeamPairLocked = lockedRefs.map { it.color }.toSet().size > 1
                val mustStayOnMainTrack = mixedTeamPairLocked
                computeMainTrackDestination(
                    currentIdx = piece.position.index,
                    effectiveDice = effectiveDice,
                    color = color,
                    deferHomeEntry = deferHomeEntry || mustStayOnMainTrack
                )
            }
            is PiecePosition.HomeColumn -> {
                val newStep = piece.position.step + effectiveDice
                when {
                    newStep < Board.HOME_COLUMN_STEPS + 1 -> PiecePosition.HomeColumn(newStep)
                    newStep == Board.HOME_COLUMN_STEPS + 1 -> PiecePosition.Finished
                    else -> null
                }
            }
            PiecePosition.Finished -> null
        }
    }

    private fun computeMainTrackDestination(
        currentIdx: Int,
        effectiveDice: Int,
        color: PlayerColor,
        deferHomeEntry: Boolean
    ): PiecePosition? {
        val stepsToEntry = Board.stepsToHomeColumnEntry(color, currentIdx)

        return if (deferHomeEntry) {
            val newIdx = (currentIdx + effectiveDice) % Board.MAIN_TRACK_SIZE
            PiecePosition.MainTrack(newIdx)
        } else {
            when {
                effectiveDice <= stepsToEntry -> {
                    val newIdx = (currentIdx + effectiveDice) % Board.MAIN_TRACK_SIZE
                    PiecePosition.MainTrack(newIdx)
                }
                effectiveDice == stepsToEntry + 1 -> PiecePosition.HomeColumn(1)
                effectiveDice > stepsToEntry + 1 -> {
                    val homeStep = effectiveDice - stepsToEntry
                    when {
                        homeStep < Board.HOME_COLUMN_STEPS + 1 -> PiecePosition.HomeColumn(homeStep)
                        homeStep == Board.HOME_COLUMN_STEPS + 1 -> PiecePosition.Finished
                        else -> null
                    }
                }
                else -> null
            }
        }
    }

    fun captureTargets(
        piece: Piece,
        diceValue: Int,
        movingColor: PlayerColor,
        players: List<Player>,
        mode: GameMode = GameMode.FREE_FOR_ALL,
        deferHomeEntry: Boolean = false
    ): List<CaptureTarget> {
        val destination = computeDestination(piece, diceValue, movingColor, players, mode, deferHomeEntry)
            ?: return emptyList()

        val mainPos = destination as? PiecePosition.MainTrack ?: return emptyList()
        if (Board.isSafeSquare(mainPos.index)) return emptyList()

        val movingAsDouble = isMovingAsLockedDouble(piece, diceValue, movingColor, players, mode)
        val enemyStacks = enemyStacksAtIndex(mainPos.index, movingColor, players, mode)

        return if (movingAsDouble) {
            enemyStacks
                .flatMap { stack ->
                    doubleComponentRefsForStack(stack).map { ref -> CaptureTarget(ref.color, ref.pieceId) }
                }
        } else {
            val pairCaptureOnEnemyDouble = pairCaptureTargetsOnEnemyDouble(
                piece = piece,
                movingColor = movingColor,
                destinationIndex = mainPos.index,
                players = players,
                mode = mode
            )
            if (pairCaptureOnEnemyDouble.isNotEmpty()) {
                return pairCaptureOnEnemyDouble
            }

            enemyStacks
                .flatMap { stack ->
                    val doubleRefs = doubleComponentRefsForStack(stack)
                    val protectedTopSingle = if (stack.size >= 3) topSingleRefForStack(stack) else null
                    stack
                        .map { occupant -> PieceRef(occupant.color, occupant.piece.id) }
                        .filter { it !in doubleRefs }
                        .filter { ref -> ref != protectedTopSingle }
                        .map { ref -> CaptureTarget(ref.color, ref.pieceId) }
                }
        }
    }

    private fun pairCaptureTargetsOnEnemyDouble(
        piece: Piece,
        movingColor: PlayerColor,
        destinationIndex: Int,
        players: List<Player>,
        mode: GameMode
    ): List<CaptureTarget> {
        val ownSinglesAtCell = occupantsAtMainIndex(players, destinationIndex)
            .filter { occupant ->
                occupant.color == movingColor &&
                    occupant.piece.id != piece.id
            }

        if (ownSinglesAtCell.isEmpty()) return emptyList()

        val enemyDoubleTargets = enemyStacksAtIndex(destinationIndex, movingColor, players, mode)
            .flatMap { stack ->
                if (stack.size < 2) emptyList()
                else doubleComponentRefsForStack(stack).toList()
            }

        return enemyDoubleTargets.map { ref -> CaptureTarget(ref.color, ref.pieceId) }
    }

    /**
     * True when moving [piece] with [diceValue] would send it from main track
     * into the home path (home column or direct finish).
     */
    fun wouldEnterHomePath(
        piece: Piece,
        diceValue: Int,
        color: PlayerColor,
        players: List<Player>,
        mode: GameMode = GameMode.FREE_FOR_ALL
    ): Boolean {
        if (piece.position !is PiecePosition.MainTrack) return false
        return when (computeDestination(piece, diceValue, color, players, mode)) {
            is PiecePosition.HomeColumn, PiecePosition.Finished -> true
            else -> false
        }
    }

    /**
     * Whether the player gets an extra dice roll.
     * Extra roll granted on: rolling a 6, OR sending a piece home (capture).
     */
    fun grantsExtraRoll(
        diceValue: Int,
        pieceBeforeMove: Piece,
        piecesAfterMove: List<Piece>,
        capturedAny: Boolean
    ): Boolean = diceValue == 6 || capturedAny

    fun countCaptures(
        piece: Piece,
        diceValue: Int,
        movingColor: PlayerColor,
        players: List<Player>,
        mode: GameMode = GameMode.FREE_FOR_ALL,
        deferHomeEntry: Boolean = false
    ): Int = captureTargets(piece, diceValue, movingColor, players, mode, deferHomeEntry).size

    private fun movingPieceRefs(
        piece: Piece,
        diceValue: Int,
        color: PlayerColor,
        players: List<Player>,
        mode: GameMode
    ): Set<PieceRef> {
        val locked = lockedPairRefsForPiece(piece, color, players, mode)
        val selfRef = PieceRef(color, piece.id)
        val movingAsDouble = selfRef in locked && diceValue % 2 == 0
        return if (movingAsDouble) locked else setOf(selfRef)
    }

    private fun effectiveDiceValue(
        piece: Piece,
        diceValue: Int,
        color: PlayerColor,
        players: List<Player>,
        mode: GameMode
    ): Int? {
        val locked = lockedPairRefsForPiece(piece, color, players, mode)
        val selfRef = PieceRef(color, piece.id)
        if (selfRef !in locked) return diceValue
        if (diceValue % 2 != 0) return null
        return diceValue / 2
    }

    private fun isMovingAsLockedDouble(
        piece: Piece,
        diceValue: Int,
        color: PlayerColor,
        players: List<Player>,
        mode: GameMode
    ): Boolean {
        val locked = lockedPairRefsForPiece(piece, color, players, mode)
        val selfRef = PieceRef(color, piece.id)
        return selfRef in locked && diceValue % 2 == 0
    }

    private fun lockedPairRefsForPiece(
        piece: Piece,
        color: PlayerColor,
        players: List<Player>,
        mode: GameMode
    ): Set<PieceRef> {
        val index = (piece.position as? PiecePosition.MainTrack)?.index ?: return emptySet()

        val stack = ownStackAtIndex(index, color, players, mode)
        if (stack.size < 2) return emptySet()

        val doubleRefs = doubleComponentRefsForStack(stack)
        val selfRef = PieceRef(color, piece.id)
        if (selfRef !in doubleRefs) return emptySet()

        if (Board.isSafeSquare(index)) return emptySet()

        val isMixedTeamPair = doubleRefs.map { it.color }.toSet().size > 1
        if (!isMixedTeamPair && index == Board.HOME_COLUMN_ENTRY.getValue(color)) {
            return emptySet()
        }

        return doubleRefs
    }

    private fun ownStackAtIndex(
        index: Int,
        movingColor: PlayerColor,
        players: List<Player>,
        mode: GameMode
    ): List<Occupant> {
        return occupantsAtMainIndex(players, index)
            .filter { occupant -> isSameSide(movingColor, occupant.color, mode) }
    }

    private fun enemyStacksAtIndex(
        index: Int,
        movingColor: PlayerColor,
        players: List<Player>,
        mode: GameMode
    ): List<List<Occupant>> {
        return groupedStacksAtIndex(index, players, mode)
            .filterKeys { key -> key != sideKey(movingColor, mode) }
            .values
            .toList()
    }

    private fun groupedStacksAtIndex(
        index: Int,
        players: List<Player>,
        mode: GameMode
    ): Map<Int, List<Occupant>> {
        return occupantsAtMainIndex(players, index)
            .groupBy { occupant -> sideKey(occupant.color, mode) }
    }

    private fun occupantsAtMainIndex(players: List<Player>, index: Int): List<Occupant> {
        return players
            .asSequence()
            .filter { it.isActive }
            .flatMap { player ->
                player.pieces.asSequence().mapNotNull { piece ->
                    val pos = piece.position as? PiecePosition.MainTrack
                    if (pos?.index == index) Occupant(player.color, piece) else null
                }
            }
            .toList()
    }

    private fun doubleComponentRefsForStack(stack: List<Occupant>): Set<PieceRef> {
        if (stack.size < 2) return emptySet()
        if (stack.size == 2) return stack.map { PieceRef(it.color, it.piece.id) }.toSet()

        val topSingle = topSingleRefForStack(stack)
        val candidates = stack
            .map { PieceRef(it.color, it.piece.id) to it.piece }
            .filter { (ref, _) -> ref != topSingle }
            .sortedWith(compareBy<Pair<PieceRef, Piece>>({ it.second.lastMovedAt }, { it.first.color.ordinal }, { it.first.pieceId }))

        return candidates.take(2).map { it.first }.toSet()
    }

    private fun topSingleRefForStack(stack: List<Occupant>): PieceRef? {
        if (stack.size < 3) return null
        val top = stack.maxWithOrNull(
            compareBy<Occupant>({ it.piece.lastMovedAt }, { it.color.ordinal }, { it.piece.id })
        ) ?: return null
        return PieceRef(top.color, top.piece.id)
    }

    private fun crossesOpponentDoubleBarrier(
        currentIndex: Int,
        effectiveDice: Int,
        movingColor: PlayerColor,
        destination: PiecePosition,
        players: List<Player>,
        mode: GameMode
    ): Boolean {
        val mainTrackSteps = when (destination) {
            is PiecePosition.MainTrack -> effectiveDice
            is PiecePosition.HomeColumn, PiecePosition.Finished ->
                Board.stepsToHomeColumnEntry(movingColor, currentIndex)
            else -> 0
        }

        if (mainTrackSteps <= 0) return false

        for (step in 1..mainTrackSteps) {
            val index = (currentIndex + step) % Board.MAIN_TRACK_SIZE
            if (!hasOpponentDoubleAt(index, movingColor, players, mode)) continue

            val landsOnBarrierCell = destination is PiecePosition.MainTrack && step == mainTrackSteps
            if (!landsOnBarrierCell) return true
        }
        return false
    }

    private fun hasOpponentDoubleAt(
        index: Int,
        movingColor: PlayerColor,
        players: List<Player>,
        mode: GameMode
    ): Boolean {
        return enemyStacksAtIndex(index, movingColor, players, mode)
            .any { stack -> stack.size >= 2 }
    }

    private fun sideKey(color: PlayerColor, mode: GameMode): Int =
        if (mode == GameMode.TEAM) color.teamIndex else color.ordinal

    private fun isSameSide(a: PlayerColor, b: PlayerColor, mode: GameMode): Boolean =
        sideKey(a, mode) == sideKey(b, mode)

    /**
     * Determines the winner(s) in FREE_FOR_ALL or TEAM mode.
     * Returns null if the game is not yet over.
     * Returns a list of winning [PlayerId]s (2 in team mode).
     */
    fun checkWinner(players: List<Player>, mode: GameMode): List<PlayerId>? {
        val activePlayers = players.filter { it.isActive }
        return when (mode) {
            GameMode.FREE_FOR_ALL -> {
                val winner = activePlayers.firstOrNull { it.hasFinished }
                winner?.let { listOf(it.id) }
            }
            GameMode.TEAM -> {
                for (teamIndex in 0..1) {
                    val teamPlayers = activePlayers.filter { it.color.teamIndex == teamIndex }
                    if (teamPlayers.isNotEmpty() && teamPlayers.all { it.hasFinished }) {
                        return teamPlayers.map { it.id }
                    }
                }
                null
            }
        }
    }

    /**
     * Returns the next active player index after [currentIndex]
     * (skips inactive seats).
     */
    fun nextPlayerIndex(currentIndex: Int, players: List<Player>): Int {
        val size = players.size
        var next = (currentIndex - 1 + size) % size
        repeat(size) {
            if (players[next].isActive) return next
            next = (next - 1 + size) % size
        }
        return currentIndex
    }
}
