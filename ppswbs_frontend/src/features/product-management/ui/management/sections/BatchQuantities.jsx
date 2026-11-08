import React, { useState } from 'react';
import { useT } from '../../../../../shared/i18n/I18nProvider';
import { Field } from '../../../../../shared/ui/components';
import { ACTIONS } from '../../../application/useCatalogueCommand';
import { messages } from '../../messages';
import { isQuantity, LIMITS } from '../../../domain/productRules';
import { BatchResults, selectionOf, SelectCell, SIZES, useBatch } from './BatchPrices';
import { QuantityStepper } from '../QuantityStepper';

/**
 * Quantities of several sizes at once, next to the per-size edit: choose sizes (or a size range), then either set
 * them to one number or add one number to what remains (a restock), adjust any row, and save. Only sizes whose
 * number differs from the current one are sent, each with its own command (PUT variants/{id}/quantity), so a
 * failed size never stops the others. A number below what is held is refused here before anything is sent; the
 * backend checks it again (QUANTITY_BELOW_HELD). Quantities can exceed Number range, so they are compared as BigInt.
 */
export function BatchQuantities({ product, api, reload, setNotice, branch = null }) {
  const t = useT(messages);
  const batch = useBatch({ api, reload, setNotice });
  const [open, setOpen] = useState(false);
  const [selected, setSelected] = useState({});
  const [values, setValues] = useState({});
  const [errors, setErrors] = useState({});
  const [from, setFrom] = useState(String(LIMITS.minSize));
  const [to, setTo] = useState(String(LIMITS.maxSize));
  const [mode, setMode] = useState('add');
  const [amount, setAmount] = useState('');
  const [amountError, setAmountError] = useState(null);

  const rows = (product.variants || [])
    .map(variant => ({ variant }))
    .sort((a, b) => Number(a.variant.size) - Number(b.variant.size));
  if (rows.length < 2) return null;
  if (!open) {
    return <div className="batch-prices"><button type="button" className="button outline" onClick={() => setOpen(true)}>{t('qty.open')}</button></div>;
  }

  // US10: with a branch, the rows show and change the quantity at that branch (each size its own branch command).
  const stockOf = variant => (variant.branchStocks || []).find(item => item.locationId === branch);
  const remainingOf = variant => (branch ? (stockOf(variant)?.remainingUnsold ?? "0") : variant.remainingUnsold);
  const valueOf = ({ variant }) => values[variant.id] ?? String(remainingOf(variant));
  const changed = row => valueOf(row).trim() !== String(remainingOf(row.variant));
  // Holds are still per size (FE-08), so the size total after the change must stay at least the held quantity.
  const newTotalOf = (row, value) => BigInt(row.variant.remainingUnsold) - BigInt(remainingOf(row.variant)) + BigInt(value);
  const chosen = selectionOf(selected, rows);
  const toSave = chosen.filter(changed);
  const setAll = checked => setSelected(Object.fromEntries(rows.map(row => [row.variant.id, checked])));
  const chooseRange = () => {
    const low = Math.min(Number(from), Number(to));
    const high = Math.max(Number(from), Number(to));
    setSelected(Object.fromEntries(rows.map(row => [row.variant.id, Number(row.variant.size) >= low && Number(row.variant.size) <= high])));
  };
  const apply = () => {
    const typed = amount.trim();
    if (!isQuantity(typed)) { setAmountError(t('validation.quantity')); return; }
    setAmountError(null);
    setValues(current => ({
      ...current,
      ...Object.fromEntries(chosen.map(row => [row.variant.id, mode === 'add' ? String(BigInt(remainingOf(row.variant)) + BigInt(typed)) : typed])),
    }));
  };
  const close = () => { setOpen(false); setValues({}); setErrors({}); setSelected({}); setAmount(''); setAmountError(null); batch.clear(); };

  const save = async () => {
    setNotice(null); // a new action replaces the previous outcome, even when it is refused here (Session 2026-11-04)
    const found = {};
    toSave.forEach(row => {
      const value = valueOf(row).trim();
      if (!isQuantity(value)) found[row.variant.id] = t('validation.quantity');
      else if (newTotalOf(row, value) < BigInt(row.variant.validHeldQuantity || '0')) found[row.variant.id] = t('qty.belowHeld', { held: row.variant.validHeldQuantity });
    });
    setErrors(found);
    if (Object.keys(found).length) return;
    const results = await batch.run(toSave.map(row => ({
      id: row.variant.id, size: row.variant.size,
      send: key => (branch
        ? api.setBranchQuantity(key, row.variant.id, branch, stockOf(row.variant)?.version ?? 0, valueOf(row).trim())
        : api.setQuantity(key, row.variant.id, row.variant.version, valueOf(row).trim())),
    })), branch ? ACTIONS.branchQuantity : ACTIONS.quantity, { key: 'qty.savedN', at: 'qty-batch', section: 'section.variants' });
    // All saved: stay open with a success line under the button; the rows now show the saved numbers, so nothing is
    // left to save. Some failed: keep only those chosen, with what was typed.
    if (results.every(result => result.ok)) {
      setValues({});
      return;
    }
    const failed = new Set(results.filter(result => !result.ok).map(result => result.id));
    setValues(current => Object.fromEntries(Object.entries(current).filter(([id]) => failed.has(id))));
    setSelected(Object.fromEntries(rows.map(row => [row.variant.id, failed.has(row.variant.id)])));
  };

  return (
    <div className="batch-prices review-decision">
      <h3>{t('qty.title')}</h3>
      <p className="muted small">{t('qty.hint')}</p>
      <div className="size-tools">
        <label>{t('size.from')}
          <select value={from} onChange={event => setFrom(event.target.value)}>{SIZES.map(size => <option key={size} value={size}>{size}</option>)}</select>
        </label>
        <label>{t('size.to')}
          <select value={to} onChange={event => setTo(event.target.value)}>{SIZES.map(size => <option key={size} value={size}>{size}</option>)}</select>
        </label>
        <button type="button" className="button outline" onClick={chooseRange}>{t('propose.chooseRange')}</button>
      </div>
      <div className="size-bulk price-bulk">
        <label>{t('qty.mode')}
          <select value={mode} onChange={event => setMode(event.target.value)}>
            <option value="add">{t('qty.modeAdd')}</option>
            <option value="set">{t('qty.modeSet')}</option>
          </select>
        </label>
        <Field label={t('qty.amount')} error={amountError}><QuantityStepper label={t('qty.amount')} value={amount} invalid={Boolean(amountError)} onChange={setAmount} /></Field>
        <button type="button" className="button outline" disabled={!chosen.length || !amount.trim()} onClick={apply}>{t('bulk.applyTo', { count: chosen.length })}</button>
        <button type="button" className="button outline" onClick={() => { setValues({}); setErrors({}); }}>{t('qty.reset')}</button>
      </div>
      <div className="data-table" role="table" aria-label={t('qty.title')}>
        <div className="data-row head batch" role="row">
          <SelectCell checked={chosen.length === rows.length} label={t('bulk.selectAll')} onChange={setAll} />
          <span role="columnheader">{t('field.size')}</span><span role="columnheader">{t('variant.remaining')}</span>
          <span role="columnheader">{t('variant.held')}</span><span role="columnheader">{t('qty.new')}</span>
          <span role="columnheader">{t('bulk.diff')}</span>
        </div>
        {rows.map(row => (
          <div className={`data-row batch${changed(row) ? ' changed' : ''}`} role="row" key={row.variant.id}>
            <SelectCell checked={selected[row.variant.id] !== false} label={t('bulk.selectSize', { size: row.variant.size })}
              onChange={checked => setSelected(current => ({ ...current, [row.variant.id]: checked }))} />
            <span role="cell"><strong>{t('size', { size: row.variant.size })}</strong></span>
            <span role="cell">{remainingOf(row.variant)}</span>
            <span role="cell">{row.variant.validHeldQuantity}</span>
            <span role="cell">
              <QuantityStepper label={t('qty.newFor', { size: row.variant.size })} value={valueOf(row)} invalid={Boolean(errors[row.variant.id])}
                onChange={value => setValues(current => ({ ...current, [row.variant.id]: value }))} />
              {errors[row.variant.id] && <span className="field-error">{errors[row.variant.id]}</span>}
            </span>
            <span role="cell"><QuantityGap from={remainingOf(row.variant)} to={valueOf(row).trim()} /></span>
          </div>
        ))}
      </div>
      {!toSave.length && !batch.pending && !batch.results.length && <p className="muted small">{t('qty.nothingChanged')}</p>}
      <div className="actions form-actions">
        <button type="button" className="button primary" disabled={batch.pending || !toSave.length} onClick={save}>{batch.pending ? t('saving') : t('qty.save', { count: toSave.length })}</button>
        <button type="button" className="button outline" disabled={batch.pending} onClick={close}>{t('cancel')}</button>
      </div>
      <BatchResults batch={batch} at="qty-batch" />
    </div>
  );
}

function QuantityGap({ from, to }) {
  const t = useT(messages);
  if (!isQuantity(to) || !isQuantity(String(from))) return <span className="muted">—</span>;
  const gap = BigInt(to) - BigInt(from);
  if (gap === 0n) return <span className="muted small">{t('qty.same')}</span>;
  return <span className={`price-gap ${gap > 0n ? 'up' : 'down'}`}>{gap > 0n ? '+' : '−'}{String(gap > 0n ? gap : -gap)}</span>;
}
