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

  const candidateFeatures = data.candidateFeatures || {};
  const isTerminal = data.reviewStatus === 'ACCEPTED' || data.reviewStatus === 'REJECTED';

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
        <span className={`badge-tag ${data.reviewStatus === 'ACCEPTED' ? 'badge-success' : data.reviewStatus === 'REJECTED' ? 'badge-danger' : 'badge-warning'}`}>
          {t('detail.reviewStatusLabel', { status: data.reviewStatus || 'NEED_REVIEW' })}
        </span>
      </div>

      <SuccessNotice>{successMsg}</SuccessNotice>
      <ErrorNotice error={command.error} messages={designReviewMessages} onRetry={command.retry} onReload={reload} />

      {data.description && (
        <section className="card" style={{ marginBottom: '1.5rem', padding: '1rem', border: '1px solid #e2e8f0', borderRadius: '6px' }}>
          <h4>{t('workspace.descriptionLabel')}</h4>
          <p>{data.description}</p>
          {data.catalogueReference && (
            <p className="muted small">
              {t('workspace.catalogueRefLabel')}: <code>{data.catalogueReference}</code>
            </p>
          )}
        </section>
      )}

      {Object.keys(candidateFeatures).length > 0 && (
        <section style={{ marginBottom: '1.5rem' }}>
          <h3>{t('detail.featuresTitle')}</h3>
          <div className="features-grid">
            {Object.entries(candidateFeatures).map(([k, v]) => (
              <div key={k} className="feature-card">
                <h4>{k}</h4>
                <p>{typeof v === 'object' ? JSON.stringify(v) : String(v)}</p>
              </div>
            ))}
          </div>
        </section>
      )}

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
                <option value="CLARIFICATION_REQUIRED">{t('reviewDetail.decisionClarification')}</option>
              </select>
            </Field>

            <Field
              label={t('reviewDetail.reasonLabel')}
              hint={t('workspace.charCount', { count: [...reason].length })}
              error={formError || command.error?.fieldError?.('reason')}
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
        <p>{t('reviewDetail.confirmMessage', { decision })}</p>
      </ConfirmDialog>
    </div>
  );
}
