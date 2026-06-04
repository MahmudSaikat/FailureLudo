import type { GameState } from '../engine/types';
import { PLAYER_COLOR } from '../theme';

interface Props {
  state: GameState;
  winnerNames: string;
  primaryLabel: string;
  onPrimary: () => void;
  secondaryLabel: string;
  onSecondary: () => void;
}

export default function WinCard({
  state, winnerNames, primaryLabel, onPrimary, secondaryLabel, onSecondary,
}: Props) {
  const ranked = [...state.players]
    .filter(p => p.isActive)
    .sort(
      (a, b) =>
        b.pieces.filter(x => x.position.type === 'Finished').length -
        a.pieces.filter(x => x.position.type === 'Finished').length,
    );

  return (
    <div className="win-overlay">
      <div className="win-card">
        <div className="win-trophy">🏆</div>
        <h2>{winnerNames} Win!</h2>

        <div className="win-scores">
          {ranked.map(p => {
            const finished = p.pieces.filter(x => x.position.type === 'Finished').length;
            return (
              <div key={p.id} className="win-score-row">
                <span className="win-score-dot" style={{ background: PLAYER_COLOR[p.color] }}>{p.id}</span>
                <span className="win-score-name">{p.name}</span>
                <span className="win-score-dots">
                  {[0, 1, 2, 3].map(i => (
                    <span
                      key={i}
                      className="win-pip"
                      style={{ background: i < finished ? PLAYER_COLOR[p.color] : 'rgba(0,0,0,0.12)' }}
                    />
                  ))}
                </span>
              </div>
            );
          })}
        </div>

        <div className="win-actions">
          <button className="btn btn-primary btn-tall" onClick={onPrimary}>{primaryLabel}</button>
          <button className="btn btn-outline btn-tall" onClick={onSecondary}>{secondaryLabel}</button>
        </div>
      </div>
    </div>
  );
}
