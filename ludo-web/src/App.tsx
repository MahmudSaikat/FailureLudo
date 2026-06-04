import { useEffect, useState, type ReactNode } from 'react';
import { BrowserRouter, Routes, Route, Navigate, useNavigate } from 'react-router-dom';
import { onAuthProfile } from './repositories/authRepository';
import type { UserProfile } from './types/online';
import HomeScreen from './screens/HomeScreen';
import SetupScreen from './screens/SetupScreen';
import LocalGameScreen from './screens/LocalGameScreen';
import AuthScreen from './screens/AuthScreen';
import LobbyScreen from './screens/LobbyScreen';
import WaitingRoomScreen from './screens/WaitingRoomScreen';
import GameBoardScreen from './screens/GameBoardScreen';

function AppRoutes() {
  const [profile, setProfile] = useState<UserProfile | null | undefined>(undefined);
  const navigate = useNavigate();

  useEffect(() => onAuthProfile(p => setProfile(p)), []);

  if (profile === undefined) {
    return <div className="screen-center"><p>Loading…</p></div>;
  }

  const requireAuth = (el: ReactNode) =>
    profile ? el : <Navigate to="/auth" replace />;

  return (
    <Routes>
      {/* Local / offline — no sign-in required */}
      <Route path="/" element={<HomeScreen />} />
      <Route path="/setup" element={<SetupScreen />} />
      <Route path="/local" element={<LocalGameScreen />} />

      {/* Online — gated by auth */}
      <Route
        path="/auth"
        element={profile ? <Navigate to="/lobby" replace /> : <AuthScreen onAuth={() => navigate('/lobby')} />}
      />
      <Route path="/lobby" element={requireAuth(<LobbyScreen />)} />
      <Route path="/waiting/:roomId" element={requireAuth(<WaitingRoomScreen />)} />
      {/* Game route stays open so spectators (no profile) can watch via shared link */}
      <Route path="/game/:roomId" element={<GameBoardScreen />} />

      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}

export default function App() {
  return (
    <BrowserRouter>
      <AppRoutes />
    </BrowserRouter>
  );
}
