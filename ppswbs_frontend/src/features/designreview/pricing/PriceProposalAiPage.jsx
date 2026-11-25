import React, { useState } from 'react';
import { useParams } from 'react-router';
import { useFormat, useT } from '../../../shared/i18n/I18nProvider';
import {
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

export default function PriceProposalAiPage() {
  const { proposalId } = useParams();
  const t = useT(designReviewMessages);
  const fmt = useFormat();
  const formatDateTime = fmt.dateTime;
  const formatVnd = amount => (amount === undefined || amount === null || Number.isNaN(Number(amount)) ? '—' : fmt.money(wholeAmount(amount)));
  const api = useDesignReviewApi();
  const command = useCommand();

  const [decision, setDecision] = useState('ACCEPTED');
  const [finalPrice, setFinalPrice] = useState('');
  const [note, setNote] = useState('');
  const [formError, setFormError] = useState('');
  const [successMsg, setSuccessMsg] = useState('');

  const { loading, error, data, reload, setData } = useResource(
    () => api.getPriceSuggestion(proposalId),
    [proposalId]
  );

  async function handleRequestSuggestion() {
    const res = await command.run(() =>
      api.requestPriceSuggestion(proposalId, { targetType: 'WORKSHOP_PACKAGE' })
    );
    if (res.ok && res.result) {
      setData(res.result);
      setSuccessMsg(t('pp.suggestionReceived'));
    }
  }

  async function handleRecordDecision(e) {
    e.preventDefault();
    if (decision === 'ADJUSTED' && (!finalPrice || isNaN(Number(finalPrice)) || Number(finalPrice) <= 0)) {
      setFormError(t('validation.finalPriceRequired'));
      return;
    }
    setFormError('');

    const res = await command.run(() =>
      api.recordPriceDecision(proposalId, {
        decision,
        finalPrice: finalPrice ? Number(finalPrice) : null,
        note: note.trim(),
      })
    );

    if (res.ok && res.result) {
      setData(res.result);
      setSuccessMsg(t('pp.decisionRecorded'));
    }
  }

  if (loading) return <div className="design-container"><Loading /></div>;

  const isAdminAudit = data?.technicalAuditEvidenceOnly === true;

  return (
    <div className="design-container">
      <header className="page-header">
        <h1>{t('priceProposal.title')}</h1>
        <p className="muted">{t('priceProposal.subtitle')}</p>
        <p className="small">
          {t('priceProposal.proposalIdLabel', { id: proposalId })}
          {data?.targetType && ` | ${t('priceProposal.targetTypeLabel', { type: data.targetType })}`}
        </p>
      </header>

      {isAdminAudit && (
        <div className="audit-banner" role="status">
          <strong>{t('priceProposal.auditModeNotice')}</strong>
        </div>
      )}

      <SuccessNotice>{successMsg}</SuccessNotice>
      <ErrorNotice error={error || command.error} messages={designReviewMessages} onReload={reload} onRetry={command.retry} />

      {/* Admin technical audit view */}
      {isAdminAudit && data && (
        <section className="card" style={{ padding: '1.25rem', border: '1px solid #cbd5e1', borderRadius: '6px' }}>
          <h3>{t('pp.auditTitle')}</h3>
          <ul>
            <li><strong>{t('pp.auditId')}:</strong> {data.id || '—'}</li>
            <li><strong>{t('pp.auditStatus')}:</strong> {data.suggestionStatus || '—'}</li>
            <li><strong>{t('pp.auditCorrelation')}:</strong> <code>{data.providerCorrelationId || '—'}</code></li>
            <li><strong>{t('pp.createdAt')}:</strong> {formatDateTime(data.createdAt)}</li>
            <li><strong>{t('pp.decidedAt')}:</strong> {formatDateTime(data.decidedAt)}</li>
          </ul>
        </section>
      )}

      {/* Manager commercial advisory view */}
      {!isAdminAudit && (
        <div className="stack" style={{ gap: '1.5rem' }}>
          {!data && (
            <div className="empty-state">
              <p>{t('pp.noSuggestion')}</p>
              <button
                type="button"
                className="button primary"
                onClick={handleRequestSuggestion}
                disabled={command.pending}
              >
                {t('priceProposal.requestButton')}
              </button>
            </div>
          )}

          {data && (
            <>
              <section className="card" style={{ padding: '1.25rem', border: '1px solid #e2e8f0', borderRadius: '6px' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.5rem' }}>
                  <h3 style={{ margin: 0 }}>{t('pp.cardTitle')}</h3>
                  <span className="badge-tag badge-warning">{t('pp.advisoryBadge')}</span>
                </div>

                <p style={{ fontSize: '1.35rem', fontWeight: 'bold', color: '#0f766e', margin: '0.75rem 0' }}>
                  {t('priceProposal.suggestedPrice', { price: formatVnd(data.suggestedPrice) })}
                </p>

                {data.explanation && (
                  <p className="muted" style={{ whiteSpace: 'pre-wrap' }}>
                    {t('priceProposal.explanation', { text: data.explanation })}
                  </p>
                )}

                <div style={{ marginTop: '1rem' }}>
                  <button
                    type="button"
                    className="button outline small"
                    onClick={handleRequestSuggestion}
                    disabled={command.pending}
                  >
                    {t('priceProposal.requestButton')} ({t('pp.refresh')})
                  </button>
                </div>
              </section>

              {/* Manager Decision Form */}
              <section className="card" style={{ padding: '1.5rem', border: '1px solid #cbd5e1', borderRadius: '8px', background: '#f8fafc' }}>
                <h3>{t('priceProposal.decisionTitle')}</h3>

                {data.managerDecision ? (
                  <div>
                    <p>
                      <strong>{t('pp.recorded')}:</strong>{' '}
                      <span className="badge-tag badge-status">{data.managerDecision}</span>
                    </p>
                    {data.finalPrice && (
                      <p><strong>{t('pp.finalPrice')}:</strong> {formatVnd(data.finalPrice)}</p>
                    )}
                    {data.decidedAt && (
                      <p className="muted small">{t('pp.recordedAt')}: {formatDateTime(data.decidedAt)}</p>
                    )}
                  </div>
                ) : (
                  <form onSubmit={handleRecordDecision} className="stack">
                    <Field label={t('priceProposal.decisionLabel')}>
                      <select
                        value={decision}
                        onChange={e => setDecision(e.target.value)}
                        disabled={command.pending}
                      >
                        <option value="ACCEPTED">{t('priceProposal.decisionAccepted')}</option>
                        <option value="ADJUSTED">{t('priceProposal.decisionAdjusted')}</option>
                        <option value="REJECTED">{t('priceProposal.decisionRejected')}</option>
                        <option value="MANUAL_FALLBACK">{t('priceProposal.decisionManual')}</option>
                      </select>
                    </Field>

                    {(decision === 'ADJUSTED' || decision === 'ACCEPTED') && (
                      <Field
                        label={t('priceProposal.finalPriceLabel')}
                        error={formError}
                        hint={decision === 'ACCEPTED' ? t('pp.hintAccepted') : t('pp.hintAdjusted')}
                      >
                        <input
                          type="number"
                          value={finalPrice}
                          onChange={e => {
                            setFinalPrice(e.target.value);
                            if (formError) setFormError('');
                          }}
                          placeholder={t('priceProposal.finalPricePlaceholder')}
                          disabled={command.pending}
                        />
                      </Field>
                    )}

                    <Field label={t('priceProposal.noteLabel')}>
                      <textarea
                        value={note}
                        onChange={e => setNote(e.target.value)}
                        placeholder={t('priceProposal.notePlaceholder')}
                        rows={3}
                        disabled={command.pending}
                      />
                    </Field>

                    <div className="actions" style={{ marginTop: '1rem' }}>
                      <button
                        type="submit"
                        className="button primary"
                        disabled={command.pending}
                      >
                        {t('priceProposal.submitDecision')}
                      </button>
                    </div>
                  </form>
                )}
              </section>

              <div className="disclaimer-banner">
                {t('priceProposal.catalogueNote')}
              </div>
            </>
          )}
        </div>
      )}
    </div>
  );
}

