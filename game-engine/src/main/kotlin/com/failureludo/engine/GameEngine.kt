package com.failureludo.engine

/**
 * The central coordinator that drives game flow.
 *
 * All public functions are pure (return a new [GameState]) so they can be called
 * from any thread, stored, and later replayed or transmitted over a network.
 *
 * Typical call sequence:
 *   1. [rollDice]  — on each turn, generates a dice value and evaluates movable pieces
 *   2. [selectPiece] — player chooses which piece to move
 *   3. Repeat until [GameState.isGameOver]
 */
object GameEngine {

    private val dice = DiceRoller()

    // ── Factory ──────────────────────────────────────────────────────────────

    /**
     * Creates a fresh [GameState] for a new game.
     *
     * @param activeColors   Which of the 4 colours are active (2-4 items).
     *                       Order determines turn order.
     * @param playerTypes    Map of colour → human or bot.
     * @param playerNames    Custom display names (optional).
     * @param mode           FFA or Team.
     */
    fun newGame(
        activeColors: List<PlayerColor>,
        playerTypes: Map<PlayerColor, PlayerType> = emptyMap(),
        playerNames: Map<PlayerColor, String> = emptyMap(),
        mode: GameMode = GameMode.FREE_FOR_ALL
    ): GameState {
        require(activeColors.size in 2..4) { "Need between 2 and 4 active colours." }
        if (mode == GameMode.TEAM) {
            require(activeColors.size == 4) { "Team mode requires exactly 4 players." }
        }

        // All 4 colours are always created; inactive ones are just marked inactive
        val players = PlayerColor.entries.map { color ->
            Player(
                color = color,
                name = playerNames[color] ?: color.displayName,
                type = playerTypes[color] ?: PlayerType.HUMAN,
                isActive = color in activeColors
            )
        }

        // First turn: first ACTIVE player in the activeColors list
        val startIndex = players.indexOfFirst { it.color == activeColors.first() }

        return GameState(
            players = players,
            mode = mode,
            moveCounter = 0L,
            currentPlayerIndex = startIndex,
            turnPhase = TurnPhase.WAITING_FOR_ROLL,
            diceByPlayer = PlayerColor.entries.associateWith { null }
        )
    }

    // ── Turn Actions ─────────────────────────────────────────────────────────

    /**
     * Rolls the dice for the current player.
     * Updates [TurnPhase] to WAITING_FOR_PIECE_SELECTION or NO_MOVES_AVAILABLE.
     */
    fun rollDice(state: GameState): GameState {
        require(state.turnPhase == TurnPhase.WAITING_FOR_ROLL) {
            "rollDice called in wrong phase: ${state.turnPhase}"
        }

        val diceValue = dice.roll()
        val rollCount = (state.lastDice?.rollCount?.takeIf { diceValue == 6 && it < 3 } ?: 0) + 1

        // Three consecutive 6s → forfeit turn
        if (diceValue == 6 && rollCount == 3) {
            val event = GameEvent.ConsecutiveSixesForfeit(state.currentPlayer.color)
            val updatedDiceByPlayer = state.diceByPlayer.toMutableMap().apply {
                this[state.currentPlayer.color] = diceValue
            }
            return advanceToNextTurn(
                state.copy(
                    lastDice = DiceResult(diceValue, rollCount),
                    diceByPlayer = updatedDiceByPlayer,
                    eventLog = state.eventLog + event
                )
            )
        }

        val diceResult = DiceResult(diceValue, rollCount)
        val movable = GameRules.movablePieces(state.currentPlayer, diceValue, state.players, state.mode)

        val newPhase = if (movable.isEmpty()) TurnPhase.NO_MOVES_AVAILABLE
                       else TurnPhase.WAITING_FOR_PIECE_SELECTION

        val updatedDiceByPlayer = state.diceByPlayer.toMutableMap().apply {
            this[state.currentPlayer.color] = diceValue
        }

        return state.copy(
            lastDice = diceResult,
            diceByPlayer = updatedDiceByPlayer,
            movablePieces = movable,
            turnPhase = newPhase
        )
    }

    /**
     * Auto-advances when there are no moves (caller should invoke after a short UI delay).
     */
    fun advanceNoMoves(state: GameState): GameState {
        require(state.turnPhase == TurnPhase.NO_MOVES_AVAILABLE)
        val event = GameEvent.TurnSkipped(state.currentPlayer.color)
        return advanceToNextTurn(state.copy(eventLog = state.eventLog + event))
    }

    /**
     * Moves the selected [piece] using the current dice value.
     *
     * Returns the updated [GameState], which may include:
     * - A capture event
     * - An extra-roll turn (dice == 6 or capture occurred)
     * - A win condition
     * - Normal turn advancement
     */
    fun selectPiece(state: GameState, piece: Piece, deferHomeEntry: Boolean = false): GameState {
        require(state.turnPhase == TurnPhase.WAITING_FOR_PIECE_SELECTION) {
            "selectPiece called in wrong phase: ${state.turnPhase}"
        }
        require(piece in state.movablePieces) { "Piece ${piece.id} is not movable." }

        val diceValue = state.lastDice!!.value
        val color = state.currentPlayer.color

        // Compute destination
        val destination = GameRules.computeDestination(piece, diceValue, color, state.players, state.mode, deferHomeEntry)!!

        // Count captures BEFORE applying move
        val captureTargets = GameRules.captureTargets(piece, diceValue, color, state.players, state.mode, deferHomeEntry)
        val captureCount = captureTargets.size

        // Apply the move
        val newMoveCounter = state.moveCounter + 1
        val newPlayers = GameRules.applyMove(
            piece = piece,
            diceValue = diceValue,
            color = color,
            players = state.players,
            mode = state.mode,
            deferHomeEntry = deferHomeEntry,
            movedAt = newMoveCounter
        )

        // Build event list
        val events = mutableListOf<GameEvent>()
        val isEntering = piece.position is PiecePosition.HomeBase
        if (isEntering) events += GameEvent.PieceEnteredBoard(color, piece.id)
        else events += GameEvent.PieceMoved(color, piece.id)
        if (destination is PiecePosition.Finished) events += GameEvent.PieceFinished(color, piece.id)

        // Capture events
        captureTargets.forEach { target ->
            events += GameEvent.PieceCaptured(target.color, color)
        }

        val newState = state.copy(
            players = newPlayers,
            moveCounter = newMoveCounter,
            eventLog = state.eventLog + events,
            movablePieces = emptyList()
        )

        // Check winner
        val winners = GameRules.checkWinner(newPlayers, state.mode)
        if (winners != null) {
            return newState.copy(
                winners = winners,
                turnPhase = TurnPhase.GAME_OVER,
                eventLog = newState.eventLog + GameEvent.PlayerWon(winners)
            )
        }

        // Extra roll?
        val captured = captureCount > 0
        val extraRoll = GameRules.grantsExtraRoll(diceValue, piece, newPlayers[state.currentPlayerIndex].pieces, captured)
        return if (extraRoll) {
            val reason = if (diceValue == 6) "rolled a 6" else "captured a piece"
            newState.copy(
                turnPhase = TurnPhase.WAITING_FOR_ROLL,
                lastDice = DiceResult(diceValue, state.lastDice.rollCount),
                eventLog = newState.eventLog + GameEvent.ExtraRollGranted(color, reason)
            )
        } else {
            advanceToNextTurn(newState)
        }
    }

    // ── Bot ──────────────────────────────────────────────────────────────────

    /**
     * Chooses a piece for a bot player using a simple priority heuristic:
     * 1. Capture an opponent
     * 2. Finish a piece (reach center)
     * 3. Move the piece closest to home (furthest along)
     * 4. Enter a new piece (if dice == 6)
     */
    fun botChoosePiece(state: GameState): Piece {
        val movable = state.movablePieces
        val diceValue = state.lastDice!!.value
        val color = state.currentPlayer.color

        // 1. Capture
        movable.firstOrNull { piece ->
            GameRules.countCaptures(piece, diceValue, color, state.players, state.mode) > 0
        }?.let { return it }

        // 2. Finish
        movable.firstOrNull { piece ->
            GameRules.computeDestination(piece, diceValue, color, state.players, state.mode) is PiecePosition.Finished
        }?.let { return it }

        // 3. Move the most-advanced active piece
        movable
            .filter { it.isActive }
            .maxByOrNull { pieceProgress(it, color) }
            ?.let { return it }

        // 4. Enter from home base
        return movable.first()
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun advanceToNextTurn(state: GameState): GameState {
        val nextIdx = GameRules.nextPlayerIndex(state.currentPlayerIndex, state.players)
        return state.copy(
            currentPlayerIndex = nextIdx,
            turnPhase = TurnPhase.WAITING_FOR_ROLL,
            lastDice = null
        )
    }

    /** Rough numeric progress value for a piece (higher = closer to finishing). */
    private fun pieceProgress(piece: Piece, color: PlayerColor): Int = when (piece.position) {
        is PiecePosition.HomeBase -> 0
        is PiecePosition.MainTrack -> {
            Board.relativePosition(color, piece.position.index) + 1
        }
        is PiecePosition.HomeColumn -> Board.MAIN_TRACK_SIZE + piece.position.step
        PiecePosition.Finished -> Board.MAIN_TRACK_SIZE + Board.HOME_COLUMN_STEPS + 1
    }
}
