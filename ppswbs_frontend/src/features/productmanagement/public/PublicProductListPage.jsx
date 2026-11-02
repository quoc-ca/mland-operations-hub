import React, { useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router';
import { apiBaseUrl } from '../../../shared/api/http';
import { useFormat, useT } from '../../../shared/i18n/I18nProvider';
import { ErrorNotice, Loading, useResource } from '../../../shared/ui/components';
import { mediaUrl } from '../api/catalogueApi';
import { useCatalogueApi } from '../api/useCatalogueApi';
import { messages } from '../messages';
import { PriceRange } from './PriceRange';

const FILTERS = ['q', 'categoryId', 'minimumPrice', 'maximumPrice', 'canBuy', 'sort'];
const SORTS = ['', 'name-asc', 'price-asc', 'price-desc', 'newest'];

/** T071: public list. Search, filter, sort and page live in the URL so a link or Back keeps them. */
export default function PublicProductListPage() {
  const t = useT(messages);
  const api = useCatalogueApi();
  const [params, setParams] = useSearchParams();
  const criteria = Object.fromEntries(FILTERS.map(name => [name, params.get(name) || '']));
  const page = Number(params.get('page') || 0);
  const [draft, setDraft] = useState(criteria);
  // Back/Forward or a shared link changes the URL: show its criteria in the form again.
  useEffect(() => { setDraft(criteria); }, [params.toString()]); // eslint-disable-line react-hooks/exhaustive-deps

  const products = useResource(() => api.listPublicProducts({ ...criteria, page: page || undefined }), [params.toString()]);
  const categories = useResource(() => api.listPublicCategories(), []);

  const apply = event => {
    event.preventDefault();
    const next = new URLSearchParams();
    FILTERS.forEach(name => { if (draft[name]) next.set(name, draft[name].trim ? draft[name].trim() : draft[name]); });
    setParams(next);
  };
  const goTo = target => {
    const next = new URLSearchParams(params);
    if (target > 0) next.set('page', String(target)); else next.delete('page');
    setParams(next);
  };
  const set = name => event => setDraft(current => ({ ...current, [name]: event.target.type === 'checkbox' ? (event.target.checked ? 'true' : '') : event.target.value }));

  const data = products.data;
  const total = Number(data?.totalElements || 0);
  const lastPage = data ? Math.max(0, Math.ceil(total / (data.pageSize || 20)) - 1) : 0;

  return (
    <main className="container public-page">
      <p className="eyebrow">{t('public.eyebrow')}</p>
      <h1>{t('public.title')}</h1>
      <form className="filter-bar" onSubmit={apply} role="search">
        <label>{t('public.search')}<input value={draft.q} onChange={set('q')} maxLength={200} placeholder={t('public.searchPlaceholder')} /></label>
        <label>{t('public.category')}
          <select value={draft.categoryId} onChange={set('categoryId')}>
            <option value="">{t('public.allCategories')}</option>
            {(categories.data?.items || []).map(category => <option key={category.id} value={category.id}>{category.name}</option>)}
          </select>
        </label>
        <label>{t('public.minimumPrice')}<input inputMode="numeric" value={draft.minimumPrice} onChange={set('minimumPrice')} /></label>
        <label>{t('public.maximumPrice')}<input inputMode="numeric" value={draft.maximumPrice} onChange={set('maximumPrice')} /></label>
        <label>{t('public.sort')}
          <select value={draft.sort} onChange={set('sort')}>
            {SORTS.map(sort => <option key={sort || 'default'} value={sort}>{t(`public.sort.${sort || 'default'}`)}</option>)}
          </select>
        </label>
        <label className="check"><input type="checkbox" checked={draft.canBuy === 'true'} onChange={set('canBuy')} /> {t('public.onlyBuyable')}</label>
        <button className="button primary" type="submit">{t('public.apply')}</button>
      </form>

      {categories.error && !products.error && <ErrorNotice error={categories.error} messages={messages} onRetry={categories.reload} />}
      {products.loading && <Loading />}
      {!products.loading && products.error && (
        <div className="empty-state">
          <ErrorNotice error={products.error} messages={messages} onRetry={() => { products.reload(); if (categories.error) categories.reload(); }} />
        </div>
      )}
      {!products.loading && !products.error && data && (data.items.length === 0
        ? <div className="empty-state">{t('public.empty')}</div>
        : <>
          <p className="muted">{t('public.count', { count: total })}</p>
          <div className="catalogue-grid">{data.items.map(product => <ProductCard key={product.id} product={product} />)}</div>
          <nav className="pager" aria-label={t('pager.label')}>
            <button type="button" className="button outline" disabled={page <= 0} onClick={() => goTo(page - 1)}>{t('pager.previous')}</button>
            <span>{t('pager.page', { page: page + 1, pages: lastPage + 1 })}</span>
            <button type="button" className="button outline" disabled={page >= lastPage} onClick={() => goTo(page + 1)}>{t('pager.next')}</button>
          </nav>
        </>)}
    </main>
  );
}

function ProductCard({ product }) {
  const t = useT(messages);
  const format = useFormat();
  const image = [...(product.images || [])].sort((a, b) => a.position - b.position)[0];
  return (
    <Link className="ring-card" to={`/products/${encodeURIComponent(product.id)}`}>
      {image ? <img className="product-image" src={mediaUrl(apiBaseUrl, image.url)} alt={image.altText} /> : <div className="ring-visual silver" aria-hidden="true"><span /></div>}
      {product.category && <span className="badge">{product.category.name}</span>}
      <strong>{product.name}</strong>
      <PriceRange range={product.priceRange} format={format} />
      <span className={product.canBuy ? 'status ok' : 'status'}>{product.canBuy ? t('public.canBuy') : t('public.cannotBuy')}</span>
    </Link>
  );
}
