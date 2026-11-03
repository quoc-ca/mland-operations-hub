import React, { useRef, useState } from 'react';
import { newCommandId } from '../../../../../shared/api/http';
import { useFormat, useT } from '../../../../../shared/i18n/I18nProvider';
import { Field, useErrorText } from '../../../../../shared/ui/components';
import { ACTIONS } from '../../../infrastructure/catalogueApi';
import { messages } from '../../messages';
import { checkReason, effectiveProposal, isAmount, LIMITS } from '../../../domain/productRules';

/**
 * Bulk price helpers shared by the initial review and the per-size price section: one amount applied to the chosen
 * sizes, a reset to the proposed amounts, and the gap between approved and proposed shown on each row.
 */
export function BulkPriceBar({ count, onApply, onReset }) {
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
      <Field label={t('bulk.price')} error={error}><input inputMode="numeric" value={amount} onChange={event => setAmount(event.target.value)} /></Field>
      <button type="button" className="button outline" disabled={count === 0 || !amount.trim()} onClick={apply}>{t('bulk.applyTo', { count })}</button>
      <button type="button" className="button outline" onClick={onReset}>{t('bulk.resetProposed')}</button>
    </div>
  );
}

export function PriceGap({ proposed, approved }) {
  const t = useT(messages);
  const format = useFormat();
  if (!isAmount(String(approved ?? '').trim()) || !isAmount(String(proposed ?? ''))) return <span className="muted">—</span>;
  const gap = Number(approved) - Number(proposed);
  if (gap === 0) return <span className="muted small">{t('bulk.same')}</span>;
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
 */
function useBatch({ api, reload, setNotice }) {
  const [state, setState] = useState({ pending: false, current: null, results: [] });
  const lostKeys = useRef({});
  const run = async (items, action) => {
    setNotice(null);
    const results = [];
    for (const item of items) {
      setState(current => ({ ...current, pending: true, current: item.size }));
      const known = lostKeys.current[item.id];
      const key = known || newCommandId();
      try {
        let done = false;
        if (known) {
          try { await api.getCommandReceipt(key, action); done = true; } catch (error) { if (error?.code !== 'COMMAND_RESULT_NOT_FOUND') throw error; }
        }
        if (!done) await item.send(key);
        delete lostKeys.current[item.id];
        results.push({ id: item.id, size: item.size, ok: true });
      } catch (error) {
        if (error?.status === 0) lostKeys.current[item.id] = key; else delete lostKeys.current[item.id];
        results.push({ id: item.id, size: item.size, ok: false, error });
      }
    }
    setState({ pending: false, current: null, results });
    if (results.some(result => result.ok)) await reload();
    return results;
  };
  const clear = () => setState(current => ({ ...current, results: [] }));
  return { ...state, run, clear };
}

function BatchResults({ batch }) {
  const t = useT(messages);
  const errorText = useErrorText(messages);
  if (batch.pending) return <p className="muted small" role="status">{t('batch.running', { size: batch.current })}</p>;
  if (!batch.results.length) return null;
  const ok = batch.results.filter(result => result.ok).length;
  return (
    <div className={`notice ${ok === batch.results.length ? 'success' : 'error'}`} role="status">
      <p>{t('batch.result', { ok, total: batch.results.length })}</p>
      <ul className="batch-results">
        {batch.results.map(result => (
          <li key={result.id}>{result.ok ? t('batch.okItem', { size: result.size }) : t('batch.failItem', { size: result.size, error: errorText(result.error) })}</li>
        ))}
      </ul>
    </div>
  );
}

const selectionOf = (selected, rows) => rows.filter(row => selected[row.variant.id] !== false);

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
  const approved = rows.filter(row => row.proposal?.state === 'APPROVED' && !row.proposal.applied);
  const context = { api, reload, setNotice };
  return (
    <>
      <PendingBatch rows={pending} {...context} />
      <SimpleBatch rows={drafts} labelKey="batch.submitAll" action={ACTIONS.submitPrice}
        send={({ proposal }) => key => api.submitPrice(key, proposal.id, proposal.version)} {...context} />
      <SimpleBatch rows={approved} labelKey="batch.applyAll" action={ACTIONS.applyPrice}
        send={({ proposal }) => key => api.applyPrice(key, proposal.id, proposal.version)} {...context} />
    </>
  );
}

function SimpleBatch({ rows, labelKey, action, send, api, reload, setNotice }) {
  const t = useT(messages);
  const batch = useBatch({ api, reload, setNotice });
  if (rows.length < 2 && !batch.results.length) return null;
  const go = () => batch.run(rows.map(row => ({ id: row.variant.id, size: row.variant.size, send: send(row) })), action);
  return (
    <div className="batch-prices">
      {rows.length >= 2 && (
        <button type="button" className="button primary" disabled={batch.pending} onClick={go}>
          {t(labelKey, { count: rows.length })}
        </button>
      )}
      <BatchResults batch={batch} />
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
    })), ACTIONS.decidePrice);
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
    })), ACTIONS.decidePrice);
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
          <div className="actions form-actions">
            {!rejecting ? (
              <>
                <button type="button" className="button primary" disabled={batch.pending || !chosen.length} onClick={approve}>{t('batch.approve', { count: chosen.length })}</button>
                <button type="button" className="button outline" disabled={batch.pending || !chosen.length} onClick={() => { setRejecting(true); batch.clear(); }}>{t('batch.reject', { count: chosen.length })}</button>
              </>
            ) : (
              <>
                <button type="button" className="button primary danger" disabled={batch.pending || !chosen.length} onClick={reject}>{t('batch.confirmReject', { count: chosen.length })}</button>
                <button type="button" className="button outline" disabled={batch.pending} onClick={() => { setRejecting(false); setErrors({}); }}>{t('cancel')}</button>
              </>
            )}
          </div>
        </>
      )}
      <BatchResults batch={batch} />
    </div>
  );
}
