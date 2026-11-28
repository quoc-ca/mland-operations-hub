import React, { useState, useMemo } from 'react';
import { Link, useLocation, useParams, useSearchParams } from 'react-router';
import { useAuth } from '../../../shared/auth/AuthProvider';
import { RequireSession } from '../../../shared/auth/RequireSession';
import { useT } from '../../../shared/i18n/I18nProvider';
import { ErrorNotice, Loading, SuccessNotice, useResource } from '../../../shared/ui/components';
import { useDesignReviewApi } from '../api/useDesignReviewApi';
import DesignImage from '../components/DesignImage';
import Design3dDetailModal from '../components/Design3dDetailModal';
import RingPreview from '../../ringdesignconfiguration/ui/RingPreview';
import { derivePreviewModel, getRingCircumferenceMm, getRingDiameterMm } from '../../ringdesignconfiguration/domain/designPreview';
import { messages as ringMessages } from '../../ringdesignconfiguration/messages';
import { designReviewMessages } from '../messages';
import { sharedMessages } from '../../../shared/ui/messages';

export default function DesignDetailPage() {
  const { designRequestId } = useParams();
  const [searchParams] = useSearchParams();
  const location = useLocation();
  const { status, userId } = useAuth();
  const isAuthenticated = status === 'signed-in' || Boolean(userId);
  const t = useT(designReviewMessages);
  const ts = useT(sharedMessages);
  const api = useDesignReviewApi();

  const tokenFromUrl = searchParams.get('token');
  const [copiedLink, setCopiedLink] = useState(false);
  const [selectedPreviewImage, setSelectedPreviewImage] = useState(null);
  const [is3dModalOpen, setIs3dModalOpen] = useState(false);

  const successMessage = location.state?.successMessage;

  const { loading, error, data, reload } = useResource(
    () => api.getDesignRequest(designRequestId, { auth: isAuthenticated, token: tokenFromUrl }),
    [designRequestId, isAuthenticated, tokenFromUrl]
  );

  const previewModel = useMemo(() => {
    if (!data?.designSnapshot && !data?.designReference) return null;
    return derivePreviewModel(data?.designSnapshot, (k) => ringMessages.vi[k] || k);
  }, [data]);

  function handleCopyCurrentLink() {
    navigator.clipboard.writeText(window.location.href).then(() => {
      setCopiedLink(true);
      setTimeout(() => setCopiedLink(false), 3000);
    });
  }

  if (!isAuthenticated) {
    return (
      <div className="design-container">
        <RequireSession requiredText="Vui lòng đăng nhập để xem tiến độ yêu cầu thiết kế." />
      </div>
    );
  }

  if (loading) {
    return (
      <div className="design-container">
        <Loading />
      </div>
    );
  }

  if (error) {
    return (
      <div className="design-container">
        <ErrorNotice error={error} messages={designReviewMessages} onReload={reload} />
      </div>
    );
  }

  if (!data) return null;

  const reviewStatus = data.reviewStatus || 'NEED_REVIEW';
  const isAccepted = reviewStatus === 'ACCEPTED';
  const isRejected = reviewStatus === 'REJECTED';
  const isDecided = isAccepted || isRejected;

  const reviewStatusBadgeClass = isAccepted
    ? 'badge-success'
    : isRejected
    ? 'badge-danger'
    : 'badge-warning';

  const reviewStatusText =
    reviewStatus === 'ACCEPTED'
      ? t('status.ACCEPTED')
      : reviewStatus === 'REJECTED'
      ? t('status.REJECTED')
      : t('status.NEED_REVIEW');

  const explanation = data.decisionExplanation || data.safeExplanation || '';
  const images = Array.isArray(data.images) ? data.images : [];
  const isGuestView = Boolean(tokenFromUrl) || (!isAuthenticated && data.guestSessionId);

  const ringSize = data.designSnapshot?.ringSize || previewModel?.ringSize || 12;
  const circMm = getRingCircumferenceMm(ringSize);
  const diamMm = getRingDiameterMm(ringSize);

  return (
    <div className="design-container">
      {successMessage && (
        <div style={{ marginBottom: '1.5rem' }}>
          <SuccessNotice>{successMessage}</SuccessNotice>
        </div>
      )}

      {isGuestView && (
        <div className="guest-tracking-card" style={{ marginBottom: '1.5rem', padding: '1rem 1.25rem' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '0.5rem' }}>
            <div>
              <strong>{t('detail.guestBanner')}</strong>
              <div className="small muted">{t('dd.keepLink')}</div>
            </div>
            <button type="button" className="button outline small" onClick={handleCopyCurrentLink}>
              {copiedLink ? `✓ ${t('guest.copied')}` : `📋 ${t('guest.copyLink')}`}
            </button>
          </div>
        </div>
      )}

      <header className="page-header">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '1rem' }}>
          <div>
            <h1>{t('detail.title')}</h1>
            <p className="muted">
              {t('detail.reference', { ref: data.requestReference || data.id })}
            </p>
          </div>
          <span className={`badge-tag ${reviewStatusBadgeClass}`} style={{ fontSize: '0.95rem', padding: '0.4rem 0.8rem' }}>
            {reviewStatusText}
          </span>
        </div>
      </header>

      {/* Reviewer Decision and Explanation Callout */}
      {isDecided && explanation && (
        <div className="reviewer-feedback-box">
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginBottom: '0.5rem' }}>
            <span style={{ fontSize: '1.25rem' }}>{isAccepted ? '✅' : '❌'}</span>
            <h3 style={{ margin: 0, color: isAccepted ? '#166534' : '#991b1b' }}>
              {isAccepted ? t('dd.acceptedTitle') : t('dd.rejectedTitle')}
            </h3>
          </div>
          <h4 style={{ margin: '0.75rem 0 0.25rem', fontSize: '0.9rem', color: '#475569' }}>
            {t('detail.decisionExplanation')}:
          </h4>
          <p style={{ margin: 0, whiteSpace: 'pre-wrap', lineHeight: 1.6, fontSize: '0.95rem' }}>
            {explanation}
          </p>
        </div>
      )}

      {!isDecided && (
        <div className="card" style={{ padding: '1.25rem', border: '1px solid #fef08a', background: '#fefce8', borderRadius: '8px', marginBottom: '1.5rem' }}>
          <div style={{ display: 'flex', gap: '0.75rem', alignItems: 'center' }}>
            <span style={{ fontSize: '1.5rem' }}>⏳</span>
            <div>
              <h3 style={{ margin: 0, color: '#854d0e' }}>{t('dd.pendingTitle')}</h3>
              <p style={{ margin: '0.25rem 0 0', color: '#713f12', fontSize: '0.9rem' }}>
                {t('dd.pendingBody')}
              </p>
            </div>
          </div>
        </div>
      )}

      {/* Linked Ring Design & SVG Preview */}
      {Boolean(data.designSnapshot || data.designReference) && (
        <section
          className="card"
          style={{
            marginBottom: '1.5rem',
            padding: '1.5rem',
            border: '1px solid #bae6fd',
            borderRadius: '8px',
            background: '#ffffff',
            boxShadow: '0 1px 3px rgba(0,0,0,0.05)',
          }}
          data-testid="linked-ring-design-section"
        >
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '0.75rem', marginBottom: '1.25rem', borderBottom: '1px solid #e0f2fe', paddingBottom: '0.75rem' }}>
            <h3 style={{ margin: 0, color: '#0369a1', fontSize: '1.15rem', display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
              <span>💍</span>
              <span>Thông tin chi tiết mẫu nhẫn thiết kế</span>
            </h3>
            <div style={{ display: 'flex', gap: '0.5rem', alignItems: 'center' }}>
              {data.designReference && (
                <span style={{ fontSize: '0.85rem', color: '#0284c7', background: '#e0f2fe', padding: '3px 8px', borderRadius: 4, fontWeight: 600 }}>
                  Mã thiết kế: <code>{data.designReference}</code>
                </span>
              )}
              <button
                type="button"
                className="button button-outline small"
                onClick={() => setIs3dModalOpen(true)}
                data-testid="open-specs-modal-btn"
                style={{ fontSize: '0.85rem' }}
              >
                🔍 Xem toàn màn hình
              </button>
            </div>
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(min(100%, 320px), 1fr))', gap: '1.5rem', alignItems: 'start' }}>
            {/* SVG Ring Preview */}
            <div style={{ background: '#f8fafc', padding: '1rem', borderRadius: 8, border: '1px solid #e2e8f0' }} data-testid="ring-svg-preview-container">
              <h4 style={{ margin: '0 0 0.5rem 0', fontSize: '0.9rem', color: '#475569' }}>
                Bản vẽ trực quan 2D / SVG:
              </h4>
              <RingPreview previewModel={previewModel} readOnly={true} />
            </div>

            {/* Specifications Details */}
            <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
              <h4 style={{ margin: '0 0 0.25rem 0', fontSize: '0.95rem', color: '#1e293b' }}>
                Thông số kỹ thuật chế tác
              </h4>
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(140px, 1fr))', gap: '0.75rem' }}>
                <div style={{ background: '#f8fafc', padding: '0.5rem 0.75rem', borderRadius: 6, border: '1px solid #f1f5f9' }}>
                  <span style={{ fontSize: '0.75rem', color: '#64748b', display: 'block' }}>Chất liệu:</span>
                  <strong style={{ fontSize: '0.9rem', color: '#0f172a' }}>
                    {previewModel?.material?.label || data.designSnapshot?.materialReference || 'Bạc Ý 925'}
                  </strong>
                </div>
                <div style={{ background: '#f8fafc', padding: '0.5rem 0.75rem', borderRadius: 6, border: '1px solid #f1f5f9' }}>
                  <span style={{ fontSize: '0.75rem', color: '#64748b', display: 'block' }}>Dáng nhẫn:</span>
                  <strong style={{ fontSize: '0.9rem', color: '#0f172a' }}>
                    {previewModel?.shape?.label || data.designSnapshot?.shapeReference || 'Tròn cổ điển'}
                  </strong>
                </div>
                <div style={{ background: '#f8fafc', padding: '0.5rem 0.75rem', borderRadius: 6, border: '1px solid #f1f5f9' }}>
                  <span style={{ fontSize: '0.75rem', color: '#64748b', display: 'block' }}>Kích thước:</span>
                  <strong style={{ fontSize: '0.9rem', color: '#0f172a' }}>
                    Size {ringSize} (~{circMm} mm / Ø {diamMm} mm)
                  </strong>
                </div>
                <div style={{ background: '#f8fafc', padding: '0.5rem 0.75rem', borderRadius: 6, border: '1px solid #f1f5f9' }}>
                  <span style={{ fontSize: '0.75rem', color: '#64748b', display: 'block' }}>Độ dày &amp; Bản rộng:</span>
                  <strong style={{ fontSize: '0.9rem', color: '#0f172a' }}>
                    {previewModel?.thicknessMm || 1.8} mm &times; {previewModel?.bandWidthMm || 3.5} mm
                  </strong>
                </div>
              </div>

              {(previewModel?.engravingText || data.designSnapshot?.engraving?.text) && (
                <div style={{ background: '#fdf4ff', padding: '0.5rem 0.75rem', borderRadius: 6, border: '1px solid #f5d0fe' }}>
                  <span style={{ fontSize: '0.75rem', color: '#86198f', display: 'block' }}>Nội dung khắc laser:</span>
                  <strong style={{ fontSize: '0.9rem', color: '#701a75' }}>
                    &ldquo;{previewModel?.engravingText || data.designSnapshot?.engraving?.text}&rdquo;
                  </strong>
                  <span style={{ fontSize: '0.75rem', color: '#a21caf', marginLeft: '0.5rem' }}>
                    ({previewModel?.engravingPlacement === 'OUTER_BAND' ? 'Mặt ngoài lòng nhẫn' : 'Mặt trong lòng nhẫn'})
                  </span>
                </div>
              )}

              {((previewModel?.gemstones?.length > 0) || (previewModel?.stones?.length > 0) || (data.designSnapshot?.components?.length > 0)) && (
                <div style={{ background: '#f0fdf4', padding: '0.5rem 0.75rem', borderRadius: 6, border: '1px solid #bbf7d0' }}>
                  <span style={{ fontSize: '0.75rem', color: '#166534', display: 'block' }}>Đá đính kèm:</span>
                  <strong style={{ fontSize: '0.9rem', color: '#14532d' }}>
                    {(previewModel?.stones || previewModel?.gemstones || data.designSnapshot?.components || []).length} viên đá trang trí
                  </strong>
                </div>
              )}
            </div>
          </div>
        </section>
      )}

      {/* Description */}
      {data.description && (
        <section className="card" style={{ marginBottom: '1.5rem', padding: '1.25rem', border: '1px solid #e2e8f0', borderRadius: '8px', background: '#ffffff' }}>
          <h3 style={{ marginTop: 0 }}>{t('workspace.descriptionLabel')}</h3>
          <p style={{ whiteSpace: 'pre-wrap', margin: 0 }}>{data.description}</p>
          {data.catalogueReference && (
            <p className="muted small" style={{ marginTop: '0.75rem' }}>
              {t('workspace.catalogueRefLabel')}: <code>{data.catalogueReference}</code>
            </p>
          )}
        </section>
      )}

      {/* Uploaded Images Gallery */}
      {images.length > 0 && (
        <section style={{ marginBottom: '2rem' }}>
          <h3>{t('detail.imagesGallery', { count: images.length })}</h3>
          <div className="image-upload-grid">
            {images.map((img, idx) => (
              <div
                key={img.imageReference || idx}
                className="image-upload-card"
                style={{ cursor: 'pointer' }}
                onClick={() => setSelectedPreviewImage(img)}
              >
                <DesignImage
                  image={img}
                  alt={img.originalFilename || t('dd.imageFallback', { n: idx + 1 })}
                  className="image-upload-thumb"
                />
                <div className="image-upload-info">
                  <span style={{ fontWeight: 600, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }} title={img.originalFilename}>
                    {img.originalFilename || t('dd.imageFallback', { n: idx + 1 })}
                  </span>
                  <span className="badge-tag badge-success" style={{ alignSelf: 'flex-start', fontSize: '0.7rem' }}>
                    ✓ {t('dd.sharp')}
                  </span>
                </div>
              </div>
            ))}
          </div>
        </section>
      )}

      {/* Full-size image modal */}
      {selectedPreviewImage && (
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
          onClick={() => setSelectedPreviewImage(null)}
        >
          <div
            style={{
              background: '#ffffff',
              padding: '1rem',
              borderRadius: '8px',
              maxWidth: '90vw',
              maxHeight: '90vh',
              display: 'flex',
              flexDirection: 'column',
              alignItems: 'center',
            }}
            onClick={e => e.stopPropagation()}
          >
            <DesignImage
              image={selectedPreviewImage}
              alt={selectedPreviewImage.originalFilename || t('dd.imageAlt')}
              style={{ maxWidth: '100%', maxHeight: '75vh', objectFit: 'contain' }}
            />
            <div style={{ marginTop: '0.75rem', display: 'flex', justifyContent: 'space-between', width: '100%', alignItems: 'center' }}>
              <span className="small muted">{selectedPreviewImage.originalFilename}</span>
              <button type="button" className="button outline small" onClick={() => setSelectedPreviewImage(null)}>
                {ts('close')}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Full-size 3D/Technical Specs Modal */}
      {is3dModalOpen && (
        <Design3dDetailModal
          isOpen={is3dModalOpen}
          onClose={() => setIs3dModalOpen(false)}
          designSnapshot={data.designSnapshot}
          designReference={data.designReference || data.requestReference || data.id}
        />
      )}

      <div className="actions" style={{ marginTop: '2rem', display: 'flex', gap: '1rem', flexWrap: 'wrap' }}>
        <Link to="/design-requests/new" className="button primary">
          {t('detail.createNew')}
        </Link>
        {isAuthenticated && (
          <Link to="/my/designs?tab=custom" className="button outline">
            {t('dd.history')}
          </Link>
        )}
      </div>
    </div>
  );
}
