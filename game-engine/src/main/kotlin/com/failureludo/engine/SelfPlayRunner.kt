package com.failureludo.engine

import kotlin.random.Random

enum class SelfPlayRollOnlyReason {
    NO_MOVES,
    THREE_SIX_FORFEIT
}

sealed interface SelfPlayAction {
    val actorId: PlayerId
    val diceValue: Int

    data class Move(
        override val actorId: PlayerId,
        val movingPlayerId: PlayerId,
        override val diceValue: Int,
        val pieceId: Int,
        val deferHomeEntry: Boolean
    ) : SelfPlayAction

    data class RollOnly(
        override val actorId: PlayerId,
        override val diceValue: Int,
        val reason: SelfPlayRollOnlyReason
    ) : SelfPlayAction
}

data class SelfPlayStep(
    val ply: Int,
    val before: GameState,
    val action: SelfPlayAction,
    val after: GameState
)

data class SelfPlayEpisode(
    val initialState: GameState,
    val steps: List<SelfPlayStep>,
    val finalState: GameState,
    val mode: GameMode,
    val terminatedByPlyLimit: Boolean
) {
    val winners: List<PlayerId>?
        get() = finalState.winners
}

data class SelfPlayTrainingSample(
    val state: GameState,
    val action: SelfPlayAction,
    val outcomeFromActorPerspective: Int
)

data class SelfPlayConfig(
    val activeColors: List<PlayerColor>,
    val mode: GameMode = GameMode.FREE_FOR_ALL,
    val playerTypes: Map<PlayerColor, PlayerType> = emptyMap(),
    val playerNames: Map<PlayerColor, String> = emptyMap(),
    val maxPly: Int = 3000,
    val seed: Long? = null
)

data class SelfPlayMoveDecision(
    val piece: Piece,
    val deferHomeEntry: Boolean = false
)

interface SelfPlayPolicy {
    fun chooseDice(state: GameState, random: Random): Int

    fun chooseMove(stateAfterRoll: GameState, random: Random): SelfPlayMoveDecision
}

object HeuristicSelfPlayPolicy : SelfPlayPolicy {
    override fun chooseDice(state: GameState, random: Random): Int = random.nextInt(1, 7)

    override fun chooseMove(stateAfterRoll: GameState, random: Random): SelfPlayMoveDecision {
        val move = HeuristicBotMoveSelector.chooseMove(stateAfterRoll)
        if (move != null) {
            return SelfPlayMoveDecision(piece = move.piece, deferHomeEntry = move.deferHomeEntry)
        }

        val fallbackPiece = stateAfterRoll.movablePieces.firstOrNull()
            ?: stateAfterRoll.currentPlayer.pieces.first()
        return SelfPlayMoveDecision(piece = fallbackPiece, deferHomeEntry = false)
    }
}

object RandomSelfPlayPolicy : SelfPlayPolicy {
    override fun chooseDice(state: GameState, random: Random): Int = random.nextInt(1, 7)

    override fun chooseMove(stateAfterRoll: GameState, random: Random): SelfPlayMoveDecision {
        val piece = stateAfterRoll.movablePieces.random(random)
        val diceValue = stateAfterRoll.lastDice?.value ?: 1
        val canDefer = GameRules.wouldEnterHomePath(
            piece = piece,
            diceValue = diceValue,
            color = piece.color,
            players = stateAfterRoll.players,
            mode = stateAfterRoll.mode
        )

        return SelfPlayMoveDecision(
            piece = piece,
            deferHomeEntry = canDefer && random.nextBoolean()
        )
    }
}

object SelfPlayRunner {

    fun playEpisodes(
        episodeCount: Int,
        config: SelfPlayConfig,
        defaultPolicy: SelfPlayPolicy = HeuristicSelfPlayPolicy,
        policiesByPlayerId: Map<PlayerId, SelfPlayPolicy> = emptyMap()
    ): List<SelfPlayEpisode> {
        require(episodeCount > 0) { "episodeCount must be > 0." }

        return (0 until episodeCount).map { index ->
            val seed = config.seed?.plus(index)
            playEpisode(
                config = config.copy(seed = seed),
                defaultPolicy = defaultPolicy,
                policiesByPlayerId = policiesByPlayerId
            )
        }
    }

    fun playEpisode(
        config: SelfPlayConfig,
        defaultPolicy: SelfPlayPolicy = HeuristicSelfPlayPolicy,
        policiesByPlayerId: Map<PlayerId, SelfPlayPolicy> = emptyMap()
    ): SelfPlayEpisode {
        require(config.maxPly > 0) { "maxPly must be > 0." }

        val random = config.seed?.let(::Random) ?: Random.Default
        val initialState = GameEngine.newGame(
            activeColors = config.activeColors,
            playerTypes = config.playerTypes,
            playerNames = config.playerNames,
            mode = config.mode
        )

        var state = initialState
        var ply = 0
        val steps = mutableListOf<SelfPlayStep>()

        while (!state.isGameOver && ply < config.maxPly) {
            require(state.turnPhase == TurnPhase.WAITING_FOR_ROLL) {
                "Self-play loop expects WAITING_FOR_ROLL; got ${state.turnPhase}."
            }

            val actor = state.currentPlayer
            val policy = policiesByPlayerId[actor.id] ?: defaultPolicy
            val diceValue = policy.chooseDice(state, random).coerceIn(1, 6)
            val rolled = GameEngine.rollDice(state, forcedDiceValue = diceValue)

            val step = when (rolled.turnPhase) {
                TurnPhase.WAITING_FOR_PIECE_SELECTION -> {
                    val requested = policy.chooseMove(rolled, random)
                    val chosenPiece = if (requested.piece in rolled.movablePieces) {
                        requested.piece
                    } else {
                        HeuristicBotMoveSelector.choosePiece(rolled)
                    }

                    val deferAllowed = GameRules.wouldEnterHomePath(
                        piece = chosenPiece,
                        diceValue = diceValue,
                        color = chosenPiece.color,
                        players = rolled.players,
                        mode = rolled.mode
                    )
                    val deferHomeEntry = deferAllowed && requested.deferHomeEntry

                    val after = GameEngine.selectPiece(
                        state = rolled,
                        piece = chosenPiece,
                        deferHomeEntry = deferHomeEntry
                    )

                    SelfPlayStep(
                        ply = ply + 1,
                        before = state,
                        action = SelfPlayAction.Move(
                            actorId = actor.id,
                            movingPlayerId = playerIdForColor(rolled.players, chosenPiece.color),
                            diceValue = diceValue,
                            pieceId = chosenPiece.id,
                            deferHomeEntry = deferHomeEntry
                        ),
                        after = after
                    )
                }

                TurnPhase.NO_MOVES_AVAILABLE -> {
                    val after = GameEngine.advanceNoMoves(rolled)
                    SelfPlayStep(
                        ply = ply + 1,
                        before = state,
                        action = SelfPlayAction.RollOnly(
                            actorId = actor.id,
                            diceValue = diceValue,
                            reason = SelfPlayRollOnlyReason.NO_MOVES
                        ),
                        after = after
                    )
                }

                TurnPhase.WAITING_FOR_ROLL -> {
                    // The only path back to WAITING_FOR_ROLL immediately after a roll is
                    // the consecutive-sixes forfeit branch, which already advanced the turn.
                    SelfPlayStep(
                        ply = ply + 1,
                        before = state,
                        action = SelfPlayAction.RollOnly(
                            actorId = actor.id,
                            diceValue = diceValue,
                            reason = SelfPlayRollOnlyReason.THREE_SIX_FORFEIT
                        ),
                        after = rolled
                    )
                }

                TurnPhase.GAME_OVER -> {
                    throw IllegalStateException("rollDice produced GAME_OVER directly.")
                }
            }

            steps += step
            state = step.after
            ply += 1
        }

        return SelfPlayEpisode(
            initialState = initialState,
            steps = steps,
            finalState = state,
            mode = config.mode,
            terminatedByPlyLimit = !state.isGameOver
        )
    }

    fun toTrainingSamples(episode: SelfPlayEpisode): List<SelfPlayTrainingSample> {
        return episode.steps.map { step ->
            SelfPlayTrainingSample(
                state = step.before,
                action = step.action,
                outcomeFromActorPerspective = outcomeForActor(
                    actorId = step.action.actorId,
                    finalState = episode.finalState,
                    mode = episode.mode,
                    terminatedByPlyLimit = episode.terminatedByPlyLimit
                )
            )
        }
    }

    private fun outcomeForActor(
        actorId: PlayerId,
        finalState: GameState,
        mode: GameMode,
        terminatedByPlyLimit: Boolean
    ): Int {
        val winners = finalState.winners
        if (terminatedByPlyLimit || winners.isNullOrEmpty()) return 0

        return when (mode) {
            GameMode.FREE_FOR_ALL -> {
                if (actorId in winners) 1 else -1
            }

            GameMode.TEAM -> {
                val actorColor = finalState.players.firstOrNull { it.id == actorId }?.color
                    ?: return 0
                val winningTeams = winners
                    .mapNotNull { winnerId ->
                        finalState.players.firstOrNull { it.id == winnerId }?.color?.teamIndex
                    }
                    .toSet()

                if (actorColor.teamIndex in winningTeams) 1 else -1
            }
        }
    }

    private fun playerIdForColor(players: List<Player>, color: PlayerColor): PlayerId {
        return players.firstOrNull { it.color == color }?.id
            ?: error("Player not found for color $color.")
    }
}