import React, { useRef, useState } from 'react';
import { useT } from '../../../../../shared/i18n/I18nProvider';
import { ErrorNotice } from '../../../../../shared/ui/components';
import { ACTIONS } from '../../../application/useCatalogueCommand';
import { ALL_SIZES, sendNewSizePrices } from '../../../application/newSizePrices';
import { isAmount, isQuantity } from '../../../domain/productRules';
import { messages } from '../../messages';
import { useProductCommand } from '../ProductDetailPage';
import { NoticeAt } from '../notices';
import { QuantityStepper } from '../QuantityStepper';
import { ApplyNowToggle } from './BatchPrices';

/**
 * FR-069 (Session 2026-11-23): choose sizes 5–29 to add. A size the product already has (`existing`) only takes a
 * quantity at the branch; a new size also takes a proposed price (the price is shared by every branch).
 * `value` maps a chosen size to { quantity, price }.
 */
// A Manager's price is the selling price (Session 2026-11-08 naming), Staff propose one.
export function NewSizePicker({ candidates, value, onChange, branchName, errors = {}, manager = false }) {
  const t = useT(messages);
  const sizeList = candidates.map(candidate => candidate.size);
  const [from, setFrom] = useState(sizeList[0] || '');
  const [to, setTo] = useState(sizeList[sizeList.length - 1] || '');
  const [bulk, setBulk] = useState({ price: '', quantity: '' });
  // A newly chosen size starts from the shared values typed in the bulk row (price only matters for a new size).
  const fresh = () => ({ quantity: bulk.quantity.trim() || '0', price: bulk.price.trim() });
  const withSizes = (current, sizes) => {
    const next = { ...current };
    for (const size of sizes) if (!next[size]) next[size] = fresh();
    return next;
  };
  const toggle = size => onChange(current => {
    if (!current[size]) return withSizes(current, [size]);
    const next = { ...current };
    delete next[size];
    return next;
  });
  const chooseRange = () => {
    const low = Math.min(Number(from), Number(to));
    const high = Math.max(Number(from), Number(to));
    onChange(current => withSizes(current, sizeList.filter(size => Number(size) >= low && Number(size) <= high)));
  };
  const applyToAll = () => onChange(current => Object.fromEntries(Object.entries(current).map(([size, entry]) => {
    const existing = candidates.find(candidate => candidate.size === size)?.existing;
    return [size, {
      ...entry,
      ...(bulk.quantity.trim() ? { quantity: bulk.quantity.trim() } : {}),
      ...(bulk.price.trim() && !existing ? { price: bulk.price.trim() } : {}),
    }];
  })));
  const set = (size, field, entry) => onChange(current => ({ ...current, [size]: { ...current[size], [field]: entry } }));
  const chosen = candidates.filter(candidate => value[candidate.size]);
  const anyNew = chosen.some(candidate => !candidate.existing);
  const priceTitle = t(manager ? 'size.bulkSellingPrice' : 'bulk.proposedPrice');
  return (
    <div className="new-size-picker">
      <div className="add-size-grid" role="group" aria-label={t('size.addChoose')}>
        {candidates.map(candidate => (
          <label key={candidate.size} className={value[candidate.size] ? "add-size-chip on" : "add-size-chip"}>
            <input type="checkbox" checked={Boolean(value[candidate.size])} onChange={() => toggle(candidate.size)} />
            {candidate.size}{candidate.existing && <span className="muted small"> {t('size.onProduct')}</span>}
          </label>
        ))}
      </div>
      <div className="size-tools">
        <label>{t('size.from')}
          <select value={from} onChange={event => setFrom(event.target.value)}>
            {sizeList.map(size => <option key={size} value={size}>{size}</option>)}
          </select>
        </label>
        <label>{t('size.to')}
          <select value={to} onChange={event => setTo(event.target.value)}>
            {sizeList.map(size => <option key={size} value={size}>{size}</option>)}
          </select>
        </label>
        <button type="button" className="button outline" onClick={chooseRange}>{t('propose.chooseRange')}</button>
        <button type="button" className="button outline" disabled={chosen.length === candidates.length} onClick={() => onChange(current => withSizes(current, sizeList))}>{t('size.all')}</button>
        <button type="button" className="button outline" disabled={chosen.length === 0} onClick={() => onChange(() => ({}))}>{t('size.none')}</button>
      </div>
      {chosen.length > 0 && (
        <div className="size-bulk">
          <span className="muted small">{t(anyNew && chosen.length > 1 && chosen.some(candidate => candidate.existing) ? 'size.applyHintNewOnly' : 'size.applyHint', { count: chosen.length })}</span>
          {anyNew && <label>{priceTitle}<input inputMode="numeric" value={bulk.price} onChange={event => setBulk(current => ({ ...current, price: event.target.value }))} /></label>}
          <div className="stepper-field"><span>{t('size.bulkQuantity')}</span>
            <QuantityStepper label={t('size.bulkQuantity')} value={bulk.quantity} onChange={entry => setBulk(current => ({ ...current, quantity: entry }))} />
          </div>
          <button type="button" className="button outline" disabled={!bulk.quantity.trim() && !(anyNew && bulk.price.trim())} onClick={applyToAll}>{t('size.apply')}</button>
        </div>
      )}
      {chosen.length > 0 && (
        <div className="data-table" role="table" aria-label={t('size.addChosen')}>
          {chosen.map(candidate => (
            <div className="data-row size-rows" role="row" key={candidate.size} style={{ gridTemplateColumns: '120px minmax(150px,1fr) minmax(150px,1fr)' }}>
              <span role="cell"><strong>{t('size', { size: candidate.size })}</strong>{!candidate.existing && <><br /><span className="muted small">{t('size.newSize')}</span></>}</span>
              <span role="cell">
                <QuantityStepper label={t('size.quantityAtFor', { size: candidate.size, branch: branchName })} value={value[candidate.size].quantity}
                  onChange={entry => set(candidate.size, 'quantity', entry)} invalid={Boolean(errors[`${candidate.size}.quantity`])} />
              </span>
              <span role="cell">
                {candidate.existing ? <span className="muted small">{t('size.sharedPrice')}</span> : (
                  <label className="stepper-field">{t(manager ? 'field.sellingPrice' : 'field.proposedPrice')}
                    <input inputMode="numeric" aria-label={t(manager ? 'size.sellingPriceFor' : 'size.priceFor', { size: candidate.size })} value={value[candidate.size].price}
                      aria-invalid={Boolean(errors[`${candidate.size}.price`])} onChange={event => set(candidate.size, 'price', event.target.value)} />
                  </label>
                )}
              </span>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

/** Local check of the chosen sizes; returns { errors, hint } (empty when valid). */
export function checkNewSizes(candidates, value) {
  const errors = {};
  for (const candidate of candidates) {
    const entry = value[candidate.size];
    if (!entry) continue;
    if (!isQuantity(String(entry.quantity).trim())) errors[`${candidate.size}.quantity`] = true;
    if (!candidate.existing && !isAmount(String(entry.price).trim())) errors[`${candidate.size}.price`] = true;
  }
  return { errors, hint: hintOf(errors) };
}

function hintOf(errors) {
  const keys = Object.keys(errors);
  return keys.some(key => key.endsWith('.price')) ? 'validation.amount' : keys.length ? 'validation.quantity' : null;
}

/**
 * checkNewSizes while typing: before the first submit only a quantity or price already typed shows its mark, so
 * ticking a size does not flag its empty price at once. `live(before, after)` checks a change; `submit(value)` checks
 * all and from then on every change checks all; `reset()` starts again.
 */
export function useNewSizeCheck(candidates) {
  const touched = useRef(new Set());
  const submitted = useRef(false);
  const live = (before, after) => {
    Object.entries(after).forEach(([size, entry]) => {
      const previous = before[size];
      if (!previous || !entry) return;
      ['quantity', 'price'].forEach(field => { if (entry[field] !== previous[field]) touched.current.add(`${size}.${field}`); });
    });
    const result = checkNewSizes(candidates, after);
    if (submitted.current) return result;
    const errors = Object.fromEntries(Object.entries(result.errors).filter(([key]) => touched.current.has(key)));
    return { errors, hint: hintOf(errors) };
  };
  const submit = value => { submitted.current = true; return checkNewSizes(candidates, value); };
  const reset = () => { touched.current = new Set(); submitted.current = false; };
  return { live, submit, reset };
}

/**
 * After the add command: before the first publication the new prices are in the initial review draft (FR-011); after
 * it, Staff submit each new price and a Manager approves (and applies) it. The page reloads and says what was done.
 */
export async function finishNewSizes({ api, product, sizes, isManager, applyNow, reload, setNotice, branch, at }) {
  const list = sizes.join(', ');
  if (!sizes.length) return;
  if (!product.firstPublishedAt) {
    setNotice({ key: 'size.addedReview', params: { sizes: list, branch }, at });
    return;
  }
  const { done, failed } = await sendNewSizePrices(api, product.id, sizes, { manager: isManager, applyNow });
  await reload();
  if (failed.length) {
    setNotice({ key: 'size.pricesIncomplete', params: { sizes: list, failed: failed.map(item => item.size).join(', ') }, at, tone: 'error' });
    return;
  }
  const key = !isManager ? 'size.addedPricesSent' : applyNow ? 'size.addedPricesApplied' : 'size.addedPricesApproved';
  setNotice({ key, params: { sizes: done.join(', '), branch }, at });
}

/** FR-069: "Thêm size" for a branch already selling the product. */
export function AddSizesPanel({ product, branch, branchName, isManager, api, reload, setNotice }) {
  const t = useT(messages);
  const [open, setOpen] = useState(false);
  const [value, setValue] = useState({});
  const [applyNow, setApplyNow] = useState(true);
  const [check, setCheck] = useState({ errors: {}, hint: null });
  const command = useProductCommand({ reload, setNotice });
  const at = `sizes-add-${branch}`;
  const sizes = product.variants || [];
  const sold = new Set(sizes.filter(variant => (variant.branchStocks || []).some(stock => stock.locationId === branch)).map(variant => variant.size));
  const onProduct = new Set(sizes.map(variant => variant.size));
  const candidates = ALL_SIZES.filter(size => !sold.has(size)).map(size => ({ size, existing: onProduct.has(size) }));
  const newSizeCheck = useNewSizeCheck(candidates);
  if (!candidates.length) return null;
  const close = () => { setOpen(false); setValue({}); setCheck({ errors: {}, hint: null }); newSizeCheck.reset(); command.reset(); };
  // The picker hands an updater; the check reads the value it leads to.
  const change = update => {
    const next = typeof update === 'function' ? update(value) : update;
    setCheck(newSizeCheck.live(value, next));
    setValue(next);
  };

  if (!open) {
    return (
      <div className="batch-prices">
        <button type="button" className="button outline" onClick={() => setOpen(true)}>{t('size.add')}</button>
        <NoticeAt at={at} />
      </div>
    );
  }
  const chosen = candidates.filter(candidate => value[candidate.size]);
  const anyNew = chosen.some(candidate => !candidate.existing);
  const submit = async event => {
    event.preventDefault();
    setNotice(null);
    if (!chosen.length) { setCheck({ errors: {}, hint: 'size.addNone' }); return; }
    const result = newSizeCheck.submit(value);
    setCheck(result);
    if (result.hint) return;
    const lines = chosen.map(candidate => candidate.existing
      ? { size: candidate.size, quantity: String(value[candidate.size].quantity).trim() }
      : { size: candidate.size, quantity: String(value[candidate.size].quantity).trim(), proposedPrice: String(value[candidate.size].price).trim() });
    const newSizes = chosen.filter(candidate => !candidate.existing).map(candidate => candidate.size);
    const outcome = await command.run(key => api.addBranchSizes(key, product.id, branch, product.version, lines),
      { key: 'size.added', params: { sizes: chosen.map(candidate => candidate.size).join(', '), branch: branchName }, at }, ACTIONS.addSizes);
    if (!outcome.ok) return;
    close();
    await finishNewSizes({ api, product, sizes: newSizes, isManager, applyNow, reload, setNotice, branch: branchName, at });
  };

  return (
    <form className="batch-prices review-decision" onSubmit={submit} noValidate aria-label={t('size.addAt', { branch: branchName })}>
      <h3>{t('size.addAt', { branch: branchName })}</h3>
      <p className="muted small">{t(product.firstPublishedAt ? (isManager ? 'size.addHintManager' : 'size.addHintStaff') : 'size.addHintReview')}</p>
      <NewSizePicker candidates={candidates} value={value} onChange={change} branchName={branchName} errors={check.errors} manager={isManager} />
      {isManager && anyNew && product.firstPublishedAt && <ApplyNowToggle checked={applyNow} onChange={setApplyNow} />}
      {check.hint && <p className="field-error">{t(check.hint)}</p>}
      <ErrorNotice error={command.error} messages={messages} onRetry={command.retry} onReload={reload} />
      <div className="actions form-actions">
        <button type="submit" className="button primary" disabled={command.pending}>{command.pending ? t('saving') : t('size.addSubmit')}</button>
        <button type="button" className="button outline" disabled={command.pending} onClick={close}>{t('cancel')}</button>
      </div>
    </form>
  );
}
