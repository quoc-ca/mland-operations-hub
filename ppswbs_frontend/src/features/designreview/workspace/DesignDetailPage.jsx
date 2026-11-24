import React, { useState } from 'react';
import { Link, useLocation, useParams, useSearchParams } from 'react-router';
import { useAuth } from '../../../shared/auth/AuthProvider';
import { RequireSession } from '../../../shared/auth/RequireSession';
import { useT } from '../../../shared/i18n/I18nProvider';
import { ErrorNotice, Loading, SuccessNotice, useResource } from '../../../shared/ui/components';
import { useDesignReviewApi } from '../api/useDesignReviewApi';
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

  const successMessage = location.state?.successMessage;

  const { loading, error, data, reload } = useResource(
    () => api.getDesignRequest(designRequestId, { auth: isAuthenticated, token: tokenFromUrl }),
    [designRequestId, isAuthenticated, tokenFromUrl]
  );

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
                <img
                  src={img.imageUrl || `/api/v1/design-requests/images/${encodeURIComponent(img.imageReference)}`}
                  alt={img.originalFilename || t('dd.imageFallback', { n: idx + 1 })}
                  className="image-upload-thumb"
                  onError={e => {
                    // Fallback to placeholder if byte stream unavailable
                    e.target.style.opacity = '0.5';
                  }}
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
            <img
              src={selectedPreviewImage.imageUrl || `/api/v1/design-requests/images/${encodeURIComponent(selectedPreviewImage.imageReference)}`}
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
