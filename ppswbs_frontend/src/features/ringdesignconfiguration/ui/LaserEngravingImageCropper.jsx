import React, { useRef, useState, useEffect, useCallback } from 'react';

/**
 * Converts a 160x160 slice of an image to high-contrast black-and-white (monochrome laser engraving).
 */
export function generateBwLaserPreview(imgElement, cropX, cropY, frameSize = 160) {
  const canvas = document.createElement('canvas');
  canvas.width = frameSize;
  canvas.height = frameSize;
  const ctx = canvas.getContext('2d');
  if (!ctx) return '';

  // White background
  ctx.fillStyle = '#FFFFFF';
  ctx.fillRect(0, 0, frameSize, frameSize);

  // Draw the cropped slice
  try {
    ctx.drawImage(imgElement, cropX, cropY, frameSize, frameSize, 0, 0, frameSize, frameSize);
  } catch {
    return '';
  }

  // Grayscale thresholding to pure black and white
  try {
    const imgData = ctx.getImageData(0, 0, frameSize, frameSize);
    const data = imgData.data;
    for (let i = 0; i < data.length; i += 4) {
      const r = data[i];
      const g = data[i + 1];
      const b = data[i + 2];
      const a = data[i + 3];

      if (a < 64) {
        // Transparent becomes white
        data[i] = 255;
        data[i + 1] = 255;
        data[i + 2] = 255;
        data[i + 3] = 255;
      } else {
        const luminance = 0.299 * r + 0.587 * g + 0.114 * b;
        const bw = luminance < 140 ? 0 : 255;
        data[i] = bw;
        data[i + 1] = bw;
        data[i + 2] = bw;
        data[i + 3] = 255;
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
function CustomImageCard({ item, onUpdate, onRemove, t }) {
  const imgRef = useRef(null);
  const containerRef = useRef(null);
  const [isLoaded, setIsLoaded] = useState(false);
  const [dragActive, setDragActive] = useState(false);
  const [dragStart, setDragStart] = useState({ mouseX: 0, mouseY: 0, initialCropX: 0, initialCropY: 0 });
  const [previewUrl, setPreviewUrl] = useState(item.previewDataUrl || '');

  const frameSize = 160;

  const handleImageLoad = () => {
    setIsLoaded(true);
    if (imgRef.current) {
      const url = generateBwLaserPreview(imgRef.current, item.cropX || 0, item.cropY || 0, frameSize);
      setPreviewUrl(url);
      onUpdate({ ...item, previewDataUrl: url });
    }
  };

  const updateCropPosition = useCallback(
    (newX, newY) => {
      if (!imgRef.current) return;
      const maxX = Math.max(0, imgRef.current.naturalWidth - frameSize);
      const maxY = Math.max(0, imgRef.current.naturalHeight - frameSize);
      const clampedX = Math.max(0, Math.min(maxX, Math.round(newX)));
      const clampedY = Math.max(0, Math.min(maxY, Math.round(newY)));

      const url = generateBwLaserPreview(imgRef.current, clampedX, clampedY, frameSize);
      setPreviewUrl(url);
      onUpdate({
        ...item,
        cropX: clampedX,
        cropY: clampedY,
        previewDataUrl: url,
      });
    },
    [item, onUpdate]
  );

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
              cursor: 'crosshair',
            }}
          >
            <img
              ref={imgRef}
              src={item.imageUrl}
              alt="Custom uploaded"
              onLoad={handleImageLoad}
              style={{
                display: 'block',
                maxWidth: '100%',
                maxHeight: '320px',
                objectFit: 'contain',
                userSelect: 'none',
                pointerEvents: 'none',
              }}
            />

            {/* Draggable 160x160 Frame */}
            {isLoaded && (
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

        {/* 160x160 Black & White Laser Preview Box */}
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
            {previewUrl ? (
              <img
                src={previewUrl}
                alt="Laser preview"
                style={{ width: '160px', height: '160px', imageRendering: 'pixelated' }}
              />
            ) : (
              <span style={{ fontSize: '0.75rem', color: '#94a3b8' }}>Đang tải...</span>
            )}
          </div>

          {/* Placement Picker */}
          <div style={{ marginTop: '0.75rem' }}>
            <div style={{ fontSize: '0.8rem', fontWeight: 500, color: '#475569', marginBottom: '0.35rem' }}>
              {t.engravingPlacementLabel || 'Vị trí khắc'}
            </div>
            <div className="ring-pills-row" style={{ gap: '0.5rem' }}>
              <button
                type="button"
                className={`ring-size-pill ${item.placement === 'INNER_BAND' ? 'selected' : ''}`}
                onClick={() => onUpdate({ ...item, placement: 'INNER_BAND' })}
                style={{ padding: '0.35rem 0.65rem', fontSize: '0.8rem' }}
                data-testid={`placement-inner-${item.id}`}
              >
                {t.innerBand || 'Mặt trong'}
              </button>
              <button
                type="button"
                className={`ring-size-pill ${item.placement === 'OUTER_BAND' ? 'selected' : ''}`}
                onClick={() => onUpdate({ ...item, placement: 'OUTER_BAND' })}
                style={{ padding: '0.35rem 0.65rem', fontSize: '0.8rem' }}
                data-testid={`placement-outer-${item.id}`}
              >
                {t.outerBand || 'Mặt ngoài'}
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

export default function LaserEngravingImageCropper({ customImages = [], onChangeCustomImages, t }) {
  const fileInputRef = useRef(null);

  const handleFileUpload = (e) => {
    const file = e.target.files?.[0];
    if (!file) return;

    const reader = new FileReader();
    reader.onload = (uploadEvent) => {
      const dataUrl = uploadEvent.target?.result;
      if (!dataUrl) return;

      const newId = `img-${Date.now().toString().slice(-4)}`;
      const newItem = {
        id: newId,
        imageUrl: dataUrl,
        cropX: 0,
        cropY: 0,
        cropWidth: 160,
        cropHeight: 160,
        placement: 'INNER_BAND',
        price: 100000,
        previewDataUrl: '',
      };

      onChangeCustomImages([...customImages, newItem]);
      if (fileInputRef.current) {
        fileInputRef.current.value = '';
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
          <h3 style={{ fontSize: '1rem', fontWeight: 600, color: '#1e293b', margin: 0 }}>
            {t.customImageSectionTitle || 'Khắc hình ảnh cá nhân hóa (+100.000 đ / ảnh)'}
          </h3>
          <p style={{ fontSize: '0.85rem', color: '#64748b', margin: '0.25rem 0 0 0' }}>
            {t.customImageSectionDesc ||
              'Tải lên ảnh mẫu (chữ ký, logo). Hệ thống chuyển sang màu đen trắng và đóng khung 160px x 160px để bạn chọn vị trí khắc đẹp nhất.'}
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

      <button
        type="button"
        onClick={() => fileInputRef.current?.click()}
        style={{
          display: 'inline-flex',
          alignItems: 'center',
          gap: '0.5rem',
          padding: '0.5rem 1rem',
          background: '#f1f5f9',
          border: '1px solid #cbd5e1',
          borderRadius: '8px',
          color: '#334155',
          fontSize: '0.9rem',
          fontWeight: 500,
          cursor: 'pointer',
          marginTop: '0.5rem',
          marginBottom: '1rem',
        }}
        data-testid="upload-engraving-image-btn"
      >
        📷 {t.uploadCustomImageBtn || 'Tải ảnh lên để khắc laser (+100.000 đ)'}
      </button>

      {/* List of uploaded custom image crop cards */}
      {customImages.map((item) => (
        <CustomImageCard key={item.id} item={item} onUpdate={handleUpdate} onRemove={handleRemove} t={t} />
      ))}
    </div>
  );
}

