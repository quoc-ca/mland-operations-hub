import React, { useRef, useState, useEffect, useCallback } from 'react';

/**
 * Converts a 160x160 slice of an image to high-contrast black-and-white (monochrome laser engraving)
 * with adjustable contrast and sharpness.
 */
export function generateBwLaserPreview(imgElement, cropX, cropY, frameSize = 160, contrast = 50, sharpness = 50) {
  const canvas = document.createElement('canvas');
  canvas.width = frameSize;
  canvas.height = frameSize;
  const ctx = canvas.getContext('2d');
  if (!ctx) return '';

  // White background
  ctx.fillStyle = '#FFFFFF';
  ctx.fillRect(0, 0, frameSize, frameSize);

  // Compute scale between natural image dimensions and displayed DOM dimensions
  const dispW = (imgElement.clientWidth && imgElement.clientWidth > 0) ? imgElement.clientWidth : (imgElement.naturalWidth || frameSize);
  const dispH = (imgElement.clientHeight && imgElement.clientHeight > 0) ? imgElement.clientHeight : (imgElement.naturalHeight || frameSize);
  const scaleX = (imgElement.naturalWidth && imgElement.naturalWidth > 0) ? imgElement.naturalWidth / dispW : 1;
  const scaleY = (imgElement.naturalHeight && imgElement.naturalHeight > 0) ? imgElement.naturalHeight / dispH : 1;

  const sourceX = Math.max(0, cropX * scaleX);
  const sourceY = Math.max(0, cropY * scaleY);
  const sourceW = frameSize * scaleX;
  const sourceH = frameSize * scaleY;

  // Draw the cropped slice
  try {
    ctx.drawImage(imgElement, sourceX, sourceY, sourceW, sourceH, 0, 0, frameSize, frameSize);
  } catch {
    return '';
  }

  // Grayscale thresholding to pure black and white with contrast & sharpness
  try {
    const imgData = ctx.getImageData(0, 0, frameSize, frameSize);
    const data = imgData.data;

    // 1. Contrast adjustment factor (0..100, default 50)
    const cVal = (Math.max(0, Math.min(100, contrast)) - 50) * 2; // -100..100
    const factor = (259 * (cVal + 255)) / (255 * (259 - cVal));

    for (let i = 0; i < data.length; i += 4) {
      const a = data[i + 3];
      if (a < 64) {
        data[i] = 255;
        data[i + 1] = 255;
        data[i + 2] = 255;
        data[i + 3] = 255;
      } else {
        let r = factor * (data[i] - 128) + 128;
        let g = factor * (data[i + 1] - 128) + 128;
        let b = factor * (data[i + 2] - 128) + 128;
        r = Math.max(0, Math.min(255, r));
        g = Math.max(0, Math.min(255, g));
        b = Math.max(0, Math.min(255, b));
        const luminance = 0.299 * r + 0.587 * g + 0.114 * b;
        data[i] = luminance;
        data[i + 1] = luminance;
        data[i + 2] = luminance;
      }
    }

    // 2. Sharpness adjustment via 3x3 convolution (0..100, default 50)
    const sAmount = (Math.max(0, Math.min(100, sharpness)) - 50) / 50; // -1.0 to 1.0
    const grayBuffer = new Uint8ClampedArray(frameSize * frameSize);
    for (let i = 0; i < frameSize * frameSize; i++) {
      grayBuffer[i] = data[i * 4];
    }

    const threshold = 140;
    for (let y = 0; y < frameSize; y++) {
      for (let x = 0; x < frameSize; x++) {
        const idx = y * frameSize + x;
        let lum = grayBuffer[idx];
        if (sAmount !== 0 && x > 0 && x < frameSize - 1 && y > 0 && y < frameSize - 1) {
          const up = grayBuffer[(y - 1) * frameSize + x];
          const down = grayBuffer[(y + 1) * frameSize + x];
          const left = grayBuffer[y * frameSize + (x - 1)];
          const right = grayBuffer[y * frameSize + (x + 1)];
          const laplace = 4 * lum - (up + down + left + right);
          lum = Math.max(0, Math.min(255, lum + sAmount * laplace));
        }
        const bw = lum < threshold ? 0 : 255;
        const pIdx = idx * 4;
        data[pIdx] = bw;
        data[pIdx + 1] = bw;
        data[pIdx + 2] = bw;
        data[pIdx + 3] = 255;
      }
    }

    ctx.putImageData(imgData, 0, 0);
  } catch {
    // Canvas tainted or unavailable in unit test
  }

  return canvas.toDataURL('image/png');
}

/**
 * Single image crop card with draggable 160x160 selector and B&W laser preview.
 */
function CustomImageCard({ item, onUpdate, onRemove, hasStonesOnOuter = false, t }) {
  const imgRef = useRef(null);
  const containerRef = useRef(null);
  const [isLoaded, setIsLoaded] = useState(false);
  const [loadError, setLoadError] = useState(false);
  const [dragActive, setDragActive] = useState(false);
  const [dragStart, setDragStart] = useState({ mouseX: 0, mouseY: 0, initialCropX: 0, initialCropY: 0 });
  const [previewUrl, setPreviewUrl] = useState(item.previewDataUrl || item.processedPreviewReference || '');

  const frameSize = 160;
  const contrast = item.contrast !== undefined ? Number(item.contrast) : 50;
  const sharpness = item.sharpness !== undefined ? Number(item.sharpness) : 50;

  const renderBwPreview = useCallback(
    (cropX, cropY, cVal, sVal) => {
      if (!imgRef.current) return;
      const url = generateBwLaserPreview(imgRef.current, cropX, cropY, frameSize, cVal, sVal);
      if (url) {
        setPreviewUrl(url);
        onUpdate({
          ...item,
          cropX,
          cropY,
          contrast: cVal,
          sharpness: sVal,
          previewDataUrl: url,
          processedPreviewReference: url,
        });
      }
    },
    [item, onUpdate]
  );

  const handleImageLoad = () => {
    setIsLoaded(true);
    setLoadError(false);
    if (imgRef.current) {
      renderBwPreview(item.cropX || 0, item.cropY || 0, contrast, sharpness);
    }
  };

  const handleImageError = () => {
    setLoadError(true);
    setIsLoaded(false);
  };

  const handleRetry = () => {
    setLoadError(false);
    if (imgRef.current) {
      imgRef.current.src = item.imageUrl;
    }
  };

  const updateCropPosition = useCallback(
    (newX, newY) => {
      if (!imgRef.current) return;
      const dispW = imgRef.current.clientWidth || imgRef.current.naturalWidth || frameSize;
      const dispH = imgRef.current.clientHeight || imgRef.current.naturalHeight || frameSize;
      const maxX = Math.max(0, dispW - frameSize);
      const maxY = Math.max(0, dispH - frameSize);
      const clampedX = Math.max(0, Math.min(maxX, Math.round(newX)));
      const clampedY = Math.max(0, Math.min(maxY, Math.round(newY)));

      renderBwPreview(clampedX, clampedY, contrast, sharpness);
    },
    [contrast, sharpness, renderBwPreview]
  );

  const handleContrastChange = (newVal) => {
    const clamped = Math.max(0, Math.min(100, Math.round(newVal)));
    renderBwPreview(item.cropX || 0, item.cropY || 0, clamped, sharpness);
  };

  const handleSharpnessChange = (newVal) => {
    const clamped = Math.max(0, Math.min(100, Math.round(newVal)));
    renderBwPreview(item.cropX || 0, item.cropY || 0, contrast, clamped);
  };

  const handleMouseDown = (e) => {
    e.preventDefault();
    setDragActive(true);
    setDragStart({
      mouseX: e.clientX,
      mouseY: e.clientY,
      initialCropX: item.cropX || 0,
      initialCropY: item.cropY || 0,
    });
  };

  useEffect(() => {
    if (!dragActive) return;

    const handleMouseMove = (e) => {
      const dx = e.clientX - dragStart.mouseX;
      const dy = e.clientY - dragStart.mouseY;
      updateCropPosition(dragStart.initialCropX + dx, dragStart.initialCropY + dy);
    };

    const handleMouseUp = () => {
      setDragActive(false);
    };

    window.addEventListener('mousemove', handleMouseMove);
    window.addEventListener('mouseup', handleMouseUp);

    return () => {
      window.removeEventListener('mousemove', handleMouseMove);
      window.removeEventListener('mouseup', handleMouseUp);
    };
  }, [dragActive, dragStart, updateCropPosition]);

  return (
    <div
      style={{
        border: '1px solid #e2e8f0',
        borderRadius: '10px',
        padding: '1rem',
        marginBottom: '1rem',
        background: '#ffffff',
        boxShadow: '0 1px 3px rgba(0,0,0,0.05)',
      }}
      data-testid={`custom-image-card-${item.id}`}
    >
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.75rem' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
          <span style={{ fontWeight: 600, color: '#1e293b' }}>Ảnh khắc #{item.id}</span>
          <span
            style={{
              fontSize: '0.8rem',
              background: '#e0f2fe',
              color: '#0369a1',
              padding: '0.2rem 0.5rem',
              borderRadius: '999px',
              fontWeight: 500,
            }}
          >
            +100.000 đ
          </span>
        </div>
        <button
          type="button"
          onClick={() => onRemove(item.id)}
          style={{
            background: 'none',
            border: 'none',
            color: '#ef4444',
            cursor: 'pointer',
            fontSize: '0.85rem',
            fontWeight: 500,
            padding: '0.25rem 0.5rem',
          }}
          data-testid={`remove-image-${item.id}`}
        >
          {t.deleteCustomImageBtn || 'Xóa ảnh'}
        </button>
      </div>

      <div style={{ display: 'flex', flexWrap: 'wrap', gap: '1.25rem', alignItems: 'flex-start' }}>
        {/* Interactive Image Frame selector */}
        <div>
          <div style={{ fontSize: '0.85rem', color: '#64748b', marginBottom: '0.5rem' }}>
            {t.dragFrameHelp || 'Kéo thả khung viền đỏ 160px x 160px để chọn vùng khắc:'}
          </div>
          <div
            ref={containerRef}
            style={{
              position: 'relative',
              display: 'inline-block',
              overflow: 'hidden',
              border: '1px solid #cbd5e1',
              borderRadius: '6px',
              background: '#f8fafc',
              maxHeight: '320px',
              maxWidth: '380px',
              minHeight: '160px',
              minWidth: '160px',
              cursor: loadError ? 'default' : 'crosshair',
            }}
          >
            {loadError ? (
              <div
                style={{
                  padding: '2rem 1rem',
                  textAlign: 'center',
                  color: '#e11d48',
                  fontSize: '0.85rem',
                }}
                data-testid={`image-error-${item.id}`}
              >
                <div>⚠️ {t.imageLoadError || 'Không thể tải ảnh khắc.'}</div>
                <button
                  type="button"
                  onClick={handleRetry}
                  style={{
                    marginTop: '0.5rem',
                    padding: '0.25rem 0.75rem',
                    background: '#f1f5f9',
                    border: '1px solid #cbd5e1',
                    borderRadius: '4px',
                    cursor: 'pointer',
                  }}
                  data-testid={`retry-load-image-${item.id}`}
                >
                  {t.imageRetry || 'Thử lại'}
                </button>
              </div>
            ) : (
              <img
                ref={imgRef}
                src={item.imageUrl}
                alt="Custom uploaded"
                onLoad={handleImageLoad}
                onError={handleImageError}
                style={{
                  display: 'block',
                  maxWidth: '100%',
                  maxHeight: '320px',
                  objectFit: 'contain',
                  userSelect: 'none',
                  pointerEvents: 'none',
                }}
              />
            )}

            {/* Draggable 160x160 Frame */}
            {isLoaded && !loadError && (
              <div
                onMouseDown={handleMouseDown}
                data-testid={`crop-frame-${item.id}`}
                style={{
                  position: 'absolute',
                  left: `${item.cropX || 0}px`,
                  top: `${item.cropY || 0}px`,
                  width: `${frameSize}px`,
                  height: `${frameSize}px`,
                  border: '2px solid #ef4444',
                  boxShadow: '0 0 0 9999px rgba(0, 0, 0, 0.45)',
                  cursor: dragActive ? 'grabbing' : 'grab',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  color: '#ffffff',
                  fontSize: '0.75rem',
                  fontWeight: 600,
                  textShadow: '0 1px 2px rgba(0,0,0,0.8)',
                  userSelect: 'none',
                  boxSizing: 'border-box',
                }}
              >
                160 × 160
              </div>
            )}
          </div>

          {/* Coordinate Sliders for fine tuning */}
          <div style={{ display: 'flex', gap: '0.75rem', marginTop: '0.5rem', fontSize: '0.8rem', color: '#475569' }}>
            <label>
              X:
              <input
                type="number"
                value={item.cropX || 0}
                onChange={(e) => updateCropPosition(Number(e.target.value) || 0, item.cropY || 0)}
                style={{ width: '60px', marginLeft: '0.25rem', padding: '0.2rem', borderRadius: '4px', border: '1px solid #cbd5e1' }}
              />
            </label>
            <label>
              Y:
              <input
                type="number"
                value={item.cropY || 0}
                onChange={(e) => updateCropPosition(item.cropX || 0, Number(e.target.value) || 0)}
                style={{ width: '60px', marginLeft: '0.25rem', padding: '0.2rem', borderRadius: '4px', border: '1px solid #cbd5e1' }}
              />
            </label>
          </div>
        </div>

        {/* 160x160 Black & White Laser Preview Box and Preprocessing Sliders */}
        <div style={{ flex: '1', minWidth: '180px' }}>
          <div style={{ fontSize: '0.85rem', fontWeight: 600, color: '#334155', marginBottom: '0.5rem' }}>
            {t.laserPreviewTitle || 'Bản xem trước khắc laser (Trắng - Đen 160px x 160px):'}
          </div>

          <div
            style={{
              width: '160px',
              height: '160px',
              border: '2px solid #0f172a',
              borderRadius: '6px',
              background: '#ffffff',
              boxShadow: '0 2px 8px rgba(0,0,0,0.1)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              overflow: 'hidden',
            }}
            data-testid={`laser-preview-${item.id}`}
          >
            {loadError ? (
              <span style={{ fontSize: '0.75rem', color: '#e11d48' }}>Lỗi tải ảnh</span>
            ) : previewUrl ? (
              <img
                src={previewUrl}
                alt="Laser preview"
                style={{ width: '160px', height: '160px', imageRendering: 'pixelated' }}
              />
            ) : (
              <span style={{ fontSize: '0.75rem', color: '#94a3b8' }}>Đang tải...</span>
            )}
          </div>

          {/* Contrast Slider */}
          <div style={{ marginTop: '0.75rem' }} data-testid={`contrast-slider-container-${item.id}`}>
            <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.8rem', fontWeight: 500, color: '#334155', marginBottom: '0.25rem' }}>
              <span>{t.contrastLabel || 'Độ tương phản'}</span>
              <span data-testid={`contrast-value-${item.id}`}>{contrast}</span>
            </div>
            <input
              type="range"
              min="0"
              max="100"
              step="1"
              value={contrast}
              onChange={(e) => handleContrastChange(Number(e.target.value))}
              style={{ width: '100%', cursor: 'pointer' }}
              data-testid={`contrast-slider-${item.id}`}
            />
            <div style={{ fontSize: '0.7rem', color: '#64748b' }}>
              {t.contrastHelper || '0 - 100, mặc định 50'}
            </div>
          </div>

          {/* Sharpness Slider */}
          <div style={{ marginTop: '0.5rem' }} data-testid={`sharpness-slider-container-${item.id}`}>
            <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.8rem', fontWeight: 500, color: '#334155', marginBottom: '0.25rem' }}>
              <span>{t.sharpnessLabel || 'Độ sắc nét'}</span>
              <span data-testid={`sharpness-value-${item.id}`}>{sharpness}</span>
            </div>
            <input
              type="range"
              min="0"
              max="100"
              step="1"
              value={sharpness}
              onChange={(e) => handleSharpnessChange(Number(e.target.value))}
              style={{ width: '100%', cursor: 'pointer' }}
              data-testid={`sharpness-slider-${item.id}`}
            />
            <div style={{ fontSize: '0.7rem', color: '#64748b' }}>
              {t.sharpnessHelper || '0 - 100, mặc định 50'}
            </div>
          </div>

          {/* Placement Picker */}
          <div style={{ marginTop: '0.75rem' }}>
            <div style={{ fontSize: '0.8rem', fontWeight: 500, color: '#475569', marginBottom: '0.35rem' }}>
              {t.engravingPlacementLabel || 'Vị trí khắc'}
            </div>
            <div className="ring-pills-row" style={{ gap: '0.5rem' }}>
              <button
                type="button"
                className={`ring-size-pill ring-placement-pill ${item.placement === 'INNER_BAND' ? 'selected' : ''}`}
                onClick={() => onUpdate({ ...item, placement: 'INNER_BAND' })}
                style={{ padding: '0.35rem 0.65rem', fontSize: '0.8rem' }}
                data-testid={`placement-inner-${item.id}`}
              >
                {t.innerBand || 'Mặt trong'}
              </button>
              <button
                type="button"
                disabled={hasStonesOnOuter}
                className={`ring-size-pill ring-placement-pill ${item.placement === 'OUTER_BAND' ? 'selected' : ''}`}
                onClick={() => {
                  if (!hasStonesOnOuter) {
                    onUpdate({ ...item, placement: 'OUTER_BAND' });
                  }
                }}
                style={{
                  padding: '0.35rem 0.65rem',
                  fontSize: '0.8rem',
                  ...(hasStonesOnOuter ? { opacity: 0.5, cursor: 'not-allowed' } : {}),
                }}
                data-testid={`placement-outer-${item.id}`}
                title={hasStonesOnOuter ? 'Do mẫu thiết kế có đính đá ở ngoài nên không thể khắc lazer' : undefined}
              >
                {t.outerBand || 'Mặt ngoài'}
              </button>
            </div>
            {hasStonesOnOuter && (
              <div style={{ color: '#dc2626', fontSize: '0.78rem', marginTop: '0.35rem', fontWeight: 500 }} data-testid={`custom-img-outer-blocked-${item.id}`}>
                ⚠️ Do mẫu thiết kế có đính đá ở ngoài nên không thể khắc lazer
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}

const SAMPLE_PRESETS = [
  {
    id: 'clover',
    label: 'Cỏ 4 lá',
    icon: '🍀',
    svg: `<svg xmlns="http://www.w3.org/2000/svg" width="160" height="160" viewBox="0 0 160 160"><rect width="160" height="160" fill="#FFFFFF"/><path d="M80,80 C60,40 30,50 40,75 C48,90 70,85 80,80 C60,120 30,110 40,85 C48,70 70,75 80,80 C100,120 130,110 120,85 C112,70 90,75 80,80 C100,40 130,50 120,75 C112,90 90,85 80,80 Z" fill="#000000"/><path d="M80,80 Q84,125 76,145 Q80,145 84,125 Z" fill="#000000"/></svg>`,
  },
  {
    id: 'heart',
    label: 'Trái tim',
    icon: '❤️',
    svg: `<svg xmlns="http://www.w3.org/2000/svg" width="160" height="160" viewBox="0 0 160 160"><rect width="160" height="160" fill="#FFFFFF"/><path d="M80,135 C25,95 20,48 55,38 C72,33 80,52 80,52 C80,52 88,33 105,38 C140,48 135,95 80,135 Z" fill="#000000"/></svg>`,
  },
  {
    id: 'sun',
    label: 'Mặt trời',
    icon: '☀️',
    svg: `<svg xmlns="http://www.w3.org/2000/svg" width="160" height="160" viewBox="0 0 160 160"><rect width="160" height="160" fill="#FFFFFF"/><circle cx="80" cy="80" r="28" fill="#000000"/><g stroke="#000000" stroke-width="7" stroke-linecap="round"><line x1="80" y1="22" x2="80" y2="40"/><line x1="80" y1="120" x2="80" y2="138"/><line x1="22" y1="80" x2="40" y2="80"/><line x1="120" y1="80" x2="138" y2="80"/><line x1="39" y1="39" x2="52" y2="52"/><line x1="108" y1="108" x2="121" y2="121"/><line x1="39" y1="121" x2="52" y2="108"/><line x1="108" y1="52" x2="121" y2="39"/></g></svg>`,
  },
  {
    id: 'moon',
    label: 'Mặt trăng',
    icon: '🌙',
    svg: `<svg xmlns="http://www.w3.org/2000/svg" width="160" height="160" viewBox="0 0 160 160"><rect width="160" height="160" fill="#FFFFFF"/><path d="M92,30 C52,38 38,76 48,110 C58,132 84,138 108,128 C76,120 68,76 92,30 Z" fill="#000000"/></svg>`,
  },
  {
    id: 'star',
    label: 'Ngôi sao',
    icon: '⭐',
    svg: `<svg xmlns="http://www.w3.org/2000/svg" width="160" height="160" viewBox="0 0 160 160"><rect width="160" height="160" fill="#FFFFFF"/><polygon points="80,26 96,65 138,67 105,92 117,132 80,108 43,132 55,92 22,67 64,65" fill="#000000"/></svg>`,
  },
  {
    id: 'infinity',
    label: 'Vô cực',
    icon: '♾️',
    svg: `<svg xmlns="http://www.w3.org/2000/svg" width="160" height="160" viewBox="0 0 160 160"><rect width="160" height="160" fill="#FFFFFF"/><path d="M80,80 C60,52 32,52 32,80 C32,108 60,108 80,80 C100,52 128,52 128,80 C128,108 100,108 80,80 Z" fill="none" stroke="#000000" stroke-width="14" stroke-linecap="round"/></svg>`,
  },
];

function convertSvgToPngDataUrl(svgString, callback) {
  if (typeof window === 'undefined' || typeof Image === 'undefined') {
    callback('data:image/svg+xml;utf8,' + encodeURIComponent(svgString));
    return;
  }
  const blob = new Blob([svgString], { type: 'image/svg+xml;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const img = new Image();
  img.onload = () => {
    try {
      const canvas = document.createElement('canvas');
      canvas.width = 160;
      canvas.height = 160;
      const ctx = canvas.getContext('2d');
      ctx.drawImage(img, 0, 0);
      URL.revokeObjectURL(url);
      callback(canvas.toDataURL('image/png'));
    } catch {
      URL.revokeObjectURL(url);
      callback('data:image/svg+xml;utf8,' + encodeURIComponent(svgString));
    }
  };
  img.onerror = () => {
    URL.revokeObjectURL(url);
    callback('data:image/svg+xml;utf8,' + encodeURIComponent(svgString));
  };
  img.src = url;
}

export default function LaserEngravingImageCropper({ customImages = [], onChangeCustomImages, hasStonesOnOuter = false, t }) {
  const fileInputRef = useRef(null);
  const isImageLimitReached = customImages.length >= 2;

  const appendCustomImage = (finalUrl, label = null) => {
    const newId = `img-${Date.now().toString().slice(-4)}`;
    const newItem = {
      id: newId,
      name: label || `Ảnh khắc #${customImages.length + 1}`,
      imageUrl: finalUrl,
      previewDataUrl: finalUrl,
      processedPreviewReference: finalUrl,
      cropX: 0,
      cropY: 0,
      cropWidth: 160,
      cropHeight: 160,
      contrast: 50,
      sharpness: 50,
      placement: 'INNER_BAND',
      price: 100000,
    };

    onChangeCustomImages([...customImages, newItem]);
    if (fileInputRef.current) {
      fileInputRef.current.value = '';
    }
  };

  const handleSelectPreset = (preset) => {
    if (isImageLimitReached) return;
    convertSvgToPngDataUrl(preset.svg, (dataUrl) => {
      appendCustomImage(dataUrl, preset.label);
    });
  };

  const handleFileUpload = (e) => {
    const file = e.target.files?.[0];
    if (!file) return;

    if (file.size > 5 * 1024 * 1024) {
      alert('Kích thước ảnh vượt quá giới hạn 5 MB. Vui lòng chọn tệp nhỏ hơn 5 MB.');
      if (fileInputRef.current) {
        fileInputRef.current.value = '';
      }
      return;
    }

    if (customImages.length >= 2) {
      if (fileInputRef.current) {
        fileInputRef.current.value = '';
      }
      return;
    }

    const reader = new FileReader();
    reader.onload = (uploadEvent) => {
      const rawDataUrl = uploadEvent.target?.result;
      if (!rawDataUrl) return;

      if (typeof window !== 'undefined' && typeof Image !== 'undefined') {
        const tempImg = new Image();
        tempImg.onload = () => {
          try {
            const MAX_DIM = 800;
            let width = tempImg.width || 0;
            let height = tempImg.height || 0;
            if (width > MAX_DIM || height > MAX_DIM) {
              const ratio = Math.min(MAX_DIM / width, MAX_DIM / height);
              width = Math.max(160, Math.round(width * ratio));
              height = Math.max(160, Math.round(height * ratio));
              const canvas = document.createElement('canvas');
              canvas.width = width;
              canvas.height = height;
              const ctx = canvas.getContext('2d');
              ctx.drawImage(tempImg, 0, 0, width, height);
              appendCustomImage(canvas.toDataURL('image/jpeg', 0.85));
            } else {
              appendCustomImage(rawDataUrl);
            }
          } catch {
            appendCustomImage(rawDataUrl);
          }
        };
        tempImg.onerror = () => appendCustomImage(rawDataUrl);
        tempImg.src = rawDataUrl;
      } else {
        appendCustomImage(rawDataUrl);
      }
    };
    reader.readAsDataURL(file);
  };

  const handleUpdate = (updatedItem) => {
    onChangeCustomImages(customImages.map((img) => (img.id === updatedItem.id ? updatedItem : img)));
  };

  const handleRemove = (id) => {
    onChangeCustomImages(customImages.filter((img) => img.id !== id));
  };

  return (
    <div style={{ marginTop: '1.25rem', paddingTop: '1.25rem', borderTop: '1px dashed #cbd5e1' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.5rem' }}>
        <div>
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
            <h3 style={{ fontSize: '1rem', fontWeight: 600, color: '#1e293b', margin: 0 }}>
              {t.customImageSectionTitle || 'Khắc hình ảnh cá nhân hóa (+100.000 đ / ảnh)'}
            </h3>
            <span
              style={{
                fontSize: '0.8rem',
                background: isImageLimitReached ? '#fee2e2' : '#f1f5f9',
                color: isImageLimitReached ? '#e11d48' : '#475569',
                padding: '0.15rem 0.5rem',
                borderRadius: '999px',
                fontWeight: 600,
              }}
              data-testid="engraving-image-counter"
            >
              {(t.imageEngravingCounter || 'Ảnh laser: {count}/2').replace('{count}', customImages.length)}
            </span>
          </div>
          <p style={{ fontSize: '0.85rem', color: '#64748b', margin: '0.25rem 0 0 0' }}>
            {t.customImageSectionDesc ||
              'Tải lên ảnh mẫu (chữ ký, logo). Hệ thống chuyển sang màu đen trắng và đóng khung 160px x 160px để bạn chọn vị trí khắc đẹp nhất (tối đa 2 ảnh).'}
          </p>
        </div>
      </div>

      {/* Hidden file input */}
      <input
        ref={fileInputRef}
        type="file"
        accept="image/*"
        onChange={handleFileUpload}
        style={{ display: 'none' }}
        data-testid="upload-engraving-image-input"
      />

      {/* Preset sample icons gallery */}
      <div style={{ marginTop: '0.75rem', marginBottom: '0.75rem', background: '#f8fafc', padding: '0.75rem', borderRadius: 8, border: '1px solid #e2e8f0' }}>
        <div style={{ fontSize: '0.82rem', fontWeight: 600, color: '#475569', marginBottom: '0.5rem' }}>
          ✨ Hoặc chọn biểu tượng mẫu có sẵn (Khắc nhanh):
        </div>
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: '0.5rem' }}>
          {SAMPLE_PRESETS.map((preset) => (
            <button
              type="button"
              key={preset.id}
              onClick={() => handleSelectPreset(preset)}
              disabled={isImageLimitReached}
              style={{
                display: 'inline-flex',
                alignItems: 'center',
                gap: '0.35rem',
                padding: '0.35rem 0.65rem',
                background: '#ffffff',
                border: '1px solid #cbd5e1',
                borderRadius: 6,
                fontSize: '0.85rem',
                color: '#1e293b',
                cursor: isImageLimitReached ? 'not-allowed' : 'pointer',
                opacity: isImageLimitReached ? 0.5 : 1,
                transition: 'all 0.15s ease',
              }}
              data-testid={`preset-engraving-${preset.id}`}
              title={`Khắc laser mẫu ${preset.label}`}
            >
              <span style={{ fontSize: '1rem' }}>{preset.icon}</span>
              <span>{preset.label}</span>
            </button>
          ))}
        </div>
      </div>

      <div style={{ marginTop: '0.5rem', marginBottom: '1rem' }}>
        <button
          type="button"
          onClick={() => fileInputRef.current?.click()}
          disabled={isImageLimitReached}
          style={{
            display: 'inline-flex',
            alignItems: 'center',
            gap: '0.5rem',
            padding: '0.5rem 1rem',
            background: isImageLimitReached ? '#f8fafc' : '#f1f5f9',
            border: '1px solid #cbd5e1',
            borderRadius: '8px',
            color: isImageLimitReached ? '#94a3b8' : '#334155',
            fontSize: '0.9rem',
            fontWeight: 500,
            cursor: isImageLimitReached ? 'not-allowed' : 'pointer',
            opacity: isImageLimitReached ? 0.6 : 1,
          }}
          data-testid="upload-engraving-image-btn"
        >
          📷 {t.uploadCustomImageBtn || 'Tải ảnh lên để khắc laser (+100.000 đ)'}
        </button>

        {isImageLimitReached && (
          <div
            style={{ color: '#e11d48', fontSize: '0.85rem', marginTop: '0.25rem', fontWeight: 500 }}
            data-testid="image-engraving-limit-message"
          >
            {t.imageEngravingLimitReached || 'Đã đạt tối đa 2 ảnh khắc laser.'}
          </div>
        )}
      </div>

      {/* List of uploaded custom image crop cards */}
      {customImages.map((item) => (
        <CustomImageCard key={item.id} item={item} onUpdate={handleUpdate} onRemove={handleRemove} hasStonesOnOuter={hasStonesOnOuter} t={t} />
      ))}

      {/* Fingerprint laser engraving notice */}
      <div
        style={{
          marginTop: '1rem',
          padding: '0.65rem 0.85rem',
          borderRadius: '8px',
          background: '#f8fafc',
          border: '1px solid #cbd5e1',
          fontSize: '0.85rem',
          color: '#475569',
          display: 'flex',
          alignItems: 'center',
          gap: '0.5rem',
        }}
        data-testid="fingerprint-notice"
      >
        <span>💡</span>
        <span>{t.fingerprintNotice || 'Để có thể khắc lazer vân tay, các bạn hãy đến cửa hàng lấy vân tay trực tiếp nhé'}</span>
      </div>
    </div>
  );
}

