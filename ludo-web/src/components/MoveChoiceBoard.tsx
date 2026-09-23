import { useState } from 'react';
import type { GameState, Piece } from '../engine/types';
import { canDeferHomeEntry, movingGroup, wouldEnterHomePath } from '../engine/gameRules';
import LudoBoard from './LudoBoard';

type PendingChoice = {
  state: GameState;
  kind: 'stack' | 'home';
  pieces: Piece[];
};

/** Shared selection flow for local and online play. Choices expire with the board state. */
export default function MoveChoiceBoard({ gameState, movablePieceIds, onPieceTap }: {
  gameState: GameState;
  movablePieceIds: Set<string>;
  onPieceTap: (piece: Piece, deferHomeEntry: boolean) => void;
}) {
  const [pending, setPending] = useState<PendingChoice | null>(null);
  const choice = pending?.state === gameState &&
    pending.pieces.every(p => movablePieceIds.has(`${p.color}:${p.id}`)) ? pending : null;

  function choosePiece(piece: Piece) {
    if (wouldEnterHomePath(piece, gameState.lastDice!.value, gameState.players, gameState.mode)) {
      setPending({ state: gameState, kind: 'home', pieces: [piece] });
    } else {
      setPending(null);
      onPieceTap(piece, false);
    }
  }

  function tap(piece: Piece) {
    if (!movablePieceIds.has(`${piece.color}:${piece.id}`)) return;
    const pos = piece.position;
    if (pos.type !== 'MainTrack') return choosePiece(piece);
    const options = new Map<string, Piece>();
    for (const candidate of gameState.movablePieces) {
      if (candidate.position.type !== 'MainTrack' || candidate.position.index !== pos.index) continue;
      const group = movingGroup(candidate, gameState.players, gameState.mode);
      const key = group.size === 2 ? [...group].sort().join('|') : `single:${candidate.color}`;
      options.set(key, candidate);
    }
    if (options.size > 1) setPending({ state: gameState, kind: 'stack', pieces: [...options.values()] });
    else choosePiece(piece);
  }

  function finish(defer: boolean) {
    if (!choice) return;
    const piece = choice.pieces[0];
    if (defer && !canDeferHomeEntry(piece, gameState.lastDice!.value, gameState.players, gameState.mode)) return;
    setPending(null);
    onPieceTap(piece, defer);
  }

  return <>
    <LudoBoard gameState={gameState} movablePieceIds={movablePieceIds} onPieceTap={tap} />
    {choice && <div className="win-overlay">
      <div className="dialog-card" role="dialog" aria-modal="true" aria-labelledby="move-choice-title">
        <h3 id="move-choice-title">{choice.kind === 'stack' ? 'Choose Pawn Move' : 'Choose Pawn Path'}</h3>
        <div className="dialog-actions">
          {choice.kind === 'stack' ? choice.pieces.map(piece =>
            <button className="btn btn-primary" key={`${piece.color}:${piece.id}`} onClick={() => choosePiece(piece)}>
              {movingGroup(piece, gameState.players, gameState.mode).size === 2 ? 'Move pair' : `Move ${piece.color.toLowerCase()} single`}
            </button>,
          ) : <>
            <button className="btn btn-primary" onClick={() => finish(false)}>Enter Finish</button>
            <button className="btn btn-outline"
              disabled={!canDeferHomeEntry(choice.pieces[0], gameState.lastDice!.value, gameState.players, gameState.mode)}
              onClick={() => finish(true)}>Keep Circulating</button>
          </>}
          <button className="btn btn-outline" onClick={() => setPending(null)}>Cancel</button>
        </div>
        {choice.kind === 'home' && !canDeferHomeEntry(choice.pieces[0], gameState.lastDice!.value, gameState.players, gameState.mode) &&
          <p>Circulating is blocked by a pair or the three-pawn limit.</p>}
      </div>
    </div>}
  </>;
}
