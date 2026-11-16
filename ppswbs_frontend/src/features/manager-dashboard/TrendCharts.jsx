import React, { useState } from 'react';

/**
 * FE-10 trends (Session 2026-11-16): small hand-drawn SVG charts in the dashboard palette, no chart library.
 * A null value is a source that could not be read and is drawn as a gap, never as zero.
 */

export const UNIT_LABEL = { HOUR: 'giờ', DAY: 'ngày', MONTH: 'tháng' };

const WIDTH = 640;
const HEIGHT = 220;
const PAD = { top: 16, right: 12, bottom: 28, left: 52 };

/** A rounded axis maximum and four ticks. */
function scaleMax(max) {
  if (!max || max <= 0) return 1;
  const exp = 10 ** Math.floor(Math.log10(max));
  const step = [1, 2, 2.5, 5, 10].map(m => m * exp).find(s => s * 4 >= max) || 10 * exp;
  return step * 4;
}

export function compact(value) {
  const n = Number(value);
  if (!Number.isFinite(n)) return '—';
  if (Math.abs(n) >= 1e9) return `${(n / 1e9).toLocaleString('vi-VN', { maximumFractionDigits: 1 })} tỷ`;
  if (Math.abs(n) >= 1e6) return `${(n / 1e6).toLocaleString('vi-VN', { maximumFractionDigits: 1 })} tr`;
  if (Math.abs(n) >= 1e3) return `${(n / 1e3).toLocaleString('vi-VN', { maximumFractionDigits: 1 })}k`;
  return n.toLocaleString('vi-VN');
}

/**
 * @param points  [{ label }]
 * @param series  [{ key, label, color, values: (number|null)[] }]
 * @param kind    'bar' draws the series side by side; 'line' draws lines with dots
 * @param format  formats a value in the tooltip
 */
export function TrendChart({ title, points, series, kind = 'line', format = v => v, empty }) {
  const [hover, setHover] = useState(null);
  const n = points.length;
  const all = series.flatMap(s => s.values).filter(v => v != null).map(Number);
  const hasData = all.some(v => v > 0);
  const max = scaleMax(Math.max(0, ...all));
  const innerW = WIDTH - PAD.left - PAD.right;
  const innerH = HEIGHT - PAD.top - PAD.bottom;
  const slot = n ? innerW / n : innerW;
  const x = i => PAD.left + slot * i + slot / 2;
  const y = v => PAD.top + innerH - (Number(v) / max) * innerH;
  // Show at most ~12 labels so a month of days stays readable.
  const every = Math.max(1, Math.ceil(n / 12));
  const barW = Math.max(2, Math.min(28, (slot * 0.7) / series.length));

  return <figure className="md-chart">
    <figcaption className="md-chart-head">
      <span className="md-chart-title">{title}</span>
      <span className="md-legend">{series.map(s => <span key={s.key}><i style={{ background: s.color }} />{s.label}</span>)}</span>
    </figcaption>
    <div className="md-chart-body">
      <svg viewBox={`0 0 ${WIDTH} ${HEIGHT}`} role="img" aria-label={title} preserveAspectRatio="none"
        onMouseLeave={() => setHover(null)}>
        {[0, 1, 2, 3, 4].map(t => {
          const value = (max / 4) * t;
          return <g key={t}>
            <line x1={PAD.left} x2={WIDTH - PAD.right} y1={y(value)} y2={y(value)} className="md-grid" />
            <text x={PAD.left - 8} y={y(value) + 4} className="md-axis" textAnchor="end">{compact(value)}</text>
          </g>;
        })}
        {points.map((p, i) => i % every === 0 && <text key={p.label + i} x={x(i)} y={HEIGHT - 8} className="md-axis" textAnchor="middle">{p.label}</text>)}
        {hover != null && <rect x={PAD.left + slot * hover} y={PAD.top} width={slot} height={innerH} className="md-hover-band" />}
        {kind === 'bar' && series.map((s, si) => s.values.map((v, i) => v == null || Number(v) <= 0 ? null :
          <rect key={`${s.key}${i}`} x={x(i) - (barW * series.length) / 2 + barW * si} y={y(v)} width={barW}
            height={Math.max(0, PAD.top + innerH - y(v))} rx="2" fill={s.color} />))}
        {kind === 'line' && series.map(s => {
          // A null breaks the line: unknown is never drawn as zero.
          const segments = [];
          let current = [];
          s.values.forEach((v, i) => {
            if (v == null) { if (current.length) segments.push(current); current = []; } else current.push(`${x(i)},${y(v)}`);
          });
          if (current.length) segments.push(current);
          return <g key={s.key}>
            {segments.map((seg, k) => <polyline key={k} points={seg.join(' ')} fill="none" stroke={s.color} strokeWidth="2" strokeLinejoin="round" />)}
            {n <= 31 && s.values.map((v, i) => v == null ? null : <circle key={i} cx={x(i)} cy={y(v)} r={hover === i ? 4 : 2.5} fill={s.color} />)}
          </g>;
        })}
        {points.map((p, i) => <rect key={`h${i}`} x={PAD.left + slot * i} y={PAD.top} width={slot} height={innerH}
          fill="transparent" onMouseEnter={() => setHover(i)} />)}
      </svg>
      {!hasData && <p className="md-chart-empty">{empty || 'Chưa có dữ liệu trong kỳ này.'}</p>}
      {hover != null && <div className="md-tooltip" role="status"
        style={{ left: `${Math.min(80, Math.max(4, ((x(hover) - PAD.left) / innerW) * 100))}%` }}>
        <strong>{points[hover].label}</strong>
        {series.map(s => <span key={s.key}><i style={{ background: s.color }} />{s.label}: <b>{s.values[hover] == null ? '—' : format(s.values[hover])}</b></span>)}
      </div>}
    </div>
  </figure>;
}

/** Change against the previous period of the same length. */
export function Change({ current, previous, inverse = false, suffix = 'so với kỳ trước' }) {
  if (current == null || previous == null) return <small className="md-change">—</small>;
  const cur = Number(current);
  const prev = Number(previous);
  if (prev === 0) return <small className="md-change">{cur === 0 ? `Không đổi ${suffix}` : 'Kỳ trước chưa có'}</small>;
  const pct = ((cur - prev) / prev) * 100;
  if (Math.abs(pct) < 0.05) return <small className="md-change">Không đổi {suffix}</small>;
  const up = pct > 0;
  const good = inverse ? !up : up;
  return <small className={`md-change ${good ? 'good' : 'bad'}`}>
    {up ? '▲' : '▼'} {Math.abs(pct).toLocaleString('vi-VN', { maximumFractionDigits: 1 })}% {suffix}
  </small>;
}

export function rate(part, whole) {
  if (part == null || whole == null || Number(whole) === 0) return null;
  return (Number(part) * 100) / Number(whole);
}
