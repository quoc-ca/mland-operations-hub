import React, { useEffect, useState } from 'react';
import { Link, useLocation, useSearchParams } from 'react-router';
import { useT } from '../../../../shared/i18n/I18nProvider';
import { ErrorNotice, Loading, useResource } from '../../../../shared/ui/components';
import { useCatalogueApi } from '../../application/useCatalogueApi';
import { productCode, skuMatch } from '../../domain/productRules';
import { publishWithoutReview } from '../../application/publishNow';
import { useRole } from '../../../../shared/auth/useRole';
import { newCommandId } from '../../../../shared/api/http';
import { RowActions, awaitingReview } from './RowActions';
import { messages } from '../messages';
import { PageTop } from './ManagementLayout';
import { PageNotice } from './notices';
import { Pager } from './Pager';

/** T028: management list with search, category and publication filters (kept in the URL). */
export default function ProductListPage() {
  const t = useT(messages);
  const api = useCatalogueApi();
  const location = useLocation();
  const [params, setParams] = useSearchParams();
  const criteria = { q: params.get('q') || '', categoryId: params.get('categoryId') || '', published: params.get('published') || '' };
  const page = Number(params.get('page') || 0);
  const [draft, setDraft] = useState(criteria);
  useEffect(() => { setDraft(criteria); }, [params.toString()]); // eslint-disable-line react-hooks/exhaustive-deps

  const products = useResource(() => api.listProducts({ ...criteria, page: page || undefined }), [params.toString()]);
  const categories = useResource(() => api.listCategories(), []);

  // A Manager changes the state of a product right from the list: publish (a never-published product is approved on the way)
  // or hide. Staff keep the details page. A failure is shown on the row, with the details one click away.
  const isManager = useRole() === 'MANAGER';
  const [working, setWorking] = useState(null);
  const [failed, setFailed] = useState(null);
  const changeState = async (product, action) => {
    setWorking(product.id);
    setFailed(null);
    try {
      if (action === 'approvePublish') await publishWithoutReview(api, product);
      else await api.setPublication(newCommandId(), product.id, product.version, action === 'publish');
      products.reload();
    } catch {
      setFailed(product.id);
    } finally {
      setWorking(null);
    }
  };

  const apply = event => {
    event.preventDefault();
    const next = new URLSearchParams();
    Object.entries(draft).forEach(([key, value]) => { if (value.trim()) next.set(key, value.trim()); });
    setParams(next);
  };
  const goTo = target => {
    const next = new URLSearchParams(params);
    if (target > 0) next.set('page', String(target)); else next.delete('page');
    setParams(next);
  };
  const set = name => event => setDraft(current => ({ ...current, [name]: event.target.value }));
  const data = products.data;

  return (
    <>
      <PageTop eyebrow={t('nav.group')} title={t('list.title')}>
        <Link className="button primary" to="/catalogue-management/products/new">{t('nav.newProduct')}</Link>
      </PageTop>
      <PageNotice notice={location.state?.notice} />
      <form className="filter-bar" onSubmit={apply} role="search">
        <label>{t('public.search')}<input value={draft.q} onChange={set('q')} maxLength={200} /></label>
        <label>{t('public.category')}
          <select value={draft.categoryId} onChange={set('categoryId')}>
            <option value="">{t('public.allCategories')}</option>
            {(categories.data?.items || []).map(category => <option key={category.id} value={category.id}>{category.name}{category.active ? '' : ` (${t('category.inactive')})`}</option>)}
          </select>
        </label>
        <label>{t('list.publication')}
          <select value={draft.published} onChange={set('published')}>
            <option value="">{t('list.all')}</option>
            <option value="true">{t('status.published')}</option>
            <option value="false">{t('status.unpublished')}</option>
          </select>
        </label>
        <button className="button primary" type="submit">{t('public.apply')}</button>
      </form>
      {categories.error && !products.error && <ErrorNotice error={categories.error} messages={messages} onRetry={categories.reload} />}
      {products.loading && <Loading />}
      {!products.loading && products.error && (
        <div className="workspace-card">
          <ErrorNotice error={products.error} messages={messages} onRetry={() => { products.reload(); if (categories.error) categories.reload(); }} />
        </div>
      )}
      {!products.loading && !products.error && data && (data.items.length === 0
        ? <div className="empty-state">{t('list.empty')}</div>
        : <section className="workspace-card">
          <div className="data-table" role="table" aria-label={t('list.title')}>
            <div className={`data-row products head${isManager ? ' with-actions' : ''}`} role="row">
              <span role="columnheader">{t('field.name')}</span><span role="columnheader">{t('field.category')}</span>
              <span role="columnheader">{t('list.sizes')}</span><span role="columnheader">{t('list.state')}</span>
              {isManager && <span role="columnheader">{t('list.actions')}</span>}
            </div>
            {data.items.map(product => (
              <div className={`data-row products${isManager ? ' with-actions' : ''}`} role="row" key={product.id}>
                <span role="cell">
                  <Link className="text-link" to={`/catalogue-management/products/${encodeURIComponent(product.id)}`}>{product.name}</Link>
                  <br /><span className="muted small mono">{productCode(product.id)}</span>
                </span>
                <span role="cell">{product.category?.name || '—'}</span>
                <span role="cell"><VariantSizes variants={product.variants} keyword={criteria.q} /></span>
                <span role="cell"><ProductStateBadge product={product} />{awaitingReview(product) && <><br /><span className="muted small">{t('list.awaitingReview')}</span></>}</span>
                {isManager && (
                  <span role="cell" className="row-actions">
                    <RowActions product={product} busy={working === product.id} disabled={working !== null} failed={failed === product.id} onChange={changeState} />
                  </span>
                )}
              </div>
            ))}
          </div>
          <Pager page={page} pageSize={data.pageSize} total={data.totalElements} onChange={goTo} />
        </section>)}
    </>
  );
}

/** Sizes of a row; when the search was a SKU, the size it matched is marked with that SKU (FR-003). */
function VariantSizes({ variants, keyword }) {
  const t = useT(messages);
  const matched = skuMatch(keyword, variants);
  return (
    <>
      {(variants || []).map((variant, index) => (
        <React.Fragment key={variant.id}>
          {index > 0 && ', '}
          {variant === matched ? <mark className="size-match">{variant.size}</mark> : variant.size}
        </React.Fragment>
      ))}
      {matched && <><br /><span className="muted small">{t('list.skuMatch', { sku: matched.sku, size: matched.size })}</span></>}
    </>
  );
}

export function ProductStateBadge({ product }) {
  const t = useT(messages);
  if (product.deletedAt) return <span className="status">{t('status.deleted')}</span>;
  return <span className={product.published ? 'status ok' : 'status'}>{product.published ? t('status.published') : t('status.unpublished')}</span>;
}
