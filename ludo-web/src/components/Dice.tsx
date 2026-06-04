import { useEffect, useRef, useState } from 'react';

// 3×3 dot grid positions (1-indexed) for each die value.
const DOTS: Record<number, [number, number][]> = {
  1: [[2, 2]],
  2: [[1, 1], [3, 3]],
  3: [[1, 1], [2, 2], [3, 3]],
  4: [[1, 1], [1, 3], [3, 1], [3, 3]],
  5: [[1, 1], [1, 3], [2, 2], [3, 1], [3, 3]],
  6: [[1, 1], [2, 1], [3, 1], [1, 3], [2, 3], [3, 3]],
};

interface Props {
  value: number | null;
  active?: boolean;
  label?: string;
  color?: string; // accent (player colour) for the active ring/label
  size?: number;
  onClick?: () => void;
}

export default function Dice({ value, active = false, label, color, size = 64, onClick }: Props) {
  const [bounce, setBounce] = useState(false);
  const prev = useRef<number | null>(value);

  useEffect(() => {
    if (value !== null && value !== prev.current) {
      setBounce(true);
      const t = setTimeout(() => setBounce(false), 320);
      prev.current = value;
      return () => clearTimeout(t);
    }
    prev.current = value;
  }, [value]);

  const dots = value && DOTS[value] ? DOTS[value] : [];
  const accent = color ?? '#5C3D2E';

  return (
    <div className="dice-wrap">
      {label && (
        <span className="dice-label" style={{ color: active ? accent : '#9a8f80' }}>
          {label}
        </span>
      )}
      <button
        type="button"
        className={`dice ${active ? 'dice-active' : ''} ${bounce ? 'dice-bounce' : ''}`}
        onClick={onClick}
        disabled={!onClick}
        style={{
          width: size,
          height: size,
          borderColor: active ? accent : '#9a8f80',
          boxShadow: active ? `0 0 0 4px ${accent}33` : 'none',
          cursor: onClick ? 'pointer' : 'default',
        }}
      >
        {value === null ? (
          <span className="dice-empty">?</span>
        ) : (
          <div className="dice-face">
            {Array.from({ length: 9 }, (_, i) => {
              const r = Math.floor(i / 3) + 1;
              const c = (i % 3) + 1;
              const filled = dots.some(([dr, dc]) => dr === r && dc === c);
              return <span key={i} className={`pip ${filled ? 'pip-on' : ''}`} />;
            })}
          </div>
        )}
      </button>
    </div>
  );
}
