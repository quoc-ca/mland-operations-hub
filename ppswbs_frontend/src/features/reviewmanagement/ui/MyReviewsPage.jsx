import React, { useMemo, useState } from 'react';
import { Link, useLocation, useNavigate, useSearchParams } from 'react-router';
import { useT } from '../../../shared/i18n/I18nProvider';
import { ErrorNotice, Loading, SuccessNotice, useResource } from '../../../shared/ui/components';
import { useReviewManagementApi } from '../api/useReviewManagementApi';
import { reviewManagementMessages } from '../messages';
import { formatDateTime } from '../rules';
import ReviewCard from './ReviewCard';
import SourceHeader, { isRetail } from './SourceHeader';

const sourceKey = source => `${source.sourceType}:${source.sourceReference}`;
const time = value => new Date(value || 0).getTime() || 0;

/**
 * "Đánh giá từ khách hàng" for a signed-in Member (session 2026-11-23): two tabs over the Member's own sources from
 * GET /reviews/eligible-sources. "Chưa đánh giá" lists what can still be reviewed; "Đã đánh giá" lists the Member's
 * reviews with the Sắp xếp / Loại trải nghiệm / Số sao filters. A retail line is reviewed together with the rest of its
 * order; a workshop ticket opens the single review form.
 */
export default function MyReviewsPage() {
  const t = useT(reviewManagementMessages);
  const api = useReviewManagementApi();
  const navigate = useNavigate();
  const location = useLocation();
  const [params, setParams] = useSearchParams();
  const tab = params.get('tab') === 'reviewed' ? 'reviewed' : 'pending';
  const setTab = next => setParams(next === 'pending' ? {} : { tab: next }, { replace: true });

  const [sort, setSort] = useState('recent');
  const [targetType, setTargetType] = useState('');
  const [rating, setRating] = useState('');

  const resource = useResource(() => api.listEligibleSources(), []);
  const sources = useMemo(() => resource.data?.items || resource.data?.sources || [], [resource.data]);

  const pending = useMemo(() => sources
    .filter(source => !source.alreadyReviewed && !source.reviewId && source.eligible !== false)
    .filter(source => !targetType || source.targetType === targetType)
    .sort((a, b) => time(b.completedAt) - time(a.completedAt)), [sources, targetType]);

  const allReviewed = useMemo(() => sources.filter(source => source.review), [sources]);
  const reviewed = useMemo(() => {
    const list = allReviewed
      .filter(source => !targetType || source.targetType === targetType)
      .filter(source => !rating || Number(source.review.rating) === Number(rating));
    const newest = (a, b) => time(b.review.createdAt) - time(a.review.createdAt);
    if (sort === 'best_to_worse') return list.sort((a, b) => b.review.rating - a.review.rating || newest(a, b));
    if (sort === 'worse_to_best') return list.sort((a, b) => a.review.rating - b.review.rating || newest(a, b));
    return list.sort(newest);
  }, [allReviewed, targetType, rating, sort]);

  const pendingCount = sources.filter(source => !source.alreadyReviewed && !source.reviewId && source.eligible !== false).length;

  const startReview = source => {
    if (isRetail(source) && source.orderId) navigate(`/reviews/orders/${encodeURIComponent(source.orderId)}`);
    else navigate('/reviews/new', { state: { source } });
  };

  return (
    <div className="review-container review-theme review-mine-page">
      <header className="page-header">
        <h1>{t('publicTitle')}</h1>
      </header>
      <SuccessNotice>{location.state?.notice}</SuccessNotice>

      <div className="review-tabs" role="tablist">
        <button type="button" role="tab" aria-selected={tab === 'pending'} className={tab === 'pending' ? 'active' : ''} onClick={() => setTab('pending')}>
          {t('tabPending')}{pendingCount ? <span className="review-tab-count">{pendingCount}</span> : null}
        </button>
        <button type="button" role="tab" aria-selected={tab === 'reviewed'} className={tab === 'reviewed' ? 'active' : ''} onClick={() => setTab('reviewed')}>
          {t('tabReviewed')}{allReviewed.length ? <span className="review-tab-count">{allReviewed.length}</span> : null}
        </button>
      </div>

      <div className="review-toolbar">
        <div className="toolbar-filters">
          {tab === 'reviewed' && (
            <label>
              {t('sortLabel')}
              <select value={sort} onChange={e => setSort(e.target.value)} data-testid="filter-sort">
                <option value="recent">{t('sortRecent')}</option>
                <option value="best_to_worse">{t('sortBest')}</option>
                <option value="worse_to_best">{t('sortWorse')}</option>
              </select>
            </label>
          )}
          <label>
            {t('filterTarget')}
            <select value={targetType} onChange={e => setTargetType(e.target.value)} data-testid="filter-target-type">
              <option value="">{t('filterAllTargets')}</option>
              <option value="WORKSHOP_SESSION">{t('filterWorkshop')}</option>
              <option value="RETAIL_PRODUCT">{t('filterProduct')}</option>
            </select>
          </label>
          {tab === 'reviewed' && (
            <label>
              {t('rating')}
              <select value={rating} onChange={e => setRating(e.target.value)} data-testid="filter-rating">
                <option value="">{t('all')}</option>
                {[5, 4, 3, 2, 1].map(v => (
                  <option key={v} value={v}>{'★'.repeat(v)} ({v})</option>
                ))}
              </select>
            </label>
          )}
        </div>
      </div>

      {resource.loading && <Loading />}
      <ErrorNotice error={resource.error} messages={reviewManagementMessages} onReload={resource.reload} />

      {!resource.loading && !resource.error && tab === 'pending' && (
        pending.length === 0
          ? <div className="empty-state">{t('noPending')}</div>
          : (
            <ul className="review-pending-list">
              {pending.map(source => (
                <li key={sourceKey(source)} className="review-pending-item">
                  <SourceHeader source={source} linkProduct />
                  {source.completedAt && <span className="muted small">{t('completedOn')} {formatDateTime(source.completedAt)}</span>}
                  <button type="button" className="button primary" onClick={() => startReview(source)}>
                    {t('reviewNow')}
                  </button>
                </li>
              ))}
            </ul>
          )
      )}

      {!resource.loading && !resource.error && tab === 'reviewed' && (
        reviewed.length === 0
          ? <div className="empty-state">{allReviewed.length ? t('noReviewedMatch') : t('noReviewed')}</div>
          : (
            <div className="review-mine-list">
              {reviewed.map(source => (
                <section key={sourceKey(source)} className="review-mine-item">
                  <SourceHeader source={source} linkProduct />
                  {source.review.status && source.review.status !== 'PUBLIC' && (
                    <p className="muted small">{t('hiddenNotice')}</p>
                  )}
                  <ReviewCard review={source.review} onOpen={item => navigate(`/reviews/${encodeURIComponent(item.id)}`, { state: { source } })} />
                  {source.review.canEdit && (
                    <Link className="button outline small" to={`/reviews/${encodeURIComponent(source.review.id)}/edit`} state={{ source }}>
                      {t('editReview')}
                    </Link>
                  )}
                </section>
              ))}
            </div>
          )
      )}
    </div>
  );
}
