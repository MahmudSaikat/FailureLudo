import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { currentProfile } from '../repositories/authRepository';
import { listenToRoom, startGame, leaveRoom } from '../repositories/onlineGameRepository';
import type { GameRoom } from '../types/online';
import PlatformBadge from '../components/PlatformBadge';

const COLOR_LABELS: Record<string, string> = {
  RED: '🔴', BLUE: '🔵', YELLOW: '🟡', GREEN: '🟢', '': '⬜',
};

export default function WaitingRoomScreen() {
  const { roomId } = useParams<{ roomId: string }>();
  const navigate = useNavigate();
  const profile = currentProfile();

  const [room, setRoom] = useState<GameRoom | null>(null);
  const [error, setError] = useState('');

  useEffect(() => {
    if (!roomId) return;
    const unsub = listenToRoom(roomId, r => {
      if (!r) {
        navigate('/lobby');
        return;
      }
      if (r.status === 'IN_PROGRESS') {
        navigate(`/game/${roomId}`);
        return;
      }
      setRoom(r);
    });
    return unsub;
  }, [roomId, navigate]);

  async function handleStart() {
    if (!roomId || !profile) return;
    try {
      await startGame(roomId, profile.uid);
    } catch (e: unknown) {
      setError((e as Error).message ?? 'Could not start game.');
    }
  }

  async function handleLeave() {
    if (!roomId || !profile) return;
    await leaveRoom(roomId, profile.uid);
    navigate('/lobby');
  }

  function handleShare() {
    const gameUrl = `${window.location.origin}/game/${roomId}?spectate=1`;
    navigator.clipboard.writeText(gameUrl);
  }

  if (!room) {
    return <div className="screen-center"><p>Loading room…</p></div>;
  }

  const isHost = room.hostUid === profile?.uid;
  const canStart = isHost && room.players.length >= 2;

  return (
    <div className="screen-center">
      <div className="card waiting-card">
        <h2>Waiting Room</h2>

        <div className="room-code-box">
          <span className="room-code-label">Room Code</span>
          <span className="room-code">{room.roomCode}</span>
          <button className="btn-link" onClick={handleShare}>Copy Link</button>
        </div>

        <p className="player-count-label">
          Players: {room.players.length} / {room.maxPlayers}
        </p>

        <ul className="player-list">
          {room.players.map(p => (
            <li key={p.uid} className="player-row">
              <PlatformBadge platform={p.platform} />
              <span className="player-name">{p.name}</span>
              {p.isHost && <span className="badge badge-host">Host</span>}
              {p.uid === profile?.uid && <span className="badge badge-you">You</span>}
              {p.color && <span className="color-dot">{COLOR_LABELS[p.color]}</span>}
            </li>
          ))}
        </ul>

        {error && <p className="error-text">{error}</p>}

        <div className="waiting-actions">
          {isHost && (
            <button className="btn btn-primary" onClick={handleStart} disabled={!canStart}>
              {canStart ? 'Start Game' : `Waiting for players…`}
            </button>
          )}
          {!isHost && <p className="waiting-hint">Waiting for host to start…</p>}
          <button className="btn btn-secondary" onClick={handleLeave}>Leave Room</button>
        </div>
      </div>
    </div>
  );
}
