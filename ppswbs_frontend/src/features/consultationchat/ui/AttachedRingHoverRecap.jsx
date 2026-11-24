import React, { useState } from 'react';
import { MATERIAL_PALETTES, SHAPE_STROKES } from '../../ringdesignconfiguration/domain/designPreview';

export default function AttachedRingHoverRecap({ conversation, children }) {
  const [isHovered, setIsHovered] = useState(false);

  if (!conversation || conversation.contextType !== 'WORKSHOP_RING_DESIGN') {
    return children;
  }

  const snapshot = conversation.contextSnapshot || {};
  const code = snapshot.displayCode || conversation.contextReference || 'Thiết kế nhẫn';
  const name = snapshot.displayName || 'Thiết kế nhẫn tùy chỉnh';
  const version = snapshot.designVersion || conversation.contextVersion || 1;
  const summary = snapshot.preview?.summary || '';

  // Extract or parse material, shape, size from summary or snapshot
  let matCode = 'MAT-SILVER-925';
  let shapeCode = 'SHP-CLASSIC-ROUND';
  let sizeVal = '8';

  if (summary) {
    const matMatch = summary.match(/Chất liệu:\s*([A-Za-z0-9_-]+)/i);
    if (matMatch) matCode = matMatch[1].trim();
    const shapeMatch = summary.match(/Dáng:\s*([A-Za-z0-9_-]+)/i);
    if (shapeMatch) shapeCode = shapeMatch[1].trim();
    const sizeMatch = summary.match(/Kích thước:\s*([A-Za-z0-9_-]+)/i);
    if (sizeMatch) sizeVal = sizeMatch[1].trim();
  }

  const material = MATERIAL_PALETTES[matCode] || MATERIAL_PALETTES['MAT-SILVER-925'];
  const shape = SHAPE_STROKES[shapeCode] || SHAPE_STROKES['SHP-CLASSIC-ROUND'];

  const matLabel =
    matCode === 'MAT-SILVER-925' ? 'Bạc Ý 925' :
    matCode === 'MAT-COPPER' ? 'Đồng nghệ thuật' :
    matCode === 'MAT-STAINLESS-STEEL' ? 'Thép không gỉ 316L' :
    matCode === 'MAT-GOLD-14K' ? 'Vàng 14K' :
    matCode === 'MAT-ROSE-GOLD-14K' ? 'Vàng hồng 14K' :
    matCode === 'MAT-WHITE-GOLD-14K' ? 'Vàng trắng 14K' : matCode;

  const shapeLabel =
    shapeCode === 'SHP-CLASSIC-ROUND' ? 'Tròn cổ điển' :
    shapeCode === 'SHP-COMFORT-FIT' ? 'Viền vát Comfort' :
    shapeCode === 'SHP-TWISTED' ? 'Xoắn nghệ thuật' :
    shapeCode === 'SHP-HAMMERED' ? 'Vân búa Hammered' :
    shapeCode === 'SHP-SINGLE-TWIST' ? 'Xoắn đơn Single-twist' : shapeCode;

  const primaryColor = material.primary || '#D6D8DC';
  const highlightColor = material.highlight || '#FFFFFF';
  const shadowColor = material.shadow || '#9CA3AF';

  return (
    <div
      className="attached-ring-hover-wrapper"
      style={{ position: 'relative', display: 'inline-flex', alignItems: 'center' }}
      onMouseEnter={() => setIsHovered(true)}
      onMouseLeave={() => setIsHovered(false)}
      data-testid="attached-ring-hover-trigger"
    >
      <div style={{ cursor: 'pointer' }}>{children}</div>

      <div
        role="tooltip"
        data-testid="attached-ring-recap-popover"
        className="attached-ring-popover-content"
        style={{
          display: isHovered ? 'block' : 'none',
          position: 'absolute',
          top: 'calc(100% + 8px)',
          left: 0,
          zIndex: 1000,
          width: '280px',
          backgroundColor: '#ffffff',
          borderRadius: '10px',
          boxShadow: '0 10px 25px -5px rgba(0, 0, 0, 0.15), 0 8px 10px -6px rgba(0, 0, 0, 0.1)',
          border: '1px solid #e2e8f0',
          padding: '1rem',
          pointerEvents: 'none',
          textAlign: 'left',
        }}
      >
          {/* Popover Header */}
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.75rem', borderBottom: '1px solid #f1f5f9', paddingBottom: '0.5rem' }}>
            <span style={{ fontWeight: 700, fontSize: '0.9rem', color: '#0f172a', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', maxWidth: '180px' }} title={name}>
              💍 {name}
            </span>
            <span style={{ fontSize: '0.75rem', fontWeight: 600, background: '#fef3c7', color: '#92400e', padding: '0.15rem 0.45rem', borderRadius: '4px' }}>
              v{version}
            </span>
          </div>

          <div style={{ fontSize: '0.8rem', color: '#64748b', marginBottom: '0.5rem' }}>
            Mã: <code style={{ color: '#0369a1', fontWeight: 600 }}>{code}</code>
          </div>

          {/* SVG Ring Graphic */}
          <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', padding: '0.5rem 0', background: 'radial-gradient(circle, #f8fafc 0%, #f1f5f9 100%)', borderRadius: '8px', marginBottom: '0.75rem' }}>
            <svg width="120" height="120" viewBox="0 0 160 160">
              <defs>
                <linearGradient id="popoverRingGrad" x1="0%" y1="0%" x2="100%" y2="100%">
                  <stop offset="0%" stopColor={highlightColor} />
                  <stop offset="40%" stopColor={primaryColor} />
                  <stop offset="100%" stopColor={shadowColor} />
                </linearGradient>
                <filter id="popoverShadow" x="-20%" y="-20%" width="140%" height="140%">
                  <feDropShadow dx="0" dy="4" stdDeviation="4" floodColor="#000000" floodOpacity="0.18" />
                </filter>
              </defs>

              {/* Main Circular Ring Band */}
              <circle
                cx="80"
                cy="80"
                r="50"
                fill="none"
                stroke="url(#popoverRingGrad)"
                strokeWidth={shape.strokeWidth || 15}
                filter="url(#popoverShadow)"
              />

              {/* Inner highlight circle */}
              <circle
                cx="80"
                cy="80"
                r="44"
                fill="none"
                stroke="#ffffff"
                strokeWidth="1.5"
                opacity="0.4"
              />

              {/* Mounted Gemstone at top */}
              <g transform="translate(80, 24)">
                <circle cx="0" cy="0" r="7" fill="#ffffff" stroke="#cbd5e1" strokeWidth="1" />
                <polygon points="0,-6 5,-2 3,5 -3,5 -5,-2" fill="#e0f2fe" opacity="0.9" />
                <polygon points="0,-6 2,-1 -2,-1" fill="#ffffff" />
                <circle cx="-1.5" cy="-2" r="1.5" fill="#ffffff" />
              </g>

              {/* Center Finger Hole Info */}
              <text x="80" y="84" textAnchor="middle" fontSize="12" fill="#64748b" fontWeight="600">
                Size {sizeVal}
              </text>
            </svg>
          </div>

          {/* Specs List */}
          <div style={{ display: 'grid', gridTemplateColumns: '1fr', gap: '0.35rem', fontSize: '0.8rem', color: '#334155' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between' }}>
              <span style={{ color: '#64748b' }}>Chất liệu:</span>
              <strong style={{ color: '#0f172a' }}>{matLabel}</strong>
            </div>
            <div style={{ display: 'flex', justifyContent: 'space-between' }}>
              <span style={{ color: '#64748b' }}>Dáng nhẫn:</span>
              <strong style={{ color: '#0f172a' }}>{shapeLabel}</strong>
            </div>
            <div style={{ display: 'flex', justifyContent: 'space-between' }}>
              <span style={{ color: '#64748b' }}>Kích thước:</span>
              <strong style={{ color: '#0f172a' }}>Ni tay {sizeVal}</strong>
            </div>
          </div>

          <div style={{ marginTop: '0.65rem', paddingTop: '0.5rem', borderTop: '1px dashed #e2e8f0', fontSize: '0.72rem', color: '#16a34a', display: 'flex', alignItems: 'center', gap: '0.3rem' }}>
            <span>🔒</span>
            <span>Thiết kế lưu trữ vĩnh viễn cho phòng tư vấn</span>
          </div>
        </div>
    </div>
  );
}
