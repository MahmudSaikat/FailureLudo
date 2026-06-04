import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import type { GameMode, PlayerColor } from '../engine/types';
import { ALL_COLORS } from '../engine/types';
import type { PlayerType } from '../game/useLocalGame';
import { setLocalConfig } from '../game/localConfig';
import { PLAYER_COLOR } from '../theme';

const SEAT_LABEL: Record<PlayerColor, string> = {
  RED: 'Red', BLUE: 'Blue', YELLOW: 'Yellow', GREEN: 'Green',
};
const MAX_NAME = 18;

export default function SetupScreen() {
  const navigate = useNavigate();

  const [mode, setMode] = useState<GameMode>('FREE_FOR_ALL');
  const [active, setActive] = useState<PlayerColor[]>(['RED', 'BLUE', 'YELLOW', 'GREEN']);
  const [names, setNames] = useState<Partial<Record<PlayerColor, string>>>({});
  const [types, setTypes] = useState<Partial<Record<PlayerColor, PlayerType>>>({});

  // Team mode is always all four seats.
  const seats: PlayerColor[] = mode === 'TEAM' ? ALL_COLORS : active;

  function toggleSeat(color: PlayerColor) {
    if (mode === 'TEAM') return;
    setActive(prev => {
      if (prev.includes(color)) {
        return prev.length > 2 ? prev.filter(c => c !== color) : prev;
      }
      // keep board order so colours stay consistent
      return ALL_COLORS.filter(c => prev.includes(c) || c === color);
    });
  }

  function nameOf(c: PlayerColor) {
    return names[c] ?? '';
  }
  function typeOf(c: PlayerColor): PlayerType {
    return types[c] ?? 'HUMAN';
  }

  function start() {
    const resolvedNames: Partial<Record<PlayerColor, string>> = {};
    for (const c of seats) {
      resolvedNames[c] = (names[c] && names[c]!.trim()) || SEAT_LABEL[c];
    }
    setLocalConfig({ activeColors: seats, names: resolvedNames, types, mode });
    navigate('/local');
  }

  return (
    <div className="setup-screen">
      <header className="topbar">
        <button className="topbar-back" onClick={() => navigate('/')} aria-label="Back">←</button>
        <h2>Game Setup</h2>
      </header>

      <div className="setup-body">
        {/* Mode */}
        <section className="setup-section">
          <h3>Mode</h3>
          <div className="chip-row">
            <button className={`chip ${mode === 'FREE_FOR_ALL' ? 'chip-on' : ''}`} onClick={() => setMode('FREE_FOR_ALL')}>
              Free for All
            </button>
            <button className={`chip ${mode === 'TEAM' ? 'chip-on' : ''}`} onClick={() => { setMode('TEAM'); setActive(ALL_COLORS); }}>
              Team (2 vs 2)
            </button>
          </div>
        </section>

        {/* Player count (FFA only) */}
        {mode === 'FREE_FOR_ALL' && (
          <section className="setup-section">
            <h3>Active Players ({active.length})</h3>
            <div className="seat-toggle-row">
              {ALL_COLORS.map((c, i) => {
                const on = active.includes(c);
                return (
                  <button
                    key={c}
                    className="seat-dot"
                    style={{ background: on ? PLAYER_COLOR[c] : `${PLAYER_COLOR[c]}40`, borderColor: on ? 'rgba(0,0,0,0.4)' : 'transparent' }}
                    onClick={() => toggleSeat(c)}
                  >
                    {i + 1}
                  </button>
                );
              })}
            </div>
            <p className="setup-hint">Tap to toggle. Minimum 2 players.</p>
          </section>
        )}

        {/* Names + Human/Bot */}
        <section className="setup-section">
          <h3>Players</h3>
          <div className="player-rows">
            {seats.map(c => (
              <div key={c} className="setup-player-row" style={{ background: `${PLAYER_COLOR[c]}1F` }}>
                <span className="setup-seat-dot" style={{ background: PLAYER_COLOR[c] }} />
                <input
                  className="setup-name-input"
                  value={nameOf(c)}
                  placeholder={SEAT_LABEL[c]}
                  maxLength={MAX_NAME}
                  onChange={e => setNames(prev => ({ ...prev, [c]: e.target.value.slice(0, MAX_NAME) }))}
                />
                <button
                  className={`type-toggle ${typeOf(c) === 'BOT' ? 'type-bot' : ''}`}
                  onClick={() => setTypes(prev => ({ ...prev, [c]: typeOf(c) === 'HUMAN' ? 'BOT' : 'HUMAN' }))}
                  title={typeOf(c) === 'HUMAN' ? 'Human (tap for bot)' : 'Bot (tap for human)'}
                >
                  {typeOf(c) === 'HUMAN' ? '🧑 Human' : '🤖 Bot'}
                </button>
              </div>
            ))}
          </div>
          <p className="setup-hint">Bots currently play random legal moves — smarter AI coming soon.</p>
        </section>

        <button className="btn btn-primary btn-tall" onClick={start} disabled={seats.length < 2}>
          Start Game
        </button>
      </div>
    </div>
  );
}
