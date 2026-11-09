import React from 'react';

/**
 * 2D Interactive SVG Live Ring Preview
 * Renders dynamically based on PreviewModel derived locally from draft state.
 */
export default function RingPreview({ previewModel }) {
  const {
    material,
    shape,
    ringSize,
    gemstones = [],
    attachments = [],
    engravingText = '',
    isNeutral = false,
  } = previewModel || {};

  const strokeWidth = shape?.strokeWidth || 14;
  const primaryColor = material?.primary || '#D6D8DC';
  const highlightColor = material?.highlight || '#FFFFFF';
  const shadowColor = material?.shadow || '#9CA3AF';

  const hasMilgrain = attachments.some((a) => a.reference === 'ATT-BEAD-EDGE');
  const hasSatin = attachments.some((a) => a.reference === 'ATT-SATIN-FINISH');

  return (
    <div className="ring-svg-container" data-testid="ring-preview-container">
      <svg
        viewBox="0 0 300 300"
        className="ring-preview-svg"
        role="img"
        aria-label={`Mô phỏng nhẫn ${material?.label || ''} dáng ${shape?.label || ''} size ${ringSize}`}
      >
        <defs>
          {/* Metallic gradient for ring band */}
          <linearGradient id="ringMetallicGrad" x1="0%" y1="0%" x2="100%" y2="100%">
            <stop offset="0%" stopColor={highlightColor} />
            <stop offset="45%" stopColor={primaryColor} />
            <stop offset="85%" stopColor={shadowColor} />
            <stop offset="100%" stopColor={highlightColor} />
          </linearGradient>

          {/* Inner ring shadow */}
          <radialGradient id="ringInnerShadow" cx="50%" cy="50%" r="50%">
            <stop offset="70%" stopColor="transparent" />
            <stop offset="100%" stopColor="rgba(0,0,0,0.18)" />
          </radialGradient>

          {/* Filter for subtle glow on gemstones */}
          <filter id="gemGlow" x="-20%" y="-20%" width="140%" height="140%">
            <feGaussianBlur stdDeviation="2" result="blur" />
            <feComposite in="SourceGraphic" in2="blur" operator="over" />
          </filter>
        </defs>

        {/* Ambient Ring Drop Shadow */}
        <ellipse cx="150" cy="245" rx="85" ry="14" fill="rgba(0, 0, 0, 0.08)" />

        {/* Main Ring Outer Contour */}
        <circle
          cx="150"
          cy="150"
          r="90"
          fill="none"
          stroke="url(#ringMetallicGrad)"
          strokeWidth={strokeWidth}
          strokeLinecap="round"
          strokeDasharray={shape?.style === 'twisted' ? '32 8' : 'none'}
        />

        {/* Inner Ring Depth Shadow */}
        <circle
          cx="150"
          cy="150"
          r={90 - strokeWidth / 2}
          fill="none"
          stroke="rgba(0,0,0,0.1)"
          strokeWidth="1.5"
        />

        {/* Optional Satin Finish Brushed Texture Overlay */}
        {hasSatin && (
          <circle
            cx="150"
            cy="150"
            r="90"
            fill="none"
            stroke="rgba(255, 255, 255, 0.25)"
            strokeWidth={strokeWidth}
            strokeDasharray="2 4"
          />
        )}

        {/* Optional Milgrain Beaded Edge Detail */}
        {hasMilgrain && (
          <circle
            cx="150"
            cy="150"
            r={90 + strokeWidth / 2 + 1.5}
            fill="none"
            stroke={shadowColor}
            strokeWidth="2"
            strokeDasharray="3 3"
          />
        )}

        {/* Inner Engraving Text Arc (Rendered along subtle inner curve) */}
        {engravingText && (
          <g>
            <path id="engravingArc" d="M 90,165 A 60,60 0 0,0 210,165" fill="none" />
            <text fill="#475569" fontSize="8" fontWeight="600" letterSpacing="1.2">
              <textPath href="#engravingArc" startOffset="50%" textAnchor="middle">
                {engravingText}
              </textPath>
            </text>
          </g>
        )}

        {/* Gemstones Rendering */}
        {gemstones.map((gem, idx) => {
          let cx = 150;
          let cy = 150 - 90; // Top Center
          let size = 9;

          if (gem.placement === 'ACCENT_LEFT') {
            cx = 150 - 30;
            cy = 150 - 85;
            size = 6;
          } else if (gem.placement === 'ACCENT_RIGHT') {
            cx = 150 + 30;
            cy = 150 - 85;
            size = 6;
          } else if (gem.placement === 'HALO') {
            size = 7;
          }

          return (
            <g key={`${gem.reference}-${idx}`} filter="url(#gemGlow)">
              {/* Gemstone Prongs / Mounting Base */}
              <circle cx={cx} cy={cy} r={size + 2.5} fill={shadowColor} />
              {/* Gemstone Outer Facet */}
              <polygon
                points={`
                  ${cx},${cy - size} 
                  ${cx + size},${cy - size / 3} 
                  ${cx + size * 0.7},${cy + size} 
                  ${cx - size * 0.7},${cy + size} 
                  ${cx - size},${cy - size / 3}
                `}
                fill={gem.fill || '#FFFFFF'}
                stroke={gem.stroke || '#94A3B8'}
                strokeWidth="0.8"
              />
              {/* Gemstone Brilliant Highlight Specular */}
              <polygon
                points={`
                  ${cx},${cy - size * 0.8} 
                  ${cx + size * 0.4},${cy - size * 0.2} 
                  ${cx},${cy + size * 0.4} 
                  ${cx - size * 0.4},${cy - size * 0.2}
                `}
                fill="#FFFFFF"
                opacity="0.85"
              />
            </g>
          );
        })}

        {/* Center Size Indicator Watermark */}
        <text
          x="150"
          y="155"
          textAnchor="middle"
          fontSize="14"
          fontWeight="600"
          fill="#94A3B8"
        >
          {isNeutral ? 'Mẫu chuẩn' : `Size ${ringSize}`}
        </text>
      </svg>
    </div>
  );
}

