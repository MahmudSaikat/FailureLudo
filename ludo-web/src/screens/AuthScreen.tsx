import { useState } from 'react';
import { signInWithGoogle, signInAsGuest } from '../repositories/authRepository';

interface Props {
  onAuth: () => void;
}

export default function AuthScreen({ onAuth }: Props) {
  const [loading, setLoading] = useState<'google' | 'guest' | null>(null);
  const [error, setError] = useState('');

  async function handleGoogle() {
    setLoading('google');
    setError('');
    try {
      await signInWithGoogle();
      onAuth();
    } catch (e: unknown) {
      setError((e as Error).message ?? 'Sign-in failed.');
    } finally {
      setLoading(null);
    }
  }

  async function handleGuest() {
    setLoading('guest');
    setError('');
    try {
      await signInAsGuest();
      onAuth();
    } catch (e: unknown) {
      setError((e as Error).message ?? 'Guest sign-in failed.');
    } finally {
      setLoading(null);
    }
  }

  return (
    <div className="screen-center">
      <div className="card auth-card">
        <h1 className="logo-text">FailureLudo</h1>
        <p className="subtitle">Online Multiplayer</p>

        <button
          className="btn btn-primary"
          onClick={handleGoogle}
          disabled={loading !== null}
        >
          {loading === 'google' ? 'Signing in…' : 'Continue with Google'}
        </button>

        <button
          className="btn btn-secondary"
          onClick={handleGuest}
          disabled={loading !== null}
        >
          {loading === 'guest' ? 'Joining…' : 'Play as Guest'}
        </button>

        {error && <p className="error-text">{error}</p>}
      </div>
    </div>
  );
}
