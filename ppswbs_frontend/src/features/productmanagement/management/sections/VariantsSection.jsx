import React, { useState } from 'react';
import { useFormat, useT } from '../../../../shared/i18n/I18nProvider';
import { ErrorNotice } from '../../../../shared/ui/components';
import { messages } from '../../messages';
import { isQuantity, serverFieldErrors } from '../../rules';
import { useProductCommand } from '../ProductDetailPage';

/**
 * T050 quantity and T064 sale status per size. Size and SKU are read-only; remaining, held and available are
 * shown apart. A quantity below what is held is refused by the backend (QUANTITY_BELOW_HELD); holds are never
 * cancelled from here. Stopping a size's sale is not hiding or deleting the product.
 */
export function VariantsSection({ product, ...context }) {
  const t = useT(messages);
  return (
    <section className="workspace-card" aria-labelledby="variants-title">
      <h2 id="variants-title">{t('section.variants')}</h2>
      <div className="data-table" role="table" aria-label={t('section.variants')}>
        <div className="data-row head variants" role="row">
          <span role="columnheader">{t('field.size')}</span><span role="columnheader">SKU</span>
          <span role="columnheader">{t('variant.remaining')}</span><span role="columnheader">{t('variant.held')}</span>
          <span role="columnheader">{t('variant.available')}</span><span role="columnheader">{t('variant.appliedPrice')}</span>
          <span role="columnheader">{t('variant.sale')}</span>
        </div>
        {(product.variants || []).map(variant => <VariantRow key={variant.id} variant={variant} {...context} />)}
      </div>
    </section>
  );
}

function VariantRow({ variant, api, reload, setNotice, deleted }) {
  const t = useT(messages);
  const format = useFormat();
  const [quantity, setQuantity] = useState(null);
  const [hint, setHint] = useState(null);
  const quantityCommand = useProductCommand({ reload, setNotice });
  const saleCommand = useProductCommand({ reload, setNotice });

  const saveQuantity = async event => {
    event.preventDefault();
    if (!isQuantity(quantity)) { setHint('validation.quantity'); return; }
    setHint(null);
    const outcome = await quantityCommand.run(key => api.setQuantity(key, variant.id, variant.version, quantity), 'variant.quantitySaved');
    if (outcome.ok) setQuantity(null);
  };
  const toggleSale = () => saleCommand.run(key => api.setSaleStatus(key, variant.id, variant.version, !variant.saleEnabled),
    variant.saleEnabled ? 'variant.saleStopped' : 'variant.saleResumed');

  const quantityError = serverFieldErrors(quantityCommand.error).quantity || (hint && t(hint));

  return (
    <>
      <div className="data-row variants" role="row">
        <span role="cell"><strong>{variant.size}</strong></span>
        <span role="cell" className="mono">{variant.sku}</span>
        <span role="cell">
          {quantity === null ? (
            <>{variant.remainingUnsold}{!deleted && <button type="button" className="text-button" onClick={() => setQuantity(variant.remainingUnsold)}>{t('edit')}</button>}</>
          ) : (
            <form className="inline-form" onSubmit={saveQuantity} noValidate>
              <input aria-label={t('variant.newQuantity', { size: variant.size })} inputMode="numeric" value={quantity} onChange={event => setQuantity(event.target.value)} aria-invalid={quantityError ? true : undefined} />
              <button type="submit" className="button primary small" disabled={quantityCommand.pending}>{t('save')}</button>
              <button type="button" className="button outline small" onClick={() => { setQuantity(null); setHint(null); quantityCommand.reset(); }}>{t('cancel')}</button>
            </form>
          )}
        </span>
        <span role="cell">{variant.validHeldQuantity}</span>
        <span role="cell">{variant.availableQuantity}</span>
        <span role="cell">{format.money(variant.appliedPrice)}</span>
        <span role="cell">
          <span className={variant.saleEnabled ? 'status ok' : 'status'}>{variant.saleEnabled ? t('variant.onSale') : t('variant.saleOff')}</span>
          {!deleted && <button type="button" className="text-button" disabled={saleCommand.pending} onClick={toggleSale}>{variant.saleEnabled ? t('variant.stopSale') : t('variant.resumeSale')}</button>}
        </span>
      </div>
      {(quantityError || quantityCommand.error || saleCommand.error) && (
        <div className="row-messages">
          {quantityError && <span className="field-error">{quantityError}</span>}
          {!serverFieldErrors(quantityCommand.error).quantity && <ErrorNotice error={quantityCommand.error} messages={messages} onRetry={quantityCommand.retry} onReload={reload} />}
          <ErrorNotice error={saleCommand.error} messages={messages} onRetry={saleCommand.retry} onReload={reload} />
        </div>
      )}
    </>
  );
}
