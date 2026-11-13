import React, { useEffect, useState, useMemo } from 'react';
import { useParams, useNavigate } from 'react-router';
import { useWorkshopRingDesignApi } from '../api/useWorkshopRingDesignApi';
import { useWorkshopDesignCommand } from '../application/useWorkshopDesignCommand';
import { useAuth } from '../../../shared/auth/AuthProvider';
import RingPreview from './RingPreview';
import { derivePreviewModel } from '../domain/designPreview';
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

  const lang = 'vi';
  const t = messages[lang];

  const handleArchive = async () => {
    if (!window.confirm('Bạn có chắc chắn muốn lưu trữ mẫu thiết kế này không?')) return;
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

      <div className="ring-configurator-layout">
        {/* Left Column: Evaluation Metrics & Details */}
        <div className="ring-controls-panel">
          {/* Card: Advisory Price & Difficulty */}
          <div className="ring-section-card">
            <h2 className="ring-section-title">Chỉ số ước tính kỹ thuật</h2>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1.5rem', marginTop: '1rem' }}>
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
                  {t.difficultyLabel}
                </div>
                <div style={{ fontSize: '1.5rem', fontWeight: 700, color: '#0f172a' }}>
                  {evalData.difficultyScore ? `${evalData.difficultyScore} / 10` : '1.0 / 10'}
                </div>
                <div style={{ fontSize: '0.75rem', color: '#94a3b8', marginTop: '0.25rem' }}>
                  Quy tắc: {evalData.rulesVersion || 'v1.0'}
                </div>
              </div>
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
                <span className="ring-summary-value">Size {previewModel.ringSize}</span>
              </div>
              <div className="ring-summary-item">
                <span className="ring-summary-label">{t.gemstonesSummary}</span>
                <span className="ring-summary-value">
                  {previewModel.gemstones.length > 0
                    ? previewModel.gemstones.map((g) => `${g.quantity}x ${g.label}`).join(', ')
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
              <div className="ring-summary-item">
                <span className="ring-summary-label">{t.engravingSummary}</span>
                <span className="ring-summary-value">
                  {previewModel.engravingText ? `"${previewModel.engravingText}"` : t.none}
                </span>
              </div>
            </div>
          </div>
        </div>

        {/* Right Column: Preview & Final Actions */}
        <div className="ring-preview-panel">
          <RingPreview previewModel={previewModel} />

          <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem', marginTop: '1rem' }}>
            {design?.status !== 'ARCHIVED' && design?.status !== 'LOCKED' && (
              <button
                type="button"
                className="ring-btn-primary"
                onClick={handleProceedBooking}
                data-testid="proceed-booking-btn"
              >
                {t.proceedToBooking} →
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

