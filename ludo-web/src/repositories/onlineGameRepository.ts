import {
  collection,
  doc,
  addDoc,
  getDoc,
  setDoc,
  query,
  where,
  limit,
  getDocs,
  onSnapshot,
  orderBy,
  runTransaction,
  serverTimestamp,
  arrayUnion,
  type Unsubscribe,
} from 'firebase/firestore';
import { db } from '../firebase';
import type { GameRoom, OnlineMove, RoomPlayer, RoomStatus, UserProfile } from '../types/online';

const rooms = collection(db, 'rooms');

function docToRoom(id: string, data: Record<string, unknown>): GameRoom {
  const rawPlayers = (data.players as Record<string, unknown>[] | undefined) ?? [];
  const players: RoomPlayer[] = rawPlayers.map(p => ({
    uid: (p.uid as string) ?? '',
    name: (p.name as string) ?? 'Player',
    platform: ((p.platform as string) ?? 'web') as 'android' | 'web',
    color: (p.color as string) ?? '',
    isHost: (p.isHost as boolean) ?? false,
  }));
  return {
    id,
    roomCode: (data.roomCode as string) ?? '',
    status: ((data.status as string) ?? 'WAITING') as RoomStatus,
    maxPlayers: (data.maxPlayers as number) ?? 4,
    players,
    hostUid: (data.hostUid as string) ?? '',
    createdAt: (data.createdAt as number) ?? 0,
  };
}

const CODE_CHARS = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
function randomCode(): string {
  return Array.from({ length: 6 }, () => CODE_CHARS[Math.floor(Math.random() * CODE_CHARS.length)]).join('');
}

async function generateUniqueCode(): Promise<string> {
  for (let i = 0; i < 10; i++) {
    const code = randomCode();
    const snap = await getDocs(query(rooms, where('roomCode', '==', code), where('status', '==', 'WAITING'), limit(1)));
    if (snap.empty) return code;
  }
  return `R${Date.now().toString().slice(-5)}`;
}

function playerMap(profile: UserProfile, color: string, isHost: boolean): Record<string, unknown> {
  return {
    uid: profile.uid,
    name: profile.name,
    platform: 'web',
    color,
    isHost,
  };
}

export async function createRoom(host: UserProfile, maxPlayers: number): Promise<GameRoom> {
  const code = await generateUniqueCode();
  const ref = await addDoc(rooms, {
    roomCode: code,
    status: 'WAITING',
    maxPlayers,
    hostUid: host.uid,
    players: [playerMap(host, '', true)],
    moves: [],
    createdAt: serverTimestamp(),
  });
  return {
    id: ref.id,
    roomCode: code,
    status: 'WAITING',
    maxPlayers,
    players: [{ uid: host.uid, name: host.name, platform: 'web', color: '', isHost: true }],
    hostUid: host.uid,
  };
}

export async function joinRoom(code: string, player: UserProfile): Promise<GameRoom> {
  const snap = await getDocs(query(rooms, where('roomCode', '==', code.toUpperCase().trim()), limit(5)));
  const docSnap = snap.docs.find(d => d.data().status === 'WAITING');
  if (!docSnap) throw new Error('Room not found or already started.');

  const roomId = docSnap.id;
  await runTransaction(db, async tx => {
    const ref = doc(rooms, roomId);
    const fresh = await tx.get(ref);
    const data = fresh.data()!;
    const currentPlayers = (data.players as unknown[]) ?? [];
    if (currentPlayers.length >= (data.maxPlayers as number)) throw new Error('Room is full.');
    const alreadyJoined = (currentPlayers as Record<string, unknown>[]).some(p => p.uid === player.uid);
    if (!alreadyJoined) {
      tx.update(ref, { players: arrayUnion(playerMap(player, '', false)) });
    }
  });

  const updated = await getDoc(doc(rooms, roomId));
  return docToRoom(roomId, updated.data()!);
}

export async function fetchRoom(roomId: string): Promise<GameRoom> {
  const snap = await getDoc(doc(rooms, roomId));
  if (!snap.exists()) throw new Error('Room not found');
  return docToRoom(roomId, snap.data());
}

export function listenToRoom(roomId: string, onChange: (room: GameRoom | null) => void): Unsubscribe {
  return onSnapshot(doc(rooms, roomId), snap => {
    onChange(snap.exists() ? docToRoom(snap.id, snap.data()) : null);
  });
}

export async function startGame(roomId: string, hostUid: string): Promise<void> {
  const COLORS = ['RED', 'BLUE', 'YELLOW', 'GREEN'];
  await runTransaction(db, async tx => {
    const ref = doc(rooms, roomId);
    const snap = await tx.get(ref);
    const data = snap.data()!;
    if (data.hostUid !== hostUid) throw new Error('Only the host can start the game.');
    const rawPlayers = (data.players as Record<string, unknown>[]) ?? [];
    const updated = rawPlayers.map((p, i) => ({ ...p, color: COLORS[i] ?? '' }));
    tx.update(ref, { players: updated, status: 'IN_PROGRESS' });
  });
}

export async function leaveRoom(roomId: string, uid: string): Promise<void> {
  try {
    await runTransaction(db, async tx => {
      const ref = doc(rooms, roomId);
      const snap = await tx.get(ref);
      if (!snap.exists()) return;
      const data = snap.data();
      const players = (data.players as Record<string, unknown>[]).filter(p => p.uid !== uid);
      if (players.length === 0) {
        tx.delete(ref);
      } else {
        const updates: Record<string, unknown> = { players };
        if (data.hostUid === uid) {
          updates.hostUid = players[0].uid;
          updates.players = players.map((p, i) => ({ ...p, isHost: i === 0 }));
        }
        tx.update(ref, updates);
      }
    });
  } catch {
    // best-effort
  }
}

export async function writeMove(roomId: string, move: OnlineMove): Promise<void> {
  await setDoc(doc(rooms, roomId, 'moves', String(move.index)), {
    index: move.index,
    actorId: move.actorId,
    movingPlayerId: move.movingPlayerId,
    diceValue: move.diceValue,
    pieceId: move.pieceId,
    deferHomeEntry: move.deferHomeEntry,
  });
}

export function listenToMoves(roomId: string, onChange: (moves: OnlineMove[]) => void): Unsubscribe {
  return onSnapshot(
    query(collection(rooms, roomId, 'moves'), orderBy('index')),
    snap => {
      const moves: OnlineMove[] = snap.docs.map(d => {
        const data = d.data();
        return {
          index: data.index as number,
          actorId: data.actorId as number,
          movingPlayerId: data.movingPlayerId as number,
          diceValue: data.diceValue as number,
          pieceId: data.pieceId as number,
          deferHomeEntry: (data.deferHomeEntry as boolean) ?? false,
        };
      });
      onChange(moves);
    },
  );
}
