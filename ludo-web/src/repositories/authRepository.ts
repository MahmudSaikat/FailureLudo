import {
  GoogleAuthProvider,
  signInWithPopup,
  signInAnonymously,
  signOut as firebaseSignOut,
  onAuthStateChanged,
  type User,
} from 'firebase/auth';
import { doc, setDoc } from 'firebase/firestore';
import { auth, db } from '../firebase';
import type { UserProfile } from '../types/online';

function userToProfile(user: User): UserProfile {
  const name = user.isAnonymous
    ? `Guest#${user.uid.slice(-4).toUpperCase()}`
    : (user.displayName?.trim() || 'Player');
  return { uid: user.uid, name, isGuest: user.isAnonymous };
}

async function upsertProfile(user: User): Promise<void> {
  try {
    await setDoc(
      doc(db, 'users', user.uid),
      {
        uid: user.uid,
        name: userToProfile(user).name,
        platform: 'web',
        isGuest: user.isAnonymous,
      },
      { merge: true },
    );
  } catch {
    // best-effort; auth succeeds regardless
  }
}

export async function signInWithGoogle(): Promise<UserProfile> {
  const provider = new GoogleAuthProvider();
  const result = await signInWithPopup(auth, provider);
  await upsertProfile(result.user);
  return userToProfile(result.user);
}

export async function signInAsGuest(): Promise<UserProfile> {
  const result = await signInAnonymously(auth);
  await upsertProfile(result.user);
  return userToProfile(result.user);
}

export async function signOut(): Promise<void> {
  await firebaseSignOut(auth);
}

export function onAuthProfile(callback: (profile: UserProfile | null) => void): () => void {
  return onAuthStateChanged(auth, user => {
    callback(user ? userToProfile(user) : null);
  });
}

export function currentProfile(): UserProfile | null {
  const user = auth.currentUser;
  return user ? userToProfile(user) : null;
}
