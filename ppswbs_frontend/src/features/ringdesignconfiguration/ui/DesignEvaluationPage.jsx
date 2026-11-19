import React, { useEffect, useState, useMemo } from 'react';
import { useParams, useNavigate } from 'react-router';
import { useWorkshopRingDesignApi } from '../api/useWorkshopRingDesignApi';
import { useWorkshopDesignCommand } from '../application/useWorkshopDesignCommand';
import { useAuth } from '../../../shared/auth/AuthProvider';
import { useLanguage } from '../../../shared/i18n/I18nProvider';
import RingPreview from './RingPreview';
import { derivePreviewModel, getDifficultyMeta } from '../domain/designPreview';
import { getTranslatedOption } from '../domain/designTranslations';
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

  const { loading: archiving, error: archiveError, executeArchive } = useWorkshopDesignCommand(isAuthenticated);

  useEffect(() => {
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
  }, [api, designId, isAuthenticated]);

  const previewModel = useMemo(() => {
    if (!design?.componentSnapshot) return derivePreviewModel(null);
    return derivePreviewModel(design.componentSnapshot);
  }, [design]);

  const { language } = useLanguage();
  const lang = language === 'en' ? 'en' : 'vi';
  const t = messages[lang] || messages.vi;

  const handleArchive = async () => {
    if (!window.confirm(t.archiveConfirm || (language === 'en' ? 'Are you sure you want to archive this design?' : 'Bạn có chắc chắn muốn lưu trữ mẫu thiết kế này không?'))) return;
    try {
      const res = await executeArchive(designId, design.designVersion);
      setDesign(res);
    } catch {
      // Error handled by command hook
    }
  };

  const handleProceedBooking = () => {
    // Navigate to Workshop booking flow with package and design context
    navigate(`/workshops/booking?packageReference=${encodeURIComponent(packageReference)}&designId=${encodeURIComponent(designId)}`);
  };

  if (loading) {
    return (
      <div className="ring-design-container" style={{ textAlign: 'center', padding: '4rem' }}>
        <p>{t.loadingEval || (language === 'en' ? 'Loading design evaluation...' : 'Đang tải kết quả thẩm định thiết kế...')}</p>
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
              {language === 'en' ? 'Design Code' : 'Mã thiết kế'}: <strong>{design?.designId}</strong> · {t.versionLabel} {design?.designVersion}
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
            <h2 className="ring-section-title">
              {language === 'en' ? 'Technical Estimation Metrics' : 'Chỉ số ước tính kỹ thuật'}
            </h2>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: '1rem', marginTop: '1rem' }}>
              <div style={{ background: '#f8fafc', padding: '1.25rem', borderRadius: '8px', border: '1px solid #e2e8f0' }}>
                <div style={{ fontSize: '0.85rem', color: '#64748b', marginBottom: '0.35rem' }}>
                  {t.estimatedPriceLabel}
                </div>
                <div style={{ fontSize: '1.5rem', fontWeight: 700, color: '#b45309' }}>
                  {evalData.estimatedPrice
                    ? `${Number(evalData.estimatedPrice).toLocaleString(language === 'en' ? 'en-US' : 'vi-VN')} ${language === 'en' ? 'VND' : 'đ'}`
                    : (language === 'en' ? 'By package' : 'Theo gói')}
                </div>
                <div style={{ fontSize: '0.75rem', color: '#94a3b8', marginTop: '0.25rem' }}>
                  {t.advisoryBadge} ({t.depositUnchanged || (language === 'en' ? 'Deposit unchanged' : 'Không đổi cọc gói')})
                </div>
              </div>

              <div style={{ background: '#f8fafc', padding: '1.25rem', borderRadius: '8px', border: '1px solid #e2e8f0' }}>
                <div style={{ fontSize: '0.85rem', color: '#64748b', marginBottom: '0.35rem' }}>
                  {t.weightSummary || (language === 'en' ? 'Estimated Weight' : 'Khối lượng ước tính')}
                </div>
                <div style={{ fontSize: '1.5rem', fontWeight: 700, color: '#0f172a' }}>
                  {evalData.estimatedWeightGrams
                    ? `${evalData.estimatedWeightGrams} g`
                    : `${previewModel.estimatedWeightGrams?.toFixed(2) || '3.00'} g`}
                </div>
                <div style={{ fontSize: '0.75rem', color: evalData.excessMaterialCost && Number(evalData.excessMaterialCost) > 0 ? '#b45309' : '#166534', marginTop: '0.25rem' }}>
                  {evalData.excessMaterialCost && Number(evalData.excessMaterialCost) > 0
                    ? `${t.excessWeightNotice || (language === 'en' ? 'Excess weight' : 'Vượt mức')} (+${Number(evalData.excessMaterialCost).toLocaleString(language === 'en' ? 'en-US' : 'vi-VN')} ${language === 'en' ? 'VND' : 'đ'})`
                    : (t.withinStandardWeight || (language === 'en' ? 'Within standard quota (≤ 3.0g)' : 'Trong định mức chuẩn (≤ 3.0g)'))}
                </div>
              </div>

              {(() => {
                const diffMeta = getDifficultyMeta(evalData.difficultyScore, language);
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
                          ⚠️ {language === 'en' ? 'Note:' : 'Ghi chú:'} {diffMeta.note}
                        </span>
                      ) : (
                        <span>{language === 'en' ? 'Note:' : 'Ghi chú:'} {diffMeta.note}</span>
                      )}
                    </div>
                    <div style={{ fontSize: '0.7rem', color: '#94a3b8', marginTop: '0.25rem' }}>
                      {t.difficultyScaleNotice || (language === 'en' ? 'Scale: 1-3: Easy · >3-6: Medium · >6-9: Hard · >9: Very Hard' : 'Thang đo: 1-3: Dễ · >3-6: Trung bình · >6-9: Khó · >9: Rất khó')}
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
                        💡 <strong>{language === 'en' ? 'Recommendation:' : 'Khuyến cáo:'}</strong> {diffMeta.advice}
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
                <span className="ring-summary-value">
                  {getTranslatedOption('materials', design?.componentSnapshot?.materialReference, language, { name: previewModel.material?.label }).name}
                </span>
              </div>
              <div className="ring-summary-item">
                <span className="ring-summary-label">{t.shapeSummary}</span>
                <span className="ring-summary-value">
                  {getTranslatedOption('shapes', design?.componentSnapshot?.shapeReference, language, { name: previewModel.shape?.label }).name}
                </span>
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
                <span className="ring-summary-label">{t.weightSummary || (language === 'en' ? 'Estimated Weight' : 'Khối lượng ước tính')}</span>
                <span className="ring-summary-value">
                  {evalData.estimatedWeightGrams || previewModel.estimatedWeightGrams?.toFixed(2)} g
                  {evalData.excessMaterialCost && Number(evalData.excessMaterialCost) > 0 && (
                    <span style={{ fontSize: '0.8rem', color: '#b45309', display: 'block' }}>
                      ({t.surchargeWeightLabel || (language === 'en' ? 'Excess weight surcharge' : 'Phụ phí khối lượng')}: +{Number(evalData.excessMaterialCost).toLocaleString(language === 'en' ? 'en-US' : 'vi-VN')} {language === 'en' ? 'VND' : 'đ'})
                    </span>
                  )}
                </span>
              </div>
              <div className="ring-summary-item">
                <span className="ring-summary-label">{t.gemstonesSummary}</span>
                <span className="ring-summary-value">
                  {previewModel.gemstoneGroups?.length > 0
                    ? previewModel.gemstoneGroups.map((g) => `${g.quantity}x ${getTranslatedOption('components', g.reference, language, { name: g.label }).name}`).join(', ')
                    : previewModel.gemstones?.length > 0
                      ? previewModel.gemstones.map((g) => `${g.quantity || 1}x ${getTranslatedOption('components', g.reference, language, { name: g.label }).name}`).join(', ')
                      : t.none}
                </span>
              </div>
              <div className="ring-summary-item">
                <span className="ring-summary-label">{t.attachmentsSummary}</span>
                <span className="ring-summary-value">
                  {previewModel.attachments.length > 0
                    ? previewModel.attachments.map((a) => `${a.quantity}x ${getTranslatedOption('attachments', a.reference, language, { name: a.reference }).name}`).join(', ')
                    : t.none}
                </span>
              </div>
              <div className="ring-summary-item">
                <span className="ring-summary-label">{t.engravingSummary}</span>
                <span className="ring-summary-value">
                  {previewModel.engravingItems?.length > 0 ? (
                    <div>
                      {previewModel.engravingItems.map((it, i) => (
                        <div key={it.id || i} style={{ fontSize: '0.85rem' }} data-testid={`eval-engraving-item-${i}`}>
                          #{i + 1}: {it.type === 'SYMBOL' ? (language === 'en' ? `Symbol "${it.content}"` : `Biểu tượng "${it.content}"`) : (language === 'en' ? `Text "${it.content}"` : `Chữ "${it.content}"`)} ({it.placement === 'OUTER_BAND' ? (language === 'en' ? 'Outer band' : 'Mặt ngoài') : (language === 'en' ? 'Inner band' : 'Mặt trong')})
                        </div>
                      ))}
                    </div>
                  ) : previewModel.engravingText ? (
                    `"${previewModel.engravingText}"`
                  ) : (
                    t.none
                  )}
                  {previewModel.standardEngravingCost > 0 && (
                    <span style={{ fontSize: '0.8rem', color: '#0369a1', display: 'block', fontWeight: 500 }}>
                      (+{Number(80000).toLocaleString(language === 'en' ? 'en-US' : 'vi-VN')} {language === 'en' ? 'VND' : 'đ'})
                    </span>
                  )}
                </span>
              </div>
              {previewModel.customImages?.length > 0 && (
                <div className="ring-summary-item" data-testid="eval-custom-images">
                  <span className="ring-summary-label">{language === 'en' ? 'Laser Engraving' : 'Ảnh khắc laser'}</span>
                  <span className="ring-summary-value">
                    {previewModel.customImages.length} {language === 'en' ? (previewModel.customImages.length > 1 ? 'images' : 'image') : 'ảnh'}
                    <span style={{ fontSize: '0.8rem', color: '#0369a1', display: 'block', fontWeight: 500 }}>
                      (+{(previewModel.customImages.length * 100000).toLocaleString(language === 'en' ? 'en-US' : 'vi-VN')} {language === 'en' ? 'VND' : 'đ'})
                    </span>
                    {previewModel.customImages.map((img, i) => (
                      <span key={img.id || i} style={{ display: 'block', fontSize: '0.75rem', color: '#64748b' }} data-testid={`eval-custom-img-${i}`}>
                        #{i + 1}: {img.placement === 'OUTER_BAND' ? (language === 'en' ? 'Outer band' : 'Mặt ngoài') : (language === 'en' ? 'Inner band' : 'Mặt trong')} ({language === 'en' ? 'Contrast' : 'Tương phản'}: {img.contrast !== undefined ? img.contrast : 50}, {language === 'en' ? 'Sharpness' : 'Sắc nét'}: {img.sharpness !== undefined ? img.sharpness : 50})
                      </span>
                    ))}
                  </span>
                </div>
              )}
            </div>
          </div>
        </div>

        {/* Right Column: Preview & Final Actions */}
        <div className="ring-preview-panel">
          <RingPreview previewModel={previewModel} readOnly={true} />

          <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem', marginTop: '1rem' }}>
            {design?.status !== 'ARCHIVED' && design?.status !== 'LOCKED' && (
              <button
                type="button"
                className="ring-btn-primary"
                onClick={handleProceedBooking}
                data-testid="proceed-booking-btn"
              >
                {t.proceedToBooking}
              </button>
            )}

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

            {design?.status !== 'ARCHIVED' && design?.status !== 'LOCKED' && design?.status !== 'ATTACHED_TO_BOOKING' && (
              <button
                type="button"
                className="ring-btn-secondary"
                style={{ color: '#ef4444', borderColor: '#fca5a5' }}
                disabled={archiving}
                onClick={handleArchive}
                data-testid="archive-design-btn"
              >
                {archiving ? t.archiving : t.archiveDesign}
              </button>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}

