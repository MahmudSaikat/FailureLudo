import type { PlayerColor } from './types';

export const MAIN_TRACK_SIZE = 52;
export const HOME_COLUMN_STEPS = 5;

export const ENTRY_POSITIONS: Record<PlayerColor, number> = {
  RED: 0,
  BLUE: 13,
  YELLOW: 26,
  GREEN: 39,
};

export const HOME_COLUMN_ENTRY: Record<PlayerColor, number> = {
  RED: 50,
  BLUE: 11,
  YELLOW: 24,
  GREEN: 37,
};

export const SAFE_SQUARES = new Set([0, 8, 13, 21, 26, 34, 39, 47]);

export function isSafeSquare(index: number): boolean {
  return SAFE_SQUARES.has(index);
}

export function relativePosition(color: PlayerColor, mainTrackIndex: number): number {
  const entry = ENTRY_POSITIONS[color];
  return (mainTrackIndex - entry + MAIN_TRACK_SIZE) % MAIN_TRACK_SIZE;
}

export function stepsToHomeColumnEntry(color: PlayerColor, currentAbsoluteIndex: number): number {
  const maxRelative = relativePosition(color, HOME_COLUMN_ENTRY[color]);
  const currentRelative = relativePosition(color, currentAbsoluteIndex);
  return (maxRelative - currentRelative + MAIN_TRACK_SIZE) % MAIN_TRACK_SIZE;
}

// Visual grid coordinates (15×15)
export const MAIN_TRACK_CELLS: [number, number][] = [
  // Red side (0-4): row 6, cols 1-5
  [6, 1], [6, 2], [6, 3], [6, 4], [6, 5],
  // Up col 6 (5-10): rows 5-0
  [5, 6], [4, 6], [3, 6], [2, 6], [1, 6], [0, 6],
  // Top row (11-12): row 0, cols 7-8
  [0, 7], [0, 8],
  // Down col 8 (13-17): rows 1-5
  [1, 8], [2, 8], [3, 8], [4, 8], [5, 8],
  // Blue side (18-23): row 6, cols 9-14
  [6, 9], [6, 10], [6, 11], [6, 12], [6, 13], [6, 14],
  // Right col 14 (24-25): rows 7-8
  [7, 14], [8, 14],
  // Yellow side (26-30): row 8, cols 13-9
  [8, 13], [8, 12], [8, 11], [8, 10], [8, 9],
  // Down col 8 (31-36): rows 9-14
  [9, 8], [10, 8], [11, 8], [12, 8], [13, 8], [14, 8],
  // Bottom row (37-38): row 14, cols 7-6
  [14, 7], [14, 6],
  // Green side (39-43): rows 13-9, col 6
  [13, 6], [12, 6], [11, 6], [10, 6], [9, 6],
  // Left row 8 (44-49): row 8, cols 5-0
  [8, 5], [8, 4], [8, 3], [8, 2], [8, 1], [8, 0],
  // Left col 0 (50-51): rows 7-6
  [7, 0], [6, 0],
];

export const HOME_COLUMNS: Record<PlayerColor, [number, number][]> = {
  RED:    [[7, 1], [7, 2], [7, 3], [7, 4], [7, 5]],
  BLUE:   [[1, 7], [2, 7], [3, 7], [4, 7], [5, 7]],
  YELLOW: [[7, 13], [7, 12], [7, 11], [7, 10], [7, 9]],
  GREEN:  [[13, 7], [12, 7], [11, 7], [10, 7], [9, 7]],
};

export const HOME_YARD_SPOTS: Record<PlayerColor, [number, number][]> = {
  RED:    [[1, 1], [1, 4], [4, 1], [4, 4]],
  BLUE:   [[1, 10], [1, 13], [4, 10], [4, 13]],
  YELLOW: [[10, 10], [10, 13], [13, 10], [13, 13]],
  GREEN:  [[10, 1], [10, 4], [13, 1], [13, 4]],
};

export const CENTER: [number, number] = [7, 7];

export function colorIndex(color: PlayerColor): number {
  return { RED: 0, BLUE: 1, YELLOW: 2, GREEN: 3 }[color];
}

export function teamIndex(color: PlayerColor): number {
  return color === 'RED' || color === 'YELLOW' ? 0 : 1;
}
