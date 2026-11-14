import React from 'react';

/**
 * 2D Interactive SVG Live Ring Preview with Dual Views:
 * 1. Top/Front View: Circular band with inner diameter, mounted gemstones, and inner engraving arc.
 * 2. Side View: Cylindrical profile showing band width (độ rộng bề mặt), wall thickness (độ dày), and surface texture.
 * Below the views: 2 Interactive Sliders for Thickness (1.0 - 3.0mm) and Surface Width (1.5 - 6.0mm).
 */
export default function RingPreview({
  previewModel,
  onThicknessChange,
  onBandWidthChange,
  onGemstoneMove,
  readOnly = false,
}) {
  const [draggingStoneId, setDraggingStoneId] = React.useState(null);
  const svgRef = React.useRef(null);
  const {
    material,
    shape,
    ringSize = '8',
    circumferenceMm = 48,
    diameterMm = '15.3',
    thicknessMm = 1.8,
    bandWidthMm = 3.5,
    gemstones = [],
    stones = [],
    attachments = [],
    engravingText = '',
    engravingItems = [],
    customImages = [],
    isNeutral = false,
  } = previewModel || {};

  const stonesToRender = stones.length > 0 ? stones : gemstones;

  // Scaled stroke width for Top View based on thickness (1.0mm -> 10px, 3.0mm -> 22px)
  const topStrokeWidth = Math.round(9 + (thicknessMm - 1.0) * 6.5);

  const primaryColor = material?.primary || '#D6D8DC';
  const highlightColor = material?.highlight || '#FFFFFF';
  const shadowColor = material?.shadow || '#9CA3AF';

  const hasMilgrain = attachments.some((a) => a.reference === 'ATT-BEAD-EDGE');
  const hasSatin = attachments.some((a) => a.reference === 'ATT-SATIN-FINISH');
  const hasHeart = attachments.some((a) => a.reference === 'ATT-HEART-EMBOSS');

  // Side view geometry calculation
  // Band height (height in pixels on SVG) represents band width (1.5mm -> 16px, 6.0mm -> 56px)
  const sideBandHeight = Math.round(16 + ((bandWidthMm - 1.5) / 4.5) * 40);
  const sideCenterY = 65;
  const sideTopY = sideCenterY - sideBandHeight / 2;
  const sideBottomY = sideCenterY + sideBandHeight / 2;
  const sideStartX = 45;
  const sideEndX = 255;
  const sideWidth = sideEndX - sideStartX;

  const handlePointerDown = (id, e) => {
    if (readOnly || !onGemstoneMove) return;
    e.stopPropagation();
    e.preventDefault();
    setDraggingStoneId(id);
  };

  const handlePointerMove = (e) => {
    if (!draggingStoneId || !svgRef.current || !onGemstoneMove) return;
    const rect = svgRef.current.getBoundingClientRect();
    if (!rect.width || !rect.height) return;

    // Convert client coordinates to SVG 300x270 viewBox
    const svgX = ((e.clientX - rect.left) / rect.width) * 300;
    const svgY = ((e.clientY - rect.top) / rect.height) * 270;

    // Center is (150, 135)
    const dx = svgX - 150;
    const dy = svgY - 135;

    // Polar angle in degrees (0 to 360)
    let angleRad = Math.atan2(dy, dx);
    let angleDeg = (angleRad * 180) / Math.PI;
    if (angleDeg < 0) angleDeg += 360;

    const roundedAngle = Math.round(angleDeg * 10) / 10;
    onGemstoneMove(draggingStoneId, roundedAngle);
  };

  const handlePointerUp = () => {
    if (draggingStoneId) {
      setDraggingStoneId(null);
    }
  };

  React.useEffect(() => {
    if (!draggingStoneId) return;

    const onMove = (e) => handlePointerMove(e);
    const onUp = () => handlePointerUp();

    window.addEventListener('pointermove', onMove);
    window.addEventListener('pointerup', onUp);
    window.addEventListener('pointercancel', onUp);

    return () => {
      window.removeEventListener('pointermove', onMove);
      window.removeEventListener('pointerup', onUp);
      window.removeEventListener('pointercancel', onUp);
    };
  }, [draggingStoneId, onGemstoneMove]);

  return (
    <div className="ring-preview-dual-wrapper" data-testid="ring-preview-container">
      {/* =========================================================================
          VIEW 1: CỬA SỔ MẶT CHÍNH DIỆN (TOP / FRONT VIEW)
          ========================================================================= */}
      <div className="ring-view-card">
        <div className="ring-view-header">
          <span className="ring-view-badge">Mặt chính diện (Top View)</span>
          <span className="ring-view-meta">
            Size {ringSize} · {circumferenceMm} mm {stonesToRender.length > 0 && `· ${stonesToRender.length} đá`}
          </span>
        </div>

        {!readOnly && stonesToRender.length > 0 && (
          <div className="ring-drag-help-pill" data-testid="gemstone-drag-hint">
            <span>💡 Nhấn giữ và kéo viên đá để bố trí quanh đường tròn nhẫn</span>
          </div>
        )}

        <div className="ring-svg-container">
          <svg
            ref={svgRef}
            viewBox="0 0 300 270"
            className="ring-preview-svg"
            role="img"
            aria-label={`Mô phỏng mặt chính diện nhẫn ${material?.label || ''} dáng ${shape?.label || ''} size ${ringSize}`}
          >
            <defs>
              <linearGradient id="ringTopMetallicGrad" x1="0%" y1="0%" x2="100%" y2="100%">
                <stop offset="0%" stopColor={highlightColor} />
                <stop offset="35%" stopColor={primaryColor} />
                <stop offset="80%" stopColor={shadowColor} />
                <stop offset="100%" stopColor={highlightColor} />
              </linearGradient>

              <radialGradient id="ringTopInnerShadow" cx="50%" cy="50%" r="50%">
                <stop offset="70%" stopColor="transparent" />
                <stop offset="100%" stopColor="rgba(0,0,0,0.18)" />
              </radialGradient>

              <filter id="ringGemGlow" x="-20%" y="-20%" width="140%" height="140%">
                <feGaussianBlur stdDeviation="2" result="blur" />
                <feComposite in="SourceGraphic" in2="blur" operator="over" />
              </filter>
            </defs>

            {/* Ambient Drop Shadow */}
            <ellipse cx="150" cy="235" rx="85" ry="14" fill="rgba(0, 0, 0, 0.08)" />

            {/* Main Outer Contour */}
            <circle
              cx="150"
              cy="135"
              r="85"
              fill="none"
              stroke="url(#ringTopMetallicGrad)"
              strokeWidth={topStrokeWidth}
              strokeLinecap="round"
              strokeDasharray={shape?.style === 'twisted' ? '32 8' : shape?.style === 'single-twist' ? '60 12' : 'none'}
            />

            {/* Inner Ring Depth Shadow */}
            <circle
              cx="150"
              cy="135"
              r={85 - topStrokeWidth / 2}
              fill="none"
              stroke="rgba(0,0,0,0.12)"
              strokeWidth="1.5"
            />

            {/* Ball-peen Hammered Texture (Small Circular Dimples / Lỗ tròn nhỏ) */}
            {(shape?.style === 'ball-peen' || shape?.reference === 'SHP-BALL-PEEN') && (
              <g>
                {[0, 24, 48, 72, 96, 120, 144, 168, 192, 216, 240, 264, 288, 312, 336].map((deg) => {
                  const rad = (deg * Math.PI) / 180;
                  const dotX = 150 + 85 * Math.cos(rad);
                  const dotY = 135 + 85 * Math.sin(rad);
                  return (
                    <circle
                      key={`ball-peen-top-${deg}`}
                      cx={dotX}
                      cy={dotY}
                      r="2.5"
                      fill="rgba(0, 0, 0, 0.16)"
                      stroke="rgba(255, 255, 255, 0.45)"
                      strokeWidth="0.5"
                    />
                  );
                })}
              </g>
            )}

            {/* Wave Accent Silhouette */}
            {shape?.style === 'wave' && (
              <circle
                cx="150"
                cy="135"
                r="85"
                fill="none"
                stroke="rgba(255,255,255,0.4)"
                strokeWidth={topStrokeWidth * 0.4}
                strokeDasharray="16 12"
              />
            )}

            {/* Optional Satin Brushed Finish */}
            {hasSatin && (
              <circle
                cx="150"
                cy="135"
                r="85"
                fill="none"
                stroke="rgba(255, 255, 255, 0.3)"
                strokeWidth={topStrokeWidth}
                strokeDasharray="2 4"
              />
            )}

            {/* Optional Milgrain Beaded Edges */}
            {hasMilgrain && (
              <circle
                cx="150"
                cy="135"
                r={85 + topStrokeWidth / 2 + 1.5}
                fill="none"
                stroke={shadowColor}
                strokeWidth="2"
                strokeDasharray="3 3"
              />
            )}

            {/* Inner Engraving Text Arc */}
            {engravingText && (
              <g>
                <path id="topEngravingArc" d="M 95,150 A 55,55 0 0,0 205,150" fill="none" />
                <text fill="#475569" fontSize="8" fontWeight="600" letterSpacing="1.2">
                  <textPath href="#topEngravingArc" startOffset="50%" textAnchor="middle">
                    {engravingText}
                  </textPath>
                </text>
              </g>
            )}

            {/* Guideline track when dragging a stone */}
            {!readOnly && draggingStoneId && (
              <circle
                cx="150"
                cy="135"
                r="85"
                fill="none"
                stroke="#3B82F6"
                strokeWidth="1.5"
                strokeDasharray="4 4"
                opacity="0.75"
              />
            )}

            {/* Mounted Gemstones (Drag & Drop along the ring circle) */}
            {stonesToRender.map((gem, idx) => {
              const stoneId = gem.id || `gem-${gem.reference || 'stone'}-${idx}`;
              const isDragging = draggingStoneId === stoneId;
              const angleDeg = gem.angleDeg !== undefined
                ? gem.angleDeg
                : gem.placement === 'ACCENT_LEFT'
                  ? 245
                  : gem.placement === 'ACCENT_RIGHT'
                    ? 295
                    : 270;

              const rad = (angleDeg * Math.PI) / 180;
              const cx = gem.cx !== undefined ? gem.cx : Math.round((150 + 85 * Math.cos(rad)) * 10) / 10;
              const cy = gem.cy !== undefined ? gem.cy : Math.round((135 + 85 * Math.sin(rad)) * 10) / 10;
              const size = gem.reference === 'GEM-MOISSANITE-3MM' ? 9.5 : 7.5;

              return (
                <g
                  key={stoneId}
                  id={stoneId}
                  className={`ring-preview-gemstone ${isDragging ? 'is-dragging' : ''}`}
                  filter="url(#ringGemGlow)"
                  style={{
                    cursor: readOnly ? 'default' : isDragging ? 'grabbing' : 'grab',
                    touchAction: 'none',
                  }}
                  onPointerDown={(e) => handlePointerDown(stoneId, e)}
                  tabIndex={readOnly ? -1 : 0}
                  role={readOnly ? 'presentation' : 'button'}
                  aria-label={`Viên đá ${gem.label || ''} tại góc ${Math.round(angleDeg)} độ. Kéo thả để bố trí quanh đai nhẫn`}
                  data-testid={`gemstone-marker-${stoneId}`}
                >
                  {/* Outer mounting prong/base */}
                  <circle
                    cx={cx}
                    cy={cy}
                    r={size + 2.5}
                    fill={shadowColor}
                    stroke={isDragging ? '#2563EB' : 'rgba(0,0,0,0.2)'}
                    strokeWidth={isDragging ? 2.2 : 0.8}
                    strokeDasharray={isDragging ? '3 2' : 'none'}
                  />
                  {/* Gemstone Facet Polygon */}
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
                  {/* Top Table Facet Highlight */}
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
                  {/* Drag halo indicator */}
                  {isDragging && (
                    <circle
                      cx={cx}
                      cy={cy}
                      r={size + 6}
                      fill="none"
                      stroke="#3B82F6"
                      strokeWidth="1.5"
                      strokeDasharray="4 2"
                      className="ring-gem-drag-halo"
                    />
                  )}
                </g>
              );
            })}

            {/* Center Size & Diameter Watermark */}
            <text x="150" y="132" textAnchor="middle" fontSize="14" fontWeight="600" fill="#64748B">
              {isNeutral ? 'Mẫu chuẩn' : `Size ${ringSize}`}
            </text>
            <text x="150" y="150" textAnchor="middle" fontSize="11" fontWeight="500" fill="#94A3B8">
              Ø ~{diameterMm} mm · C {circumferenceMm} mm
            </text>
          </svg>
        </div>
      </div>

      {/* =========================================================================
          VIEW 2: CỬA SỔ MẶT CẠNH / TIẾT DIỆN (SIDE VIEW)
          ========================================================================= */}
      <div className="ring-view-card" data-testid="ring-side-view-card">
        <div className="ring-view-header">
          <span className="ring-view-badge">Mặt cạnh / Tiết diện (Side View)</span>
          <span className="ring-view-meta">
            Rộng {bandWidthMm.toFixed(1)} mm · Dày {thicknessMm.toFixed(1)} mm
          </span>
        </div>

        <div className="ring-svg-container side-view-container">
          <svg
            viewBox="0 0 300 130"
            className="ring-preview-svg ring-side-svg"
            role="img"
            aria-label={`Mô phỏng mặt cạnh nhẫn ${material?.label || ''} bề rộng ${bandWidthMm}mm độ dày ${thicknessMm}mm`}
          >
            <defs>
              {/* Cylindrical horizontal metallic gradient */}
              <linearGradient id="ringSideMetallicGrad" x1="0%" y1="0%" x2="0%" y2="100%">
                <stop offset="0%" stopColor={highlightColor} />
                <stop offset="25%" stopColor={primaryColor} />
                <stop offset="75%" stopColor={shadowColor} />
                <stop offset="100%" stopColor={highlightColor} />
              </linearGradient>

              {/* End-cap depth gradient */}
              <linearGradient id="ringCapGrad" x1="0%" y1="0%" x2="100%" y2="0%">
                <stop offset="0%" stopColor={shadowColor} />
                <stop offset="100%" stopColor={highlightColor} />
              </linearGradient>
            </defs>

            {/* Side Ambient Shadow */}
            <ellipse cx="150" cy={sideBottomY + 12} rx="105" ry="6" fill="rgba(0, 0, 0, 0.07)" />

            {/* Mounted Gemstones Profile in Side View */}
            {stonesToRender.length > 0 && (
              <g filter="url(#ringGemGlow)">
                {stonesToRender.slice(0, 4).map((gem, idx) => {
                  const stoneCount = Math.min(stonesToRender.length, 4);
                  const offsetX = 150 + (idx - (stoneCount - 1) / 2) * 18;
                  return (
                    <g key={`side-gem-${gem.id || idx}`}>
                      {/* Crown Setting Prongs */}
                      <path
                        d={`M ${offsetX - 7},${sideTopY} L ${offsetX - 9},${sideTopY - 14} L ${offsetX + 9},${sideTopY - 14} L ${offsetX + 7},${sideTopY} Z`}
                        fill={shadowColor}
                      />
                      {/* Gemstone Profile */}
                      <polygon
                        points={`
                          ${offsetX},${sideTopY - 18}
                          ${offsetX + 10},${sideTopY - 12}
                          ${offsetX + 7},${sideTopY - 6}
                          ${offsetX - 7},${sideTopY - 6}
                          ${offsetX - 10},${sideTopY - 12}
                        `}
                        fill={gem.fill || '#FFFFFF'}
                        stroke={gem.stroke || '#94A3B8'}
                        strokeWidth="0.8"
                      />
                    </g>
                  );
                })}
              </g>
            )}

            {/* Main Side Cylindrical Band */}
            {shape?.style === 'wave' ? (
              /* Sinusoidal Wave Silhouette */
              <path
                d={`
                  M ${sideStartX},${sideTopY}
                  Q ${sideStartX + sideWidth * 0.25},${sideTopY - 6} ${sideStartX + sideWidth * 0.5},${sideTopY}
                  T ${sideEndX},${sideTopY}
                  L ${sideEndX},${sideBottomY}
                  Q ${sideStartX + sideWidth * 0.75},${sideBottomY + 6} ${sideStartX + sideWidth * 0.5},${sideBottomY}
                  T ${sideStartX},${sideBottomY}
                  Z
                `}
                fill="url(#ringSideMetallicGrad)"
                stroke={shadowColor}
                strokeWidth="1.2"
              />
            ) : shape?.style === 'twisted' || shape?.style === 'single-twist' ? (
              /* Twisted Ridge Lines across Band */
              <g>
                <rect
                  x={sideStartX}
                  y={sideTopY}
                  width={sideWidth}
                  height={sideBandHeight}
                  rx="3"
                  fill="url(#ringSideMetallicGrad)"
                  stroke={shadowColor}
                  strokeWidth="1.2"
                />
                {/* Diagonal twist highlights */}
                <line
                  x1={sideStartX + 50}
                  y1={sideTopY}
                  x2={sideStartX + 90}
                  y2={sideBottomY}
                  stroke={highlightColor}
                  strokeWidth="2.5"
                  opacity="0.7"
                />
                <line
                  x1={sideStartX + 110}
                  y1={sideTopY}
                  x2={sideStartX + 150}
                  y2={sideBottomY}
                  stroke={shadowColor}
                  strokeWidth="2"
                  opacity="0.8"
                />
              </g>
            ) : (
              /* Classic / Flat / Comfort Band */
              <rect
                x={sideStartX}
                y={sideTopY}
                width={sideWidth}
                height={sideBandHeight}
                rx={shape?.style === 'round' ? '4' : '2'}
                fill="url(#ringSideMetallicGrad)"
                stroke={shadowColor}
                strokeWidth="1.2"
              />
            )}

            {/* Ball-peen Texture (Búa bi tạo các lỗ tròn nhỏ) across Side Profile */}
            {(shape?.style === 'ball-peen' || shape?.reference === 'SHP-BALL-PEEN') && (
              <g>
                {[60, 85, 110, 135, 160, 185, 210, 235].map((x, colIdx) => {
                  const yOffsets = sideBandHeight > 30 ? [-0.25, 0.25] : [0];
                  return yOffsets.map((yFactor, rowIdx) => {
                    const dotY = sideCenterY + yFactor * (sideBandHeight * 0.6);
                    return (
                      <circle
                        key={`ball-peen-side-${colIdx}-${rowIdx}`}
                        cx={x + (rowIdx % 2 ? 10 : 0)}
                        cy={dotY}
                        r="3"
                        fill="rgba(0, 0, 0, 0.22)"
                        stroke="rgba(255, 255, 255, 0.5)"
                        strokeWidth="0.6"
                      />
                    );
                  });
                })}
              </g>
            )}

            {/* Faceted Hammered Lines */}
            {(shape?.style === 'hammered' || shape?.reference === 'SHP-HAMMERED') && (
              <g stroke="rgba(255, 255, 255, 0.4)" strokeWidth="1">
                {[75, 105, 135, 165, 195, 225].map((x) => (
                  <line key={`facet-${x}`} x1={x} y1={sideTopY} x2={x - 8} y2={sideBottomY} />
                ))}
              </g>
            )}

            {/* Satin Finish Hairline Strokes */}
            {hasSatin && (
              <g stroke="rgba(255, 255, 255, 0.25)" strokeWidth="0.8">
                <line x1={sideStartX + 10} y1={sideCenterY - 4} x2={sideEndX - 10} y2={sideCenterY - 4} />
                <line x1={sideStartX + 10} y1={sideCenterY + 4} x2={sideEndX - 10} y2={sideCenterY + 4} />
              </g>
            )}

            {/* Milgrain Beaded Edges on Top & Bottom Rims */}
            {hasMilgrain && (
              <g stroke={shadowColor} strokeWidth="2.5" strokeDasharray="3 3">
                <line x1={sideStartX} y1={sideTopY + 1} x2={sideEndX} y2={sideTopY + 1} />
                <line x1={sideStartX} y1={sideBottomY - 1} x2={sideEndX} y2={sideBottomY - 1} />
              </g>
            )}

            {/* Heart Stamp Emboss */}
            {hasHeart && (
              <path
                d={`M 150,${sideCenterY + 4} A 3.5,3.5 0 0,0 157,${sideCenterY - 2} A 3.5,3.5 0 0,0 150,${sideCenterY + 1} A 3.5,3.5 0 0,0 143,${sideCenterY - 2} A 3.5,3.5 0 0,0 150,${sideCenterY + 4} Z`}
                fill={shadowColor}
                opacity="0.75"
              />
            )}

            {/* Outer Band Engraving Text & Marks */}
            {engravingItems.some((it) => it.placement === 'OUTER_BAND') && (
              <text
                x="150"
                y={sideCenterY + 3}
                textAnchor="middle"
                fill="#1e293b"
                fontSize="8"
                fontWeight="700"
                letterSpacing="1.2"
                opacity="0.8"
              >
                {engravingItems
                  .filter((it) => it.placement === 'OUTER_BAND')
                  .map((it) => it.content)
                  .join(' • ')}
              </text>
            )}

            {/* Outer Band Custom Image Mark */}
            {customImages.some((img) => img.placement === 'OUTER_BAND') && (
              <g transform={`translate(${sideStartX + 18}, ${sideCenterY - 6})`}>
                <rect width="12" height="12" rx="2" fill="none" stroke="#ef4444" strokeWidth="1" strokeDasharray="1 1" />
                <text x="6" y="9" textAnchor="middle" fontSize="7" fill="#ef4444" fontWeight="bold">
                  📷
                </text>
              </g>
            )}

            {/* Dimension Callout - Độ rộng bề mặt nhẫn (Height Indicator) */}
            <g>
              <line x1="270" y1={sideTopY} x2="270" y2={sideBottomY} stroke="#0284C7" strokeWidth="1.5" />
              <line x1="266" y1={sideTopY} x2="274" y2={sideTopY} stroke="#0284C7" strokeWidth="1.5" />
              <line x1="266" y1={sideBottomY} x2="274" y2={sideBottomY} stroke="#0284C7" strokeWidth="1.5" />
              <text x="277" y={sideCenterY + 4} fontSize="10" fontWeight="700" fill="#0284C7">
                {bandWidthMm.toFixed(1)}mm
              </text>
            </g>

            {/* Dimension Callout - Độ dày thành nhẫn (Thickness Indicator) */}
            <g>
              <line x1="30" y1={sideTopY} x2="30" y2={sideTopY + topStrokeWidth * 0.9} stroke="#D97706" strokeWidth="1.5" />
              <line x1="26" y1={sideTopY} x2="34" y2={sideTopY} stroke="#D97706" strokeWidth="1.5" />
              <line x1="26" y1={sideTopY + topStrokeWidth * 0.9} x2="34" y2={sideTopY + topStrokeWidth * 0.9} stroke="#D97706" strokeWidth="1.5" />
              <text x="24" y={sideTopY + (topStrokeWidth * 0.9) / 2 + 4} fontSize="9" fontWeight="700" fill="#D97706" textAnchor="end">
                {thicknessMm.toFixed(1)}mm
              </text>
            </g>
          </svg>
        </div>
      </div>

      {/* =========================================================================
          2 SLIDERS ĐIỀU CHỈNH ĐỘ DÀY & ĐỘ RỘNG NHẪN
          ========================================================================= */}
      <div className="ring-dimension-sliders-card" data-testid="ring-dimension-sliders">
        <h4 className="ring-dimension-title">Tùy chỉnh thông số kích thước</h4>

        {/* Slider 1: Độ dày nhẫn (1.0mm - 3.0mm, step 0.1) */}
        <div className="ring-slider-group">
          <div className="ring-slider-meta">
            <label htmlFor="thickness-range" className="ring-slider-label">
              Độ dày nhẫn (Thickness)
            </label>
            <span className="ring-slider-value" data-testid="thickness-value">
              {thicknessMm.toFixed(1)} mm
            </span>
          </div>
          <input
            id="thickness-range"
            type="range"
            min="1.0"
            max="3.0"
            step="0.1"
            value={thicknessMm}
            disabled={readOnly}
            onChange={(e) => onThicknessChange?.(parseFloat(e.target.value))}
            className="ring-range-input"
            aria-label="Độ dày nhẫn mm"
            data-testid="thickness-slider"
          />
          <div className="ring-slider-ticks">
            <span>1.0 mm (Mỏng)</span>
            <span>2.0 mm (Tiêu chuẩn)</span>
            <span>3.0 mm (Dày dặn)</span>
          </div>
        </div>

        {/* Slider 2: Độ rộng bề mặt nhẫn (1.5mm - 6.0mm, step 0.1) */}
        <div className="ring-slider-group" style={{ marginTop: '1rem' }}>
          <div className="ring-slider-meta">
            <label htmlFor="bandwidth-range" className="ring-slider-label">
              Độ rộng bề mặt (Band Width)
            </label>
            <span className="ring-slider-value" data-testid="bandwidth-value">
              {bandWidthMm.toFixed(1)} mm
            </span>
          </div>
          <input
            id="bandwidth-range"
            type="range"
            min="1.5"
            max="6.0"
            step="0.1"
            value={bandWidthMm}
            disabled={readOnly}
            onChange={(e) => onBandWidthChange?.(parseFloat(e.target.value))}
            className="ring-range-input"
            aria-label="Độ rộng bề mặt mm"
            data-testid="bandwidth-slider"
          />
          <div className="ring-slider-ticks">
            <span>1.5 mm (Thanh mảnh)</span>
            <span>3.5 mm (Vừa vặn)</span>
            <span>6.0 mm (Bản to)</span>
          </div>
        </div>
      </div>

      {/* =========================================================================
          BẢN XEM TRƯỚC KHẮC LASER 160x160 (KHI CÓ ẢNH UPLOAD)
          ========================================================================= */}
      {customImages.length > 0 && (
        <div
          style={{
            marginTop: '1rem',
            padding: '1rem',
            borderRadius: '10px',
            border: '1px solid #cbd5e1',
            background: '#ffffff',
            boxShadow: '0 1px 3px rgba(0,0,0,0.05)',
          }}
          data-testid="laser-preview-gallery"
        >
          <h4 style={{ margin: '0 0 0.5rem 0', fontSize: '0.9rem', color: '#1e293b' }}>
            Bản xem trước khắc laser (160px × 160px)
          </h4>
          <div style={{ display: 'flex', gap: '1rem', flexWrap: 'wrap' }}>
            {customImages.map((img) => (
              <div key={img.id} style={{ textAlign: 'center' }}>
                <div
                  style={{
                    width: '160px',
                    height: '160px',
                    border: '2px solid #0f172a',
                    borderRadius: '6px',
                    background: '#ffffff',
                    overflow: 'hidden',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                  }}
                >
                  {img.previewDataUrl ? (
                    <img
                      src={img.previewDataUrl}
                      alt="B&W Laser Engraving Preview"
                      style={{ width: '160px', height: '160px', imageRendering: 'pixelated' }}
                    />
                  ) : (
                    <span style={{ fontSize: '0.75rem', color: '#94a3b8' }}>160 × 160 B&W</span>
                  )}
                </div>
                <span style={{ fontSize: '0.75rem', color: '#64748b', display: 'block', marginTop: '0.25rem' }}>
                  {img.placement === 'OUTER_BAND' ? 'Mặt ngoài (+100k)' : 'Mặt trong (+100k)'}
                </span>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}
