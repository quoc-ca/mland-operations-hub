import React, { useState } from 'react';
import { useFormat, useT } from '../../../../../shared/i18n/I18nProvider';
import { ErrorNotice, useResource } from '../../../../../shared/ui/components';
import { ACTIONS } from '../../../application/useCatalogueCommand';
import { messages } from '../../messages';
import { isQuantity, serverFieldErrors } from '../../../domain/productRules';
import { useProductCommand } from '../ProductDetailPage';
import { BatchQuantities } from './BatchQuantities';
import { NoticeAt } from '../notices';
import { QuantityStepper } from '../QuantityStepper';
import { useRole } from '../../../../../shared/auth/useRole';

/**
 * T050 quantity and T064 sale status per size, and US10 quantity per branch (Session 2026-11-08). Size and SKU are
 * read-only; remaining, held and available are shown apart. A quantity below what is held is refused by the backend
 * (QUANTITY_BELOW_HELD); holds are never cancelled from here. Stopping a size's sale is not hiding or deleting the
 * product.
 *
 * A product sold at branches shows one tab per branch: the quantity of each size there, stopping/resuming sale at that
 * branch, and adding another branch. Buttons for a branch outside the account's scope are not shown (the branch list
 * is the account's own: every active branch for a Manager, the assigned ones for Staff); the backend checks again.
 * A product not yet at any branch (data from before US10) keeps the single quantity and offers to assign a branch.
 */
export function VariantsSection({ product, ...context }) {
  const t = useT(messages);
  const branches = product.branches || [];
  const branchList = useResource(() => context.api.listBranches(), []);
  const myBranches = (Array.isArray(branchList.data) ? branchList.data : []).filter(branch => branch.active !== false);
  const canEdit = id => myBranches.some(branch => String(branch.id) === String(id));
  const nameOf = branch => branch.name || myBranches.find(item => String(item.id) === branch.locationId)?.name || t('branch.unknown', { id: branch.locationId });
  const [selected, setSelected] = useState(null);
  const current = branches.find(branch => branch.locationId === selected) || branches[0] || null;
  const addable = myBranches.filter(branch => !branches.some(item => item.locationId === String(branch.id)));
  // FR-021 with FR-050 (T134): stopping a size acts at every branch, so Staff need every branch selling the product
  // (a product without a branch: at least one of their own). The backend refuses otherwise.
  const isManager = useRole() === 'MANAGER';
  const sizeSaleEditable = !context.deleted && (isManager
    || (branches.length ? branches.every(branch => canEdit(branch.locationId)) : myBranches.length > 0));

  return (
    <section className="workspace-card" aria-labelledby="variants-title">
      <h2 id="variants-title">{t('section.variants')}</h2>
      {branches.length === 0 && <p className="notice">{t('branch.legacyNotice')}</p>}
      {branches.length > 0 && (
        <div className="branch-tabs" role="tablist" aria-label={t('branch.title')}>
          {branches.map(branch => (
            <button key={branch.locationId} type="button" role="tab" aria-selected={current?.locationId === branch.locationId}
              className={current?.locationId === branch.locationId ? 'branch-tab on' : 'branch-tab'} onClick={() => setSelected(branch.locationId)}>
              {nameOf(branch)}{!branch.saleEnabled && <span className="status small">{t('branch.saleOff')}</span>}
            </button>
          ))}
        </div>
      )}
      {current && (
        <BranchBar product={product} branch={current} name={nameOf(current)} editable={canEdit(current.locationId) && !context.deleted} {...context} />
      )}
      {!context.deleted && (!current || canEdit(current.locationId)) && (
        <BatchQuantities product={product} branch={current?.locationId || null} {...context} />
      )}
      <div className="data-table" role="table" aria-label={t('section.variants')}>
        <div className="data-row head variants" role="row">
          <span role="columnheader">{t('field.size')}</span><span role="columnheader">SKU</span>
          <span role="columnheader">{current ? t('branch.quantityAt', { branch: nameOf(current) }) : t('variant.remaining')}</span>
          <span role="columnheader">{current ? t('branch.totalAll') : t('variant.held')}</span>
          <span role="columnheader">{current ? t('variant.held') : t('variant.available')}</span>
          <span role="columnheader">{t('variant.appliedPrice')}</span>
          <span role="columnheader">{t('variant.sale')}</span>
        </div>
        {(product.variants || []).map(variant => (
          <VariantRow key={variant.id} variant={variant} branch={current?.locationId || null}
            editable={!context.deleted && (!current || canEdit(current.locationId))} saleEditable={sizeSaleEditable} {...context} />
        ))}
      </div>
      {!context.deleted && addable.length > 0 && (
        <AddBranchPanel product={product} addable={addable} legacy={branches.length === 0} {...context} />
      )}
      {branchList.error && <ErrorNotice error={branchList.error} messages={messages} onRetry={branchList.reload} />}
    </section>
  );
}

/** Sale status of the product at one branch, with the stop/resume button. */
function BranchBar({ product, branch, name, editable, api, reload, setNotice }) {
  const t = useT(messages);
  const command = useProductCommand({ reload, setNotice });
  const at = `branch-${branch.locationId}`;
  // T142 (FR-026): send the state shown here; if someone changed it meanwhile the server answers 409 and the page reloads.
  const toggle = () => command.run(key => api.setBranchSale(key, product.id, branch.locationId, !branch.saleEnabled, branch.saleEnabled),
    { key: branch.saleEnabled ? 'branch.saleStopped' : 'branch.saleResumed', params: { branch: name }, at }, ACTIONS.branchSale);
  return (
    <div className="branch-bar">
      <span>{t('branch.saleAt', { branch: name })}: <span className={branch.saleEnabled ? 'status ok' : 'status'}>{branch.saleEnabled ? t('variant.onSale') : t('variant.saleOff')}</span></span>
      {!branch.active && <span className="muted small">{t('branch.inactive')}</span>}
      {editable && (
        <button type="button" className="text-button" disabled={command.pending} onClick={toggle}>
          {branch.saleEnabled ? t('branch.stopSale') : t('branch.resumeSale')}
        </button>
      )}
      <ErrorNotice error={command.error} messages={messages} onRetry={command.retry} onReload={reload} />
      <NoticeAt at={at} />
    </div>
  );
}

function VariantRow({ variant, branch, editable, saleEditable = editable, api, reload, setNotice, deleted }) {
  const t = useT(messages);
  const format = useFormat();
  const [quantity, setQuantity] = useState(null);
  const [hint, setHint] = useState(null);
  const quantityCommand = useProductCommand({ reload, setNotice });
  const saleCommand = useProductCommand({ reload, setNotice });
  const at = `variant-${variant.id}`;
  const stock = branch ? (variant.branchStocks || []).find(item => item.locationId === branch) : null;
  const shown = branch ? (stock?.remainingUnsold ?? '0') : variant.remainingUnsold;

  const saveQuantity = async event => {
    event.preventDefault();
    setNotice(null); // a new action replaces the previous outcome, even when it is refused here (Session 2026-11-04)
    if (!isQuantity(quantity)) { setHint('validation.quantity'); return; }
    setHint(null);
    const send = branch
      ? key => api.setBranchQuantity(key, variant.id, branch, stock.version, quantity)
      : key => api.setQuantity(key, variant.id, variant.version, quantity);
    const outcome = await quantityCommand.run(send,
      { key: 'variant.quantitySaved', params: { size: variant.size, from: shown, to: quantity }, at }, branch ? ACTIONS.branchQuantity : ACTIONS.quantity);
    if (outcome.ok) setQuantity(null);
  };
  const toggleSale = () => saleCommand.run(key => api.setSaleStatus(key, variant.id, variant.version, !variant.saleEnabled),
    { key: variant.saleEnabled ? 'variant.saleStopped' : 'variant.saleResumed', params: { size: variant.size }, at }, ACTIONS.saleStatus);

  const quantityError = serverFieldErrors(quantityCommand.error).quantity || (hint && t(hint));

  return (
    <>
      <div className="data-row variants" role="row">
        <span role="cell"><strong>{variant.size}</strong><br /><span className="muted small">{t('variant.id', { id: variant.id })}</span></span>
        <span role="cell" className="mono">{variant.sku}</span>
        <span role="cell">
          {quantity === null ? (
            <>{shown}{editable && (!branch || stock) && <button type="button" className="text-button" onClick={() => setQuantity(shown)}>{t('edit')}</button>}</>
          ) : (
            <form className="inline-form" onSubmit={saveQuantity} noValidate>
              <QuantityStepper label={t('variant.newQuantity', { size: variant.size })} value={quantity} onChange={setQuantity} invalid={Boolean(quantityError)} />
              <button type="submit" className="button primary small" disabled={quantityCommand.pending}>{t('save')}</button>
              <button type="button" className="button outline small" onClick={() => { setQuantity(null); setHint(null); quantityCommand.reset(); }}>{t('cancel')}</button>
            </form>
          )}
        </span>
        <span role="cell">{branch ? variant.remainingUnsold : variant.validHeldQuantity}</span>
        <span role="cell">{branch ? variant.validHeldQuantity : variant.availableQuantity}</span>
        <span role="cell">{format.money(variant.appliedPrice)}</span>
        <span role="cell">
          <span className={variant.saleEnabled ? 'status ok' : 'status'}>{variant.saleEnabled ? t('variant.onSale') : t('variant.saleOff')}</span>
          {!deleted && saleEditable && <button type="button" className="text-button" disabled={saleCommand.pending} onClick={toggleSale}>{variant.saleEnabled ? t('variant.stopSale') : t('variant.resumeSale')}</button>}
        </span>
      </div>
      {(quantityError || quantityCommand.error || saleCommand.error) && (
        <div className="row-messages">
          {quantityError && <span className="field-error">{quantityError}</span>}
          {!serverFieldErrors(quantityCommand.error).quantity && <ErrorNotice error={quantityCommand.error} messages={messages} onRetry={quantityCommand.retry} onReload={reload} />}
          <ErrorNotice error={saleCommand.error} messages={messages} onRetry={saleCommand.retry} onReload={reload} />
        </div>
      )}
      <NoticeAt at={at} />
    </>
  );
}

/**
 * US10 FR-049: start selling at another branch with a quantity per size. For a product not yet at any branch the
 * current quantities are filled in (they move to the first branch).
 */
function AddBranchPanel({ product, addable, legacy, api, reload, setNotice }) {
  const t = useT(messages);
  const [open, setOpen] = useState(false);
  const [locationId, setLocationId] = useState('');
  const [values, setValues] = useState({});
  const [hint, setHint] = useState(null);
  const command = useProductCommand({ reload, setNotice });
  const sizes = product.variants || [];
  const valueOf = variant => values[variant.id] ?? (legacy ? String(variant.remainingUnsold) : '0');

  if (!open) {
    return (
      <div className="batch-prices">
        <button type="button" className="button outline" onClick={() => { setOpen(true); setLocationId(String(addable[0].id)); }}>
          {t(legacy ? 'branch.assign' : 'branch.add')}
        </button>
      </div>
    );
  }
  const branchName = addable.find(branch => String(branch.id) === locationId)?.name || '';
  const submit = async event => {
    event.preventDefault();
    setNotice(null);
    if (!locationId) { setHint('branch.required'); return; }
    if (sizes.some(variant => !isQuantity(valueOf(variant).trim()))) { setHint('validation.quantity'); return; }
    setHint(null);
    const quantities = sizes.map(variant => ({ size: variant.size, quantity: valueOf(variant).trim() }));
    const outcome = await command.run(key => api.addBranch(key, product.id, product.version, locationId, quantities),
      { key: 'branch.added', params: { branch: branchName }, at: 'branch-add' }, ACTIONS.addBranch);
    if (outcome.ok) { setOpen(false); setValues({}); }
  };

  return (
    <form className="batch-prices review-decision" onSubmit={submit} noValidate>
      <h3>{t(legacy ? 'branch.assign' : 'branch.add')}</h3>
      {legacy && <p className="muted small">{t('branch.assignHint')}</p>}
      <label className="stepper-field">{t('branch.choose')}
        <select value={locationId} onChange={event => setLocationId(event.target.value)}>
          {addable.map(branch => <option key={branch.id} value={String(branch.id)}>{branch.name}</option>)}
        </select>
      </label>
      <div className="data-table" role="table" aria-label={t('branch.quantityAt', { branch: branchName })}>
        {sizes.map(variant => (
          <div className="data-row size-rows" role="row" key={variant.id} style={{ gridTemplateColumns: '120px minmax(150px,1fr)' }}>
            <span role="cell"><strong>{t('size', { size: variant.size })}</strong></span>
            <span role="cell">
              <QuantityStepper label={t('size.quantityAtFor', { size: variant.size, branch: branchName })} value={valueOf(variant)}
                onChange={value => setValues(current => ({ ...current, [variant.id]: value }))} />
            </span>
          </div>
        ))}
      </div>
      {hint && <p className="field-error">{t(hint)}</p>}
      <ErrorNotice error={command.error} messages={messages} onRetry={command.retry} onReload={reload} />
      <div className="actions form-actions">
        <button type="submit" className="button primary" disabled={command.pending}>{command.pending ? t('saving') : t('branch.addSubmit')}</button>
        <button type="button" className="button outline" disabled={command.pending} onClick={() => { setOpen(false); setValues({}); command.reset(); }}>{t('cancel')}</button>
      </div>
      <NoticeAt at="branch-add" />
    </form>
  );
}
