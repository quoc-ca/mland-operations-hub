import React, { useEffect, useState } from 'react';
import { useParams } from 'react-router';
import { useRole } from '../../../shared/auth/useRole';
import { useEnumLabel, useFormat, useT } from '../../../shared/i18n/I18nProvider';
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
import { wholeAmount } from '../format';
import { sharedMessages } from '../../../shared/ui/messages';
import { LIMITS } from '../rules';

export default function ReviewDetailPage() {
  const { reviewRequestId } = useParams();
  const t = useT(designReviewMessages);
  const ts = useT(sharedMessages);
  const fmt = useFormat();
  const statusLabel = useEnumLabel(designReviewMessages, 'status');
  const api = useDesignReviewApi();
  const command = useCommand();
  const role = useRole();
  const isManager = role === 'MANAGER';

  const [decision, setDecision] = useState('ACCEPTED');
  const [reason, setReason] = useState('');
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [successMsg, setSuccessMsg] = useState('');
  const [formError, setFormError] = useState('');
  const [selectedPreviewImage, setSelectedPreviewImage] = useState(null);

  // Quote preparation state
  const [quoteAmount, setQuoteAmount] = useState('');
  const [quoteHours, setQuoteHours] = useState('72');
  const [quoteReason, setQuoteReason] = useState('');
  const [quoteSuccess, setQuoteSuccess] = useState('');
  const [quoteError, setQuoteError] = useState('');
  const [quoteConfirmOpen, setQuoteConfirmOpen] = useState(false);
  const [quoteSubmitted, setQuoteSubmitted] = useState(false);
  const [quoteData, setQuoteData] = useState(null);
  const [managerApproveConfirmOpen, setManagerApproveConfirmOpen] = useState(false);

  const { loading, error, data, reload, setData } = useResource(
    () => api.getDesignRequest(reviewRequestId),
    [reviewRequestId]
  );

  useEffect(() => {
    if (data?.quoteReference) {
      api.getCustomDesignQuote?.(reviewRequestId)
        .then(res => {
          if (res) setQuoteData(res);
        })
        .catch(() => {});
    }
  }, [data?.quoteReference, reviewRequestId, api]);

  function handleOpenQuoteConfirm(e) {
    e.preventDefault();
    if (!quoteAmount || Number(quoteAmount) <= 0) {
      setFormError(t('rv.zeroQuote'));
      return;
    }
    setFormError('');
    setQuoteError('');
    setQuoteConfirmOpen(true);
  }

  async function handleConfirmPrepareQuote() {
    if (command.pending) return;
    await command.run(async () => {
      try {
        const expiresAt = new Date(Date.now() + Number(quoteHours || 72) * 3600 * 1000).toISOString();
        const res = await api.prepareCustomDesignQuote(reviewRequestId, {
          totalAmount: quoteAmount,
          currency: 'VND',
          quoteExpiresAt: expiresAt,
          managerReason: quoteReason,
        });
        setQuoteConfirmOpen(false);
        setQuoteSubmitted(true);
        setQuoteData(res);
        setQuoteSuccess(t('rv.quoteSentOk', { ref: res.quoteReference, amount: fmt.money(wholeAmount(res.totalAmount)) }));
        setQuoteError('');
        reload();
        return res;
      } catch (err) {
        setQuoteConfirmOpen(false);
        setQuoteError(err?.message || t('rv.quoteSendFailed'));
        throw err;
      }
    });
  }

  async function handleConfirmManagerApprove() {
    if (command.pending) return;
    await command.run(async () => {
      try {
        const res = await api.approveCustomDesignQuoteByRequestId(reviewRequestId, {
          managerReason: 'Quản lý phê duyệt báo giá',
        });
        setManagerApproveConfirmOpen(false);
        setQuoteData(res);
        setQuoteSuccess(t('rv.approveOk', { ref: res.quoteReference }));
        setQuoteError('');
        reload();
        return res;
      } catch (err) {
        setManagerApproveConfirmOpen(false);
        setQuoteError(err?.message || t('rv.approveFailed'));
        throw err;
      }
    });
  }

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
          {t('detail.statusLabel', { status: statusLabel(data.requestStatus || 'UNKNOWN') })}
        </span>
        <span className={`badge-tag ${isAccepted ? 'badge-success' : isRejected ? 'badge-danger' : 'badge-warning'}`}>
          {t('detail.reviewStatusLabel', { status: statusLabel(data.reviewStatus || 'NEED_REVIEW') })}
        </span>
        {data.ownershipType && (
          <span className="badge-tag badge-status">
            {data.ownershipType === 'MEMBER' ? t('rv.member') : t('rv.guest')}
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
              {t('rv.decisionPrefix', { decision: isAccepted ? t('reviewDetail.decisionAccepted') : t('reviewDetail.decisionRejected') })}
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

      {/* Quote Preparation section for Accepted Member Requests */}
      {isAccepted && (() => {
        const activeQuoteRef = quoteData?.quoteReference || data.quoteReference;
        const hasQuote = Boolean(quoteSubmitted || activeQuoteRef);
        const isQuoteApproved = Boolean(quoteData?.approvedByManager);
        const quoteGen = quoteData?.quoteGeneration;
        const displayAmount = quoteData?.totalAmount || quoteAmount;
        const displayReason = quoteData?.managerReason || quoteReason;
        const displayExpiry = quoteData?.expiresAt;

        return (
          <section className="card" style={{ marginBottom: '1.5rem', padding: '1.5rem', border: '1px solid #bfdbfe', borderRadius: '8px', background: '#eff6ff' }}>
            <h3 style={{ marginTop: 0, color: '#1e40af', fontSize: '1.2rem', marginBottom: '0.5rem' }}>
              {t('rv.quoteTitle')}
            </h3>
            <p style={{ fontSize: '0.9rem', color: '#3b82f6', marginBottom: '1rem' }}>
              {t('rv.quoteIntro')}
            </p>

            {quoteError && (
              <div className="alert-banner alert-banner-danger" style={{ marginBottom: '1rem', backgroundColor: '#fee2e2', color: '#991b1b', padding: '0.75rem 1rem', borderRadius: '6px', border: '1px solid #fca5a5' }}>
                <p style={{ margin: 0 }}><strong>{`✕ ${t('rv.failed')}: `}</strong>{quoteError}</p>
              </div>
            )}

            {quoteSuccess && (
              <div className="alert-banner alert-banner-success" style={{ marginBottom: '1rem', backgroundColor: '#dcfce7', color: '#166534', padding: '0.75rem 1rem', borderRadius: '6px', border: '1px solid #86efac' }}>
                <p style={{ margin: 0 }}><strong>{`✓ ${t('rv.success')}: `}</strong>{quoteSuccess}</p>
              </div>
            )}

            {hasQuote ? (
              <div style={{ background: '#ffffff', borderRadius: 6, border: '1px solid #dbeafe', padding: '1rem' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.75rem', flexWrap: 'wrap', gap: '0.5rem' }}>
                  <div>
                    <span style={{ fontSize: '0.85rem', color: '#64748b' }}>{t('rv.quoteCode')}: </span>
                    <strong style={{ fontSize: '1.05rem', color: '#1e40af' }}>{activeQuoteRef}</strong>
                    {quoteGen && <span className="muted small" style={{ marginLeft: '0.5rem' }}>{t('rv.generation', { n: quoteGen })}</span>}
                  </div>
                  <div>
                    <span className={`badge-tag ${isQuoteApproved ? 'badge-success' : 'badge-warning'}`}>
                      {isQuoteApproved ? `✓ ${t('rv.approvedByManager')}` : `⏳ ${t('rv.pendingManager')}`}
                    </span>
                  </div>
                </div>

                {displayAmount && (
                  <div style={{ margin: '0.5rem 0' }}>
                    <span style={{ fontSize: '0.85rem', color: '#64748b', display: 'block' }}>{t('rv.quoteTotal')}:</span>
                    <span style={{ fontSize: '1.25rem', fontWeight: 700, color: '#0f172a' }}>
                      {fmt.money(wholeAmount(displayAmount))}
                    </span>
                  </div>
                )}

                {displayExpiry && (
                  <div style={{ fontSize: '0.85rem', color: '#64748b', marginBottom: '0.5rem' }}>
                    {t('rv.payDeadline')}: <strong>{fmt.dateTime(displayExpiry)}</strong>
                  </div>
                )}

                {displayReason && (
                  <div style={{ fontSize: '0.875rem', color: '#334155', background: '#f8fafc', padding: '0.5rem 0.75rem', borderRadius: 4, margin: '0.5rem 0' }}>
                    <strong>{t('rv.reasonBasis')}:</strong> {displayReason}
                  </div>
                )}

                <p className="muted small" style={{ marginTop: '0.75rem', marginBottom: 0 }}>
                  {isQuoteApproved
                    ? t('rv.noteApproved')
                    : t('rv.notePending')}
                </p>

                {isManager && !isQuoteApproved && (
                  <div style={{ marginTop: '1rem', paddingTop: '1rem', borderTop: '1px dashed #cbd5e1' }}>
                    <button
                      type="button"
                      className="button button-primary"
                      onClick={() => setManagerApproveConfirmOpen(true)}
                      disabled={command.pending}
                      style={{ backgroundColor: '#16a34a', color: '#ffffff', padding: '0.5rem 1.25rem' }}
                    >
                      {command.pending ? t('rv.approving') : t('rv.approveButton')}
                    </button>
                  </div>
                )}
              </div>
            ) : (
              <form onSubmit={handleOpenQuoteConfirm} className="stack">
                <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: '1rem' }}>
                  <div>
                    <label style={{ display: 'block', fontWeight: 600, fontSize: '0.875rem', marginBottom: '0.25rem' }}>
                      {t('rv.amountLabel')}
                    </label>
                    <input
                      type="number"
                      min="1000"
                      step="1000"
                      value={quoteAmount}
                      onChange={e => {
                        setQuoteAmount(e.target.value);
                        if (formError) setFormError('');
                      }}
                      placeholder={t('rv.amountPlaceholder')}
                      required
                      style={{ width: '100%', padding: '0.5rem', borderRadius: 6, border: '1px solid #cbd5e1' }}
                    />
                  </div>
                  <div>
                    <label style={{ display: 'block', fontWeight: 600, fontSize: '0.875rem', marginBottom: '0.25rem' }}>
                      {t('rv.hoursLabel')}
                    </label>
                    <input
                      type="number"
                      min="1"
                      max="720"
                      value={quoteHours}
                      onChange={e => setQuoteHours(e.target.value)}
                      placeholder="72"
                      style={{ width: '100%', padding: '0.5rem', borderRadius: 6, border: '1px solid #cbd5e1' }}
                    />
                  </div>
                </div>

                <div style={{ marginTop: '0.75rem' }}>
                  <label style={{ display: 'block', fontWeight: 600, fontSize: '0.875rem', marginBottom: '0.25rem' }}>
                    {t('rv.quoteReasonLabel')}
                  </label>
                  <textarea
                    rows={2}
                    value={quoteReason}
                    onChange={e => setQuoteReason(e.target.value)}
                    placeholder={t('rv.quoteReasonPlaceholder')}
                    style={{ width: '100%', padding: '0.5rem', borderRadius: 6, border: '1px solid #cbd5e1' }}
                  />
                </div>

                {formError && (
                  <div style={{ color: '#dc2626', fontSize: '0.875rem', marginTop: '0.5rem' }}>
                    {formError}
                  </div>
                )}

                <div style={{ display: 'flex', justifyContent: 'flex-end', marginTop: '1rem' }}>
                  <button
                    type="submit"
                    className="button button-primary"
                    disabled={command.pending || !quoteAmount}
                    style={{ backgroundColor: '#1d4ed8', color: '#ffffff', padding: '0.5rem 1.25rem' }}
                  >
                    {command.pending ? t('rv.sending') : t('rv.sendQuote')}
                  </button>
                </div>
              </form>
            )}
          </section>
        );
      })()}

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
                  alt={img.originalFilename || t('dd.imageFallback', { n: idx + 1 })}
                  className="image-upload-thumb"
                  onError={e => {
                    e.target.style.opacity = '0.5';
                  }}
                />
                <div className="image-upload-info">
                  <span style={{ fontWeight: 600, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }} title={img.originalFilename}>
                    {img.originalFilename || t('dd.imageFallback', { n: idx + 1 })}
                  </span>
                  <span className="badge-tag badge-success" style={{ alignSelf: 'flex-start', fontSize: '0.7rem' }}>
                    ✓ {t('rv.qualityOk')}
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
                <option value="CLARIFICATION_REQUIRED">{t('rv.optClarify')}</option>
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
        <p>{t('reviewDetail.confirmMessage', { decision: decision === 'ACCEPTED' ? t('rv.acceptedShort') : t('rv.rejectedShort') })}</p>
        <p><strong>{t('rv.reasonExplained')}:</strong> <em>{reason}</em></p>
      </ConfirmDialog>

      {/* Quote Preparation Confirmation Dialog */}
      <ConfirmDialog
        open={quoteConfirmOpen}
        title={t('rv.confirmQuoteTitle')}
        confirmLabel={command.pending ? t('rv.sending') : t('rv.confirmSend')}
        onConfirm={handleConfirmPrepareQuote}
        onCancel={() => { if (!command.pending) setQuoteConfirmOpen(false); }}
        pending={command.pending}
      >
        <p>{t('rv.confirmQuoteBody')}</p>
        <div style={{ background: '#f8fafc', padding: '0.75rem', borderRadius: 6, margin: '0.75rem 0', border: '1px solid #e2e8f0', fontSize: '0.9rem' }}>
          <div><strong>{t('rv.quoteTotal')}:</strong> {fmt.money(wholeAmount(quoteAmount || 0))}</div>
          <div><strong>{t('rv.payDeadline')}:</strong> {t('rv.hoursValue', { hours: quoteHours || 72 })}</div>
          {quoteReason && <div><strong>{t('rv.basisNote')}:</strong> <em>{quoteReason}</em></div>}
        </div>
        <p className="muted small" style={{ margin: 0 }}>
          {t('rv.confirmQuoteNote')}
        </p>
      </ConfirmDialog>

      {/* Manager Approve Quote Dialog */}
      <ConfirmDialog
        open={managerApproveConfirmOpen}
        title={t('rv.confirmApproveTitle')}
        confirmLabel={command.pending ? t('rv.approvingPending') : t('rv.approveConfirm')}
        onConfirm={handleConfirmManagerApprove}
        onCancel={() => { if (!command.pending) setManagerApproveConfirmOpen(false); }}
        pending={command.pending}
      >
        <p>{t('rv.confirmApproveBody')}</p>
        <p className="muted small" style={{ margin: 0 }}>
          {t('rv.confirmApproveNote')}
        </p>
      </ConfirmDialog>
    </div>
  );
}
