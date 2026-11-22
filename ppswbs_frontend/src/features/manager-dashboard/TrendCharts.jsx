import React, { useCallback, useEffect, useRef, useState } from 'react';
import { useLanguage } from '../../shared/i18n/I18nProvider';

/** Picks the Vietnamese or the English wording; the dashboard follows the VI/EN switch (Session 2026-11-22). */
export function useL() {
  const { language } = useLanguage();
  return useCallback((vi, en) => (language === 'en' ? en : vi), [language]);
}

/**
 * FE-10 trends (Session 2026-11-16): small hand-drawn SVG charts in the dashboard palette, no chart library.
 * A null value is a source that could not be read and is drawn as a gap, never as zero.
 */

export const UNIT_LABEL = { HOUR: ['giờ', 'hour'], DAY: ['ngày', 'day'], MONTH: ['tháng', 'month'] };

const WIDTH = 640; // starting width; the chart then takes its real width so the text is never stretched
const HEIGHT = 220;
const PAD = { top: 16, right: 12, bottom: 28, left: 52 };

/** A rounded axis maximum and four ticks. Counts get whole-number ticks: no "0,25 orders". */
function scaleMax(max, whole = false) {
  if (!max || max <= 0) return whole ? 4 : 1;
  const exp = 10 ** Math.floor(Math.log10(max));
  let step = [1, 2, 2.5, 5, 10].map(m => m * exp).find(s => s * 4 >= max) || 10 * exp;
  if (whole) step = Math.max(1, Math.ceil(step));
  return step * 4;
}

export function compact(value, en = false) {
  const n = Number(value);
  if (!Number.isFinite(n)) return '—';
  const locale = en ? 'en-US' : 'vi-VN';
  const short = number => number.toLocaleString(locale, { maximumFractionDigits: 1 });
  if (Math.abs(n) >= 1e9) return en ? `${short(n / 1e9)}B` : `${short(n / 1e9)} tỷ`;
  if (Math.abs(n) >= 1e6) return en ? `${short(n / 1e6)}M` : `${short(n / 1e6)} tr`;
  if (Math.abs(n) >= 1e3) return `${short(n / 1e3)}k`;
  return n.toLocaleString(locale);
}

/**
 * @param points  [{ label }]
 * @param series  [{ key, label, color, values: (number|null)[] }]
 * @param kind    'bar' draws the series side by side; 'line' draws lines with dots
 * @param format  formats a value in the tooltip
 */
export function TrendChart({ title, points, series, kind = 'line', format = v => v, empty }) {
  const [hover, setHover] = useState(null);
  const L = useL();
  const en = L(false, true);
  // Draw at the box's real width (Session 2026-11-22): a stretched SVG made the axis text wide and blurry.
  const bodyRef = useRef(null);
  const [width, setWidth] = useState(WIDTH);
  useEffect(() => {
    const element = bodyRef.current;
    if (!element || typeof ResizeObserver === 'undefined') return undefined;
    const observer = new ResizeObserver(([entry]) => {
      const next = Math.round(entry.contentRect.width);
      if (next > 0) setWidth(next);
    });
    observer.observe(element);
    return () => observer.disconnect();
  }, []);
  const n = points.length;
  const all = series.flatMap(s => s.values).filter(v => v != null).map(Number);
  const hasData = all.some(v => v > 0);
  const whole = all.every(v => Number.isInteger(v));
  const max = scaleMax(Math.max(0, ...all), whole);
  // The value is written on each bar and point (Thinh, 2026-11-22); a long period labels only each series' peak.
  const labelAll = n <= 16;
  const peak = s => s.values.reduce((best, v, i) => (v != null && Number(v) > 0 && (best < 0 || Number(v) > Number(s.values[best])) ? i : best), -1);
  const innerW = width - PAD.left - PAD.right;
  const innerH = HEIGHT - PAD.top - PAD.bottom;
  const slot = n ? innerW / n : innerW;
  const x = i => PAD.left + slot * i + slot / 2;
  const y = v => PAD.top + innerH - (Number(v) / max) * innerH;
  // Show at most ~12 labels so a month of days stays readable.
  const every = Math.max(1, Math.ceil(n / Math.max(4, Math.floor(innerW / 64))));
  const barW = Math.max(2, Math.min(28, (slot * 0.7) / series.length));

  return <figure className="md-chart">
    <figcaption className="md-chart-head">
      <span className="md-chart-title">{title}</span>
      <span className="md-legend">{series.map(s => <span key={s.key}><i style={{ background: s.color }} />{s.label}</span>)}</span>
    </figcaption>
    <div className="md-chart-body" ref={bodyRef}>
      <svg viewBox={`0 0 ${width} ${HEIGHT}`} role="img" aria-label={title} preserveAspectRatio="none"
        onMouseLeave={() => setHover(null)}>
        {[0, 1, 2, 3, 4].map(t => {
          const value = (max / 4) * t;
          return <g key={t}>
            <line x1={PAD.left} x2={width - PAD.right} y1={y(value)} y2={y(value)} className="md-grid" />
            <text x={PAD.left - 8} y={y(value) + 4} className="md-axis" textAnchor="end">{compact(value, en)}</text>
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
        {series.map((s, si) => {
          const top = peak(s);
          return s.values.map((v, i) => {
            if (v == null || Number(v) <= 0 || (!labelAll && i !== top)) return null;
            const cx = kind === 'bar' ? x(i) - (barW * series.length) / 2 + barW * si + barW / 2 : x(i);
            return <text key={`v${s.key}${i}`} x={cx} y={Math.max(PAD.top + 9, y(v) - 6)} className="md-value"
              textAnchor="middle" fill={s.color}>{compact(v, en)}</text>;
          });
        })}
        {points.map((p, i) => <rect key={`h${i}`} x={PAD.left + slot * i} y={PAD.top} width={slot} height={innerH}
          fill="transparent" onMouseEnter={() => setHover(i)} />)}
      </svg>
      {!hasData && <p className="md-chart-empty">{empty || L('Chưa có dữ liệu trong kỳ này.', 'No data in this period.')}</p>}
      {hover != null && <div className="md-tooltip" role="status"
        style={{ left: `${Math.min(80, Math.max(4, ((x(hover) - PAD.left) / innerW) * 100))}%` }}>
        <strong>{points[hover].label}</strong>
        {series.map(s => <span key={s.key}><i style={{ background: s.color }} />{s.label}: <b>{s.values[hover] == null ? '—' : format(s.values[hover])}</b></span>)}
      </div>}
    </div>
  </figure>;
}

/** Change against the previous period of the same length. */
export function Change({ current, previous, inverse = false, suffix }) {
  const L = useL();
  const vs = suffix ?? L('so với kỳ trước', 'vs previous period');
  if (current == null || previous == null) return <small className="md-change">—</small>;
  const cur = Number(current);
  const prev = Number(previous);
  if (prev === 0) return <small className="md-change">{cur === 0 ? L(`Không đổi ${vs}`, `No change ${vs}`) : L('Kỳ trước chưa có', 'No previous data')}</small>;
  const pct = ((cur - prev) / prev) * 100;
  if (Math.abs(pct) < 0.05) return <small className="md-change">{L(`Không đổi ${vs}`, `No change ${vs}`)}</small>;
  const up = pct > 0;
  const good = inverse ? !up : up;
  return <small className={`md-change ${good ? 'good' : 'bad'}`}>
    {up ? '▲' : '▼'} {Math.abs(pct).toLocaleString(L('vi-VN', 'en-US'), { maximumFractionDigits: 1 })}% {vs}
  </small>;
}

export function rate(part, whole) {
  if (part == null || whole == null || Number(whole) === 0) return null;
  return (Number(part) * 100) / Number(whole);
}
