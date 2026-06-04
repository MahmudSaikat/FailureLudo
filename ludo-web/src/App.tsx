import { useEffect, useState } from 'react';
import { BrowserRouter, Routes, Route, Navigate, useLocation, useNavigate } from 'react-router-dom';
import { onAuthProfile } from './repositories/authRepository';
import type { UserProfile } from './types/online';
import AuthScreen from './screens/AuthScreen';
import LobbyScreen from './screens/LobbyScreen';
import WaitingRoomScreen from './screens/WaitingRoomScreen';
import GameBoardScreen from './screens/GameBoardScreen';

function AppRoutes() {
  const [profile, setProfile] = useState<UserProfile | null | undefined>(undefined);
  const navigate = useNavigate();
  const location = useLocation();

  useEffect(() => {
    const unsub = onAuthProfile(p => setProfile(p));
    return unsub;
  }, []);

  if (profile === undefined) {
    return <div className="screen-center"><p>Loading…</p></div>;
  }

  if (!profile && location.pathname !== '/') {
    return <Navigate to="/" replace />;
  }

  return (
    <Routes>
      <Route
        path="/"
        element={
          profile
            ? <Navigate to="/lobby" replace />
            : <AuthScreen onAuth={() => navigate('/lobby')} />
        }
      />
      <Route path="/lobby" element={<LobbyScreen />} />
      <Route path="/waiting/:roomId" element={<WaitingRoomScreen />} />
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
