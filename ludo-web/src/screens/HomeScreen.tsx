import { useNavigate } from 'react-router-dom';
import { currentProfile, signOut } from '../repositories/authRepository';
import { PLAYER_COLOR } from '../theme';
import type { PlayerColor } from '../engine/types';

const PATCHES: PlayerColor[] = ['RED', 'BLUE', 'YELLOW', 'GREEN'];

export default function HomeScreen() {
  const navigate = useNavigate();
  const profile = currentProfile();

  const playOnline = () => navigate(profile ? '/lobby' : '/auth');

  return (
    <div className="home-screen">
      {profile && (
        <div className="home-userchip">
          <span>{profile.name}</span>
          {profile.isGuest && <span className="badge badge-guest">Guest</span>}
          <button className="btn-link" onClick={async () => { await signOut(); navigate('/'); }}>
            Sign out
          </button>
        </div>
      )}

      <div className="home-center">
        <div className="home-title">
          <div className="home-dice-emoji">🎲</div>
          <h1 className="home-logo">LUDO</h1>
          <p className="home-subtitle">Failure Edition</p>
        </div>

        <div className="home-actions">
          <button className="btn btn-primary btn-tall" onClick={() => navigate('/setup')}>
            New Game
          </button>
          <button className="btn btn-outline btn-tall" onClick={playOnline}>
            Play Online
          </button>
        </div>

        <div className="home-patches">
          {PATCHES.map(c => (
            <span key={c} className="home-patch" style={{ background: PLAYER_COLOR[c] }} />
          ))}
        </div>
      </div>
    </div>
  );
}
