import React, { useState } from 'react';
import { useT } from '../../../shared/i18n/I18nProvider';
import { ErrorNotice } from '../../../shared/ui/components';
import { messages } from '../messages';

/** Label of a whole product or of one size, shown in the picker, the selection and the programme detail. */
export const productLabel = product => `${product.name} (#${product.id})`;
export const sizeLabel = (product, variant) => `${product.name} · size ${variant.size} · ${variant.sku}`;

/**
 * Product-scope target picker of the create form: the Manager searches products by name or SKU through 002's
 * management list and ticks a whole product or single sizes; only their IDs are sent, as before. Every size of a
 * product is listed, including unpublished or stopped ones, because loyalty keeps such targets valid (spec
 * clarification 2026-10-26). A chosen whole product already covers its sizes, so they cannot also be ticked.
 *
 * @param value { products: {id: label}, variants: {id: label} } — the current selection with its labels
 */
export function ProductTargetPicker({ api, value, onChange, errors = {} }) {
  const t = useT(messages);
  const [query, setQuery] = useState('');
  const [result, setResult] = useState(null);
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(false);

  const search = async (page = 0) => {
    setLoading(true);
    setError(null);
    try {
      setResult(await api.searchCatalogueProducts({ q: query.trim() || undefined, page }));
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
    onChange({ products, variants });
  };

  const chosen = [
    ...Object.entries(value.products).map(([id, label]) => ['products', id, label]),
    ...Object.entries(value.variants).map(([id, label]) => ['variants', id, label]),
  ];
  const page = result ? Number(result.page) : 0;
  const pages = result ? Math.ceil(Number(result.totalElements) / Number(result.pageSize)) : 0;

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

      {result && result.items.length === 0 && <p className="muted small">{t('picker.none')}</p>}
      {result && result.items.length > 0 && (
        <ul className="target-results" aria-label={t('picker.results')}>
          {result.items.map(product => {
            const whole = Boolean(value.products[product.id]);
            return (
              <li key={product.id}>
                <label className="check-row">
                  <input type="checkbox" checked={whole} onChange={() => toggle('products', product.id, productLabel(product))} />
                  <strong>{productLabel(product)}</strong> <span className="muted small">{t('picker.wholeProduct')}</span>
                </label>
                <div className="target-sizes">
                  {(product.variants || []).map(variant => (
                    <label key={variant.id} className="check-row">
                      <input type="checkbox" disabled={whole} checked={whole || Boolean(value.variants[variant.id])}
                        onChange={() => toggle('variants', variant.id, sizeLabel(product, variant))} />
                      {t('picker.size', { size: variant.size })} <span className="mono small">{variant.sku}</span>
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

function dropSizesOf(variants, productId, result) {
  const product = result?.items.find(item => item.id === productId);
  if (!product) return variants;
  const next = { ...variants };
  (product.variants || []).forEach(variant => { delete next[variant.id]; });
  return next;
}
