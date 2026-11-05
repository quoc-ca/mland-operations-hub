import React, { useRef, useState } from 'react';
import { newCommandId } from '../../../../../shared/api/http';
import { useFormat, useT } from '../../../../../shared/i18n/I18nProvider';
import { Field, useErrorText } from '../../../../../shared/ui/components';
import { ACTIONS } from '../../../application/useCatalogueCommand';
import { messages } from '../../messages';
import { NoticeAt } from '../notices';
import { checkReason, effectiveProposal, isAmount, LIMITS } from '../../../domain/productRules';

/**
 * Bulk price helpers shared by the initial review and the per-size price section: one amount applied to the chosen
 * sizes, a reset to the proposed amounts, and the gap between approved and proposed shown on each row.
 */
export function BulkPriceBar({ count, onApply, onReset, labelKey = 'bulk.price', resetKey = 'bulk.resetProposed' }) {
  const t = useT(messages);
  const [amount, setAmount] = useState('');
  const [error, setError] = useState(null);
  const apply = () => {
    if (!isAmount(amount.trim())) { setError(t('validation.amount')); return; }
    setError(null);
    onApply(amount.trim());
  };
  return (
    <div className="size-bulk price-bulk">
      <Field label={t(labelKey)} error={error}><input inputMode="numeric" value={amount} onChange={event => setAmount(event.target.value)} /></Field>
      <button type="button" className="button outline" disabled={count === 0 || !amount.trim()} onClick={apply}>{t('bulk.applyTo', { count })}</button>
      <button type="button" className="button outline" onClick={onReset}>{t(resetKey)}</button>
    </div>
  );
}

export function PriceGap({ proposed, approved, sameKey = 'bulk.same' }) {
  const t = useT(messages);
  const format = useFormat();
  if (!isAmount(String(approved ?? '').trim()) || !isAmount(String(proposed ?? ''))) return <span className="muted">—</span>;
  const gap = Number(approved) - Number(proposed);
  if (gap === 0) return <span className="muted small">{t(sameKey)}</span>;
  return <span className={`price-gap ${gap > 0 ? 'up' : 'down'}`}>{gap > 0 ? '+' : '−'}{format.money(String(Math.abs(gap)))}</span>;
}

export function SelectCell({ checked, label, onChange }) {
  return <span role="cell"><input type="checkbox" aria-label={label} checked={checked} onChange={event => onChange(event.target.checked)} /></span>;
}

/**
 * Runs one existing per-size command for each chosen size, one after another, each with its own idempotency key.
 * Every size is its own decision (FR-013/FR-014), so a failure never stops or undoes the others; the outcome of each
 * size is kept for the list under the table. A size whose answer was lost keeps its key: the next run first asks for
 * the receipt of that key and only sends again (same key, replayed by the backend) when there is none (FR-028).
 * An item may name its own action (sizes of one batch can use different commands) and an `after` step that runs on
 * the committed result, sent or read from the receipt (e.g. submitting the proposal it just saved).
 */
export function useBatch({ api, reload, setNotice }) {
  const t = useT(messages);
  const [state, setState] = useState({ pending: false, results: [] });
  const lostKeys = useRef({});
  // outcome: { key, at, section } -- the notice when every size went through ({count} is filled in), and the section
  // named in the page-top warning when some failed (the failed sizes are listed under the button).
  const run = async (items, action, outcome = {}) => {
    setNotice(null);
    const results = [];
    for (const item of items) {
      setState(current => ({ ...current, pending: true }));
      const known = lostKeys.current[item.id];
      const key = known || newCommandId();
      try {
        let result;
        let done = false;
        if (known) {
          try { result = (await api.getCommandReceipt(key, item.action || action))?.result; done = true; } catch (error) { if (error?.code !== 'COMMAND_RESULT_NOT_FOUND') throw error; }
        }
        if (!done) result = await item.send(key);
        if (item.after) await item.after(result);
        delete lostKeys.current[item.id];
        results.push({ id: item.id, size: item.size, ok: true });
      } catch (error) {
        if (error?.status === 0) lostKeys.current[item.id] = key; else delete lostKeys.current[item.id];
        results.push({ id: item.id, size: item.size, ok: false, error });
      }
    }
    // The outcome shows only once the product is read again, so nothing flashes before the table closes or changes.
    if (results.some(result => result.ok)) await reload();
    setState({ pending: false, results });
    const ok = results.filter(result => result.ok).length;
    if (ok === results.length) {
      if (outcome.key) setNotice({ key: outcome.key, params: { count: ok }, at: outcome.at });
    } else {
      setNotice({ key: 'batch.partial', params: { ok, total: results.length, section: t(outcome.section || 'section.prices') }, tone: 'error' });
    }
    return results;
  };
  const clear = () => setState(current => ({ ...current, results: [] }));
  return { ...state, run, clear };
}

export function BatchResults({ batch, at }) {
  const t = useT(messages);
  const errorText = useErrorText(messages);
  if (batch.pending || !batch.results.length) return null;
  const failed = batch.results.filter(result => !result.ok);
  // Every size went through: the outcome notice again, under the button. Otherwise the count, then only the sizes
  // that failed and why.
  if (!failed.length) return <NoticeAt at={at} />;
  return (
    <div className="notice error" role="status">
      <p>{t('batch.result', { ok: batch.results.length - failed.length, total: batch.results.length })}</p>
      <ul className="batch-results">
        {failed.map(result => <li key={result.id}>{t('batch.failItem', { size: result.size, error: errorText(result.error) })}</li>)}
      </ul>
    </div>
  );
}

export const selectionOf = (selected, rows) => rows.filter(row => selected[row.variant.id] !== false);

/** Sizes whose current proposal is approved but not applied yet, in size order. */
export function approvedRows(product) {
  return (product.variants || [])
    .map(variant => ({ variant, proposal: effectiveProposal(variant) }))
    .filter(row => row.proposal?.state === 'APPROVED' && !row.proposal.applied)
    .sort((a, b) => Number(a.variant.size) - Number(b.variant.size));
}

/**
 * "Apply right after approving": approving and applying stay two commands with their own records (the spec keeps
 * decision and application apart), the Manager only does not have to come back for the second. Unticked, the
 * approved price waits to be applied later.
 */
export function ApplyNowToggle({ checked, onChange }) {
  const t = useT(messages);
  return (
    <label className="apply-now">
      <input type="checkbox" checked={checked} onChange={event => onChange(event.target.checked)} />
      <span>{t('decision.applyNow')}</span>
    </label>
  );
}

/** Applies every approved, not yet applied price of the product (one command per size), from one button. */
export function ApplyApproved({ product, api, reload, setNotice }) {
  return (
    <SimpleBatch rows={approvedRows(product)} min={1} labelKey="batch.applyAll" action={ACTIONS.applyPrice} successKey="batch.appliedN" at="review-apply"
      send={({ proposal }) => key => api.applyPrice(key, proposal.id, proposal.version)} api={api} reload={reload} setNotice={setNotice} />
  );
}

/**
 * After the first publication each size's proposal is decided on its own. With two or more sizes waiting, a Manager
 * can decide them together here (approve with an amount per size, or reject with one shared reason), apply every
 * approved price in one go, or a Staff member can submit every draft. The per-size blocks below stay as they were.
 */
export function BatchPrices({ product, api, reload, setNotice }) {
  const independent = Boolean(product.firstPublishedAt);
  const rows = (product.variants || [])
    .map(variant => ({ variant, proposal: effectiveProposal(variant) }))
    .sort((a, b) => Number(a.variant.size) - Number(b.variant.size));
  const pending = independent ? rows.filter(row => row.proposal?.state === 'PENDING') : [];
  const drafts = independent ? rows.filter(row => row.proposal?.state === 'DRAFT') : [];
  // Before the first publication the Initial review section offers this apply button, so it is not shown twice.
  const approved = independent ? approvedRows(product) : [];
  const context = { api, reload, setNotice };
  return (
    <>
      <ProposeBatch rows={rows} independent={independent} {...context} />
      <PendingBatch rows={pending} {...context} />
      <SimpleBatch rows={drafts} labelKey="batch.submitAll" action={ACTIONS.submitPrice} successKey="batch.submittedN" at="submit-batch"
        send={({ proposal }) => key => api.submitPrice(key, proposal.id, proposal.version)} {...context} />
      <SimpleBatch rows={approved} labelKey="batch.applyAll" action={ACTIONS.applyPrice} successKey="batch.appliedN" at="apply-batch"
        send={({ proposal }) => key => api.applyPrice(key, proposal.id, proposal.version)} {...context} />
    </>
  );
}

function SimpleBatch({ rows, labelKey, action, send, api, reload, setNotice, min = 2, successKey, at }) {
  const t = useT(messages);
  const batch = useBatch({ api, reload, setNotice });
  if (rows.length < min && !batch.results.length) return null;
  const go = () => batch.run(rows.map(row => ({ id: row.variant.id, size: row.variant.size, send: send(row) })), action, { key: successKey, at });
  return (
    <div className="batch-prices">
      {rows.length >= min && (
        <button type="button" className="button primary" disabled={batch.pending} onClick={go}>
          {batch.pending ? t('saving') : t(labelKey, { count: rows.length })}
        </button>
      )}
      <BatchResults batch={batch} at={at} />
    </div>
  );
}

function PendingBatch({ rows, api, reload, setNotice }) {
  const t = useT(messages);
  const format = useFormat();
  const batch = useBatch({ api, reload, setNotice });
  const [selected, setSelected] = useState({});
  const [prices, setPrices] = useState({});
  const [errors, setErrors] = useState({});
  const [rejecting, setRejecting] = useState(false);
  const [reason, setReason] = useState('');
  const [applyNow, setApplyNow] = useState(true);
  if (rows.length < 2 && !batch.results.length) return null;

  // Amounts are keyed by proposal revision, so a reload that brings a newer proposal starts again from its amount.
  const keyOf = ({ proposal }) => `${proposal.id}-${proposal.version}`;
  const priceOf = row => prices[keyOf(row)] ?? row.proposal.proposedPrice;
  const chosen = selectionOf(selected, rows);
  const allChosen = rows.length > 0 && chosen.length === rows.length;
  const setAll = checked => setSelected(Object.fromEntries(rows.map(row => [row.variant.id, checked])));

  const approve = async () => {
    const found = {};
    if (!chosen.length) found.selection = t('batch.noneSelected');
    chosen.forEach(row => { if (!isAmount(String(priceOf(row)).trim())) found[row.variant.id] = t('validation.amount'); });
    setErrors(found);
    if (Object.keys(found).length) return;
    await batch.run(chosen.map(row => ({
      id: row.variant.id, size: row.variant.size,
      send: key => api.decidePrice(key, row.proposal.id, { expectedVersion: row.proposal.version, decision: 'APPROVE', approvedPrice: String(priceOf(row)).trim() }),
      after: applyNow ? result => api.applyPrice(newCommandId(), result.resourceId, result.version) : undefined,
    })), ACTIONS.decidePrice, { key: applyNow ? 'batch.approvedAppliedN' : 'batch.approvedN', at: 'pending-batch' });
  };
  const reject = async () => {
    const found = {};
    if (!chosen.length) found.selection = t('batch.noneSelected');
    const problem = checkReason(reason);
    if (problem) found.reason = t(problem);
    setErrors(found);
    if (Object.keys(found).length) return;
    const results = await batch.run(chosen.map(row => ({
      id: row.variant.id, size: row.variant.size,
      send: key => api.decidePrice(key, row.proposal.id, { expectedVersion: row.proposal.version, decision: 'REJECT', reason: reason.trim() }),
    })), ACTIONS.decidePrice, { key: 'batch.rejectedN', at: 'pending-batch' });
    if (results.every(result => result.ok)) { setRejecting(false); setReason(''); }
  };

  return (
    <div className="batch-prices review-decision">
      <h3>{t('batch.pendingTitle', { count: rows.length })}</h3>
      <p className="muted small">{t('batch.hint')}</p>
      {rows.length > 0 && (
        <>
          <BulkPriceBar count={chosen.length}
            onApply={amount => setPrices(current => ({ ...current, ...Object.fromEntries(chosen.map(row => [keyOf(row), amount])) }))}
            onReset={() => setPrices({})} />
          <div className="data-table" role="table" aria-label={t('batch.pendingTitle', { count: rows.length })}>
            <div className="data-row head batch" role="row">
              <SelectCell checked={allChosen} label={t('bulk.selectAll')} onChange={setAll} />
              <span role="columnheader">{t('field.size')}</span><span role="columnheader">{t('batch.applied')}</span>
              <span role="columnheader">{t('price.proposed')}</span><span role="columnheader">{t('price.approvedAmount')}</span>
              <span role="columnheader">{t('bulk.diff')}</span>
            </div>
            {rows.map(row => (
              <div className={`data-row batch${String(priceOf(row)) !== String(row.proposal.proposedPrice) ? ' changed' : ''}`} role="row" key={row.variant.id}>
                <SelectCell checked={selected[row.variant.id] !== false} label={t('bulk.selectSize', { size: row.variant.size })}
                  onChange={checked => setSelected(current => ({ ...current, [row.variant.id]: checked }))} />
                <span role="cell"><strong>{t('size', { size: row.variant.size })}</strong></span>
                <span role="cell">{format.money(row.variant.appliedPrice)}</span>
                <span role="cell">{format.money(row.proposal.proposedPrice)}</span>
                <span role="cell">
                  <input inputMode="numeric" aria-label={t('review.approvedFor', { size: row.variant.size })} value={priceOf(row)}
                    aria-invalid={errors[row.variant.id] ? true : undefined}
                    onChange={event => { const { value } = event.target; setPrices(current => ({ ...current, [keyOf(row)]: value })); }} />
                  {errors[row.variant.id] && <span className="field-error">{errors[row.variant.id]}</span>}
                </span>
                <span role="cell"><PriceGap proposed={row.proposal.proposedPrice} approved={priceOf(row)} /></span>
              </div>
            ))}
          </div>
          {errors.selection && <p className="field-error">{errors.selection}</p>}
          {rejecting && (
            <Field label={t('batch.rejectReason')} error={errors.reason}>
              <textarea value={reason} maxLength={LIMITS.reasonMax} onChange={event => setReason(event.target.value)} />
            </Field>
          )}
          {!rejecting && <ApplyNowToggle checked={applyNow} onChange={setApplyNow} />}
          <div className="actions form-actions">
            {!rejecting ? (
              <>
                <button type="button" className="button primary" disabled={batch.pending || !chosen.length} onClick={approve}>{batch.pending ? t('saving') : t('batch.approve', { count: chosen.length })}</button>
                <button type="button" className="button outline" disabled={batch.pending || !chosen.length} onClick={() => { setRejecting(true); batch.clear(); }}>{t('batch.reject', { count: chosen.length })}</button>
              </>
            ) : (
              <>
                <button type="button" className="button primary danger" disabled={batch.pending || !chosen.length} onClick={reject}>{batch.pending ? t('saving') : t('batch.confirmReject', { count: chosen.length })}</button>
                <button type="button" className="button outline" disabled={batch.pending} onClick={() => { setRejecting(false); setErrors({}); }}>{t('cancel')}</button>
              </>
            )}
          </div>
        </>
      )}
      <BatchResults batch={batch} at="pending-batch" />
    </div>
  );
}

export const SIZES = Array.from({ length: LIMITS.maxSize - LIMITS.minSize + 1 }, (_, index) => String(LIMITS.minSize + index));

/**
 * A Staff member (or Manager) sets the proposed price of several sizes at once instead of one block at a time: choose
 * sizes (or a size range), fill one amount, adjust any row, then save. A size whose current proposal is a draft or
 * pending is revised (a pending one goes back to draft), any other size gets a new proposal. After the first
 * publication the saved drafts can be submitted in the same run; before it they travel with the initial review.
 */
function ProposeBatch({ rows, independent, api, reload, setNotice }) {
  const t = useT(messages);
  const format = useFormat();
  const batch = useBatch({ api, reload, setNotice });
  const [open, setOpen] = useState(false);
  const [selected, setSelected] = useState({});
  const [prices, setPrices] = useState({});
  const [errors, setErrors] = useState({});
  const [from, setFrom] = useState(String(LIMITS.minSize));
  const [to, setTo] = useState(String(LIMITS.maxSize));
  if (rows.length < 2) return null;
  if (!open) {
    return <div className="batch-prices"><button type="button" className="button outline" onClick={() => setOpen(true)}>{t('propose.open')}</button></div>;
  }

  const priceOf = row => prices[row.variant.id] ?? '';
  const chosen = selectionOf(selected, rows);
  const allChosen = chosen.length === rows.length;
  const setAll = checked => setSelected(Object.fromEntries(rows.map(row => [row.variant.id, checked])));
  const chooseRange = () => {
    const low = Math.min(Number(from), Number(to));
    const high = Math.max(Number(from), Number(to));
    setSelected(Object.fromEntries(rows.map(row => [row.variant.id, Number(row.variant.size) >= low && Number(row.variant.size) <= high])));
  };
  const revisable = ({ proposal }) => proposal?.state === 'DRAFT' || proposal?.state === 'PENDING';

  // Only chosen rows with a typed amount are sent; an empty row is simply left as it is.
  const filled = chosen.filter(row => String(priceOf(row)).trim());
  const close = () => { setOpen(false); setErrors({}); setPrices({}); setSelected({}); batch.clear(); };

  const save = async submit => {
    const found = {};
    filled.forEach(row => { if (!isAmount(String(priceOf(row)).trim())) found[row.variant.id] = t('validation.amount'); });
    setErrors(found);
    if (Object.keys(found).length) return;
    const results = await batch.run(filled.map(row => {
      const amount = String(priceOf(row)).trim();
      return {
        id: row.variant.id, size: row.variant.size,
        action: revisable(row) ? ACTIONS.revisePrice : ACTIONS.proposePrice,
        send: key => (revisable(row)
          ? api.revisePrice(key, row.proposal.id, row.proposal.version, amount)
          : api.proposePrice(key, row.variant.id, row.variant.version, amount)),
        after: submit ? result => api.submitPrice(newCommandId(), result.resourceId, result.version) : undefined,
      };
    }), ACTIONS.proposePrice, {
      key: independent ? (submit ? 'propose.submittedN' : 'propose.draftsN') : 'propose.savedN', at: 'propose-batch',
    });
    // All saved: the table stays open with a success line under the buttons; the typed amounts are cleared, so the
    // save buttons are disabled and the same change cannot be sent twice by mistake.
    // Some failed: only the failed sizes stay chosen, with their amounts, ready to try again.
    if (results.every(result => result.ok)) {
      setPrices({});
      return;
    }
    const failed = new Set(results.filter(result => !result.ok).map(result => result.id));
    setPrices(current => Object.fromEntries(Object.entries(current).filter(([id]) => failed.has(id))));
    setSelected(Object.fromEntries(rows.map(row => [row.variant.id, failed.has(row.variant.id)])));
  };

  return (
    <div className="batch-prices review-decision">
      <h3>{t('propose.title')}</h3>
      <p className="muted small">{t(independent ? 'propose.hint' : 'propose.initialHint')}</p>
      <div className="size-tools">
        <label>{t('size.from')}
          <select value={from} onChange={event => setFrom(event.target.value)}>{SIZES.map(size => <option key={size} value={size}>{size}</option>)}</select>
        </label>
        <label>{t('size.to')}
          <select value={to} onChange={event => setTo(event.target.value)}>{SIZES.map(size => <option key={size} value={size}>{size}</option>)}</select>
        </label>
        <button type="button" className="button outline" onClick={chooseRange}>{t('propose.chooseRange')}</button>
      </div>
      <BulkPriceBar count={chosen.length} labelKey="bulk.proposedPrice" resetKey="bulk.clearPrices"
        onApply={amount => setPrices(current => ({ ...current, ...Object.fromEntries(chosen.map(row => [row.variant.id, amount])) }))}
        onReset={() => setPrices({})} />
      <div className="data-table" role="table" aria-label={t('propose.title')}>
        <div className="data-row head batch" role="row">
          <SelectCell checked={allChosen} label={t('bulk.selectAll')} onChange={setAll} />
          <span role="columnheader">{t('field.size')}</span><span role="columnheader">{t('batch.applied')}</span>
          <span role="columnheader">{t('propose.current')}</span><span role="columnheader">{t('propose.newPrice')}</span>
          <span role="columnheader">{t('bulk.diff')}</span>
        </div>
        {rows.map(row => (
          <div className={`data-row batch${priceOf(row) ? ' changed' : ''}`} role="row" key={row.variant.id}>
            <SelectCell checked={selected[row.variant.id] !== false} label={t('bulk.selectSize', { size: row.variant.size })}
              onChange={checked => setSelected(current => ({ ...current, [row.variant.id]: checked }))} />
            <span role="cell"><strong>{t('size', { size: row.variant.size })}</strong></span>
            <span role="cell">{format.money(row.variant.appliedPrice)}</span>
            <span role="cell">{row.proposal ? `${format.money(row.proposal.proposedPrice)} · ${t(`state.${row.proposal.state}`)}` : '—'}</span>
            <span role="cell">
              <input inputMode="numeric" aria-label={t('propose.newFor', { size: row.variant.size })} value={priceOf(row)}
                aria-invalid={errors[row.variant.id] ? true : undefined}
                onChange={event => { const { value } = event.target; setPrices(current => ({ ...current, [row.variant.id]: value })); }} />
              {errors[row.variant.id] && <span className="field-error">{errors[row.variant.id]}</span>}
            </span>
            <span role="cell"><PriceGap proposed={row.variant.appliedPrice} approved={priceOf(row)} sameKey="bulk.sameApplied" /></span>
          </div>
        ))}
      </div>
      {!filled.length && !batch.pending && !batch.results.length && <p className="muted small">{t('propose.nothingTyped')}</p>}
      <div className="actions form-actions">
        {independent && (
          <button type="button" className="button primary" disabled={batch.pending || !filled.length} onClick={() => save(true)}>{batch.pending ? t('saving') : t('propose.saveSubmit', { count: filled.length })}</button>
        )}
        <button type="button" className={`button ${independent ? 'outline' : 'primary'}`} disabled={batch.pending || !filled.length} onClick={() => save(false)}>
          {batch.pending && !independent ? t('saving') : t(independent ? 'propose.save' : 'propose.saveInitial', { count: filled.length })}
        </button>
        <button type="button" className="button outline" disabled={batch.pending} onClick={close}>{t('cancel')}</button>
      </div>
      <BatchResults batch={batch} at="propose-batch" />
    </div>
  );
}
