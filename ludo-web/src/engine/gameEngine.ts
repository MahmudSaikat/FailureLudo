import {
  type DeterministicTurnInput,
  type DiceResult,
  type GameEvent,
  type GameMode,
  type GameState,
  type Piece,
  type Player,
  type PlayerColor,
  HomeBase,
  currentPlayer,
  ALL_COLORS,
} from './types';
import { colorIndex, teamIndex } from './board';
import {
  applyMove,
  captureTargets,
  checkWinner,
  computeDestination,
  grantsExtraRoll,
  movablePiecesForTurn,
  nextPlayerIndex,
} from './gameRules';

function makePiece(id: number, color: PlayerColor): Piece {
  return { id, color, position: HomeBase, lastMovedAt: 0 };
}

function makePlayer(color: PlayerColor, name: string, isActive: boolean): Player {
  return {
    id: colorIndex(color) + 1,
    color,
    name,
    pieces: [0, 1, 2, 3].map(id => makePiece(id, color)),
    isActive,
  };
}

function initialDiceByPlayer(): Record<number, number | null> {
  const r: Record<number, number | null> = {};
  for (const c of ALL_COLORS) r[colorIndex(c) + 1] = null;
  return r;
}

function initialEnteredFlags(): Record<number, boolean> {
  const r: Record<number, boolean> = {};
  for (const c of ALL_COLORS) r[colorIndex(c) + 1] = false;
  return r;
}

export function newGame(
  activeColors: PlayerColor[],
  playerNames: Partial<Record<PlayerColor, string>> = {},
  mode: GameMode = 'FREE_FOR_ALL',
): GameState {
  const players = ALL_COLORS.map(c =>
    makePlayer(c, playerNames[c] ?? `Player-${colorIndex(c) + 1}`, activeColors.includes(c)),
  );
  const startIndex = players.findIndex(p => p.color === activeColors[0]);
  return {
    players,
    mode,
    moveCounter: 0,
    currentPlayerIndex: startIndex,
    turnPhase: 'WAITING_FOR_ROLL',
    lastDice: null,
    diceByPlayer: initialDiceByPlayer(),
    hasEnteredBoardAtLeastOnce: initialEnteredFlags(),
    sharedTeamDiceEnabled: new Set(),
    movablePieces: [],
    winners: null,
    eventLog: [],
  };
}

function rollDiceWithValue(state: GameState, diceValue: number): GameState {
  const player = currentPlayer(state);
  const prevRollCount = state.lastDice?.rollCount ?? 0;
  const newRollCount = diceValue === 6 ? prevRollCount + 1 : 1;

  if (diceValue === 6 && newRollCount === 3) {
    const event: GameEvent = { type: 'ConsecutiveSixesForfeit', playerId: player.id, color: player.color };
    const updated = {
      ...state,
      lastDice: { value: diceValue, rollCount: newRollCount } as DiceResult,
      diceByPlayer: { ...state.diceByPlayer, [player.id]: diceValue },
      eventLog: [...state.eventLog, event],
    };
    return advanceToNextTurn(updated);
  }

  const movable = movablePiecesForTurn(player, diceValue, state.players, state.mode, state.sharedTeamDiceEnabled);
  const newPhase = movable.length === 0 ? 'NO_MOVES_AVAILABLE' : 'WAITING_FOR_PIECE_SELECTION';

  return {
    ...state,
    lastDice: { value: diceValue, rollCount: newRollCount },
    diceByPlayer: { ...state.diceByPlayer, [player.id]: diceValue },
    movablePieces: movable,
    turnPhase: newPhase,
  };
}

export function rollDice(state: GameState, forcedValue: number): GameState {
  return rollDiceWithValue(state, forcedValue);
}

export function advanceNoMoves(state: GameState): GameState {
  const player = currentPlayer(state);
  const event: GameEvent = { type: 'TurnSkipped', playerId: player.id, color: player.color };
  return advanceToNextTurn({ ...state, eventLog: [...state.eventLog, event] });
}

export function selectPiece(state: GameState, piece: Piece, deferHomeEntry = false): GameState {
  const diceValue = state.lastDice!.value;
  const movingPlayer = state.players.find(p => p.color === piece.color)!;
  const actingPlayer = currentPlayer(state);

  const captures = captureTargets(piece, diceValue, piece.color, state.players, state.mode, deferHomeEntry);
  const captureCount = captures.length;
  const newMoveCounter = state.moveCounter + 1;

  const newPlayers = applyMove(
    piece, diceValue, piece.color, state.players, state.mode, deferHomeEntry, newMoveCounter,
  );

  const events: GameEvent[] = [];
  const isEntering = piece.position.type === 'HomeBase';
  const destination = computeDestination(piece, diceValue, piece.color, state.players, state.mode, deferHomeEntry)!;

  if (isEntering) {
    events.push({ type: 'PieceEnteredBoard', playerId: movingPlayer.id, color: piece.color, pieceId: piece.id });
  } else {
    events.push({ type: 'PieceMoved', playerId: movingPlayer.id, color: piece.color, pieceId: piece.id });
  }
  if (destination.type === 'Finished') {
    events.push({ type: 'PieceFinished', playerId: movingPlayer.id, color: piece.color, pieceId: piece.id });
  }
  for (const c of captures) {
    const capturedPlayer = state.players.find(p => p.color === c.color)!;
    events.push({
      type: 'PieceCaptured',
      capturedPlayerId: capturedPlayer.id,
      capturedColor: c.color,
      byPlayerId: actingPlayer.id,
      byColor: piece.color,
    });
  }

  const updatedEntered = {
    ...state.hasEnteredBoardAtLeastOnce,
    ...(isEntering ? { [movingPlayer.id]: true } : {}),
  };

  const newState: GameState = {
    ...state,
    players: newPlayers,
    moveCounter: newMoveCounter,
    eventLog: [...state.eventLog, ...events],
    hasEnteredBoardAtLeastOnce: updatedEntered,
    movablePieces: [],
  };

  const winners = checkWinner(newPlayers, state.mode);
  if (winners) {
    return {
      ...newState,
      winners,
      turnPhase: 'GAME_OVER',
      eventLog: [...newState.eventLog, { type: 'PlayerWon', playerIds: winners }],
    };
  }

  const extra = grantsExtraRoll(diceValue, captureCount > 0);
  if (extra) {
    const reason = diceValue === 6 ? 'rolled a 6' : 'captured a piece';
    return {
      ...newState,
      turnPhase: 'WAITING_FOR_ROLL',
      lastDice: state.lastDice,
      eventLog: [
        ...newState.eventLog,
        { type: 'ExtraRollGranted', playerId: actingPlayer.id, color: actingPlayer.color, reason },
      ],
    };
  }

  return advanceToNextTurn(newState);
}

export function applyDeterministicTurn(state: GameState, input: DeterministicTurnInput): GameState {
  const rolled = rollDice(state, input.diceValue);
  const movingPlayer = rolled.players.find(p => p.id === input.movingPlayerId)!;
  const selectedPiece = rolled.movablePieces.find(
    p => p.color === movingPlayer.color && p.id === input.pieceId,
  );
  if (!selectedPiece) throw new Error(`Piece ${input.pieceId} for player ${input.movingPlayerId} not movable`);
  return selectPiece(rolled, selectedPiece, input.deferHomeEntry);
}

export function applyDeterministicRollOnly(state: GameState, _actorId: number, diceValue: number): GameState {
  const rolled = rollDice(state, diceValue);
  if (rolled.turnPhase === 'NO_MOVES_AVAILABLE') return advanceNoMoves(rolled);
  if (rolled.turnPhase === 'WAITING_FOR_ROLL') return rolled; // consecutive-sixes forfeit
  throw new Error(`applyDeterministicRollOnly: unexpected phase ${rolled.turnPhase}`);
}

function advanceToNextTurn(state: GameState): GameState {
  const nextIdx = nextPlayerIndex(state.currentPlayerIndex, state.players);
  return {
    ...state,
    currentPlayerIndex: nextIdx,
    turnPhase: 'WAITING_FOR_ROLL',
    lastDice: null,
    sharedTeamDiceEnabled: computeSharedTeamDiceEnabled(state),
  };
}

function computeSharedTeamDiceEnabled(state: GameState): Set<number> {
  if (state.mode !== 'TEAM') return new Set();
  const unlocked = new Set(state.sharedTeamDiceEnabled);
  const activeByTeam = new Map<number, Player[]>();
  for (const p of state.players.filter(p => p.isActive)) {
    const ti = teamIndex(p.color);
    if (!activeByTeam.has(ti)) activeByTeam.set(ti, []);
    activeByTeam.get(ti)!.push(p);
  }
  for (const [ti, team] of activeByTeam) {
    if (team.length < 2) continue;
    if (team.every(p => state.hasEnteredBoardAtLeastOnce[p.id])) unlocked.add(ti);
  }
  return unlocked;
}
