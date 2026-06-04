import type { GameState, Piece, PlayerColor } from '../engine/types';
import {
  MAIN_TRACK_CELLS,
  HOME_COLUMNS,
  HOME_YARD_SPOTS,
  CENTER,
  SAFE_SQUARES,
  ENTRY_POSITIONS,
} from '../engine/board';
import {
  PLAYER_COLOR,
  PLAYER_COLOR_LIGHT,
  BOARD_WHITE,
  BOARD_CREAM,
  SAFE_SQUARE,
  SECONDARY,
} from '../theme';

const GRID = 15;
const CELL = 40; // px per grid cell
const SIZE = GRID * CELL;

const COLOR_MAP = PLAYER_COLOR;
const COLOR_LIGHT = PLAYER_COLOR_LIGHT;

const ALL_COLORS: PlayerColor[] = ['RED', 'BLUE', 'YELLOW', 'GREEN'];

// ── Lookup maps ────────────────────────────────────────────────────────────

const TRACK_INDEX_MAP = new Map<string, number>(
  MAIN_TRACK_CELLS.map(([r, c], i) => [`${r},${c}`, i]),
);

const HOME_COL_MAP = new Map<string, { color: PlayerColor; step: number }>();
for (const color of ALL_COLORS) {
  HOME_COLUMNS[color].forEach(([r, c], i) =>
    HOME_COL_MAP.set(`${r},${c}`, { color, step: i + 1 }),
  );
}

const HOME_YARD_MAP = new Map<string, { color: PlayerColor; slotIndex: number }>();
for (const color of ALL_COLORS) {
  HOME_YARD_SPOTS[color].forEach(([r, c], i) =>
    HOME_YARD_MAP.set(`${r},${c}`, { color, slotIndex: i }),
  );
}

const HOME_AREA_MAP = new Map<string, PlayerColor>();
for (const color of ALL_COLORS) {
  const [[rMin, rMax], [cMin, cMax]] = homeAreaRange(color);
  for (let r = rMin; r <= rMax; r++) {
    for (let c = cMin; c <= cMax; c++) {
      HOME_AREA_MAP.set(`${r},${c}`, color);
    }
  }
}

const ENTRY_COLOR_MAP = new Map<number, PlayerColor>();
for (const color of ALL_COLORS) ENTRY_COLOR_MAP.set(ENTRY_POSITIONS[color], color);

function homeAreaRange(color: PlayerColor): [[number, number], [number, number]] {
  switch (color) {
    case 'RED':    return [[9, 14], [0, 5]];
    case 'BLUE':   return [[9, 14], [9, 14]];
    case 'YELLOW': return [[0, 5],  [9, 14]];
    case 'GREEN':  return [[0, 5],  [0, 5]];
  }
}

function getCellBackground(row: number, col: number): string {
  const key = `${row},${col}`;
  if (row === CENTER[0] && col === CENTER[1]) return BOARD_WHITE;
  const homeColInfo = HOME_COL_MAP.get(key);
  if (homeColInfo) return COLOR_MAP[homeColInfo.color];
  const trackIdx = TRACK_INDEX_MAP.get(key);
  if (trackIdx !== undefined) {
    const entryColor = ENTRY_COLOR_MAP.get(trackIdx);
    if (entryColor) return COLOR_MAP[entryColor];
    return SAFE_SQUARES.has(trackIdx) ? SAFE_SQUARE : BOARD_WHITE;
  }
  const areaColor = HOME_AREA_MAP.get(key);
  if (areaColor) return COLOR_LIGHT[areaColor];
  return BOARD_CREAM;
}

// ── Piece location helpers ─────────────────────────────────────────────────

function piecesAtCell(pieces: Piece[], row: number, col: number): Piece[] {
  return pieces.filter(p => {
    switch (p.position.type) {
      case 'HomeBase': {
        const spot = HOME_YARD_SPOTS[p.color][p.id];
        return spot && spot[0] === row && spot[1] === col;
      }
      case 'MainTrack': {
        const cell = MAIN_TRACK_CELLS[p.position.index];
        return cell && cell[0] === row && cell[1] === col;
      }
      case 'HomeColumn': {
        const cell = HOME_COLUMNS[p.color][p.position.step - 1];
        return cell && cell[0] === row && cell[1] === col;
      }
      case 'Finished':
        return row === CENTER[0] && col === CENTER[1];
    }
  });
}

// ── Pawn layout helpers ────────────────────────────────────────────────────

function pawnOffsets(count: number): [number, number][] {
  switch (count) {
    case 1:  return [[0, 0]];
    case 2:  return [[-1, 0], [1, 0]];
    case 3:  return [[-1, -1], [1, -1], [0, 1]];
    default: return [[-1, -1], [1, -1], [-1, 1], [1, 1]];
  }
}

function starPoints(cx: number, cy: number, outerR: number, innerR: number, pts: number): string {
  const coords: string[] = [];
  for (let i = 0; i < pts * 2; i++) {
    const angle = (i * Math.PI) / pts - Math.PI / 2;
    const r = i % 2 === 0 ? outerR : innerR;
    coords.push(`${cx + r * Math.cos(angle)},${cy + r * Math.sin(angle)}`);
  }
  return coords.join(' ');
}

function darken(_hex: string): string {
  return 'rgba(0,0,0,0.35)';
}

// ── Pawn silhouette (body + head, like the native canvas token) ──────────────
function Pawn({ cx, cy, r, color }: { cx: number; cy: number; r: number; color: string }) {
  return (
    <g pointerEvents="none">
      <ellipse cx={cx} cy={cy + r * 0.62} rx={r * 0.72} ry={r * 0.26} fill="rgba(0,0,0,0.16)" />
      <circle cx={cx} cy={cy + r * 0.18} r={r * 0.82} fill={color} stroke={darken(color)} strokeWidth={1} />
      <circle cx={cx} cy={cy - r * 0.48} r={r * 0.46} fill={color} stroke={darken(color)} strokeWidth={1} />
      <circle cx={cx - r * 0.18} cy={cy - r * 0.58} r={r * 0.15} fill="rgba(255,255,255,0.45)" />
    </g>
  );
}

// ── Component ──────────────────────────────────────────────────────────────

interface Props {
  gameState: GameState;
  movablePieceIds: Set<string>; // "COLOR:id"
  onPieceTap?: (piece: Piece) => void;
}

export default function LudoBoard({ gameState, movablePieceIds, onPieceTap }: Props) {
  const allPieces = gameState.players.flatMap(p => p.pieces);

  return (
    <div className="board-container">
      <svg
        width={SIZE}
        height={SIZE}
        viewBox={`0 0 ${SIZE} ${SIZE}`}
        style={{ maxWidth: '100%', height: 'auto', display: 'block' }}
      >
        <defs>
          <style>{`@keyframes pulse { 0%,100%{opacity:1} 50%{opacity:0.35} }`}</style>
        </defs>

        {/* board backdrop */}
        <rect width={SIZE} height={SIZE} fill={BOARD_CREAM} rx={10} />

        {/* ── Layer 1: cell backgrounds ── */}
        {Array.from({ length: GRID }, (_, row) =>
          Array.from({ length: GRID }, (_, col) => {
            const key = `${row},${col}`;
            const bg = getCellBackground(row, col);
            const trackIdx = TRACK_INDEX_MAP.get(key);
            const isSafe =
              trackIdx !== undefined &&
              SAFE_SQUARES.has(trackIdx) &&
              !ENTRY_COLOR_MAP.has(trackIdx);
            const onTrack = trackIdx !== undefined;
            const yardInfo = HOME_YARD_MAP.get(key);

            return (
              <g key={key} transform={`translate(${col * CELL},${row * CELL})`}>
                <rect
                  width={CELL}
                  height={CELL}
                  fill={bg}
                  stroke="rgba(0,0,0,0.10)"
                  strokeWidth={onTrack ? 1 : 0.5}
                  rx={onTrack ? 3 : 0}
                />
                {isSafe && (
                  <polygon
                    points={starPoints(CELL / 2, CELL / 2, CELL * 0.32, CELL * 0.14, 5)}
                    fill="none"
                    stroke="rgba(0,0,0,0.30)"
                    strokeWidth={1.2}
                  />
                )}
                {/* Home yard slot circle — pawns draw on top */}
                {yardInfo && (
                  <circle
                    cx={CELL / 2}
                    cy={CELL / 2}
                    r={CELL * 0.38}
                    fill="#ffffff"
                    stroke={COLOR_MAP[yardInfo.color]}
                    strokeWidth={2}
                    pointerEvents="none"
                  />
                )}
              </g>
            );
          })
        )}

        {/* ── Layer 2: center (finish) ── */}
        <g transform={`translate(${CENTER[1] * CELL},${CENTER[0] * CELL})`} pointerEvents="none">
          <polygon
            points={starPoints(CELL / 2, CELL / 2, CELL * 0.46, CELL * 0.2, 6)}
            fill={SECONDARY}
            stroke="#b8862f"
            strokeWidth={1.2}
          />
        </g>

        {/* ── Layer 3: pawns (always on top) ── */}
        {Array.from({ length: GRID }, (_, row) =>
          Array.from({ length: GRID }, (_, col) => {
            const pieces = piecesAtCell(allPieces, row, col);
            if (pieces.length === 0) return null;

            const cx = col * CELL + CELL / 2;
            const cy = row * CELL + CELL / 2;
            const spread = pieces.length > 1 ? 9 : 0;
            const offsets = pawnOffsets(pieces.length);
            const r =
              pieces.length === 1 ? 13
              : pieces.length === 2 ? 10.5
              : pieces.length === 3 ? 9
              : 8;

            return pieces.map((piece, i) => {
              const idKey = `${piece.color}:${piece.id}`;
              const isMovable = movablePieceIds.has(idKey);
              const px = cx + offsets[i][0] * spread;
              const py = cy + offsets[i][1] * spread;

              return (
                <g
                  key={`pawn-${idKey}`}
                  style={{ cursor: isMovable ? 'pointer' : 'default' }}
                  onClick={isMovable ? () => onPieceTap?.(piece) : undefined}
                >
                  {isMovable && (
                    <circle
                      cx={px}
                      cy={py}
                      r={r + 6}
                      fill={`${SECONDARY}55`}
                      stroke={SECONDARY}
                      strokeWidth={2.5}
                      style={{ animation: 'pulse 1s ease-in-out infinite' }}
                      pointerEvents="none"
                    />
                  )}
                  <Pawn cx={px} cy={py} r={r} color={COLOR_MAP[piece.color]} />
                </g>
              );
            });
          })
        )}

        {/* ── Layer 4: stack-count badges ── */}
        {Array.from({ length: GRID }, (_, row) =>
          Array.from({ length: GRID }, (_, col) => {
            const pieces = piecesAtCell(allPieces, row, col);
            if (pieces.length < 2) return null;
            const bx = col * CELL + CELL * 0.78;
            const by = row * CELL + CELL * 0.22;
            return (
              <g key={`badge-${row}-${col}`} pointerEvents="none">
                <circle cx={bx} cy={by} r={7} fill="#fff" stroke="rgba(0,0,0,0.4)" strokeWidth={1} />
                <text x={bx} y={by + 3.2} textAnchor="middle" fontSize={9} fontWeight="bold" fill="#1c1b1f">
                  {pieces.length}
                </text>
              </g>
            );
          })
        )}
      </svg>
    </div>
  );
}
