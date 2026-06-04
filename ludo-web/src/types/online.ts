export interface UserProfile {
  uid: string;
  name: string;
  isGuest: boolean;
}

export type RoomStatus = 'WAITING' | 'IN_PROGRESS' | 'FINISHED';

export interface RoomPlayer {
  uid: string;
  name: string;
  platform: 'android' | 'web';
  color: string; // "RED" | "BLUE" | "YELLOW" | "GREEN" | ""
  isHost: boolean;
}

export interface GameRoom {
  id: string;
  roomCode: string;
  status: RoomStatus;
  maxPlayers: number;
  players: RoomPlayer[];
  hostUid: string;
  createdAt?: number;
}

export interface OnlineMove {
  index: number;
  actorId: number;
  movingPlayerId: number;
  diceValue: number;
  pieceId: number; // -1 = no movable pieces
  deferHomeEntry: boolean;
}
