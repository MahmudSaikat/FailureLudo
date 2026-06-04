import type { PlayerColor } from './engine/types';

// ── Player piece colours (match app/.../ui/theme/Color.kt exactly) ───────────
export const PLAYER_COLOR: Record<PlayerColor, string> = {
  RED: '#D32F2F',
  BLUE: '#1976D2',
  YELLOW: '#F9A825',
  GREEN: '#388E3C',
};

export const PLAYER_COLOR_LIGHT: Record<PlayerColor, string> = {
  RED: '#FFCDD2',
  BLUE: '#BBDEFB',
  YELLOW: '#FFF9C4',
  GREEN: '#C8E6C9',
};

// ── Board neutrals ───────────────────────────────────────────────────────────
export const BOARD_WHITE = '#FAFAFA';
export const BOARD_CREAM = '#F5F0E8';
export const SAFE_SQUARE = '#E0E0E0';

// ── Material theme colours ───────────────────────────────────────────────────
export const PRIMARY = '#5C3D2E'; // warm brown
export const ON_PRIMARY = '#FFFFFF';
export const SECONDARY = '#D4A044'; // golden
export const BACKGROUND = '#F5EDDC'; // cream
export const SURFACE = '#FFFFFF';
export const ON_SURFACE = '#1C1B1F';

// Default seat → colour assignment, mirroring native defaultPlayerColors().
export const DEFAULT_SEAT_COLOR: Record<PlayerColor, string> = { ...PLAYER_COLOR };

// Selectable colour palette from GameSetupScreen.kt (Color.hsv → hex).
export const SELECTABLE_COLORS: string[] = [
  '#DB3030', // hsv(0, .78, .86)
  '#E69D2E', // hsv(30, .80, .90)
  '#EBD53B', // hsv(50, .75, .92)
  '#9BD640', // hsv(85, .70, .84)
  '#3FD14E', // hsv(120, .70, .82)
  '#31C7A6', // hsv(165, .75, .78)
  '#39A8E6', // hsv(200, .75, .90)
  '#3C5FE0', // hsv(235, .73, .88)
  '#9B3FD6', // hsv(275, .70, .84)
  '#D63FC7', // hsv(310, .70, .84)
  '#E03C6E', // hsv(340, .72, .88)
  '#B88A5C', // hsv(15, .55, .72)
];
