import React, { useMemo } from 'react';
import RingPreview from '../../ringdesignconfiguration/ui/RingPreview';
import { derivePreviewModel, getRingCircumferenceMm, getRingDiameterMm } from '../../ringdesignconfiguration/domain/designPreview';
import { messages } from '../../ringdesignconfiguration/messages';

const formatVnd = (amt) => Number(Math.round(Number(amt) || 0)).toLocaleString('vi-VN') + ' đ';

export default function Design3dDetailModal({
  isOpen,
  onClose,
  snapshot,
  designSnapshot,
  title = 'Chi tiết Bản vẽ Thiết kế & Thông số Kỹ thuật',
  reference = '',
  designReference = '',
}) {
  if (!isOpen) return null;

  const t = messages.vi;
  const effectiveSnapshot = snapshot || designSnapshot;
  const effectiveRef = reference || designReference || '';

  const previewModel = useMemo(() => {
    return derivePreviewModel(effectiveSnapshot, (k) => t[k] || k);
  }, [effectiveSnapshot, t]);

  const ringSize = effectiveSnapshot?.ringSize || previewModel?.ringSize || 12;
  const circMm = getRingCircumferenceMm(ringSize);
  const diamMm = getRingDiameterMm(ringSize);

  const customImages = effectiveSnapshot?.engraving?.customImages || previewModel?.customImages || [];
  const engravingText = previewModel.engravingText || effectiveSnapshot?.engraving?.text || '';
  const engravingPlacement = previewModel.engravingPlacement || effectiveSnapshot?.engraving?.placement || 'INNER_BAND';

  return (
    <div
      role="dialog"
      aria-modal="true"
      style={{
        position: 'fixed',
        top: 0,
        left: 0,
        width: '100vw',
        height: '100vh',
        background: 'rgba(0, 0, 0, 0.75)',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        zIndex: 9999,
        padding: '1rem',
      }}
      onMouseDown={(e) => {
        if (e.target === e.currentTarget) onClose();
      }}
      data-testid="design-3d-detail-modal"
    >
      <div
        style={{
          background: '#ffffff',
          borderRadius: '12px',
          maxWidth: '880px',
          width: '100%',
          maxHeight: '92vh',
          display: 'flex',
          flexDirection: 'column',
          boxShadow: '0 25px 50px -12px rgba(0, 0, 0, 0.25)',
          overflow: 'hidden',
        }}
        onClick={(e) => e.stopPropagation()}
      >
        {/* Header */}
        <div
          style={{
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            padding: '1rem 1.5rem',
            borderBottom: '1px solid #e2e8f0',
            background: '#f8fafc',
          }}
        >
          <div>
            <h3 style={{ margin: 0, fontSize: '1.2rem', color: '#0f172a', display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
              <span>✨</span> {title}
            </h3>
            {effectiveRef && (
              <span style={{ fontSize: '0.8rem', color: '#64748b', fontWeight: 500 }}>
                Mã tham chiếu: <code>{effectiveRef}</code>
              </span>
            )}
          </div>
          <button
            type="button"
            onClick={onClose}
            style={{
              background: 'none',
              border: 'none',
              fontSize: '1.5rem',
              color: '#64748b',
              cursor: 'pointer',
              lineHeight: 1,
              padding: '0.25rem 0.5rem',
            }}
            aria-label="Đóng"
            data-testid="close-3d-modal-btn"
          >
            ×
          </button>
        </div>

        {/* Content Body */}
        <div
          style={{
            padding: '1.25rem 1.5rem',
            overflowY: 'auto',
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fit, minmax(min(100%, 300px), 1fr))',
            gap: '1.5rem',
            boxSizing: 'border-box',
          }}
        >
          {/* Left Column: Visual Ring Preview */}
          <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', width: '100%', minWidth: 0 }}>
            <div
              style={{
                width: '100%',
                maxWidth: '340px',
                background: '#f8fafc',
                borderRadius: '12px',
                border: '1px solid #e2e8f0',
                padding: '1rem',
                display: 'flex',
                flexDirection: 'column',
                alignItems: 'center',
                boxSizing: 'border-box',
              }}
            >
              <h4 style={{ margin: '0 0 0.5rem 0', fontSize: '0.9rem', color: '#475569', textTransform: 'uppercase' }}>
                Mô hình trực quan bản vẽ nhẫn
              </h4>
              <div style={{ width: '100%', minHeight: '260px' }}>
                <RingPreview previewModel={previewModel} readOnly={true} />
              </div>
              <span style={{ fontSize: '0.78rem', color: '#64748b', textAlign: 'center', marginTop: '0.75rem', lineHeight: 1.4 }}>
                💡 Mô phỏng trực quan tương tác tỉ lệ thực tế theo góc nhìn trực diện.
              </span>
            </div>
          </div>

          {/* Right Column: Full Specifications Breakdown */}
          <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem', minWidth: 0, width: '100%' }}>
            {/* Box 1: Material & Dimensions */}
            <div style={{ background: '#f8fafc', borderRadius: '8px', border: '1px solid #e2e8f0', padding: '1rem' }}>
              <h4 style={{ margin: '0 0 0.5rem 0', fontSize: '0.95rem', color: '#0369a1' }}>
                1. Thông số Kích thước & Vật liệu
              </h4>
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(135px, 1fr))', gap: '0.5rem', fontSize: '0.875rem', color: '#334155' }}>
                <div>
                  <strong>Chất liệu:</strong> {previewModel.material?.label || effectiveSnapshot?.metalType || 'Bạc Ý 925'}
                </div>
                <div>
                  <strong>Dáng đai:</strong> {previewModel.shape?.label || 'Comfort Fit'}
                </div>
                <div>
                  <strong>Size nhẫn:</strong> Size {ringSize} ({circMm} mm)
                </div>
                <div>
                  <strong>Đường kính trong:</strong> ~{diamMm} mm
                </div>
                <div>
                  <strong>Bản rộng đai:</strong> {previewModel.bandWidthMm || 3.0} mm
                </div>
                <div>
                  <strong>Độ dày đai:</strong> {previewModel.thicknessMm || 1.8} mm
                </div>
              </div>
            </div>

            {/* Box 2: Gemstones */}
            <div style={{ background: '#f8fafc', borderRadius: '8px', border: '1px solid #e2e8f0', padding: '1rem' }}>
              <h4 style={{ margin: '0 0 0.5rem 0', fontSize: '0.95rem', color: '#0369a1' }}>
                2. Đá quý đính kèm
              </h4>
              {((previewModel.components && previewModel.components.length > 0) ? previewModel.components : (effectiveSnapshot?.components || [])).length > 0 ? (
                <ul style={{ margin: 0, paddingLeft: '1.2rem', fontSize: '0.85rem', color: '#334155', lineHeight: 1.6 }}>
                  {((previewModel.components && previewModel.components.length > 0) ? previewModel.components : (effectiveSnapshot?.components || [])).map((c, i) => (
                    <li key={i}>
                      {c.label || c.reference} (Số lượng: {c.quantity || 1})
                    </li>
                  ))}
                </ul>
              ) : (
                <p style={{ margin: 0, fontSize: '0.85rem', color: '#64748b', fontStyle: 'italic' }}>
                  Không đính đá quý (Nhẫn trơn).
                </p>
              )}
            </div>

            {/* Box 3: Engravings */}
            <div style={{ background: '#f8fafc', borderRadius: '8px', border: '1px solid #e2e8f0', padding: '1rem' }}>
              <h4 style={{ margin: '0 0 0.5rem 0', fontSize: '0.95rem', color: '#0369a1' }}>
                3. Nội dung Khắc Laser Cá nhân hóa
              </h4>
              <div style={{ fontSize: '0.85rem', color: '#334155', display: 'flex', flexDirection: 'column', gap: '0.4rem' }}>
                {engravingText ? (
                  <div>
                    <strong>Chữ khắc:</strong> "{engravingText}" (
                    {engravingPlacement === 'INNER_BAND' ? 'Lòng trong nhẫn' : 'Mặt ngoài nhẫn'})
                  </div>
                ) : (
                  <div style={{ color: '#64748b', fontStyle: 'italic' }}>Không khắc chữ.</div>
                )}

                {customImages.length > 0 && (
                  <div>
                    <strong>Ảnh khắc laser ({customImages.length} ảnh):</strong>
                    <div style={{ display: 'flex', gap: '0.5rem', marginTop: '0.35rem', flexWrap: 'wrap' }}>
                      {customImages.map((img, idx) => {
                        const imgUrl = img.previewDataUrl || img.processedPreviewReference || img.imageReference || img.imageUrl;
                        return (
                          <div key={img.id || idx} style={{ textAlign: 'center' }}>
                            {imgUrl ? (
                              <img
                                src={imgUrl}
                                alt="Ảnh khắc laser"
                                style={{
                                  width: '56px',
                                  height: '56px',
                                  objectFit: 'contain',
                                  background: '#ffffff',
                                  border: '1px solid #cbd5e1',
                                  borderRadius: '4px',
                                }}
                              />
                            ) : (
                              <div style={{ width: '56px', height: '56px', display: 'flex', alignItems: 'center', justifyContent: 'center', background: '#f1f5f9', border: '1px solid #cbd5e1', borderRadius: '4px', fontSize: '0.7rem', color: '#94a3b8' }}>
                                Ảnh
                              </div>
                            )}
                            <span style={{ fontSize: '0.7rem', color: '#64748b', display: 'block' }}>
                              {img.name || `Ảnh #${idx + 1}`}
                            </span>
                          </div>
                        );
                      })}
                    </div>
                  </div>
                )}
              </div>
            </div>

            {/* Box 4: Estimated Weight & Cost Breakdown */}
            <div style={{ background: '#f0fdf4', borderRadius: '8px', border: '1px solid #bbf7d0', padding: '0.75rem 1rem', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <div>
                <span style={{ fontSize: '0.8rem', color: '#166534', display: 'block' }}>Khối lượng ước tính:</span>
                <strong style={{ fontSize: '0.95rem', color: '#166534' }}>{previewModel.estimatedWeightGrams || 3.0} gam</strong>
              </div>
              <div style={{ textAlign: 'right' }}>
                <span style={{ fontSize: '0.8rem', color: '#166534', display: 'block' }}>Tổng dự toán chế tác:</span>
                <strong style={{ fontSize: '1.1rem', color: '#166534' }}>
                  {formatVnd(previewModel.totalCost || 0)}
                </strong>
              </div>
            </div>
          </div>
        </div>

        {/* Footer */}
        <div
          style={{
            display: 'flex',
            justifyContent: 'flex-end',
            padding: '0.75rem 1.5rem',
            borderTop: '1px solid #e2e8f0',
            background: '#f8fafc',
          }}
        >
          <button
            type="button"
            onClick={onClose}
            className="button button-primary"
            style={{
              padding: '0.5rem 1.25rem',
              backgroundColor: '#0284c7',
              color: '#ffffff',
              border: 'none',
              borderRadius: '6px',
              cursor: 'pointer',
              fontWeight: 600,
            }}
          >
            Đóng
          </button>
        </div>
      </div>
    </div>
  );
}

export { Design3dDetailModal as DesignDetailModal };
