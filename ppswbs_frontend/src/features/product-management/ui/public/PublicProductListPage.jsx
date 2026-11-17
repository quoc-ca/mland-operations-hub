import React, { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router';
import { useT } from '../../../../shared/i18n/I18nProvider';
import { ErrorNotice, Loading, useResource } from '../../../../shared/ui/components';
import { useCatalogueApi } from '../../application/useCatalogueApi';
import { messages } from '../messages';
import { PublicProductGrid } from './PublicProductGrid';

const FILTERS = ['q', 'categoryId', 'minimumPrice', 'maximumPrice', 'canBuy', 'sort'];
const SORTS = ['', 'name-asc', 'price-asc', 'price-desc', 'newest'];

/**
 * 02 · Product Catalogue (/products)
 * Follows Figma Page 01 · Public & Guest (Node 521:328 Laptop & 521:423 Phone)
 */
export default function PublicProductListPage() {
  const t = useT(messages);
  const api = useCatalogueApi();
  const [params, setParams] = useSearchParams();
  const criteria = Object.fromEntries(FILTERS.map(name => [name, params.get(name) || '']));
  const page = Number(params.get('page') || 0);
  const [draft, setDraft] = useState(criteria);

  useEffect(() => {
    setDraft(criteria);
  }, [params.toString()]); // eslint-disable-line react-hooks/exhaustive-deps

  const products = useResource(() => api.listPublicProducts({ ...criteria, page: page || undefined }), [params.toString()]);
  const categories = useResource(() => api.listAllPublicCategories(), []);

  const apply = event => {
    if (event) event.preventDefault();
    const next = new URLSearchParams();
    FILTERS.forEach(name => {
      if (draft[name]) next.set(name, draft[name].trim ? draft[name].trim() : draft[name]);
    });
    setParams(next);
  };

  const selectCategory = (catId) => {
    const nextDraft = { ...draft, categoryId: catId };
    setDraft(nextDraft);
    const next = new URLSearchParams();
    FILTERS.forEach(name => {
      const val = nextDraft[name];
      if (val) next.set(name, val.trim ? val.trim() : val);
    });
    setParams(next);
  };

  const goTo = target => {
    const next = new URLSearchParams(params);
    if (target > 0) next.set('page', String(target));
    else next.delete('page');
    setParams(next);
  };

  const set = name => event => setDraft(current => ({
    ...current,
    [name]: event.target.type === 'checkbox' ? (event.target.checked ? 'true' : '') : event.target.value,
  }));

  const data = products.data;
  const total = Number(data?.totalElements || 0);
  const lastPage = data ? Math.max(0, Math.ceil(total / (data.pageSize || 20)) - 1) : 0;
  const catItems = categories.data?.items || [];

  return (
    <main className="container public-page pm-theme pm-public pm-catalogue-page" data-node-id="521:328">
      {/* Section Header Banner (Node 539:1690) */}
      <div className="pm-catalogue-banner" data-node-id="539:1690" data-name="Section_Header_Banner">
        <h1 className="pm-catalogue-title" data-node-id="521:343">
          TRANG SỨC CHẾ TÁC THỦ CÔNG
        </h1>
        <p className="pm-catalogue-subtitle" data-node-id="521:344">
          Tuyển tập các mẫu nhẫn bạc 925, nhẫn đính đá và nhẫn cặp hoàn thiện tinh xảo.
        </p>
      </div>

      {/* Category Pills Navigation (Node 539:1691 / 539:1692) */}
      <div className="pm-catalogue-filter-section" data-node-id="539:1691" data-name="Section_Filter_Search">
        <div className="pm-category-chips-row" data-node-id="539:1692" role="group" aria-label="Lọc theo danh mục">
          <button
            type="button"
            className={`pm-category-chip ${!draft.categoryId ? 'active' : ''}`}
            onClick={() => selectCategory('')}
            data-node-id="521:345"
          >
            Tất cả {total > 0 ? `(${total})` : ''}
          </button>
          {catItems.map(category => (
            <button
              key={category.id}
              type="button"
              className={`pm-category-chip ${draft.categoryId === String(category.id) ? 'active' : ''}`}
              onClick={() => selectCategory(String(category.id))}
            >
              {category.name}
            </button>
          ))}
        </div>

        {/* Filter Form Bar (Search, sort, price, canBuy) */}
        <form className="filter-bar pm-catalogue-filter-bar" onSubmit={apply} role="search">
          <label>
            {t('public.search')}
            <input
              value={draft.q}
              onChange={set('q')}
              maxLength={200}
              placeholder="Tìm kiếm theo tên, SKU..."
              data-node-id="521:356"
            />
          </label>
          <label>
            {t('public.category')}
            <select value={draft.categoryId} onChange={set('categoryId')}>
              <option value="">{t('public.allCategories')}</option>
              {catItems.map(category => (
                <option key={category.id} value={category.id}>{category.name}</option>
              ))}
            </select>
          </label>
          <label>
            {t('public.sort')}
            <select value={draft.sort} onChange={set('sort')}>
              {SORTS.map(sort => (
                <option key={sort || 'default'} value={sort}>{t(`public.sort.${sort || 'default'}`)}</option>
              ))}
            </select>
          </label>
          <label>
            {t('public.minimumPrice')}
            <input inputMode="numeric" value={draft.minimumPrice} onChange={set('minimumPrice')} />
          </label>
          <label>
            {t('public.maximumPrice')}
            <input inputMode="numeric" value={draft.maximumPrice} onChange={set('maximumPrice')} />
          </label>
          <label className="check">
            <input type="checkbox" checked={draft.canBuy === 'true'} onChange={set('canBuy')} />
            {t('public.onlyBuyable')}
          </label>
          <button className="button primary pm-btn-apply" type="submit">
            {t('public.apply')}
          </button>
        </form>
      </div>

      {categories.error && !products.error && (
        <ErrorNotice error={categories.error} messages={messages} onRetry={categories.reload} />
      )}
      {products.loading && <Loading />}
      {!products.loading && products.error && (
        <div className="empty-state">
          <ErrorNotice
            error={products.error}
            messages={messages}
            onRetry={() => {
              products.reload();
              if (categories.error) categories.reload();
            }}
          />
        </div>
      )}
      {!products.loading && !products.error && data && (
        data.items.length === 0 ? (
          <div className="empty-state">{t('public.empty')}</div>
        ) : (
          <div className="pm-catalogue-grid-container" data-node-id="539:1693" data-name="Section_Product_Grid">
            <p className="muted pm-count-label">{t('public.count', { count: total })}</p>
            <PublicProductGrid products={data.items} />

            {/* Pagination (Node 539:1696) */}
            <nav className="pager pm-catalogue-pager" aria-label={t('pager.label')} data-node-id="539:1696">
              <button
                type="button"
                className="button outline"
                disabled={page <= 0}
                onClick={() => goTo(page - 1)}
              >
                {t('pager.previous')}
              </button>
              <span className="pm-pager-info" data-node-id="521:411">
                {t('pager.page', { page: page + 1, pages: lastPage + 1 })}
              </span>
              <button
                type="button"
                className="button outline"
                disabled={page >= lastPage}
                onClick={() => goTo(page + 1)}
              >
                {t('pager.next')}
              </button>
            </nav>
          </div>
        )
      )}
    </main>
  );
}
