import React, { useState } from 'react';
import { useFormat, useT } from '../../../../shared/i18n/I18nProvider';
import { ConfirmDialog, ErrorNotice, Field } from '../../../../shared/ui/components';
import { messages } from '../../messages';
import { checkReason, currentReview, effectiveProposal, isAmount, LIMITS, serverFieldErrors } from '../../rules';
import { useProductCommand } from '../ProductDetailPage';

/**
 * T034 initial review, before the first publication: submit freezes the content and every size's proposal;
 * a Manager approves the whole file (an amount for every size, adjustable) or rejects it with a reason — never a
 * part of it. Approval applies and publishes nothing. Cancel never calls the API.
 */
export function ReviewSection({ product, api, reload, setNotice, deleted }) {
  const t = useT(messages);
  const review = currentReview(product);
  const submitCommand = useProductCommand({ reload, setNotice });
  const [confirmSubmit, setConfirmSubmit] = useState(false);
  const history = [...(product.reviews || [])].sort((a, b) => b.revision - a.revision);

  const submit = async () => {
    const outcome = await submitCommand.run(key => api.submitReview(key, product.id, product.version), 'review.submitted');
    if (outcome.ok || !outcome.error?.retryable) setConfirmSubmit(false);
  };

  return (
    <section className="workspace-card" aria-labelledby="review-title">
      <h2 id="review-title">{t('section.review')}</h2>
      <p>{t('review.current')}: <strong>{review ? t(`state.${review.state}`) : t('review.none')}</strong></p>
      {review?.state === 'REJECTED' && review.reason && <p className="notice error pre-line">{t('review.rejectedBecause', { reason: review.reason })}</p>}
      {review?.state === 'APPROVED' && <p className="notice success">{t('review.approvedNext')}</p>}

      {!deleted && review?.state !== 'PENDING' && review?.state !== 'APPROVED' && (
        <>
          <p className="muted small">{t('review.prerequisites')}</p>
          <button type="button" className="button primary" onClick={() => setConfirmSubmit(true)}>{t('review.submit')}</button>
          {!confirmSubmit && <ErrorNotice error={submitCommand.error} messages={messages} onReload={reload} />}
        </>
      )}
      <ConfirmDialog open={confirmSubmit} title={t('review.submit')} confirmLabel={t('review.submit')} pending={submitCommand.pending}
        onConfirm={submit} onCancel={() => { setConfirmSubmit(false); submitCommand.reset(); }}>
        <p>{t('review.submitConfirm')}</p>
        <ErrorNotice error={submitCommand.error} messages={messages} onRetry={submitCommand.retry} />
      </ConfirmDialog>

      {!deleted && review?.state === 'PENDING' && <ReviewDecision product={product} review={review} api={api} reload={reload} setNotice={setNotice} />}

      {history.length > 0 && (
        <details className="history">
          <summary>{t('review.history')}</summary>
          <ul>
            {history.map(item => (
              <li key={item.id}>
                {t('review.historyItem', { revision: item.revision, state: t(`state.${item.state}`), contentVersion: item.contentVersion })}
                {item.reason && <span className="pre-line"> — {item.reason}</span>}
              </li>
            ))}
          </ul>
        </details>
      )}
    </section>
  );
}

function ReviewDecision({ product, review, api, reload, setNotice }) {
  const t = useT(messages);
  const format = useFormat();
  const rows = (product.variants || []).map(variant => ({ variant, proposal: effectiveProposal(variant) }));
  const [mode, setMode] = useState(null);
  const [prices, setPrices] = useState(() => Object.fromEntries(rows.map(({ variant, proposal }) => [variant.id, proposal?.proposedPrice || ''])));
  const [reason, setReason] = useState('');
  const [errors, setErrors] = useState({});
  const command = useProductCommand({ reload, setNotice });
  const changedSinceSubmit = review.contentVersion !== product.contentVersion;

  const decide = async event => {
    event.preventDefault();
    let body;
    const found = {};
    if (mode === 'APPROVE') {
      rows.forEach(({ variant, proposal }) => {
        if (!proposal || proposal.state !== 'PENDING') found[variant.id] = t('review.noPendingProposal');
        else if (!isAmount(prices[variant.id])) found[variant.id] = t('validation.amount');
      });
      body = {
        reviewId: review.id, reviewRevision: review.revision, contentVersion: review.contentVersion, decision: 'APPROVE',
        prices: rows.map(({ variant, proposal }) => ({ variantId: variant.id, proposalId: proposal?.id, approvedPrice: prices[variant.id] })),
      };
    } else {
      const problem = checkReason(reason);
      if (problem) found.reason = t(problem);
      body = { reviewId: review.id, reviewRevision: review.revision, contentVersion: review.contentVersion, decision: 'REJECT', reason: reason.trim() };
    }
    setErrors(found);
    if (Object.keys(found).length) return;
    const outcome = await command.run(key => api.decideReview(key, product.id, body), mode === 'APPROVE' ? 'review.approved' : 'review.rejected');
    if (outcome.ok) setMode(null);
  };

  const serverErrors = serverFieldErrors(command.error);
  return (
    <div className="review-decision">
      <h3>{t('review.decisionTitle')}</h3>
      {changedSinceSubmit && <p className="notice error">{t('review.changedSinceSubmit')}</p>}
      <p className="muted small">{t('review.managerOnly')}</p>
      {!mode && (
        <div className="actions">
          <button type="button" className="button primary" onClick={() => setMode('APPROVE')}>{t('decision.approve')}</button>
          <button type="button" className="button outline" onClick={() => setMode('REJECT')}>{t('decision.reject')}</button>
        </div>
      )}
      {mode && (
        <form onSubmit={decide} noValidate>
          {mode === 'APPROVE' ? (
            <div className="data-table" role="table" aria-label={t('review.decisionTitle')}>
              <div className="data-row head decision" role="row">
                <span role="columnheader">{t('field.size')}</span><span role="columnheader">{t('price.proposed')}</span>
                <span role="columnheader">{t('price.approvedAmount')}</span>
              </div>
              {rows.map(({ variant, proposal }, index) => (
                <div className="data-row decision" role="row" key={variant.id}>
                  <span role="cell">{variant.size}</span>
                  <span role="cell">{format.money(proposal?.proposedPrice)}</span>
                  <span role="cell">
                    <input aria-label={t('review.approvedFor', { size: variant.size })} inputMode="numeric" value={prices[variant.id]}
                      aria-invalid={errors[variant.id] ? true : undefined}
                      onChange={event => setPrices(current => ({ ...current, [variant.id]: event.target.value }))} />
                    {(errors[variant.id] || serverErrors[`prices[${index}].approvedPrice`]) && <span className="field-error">{errors[variant.id] || serverErrors[`prices[${index}].approvedPrice`]}</span>}
                  </span>
                </div>
              ))}
            </div>
          ) : (
            <Field label={t('decision.reason')} error={errors.reason || serverErrors.reason}>
              <textarea value={reason} maxLength={LIMITS.reasonMax} onChange={event => setReason(event.target.value)} />
            </Field>
          )}
          {serverErrors.prices && <p className="field-error">{serverErrors.prices}</p>}
          <ErrorNotice error={command.error} messages={messages} onRetry={command.retry} onReload={reload} />
          <div className="actions form-actions">
            <button type="submit" className={`button primary${mode === 'REJECT' ? ' danger' : ''}`} disabled={command.pending}>{mode === 'APPROVE' ? t('review.approveAll') : t('decision.reject')}</button>
            <button type="button" className="button outline" onClick={() => { setMode(null); setErrors({}); command.reset(); }}>{t('cancel')}</button>
          </div>
        </form>
      )}
    </div>
  );
}
