import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { currentProfile, signOut } from '../repositories/authRepository';
import { createRoom, joinRoom } from '../repositories/onlineGameRepository';

export default function LobbyScreen() {
  const navigate = useNavigate();
  const profile = currentProfile();

  const [tab, setTab] = useState<'create' | 'join'>('create');
  const [maxPlayers, setMaxPlayers] = useState(4);
  const [roomCode, setRoomCode] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  async function handleCreate() {
    if (!profile) return;
    setLoading(true);
    setError('');
    try {
      const room = await createRoom(profile, maxPlayers);
      navigate(`/waiting/${room.id}`);
    } catch (e: unknown) {
      setError((e as Error).message ?? 'Could not create room.');
    } finally {
      setLoading(false);
    }
  }

  async function handleJoin() {
    if (!profile || !roomCode.trim()) {
      setError('Enter a room code.');
      return;
    }
    setLoading(true);
    setError('');
    try {
      const room = await joinRoom(roomCode, profile);
      navigate(`/waiting/${room.id}`);
    } catch (e: unknown) {
      setError((e as Error).message ?? 'Could not join room.');
    } finally {
      setLoading(false);
    }
  }

  async function handleSignOut() {
    await signOut();
    navigate('/');
  }

  return (
    <div className="screen-center">
      <div className="card">
        <div className="lobby-header">
          <h2>Play Online</h2>
          <div className="user-info">
            <span className="username">{profile?.name}</span>
            {profile?.isGuest && <span className="badge badge-guest">Guest</span>}
            <button className="btn-link" onClick={handleSignOut}>Sign out</button>
          </div>
        </div>

        <div className="tab-row">
          <button
            className={`tab-btn ${tab === 'create' ? 'active' : ''}`}
            onClick={() => { setTab('create'); setError(''); }}
          >
            Create Room
          </button>
          <button
            className={`tab-btn ${tab === 'join' ? 'active' : ''}`}
            onClick={() => { setTab('join'); setError(''); }}
          >
            Join Room
          </button>
        </div>

        {tab === 'create' && (
          <div className="tab-content">
            <label className="field-label">Number of players</label>
            <div className="player-count-row">
              {[2, 3, 4].map(n => (
                <button
                  key={n}
                  className={`count-btn ${maxPlayers === n ? 'active' : ''}`}
                  onClick={() => setMaxPlayers(n)}
                >
                  {n}
                </button>
              ))}
            </div>
            <button className="btn btn-primary" onClick={handleCreate} disabled={loading}>
              {loading ? 'Creating…' : 'Create Room'}
            </button>
          </div>
        )}

        {tab === 'join' && (
          <div className="tab-content">
            <label className="field-label">Room code</label>
            <input
              className="text-input"
              value={roomCode}
              onChange={e => setRoomCode(e.target.value.toUpperCase())}
              placeholder="e.g. XK92P3"
              maxLength={6}
            />
            <button className="btn btn-primary" onClick={handleJoin} disabled={loading}>
              {loading ? 'Joining…' : 'Join Room'}
            </button>
          </div>
        )}

        {error && <p className="error-text">{error}</p>}
      </div>
    </div>
  );
}
