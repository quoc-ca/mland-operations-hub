import React, { useEffect, useState } from 'react';
import { useT } from '../../../../shared/i18n/I18nProvider';
import { ErrorNotice } from '../../../../shared/ui/components';
import { messages } from '../messages';
import { branchSale, productLabel, sizeLabel } from '../../domain/promotionRules';


/**
 * Product-scope target picker of the create form: the Manager searches products by name or SKU through 002's
 * management list and ticks a whole product or single sizes; only their IDs are sent, as before. Every size of a
 * product is listed, including unpublished or stopped ones, because loyalty keeps such targets valid (spec
 * clarification 2026-10-26). A chosen whole product already covers its sizes, so they cannot also be ticked.
 *
 * With branches chosen for a voucher (Session 2026-11-09, US1.9) only products sold at one of them are suggested (one
 * branch: 002 filters the list; several: the page is filtered here), and sizes not sold at any chosen branch carry a
 * badge. Such a size can still be chosen — stock may be added later — and the selection then shows a warning.
 *
 * @param value { products: {id: label}, variants: {id: label} } — the current selection with its labels
 * @param branchIds branches the voucher is limited to; [] = every branch
 */
export function ProductTargetPicker({ api, value, onChange, errors = {}, branchIds = [] }) {
  const t = useT(messages);
  const [query, setQuery] = useState('');
  const [result, setResult] = useState(null);
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(false);
  // Products seen in any search, so a chosen size keeps its branch facts after the page changes.
  const [known, setKnown] = useState({});
  const branchKey = branchIds.join(',');
  // A new branch choice makes the shown page stale: search again with it.
  useEffect(() => { if (result) search(0); }, [branchKey]); // eslint-disable-line react-hooks/exhaustive-deps

  const search = async (page = 0) => {
    setLoading(true);
    setError(null);
    try {
      const locationId = branchIds.length === 1 ? branchIds[0] : undefined;
      const found = await api.searchCatalogueProducts({ q: query.trim() || undefined, page, locationId });
      setResult(found);
      setKnown(current => {
        const next = { ...current };
        (found?.items || []).forEach(product => (product.variants || []).forEach(variant => { next[variant.id] = product; }));
        return next;
      });
    } catch (failure) {
      setError(failure);
    } finally {
      setLoading(false);
    }
  };
  const onKey = event => { if (event.key === 'Enter') { event.preventDefault(); search(0); } };

  const toggle = (group, id, label) => {
    const next = { ...value[group] };
    if (next[id]) delete next[id];
    else next[id] = label;
    const products = group === 'products' ? next : value.products;
    // Choosing a whole product drops its single sizes: the product already covers them.
    const variants = group === 'variants' ? next : dropSizesOf(value.variants, id, result);
    // Keep the sizes of each whole product: a mix with single sizes is sent as sizes (productTargets).
    const sizesOf = { ...(value.sizesOf || {}) };
    if (group === 'products') {
      if (products[id]) sizesOf[id] = (result?.items.find(item => item.id === id)?.variants || []).map(variant => String(variant.id));
      else delete sizesOf[id];
    }
    onChange({ products, variants, sizesOf });
  };
  const mixed = Object.keys(value.products).length > 0 && Object.keys(value.variants).length > 0;

  const chosen = [
    ...Object.entries(value.products).map(([id, label]) => ['products', id, label]),
    ...Object.entries(value.variants).map(([id, label]) => ['variants', id, label]),
  ];
  const page = result ? Number(result.page) : 0;
  const pages = result ? Math.ceil(Number(result.totalElements) / Number(result.pageSize)) : 0;
  const limited = branchIds.length > 0;
  const shown = (result?.items || []).filter(product => branchSale(product, branchIds).atBranch);
  const offBranchChosen = limited && Object.keys(value.variants).some(id => known[id] && branchSale(known[id], branchIds).offSizes.has(String(id)));

  return (
    <div className="target-picker">
      <div className="target-search">
        <label className="field">
          <span>{t('picker.search')}</span>
          <input value={query} onChange={event => setQuery(event.target.value)} onKeyDown={onKey} placeholder={t('picker.searchHint')} />
        </label>
        <button type="button" className="button outline" onClick={() => search(0)} disabled={loading}>{loading ? t('picker.searching') : t('picker.find')}</button>
      </div>
      <ErrorNotice error={error} messages={messages} onRetry={() => search(page)} />

      {limited && <p className="muted small">{t('picker.branchFilter')}</p>}
      {result && shown.length === 0 && <p className="muted small">{t('picker.none')}</p>}
      {result && shown.length > 0 && (
        <ul className="target-results" aria-label={t('picker.results')}>
          {shown.map(product => {
            const whole = Boolean(value.products[product.id]);
            const state = productState(product);
            const deleted = state === 'deleted';
            const offSizes = branchSale(product, branchIds).offSizes;
            return (
              <li key={product.id}>
                <label className="check-row">
                  <input type="checkbox" checked={whole} disabled={deleted && !whole} onChange={() => toggle('products', product.id, productLabel(product))} />
                  <strong>{productLabel(product)}</strong> <span className="muted small">{t('picker.wholeProduct')}</span>
                  {state && <span className={`target-state ${state}`}>{t(`picker.state.${state}`)}</span>}
                </label>
                <div className="target-sizes">
                  {(product.variants || []).map(variant => (
                    <label key={variant.id} className="check-row">
                      <input type="checkbox" disabled={whole || (deleted && !value.variants[variant.id])} checked={whole || Boolean(value.variants[variant.id])}
                        onChange={() => toggle('variants', variant.id, sizeLabel(product, variant))} />
                      {t('picker.size', { size: variant.size })} <span className="mono small">{variant.sku}</span>
                      {!deleted && variant.saleEnabled === false && <span className="target-state stopped">{t('picker.state.stopped')}</span>}
                      {!deleted && limited && offSizes.has(String(variant.id)) && <span className="target-state stopped">{t('picker.state.offBranch')}</span>}
                    </label>
                  ))}
                </div>
              </li>
            );
          })}
        </ul>
      )}
      {pages > 1 && (
        <div className="actions">
          <button type="button" className="button outline" disabled={loading || page === 0} onClick={() => search(page - 1)}>{t('picker.previous')}</button>
          <span className="muted small">{t('picker.page', { page: page + 1, pages })}</span>
          <button type="button" className="button outline" disabled={loading || page + 1 >= pages} onClick={() => search(page + 1)}>{t('picker.next')}</button>
        </div>
      )}

      <h3 className="picker-title">{t('picker.chosen', { count: chosen.length })}</h3>
      {mixed && <p className="muted small" role="status">{t('picker.mixedHint')}</p>}
      {offBranchChosen && <p className="field-error" role="status">{t('picker.offBranchHint')}</p>}
      {chosen.length === 0
        ? <p className="muted small">{t('picker.nothingChosen')}</p>
        : (
          <ul className="target-chosen">
            {chosen.map(([group, id, label]) => (
              <li key={`${group}-${id}`}>
                <span>{label}</span>
                <button type="button" className="text-button" onClick={() => toggle(group, id, label)} aria-label={t('picker.remove', { label })}>{t('picker.removeShort')}</button>
              </li>
            ))}
          </ul>
        )}
      {errors.productIds && <p className="field-error">{errors.productIds}</p>}
      {errors.variantIds && <p className="field-error">{errors.variantIds}</p>}
    </div>
  );
}

/**
 * Why a product found here may not be on sale (from 002's management fields), or null when it is public. A product
 * that was never published is "not public yet"; one published before and now unpublished is "hidden"; a soft-deleted
 * one is refused by the backend, so it is shown but cannot be ticked. A missing field says nothing.
 */
export function productState(product) {
  if (product.deletedAt) return 'deleted';
  if (product.published !== false) return null;
  return product.firstPublishedAt ? 'hidden' : 'draft';
}

function dropSizesOf(variants, productId, result) {
  const product = result?.items.find(item => item.id === productId);
  if (!product) return variants;
  const next = { ...variants };
  (product.variants || []).forEach(variant => { delete next[variant.id]; });
  return next;
}
