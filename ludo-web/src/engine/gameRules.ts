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
  if (stack.length !== 3) return null;
  const pair = doubleComponentRefsForStack(stack);
  if (pair.size === 0) return null;
  const single = stack.find(o => !pair.has(`${o.color}:${o.piece.id}`));
  return single ? { color: single.color, pieceId: single.piece.id } : null;
}

function doubleComponentRefsForStack(stack: Occupant[]): Set<string> {
  if (stack.length < 2) return new Set();
  const pos = stack[0].piece.position;
  if (pos.type !== 'MainTrack' || isSafeSquare(pos.index)) return new Set();
  const groups = new Map<string, Occupant[]>();
  for (const o of stack) {
    if (!o.piece.pairKey) continue;
    const group = groups.get(o.piece.pairKey) ?? [];
    group.push(o);
    groups.set(o.piece.pairKey, group);
  }
  // Legacy snapshots use the oldest-two convention until their next transition.
  const pair = [...groups.values()].find(group => group.length === 2) ?? [...stack]
    .sort((a, b) => a.piece.lastMovedAt - b.piece.lastMovedAt ||
      colorIndex(a.color) - colorIndex(b.color) || a.piece.id - b.piece.id)
    .slice(0, 2);
  return new Set(pair.map(o => `${o.color}:${o.piece.id}`));
}

export function normalizePairs(players: Player[], mode: GameMode): Player[] {
  const keys = new Map<string, string>();
  const cells = new Set(players.filter(p => p.isActive).flatMap(p => p.pieces)
    .flatMap(p => p.position.type === 'MainTrack' ? [p.position.index] : []));
  for (const index of cells) {
    const groups = new Map<number, Occupant[]>();
    for (const o of occupantsAtMainIndex(players, index)) {
      const side = sideKey(o.color, mode);
      groups.set(side, [...(groups.get(side) ?? []), o]);
    }
    for (const stack of groups.values()) {
      const pair = doubleComponentRefsForStack(stack);
      if (pair.size !== 2) continue;
      const members = stack.filter(o => pair.has(`${o.color}:${o.piece.id}`))
        .sort((a, b) => colorIndex(a.color) - colorIndex(b.color) || a.piece.id - b.piece.id);
      const key = members.map(o => `${o.color}:${o.piece.id}`).join('|');
      for (const ref of pair) keys.set(ref, key);
    }
  }
  return players.map(p => ({ ...p, pieces: p.pieces.map(pc => ({
    ...pc, pairKey: keys.get(`${p.color}:${pc.id}`) ?? null,
  })) }));
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

export function canMove(piece: Piece, diceValue: number, color: PlayerColor, players: Player[], mode: GameMode, deferHomeEntry = false): boolean {
  if (piece.position.type === 'Finished' || !Number.isInteger(diceValue) || diceValue < 1 || diceValue > 6) return false;
  if (deferHomeEntry && !wouldEnterHomePath(piece, diceValue, players, mode)) return false;
  const destination = computeDestination(piece, diceValue, color, players, mode, deferHomeEntry);
  if (!destination) return false;
  if (destination.type === 'MainTrack' && !isSafeSquare(destination.index)) {
    const moving = movingGroup(piece, players, mode);
    const remaining = ownStackAtIndex(destination.index, color, players, mode)
      .filter(o => !moving.has(`${o.color}:${o.piece.id}`)).length;
    if (remaining + moving.size > 3) return false;
  }

  if (piece.position.type === 'MainTrack' && !isMovingAsLockedDouble(piece, diceValue, color, players, mode)) {
    const eff = effectiveDiceValue(piece, diceValue, color, players, mode);
    if (eff === null) return false;
    if (crossesOpponentDoubleBarrier(piece.position.index, eff, color, destination, players, mode)) return false;
  }
  return true;
}

export function movingGroup(piece: Piece, players: Player[], mode: GameMode): Set<string> {
  const pair = lockedPairRefsForPiece(piece, piece.color, players, mode);
  return pair.size ? pair : new Set([`${piece.color}:${piece.id}`]);
}

export function wouldEnterHomePath(piece: Piece, diceValue: number, players: Player[], mode: GameMode): boolean {
  if (piece.position.type !== 'MainTrack') return false;
  const destination = computeDestination(piece, diceValue, piece.color, players, mode);
  return destination?.type === 'HomeColumn' || destination?.type === 'Finished';
}

export function canDeferHomeEntry(piece: Piece, diceValue: number, players: Player[], mode: GameMode): boolean {
  return canMove(piece, diceValue, piece.color, players, mode, true);
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
  if (ownSinglesAtCell.length !== 1) return [];

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
  return pairCapture.concat(enemyStacks.flatMap(stack => {
    const doubleRefs = doubleComponentRefsForStack(stack);
    const protectedTop = stack.length >= 3 ? topSingleRefForStack(stack) : null;
    const protectedKey = protectedTop ? `${protectedTop.color}:${protectedTop.pieceId}` : null;
    return stack
      .filter(o => !doubleRefs.has(`${o.color}:${o.piece.id}`))
      .filter(o => `${o.color}:${o.piece.id}` !== protectedKey)
      .map(o => ({ color: o.color, pieceId: o.piece.id }));
  }));
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
  if (!canMove(piece, diceValue, color, players, mode, deferHomeEntry)) return players;
  const bonded = normalizePairs(players, mode);
  const movingPiece = bonded.find(p => p.color === color)!.pieces.find(p => p.id === piece.id)!;
  const newPosition = computeDestination(movingPiece, diceValue, color, bonded, mode, deferHomeEntry);
  if (!newPosition) return players;
  const movingKeys = movingGroup(movingPiece, bonded, mode);
  const captures = new Set(captureTargets(movingPiece, diceValue, color, bonded, mode, deferHomeEntry)
    .map(c => `${c.color}:${c.pieceId}`));
  const moved = bonded.map(player => ({
    ...player,
    pieces: player.pieces.map(p => {
      const key = `${player.color}:${p.id}`;
      if (movingKeys.has(key)) return { ...p, position: newPosition, lastMovedAt: movedAt };
      if (captures.has(key)) return { ...p, position: HomeBase, pairKey: null };
      return p;
    }),
  }));
  return normalizePairs(moved, mode);
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
