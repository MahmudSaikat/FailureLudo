import { useCallback, useEffect, useRef, useState } from 'react';
import { useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { currentProfile } from '../repositories/authRepository';
import { fetchRoom, listenToMoves, writeMove } from '../repositories/onlineGameRepository';
import {
  newGame,
  rollDice,
  advanceNoMoves,
  selectPiece,
  applyDeterministicTurn,
  applyDeterministicRollOnly,
} from '../engine/gameEngine';
import type { GameState, Piece, PlayerColor } from '../engine/types';
import { currentPlayer as getCurrentPlayer } from '../engine/types';
import type { OnlineMove, RoomPlayer } from '../types/online';
import LudoBoard from '../components/LudoBoard';
import PlatformBadge from '../components/PlatformBadge';

export default function GameBoardScreen() {
  const { roomId } = useParams<{ roomId: string }>();
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const profile = currentProfile();

  const [isSpectator, setIsSpectator] = useState(searchParams.get('spectate') === '1');

  const [gameState, setGameState] = useState<GameState | null>(null);
  const [roomPlayers, setRoomPlayers] = useState<RoomPlayer[]>([]);
  const [myColor, setMyColor] = useState<PlayerColor | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState('');
  const [winner, setWinner] = useState<string | null>(null);

  // These refs hold the authoritative mutable state for the sync loop
  const stateRef = useRef<GameState | null>(null);
  const appliedCountRef = useRef(0);
  const noMovesTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const applyState = useCallback((s: GameState) => {
    stateRef.current = s;
    setGameState(s);
    if (s.turnPhase === 'GAME_OVER') {
      const winnerIds = s.winners ?? [];
      const winnerPlayers = roomPlayers.filter(p => {
        const pl = s.players.find(sp => sp.id === winnerIds[0]);
        return pl && p.color === pl.color;
      });
      setWinner(winnerPlayers.map(p => p.name).join(' & ') || 'Someone');
    }
  }, [roomPlayers]);

  // Init game from room data
  useEffect(() => {
    if (!roomId) return;

    async function init() {
      try {
        const room = await fetchRoom(roomId!);
        const myUid = profile?.uid;
        const myPlayer = room.players.find(p => p.uid === myUid);
        const myCol = myPlayer?.color as PlayerColor | undefined;

        setRoomPlayers(room.players);
        if (!myPlayer) setIsSpectator(true);
        if (myCol) setMyColor(myCol);

        const activeColors = room.players
          .map(p => p.color as PlayerColor)
          .filter(c => ['RED', 'BLUE', 'YELLOW', 'GREEN'].includes(c))
          .sort((a, b) => ['RED', 'BLUE', 'YELLOW', 'GREEN'].indexOf(a) - ['RED', 'BLUE', 'YELLOW', 'GREEN'].indexOf(b));

        const names: Partial<Record<PlayerColor, string>> = {};
        for (const p of room.players) {
          if (p.color) names[p.color as PlayerColor] = p.name;
        }

        const initial = newGame(activeColors, names, 'FREE_FOR_ALL');
        stateRef.current = initial;
        setGameState(initial);
      } catch (e: unknown) {
        setError('Could not load game.');
      }
    }

    init();
  }, [roomId, profile?.uid, isSpectator]);

  // Listen to moves from Firestore
  useEffect(() => {
    if (!roomId) return;

    const unsub = listenToMoves(roomId, moves => {
      const newMoves = moves
        .filter(m => m.index >= appliedCountRef.current)
        .sort((a, b) => a.index - b.index);

      if (newMoves.length === 0) return;

      let state = stateRef.current;
      if (!state) return;

      for (const move of newMoves) {
        try {
          if (move.pieceId === -1) {
            state = applyDeterministicRollOnly(state, move.actorId, move.diceValue);
          } else {
            state = applyDeterministicTurn(state, {
              actorId: move.actorId,
              movingPlayerId: move.movingPlayerId,
              diceValue: move.diceValue,
              pieceId: move.pieceId,
              deferHomeEntry: move.deferHomeEntry,
            });
          }
          appliedCountRef.current++;
        } catch (e: unknown) {
          setError('Game sync error: ' + (e as Error).message);
          return;
        }
      }

      applyState(state);
    });

    return unsub;
  }, [roomId, applyState]);

  const isMyTurn = useCallback(() => {
    const s = stateRef.current;
    if (!s || !myColor) return false;
    const cp = getCurrentPlayer(s);
    return cp.color === myColor && s.turnPhase !== 'GAME_OVER';
  }, [myColor]);

  async function submitMove(move: OnlineMove, finalState: GameState) {
    noMovesTimerRef.current && clearTimeout(noMovesTimerRef.current);
    appliedCountRef.current++;
    applyState(finalState);
    setIsSubmitting(false);
    try {
      await writeMove(roomId!, move);
    } catch {
      setError('Move failed. Check connection.');
    }
  }

  async function handleRoll() {
    const s = stateRef.current;
    if (!s || !isMyTurn() || s.turnPhase !== 'WAITING_FOR_ROLL' || isSubmitting) return;
    setIsSubmitting(true);

    const diceValue = Math.floor(Math.random() * 6) + 1;
    const rolled = rollDice(s, diceValue);
    applyState(rolled);
    stateRef.current = rolled;

    const move: OnlineMove = {
      index: appliedCountRef.current,
      actorId: getCurrentPlayer(s).id,
      movingPlayerId: getCurrentPlayer(s).id,
      diceValue,
      pieceId: -1,
      deferHomeEntry: false,
    };

    if (rolled.turnPhase === 'NO_MOVES_AVAILABLE') {
      noMovesTimerRef.current = setTimeout(async () => {
        const final = advanceNoMoves(rolled);
        await submitMove(move, final);
      }, 1200);
    } else if (rolled.turnPhase === 'WAITING_FOR_ROLL') {
      // consecutive-sixes forfeit — already advanced
      await submitMove(move, rolled);
    } else {
      // WAITING_FOR_PIECE_SELECTION — let user pick
      setIsSubmitting(false);
    }
  }

  async function handlePieceTap(piece: Piece) {
    const s = stateRef.current;
    if (!s || !isMyTurn() || s.turnPhase !== 'WAITING_FOR_PIECE_SELECTION' || isSubmitting) return;
    if (!s.movablePieces.find(p => p.color === piece.color && p.id === piece.id)) return;

    setIsSubmitting(true);
    const diceValue = s.lastDice!.value;
    const final = selectPiece(s, piece);

    const move: OnlineMove = {
      index: appliedCountRef.current,
      actorId: getCurrentPlayer(s).id,
      movingPlayerId: getCurrentPlayer(s).id,
      diceValue,
      pieceId: piece.id,
      deferHomeEntry: false,
    };

    await submitMove(move, final);
  }

  useEffect(() => () => { noMovesTimerRef.current && clearTimeout(noMovesTimerRef.current); }, []);

  if (!gameState) {
    return <div className="screen-center"><p>Loading game…</p></div>;
  }

  const cp = getCurrentPlayer(gameState);
  const myTurn = isMyTurn();
  const canRoll = myTurn && gameState.turnPhase === 'WAITING_FOR_ROLL' && !isSubmitting;

  const movablePieceIds = new Set(
    (myTurn && gameState.turnPhase === 'WAITING_FOR_PIECE_SELECTION')
      ? gameState.movablePieces.map(p => `${p.color}:${p.id}`)
      : [],
  );

  return (
    <div className="game-screen">
      {winner && (
        <div className="win-overlay">
          <div className="win-card">
            <h2>🎉 {winner} wins!</h2>
            <button className="btn btn-primary" onClick={() => navigate('/lobby')}>Back to Lobby</button>
          </div>
        </div>
      )}

      <div className="player-sidebar">
        {roomPlayers.map(p => {
          const gp = gameState.players.find(sp => sp.color === p.color);
          const isActive = cp.color === p.color;
          return (
            <div key={p.uid} className={`player-card ${isActive ? 'active' : ''}`}>
              <PlatformBadge platform={p.platform} />
              <div className="player-card-info">
                <span className="player-card-name">{p.name}</span>
                {p.uid === profile?.uid && <span className="badge badge-you">You</span>}
                {gp && (
                  <span className="pieces-count">
                    {gp.pieces.filter(pc => pc.position.type === 'Finished').length}/4 ✓
                  </span>
                )}
              </div>
            </div>
          );
        })}
      </div>

      <div className="game-main">
        <LudoBoard
          gameState={gameState}
          movablePieceIds={movablePieceIds}
          onPieceTap={handlePieceTap}
        />

        <div className="game-controls">
          {isSpectator ? (
            <p className="spectator-label">👁 Spectating</p>
          ) : (
            <>
              {gameState.turnPhase !== 'GAME_OVER' && (
                <p className="turn-label">
                  {myTurn ? 'Your turn' : `${cp.name}'s turn`}
                  {gameState.lastDice && ` — Dice: ${gameState.lastDice.value}`}
                </p>
              )}
              {canRoll && (
                <button className="btn btn-primary btn-roll" onClick={handleRoll}>
                  Roll Dice 🎲
                </button>
              )}
              {gameState.turnPhase === 'WAITING_FOR_PIECE_SELECTION' && myTurn && (
                <p className="hint-text">Tap a highlighted piece to move</p>
              )}
              {gameState.turnPhase === 'NO_MOVES_AVAILABLE' && (
                <p className="hint-text">No moves — skipping turn…</p>
              )}
            </>
          )}
          {error && <p className="error-text">{error}</p>}
        </div>
      </div>
    </div>
  );
}
