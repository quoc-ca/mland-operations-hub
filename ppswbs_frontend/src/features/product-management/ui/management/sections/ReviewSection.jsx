import React, { useState } from 'react';
import { useFormat, useT } from '../../../../../shared/i18n/I18nProvider';
import { ConfirmDialog, ErrorNotice, Field } from '../../../../../shared/ui/components';
import { ACTIONS } from '../../../infrastructure/catalogueApi';
import { messages } from '../../messages';
import { checkReason, currentReview, effectiveProposal, isAmount, LIMITS, missingRequirements, REQUIREMENT_SECTIONS, serverFieldErrors } from '../../../domain/productRules';
import { useProductCommand } from '../ProductDetailPage';
import { newCommandId } from '../../../../../shared/api/http';
import { ApplyApproved, ApplyNowToggle, approvedRows, BulkPriceBar, PriceGap, SelectCell } from './BatchPrices';
import { NoticeAt } from '../notices';

/**
 * T034 initial review, before the first publication: submit freezes the content and every size's proposal;
 * a Manager approves the whole file (an amount for every size, adjustable) or rejects it with a reason — never a
 * part of it. Approval applies and publishes nothing. Cancel never calls the API.
 */
export function ReviewSection({ product, api, reload, setNotice, deleted, missing = {}, flagMissing = () => {} }) {
  const t = useT(messages);
  const review = currentReview(product);
  const submitCommand = useProductCommand({ reload, setNotice });
  const [confirmSubmit, setConfirmSubmit] = useState(false);
  const history = [...(product.reviews || [])].sort((a, b) => b.revision - a.revision);

  // A known gap (no image, inactive category, a size without a price) is pointed at before anything is sent.
  const askSubmit = () => {
    if (Object.keys(missingRequirements(product)).length) { submitCommand.reset(); flagMissing(); return; }
    setConfirmSubmit(true);
  };
  const submit = async () => {
    const outcome = await submitCommand.run(key => api.submitReview(key, product.id, product.version), { key: 'review.submitted', at: 'review' }, ACTIONS.submitReview);
    if (outcome.ok || !outcome.error?.retryable) setConfirmSubmit(false);
    if (!outcome.ok && outcome.error?.fieldErrors?.length) flagMissing();
  };
  const missingList = Object.entries(REQUIREMENT_SECTIONS).filter(([section]) => missing[section]);

  return (
    <section className="workspace-card" aria-labelledby="review-title">
      <h2 id="review-title">{t('section.review')}</h2>
      <p>{t('review.current')}: <strong>{review ? t(`state.${review.state}`) : t('review.none')}</strong></p>
      {review?.state === 'REJECTED' && review.reason && <p className="notice error pre-line">{t('review.rejectedBecause', { reason: review.reason })}</p>}
      {review?.state === 'APPROVED' && <p className="notice success">{t(approvedRows(product).length ? 'review.approvedNext' : 'review.approvedPublishNext')}</p>}
      {review?.state === 'APPROVED' && !deleted && <ApplyApproved product={product} api={api} reload={reload} setNotice={setNotice} />}

      {!deleted && review?.state !== 'PENDING' && review?.state !== 'APPROVED' && (
        <>
          <p className="muted small">{t('review.prerequisites')}</p>
          {missingList.length > 0 && (
            <div className="notice error" role="alert">
              <p>{t('require.title')}</p>
              <ul className="batch-results">
                {missingList.map(([section, id]) => <li key={section}><a href={`#${id}`}>{t(missing[section])}</a></li>)}
              </ul>
            </div>
          )}
          <button type="button" className="button primary" onClick={askSubmit}>{t('review.submit')}</button>
          {!confirmSubmit && <ErrorNotice error={submitCommand.error} messages={messages} onReload={reload} />}
        </>
      )}
      <ConfirmDialog open={confirmSubmit} title={t('review.submit')} confirmLabel={t('review.submit')} pending={submitCommand.pending}
        onConfirm={submit} onCancel={() => { setConfirmSubmit(false); submitCommand.reset(); }}>
        <p>{t('review.submitConfirm')}</p>
        <ErrorNotice error={submitCommand.error} messages={messages} onRetry={submitCommand.retry} />
      </ConfirmDialog>

      {!deleted && review?.state === 'PENDING' && <ReviewDecision product={product} review={review} api={api} reload={reload} setNotice={setNotice} />}

      <NoticeAt at="review" />

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
  const proposedPrices = () => Object.fromEntries(rows.map(({ variant, proposal }) => [variant.id, proposal?.proposedPrice || '']));
  const [prices, setPrices] = useState(proposedPrices);
  // The whole file is still approved at once (every size needs an amount); choosing sizes only scopes the bulk amount.
  const [selected, setSelected] = useState({});
  const [reason, setReason] = useState('');
  const [errors, setErrors] = useState({});
  const [applyNow, setApplyNow] = useState(true);
  const command = useProductCommand({ reload, setNotice });
  const chosen = rows.filter(({ variant }) => selected[variant.id] !== false);
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
    // Approve, then (if ticked) apply each approved price read back from the product, every apply its own command.
    // If an apply fails the error shows here; Retry replays the same decision (same key) and applies what is left.
    const withApply = mode === 'APPROVE' && applyNow;
    const send = async key => {
      const result = await api.decideReview(key, product.id, body);
      let applied = 0;
      if (withApply) {
        for (const { proposal } of approvedRows(await api.getProduct(product.id))) {
          await api.applyPrice(newCommandId(), proposal.id, proposal.version);
          applied += 1;
        }
      }
      return { ...result, applied };
    };
    const notice = mode === 'REJECT' ? { key: 'review.rejected', at: 'review' }
      : result => (withApply ? { key: 'review.approvedApplied', params: { count: result?.applied ?? 0 }, at: 'review' } : { key: 'review.approved', at: 'review' });
    const outcome = await command.run(send, notice, ACTIONS.decideReview);
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
            <>
              {rows.length > 1 && (
                <BulkPriceBar count={chosen.length}
                  onApply={amount => setPrices(current => ({ ...current, ...Object.fromEntries(chosen.map(({ variant }) => [variant.id, amount])) }))}
                  onReset={() => setPrices(proposedPrices())} />
              )}
              <div className="data-table" role="table" aria-label={t('review.decisionTitle')}>
              <div className="data-row head decision" role="row">
                <SelectCell checked={chosen.length === rows.length} label={t('bulk.selectAll')}
                  onChange={checked => setSelected(Object.fromEntries(rows.map(({ variant }) => [variant.id, checked])))} />
                <span role="columnheader">{t('field.size')}</span><span role="columnheader">{t('price.proposed')}</span>
                <span role="columnheader">{t('price.approvedAmount')}</span><span role="columnheader">{t('bulk.diff')}</span>
              </div>
              {rows.map(({ variant, proposal }, index) => (
                <div className={`data-row decision${String(prices[variant.id]) !== String(proposal?.proposedPrice || '') ? ' changed' : ''}`} role="row" key={variant.id}>
                  <SelectCell checked={selected[variant.id] !== false} label={t('bulk.selectSize', { size: variant.size })}
                    onChange={checked => setSelected(current => ({ ...current, [variant.id]: checked }))} />
                  <span role="cell">{variant.size}</span>
                  <span role="cell">{format.money(proposal?.proposedPrice)}</span>
                  <span role="cell">
                    <input aria-label={t('review.approvedFor', { size: variant.size })} inputMode="numeric" value={prices[variant.id]}
                      aria-invalid={errors[variant.id] ? true : undefined}
                      onChange={event => setPrices(current => ({ ...current, [variant.id]: event.target.value }))} />
                    {(errors[variant.id] || serverErrors[`prices[${index}].approvedPrice`]) && <span className="field-error">{errors[variant.id] || serverErrors[`prices[${index}].approvedPrice`]}</span>}
                  </span>
                  <span role="cell"><PriceGap proposed={proposal?.proposedPrice} approved={prices[variant.id]} /></span>
                </div>
              ))}
              </div>
            </>
          ) : (
            <Field label={t('decision.reason')} error={errors.reason || serverErrors.reason}>
              <textarea value={reason} maxLength={LIMITS.reasonMax} onChange={event => setReason(event.target.value)} />
            </Field>
          )}
          {serverErrors.prices && <p className="field-error">{serverErrors.prices}</p>}
          {mode === 'APPROVE' && <ApplyNowToggle checked={applyNow} onChange={setApplyNow} />}
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
