import React, { useEffect, useRef, useState } from 'react';
import { Link, useLocation, useSearchParams } from 'react-router';
import { useT } from '../../../../shared/i18n/I18nProvider';
import { ErrorNotice, Loading, useResource } from '../../../../shared/ui/components';
import { useCatalogueApi } from '../../application/useCatalogueApi';
import { productCode, skuMatch } from '../../domain/productRules';
import { publishWithoutReview } from '../../application/publishNow';
import { useRole } from '../../../../shared/auth/useRole';
import { newCommandId } from '../../../../shared/api/http';
import { RowActions, branchSaleAction } from './RowActions';
import { messages } from '../messages';
import { NoticeContext, PageNotice } from './notices';
import { Pager } from './Pager';
import { productOutOfScope } from './ProductDetailPage';
import { PendingPriceList } from './PendingPriceList';
import { ProductStateBadge } from './ProductStateBadge';

/**
 * FR-064/FR-065 (Session 2026-11-22): one tab per product state, with counts, plus the Manager's price tab. Each tab
 * lists exactly the products of its state; the counts follow the search, category and branch. Filters apply at once
 * (the search after a short pause or Enter); the tab and filters stay in the URL without adding history entries.
 */
// FR-065: the price tab is shown to Staff too, read-only, so they can follow the prices they sent.
export const LIST_TABS = ['', 'DRAFT', 'PENDING', 'REJECTED', 'APPROVED', 'PUBLISHED', 'HIDDEN', 'PRICE_PENDING'];
const COUNT_FIELD = { '': 'all', DRAFT: 'draft', PENDING: 'pending', REJECTED: 'rejected', APPROVED: 'approved', PUBLISHED: 'published', HIDDEN: 'hidden', PRICE_PENDING: 'pricePending' };
// Tabs that hold work for the viewer (Session 2026-11-22): highlighted while their count is above zero.
const ATTENTION = { MANAGER: ['PENDING', 'APPROVED', 'PRICE_PENDING'], STAFF: ['REJECTED', 'APPROVED'] };
// FR-065 sub-filters of the price tab and their count fields. The tab count is the waiting prices for every role
// (Session 2026-11-23); each sub-filter shows its own count. Rejected prices are for information: the old price stays.
const PRICE_SUBS = ['PRICE_PENDING', 'PRICE_DRAFT', 'PRICE_REJECTED'];
const MANAGER_PRICE_SUBS = ['PRICE_PENDING', 'PRICE_REJECTED'];
const PRICE_SUB_COUNT = { PRICE_PENDING: 'pricePending', PRICE_DRAFT: 'priceDraft', PRICE_REJECTED: 'priceRejected' };

/** Count and highlight of one tab for the viewer's role. */
export function tabBadge(key, counts, role) {
  if (!counts) return { count: undefined, attention: false };
  const count = counts[COUNT_FIELD[key]];
  return { count, attention: (ATTENTION[role] || []).includes(key) && Number(count) > 0 };
}
const SEARCH_PAUSE_MS = 300;
const LIST_PANEL = 'product-list-panel';
const tabId = key => `product-list-tab-${key || 'ALL'}`;
// URL value of the branch filter for "every branch" when a Staff member's default is their own branch.
const ALL_BRANCHES = 'all';

export default function ProductListPage() {
  const t = useT(messages);
  const api = useCatalogueApi();
  const location = useLocation();
  const [params, setParams] = useSearchParams();
  const role = useRole();
  const isManager = role === 'MANAGER';
  const tabs = LIST_TABS;
  const requestedTab = params.get('tab') || '';
  // FR-065 sub-filters of the price tab: Staff get waiting, unsent drafts and rejected prices; a Manager gets waiting and
  // rejected prices (Session 2026-11-23, to follow the rejections) but not the drafts. An unknown tab value shows "All".
  const priceSubs = isManager ? MANAGER_PRICE_SUBS : PRICE_SUBS;
  const tab = requestedTab === 'PRICE_DRAFT' && isManager ? 'PRICE_PENDING'
    : PRICE_SUBS.includes(requestedTab) ? requestedTab
    : tabs.includes(requestedTab) ? requestedTab : '';
  const topTab = PRICE_SUBS.includes(tab) ? 'PRICE_PENDING' : tab;
  const showPriceSub = topTab === 'PRICE_PENDING' && role !== null && role !== undefined;
  // US10 FR-051: filter by branch. Every active branch is offered (Staff see the products of every branch too).
  const branches = useResource(() => api.listSellingLocations(), []);
  // T182: a Staff member acts only on products sold at their branches (same rule as the details page and the backend).
  const myBranches = useResource(() => api.listBranches(), []);
  // Session 2026-11-23: a Staff member working at one branch opens the list on that branch, so tab counts and highlights
  // match their work; "Tất cả chi nhánh" (URL locationId=all) still shows every branch (FR-051). Without locationId in the
  // URL the default applies; "Xóa lọc" goes back to it.
  const staffBranch = role === 'STAFF' && Array.isArray(myBranches.data) && myBranches.data.length === 1
    ? String(myBranches.data[0].id) : '';
  // T231: without locationId in the URL the default depends on the role and the Staff member's branches, so nothing is
  // read until both are known (no first read of every branch that the Staff default then replaces).
  const waitingForDefault = params.get('locationId') === null && (role === null || role === undefined || (role === 'STAFF' && myBranches.loading));
  const rawLocation = params.get('locationId');
  const locationId = rawLocation === null ? staffBranch : rawLocation === ALL_BRANCHES ? '' : rawLocation;
  const filters = { q: params.get('q') || '', categoryId: params.get('categoryId') || '', locationId };
  const page = Number(params.get('page') || 0);

  const products = useResource(
    () => (waitingForDefault ? new Promise(() => {}) : api.listProducts({ ...filters, tab: tab || undefined, page: page || undefined })),
    [params.toString(), tab, locationId, waitingForDefault],
  );
  const counts = useResource(() => (waitingForDefault ? new Promise(() => {}) : api.countProductTabs(filters)),
    [filters.q, filters.categoryId, filters.locationId, waitingForDefault]);
  const categories = useResource(() => api.listAllCategories(), []);
  const [notice, setNotice] = useState(location.state?.notice || null);

  // FR-064: counts are read again when the browser tab comes back into view.
  const reloadCounts = useRef(counts.reload);
  reloadCounts.current = counts.reload;
  useEffect(() => {
    const onVisible = () => { if (document.visibilityState === 'visible') reloadCounts.current(); };
    document.addEventListener('visibilitychange', onVisible);
    return () => document.removeEventListener('visibilitychange', onVisible);
  }, []);
  const reloadAll = async () => { await Promise.all([products.reload(), counts.reload()]); };

  /** Replaces URL values (empty removes them), back to the first page, without a new history entry. */
  const update = changes => {
    const next = new URLSearchParams(params);
    Object.entries(changes).forEach(([key, value]) => { if (value && String(value).trim()) next.set(key, String(value).trim()); else next.delete(key); });
    next.delete('page');
    next.delete('published'); // the old publication filter is replaced by the tabs
    setParams(next, { replace: true });
  };
  const goTo = target => {
    const next = new URLSearchParams(params);
    if (target > 0) next.set('page', String(target)); else next.delete('page');
    setParams(next);
  };

  // Search box: applied after a short pause, or at once with Enter.
  const [query, setQuery] = useState(filters.q);
  useEffect(() => { setQuery(filters.q); }, [filters.q]);
  useEffect(() => {
    if (query.trim() === filters.q) return undefined;
    const timer = setTimeout(() => update({ q: query }), SEARCH_PAUSE_MS);
    return () => clearTimeout(timer);
  }, [query]); // eslint-disable-line react-hooks/exhaustive-deps
  const submitSearch = event => { event.preventDefault(); if (query.trim() !== filters.q) update({ q: query }); };
  const filtering = Boolean(filters.q || filters.categoryId || filters.locationId !== staffBranch);

  // State changes right from the list: a Manager publishes (a never-published product is approved on the way), hides and
  // shows again; a Staff member stops or resumes selling at the branch the list is filtered on, when it is theirs
  // (Session 2026-11-23, FR-049). A failure is shown on the row.
  const [working, setWorking] = useState(null);
  const [failed, setFailed] = useState(null);
  const changeState = async (product, action, detail) => {
    setWorking(product.id);
    setFailed(null);
    try {
      if (action === 'branchStop' || action === 'branchResume') {
        // FR-026: send the state shown on the row; a change made meanwhile answers 409 and the row shows the failure.
        await api.setBranchSale(newCommandId(), product.id, detail.locationId, !detail.saleEnabled, detail.saleEnabled);
      } else if (action === 'approvePublish') await publishWithoutReview(api, product);
      else await api.setPublication(newCommandId(), product.id, product.version, action === 'publish');
      await reloadAll();
    } catch (error) {
      // T220 (FR-026, FR-051): the row says why it failed. A 409 means the row is stale (the state changed meanwhile), so
      // the list is read again and the row shows the latest state instead of failing again on every retry.
      const stale = error?.status === 409;
      setFailed({ id: product.id, error, reloaded: stale });
      if (stale) await reloadAll().catch(() => {});
    } finally {
      setWorking(null);
    }
  };
  const data = products.data;

  // T232 (WAI-ARIA tabs): Left/Right move to the previous/next tab, Home/End to the first/last, and select it.
  const tabRefs = useRef({});
  const moveTab = event => {
    const index = tabs.indexOf(topTab);
    const target = { ArrowRight: index + 1, ArrowLeft: index - 1, Home: 0, End: tabs.length - 1 }[event.key];
    if (target === undefined) return;
    event.preventDefault();
    const key = tabs[(target + tabs.length) % tabs.length];
    update({ tab: key });
    tabRefs.current[key]?.focus();
  };

  return (
    <NoticeContext.Provider value={notice}>
      <PageNotice notice={notice} />
      <form className="filter-bar" onSubmit={submitSearch} role="search">
        <label>{t('public.search')}<input value={query} onChange={event => setQuery(event.target.value)} maxLength={200} /></label>
        <label>{t('public.category')}
          <select value={filters.categoryId} onChange={event => update({ categoryId: event.target.value })}>
            <option value="">{t('public.allCategories')}</option>
            {(categories.data?.items || []).map(category => <option key={category.id} value={category.id}>{category.name}{category.active ? '' : ` (${t('category.inactive')})`}</option>)}
          </select>
        </label>
        <label>{t('branch.choose')}
          <select value={filters.locationId} onChange={event => update({ locationId: event.target.value || (staffBranch ? ALL_BRANCHES : '') })}>
            <option value="">{t('branch.all')}</option>
            {(branches.data?.items || []).map(branch => <option key={branch.id} value={branch.id}>{branch.name}</option>)}
          </select>
        </label>
        {filtering && <button className="button outline" type="button" onClick={() => { setQuery(''); update({ q: '', categoryId: '', locationId: '' }); }}>{t('list.clearFilters')}</button>}
      </form>
      <div className="list-tabs" role="tablist" aria-label={t('list.tabs')} onKeyDown={moveTab}>
        {tabs.map(key => {
          const { count, attention } = tabBadge(key, counts.data, role);
          // T232: one tab stop for the row (the selected tab), the others are reached with the arrow keys.
          return (
            <button key={key || 'ALL'} type="button" role="tab" id={tabId(key)} aria-selected={key === topTab} aria-controls={LIST_PANEL}
              tabIndex={key === topTab ? 0 : -1} ref={element => { tabRefs.current[key] = element; }}
              className={`list-tab${key === topTab ? ' active' : ''}${attention ? ' attention' : ''}`} onClick={() => update({ tab: key })}>
              {t(`list.tab.${key || 'ALL'}`)}
              {count !== undefined && count !== null && <>{' '}<span className="tab-count">{count}</span></>}
            </button>
          );
        })}
      </div>
      <div role="tabpanel" id={LIST_PANEL} aria-labelledby={tabId(topTab)}>
      {showPriceSub && (
        <div className="price-sub" role="group" aria-label={t('list.priceSub')}>
          {priceSubs.map(key => {
            const count = counts.data?.[PRICE_SUB_COUNT[key]];
            // Work to do: waiting prices for a Manager, unsent drafts for Staff. Rejected prices are not highlighted.
            const attention = (isManager ? key === 'PRICE_PENDING' : key === 'PRICE_DRAFT') && Number(count) > 0;
            return (
              <button key={key} type="button" aria-pressed={key === tab} onClick={() => update({ tab: key })}
                className={`price-sub-chip${key === tab ? ' active' : ''}${attention ? ' attention' : ''}`}>
                {t(`list.priceSub.${key}`)}
                {count !== undefined && count !== null && <>{' '}<span className="tab-count">{count}</span></>}
              </button>
            );
          })}
        </div>
      )}
      {categories.error && !products.error && <ErrorNotice error={categories.error} messages={messages} onRetry={categories.reload} />}
      {/* T185 (FR-038, FR-051): the branch filter stays empty when the branches cannot be read; say so and offer a retry. */}
      {branches.error && !products.error && <ErrorNotice error={branches.error} messages={messages} onRetry={branches.reload} />}
      {products.loading && <Loading />}
      {!products.loading && products.error && (
        <div className="workspace-card">
          <ErrorNotice error={products.error} messages={messages} onRetry={() => { products.reload(); if (categories.error) categories.reload(); }} />
        </div>
      )}
      {!products.loading && !products.error && data && (data.items.length === 0
        ? <div className="empty-state">{t(filtering ? 'list.empty' : `list.emptyTab.${tab || 'ALL'}`)}</div>
        : <section className="workspace-card">
          {topTab === 'PRICE_PENDING'
            ? <PendingPriceList products={data.items} api={api} reload={reloadAll} setNotice={setNotice} canDecide={isManager} isManager={isManager}
                outOfScope={product => productOutOfScope(product, myBranches.data, isManager)} mode={tab === 'PRICE_PENDING' ? 'PENDING' : tab.replace('PRICE_', '')} />
            : <ProductTable products={data.items} keyword={filters.q} locationId={filters.locationId} isManager={isManager} myBranches={myBranches.data} working={working} failed={failed} onChange={changeState} />}
          <Pager page={page} pageSize={data.pageSize} total={data.totalElements} onChange={goTo} />
        </section>)}
      </div>
    </NoticeContext.Provider>
  );
}

function ProductTable({ products, keyword, locationId, isManager, myBranches, working, failed, onChange }) {
  const t = useT(messages);
  return (
    <div className="data-table" role="table" aria-label={t('list.title')}>
      <div className="data-row products head with-actions" role="row">
        <span role="columnheader">{t('field.name')}</span><span role="columnheader">{t('field.category')}</span>
        <span role="columnheader">{t('list.stock')}</span><span role="columnheader">{t('list.state')}</span>
        <span role="columnheader">{t('list.actions')}</span>
      </div>
      {products.map(product => (
        <div className="data-row products with-actions" role="row" key={product.id}>
          <span role="cell">
            <Link className="text-link" to={`/catalogue-management/products/${encodeURIComponent(product.id)}`}>{product.name}</Link>
            <br /><span className="muted small mono">{productCode(product.id)}</span>
          </span>
          <span role="cell">{product.category?.name || '—'}</span>
          <span role="cell"><ProductStock variants={product.variants} locationId={locationId} keyword={keyword} /></span>
          <span role="cell"><ProductStateBadge product={product} /><BranchSaleNote product={product} locationId={locationId} /></span>
          <span role="cell" className="row-actions">
            <RowActions product={product} isManager={isManager} outOfScope={productOutOfScope(product, myBranches, isManager)}
              branchAction={branchSaleAction(product, { isManager, locationId, myBranches })}
              busy={working === product.id} disabled={working !== null} failed={failed?.id === product.id ? failed : null} onChange={onChange} />
          </span>
        </div>
      ))}
    </div>
  );
}

const amount = value => {
  const number = Number(value);
  return Number.isFinite(number) ? number : 0;
};

/**
 * Session 2026-11-23: stock of a row, in place of the sizes. With a branch filter, the quantities of every size at that
 * branch; otherwise of every branch. The available quantity is added when holds make it smaller. When the search was a SKU,
 * the size it matched is still named with that SKU (FR-003).
 */
export function stockOf(variants, locationId) {
  let remaining = 0;
  let available = 0;
  (variants || []).forEach(variant => {
    if (locationId) {
      const stock = (variant.branchStocks || []).find(item => String(item.locationId) === String(locationId));
      if (!stock) return;
      remaining += amount(stock.remainingUnsold);
      available += amount(stock.availableQuantity ?? stock.remainingUnsold);
    } else {
      remaining += amount(variant.remainingUnsold);
      available += amount(variant.availableQuantity ?? variant.remainingUnsold);
    }
  });
  return { remaining, available };
}

function ProductStock({ variants, locationId, keyword }) {
  const t = useT(messages);
  const matched = skuMatch(keyword, variants);
  const { remaining, available } = stockOf(variants, locationId);
  return (
    <>
      <strong>{remaining}</strong>
      {available < remaining && <><br /><span className="muted small">{t('list.stockAvailable', { count: available })}</span></>}
      {matched && <><br /><span className="muted small">{t('list.skuMatch', { sku: matched.sku, size: matched.size })}</span></>}
    </>
  );
}

/** Session 2026-11-23 (FR-049): with a branch filter, says when selling is stopped at that branch. */
function BranchSaleNote({ product, locationId }) {
  const t = useT(messages);
  if (!locationId) return null;
  const branch = (product.branches || []).find(item => String(item.locationId) === String(locationId));
  if (!branch || branch.saleEnabled) return null;
  return <><br /><span className="muted small">{t('list.branchStopped')}</span></>;
}