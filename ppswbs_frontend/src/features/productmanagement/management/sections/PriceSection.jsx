import React, { useState } from 'react';
import { useFormat, useT } from '../../../../shared/i18n/I18nProvider';
import { ErrorNotice, Field } from '../../../../shared/ui/components';
import { messages } from '../../messages';
import { checkReason, effectiveProposal, isAmount, LIMITS, serverFieldErrors } from '../../rules';
import { useProductCommand } from '../ProductDetailPage';

/**
 * T057 price proposals per size: proposed, approved and applied amounts are shown apart with the revision
 * history. Before the first publication the proposals travel with the product review (ReviewSection); after it a
 * size has its own submit/decision. Applying takes no amount: it applies exactly the approved revision.
 * Deciding is for a Manager (a Staff member gets 403 from the backend); a Manager may decide their own proposal.
 */
export function PriceSection({ product, ...context }) {
  const t = useT(messages);
  return (
    <section className="workspace-card" aria-labelledby="price-title">
      <h2 id="price-title">{t('section.prices')}</h2>
      {!product.firstPublishedAt && <p className="muted small">{t('price.initialHint')}</p>}
      {(product.variants || []).map(variant => <VariantPrices key={variant.id} product={product} variant={variant} {...context} />)}
    </section>
  );
}

function VariantPrices({ product, variant, api, reload, setNotice, deleted }) {
  const t = useT(messages);
  const format = useFormat();
  const current = effectiveProposal(variant);
  const independent = Boolean(product.firstPublishedAt);
  const state = current?.state;
  const canRevise = state === 'DRAFT' || state === 'PENDING';
  const history = [...(variant.proposals || [])].sort((a, b) => b.revision - a.revision);

  return (
    <div className="price-block">
      <h3>{t('size', { size: variant.size })} <span className="muted small">— {t('variant.appliedPrice')}: {format.money(variant.appliedPrice)}</span></h3>
      {history.length > 0 ? (
        <div className="data-table" role="table" aria-label={t('price.history', { size: variant.size })}>
          <div className="data-row head prices" role="row">
            <span role="columnheader">{t('price.revision')}</span><span role="columnheader">{t('price.state')}</span>
            <span role="columnheader">{t('price.proposed')}</span><span role="columnheader">{t('price.approved')}</span>
            <span role="columnheader">{t('price.applied')}</span><span role="columnheader">{t('price.reason')}</span>
          </div>
          {history.map(proposal => (
            <div className={`data-row prices${proposal.effective ? ' current' : ''}`} role="row" key={`${proposal.id}-${proposal.revision}`}>
              <span role="cell">{proposal.revision}{proposal.effective && ` (${t('price.current')})`}</span>
              <span role="cell">{t(`state.${proposal.state}`)}</span>
              <span role="cell">{format.money(proposal.proposedPrice)}</span>
              <span role="cell">{format.money(proposal.approvedPrice)}</span>
              <span role="cell">{proposal.applied ? t('yes') : t('no')}</span>
              <span role="cell" className="pre-line">{proposal.reason || '—'}</span>
            </div>
          ))}
        </div>
      ) : <p className="muted">{t('price.none')}</p>}

      {!deleted && (
        <div className="price-actions">
          {canRevise
            ? <AmountForm label={t('price.revise')} hint={state === 'PENDING' ? t('price.reviseWithdraws') : null} initial={current.proposedPrice}
              send={(key, amount) => api.revisePrice(key, current.id, current.version, amount)} successKey="price.revised" reload={reload} setNotice={setNotice} />
            : <AmountForm label={t('price.propose')} initial=""
              send={(key, amount) => api.proposePrice(key, variant.id, variant.version, amount)} successKey="price.proposedNotice" reload={reload} setNotice={setNotice} />}
          {state === 'DRAFT' && independent && (
            <ActionButton label={t('price.submit')} send={key => api.submitPrice(key, current.id, current.version)} successKey="price.submitted" reload={reload} setNotice={setNotice} />
          )}
          {state === 'PENDING' && independent && <PriceDecision proposal={current} api={api} reload={reload} setNotice={setNotice} />}
          {state === 'PENDING' && !independent && <p className="muted small">{t('price.decideInReview')}</p>}
          {state === 'APPROVED' && !current.applied && (
            <ActionButton label={t('price.apply', { amount: format.money(current.approvedPrice) })} send={key => api.applyPrice(key, current.id, current.version)} successKey="price.appliedNotice" reload={reload} setNotice={setNotice} />
          )}
        </div>
      )}
    </div>
  );
}

function AmountForm({ label, hint, initial, send, successKey, reload, setNotice }) {
  const t = useT(messages);
  const [amount, setAmount] = useState(null);
  const [localError, setLocalError] = useState(null);
  const command = useProductCommand({ reload, setNotice });
  if (amount === null) return <button type="button" className="button outline" onClick={() => setAmount(initial || '')}>{label}</button>;
  const submit = async event => {
    event.preventDefault();
    if (!isAmount(amount)) { setLocalError(t('validation.amount')); return; }
    setLocalError(null);
    const outcome = await command.run(key => send(key, amount), successKey);
    if (outcome.ok) setAmount(null);
  };
  const fieldError = serverFieldErrors(command.error).proposedPrice || localError;
  return (
    <form className="amount-form" onSubmit={submit} noValidate>
      <Field label={label} error={fieldError} hint={hint}><input inputMode="numeric" value={amount} onChange={event => setAmount(event.target.value)} /></Field>
      <ErrorNotice error={command.error} messages={messages} onRetry={command.retry} onReload={reload} />
      <div className="actions">
        <button type="submit" className="button primary" disabled={command.pending}>{t('save')}</button>
        <button type="button" className="button outline" onClick={() => { setAmount(null); setLocalError(null); command.reset(); }}>{t('cancel')}</button>
      </div>
    </form>
  );
}

function ActionButton({ label, send, successKey, reload, setNotice }) {
  const command = useProductCommand({ reload, setNotice });
  return (
    <div>
      <button type="button" className="button primary" disabled={command.pending} onClick={() => command.run(send, successKey)}>{label}</button>
      <ErrorNotice error={command.error} messages={messages} onRetry={command.retry} onReload={reload} />
    </div>
  );
}

function PriceDecision({ proposal, api, reload, setNotice }) {
  const t = useT(messages);
  const [mode, setMode] = useState(null);
  const [approvedPrice, setApprovedPrice] = useState(proposal.proposedPrice);
  const [reason, setReason] = useState('');
  const [localError, setLocalError] = useState(null);
  const command = useProductCommand({ reload, setNotice });

  const submit = async event => {
    event.preventDefault();
    let body;
    if (mode === 'APPROVE') {
      if (!isAmount(approvedPrice)) { setLocalError(t('validation.amount')); return; }
      body = { expectedVersion: proposal.version, decision: 'APPROVE', approvedPrice };
    } else {
      const problem = checkReason(reason);
      if (problem) { setLocalError(t(problem)); return; }
      body = { expectedVersion: proposal.version, decision: 'REJECT', reason: reason.trim() };
    }
    setLocalError(null);
    const outcome = await command.run(key => api.decidePrice(key, proposal.id, body), mode === 'APPROVE' ? 'price.approvedNotice' : 'price.rejectedNotice');
    if (outcome.ok) setMode(null);
  };

  if (!mode) {
    return (
      <div className="actions">
        <button type="button" className="button primary" onClick={() => setMode('APPROVE')}>{t('decision.approve')}</button>
        <button type="button" className="button outline" onClick={() => setMode('REJECT')}>{t('decision.reject')}</button>
      </div>
    );
  }
  const serverErrors = serverFieldErrors(command.error);
  return (
    <form className="amount-form" onSubmit={submit} noValidate>
      {mode === 'APPROVE'
        ? <Field label={t('price.approvedAmount')} error={serverErrors.approvedPrice || localError}><input inputMode="numeric" value={approvedPrice} onChange={event => setApprovedPrice(event.target.value)} /></Field>
        : <Field label={t('decision.reason')} error={serverErrors.reason || localError}><textarea value={reason} maxLength={LIMITS.reasonMax} onChange={event => setReason(event.target.value)} /></Field>}
      <ErrorNotice error={command.error} messages={messages} onRetry={command.retry} onReload={reload} />
      <div className="actions">
        <button type="submit" className="button primary" disabled={command.pending}>{mode === 'APPROVE' ? t('decision.approve') : t('decision.reject')}</button>
        <button type="button" className="button outline" onClick={() => { setMode(null); setLocalError(null); command.reset(); }}>{t('cancel')}</button>
      </div>
    </form>
  );
}
