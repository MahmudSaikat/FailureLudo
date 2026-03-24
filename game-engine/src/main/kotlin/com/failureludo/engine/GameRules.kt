package com.failureludo.engine

/**
 * Pure stateless functions that encode every Ludo rule.
 *
 * All functions take immutable inputs and return new state — no mutation.
 */
object GameRules {

    /**
     * Returns the list of pieces that can legally move given [diceValue] for [player].
     * A piece with no legal destination is excluded.
     */
    fun movablePieces(player: Player, diceValue: Int, allPlayers: List<Player>): List<Piece> =
        player.pieces.filter { piece ->
            canMove(piece, diceValue, player, allPlayers)
        }

    /** True if [piece] has at least one legal move for [diceValue]. */
    fun canMove(piece: Piece, diceValue: Int, player: Player, allPlayers: List<Player>): Boolean {
        if (piece.isFinished) return false
        return when (piece.position) {
            is PiecePosition.HomeBase -> diceValue == 6
            is PiecePosition.MainTrack -> {
                val destination = computeDestination(piece, diceValue, player.color)
                destination != null
            }
            is PiecePosition.HomeColumn -> {
                val step = piece.position.step
                step + diceValue <= Board.HOME_COLUMN_STEPS + 1 // +1 so step 5 + die 1 = Finished
            }
            PiecePosition.Finished -> false
        }
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
        players: List<Player>
    ): List<Player> {
        val newPosition = computeDestination(piece, diceValue, color)
            ?: return players  // no valid move; shouldn't be called in this case

        // Build updated piece
        var movedPiece = piece.copy(position = newPosition)

        // Check for capture: only on main track, only non-safe squares
        var updatedPlayers = players
        if (newPosition is PiecePosition.MainTrack && !Board.isSafeSquare(newPosition.index)) {
            updatedPlayers = captureOpponents(movedPiece, color, players)
        }

        // Update the moved piece inside its player
        return updatedPlayers.map { player ->
            if (player.color == color) {
                player.copy(
                    pieces = player.pieces.map { p ->
                        if (p.id == piece.id) movedPiece else p
                    }
                )
            } else player
        }
    }

    /**
     * Computes the destination [PiecePosition] for [piece] moved by [diceValue].
     * Returns null if the move is impossible (e.g. overshoots home column).
     */
    fun computeDestination(
        piece: Piece,
        diceValue: Int,
        color: PlayerColor
    ): PiecePosition? = when (piece.position) {

        is PiecePosition.HomeBase -> {
            if (diceValue == 6) PiecePosition.MainTrack(Board.ENTRY_POSITIONS.getValue(color))
            else null
        }

        is PiecePosition.MainTrack -> {
            val currentIdx = piece.position.index
            val stepsToEntry = Board.stepsToHomeColumnEntry(color, currentIdx)

            when {
                diceValue <= stepsToEntry -> {
                    // Still on main track
                    val newIdx = (currentIdx + diceValue) % Board.MAIN_TRACK_SIZE
                    PiecePosition.MainTrack(newIdx)
                }
                diceValue == stepsToEntry + 1 -> {
                    // Exactly enters home column at step 1
                    PiecePosition.HomeColumn(1)
                }
                diceValue > stepsToEntry + 1 -> {
                    // Enters home column at some step
                    val homeStep = diceValue - stepsToEntry
                    when {
                        homeStep < Board.HOME_COLUMN_STEPS + 1 ->
                            PiecePosition.HomeColumn(homeStep)
                        homeStep == Board.HOME_COLUMN_STEPS + 1 ->
                            PiecePosition.Finished
                        else -> null  // overshoots
                    }
                }
                else -> null
            }
        }

        is PiecePosition.HomeColumn -> {
            val newStep = piece.position.step + diceValue
            when {
                newStep < Board.HOME_COLUMN_STEPS + 1 -> PiecePosition.HomeColumn(newStep)
                newStep == Board.HOME_COLUMN_STEPS + 1 -> PiecePosition.Finished
                else -> null  // overshoots home column
            }
        }

        PiecePosition.Finished -> null
    }

    /**
     * If [movedPiece] lands on any opponent pieces (that are not on a safe square
     * and not finished), those pieces are sent back to HomeBase.
     */
    private fun captureOpponents(
        movedPiece: Piece,
        ownerColor: PlayerColor,
        players: List<Player>
    ): List<Player> {
        val landingPos = movedPiece.position as? PiecePosition.MainTrack ?: return players

        return players.map { player ->
            if (player.color == ownerColor) return@map player  // can't capture own pieces
            player.copy(
                pieces = player.pieces.map { target ->
                    if (target.position == landingPos && !target.isFinished) {
                        // Captured — send home
                        target.copy(position = PiecePosition.HomeBase)
                    } else target
                }
            )
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

    /**
     * Counts how many opponent pieces were captured by moving [movingColor]'s piece
     * to [newPosition] (before the move is applied to players list).
     */
    fun countCaptures(
        movingColor: PlayerColor,
        newPosition: PiecePosition,
        players: List<Player>
    ): Int {
        val mainPos = newPosition as? PiecePosition.MainTrack ?: return 0
        if (Board.isSafeSquare(mainPos.index)) return 0

        return players
            .filter { it.color != movingColor && it.isActive }
            .sumOf { player ->
                player.pieces.count { it.position == mainPos && !it.isFinished }
            }
    }

    /**
     * Determines the winner(s) in FREE_FOR_ALL or TEAM mode.
     * Returns null if the game is not yet over.
     * Returns a list of winning [PlayerColor]s (2 in team mode).
     */
    fun checkWinner(players: List<Player>, mode: GameMode): List<PlayerColor>? {
        val activePlayers = players.filter { it.isActive }
        return when (mode) {
            GameMode.FREE_FOR_ALL -> {
                val winner = activePlayers.firstOrNull { it.hasFinished }
                winner?.let { listOf(it.color) }
            }
            GameMode.TEAM -> {
                // Team 0: RED + YELLOW, Team 1: BLUE + GREEN
                for (teamIndex in 0..1) {
                    val teamPlayers = activePlayers.filter { it.color.teamIndex == teamIndex }
                    if (teamPlayers.isNotEmpty() && teamPlayers.all { it.hasFinished }) {
                        return teamPlayers.map { it.color }
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
        var next = (currentIndex + 1) % size
        repeat(size) {
            if (players[next].isActive) return next
            next = (next + 1) % size
        }
        return currentIndex // fallback (should never happen with valid player list)
    }
}
