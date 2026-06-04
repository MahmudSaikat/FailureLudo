export type PlayerColor = 'RED' | 'BLUE' | 'YELLOW' | 'GREEN';
export const ALL_COLORS: PlayerColor[] = ['RED', 'BLUE', 'YELLOW', 'GREEN'];

export type GameMode = 'FREE_FOR_ALL' | 'TEAM';
export type TurnPhase =
  | 'WAITING_FOR_ROLL'
  | 'WAITING_FOR_PIECE_SELECTION'
  | 'NO_MOVES_AVAILABLE'
  | 'GAME_OVER';

export type PiecePosition =
  | { type: 'HomeBase' }
  | { type: 'MainTrack'; index: number }
  | { type: 'HomeColumn'; step: number }
  | { type: 'Finished' };

export const HomeBase: PiecePosition = { type: 'HomeBase' };
export const Finished: PiecePosition = { type: 'Finished' };
export const MainTrack = (index: number): PiecePosition => ({ type: 'MainTrack', index });
export const HomeColumn = (step: number): PiecePosition => ({ type: 'HomeColumn', step });

export interface Piece {
  id: number;
  color: PlayerColor;
  position: PiecePosition;
  lastMovedAt: number;
}

export interface Player {
  id: number; // 1..4 (PlayerId.value)
  color: PlayerColor;
  name: string;
  pieces: Piece[];
  isActive: boolean;
}

export interface DiceResult {
  value: number;
  rollCount: number;
}

export type GameEvent =
  | { type: 'PieceMoved'; playerId: number; color: PlayerColor; pieceId: number }
  | { type: 'PieceEnteredBoard'; playerId: number; color: PlayerColor; pieceId: number }
  | { type: 'PieceCaptured'; capturedPlayerId: number; capturedColor: PlayerColor; byPlayerId: number; byColor: PlayerColor }
  | { type: 'PieceFinished'; playerId: number; color: PlayerColor; pieceId: number }
  | { type: 'PlayerWon'; playerIds: number[] }
  | { type: 'ExtraRollGranted'; playerId: number; color: PlayerColor; reason: string }
  | { type: 'TurnSkipped'; playerId: number; color: PlayerColor }
  | { type: 'ConsecutiveSixesForfeit'; playerId: number; color: PlayerColor };

export interface GameState {
  players: Player[];
  mode: GameMode;
  moveCounter: number;
  currentPlayerIndex: number;
  turnPhase: TurnPhase;
  lastDice: DiceResult | null;
  diceByPlayer: Record<number, number | null>;
  hasEnteredBoardAtLeastOnce: Record<number, boolean>;
  sharedTeamDiceEnabled: Set<number>;
  movablePieces: Piece[];
  winners: number[] | null;
  eventLog: GameEvent[];
}

export const currentPlayer = (state: GameState): Player =>
  state.players[state.currentPlayerIndex];

export interface DeterministicTurnInput {
  actorId: number;
  movingPlayerId: number;
  pieceId: number;
  diceValue: number;
  deferHomeEntry: boolean;
}
