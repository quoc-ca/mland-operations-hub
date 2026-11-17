import React, { useState } from 'react';
import { useT } from '../../../../shared/i18n/I18nProvider';
import { LIMITS } from '../../domain/productRules';
import { messages } from '../messages';
import { QuantityStepper } from './QuantityStepper';

const ALL_SIZES = Array.from({ length: LIMITS.maxSize - LIMITS.minSize + 1 }, (_, index) => String(LIMITS.minSize + index));
const sorted = list => [...list].sort((a, b) => Number(a.size) - Number(b.size));

/**
 * Size entry of the create form: sizes 5–29 are toggled as chips (or a whole range at once), one proposed price and
 * quantity can be applied to every chosen size, then any row is adjusted on its own. The value is the same variants
 * list the form sends ({ size, proposedPrice, quantity }), kept in size order, so validation keys
 * variants[index].field still point at the row shown at that index.
 *
 * With `branches` (US10) there is one quantity column per chosen branch, kept in `variant.quantities[branchId]`
 * (missing = 0); the common quantity is then applied to every branch.
 */
export function SizePicker({ variants, onChange, errorOf, disabled, priceLabel, branches }) {
  const t = useT(messages);
  const priceTitle = priceLabel || t('field.proposedPrice');
  const perBranch = Array.isArray(branches) && branches.length > 0;
  const [from, setFrom] = useState(String(LIMITS.minSize));
  const [to, setTo] = useState(String(LIMITS.maxSize));
  const [bulk, setBulk] = useState({ proposedPrice: '', quantity: '' });
  const chosen = new Set(variants.map(variant => variant.size));

  const bulkQuantities = () => Object.fromEntries((branches || []).map(branch => [String(branch.id), bulk.quantity.trim() || '0']));
  // Every change is computed from the latest list (onChange receives an updater), so quick consecutive clicks add up.
  const withSizes = (current, sizes) => sorted([
    ...current,
    ...sizes.filter(size => !current.some(variant => variant.size === size))
      .map(size => ({ size, proposedPrice: bulk.proposedPrice.trim(), quantity: bulk.quantity.trim() || '0', quantities: bulkQuantities() })),
  ]);
  const toggle = size => onChange(current => (current.some(variant => variant.size === size) ? current.filter(variant => variant.size !== size) : withSizes(current, [size])));
  const addRange = () => {
    const low = Math.min(Number(from), Number(to));
    const high = Math.max(Number(from), Number(to));
    onChange(current => withSizes(current, ALL_SIZES.filter(size => Number(size) >= low && Number(size) <= high)));
  };
  const applyToAll = () => onChange(current => current.map(variant => ({
    ...variant,
    ...(bulk.proposedPrice.trim() ? { proposedPrice: bulk.proposedPrice.trim() } : {}),
    ...(bulk.quantity.trim() ? { quantity: bulk.quantity.trim(), quantities: { ...(variant.quantities || {}), ...bulkQuantities() } } : {}),
  })));
  const setRowValue = (index, name, value) => onChange(current => current.map((variant, i) => (i === index ? { ...variant, [name]: value } : variant)));
  const setRow = (index, name) => event => setRowValue(index, name, event.target.value);
  const setBranchValue = (index, branchId, value) => onChange(current => current.map((variant, i) => (
    i === index ? { ...variant, quantities: { ...(variant.quantities || {}), [branchId]: value } } : variant)));

  const quantityColumns = perBranch ? branches.map(branch => ({ id: String(branch.id), title: t('branch.quantityAt', { branch: branch.name }) }))
    : [{ id: null, title: t('field.quantity') }];
  const columns = { gridTemplateColumns: `120px minmax(140px,1fr) repeat(${quantityColumns.length}, minmax(150px,1fr)) 110px` };

  return (
    <div className="size-picker">
      <div className="size-chips" role="group" aria-label={t('size.choose')}>
        {ALL_SIZES.map(size => (
          <button key={size} type="button" className={chosen.has(size) ? 'size-chip on' : 'size-chip'} aria-pressed={chosen.has(size)}
            disabled={disabled} onClick={() => toggle(size)}>{size}</button>
        ))}
      </div>
      <div className="size-tools">
        <label>{t('size.from')}
          <select value={from} onChange={event => setFrom(event.target.value)} disabled={disabled}>
            {ALL_SIZES.map(size => <option key={size} value={size}>{size}</option>)}
          </select>
        </label>
        <label>{t('size.to')}
          <select value={to} onChange={event => setTo(event.target.value)} disabled={disabled}>
            {ALL_SIZES.map(size => <option key={size} value={size}>{size}</option>)}
          </select>
        </label>
        <button type="button" className="button outline" disabled={disabled} onClick={addRange}>{t('size.addRange')}</button>
        <button type="button" className="button outline" disabled={disabled || variants.length === ALL_SIZES.length} onClick={() => onChange(current => withSizes(current, ALL_SIZES))}>{t('size.all')}</button>
        <button type="button" className="button outline" disabled={disabled || variants.length === 0} onClick={() => onChange(() => [])}>{t('size.none')}</button>
      </div>

      {variants.length > 0 && (
        <>
          <div className="size-bulk">
            <span className="muted small">{t(perBranch ? 'size.applyHintBranches' : 'size.applyHint', { count: variants.length, branches: (branches || []).length })}</span>
            <label>{priceTitle}<input inputMode="numeric" value={bulk.proposedPrice} disabled={disabled} onChange={event => setBulk(current => ({ ...current, proposedPrice: event.target.value }))} /></label>
            <div className="stepper-field"><span>{t('field.quantity')}</span>
              <QuantityStepper label={t('field.quantity')} value={bulk.quantity} disabled={disabled} onChange={value => setBulk(current => ({ ...current, quantity: value }))} />
            </div>
            <button type="button" className="button outline" disabled={disabled || (!bulk.proposedPrice.trim() && !bulk.quantity.trim())} onClick={applyToAll}>{t('size.apply')}</button>
          </div>
          <div className="data-table size-table" role="table" aria-label={t('create.sizes')}>
            <div className="data-row head size-rows" role="row" style={columns}>
              <span role="columnheader">{t('field.size')}</span><span role="columnheader">{priceTitle}</span>
              {quantityColumns.map(column => <span role="columnheader" key={column.id ?? 'all'}>{column.title}</span>)}
              <span role="columnheader" aria-label={t('create.removeSize')} />
            </div>
            {variants.map((variant, index) => {
              const priceError = errorOf(`variants[${index}].proposedPrice`);
              const sizeError = errorOf(`variants[${index}].size`);
              return (
                <div className="data-row size-rows" role="row" key={variant.size} style={columns}>
                  <span role="cell"><strong>{t('size', { size: variant.size })}</strong>{sizeError && <span className="field-error">{sizeError}</span>}</span>
                  <span role="cell">
                    <input inputMode="numeric" aria-label={t('size.priceFor', { size: variant.size })} value={variant.proposedPrice} disabled={disabled}
                      aria-invalid={priceError ? true : undefined} onChange={setRow(index, 'proposedPrice')} />
                    {priceError && <span className="field-error">{priceError}</span>}
                  </span>
                  {quantityColumns.map(column => {
                    const error = errorOf(column.id ? `variants[${index}].quantities.${column.id}` : `variants[${index}].quantity`);
                    const label = column.id ? t('size.quantityAtFor', { size: variant.size, branch: branches.find(b => String(b.id) === column.id)?.name })
                      : t('size.quantityFor', { size: variant.size });
                    return (
                      <span role="cell" key={column.id ?? 'all'}>
                        <QuantityStepper label={label} value={column.id ? (variant.quantities?.[column.id] ?? '0') : variant.quantity} disabled={disabled}
                          invalid={Boolean(error)} onChange={value => (column.id ? setBranchValue(index, column.id, value) : setRowValue(index, 'quantity', value))} />
                        {error && <span className="field-error">{error}</span>}
                      </span>
                    );
                  })}
                  <span role="cell">
                    <button type="button" className="text-button" disabled={disabled} aria-label={t('size.removeOne', { size: variant.size })} onClick={() => toggle(variant.size)}>{t('create.removeSize')}</button>
                  </span>
                </div>
              );
            })}
          </div>
        </>
      )}
    </div>
  );
}
