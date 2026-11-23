import React, { useEffect, useState } from 'react';
import { useLocation } from 'react-router';
import { useFormat, useT } from '../../../../../shared/i18n/I18nProvider';
import { ErrorNotice, Field } from '../../../../../shared/ui/components';
import { ACTIONS } from '../../../application/useCatalogueCommand';
import { messages } from '../../messages';
import { checkReason, effectiveProposal, isAmount, LIMITS, proposalStatus, serverFieldErrors } from '../../../domain/productRules';
import { MissingNote, sectionClass, useProductCommand } from '../ProductDetailPage';
import { newCommandId } from '../../../../../shared/api/http';
import { ApplyNowToggle, BatchPrices } from './BatchPrices';
import { NoticeAt } from '../notices';
import { useRole } from '../../../../../shared/auth/useRole';
import { SubmitterInfo } from './SubmitterInfo';
import { CollapsibleHead, useCollapsible } from '../Collapsible';

/**
 * T057 price proposals per size: proposed, approved and applied amounts are shown apart with the revision
 * history. Before the first publication the proposals travel with the product review (ReviewSection); after it a
 * size has its own submit/decision. Applying takes no amount: it applies exactly the approved revision.
 * Deciding is for a Manager (a Staff member gets 403 from the backend); a Manager may decide their own proposal.
 */
export function PriceSection({ product, missing = {}, ...context }) {
  const t = useT(messages);
  const isManager = useRole() === 'MANAGER';
  const variants = product.variants || [];
  // Session 2026-11-22: with many sizes the price history is long, so each size folds. Sizes that need someone (a draft,
  // a submission, an approved price not applied yet) start open; with three sizes or fewer nothing folds.
  const collapsible = variants.length > 3;
  // The "Bị từ chối" price list (2026-11-23) links here with ?priceSize=<variant id> so that size opens.
  const needs = NEEDS;
  const linkedSize = new URLSearchParams(useLocation().search).get('priceSize');
  const [open, setOpen] = useState(() => new Set(variants
    .filter(variant => needs.includes(currentStatus(variant)) || String(variant.id) === linkedSize)
    .map(variant => variant.id)));
  const toggle = id => setOpen(current => {
    const next = new Set(current);
    if (next.has(id)) next.delete(id); else next.add(id);
    return next;
  });
  // The section itself folds too: open while a size needs someone, with three sizes or fewer, or when something is missing.
  const waiting = variants.filter(variant => needs.includes(currentStatus(variant))).length;
  const [sectionOpen, toggleSection] = useCollapsible(!collapsible || waiting > 0 || Boolean(linkedSize));
  const showBody = sectionOpen || Boolean(missing.prices);
  useEffect(() => { if (linkedSize) document.getElementById('price-title')?.scrollIntoView?.({ block: 'start' }); }, [linkedSize]);
  return (
    <section className={sectionClass(missing.prices)} aria-labelledby="price-title">
      <CollapsibleHead id="price-title" title={t('section.prices')} open={showBody} onToggle={toggleSection}
        summary={waiting > 0 ? t('price.waitingCount', { count: waiting }) : t('section.sizeCount', { count: variants.length })} />
      {showBody && (<div id="price-title-body">
      <MissingNote messageKey={missing.prices} />
      {!product.firstPublishedAt && <p className="muted small">{t(isManager ? 'price.initialHintManager' : 'price.initialHint')}</p>}
      {!context.deleted && !context.outOfScope && <BatchPrices product={product} isManager={isManager} {...context} />}
      {variants.map(variant => (
        <VariantPrices key={variant.id} product={product} variant={variant} isManager={isManager} {...context}
          expanded={!collapsible || open.has(variant.id)} collapsible={collapsible} onToggle={() => toggle(variant.id)} />
      ))}
      </div>)}
    </section>
  );
}

function VariantPrices({ product, variant, api, reload, setNotice, deleted, outOfScope = false, isManager = true, expanded = true, collapsible = false, onToggle }) {
  const t = useT(messages);
  const format = useFormat();
  const current = effectiveProposal(variant);
  const independent = Boolean(product.firstPublishedAt);
  const state = current?.state;
  const canRevise = state === 'DRAFT' || state === 'PENDING';
  // FR-066: a Manager changes the price of a published size in one step, unless a submission waits for a decision
  // (then the decision form below is used, so a Staff proposal is never withdrawn).
  const oneStep = isManager && independent && state !== 'PENDING';
  const history = [...(variant.proposals || [])].sort((a, b) => b.revision - a.revision);
  // Every outcome of this size names it (and the amount) and shows again under this size's buttons.
  const at = `price-${variant.id}`;
  const note = (key, amount) => ({ key, params: { size: variant.size, amount: amount === undefined ? undefined : format.money(String(amount)) }, at });

  return (
    <div className="price-block">
      {collapsible
        ? (
          <CollapsibleHead level={3} id={`price-size-${variant.id}`} title={t('size', { size: variant.size })} open={expanded} onToggle={onToggle}
            summary={<><span className="muted">{format.money(variant.appliedPrice)}</span>{current && <>{' '}<PriceStatus status={currentStatus(variant)} /></>}</>} />
        )
        : <h3>{t('size', { size: variant.size })} <span className="muted small">— {t('variant.appliedPrice')}: {format.money(variant.appliedPrice)}</span></h3>}
      {expanded && (<div id={`price-size-${variant.id}-body`}>
      {history.length > 0 ? (
        <div className="data-table" role="table" aria-label={t('price.history', { size: variant.size })}>
          <div className="data-row head prices" role="row">
            <span role="columnheader">{t('price.revision')}</span><span role="columnheader">{t('price.state')}</span>
            <span role="columnheader">{t('price.proposed')}</span><span role="columnheader">{t('price.approved')}</span>
            <span role="columnheader">{t('price.reason')}</span>
          </div>
          {history.map(proposal => (
            <React.Fragment key={`${proposal.id}-${proposal.revision}`}>
            <div className={`data-row prices${proposal === current ? ' current' : ''}`} role="row">
              <span role="cell">{proposal.revision}{proposal === current && ` (${t('price.current')})`}</span>
              <span role="cell"><PriceStatus status={proposalStatus(variant, proposal)} /></span>
              <span role="cell">{format.money(proposal.proposedPrice)}</span>
              <span role="cell">{format.money(proposal.approvedPrice)}</span>
              <span role="cell" className="pre-line">{proposal.reason || '—'}</span>
            </div>
            {(proposal.submittedBy || proposal.decidedBy) && (
              <div className={`data-row people${proposal === current ? ' current' : ''}`} role="row">
                <span role="cell" aria-label={t('people.column')}><SubmitterInfo item={proposal} /></span>
              </div>
            )}
            </React.Fragment>
          ))}
        </div>
      ) : <p className="muted">{t('price.none')}</p>}

      {!deleted && !outOfScope && (
        <div className="price-actions">
          {oneStep
            ? <ChangePriceForm variant={variant} current={state === 'DRAFT' ? current : null} api={api} reload={reload} setNotice={setNotice} note={note} at={at} />
            : canRevise
            ? <AmountForm label={t('price.revise')} hint={state === 'PENDING' ? t('price.reviseWithdraws') : null} initial={current.proposedPrice}
              send={(key, amount) => api.revisePrice(key, current.id, current.version, amount)} action={ACTIONS.revisePrice} notice={amount => note(state === 'PENDING' ? 'price.revisedWithdrawn' : 'price.revised', amount)} reload={reload} setNotice={setNotice} />
            : <AmountForm label={t('price.propose')} initial=""
              send={(key, amount) => api.proposePrice(key, variant.id, variant.version, amount)} action={ACTIONS.proposePrice} notice={amount => note(independent ? 'price.proposedDraft' : 'price.proposedInitial', amount)} reload={reload} setNotice={setNotice} />}
          {state === 'DRAFT' && independent && !oneStep && <p className="muted small price-step">{t('price.draftHint')}</p>}
          {state === 'DRAFT' && independent && !oneStep && (
            <ActionButton label={t('price.submit')} send={key => api.submitPrice(key, current.id, current.version)} action={ACTIONS.submitPrice} notice={note('price.submitted')} reload={reload} setNotice={setNotice} />
          )}
          {state === 'PENDING' && independent && <p className="muted small price-step">{t('price.pendingHint')}</p>}
          {state === 'PENDING' && independent && isManager && <PriceDecision proposal={current} note={note} api={api} reload={reload} setNotice={setNotice} />}
          {state === 'PENDING' && !independent && <p className="muted small">{t('price.decideInReview')}</p>}
          {/* T228 (FR-067): an approved revision built on an older applied price is "Đã thay thế" and can no longer be
              applied (PRICE_APPLICATION_STALE), so only a revision still waiting to be applied offers the button. */}
          {state === 'APPROVED' && proposalStatus(variant, current) === 'APPROVED_WAITING' && (
            <ActionButton label={t('price.apply', { amount: format.money(current.approvedPrice) })} send={key => api.applyPrice(key, current.id, current.version)} action={ACTIONS.applyPrice} notice={note('price.appliedNotice', current.approvedPrice)} reload={reload} setNotice={setNotice} />
          )}
          <NoticeAt at={at} />
        </div>
      )}
      </div>)}
    </div>
  );
}

/** FR-067 status of the size's current revision (the one the actions below act on). */
const NEEDS = ['DRAFT', 'PENDING', 'APPROVED_WAITING'];

function currentStatus(variant) {
  const current = effectiveProposal(variant);
  return current ? proposalStatus(variant, current) : null;
}

function PriceStatus({ status }) {
  const t = useT(messages);
  const tone = status === 'APPLIED' ? ' ok' : status === 'APPROVED_WAITING' || status === 'PENDING' ? ' warn' : '';
  return <span className={`status${tone}`}>{t(`priceStatus.${status}`)}</span>;
}

/**
 * FR-066 (Session 2026-11-22): a Manager's price change in one save. The screen sends, one command each (FR-013–FR-016):
 * propose (or revise the current draft) → submit → approve at the typed amount → apply when ticked. A failure after the
 * first command keeps what was done: the page reloads and says the change is not finished, so the Manager carries on with
 * the buttons of the state reached (decide / apply).
 */
function ChangePriceForm({ variant, current, api, reload, setNotice, note, at }) {
  const t = useT(messages);
  const [amount, setAmount] = useState(null);
  const [applyNow, setApplyNow] = useState(true);
  const [localError, setLocalError] = useState(null);
  const command = useProductCommand({ reload, setNotice });
  if (amount === null) return <button type="button" className="button outline" onClick={() => setAmount('')}>{t('price.change')}</button>;

  const submit = async event => {
    event.preventDefault();
    setNotice(null); // a new action replaces the previous outcome (Session 2026-11-04)
    if (!isAmount(amount)) { setLocalError(t('validation.amount')); return; }
    setLocalError(null);
    const value = String(amount).trim();
    let started = false;
    const send = async key => {
      const created = current
        ? await api.revisePrice(key, current.id, current.version, value)
        : await api.proposePrice(key, variant.id, variant.version, value);
      started = true;
      const submitted = await api.submitPrice(newCommandId(), created.resourceId, created.version);
      const decided = await api.decidePrice(newCommandId(), submitted.resourceId,
        { expectedVersion: submitted.version, decision: 'APPROVE', approvedPrice: value });
      if (applyNow) await api.applyPrice(newCommandId(), decided.resourceId, decided.version);
      return decided;
    };
    const outcome = await command.run(send, note(applyNow ? 'price.approvedAppliedNotice' : 'price.approvedNotice', value), ACTIONS.proposePrice);
    if (outcome.ok) { setAmount(null); return; }
    if (started) {
      await reload();
      setAmount(null);
      command.reset();
      setNotice({ ...note('price.changeIncomplete', value), tone: 'error' });
    }
  };
  const fieldError = serverFieldErrors(command.error).proposedPrice || serverFieldErrors(command.error).approvedPrice || localError;
  return (
    <form className="amount-form" onSubmit={submit} noValidate aria-label={t('price.change')}>
      <Field label={t('price.newPrice')} error={fieldError}><input inputMode="numeric" value={amount} onChange={event => setAmount(event.target.value)} /></Field>
      <ApplyNowToggle checked={applyNow} onChange={setApplyNow} />
      <ErrorNotice error={command.error} messages={messages} onReload={reload} />
      <div className="actions">
        <button type="submit" className="button primary" disabled={command.pending}>{t('save')}</button>
        <button type="button" className="button outline" onClick={() => { setAmount(null); setLocalError(null); command.reset(); }}>{t('cancel')}</button>
      </div>
    </form>
  );
}

function AmountForm({ label, hint, initial, send, action, notice, reload, setNotice }) {
  const t = useT(messages);
  const [amount, setAmount] = useState(null);
  const [localError, setLocalError] = useState(null);
  const command = useProductCommand({ reload, setNotice });
  if (amount === null) return <button type="button" className="button outline" onClick={() => setAmount(initial || '')}>{label}</button>;
  const submit = async event => {
    event.preventDefault();
    setNotice(null); // a new action replaces the previous outcome, even when it is refused here (Session 2026-11-04)
    if (!isAmount(amount)) { setLocalError(t('validation.amount')); return; }
    setLocalError(null);
    const outcome = await command.run(key => send(key, amount), notice(amount), action);
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

function ActionButton({ label, send, action, notice, reload, setNotice }) {
  const command = useProductCommand({ reload, setNotice });
  return (
    <div>
      <button type="button" className="button primary" disabled={command.pending} onClick={() => command.run(send, notice, action)}>{label}</button>
      <ErrorNotice error={command.error} messages={messages} onRetry={command.retry} onReload={reload} />
    </div>
  );
}

export function PriceDecision({ proposal, note, api, reload, setNotice }) {
  const t = useT(messages);
  const [mode, setMode] = useState(null);
  const [approvedPrice, setApprovedPrice] = useState(proposal.proposedPrice);
  const [reason, setReason] = useState('');
  const [localError, setLocalError] = useState(null);
  const [applyNow, setApplyNow] = useState(true);
  const command = useProductCommand({ reload, setNotice });

  const submit = async event => {
    event.preventDefault();
    setNotice(null); // a new action replaces the previous outcome, even when it is refused here (Session 2026-11-04)
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
    // Approve, then (if ticked) apply the revision just approved as its own command.
    const withApply = mode === 'APPROVE' && applyNow;
    const send = async key => {
      const result = await api.decidePrice(key, proposal.id, body);
      if (withApply) await api.applyPrice(newCommandId(), result.resourceId, result.version);
      return result;
    };
    const notice = mode === 'APPROVE' ? note(withApply ? 'price.approvedAppliedNotice' : 'price.approvedNotice', approvedPrice) : note('price.rejectedNotice');
    const outcome = await command.run(send, notice, ACTIONS.decidePrice);
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
      {mode === 'APPROVE' && <ApplyNowToggle checked={applyNow} onChange={setApplyNow} />}
      <ErrorNotice error={command.error} messages={messages} onRetry={command.retry} onReload={reload} />
      <div className="actions">
        <button type="submit" className="button primary" disabled={command.pending}>{mode === 'APPROVE' ? t('decision.approve') : t('decision.reject')}</button>
        <button type="button" className="button outline" onClick={() => { setMode(null); setLocalError(null); command.reset(); }}>{t('cancel')}</button>
      </div>
    </form>
  );
}
