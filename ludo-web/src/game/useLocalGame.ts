import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  newGame,
  rollDice,
  advanceNoMoves,
  selectPiece as engineSelectPiece,
} from '../engine/gameEngine';
import type { GameMode, GameState, Piece, PlayerColor } from '../engine/types';
import { currentPlayer } from '../engine/types';

export type PlayerType = 'HUMAN' | 'BOT';

export interface LocalGameConfig {
  activeColors: PlayerColor[];
  names: Partial<Record<PlayerColor, string>>;
  types: Partial<Record<PlayerColor, PlayerType>>;
  mode: GameMode;
}

function randomDie(): number {
  return Math.floor(Math.random() * 6) + 1;
}

/**
 * Placeholder bot: picks a random legal piece. The real heuristic/AI bot
 * (ported from Kotlin HeuristicBotMoveSelector) is a Stage-2 task — see plan 008.
 */
function pickBotPiece(state: GameState): Piece {
  const options = state.movablePieces;
  return options[Math.floor(Math.random() * options.length)];
}

const ROLL_DELAY_MS = 650;
const SELECT_DELAY_MS = 650;
const NO_MOVES_DELAY_MS = 900;

export function useLocalGame(config: LocalGameConfig) {
  const [state, setState] = useState<GameState>(() =>
    newGame(config.activeColors, config.names, config.mode),
  );
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const clearTimer = () => {
    if (timerRef.current) {
      clearTimeout(timerRef.current);
      timerRef.current = null;
    }
  };

  const restart = useCallback(() => {
    clearTimer();
    setState(newGame(config.activeColors, config.names, config.mode));
    // config is only read at New Game time; intentionally not a dependency.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const roll = useCallback(() => {
    setState(s =>
      s.turnPhase === 'WAITING_FOR_ROLL' ? rollDice(s, randomDie()) : s,
    );
  }, []);

  const select = useCallback((piece: Piece, deferHomeEntry = false) => {
    setState(s => {
      if (s.turnPhase !== 'WAITING_FOR_PIECE_SELECTION') return s;
      const movable = s.movablePieces.find(
        p => p.color === piece.color && p.id === piece.id,
      );
      if (!movable) return s;
      return engineSelectPiece(s, movable, deferHomeEntry);
    });
  }, []);

  const isBotSeat = (color: PlayerColor) => config.types[color] === 'BOT';

  // Drive auto-advance (no-moves) and bot turns off the current state.
  useEffect(() => {
    clearTimer();
    if (state.turnPhase === 'GAME_OVER') return;

    const player = currentPlayer(state);

    if (state.turnPhase === 'NO_MOVES_AVAILABLE') {
      timerRef.current = setTimeout(
        () => setState(s => (s.turnPhase === 'NO_MOVES_AVAILABLE' ? advanceNoMoves(s) : s)),
        NO_MOVES_DELAY_MS,
      );
      return clearTimer;
    }

    if (!isBotSeat(player.color)) return;

    if (state.turnPhase === 'WAITING_FOR_ROLL') {
      timerRef.current = setTimeout(
        () => setState(s => (s.turnPhase === 'WAITING_FOR_ROLL' ? rollDice(s, randomDie()) : s)),
        ROLL_DELAY_MS,
      );
    } else if (state.turnPhase === 'WAITING_FOR_PIECE_SELECTION') {
      timerRef.current = setTimeout(
        () =>
          setState(s =>
            s.turnPhase === 'WAITING_FOR_PIECE_SELECTION'
              ? engineSelectPiece(s, pickBotPiece(s))
              : s,
          ),
        SELECT_DELAY_MS,
      );
    }
    return clearTimer;
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [state]);

  useEffect(() => clearTimer, []);

  const cp = currentPlayer(state);
  const currentIsBot = isBotSeat(cp.color);

  const movablePieceIds = useMemo(
    () =>
      new Set(
        state.turnPhase === 'WAITING_FOR_PIECE_SELECTION' && !currentIsBot
          ? state.movablePieces.map(p => `${p.color}:${p.id}`)
          : [],
      ),
    [state, currentIsBot],
  );

  const winnerNames = useMemo(() => {
    if (!state.winners) return null;
    return state.winners
      .map(id => state.players.find(p => p.id === id)?.name ?? 'Player')
      .join(' & ');
  }, [state]);

  return {
    state,
    currentPlayer: cp,
    currentIsBot,
    movablePieceIds,
    winnerNames,
    canRoll: state.turnPhase === 'WAITING_FOR_ROLL' && !currentIsBot,
    roll,
    select,
    restart,
  };
}
