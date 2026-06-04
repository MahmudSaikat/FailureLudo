import { useState } from 'react';
import { Navigate, useNavigate } from 'react-router-dom';
import { getLocalConfig } from '../game/localConfig';
import { useLocalGame } from '../game/useLocalGame';
import LudoBoard from '../components/LudoBoard';
import Dice from '../components/Dice';
import WinCard from '../components/WinCard';
import { PLAYER_COLOR } from '../theme';

export default function LocalGameScreen() {
  const navigate = useNavigate();
  const config = getLocalConfig();
  const [confirmQuit, setConfirmQuit] = useState(false);

  if (!config) return <Navigate to="/setup" replace />;

  return <LocalGame
    key={JSON.stringify(config.activeColors) + config.mode}
    config={config}
    confirmQuit={confirmQuit}
    setConfirmQuit={setConfirmQuit}
    navigate={navigate}
  />;
}

function LocalGame({
  config, confirmQuit, setConfirmQuit, navigate,
}: {
  config: NonNullable<ReturnType<typeof getLocalConfig>>;
  confirmQuit: boolean;
  setConfirmQuit: (v: boolean) => void;
  navigate: (to: string) => void;
}) {
  const game = useLocalGame(config);
  const { state, currentPlayer: cp, currentIsBot, movablePieceIds, winnerNames } = game;

  const activePlayers = state.players.filter(p => p.isActive);
  const dice = state.diceByPlayer[cp.id] ?? state.lastDice?.value ?? null;

  return (
    <div className="local-game">
      <header className="topbar">
        <button className="topbar-back" onClick={() => setConfirmQuit(true)} aria-label="Quit">←</button>
        <h2>LUDO</h2>
      </header>

      {/* turn indicator chips */}
      <div className="turn-chips">
        {activePlayers.map(p => {
          const isTurn = p.id === cp.id;
          const finished = p.pieces.filter(x => x.position.type === 'Finished').length;
          return (
            <div
              key={p.id}
              className={`turn-chip ${isTurn ? 'turn-chip-on' : ''}`}
              style={{ background: isTurn ? PLAYER_COLOR[p.color] : `${PLAYER_COLOR[p.color]}40` }}
            >
              <span>{p.name}</span>
              {finished > 0 && <span className="turn-chip-badge">{finished}/4</span>}
            </div>
          );
        })}
      </div>

      <div className="game-main">
        <LudoBoard gameState={state} movablePieceIds={movablePieceIds} onPieceTap={game.select} />

        <div className="game-controls">
          <Dice
            value={dice}
            active
            color={PLAYER_COLOR[cp.color]}
            label={currentIsBot ? `${cp.name} (bot)` : `${cp.name}'s turn`}
            size={72}
            onClick={game.canRoll ? game.roll : undefined}
          />
          {game.canRoll && <p className="hint-text">Tap the dice to roll</p>}
          {state.turnPhase === 'WAITING_FOR_PIECE_SELECTION' && !currentIsBot && (
            <p className="hint-text">Tap a highlighted pawn to move</p>
          )}
          {state.turnPhase === 'NO_MOVES_AVAILABLE' && <p className="hint-text">No moves — skipping…</p>}
          {currentIsBot && state.turnPhase !== 'GAME_OVER' && <p className="hint-text">🤖 thinking…</p>}
        </div>
      </div>

      {winnerNames && (
        <WinCard
          state={state}
          winnerNames={winnerNames}
          primaryLabel="Play Again"
          onPrimary={game.restart}
          secondaryLabel="Main Menu"
          onSecondary={() => navigate('/')}
        />
      )}

      {confirmQuit && (
        <div className="win-overlay">
          <div className="dialog-card">
            <h3>Quit Game?</h3>
            <p>Your current game will be lost.</p>
            <div className="dialog-actions">
              <button className="btn btn-outline" onClick={() => setConfirmQuit(false)}>Cancel</button>
              <button className="btn btn-danger" onClick={() => navigate('/')}>Quit</button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
