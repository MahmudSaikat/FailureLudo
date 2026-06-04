import {
  type GameMode,
  type Piece,
  type PiecePosition,
  type Player,
  type PlayerColor,
  HomeBase,
  Finished,
  MainTrack,
  HomeColumn,
} from './types';
import {
  colorIndex,
  ENTRY_POSITIONS,
  HOME_COLUMN_STEPS,
  isSafeSquare,
  MAIN_TRACK_SIZE,
  stepsToHomeColumnEntry,
  teamIndex,
} from './board';

interface PieceRef {
  color: PlayerColor;
  pieceId: number;
}

interface Occupant {
  color: PlayerColor;
  piece: Piece;
}

export interface CaptureTarget {
  color: PlayerColor;
  pieceId: number;
}

function sideKey(color: PlayerColor, mode: GameMode): number {
  return mode === 'TEAM' ? teamIndex(color) : colorIndex(color);
}

function isSameSide(a: PlayerColor, b: PlayerColor, mode: GameMode): boolean {
  return sideKey(a, mode) === sideKey(b, mode);
}

function occupantsAtMainIndex(players: Player[], index: number): Occupant[] {
  const result: Occupant[] = [];
  for (const player of players) {
    if (!player.isActive) continue;
    for (const piece of player.pieces) {
      const pos = piece.position;
      if (pos.type === 'MainTrack' && pos.index === index) {
        result.push({ color: player.color, piece });
      }
    }
  }
  return result;
}

function topSingleRefForStack(stack: Occupant[]): PieceRef | null {
  if (stack.length < 3) return null;
  let top = stack[0];
  for (const o of stack) {
    if (
      o.piece.lastMovedAt > top.piece.lastMovedAt ||
      (o.piece.lastMovedAt === top.piece.lastMovedAt && colorIndex(o.color) > colorIndex(top.color)) ||
      (o.piece.lastMovedAt === top.piece.lastMovedAt && colorIndex(o.color) === colorIndex(top.color) && o.piece.id > top.piece.id)
    ) {
      top = o;
    }
  }
  return { color: top.color, pieceId: top.piece.id };
}

function doubleComponentRefsForStack(stack: Occupant[]): Set<string> {
  if (stack.length < 2) return new Set();
  if (stack.length === 2) {
    return new Set(stack.map(o => `${o.color}:${o.piece.id}`));
  }
  const topSingle = topSingleRefForStack(stack);
  const topKey = topSingle ? `${topSingle.color}:${topSingle.pieceId}` : null;
  const candidates = stack
    .filter(o => `${o.color}:${o.piece.id}` !== topKey)
    .map(o => ({ ref: { color: o.color, pieceId: o.piece.id }, piece: o.piece }))
    .sort((a, b) => {
      if (a.piece.lastMovedAt !== b.piece.lastMovedAt) return a.piece.lastMovedAt - b.piece.lastMovedAt;
      if (colorIndex(a.ref.color) !== colorIndex(b.ref.color)) return colorIndex(a.ref.color) - colorIndex(b.ref.color);
      return a.ref.pieceId - b.ref.pieceId;
    });
  return new Set(candidates.slice(0, 2).map(c => `${c.ref.color}:${c.ref.pieceId}`));
}

function ownStackAtIndex(index: number, movingColor: PlayerColor, players: Player[], mode: GameMode): Occupant[] {
  return occupantsAtMainIndex(players, index).filter(o => isSameSide(movingColor, o.color, mode));
}

function enemyStacksAtIndex(index: number, movingColor: PlayerColor, players: Player[], mode: GameMode): Occupant[][] {
  const groups = new Map<number, Occupant[]>();
  for (const o of occupantsAtMainIndex(players, index)) {
    if (isSameSide(movingColor, o.color, mode)) continue;
    const key = sideKey(o.color, mode);
    if (!groups.has(key)) groups.set(key, []);
    groups.get(key)!.push(o);
  }
  return Array.from(groups.values());
}

function lockedPairRefsForPiece(piece: Piece, color: PlayerColor, players: Player[], mode: GameMode): Set<string> {
  if (piece.position.type !== 'MainTrack') return new Set();
  const index = piece.position.index;
  const stack = ownStackAtIndex(index, color, players, mode);
  if (stack.length < 2) return new Set();

  const doubleRefs = doubleComponentRefsForStack(stack);
  const selfKey = `${color}:${piece.id}`;
  if (!doubleRefs.has(selfKey)) return new Set();
  if (isSafeSquare(index)) return new Set();

  const isMixedTeamPair = new Set(Array.from(doubleRefs).map(k => k.split(':')[0] as PlayerColor)).size > 1;
  const homeColEntry = { RED: 50, BLUE: 11, YELLOW: 24, GREEN: 37 }[color];
  if (!isMixedTeamPair && index === homeColEntry) return new Set();

  return doubleRefs;
}

function effectiveDiceValue(piece: Piece, diceValue: number, color: PlayerColor, players: Player[], mode: GameMode): number | null {
  const locked = lockedPairRefsForPiece(piece, color, players, mode);
  const selfKey = `${color}:${piece.id}`;
  if (!locked.has(selfKey)) return diceValue;
  if (diceValue % 2 !== 0) return null;
  return diceValue / 2;
}

function isMovingAsLockedDouble(piece: Piece, diceValue: number, color: PlayerColor, players: Player[], mode: GameMode): boolean {
  const locked = lockedPairRefsForPiece(piece, color, players, mode);
  const selfKey = `${color}:${piece.id}`;
  return locked.has(selfKey) && diceValue % 2 === 0;
}

function computeMainTrackDestination(
  currentIdx: number,
  effectiveDice: number,
  color: PlayerColor,
  deferHomeEntry: boolean,
): PiecePosition | null {
  const stepsToEntry = stepsToHomeColumnEntry(color, currentIdx);

  if (deferHomeEntry) {
    return MainTrack((currentIdx + effectiveDice) % MAIN_TRACK_SIZE);
  }

  if (effectiveDice <= stepsToEntry) {
    return MainTrack((currentIdx + effectiveDice) % MAIN_TRACK_SIZE);
  }
  const homeStep = effectiveDice - stepsToEntry;
  if (homeStep < HOME_COLUMN_STEPS + 1) return HomeColumn(homeStep);
  if (homeStep === HOME_COLUMN_STEPS + 1) return Finished;
  return null;
}

export function computeDestination(
  piece: Piece,
  diceValue: number,
  color: PlayerColor,
  players: Player[],
  mode: GameMode,
  deferHomeEntry = false,
): PiecePosition | null {
  const eff = effectiveDiceValue(piece, diceValue, color, players, mode);
  if (eff === null) return null;

  switch (piece.position.type) {
    case 'HomeBase':
      return eff === 6 ? MainTrack(ENTRY_POSITIONS[color]) : null;
    case 'MainTrack': {
      const lockedRefs = lockedPairRefsForPiece(piece, color, players, mode);
      const selfKey = `${color}:${piece.id}`;
      const isMixedTeam = lockedRefs.size > 0 &&
        new Set(Array.from(lockedRefs).map(k => k.split(':')[0])).size > 1;
      return computeMainTrackDestination(piece.position.index, eff, color, deferHomeEntry || (lockedRefs.has(selfKey) && isMixedTeam));
    }
    case 'HomeColumn': {
      const newStep = piece.position.step + eff;
      if (newStep < HOME_COLUMN_STEPS + 1) return HomeColumn(newStep);
      if (newStep === HOME_COLUMN_STEPS + 1) return Finished;
      return null;
    }
    case 'Finished':
      return null;
  }
}

function hasOpponentDoubleAt(index: number, movingColor: PlayerColor, players: Player[], mode: GameMode): boolean {
  if (isSafeSquare(index)) return false;
  return enemyStacksAtIndex(index, movingColor, players, mode).some(stack => stack.length >= 2);
}

function crossesOpponentDoubleBarrier(
  currentIndex: number,
  effectiveDice: number,
  movingColor: PlayerColor,
  destination: PiecePosition,
  players: Player[],
  mode: GameMode,
): boolean {
  let mainTrackSteps: number;
  if (destination.type === 'MainTrack') {
    mainTrackSteps = effectiveDice;
  } else if (destination.type === 'HomeColumn' || destination.type === 'Finished') {
    mainTrackSteps = stepsToHomeColumnEntry(movingColor, currentIndex);
  } else {
    mainTrackSteps = 0;
  }

  if (mainTrackSteps <= 0) return false;

  for (let step = 1; step <= mainTrackSteps; step++) {
    const index = (currentIndex + step) % MAIN_TRACK_SIZE;
    if (!hasOpponentDoubleAt(index, movingColor, players, mode)) continue;
    const landsOnBarrierCell = destination.type === 'MainTrack' && step === mainTrackSteps;
    if (!landsOnBarrierCell) return true;
  }
  return false;
}

export function canMove(piece: Piece, diceValue: number, color: PlayerColor, players: Player[], mode: GameMode): boolean {
  if (piece.position.type === 'Finished') return false;
  const destination = computeDestination(piece, diceValue, color, players, mode);
  if (!destination) return false;

  if (piece.position.type === 'MainTrack' && !isMovingAsLockedDouble(piece, diceValue, color, players, mode)) {
    const eff = effectiveDiceValue(piece, diceValue, color, players, mode);
    if (eff === null) return false;
    if (crossesOpponentDoubleBarrier(piece.position.index, eff, color, destination, players, mode)) return false;
  }
  return true;
}

export function movablePiecesForTurn(
  currentPlayer: Player,
  diceValue: number,
  allPlayers: Player[],
  mode: GameMode,
  sharedTeamDiceEnabled: Set<number>,
): Piece[] {
  let controllable: Player[];
  if (mode === 'TEAM' && sharedTeamDiceEnabled.has(teamIndex(currentPlayer.color))) {
    const teammates = allPlayers.filter(
      p => p.isActive && teamIndex(p.color) === teamIndex(currentPlayer.color) && p.id !== currentPlayer.id,
    );
    controllable = [currentPlayer, ...teammates];
  } else {
    controllable = [currentPlayer];
  }

  const seen = new Set<string>();
  const result: Piece[] = [];
  for (const player of controllable) {
    for (const piece of player.pieces) {
      if (!canMove(piece, diceValue, player.color, allPlayers, mode)) continue;
      const key = `${piece.color}:${piece.id}`;
      if (!seen.has(key)) {
        seen.add(key);
        result.push(piece);
      }
    }
  }
  return result;
}

function pairCaptureTargetsOnEnemyDouble(
  piece: Piece,
  movingColor: PlayerColor,
  destinationIndex: number,
  players: Player[],
  mode: GameMode,
): CaptureTarget[] {
  const movingRefKey = `${movingColor}:${piece.id}`;
  const ownSinglesAtCell = occupantsAtMainIndex(players, destinationIndex).filter(
    o => isSameSide(movingColor, o.color, mode) && `${o.color}:${o.piece.id}` !== movingRefKey,
  );
  if (ownSinglesAtCell.length === 0) return [];

  const enemyDoubleTargets: CaptureTarget[] = [];
  for (const stack of enemyStacksAtIndex(destinationIndex, movingColor, players, mode)) {
    if (stack.length < 2) continue;
    for (const key of doubleComponentRefsForStack(stack)) {
      const [c, id] = key.split(':');
      enemyDoubleTargets.push({ color: c as PlayerColor, pieceId: Number(id) });
    }
  }
  return enemyDoubleTargets;
}

export function captureTargets(
  piece: Piece,
  diceValue: number,
  movingColor: PlayerColor,
  players: Player[],
  mode: GameMode,
  deferHomeEntry = false,
): CaptureTarget[] {
  const destination = computeDestination(piece, diceValue, movingColor, players, mode, deferHomeEntry);
  if (!destination || destination.type !== 'MainTrack') return [];
  if (isSafeSquare(destination.index)) return [];

  const movingAsDouble = isMovingAsLockedDouble(piece, diceValue, movingColor, players, mode);
  const enemyStacks = enemyStacksAtIndex(destination.index, movingColor, players, mode);

  if (movingAsDouble) {
    return enemyStacks.flatMap(stack =>
      Array.from(doubleComponentRefsForStack(stack)).map(key => {
        const [c, id] = key.split(':');
        return { color: c as PlayerColor, pieceId: Number(id) };
      }),
    );
  }

  const pairCapture = pairCaptureTargetsOnEnemyDouble(piece, movingColor, destination.index, players, mode);
  if (pairCapture.length > 0) return pairCapture;

  return enemyStacks.flatMap(stack => {
    const doubleRefs = doubleComponentRefsForStack(stack);
    const protectedTop = stack.length >= 3 ? topSingleRefForStack(stack) : null;
    const protectedKey = protectedTop ? `${protectedTop.color}:${protectedTop.pieceId}` : null;
    return stack
      .filter(o => !doubleRefs.has(`${o.color}:${o.piece.id}`))
      .filter(o => `${o.color}:${o.piece.id}` !== protectedKey)
      .map(o => ({ color: o.color, pieceId: o.piece.id }));
  });
}

export function applyMove(
  piece: Piece,
  diceValue: number,
  color: PlayerColor,
  players: Player[],
  mode: GameMode,
  deferHomeEntry = false,
  movedAt = 0,
): Player[] {
  const newPosition = computeDestination(piece, diceValue, color, players, mode, deferHomeEntry);
  if (!newPosition) return players;

  const locked = lockedPairRefsForPiece(piece, color, players, mode);
  const selfKey = `${color}:${piece.id}`;
  const movingAsDouble = locked.has(selfKey) && diceValue % 2 === 0;
  const movingKeys = movingAsDouble ? locked : new Set([selfKey]);

  const captures = captureTargets(piece, diceValue, color, players, mode, deferHomeEntry);
  const captureMap = new Map<PlayerColor, Set<number>>();
  for (const c of captures) {
    if (!captureMap.has(c.color)) captureMap.set(c.color, new Set());
    captureMap.get(c.color)!.add(c.pieceId);
  }

  return players.map(player => ({
    ...player,
    pieces: player.pieces.map(p => {
      const key = `${player.color}:${p.id}`;
      if (movingKeys.has(key)) return { ...p, position: newPosition, lastMovedAt: movedAt };
      const capturedIds = captureMap.get(player.color);
      if (capturedIds?.has(p.id)) return { ...p, position: HomeBase };
      return p;
    }),
  }));
}

export function checkWinner(players: Player[], mode: GameMode): number[] | null {
  const active = players.filter(p => p.isActive);
  if (mode === 'FREE_FOR_ALL') {
    const winner = active.find(p => p.pieces.every(pc => pc.position.type === 'Finished'));
    return winner ? [winner.id] : null;
  }
  for (let ti = 0; ti <= 1; ti++) {
    const team = active.filter(p => teamIndex(p.color) === ti);
    if (team.length > 0 && team.every(p => p.pieces.every(pc => pc.position.type === 'Finished'))) {
      return team.map(p => p.id);
    }
  }
  return null;
}

export function nextPlayerIndex(currentIndex: number, players: Player[]): number {
  const size = players.length;
  let next = (currentIndex - 1 + size) % size;
  for (let i = 0; i < size; i++) {
    if (players[next].isActive) return next;
    next = (next - 1 + size) % size;
  }
  return currentIndex;
}

export function grantsExtraRoll(diceValue: number, capturedAny: boolean): boolean {
  return diceValue === 6 || capturedAny;
}
