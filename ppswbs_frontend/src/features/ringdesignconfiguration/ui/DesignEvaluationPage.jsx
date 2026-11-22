import React, { useEffect, useState, useMemo } from 'react';
import { useParams, useNavigate } from 'react-router';
import { useWorkshopRingDesignApi } from '../api/useWorkshopRingDesignApi';
import { useWorkshopDesignCommand } from '../application/useWorkshopDesignCommand';
import { useAuth } from '../../../shared/auth/AuthProvider';
import { useLanguage } from '../../../shared/i18n/I18nProvider';
import { listWorkshopPackages } from '../../../api/workshops';
import RingPreview from './RingPreview';
import { derivePreviewModel, getDifficultyMeta } from '../domain/designPreview';
import { getErrorMessage } from '../domain/designErrors';
import { messages } from '../messages';
import '../ring-design.css';

export default function DesignEvaluationPage() {
  const { packageReference, designId } = useParams();
  const navigate = useNavigate();
  const { status, userId } = useAuth();
  const isAuthenticated = status === 'signed-in' || Boolean(userId);
  const api = useWorkshopRingDesignApi();

  const [design, setDesign] = useState(null);
  const [loading, setLoading] = useState(true);
  const [fetchError, setFetchError] = useState(null);
  const [showArchiveConfirm, setShowArchiveConfirm] = useState(false);
  const [publishedPackages, setPublishedPackages] = useState([]);

  const { loading: archiving, error: archiveError, executeArchive } = useWorkshopDesignCommand(isAuthenticated);

  useEffect(() => {
    listWorkshopPackages()
      .then((data) => setPublishedPackages(Array.isArray(data) ? data : []))
      .catch(() => {});
  }, []);

  useEffect(() => {
    if (status === 'loading') return;
    async function load() {
      setLoading(true);
      setFetchError(null);
      try {
        const res = await api.getDesign(designId, null, isAuthenticated);
        setDesign(res);
      } catch (err) {
        setFetchError(getErrorMessage(err));
      } finally {
        setLoading(false);
      }
    }
    load();
  }, [api, designId, isAuthenticated, status]);

  const previewModel = useMemo(() => {
    if (!design?.componentSnapshot) return derivePreviewModel(null);
    return derivePreviewModel(design.componentSnapshot);
  }, [design]);

  const { language } = useLanguage();
  const lang = language === 'en' ? 'en' : 'vi';
  const t = messages[lang];

  const recommendation = useMemo(() => {
    const evalData = design?.evaluation || {};
    const gemCount = previewModel.stones?.length || (previewModel.gemstones || []).reduce((sum, g) => sum + (Number(g.quantity) || 1), 0);
    const rawDiff = evalData.difficultyScore ?? previewModel.difficultyScore ?? 1;
    const diffScore = typeof rawDiff === 'number' ? rawDiff : (parseFloat(rawDiff) || 1);

    const waxPkg = publishedPackages.find((p) => p.name?.toLowerCase().includes('wax')) || { packageId: '10', name: 'Gói Wax Casting' };
    const silverClayPkg = publishedPackages.find((p) => p.name?.toLowerCase().includes('silver clay')) || { packageId: '8', name: 'Gói Silver Clay' };
    const signaturePkg = publishedPackages.find((p) => p.name?.toLowerCase().includes('signature')) || { packageId: '6', name: 'Gói Signature Workshop' };

    if (diffScore > 9) {
      return {
        packageId: waxPkg.packageId,
        packageName: waxPkg.name || 'Gói Wax Casting',
        reason: 'Nghệ thuật đúc sáp tinh xảo đảm bảo mọi chi tiết trên thiết kế của bạn sẽ nổi bật hơn',
        note: 'workshop wax casting sẽ mất 2 buổi vào 2 ngày khác nhau',
      };
    }
    if (gemCount > 5) {
      return {
        packageId: silverClayPkg.packageId,
        packageName: silverClayPkg.name || 'Gói Silver Clay',
        reason: 'Những viên đá quý của bạn sẽ tỏa sáng như những vì sao khi bạn chọn gói silverclay',
        note: null,
      };
    }
    return {
      packageId: signaturePkg.packageId,
      packageName: signaturePkg.name || 'Gói Signature Workshop',
      reason: 'Gói signature workshop đảm bảo thiết kế tinh tế của bạn sẽ đạt được kết quả tốt nhất',
      note: null,
    };
  }, [design, previewModel, publishedPackages]);

  const handleArchive = async () => {
    try {
      const res = await executeArchive(designId, design.designVersion);
      setDesign(res);
      setShowArchiveConfirm(false);
    } catch {
      // Error handled by command hook
    }
  };

  const handleProceedBooking = () => {
    navigate('/workshops');
  };

  const handleConsultation = () => {
    if (!design) return;
    navigate(`/member/consultations?designReference=${encodeURIComponent(design.designId)}&designVersion=${encodeURIComponent(design.designVersion || 1)}`);
  };

  if (loading) {
    return (
      <div className="ring-design-container" style={{ textAlign: 'center', padding: '4rem' }}>
        <p>Đang tải kết quả thẩm định thiết kế...</p>
      </div>
    );
  }

  if (fetchError) {
    return (
      <div className="ring-design-container">
        <div className="ring-error-card" role="alert">
          {fetchError}
        </div>
        <button
          type="button"
          className="ring-btn-secondary"
          onClick={() => navigate(`/workshops/${encodeURIComponent(packageReference)}/design`)}
        >
          {t.restartFlow}
        </button>
      </div>
    );
  }

  const evalData = design?.evaluation || {};

  return (
    <div className="ring-design-container" data-testid="design-evaluation-page">
      <div className="ring-design-header">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '1rem' }}>
          <div>
            <h1 className="ring-design-title">{t.evaluationTitle}</h1>
            <p className="ring-design-subtitle">
              Mã thiết kế: <strong>{design?.designId}</strong> · {t.versionLabel} {design?.designVersion}
            </p>
          </div>
          <div>
            <span className={`ring-status-badge ${design?.status?.toLowerCase() || 'draft'}`}>
              {t[design?.status] || design?.status}
            </span>
          </div>
        </div>
        <div className="ring-notice-banner" role="note">
          <span>ℹ️</span>
          <span>{t.advisoryWarning}</span>
        </div>
      </div>

      {archiveError && (
        <div className="ring-error-card" role="alert">
          {archiveError}
        </div>
      )}

      {/* Surface Texture Warning */}
      {(evalData.warnings?.length > 0 || previewModel.showSurfaceTextureWarning) && (
        <div
          style={{
            marginBottom: '1rem',
            padding: '0.85rem 1rem',
            borderRadius: '8px',
            background: '#fffbeb',
            border: '1.5px solid #f59e0b',
            color: '#92400e',
            fontSize: '0.875rem',
            lineHeight: 1.5,
          }}
          data-testid="evaluation-surface-texture-warning"
        >
          {evalData.warnings?.[0] || t.surfaceTextureWarning}
        </div>
      )}

      <div className="ring-configurator-layout">
        {/* Left Column: Evaluation Metrics & Details */}
        <div className="ring-controls-panel">
          {/* Card: Advisory Price & Difficulty */}
          <div className="ring-section-card">
            <h2 className="ring-section-title">Chỉ số ước tính kỹ thuật</h2>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: '1rem', marginTop: '1rem' }}>
              <div style={{ background: '#f8fafc', padding: '1.25rem', borderRadius: '8px', border: '1px solid #e2e8f0' }}>
                <div style={{ fontSize: '0.85rem', color: '#64748b', marginBottom: '0.35rem' }}>
                  {t.estimatedPriceLabel}
                </div>
                <div style={{ fontSize: '1.5rem', fontWeight: 700, color: '#b45309' }}>
                  {evalData.estimatedPrice
                    ? `${Number(evalData.estimatedPrice).toLocaleString('vi-VN')} đ`
                    : 'Theo gói'}
                </div>
                <div style={{ fontSize: '0.75rem', color: '#94a3b8', marginTop: '0.25rem' }}>
                  {t.advisoryBadge} (Không đổi cọc gói)
                </div>
              </div>

              <div style={{ background: '#f8fafc', padding: '1.25rem', borderRadius: '8px', border: '1px solid #e2e8f0' }}>
                <div style={{ fontSize: '0.85rem', color: '#64748b', marginBottom: '0.35rem' }}>
                  Khối lượng ước tính
                </div>
                <div style={{ fontSize: '1.5rem', fontWeight: 700, color: '#0f172a' }}>
                  {evalData.estimatedWeightGrams
                    ? `${evalData.estimatedWeightGrams} g`
                    : `${previewModel.estimatedWeightGrams?.toFixed(2) || '3.00'} g`}
                </div>
                <div style={{ fontSize: '0.75rem', color: evalData.excessMaterialCost && Number(evalData.excessMaterialCost) > 0 ? '#b45309' : '#166534', marginTop: '0.25rem' }}>
                  {evalData.excessMaterialCost && Number(evalData.excessMaterialCost) > 0
                    ? `Vượt mức (+${Number(evalData.excessMaterialCost).toLocaleString('vi-VN')} đ)`
                    : 'Trong định mức chuẩn (≤ 3.0g)'}
                </div>
              </div>

              {(() => {
                const diffMeta = getDifficultyMeta(evalData.difficultyScore);
                return (
                  <div
                    style={{ background: '#f8fafc', padding: '1.25rem', borderRadius: '8px', border: '1px solid #e2e8f0' }}
                    data-testid="evaluation-difficulty-card"
                  >
                    <div style={{ fontSize: '0.85rem', color: '#64748b', marginBottom: '0.35rem' }}>
                      {t.difficultyLabel}
                    </div>
                    <div style={{ display: 'flex', alignItems: 'baseline', gap: '0.5rem' }}>
                      <span style={{ fontSize: '1.5rem', fontWeight: 700, color: '#0f172a' }}>
                        {diffMeta.score} / 10
                      </span>
                      <span
                        style={{
                          fontSize: '0.8rem',
                          fontWeight: 600,
                          color: diffMeta.badgeColor,
                          backgroundColor: diffMeta.badgeBg,
                          padding: '0.15rem 0.5rem',
                          borderRadius: '4px',
                        }}
                        data-testid="difficulty-badge"
                      >
                        {diffMeta.label}
                      </span>
                    </div>
                    <div style={{ fontSize: '0.75rem', color: '#475569', marginTop: '0.35rem', lineHeight: 1.4 }} data-testid="difficulty-note">
                      {diffMeta.isVeryHard ? (
                        <span style={{ color: '#b91c1c', fontWeight: 600 }}>
                          ⚠️ Ghi chú: {diffMeta.note}
                        </span>
                      ) : (
                        <span>Ghi chú: {diffMeta.note}</span>
                      )}
                    </div>
                    <div style={{ fontSize: '0.7rem', color: '#94a3b8', marginTop: '0.25rem' }}>
                      Thang đo: 1-3: Dễ · &gt;3-6: Trung bình · &gt;6-9: Khó · &gt;9: Rất khó
                    </div>
                    {diffMeta.isVeryHard && (
                      <div
                        style={{
                          marginTop: '0.6rem',
                          padding: '0.5rem 0.75rem',
                          background: '#fef2f2',
                          border: '1px solid #fecaca',
                          borderRadius: '6px',
                          color: '#991b1b',
                          fontSize: '0.75rem',
                          lineHeight: 1.4,
                        }}
                        data-testid="difficulty-very-hard-warning"
                      >
                        💡 <strong>Khuyến cáo:</strong> {diffMeta.advice}
                      </div>
                    )}
                  </div>
                );
              })()}
            </div>
          </div>

          {/* Card: Configuration Breakdown */}
          <div className="ring-section-card">
            <h2 className="ring-section-title">{t.summaryHeading}</h2>
            <div className="ring-summary-box" style={{ background: 'transparent', padding: 0 }}>
              <div className="ring-summary-item">
                <span className="ring-summary-label">{t.materialSummary}</span>
                <span className="ring-summary-value">{previewModel.material?.label}</span>
              </div>
              <div className="ring-summary-item">
                <span className="ring-summary-label">{t.shapeSummary}</span>
                <span className="ring-summary-value">{previewModel.shape?.label}</span>
              </div>
              <div className="ring-summary-item">
                <span className="ring-summary-label">{t.sizeSummary}</span>
                <span className="ring-summary-value">Size {previewModel.ringSize} ({previewModel.circumferenceMm} mm)</span>
              </div>
              <div className="ring-summary-item">
                <span className="ring-summary-label">{t.thicknessSummary}</span>
                <span className="ring-summary-value">{previewModel.thicknessMm?.toFixed(1)} mm</span>
              </div>
              <div className="ring-summary-item">
                <span className="ring-summary-label">{t.bandWidthSummary}</span>
                <span className="ring-summary-value">{previewModel.bandWidthMm?.toFixed(1)} mm</span>
              </div>
              <div className="ring-summary-item">
                <span className="ring-summary-label">{t.weightSummary}</span>
                <span className="ring-summary-value">
                  {evalData.estimatedWeightGrams || previewModel.estimatedWeightGrams?.toFixed(2)} g
                  {evalData.excessMaterialCost && Number(evalData.excessMaterialCost) > 0 && (
                    <span style={{ fontSize: '0.8rem', color: '#b45309', display: 'block' }}>
                      (Phụ phí khối lượng: +{Number(evalData.excessMaterialCost).toLocaleString('vi-VN')} đ)
                    </span>
                  )}
                </span>
              </div>
              <div className="ring-summary-item">
                <span className="ring-summary-label">{t.gemstonesSummary}</span>
                <span className="ring-summary-value">
                  {previewModel.gemstoneGroups?.length > 0
                    ? previewModel.gemstoneGroups.map((g) => `${g.quantity}x ${g.label}`).join(', ')
                    : previewModel.gemstones?.length > 0
                      ? previewModel.gemstones.map((g) => `${g.quantity || 1}x ${g.label}`).join(', ')
                      : t.none}
                </span>
              </div>
              <div className="ring-summary-item">
                <span className="ring-summary-label">{t.attachmentsSummary}</span>
                <span className="ring-summary-value">
                  {previewModel.attachments.length > 0
                    ? previewModel.attachments.map((a) => `${a.quantity}x ${a.reference}`).join(', ')
                    : t.none}
                </span>
              </div>
              <div className="ring-summary-item" data-testid="eval-engraving-items">
                <span className="ring-summary-label">{t.engravingSummary}</span>
                <div className="ring-summary-value">
                  {previewModel.engravingItems && previewModel.engravingItems.length > 0 ? (
                    <div style={{ display: 'flex', flexDirection: 'column', gap: '0.25rem' }}>
                      {previewModel.engravingItems.map((item, idx) => (
                        <div key={item.id || idx} style={{ fontSize: '0.85rem' }} data-testid={`eval-engraving-item-${idx}`}>
                          <span style={{
                            display: 'inline-block',
                            fontSize: '0.7rem',
                            padding: '1px 5px',
                            borderRadius: '4px',
                            background: '#e0e7ff',
                            color: '#3730a3',
                            marginRight: '0.35rem',
                            fontWeight: 600
                          }}>
                            {lang === 'en' ? `Pos #${idx + 1}` : `Vị trí #${idx + 1}`}
                          </span>
                          <strong>"{item.content}"</strong>
                          <span style={{ color: '#64748b', fontSize: '0.75rem', marginLeft: '0.35rem' }}>
                            ({item.placement === 'OUTER_BAND' ? (lang === 'en' ? 'Outer band' : 'Mặt ngoài') : (lang === 'en' ? 'Inner band' : 'Mặt trong')})
                          </span>
                        </div>
                      ))}
                    </div>
                  ) : previewModel.engravingText ? (
                    `"${previewModel.engravingText}"`
                  ) : (
                    t.none
                  )}
                  {previewModel.standardEngravingCost > 0 && (
                    <span style={{ fontSize: '0.8rem', color: '#0369a1', display: 'block', fontWeight: 500, marginTop: '0.25rem' }}>
                      (+80.000 đ)
                    </span>
                  )}
                </div>
              </div>
              {previewModel.customImages?.length > 0 && (
                <div className="ring-summary-item" data-testid="eval-custom-images">
                  <span className="ring-summary-label">{lang === 'en' ? 'Laser images' : 'Ảnh khắc laser'}</span>
                  <div className="ring-summary-value">
                    <span>
                      {previewModel.customImages.length} {lang === 'en' ? 'image(s)' : 'ảnh'}
                      <span style={{ fontSize: '0.8rem', color: '#0369a1', display: 'block', fontWeight: 500 }}>
                        (+{(previewModel.customImages.length * 100000).toLocaleString('vi-VN')} đ)
                      </span>
                    </span>
                    <div style={{ display: 'flex', gap: '0.5rem', marginTop: '0.5rem', flexWrap: 'wrap' }}>
                      {previewModel.customImages.map((img, idx) => {
                        const imgUrl = img.previewDataUrl || img.processedPreviewReference || img.imageReference || img.imageUrl;
                        return (
                          <div key={img.id || idx} style={{ border: '1px solid #cbd5e1', borderRadius: '4px', padding: '4px', background: '#f8fafc', textAlign: 'center' }}>
                            {imgUrl ? (
                              <img
                                src={imgUrl}
                                alt={`Laser ${idx + 1}`}
                                style={{ width: '56px', height: '56px', objectFit: 'contain', imageRendering: 'pixelated', display: 'block', margin: '0 auto' }}
                              />
                            ) : (
                              <div style={{ width: '56px', height: '56px', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: '0.65rem', color: '#ef4444' }}>
                                {lang === 'en' ? 'Image error' : 'Lỗi ảnh'}
                              </div>
                            )}
                            <div style={{ fontSize: '0.65rem', color: '#64748b', marginTop: '2px' }}>
                              C:{img.contrast ?? 50} S:{img.sharpness ?? 50}
                            </div>
                            <div style={{ fontSize: '0.65rem', color: '#94a3b8' }}>
                              {img.placement === 'OUTER_BAND' ? (lang === 'en' ? 'Outer' : 'Ngoài') : (lang === 'en' ? 'Inner' : 'Trong')}
                            </div>
                          </div>
                        );
                      })}
                    </div>
                  </div>
                </div>
              )}
            </div>
          </div>
        </div>

        {/* Right Column: Preview & Final Actions */}
        <div className="ring-preview-panel">
          <RingPreview previewModel={previewModel} readOnly={true} />

          <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem', marginTop: '1rem' }}>
            <button
              type="button"
              className="ring-btn-primary"
              onClick={handleProceedBooking}
              data-testid="proceed-booking-btn"
            >
              {t.proceedToBooking}
            </button>

            {design?.status !== 'LOCKED' && design?.status !== 'ARCHIVED' && (
              <button
                type="button"
                className="ring-btn-secondary"
                onClick={() =>
                  navigate(
                    `/workshops/${encodeURIComponent(packageReference)}/design/configure?mode=EDIT&designId=${encodeURIComponent(
                      designId
                    )}`
                  )
                }
              >
                {t.editDesign}
              </button>
            )}

            <button
              type="button"
              className="ring-btn-secondary"
              onClick={handleConsultation}
              data-testid="consult-design-btn"
            >
              {lang === 'en' ? 'Design Consultation' : 'Tư vấn thiết kế'}
            </button>

            {design?.status !== 'ARCHIVED' && design?.status !== 'LOCKED' && design?.status !== 'ATTACHED_TO_BOOKING' && (
              <button
                type="button"
                className="ring-btn-secondary"
                style={{ color: '#ef4444', borderColor: '#fca5a5' }}
                disabled={archiving}
                onClick={() => setShowArchiveConfirm(true)}
                data-testid="archive-design-btn"
              >
                {archiving ? t.archiving : t.archiveDesign}
              </button>
            )}

            <div
              style={{
                marginTop: '1rem',
                paddingTop: '1rem',
                borderTop: '1px solid #e2e8f0',
                display: 'flex',
                flexDirection: 'column',
                gap: '0.5rem',
              }}
              data-testid="workshop-recommendation-card"
            >
              <div style={{ fontWeight: 700, fontSize: '1rem', color: '#0f172a' }}>
                Recommended
              </div>
              <button
                type="button"
                className="ring-btn-primary"
                style={{
                  backgroundColor: '#0284c7',
                  borderColor: '#0284c7',
                  color: '#ffffff',
                }}
                onClick={() => navigate(`/workshops/${encodeURIComponent(recommendation.packageId)}`)}
                data-testid="recommended-workshop-btn"
              >
                {recommendation.packageName}
              </button>
              <p
                style={{
                  margin: 0,
                  fontSize: '0.85rem',
                  color: '#475569',
                  lineHeight: 1.45,
                }}
                data-testid="recommended-workshop-reason"
              >
                {recommendation.reason}
              </p>
              {recommendation.note && (
                <p
                  style={{
                    margin: 0,
                    fontSize: '0.8rem',
                    color: '#b45309',
                    fontWeight: 600,
                    lineHeight: 1.4,
                  }}
                  data-testid="recommended-workshop-note"
                >
                  ⚠️ Ghi chú: {recommendation.note}
                </p>
              )}
            </div>
          </div>
        </div>
      </div>

      {showArchiveConfirm && (
        <div
          role="dialog"
          aria-modal="true"
          data-testid="archive-confirm-dialog"
          style={{
            position: 'fixed',
            inset: 0,
            backgroundColor: 'rgba(0, 0, 0, 0.5)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            zIndex: 1000,
          }}
        >
          <div
            style={{
              backgroundColor: '#fff',
              borderRadius: '8px',
              padding: '1.5rem',
              maxWidth: '420px',
              width: '90%',
              boxShadow: '0 4px 6px -1px rgba(0, 0, 0, 0.1)',
            }}
          >
            <h3 style={{ fontSize: '1.125rem', fontWeight: 600, color: '#1e293b', marginBottom: '0.75rem' }}>
              {lang === 'en' ? 'Archive Ring Design' : 'Lưu trữ mẫu thiết kế'}
            </h3>
            <p style={{ fontSize: '0.875rem', color: '#64748b', marginBottom: '1.25rem', lineHeight: 1.5 }}>
              {lang === 'en'
                ? 'Are you sure you want to archive this design? It will no longer be editable.'
                : 'Bạn có chắc chắn muốn lưu trữ mẫu thiết kế này không? Mẫu thiết kế sẽ chuyển sang trạng thái đã lưu trữ.'}
            </p>
            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '0.75rem' }}>
              <button
                type="button"
                className="ring-btn-secondary"
                onClick={() => setShowArchiveConfirm(false)}
                disabled={archiving}
                data-testid="cancel-archive-btn"
              >
                {lang === 'en' ? 'Cancel' : 'Hủy'}
              </button>
              <button
                type="button"
                className="ring-btn-primary"
                style={{ backgroundColor: '#ef4444', borderColor: '#ef4444', color: '#fff' }}
                onClick={handleArchive}
                disabled={archiving}
                data-testid="confirm-archive-btn"
              >
                {archiving ? t.archiving : (lang === 'en' ? 'Confirm Archive' : 'Xác nhận lưu trữ')}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

