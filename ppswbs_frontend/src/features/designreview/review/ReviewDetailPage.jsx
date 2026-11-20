import React, { useState } from 'react';
import { useParams } from 'react-router';
import { useT } from '../../../shared/i18n/I18nProvider';
import {
  ConfirmDialog,
  ErrorNotice,
  Field,
  Loading,
  SuccessNotice,
  useCommand,
  useResource,
} from '../../../shared/ui/components';
import { useDesignReviewApi } from '../api/useDesignReviewApi';
import { designReviewMessages } from '../messages';
import { LIMITS } from '../rules';

export default function ReviewDetailPage() {
  const { reviewRequestId } = useParams();
  const t = useT(designReviewMessages);
  const api = useDesignReviewApi();
  const command = useCommand();

  const [decision, setDecision] = useState('ACCEPTED');
  const [reason, setReason] = useState('');
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [successMsg, setSuccessMsg] = useState('');
  const [formError, setFormError] = useState('');
  const [selectedPreviewImage, setSelectedPreviewImage] = useState(null);

  const { loading, error, data, reload, setData } = useResource(
    () => api.getDesignRequest(reviewRequestId),
    [reviewRequestId]
  );

  async function handleConfirmDecision() {
    setConfirmOpen(false);
    await command.run(async () => {
      const result = await api.recordReviewDecision(reviewRequestId, {
        decision,
        designVersion: data?.requestVersion || 1,
        explanation: reason.trim(),
        reason: reason.trim(),
      });
      setData(result);
      setSuccessMsg(t('reviewDetail.success'));
      setReason('');
      return result;
    });
  }

  function handleOpenConfirm(e) {
    e.preventDefault();
    if (!reason.trim()) {
      setFormError(t('validation.required'));
      return;
    }
    if ([...reason.trim()].length > LIMITS.reasonMax) {
      setFormError(t('validation.reasonTooLong'));
      return;
    }
    setFormError('');
    setConfirmOpen(true);
  }

  if (loading) return <div className="design-container"><Loading /></div>;
  if (error) {
    return (
      <div className="design-container">
        <ErrorNotice error={error} messages={designReviewMessages} onReload={reload} />
      </div>
    );
  }
  if (!data) return null;

  const isTerminal = data.reviewStatus === 'ACCEPTED' || data.reviewStatus === 'REJECTED';
  const isAccepted = data.reviewStatus === 'ACCEPTED';
  const isRejected = data.reviewStatus === 'REJECTED';
  const explanation = data.decisionExplanation || data.safeExplanation || '';
  const images = Array.isArray(data.images) ? data.images : [];

  return (
    <div className="design-container">
      <header className="page-header">
        <h1>{t('reviewDetail.title', { id: data.id })}</h1>
        <p className="muted">
          {t('detail.reference', { ref: data.requestReference || data.id })} | {t('detail.version', { version: data.requestVersion || 1 })}
        </p>
      </header>

      <div style={{ display: 'flex', gap: '0.75rem', marginBottom: '1.5rem', flexWrap: 'wrap' }}>
        <span className="badge-tag badge-status">
          {t('detail.statusLabel', { status: data.requestStatus || 'UNKNOWN' })}
        </span>
        <span className={`badge-tag ${isAccepted ? 'badge-success' : isRejected ? 'badge-danger' : 'badge-warning'}`}>
          {t('detail.reviewStatusLabel', { status: data.reviewStatus || 'NEED_REVIEW' })}
        </span>
        {data.ownershipType && (
          <span className="badge-tag badge-status">
            {data.ownershipType === 'MEMBER' ? 'Hội viên' : 'Khách vãng lai'}
          </span>
        )}
      </div>

      <SuccessNotice>{successMsg}</SuccessNotice>
      <ErrorNotice error={command.error} messages={designReviewMessages} onRetry={command.retry} onReload={reload} />

      {/* Already decided callout */}
      {isTerminal && (
        <div className="reviewer-feedback-box">
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginBottom: '0.5rem' }}>
            <span style={{ fontSize: '1.25rem' }}>{isAccepted ? '✅' : '❌'}</span>
            <h3 style={{ margin: 0, color: isAccepted ? '#166534' : '#991b1b' }}>
              Quyết định: {isAccepted ? 'Chấp thuận (ACCEPTED)' : 'Từ chối (REJECTED)'}
            </h3>
          </div>
          {explanation && (
            <div>
              <h4 style={{ margin: '0.75rem 0 0.25rem', fontSize: '0.9rem', color: '#475569' }}>
                {t('detail.decisionExplanation')}:
              </h4>
              <p style={{ margin: 0, whiteSpace: 'pre-wrap', lineHeight: 1.6 }}>{explanation}</p>
            </div>
          )}
        </div>
      )}

      {/* Description */}
      {data.description && (
        <section className="card" style={{ marginBottom: '1.5rem', padding: '1.25rem', border: '1px solid #e2e8f0', borderRadius: '8px', background: '#ffffff' }}>
          <h4 style={{ marginTop: 0 }}>{t('workspace.descriptionLabel')}</h4>
          <p style={{ whiteSpace: 'pre-wrap', margin: 0 }}>{data.description}</p>
          {data.catalogueReference && (
            <p className="muted small" style={{ marginTop: '0.5rem' }}>
              {t('workspace.catalogueRefLabel')}: <code>{data.catalogueReference}</code>
            </p>
          )}
        </section>
      )}

      {/* Uploaded Images for Review */}
      {images.length > 0 && (
        <section style={{ marginBottom: '1.5rem' }}>
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
                  alt={img.originalFilename || `Ảnh ${idx + 1}`}
                  className="image-upload-thumb"
                  onError={e => {
                    e.target.style.opacity = '0.5';
                  }}
                />
                <div className="image-upload-info">
                  <span style={{ fontWeight: 600, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }} title={img.originalFilename}>
                    {img.originalFilename || `Ảnh ${idx + 1}`}
                  </span>
                  <span className="badge-tag badge-success" style={{ alignSelf: 'flex-start', fontSize: '0.7rem' }}>
                    ✓ Đạt chất lượng
                  </span>
                </div>
              </div>
            ))}
          </div>
        </section>
      )}

      {/* Full-size image modal for reviewer */}
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
              alt={selectedPreviewImage.originalFilename || 'Ảnh mẫu thiết kế'}
              style={{ maxWidth: '100%', maxHeight: '75vh', objectFit: 'contain' }}
            />
            <div style={{ marginTop: '0.75rem', display: 'flex', justifyContent: 'space-between', width: '100%', alignItems: 'center' }}>
              <span className="small muted">{selectedPreviewImage.originalFilename}</span>
              <button type="button" className="button outline small" onClick={() => setSelectedPreviewImage(null)}>
                Đóng
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Review Decision Form (Only if not decided yet) */}
      {!isTerminal && (
        <section className="card" style={{ padding: '1.5rem', border: '1px solid #cbd5e1', borderRadius: '8px', background: '#f8fafc' }}>
          <h3>{t('reviewDetail.decisionTitle')}</h3>

          <form onSubmit={handleOpenConfirm} className="stack">
            <Field label={t('reviewDetail.decisionLabel')}>
              <select
                value={decision}
                onChange={e => setDecision(e.target.value)}
                disabled={command.pending}
              >
                <option value="ACCEPTED">{t('reviewDetail.decisionAccepted')}</option>
                <option value="REJECTED">{t('reviewDetail.decisionRejected')}</option>
              </select>
            </Field>

            <Field
              label={t('reviewDetail.reasonLabel')}
              hint={t('workspace.charCount', { count: [...reason].length })}
              error={formError || command.error?.fieldError?.('reason') || command.error?.fieldError?.('explanation')}
            >
              <textarea
                value={reason}
                onChange={e => {
                  setReason(e.target.value);
                  if (formError) setFormError('');
                }}
                placeholder={t('reviewDetail.reasonPlaceholder')}
                rows={4}
                disabled={command.pending}
                required
              />
            </Field>

            <div className="actions" style={{ marginTop: '1rem' }}>
              <button
                type="submit"
                className={`button primary${decision === 'REJECTED' ? ' danger' : ''}`}
                disabled={command.pending || !reason.trim()}
              >
                {t('reviewDetail.submit')}
              </button>
            </div>
          </form>
        </section>
      )}

      <ConfirmDialog
        open={confirmOpen}
        title={t('reviewDetail.confirmTitle')}
        confirmLabel={t('reviewDetail.submit')}
        onConfirm={handleConfirmDecision}
        onCancel={() => setConfirmOpen(false)}
        pending={command.pending}
        danger={decision === 'REJECTED'}
      >
        <p>{t('reviewDetail.confirmMessage', { decision: decision === 'ACCEPTED' ? 'Chấp thuận' : 'Từ chối' })}</p>
        <p><strong>Lý do giải thích:</strong> <em>{reason}</em></p>
      </ConfirmDialog>
    </div>
  );
}
