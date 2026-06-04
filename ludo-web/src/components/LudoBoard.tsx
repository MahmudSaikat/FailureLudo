import type { GameState, Piece, PlayerColor } from '../engine/types';
import {
  MAIN_TRACK_CELLS,
  HOME_COLUMNS,
  HOME_YARD_SPOTS,
  CENTER,
  SAFE_SQUARES,
  HOME_COLUMN_ENTRY,
  ENTRY_POSITIONS,
} from '../engine/board';

const GRID = 15;
const CELL = 40; // px per grid cell
const SIZE = GRID * CELL;

const COLOR_MAP: Record<PlayerColor, string> = {
  RED: '#e53935',
  BLUE: '#1e88e5',
  YELLOW: '#fdd835',
  GREEN: '#43a047',
};

const COLOR_LIGHT: Record<PlayerColor, string> = {
  RED: '#ffcdd2',
  BLUE: '#bbdefb',
  YELLOW: '#fff9c4',
  GREEN: '#c8e6c9',
};

const ALL_COLORS: PlayerColor[] = ['RED', 'BLUE', 'YELLOW', 'GREEN'];

// Build lookup: "row,col" → main track index
const TRACK_INDEX_MAP = new Map<string, number>(
  MAIN_TRACK_CELLS.map(([r, c], i) => [`${r},${c}`, i]),
);

// Build lookup: "row,col" → {color, step}
const HOME_COL_MAP = new Map<string, { color: PlayerColor; step: number }>();
for (const color of ALL_COLORS) {
  HOME_COLUMNS[color].forEach(([r, c], i) => {
    HOME_COL_MAP.set(`${r},${c}`, { color, step: i + 1 });
  });
}

// Build lookup: "row,col" → color (home yard)
const HOME_YARD_MAP = new Map<string, PlayerColor>();
for (const color of ALL_COLORS) {
  for (const [r, c] of HOME_YARD_SPOTS[color]) {
    HOME_YARD_MAP.set(`${r},${c}`, color);
  }
}

// Entry positions: index → color
const ENTRY_COLOR_MAP = new Map<number, PlayerColor>();
for (const color of ALL_COLORS) {
  ENTRY_COLOR_MAP.set(ENTRY_POSITIONS[color], color);
}

// Home column entry positions: index → color
const HOME_ENTRY_COLOR_MAP = new Map<number, PlayerColor>();
for (const color of ALL_COLORS) {
  HOME_ENTRY_COLOR_MAP.set(HOME_COLUMN_ENTRY[color], color);
}

function getCellBackground(row: number, col: number): string {
  const key = `${row},${col}`;
  const trackIdx = TRACK_INDEX_MAP.get(key);
  const homeColInfo = HOME_COL_MAP.get(key);

  if (row === CENTER[0] && col === CENTER[1]) return '#f5f5f5';
  if (homeColInfo) return COLOR_MAP[homeColInfo.color];
  if (trackIdx !== undefined) {
    const entryColor = ENTRY_COLOR_MAP.get(trackIdx);
    if (entryColor) return COLOR_MAP[entryColor];
    if (SAFE_SQUARES.has(trackIdx)) return '#f5f5f5';
    return '#fafafa';
  }

  // Home yard cells
  for (const color of ALL_COLORS) {
    const yards = HOME_YARD_SPOTS[color];
    for (const [r, c] of yards) {
      if (r === row && c === col) return COLOR_LIGHT[color];
    }
    // Full home area background
    const [hr, hc] = homeAreaRange(color);
    if (row >= hr[0] && row <= hr[1] && col >= hc[0] && col <= hc[1]) {
      return COLOR_LIGHT[color];
    }
  }

  return '#e0e0e0';
}

function homeAreaRange(color: PlayerColor): [[number, number], [number, number]] {
  switch (color) {
    case 'RED':    return [[9, 14], [0, 5]];
    case 'BLUE':   return [[9, 14], [9, 14]];
    case 'YELLOW': return [[0, 5], [9, 14]];
    case 'GREEN':  return [[0, 5], [0, 5]];
  }
}

function piecesAtCell(pieces: Piece[], row: number, col: number): Piece[] {
  return pieces.filter(p => {
    switch (p.position.type) {
      case 'HomeBase': {
        const spots = HOME_YARD_SPOTS[p.color];
        const spot = spots[p.id];
        return spot && spot[0] === row && spot[1] === col;
      }
      case 'MainTrack': {
        const cell = MAIN_TRACK_CELLS[p.position.index];
        return cell && cell[0] === row && cell[1] === col;
      }
      case 'HomeColumn': {
        const cells = HOME_COLUMNS[p.color];
        const cell = cells[p.position.step - 1];
        return cell && cell[0] === row && cell[1] === col;
      }
      case 'Finished':
        return row === CENTER[0] && col === CENTER[1];
    }
  });
}

interface Props {
  gameState: GameState;
  movablePieceIds: Set<string>; // "COLOR:id"
  onPieceTap?: (piece: Piece) => void;
}

export default function LudoBoard({ gameState, movablePieceIds, onPieceTap }: Props) {
  const allPieces = gameState.players.flatMap(p => p.pieces);

  function renderPawns(row: number, col: number) {
    const pieces = piecesAtCell(allPieces, row, col);
    if (pieces.length === 0) return null;

    const cx = CELL / 2;
    const cy = CELL / 2;
    const r = pieces.length === 1 ? 14 : pieces.length === 2 ? 11 : 9;
    const offsets = pawnOffsets(pieces.length);

    return pieces.map((piece, i) => {
      const key = `${piece.color}:${piece.id}`;
      const isMovable = movablePieceIds.has(key);
      const ox = cx + offsets[i][0] * (pieces.length > 1 ? 10 : 0);
      const oy = cy + offsets[i][1] * (pieces.length > 1 ? 10 : 0);

      return (
        <g key={key} style={{ cursor: isMovable ? 'pointer' : 'default' }}
          onClick={isMovable ? () => onPieceTap?.(piece) : undefined}>
          <circle cx={ox} cy={oy} r={r} fill={COLOR_MAP[piece.color]} stroke="#fff" strokeWidth={2} />
          {isMovable && (
            <circle cx={ox} cy={oy} r={r + 3} fill="none" stroke="#FFD700" strokeWidth={2.5}
              style={{ animation: 'pulse 1s ease-in-out infinite' }} />
          )}
          <text x={ox} y={oy + 4} textAnchor="middle" fontSize={9} fill="#fff" fontWeight="bold">
            {piece.id + 1}
          </text>
        </g>
      );
    });
  }

  return (
    <div className="board-container">
      <svg width={SIZE} height={SIZE} viewBox={`0 0 ${SIZE} ${SIZE}`}
        style={{ maxWidth: '100%', height: 'auto' }}>
        <defs>
          <style>{`@keyframes pulse { 0%,100%{opacity:1} 50%{opacity:0.4} }`}</style>
        </defs>

        {/* Grid cells */}
        {Array.from({ length: GRID }, (_, row) =>
          Array.from({ length: GRID }, (_, col) => {
            const bg = getCellBackground(row, col);
            const key = `${row},${col}`;
            const trackIdx = TRACK_INDEX_MAP.get(key);
            const isSafe = trackIdx !== undefined && SAFE_SQUARES.has(trackIdx) && !ENTRY_COLOR_MAP.has(trackIdx);

            return (
              <g key={key} transform={`translate(${col * CELL},${row * CELL})`}>
                <rect width={CELL} height={CELL} fill={bg} stroke="#ccc" strokeWidth={0.5} />
                {isSafe && (
                  <polygon
                    points={`${CELL / 2},4 ${CELL - 4},${CELL / 2} ${CELL / 2},${CELL - 4} 4,${CELL / 2}`}
                    fill="none"
                    stroke="#888"
                    strokeWidth={1}
                  />
                )}
                {renderPawns(row, col)}
              </g>
            );
          })
        )}

        {/* Center star */}
        <g transform={`translate(${CENTER[1] * CELL},${CENTER[0] * CELL})`}>
          <polygon
            points={starPoints(CELL / 2, CELL / 2, CELL * 0.45, CELL * 0.22, 6)}
            fill="#ffb300"
            stroke="#f57f17"
            strokeWidth={1}
          />
        </g>

        {/* Home yard circles */}
        {ALL_COLORS.map(color =>
          HOME_YARD_SPOTS[color].map(([r, c], i) => (
            <g key={`${color}-${i}`} transform={`translate(${c * CELL},${r * CELL})`}>
              <circle cx={CELL / 2} cy={CELL / 2} r={CELL * 0.38}
                fill={COLOR_LIGHT[color]} stroke={COLOR_MAP[color]} strokeWidth={2} />
            </g>
          ))
        )}
      </svg>
    </div>
  );
}

function pawnOffsets(count: number): [number, number][] {
  switch (count) {
    case 1: return [[0, 0]];
    case 2: return [[-1, 0], [1, 0]];
    case 3: return [[-1, -1], [1, -1], [0, 1]];
    default: return [[-1, -1], [1, -1], [-1, 1], [1, 1]];
  }
}

function starPoints(cx: number, cy: number, outerR: number, innerR: number, points: number): string {
  const pts: string[] = [];
  for (let i = 0; i < points * 2; i++) {
    const angle = (i * Math.PI) / points - Math.PI / 2;
    const r = i % 2 === 0 ? outerR : innerR;
    pts.push(`${cx + r * Math.cos(angle)},${cy + r * Math.sin(angle)}`);
  }
  return pts.join(' ');
}
