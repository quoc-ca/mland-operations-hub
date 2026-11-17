import React, { useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router';
import { useT } from '../../../shared/i18n/I18nProvider';
import { useAuth } from '../../../shared/auth/AuthProvider';
import { useRole } from '../../../shared/auth/useRole';
import { ErrorNotice, Loading, useResource } from '../../../shared/ui/components';
import { useReviewManagementApi } from '../api/useReviewManagementApi';
import { reviewManagementMessages } from '../messages';
import { formatStars } from '../rules';
import ReviewCard from './ReviewCard';

export default function PublicReviewListPage() {
  const t = useT(reviewManagementMessages);
  const api = useReviewManagementApi();
  const navigate = useNavigate();

  const [rating, setRating] = useState('');
  const [targetType, setTargetType] = useState('');
  const [sort, setSort] = useState('recent');

  // /reviews?packageId=145 lists the reviews of that workshop package (all of its sessions), e.g. from the package page.
  const [searchParams] = useSearchParams();
  const rawPackageId = searchParams.get('packageId');
  const packageId = rawPackageId && /^[0-9]+$/.test(rawPackageId) ? rawPackageId : undefined;

  // Who sees which way in: a Guest looks sources up by email or phone; a signed-in Member has their own list. The two are
  // not offered together, and nothing is offered to Staff/Manager (they moderate) or while the role is still unknown.
  const { status: authStatus } = useAuth();
  const role = useRole();
  const isGuest = authStatus === 'signed-out' || authStatus === 'unconfigured';
  const isMember = authStatus === 'signed-in' && role === 'MEMBER';

  const resource = useResource(
    () => api.listPublicReviews({
      rating: rating || undefined,
      targetType: targetType || undefined,
      sort: sort || undefined,
      packageId,
    }),
    [rating, targetType, sort, packageId]
  );

  const page = resource.data || {
    items: [],
    aggregate: { count: 0, averageRating: 0 },
  };

  // "Show more" follows the backend cursor and appends the next page. A new first page (another rating) starts over.
  const [more, setMore] = useState({ firstPage: null, items: [], nextCursor: undefined });
  const [loadingMore, setLoadingMore] = useState(false);
  const [moreError, setMoreError] = useState(null);
  const continuing = more.firstPage === resource.data;
  const shownItems = continuing ? [...page.items, ...more.items] : page.items;
  const nextCursor = continuing && more.nextCursor !== undefined ? more.nextCursor : page.nextCursor;

  async function loadMore() {
    if (!nextCursor || loadingMore) return;
    setLoadingMore(true);
    setMoreError(null);
    try {
      const next = await api.listPublicReviews({
        rating: rating || undefined,
        targetType: targetType || undefined,
        sort: sort || undefined,
        packageId,
        cursor: nextCursor,
      });
      setMore(current => ({
        firstPage: resource.data,
        items: [...(current.firstPage === resource.data ? current.items : []), ...(next.items || [])],
        nextCursor: next.nextCursor ?? null,
      }));
    } catch (failure) {
      setMoreError(failure);
    } finally {
      setLoadingMore(false);
    }
  }

  const avgRating = Number(page.aggregate?.averageRating || 0);
  const count = Number(page.aggregate?.count || 0);
  const total = Number(page.totalElements ?? count);
  // The package named on the reviews of the filtered list (the name is stored with each review).
  const packageName = packageId ? (page.items.find(item => item.packageName)?.packageName || `#${packageId}`) : null;

  return (
    <div className="review-container figma-review-page" data-node-id="521:734">
      <header className="page-header" data-node-id="539:1727">
        <h1 className="review-main-title" data-node-id="521:748">
          {packageName ? `${t('packageReviewsTitle')}: ${packageName}` : t('publicTitle')}
        </h1>
        <p className="muted review-main-subtitle" data-node-id="521:749">
          {t('publicSubtitle')} 100% đánh giá công khai từ khách hàng đã tham gia workshop hoặc mua trang sức tại xưởng.
        </p>
        {packageName && (
          <p>
            <Link to="/reviews">{t('showAllPackages')}</Link>
          </p>
        )}
      </header>

      <div className="review-aggregate-card" data-node-id="521:750">
        <div className="aggregate-score-box">
          <div className="aggregate-score-large" data-node-id="521:751">
            {avgRating > 0 ? avgRating.toFixed(1) : '0.0'}
          </div>
          <div className="aggregate-score-details">
            <span className="review-stars" style={{ fontSize: '1.25rem', color: '#f2a600' }} data-node-id="521:752">
              {formatStars(avgRating || 5)}
            </span>
            <span className="muted" style={{ fontSize: '11px', color: '#73757a' }} data-node-id="521:753">
              {count > 0 ? `Dựa trên ${count} đánh giá đã xác thực qua hệ thống` : `${count} ${t('review').toLowerCase()}`}
            </span>
          </div>
        </div>

        <div className="review-actions" style={{ margin: 0 }}>
          {isGuest && (
            <Link className="button primary rev-btn-lookup" to="/reviews/guest/lookup" data-node-id="521:754">
              {t('lookupTitle')}
            </Link>
          )}
          {isMember && (
            <Link className="button primary rev-btn-lookup" to="/reviews/eligible-sources" data-node-id="521:754">
              {t('eligibleTitle')}
            </Link>
          )}
        </div>
      </div>

      <div className="review-toolbar">
        <div className="toolbar-filters">
          <label>
            {t('sortLabel')}
            <select
              value={sort}
              onChange={e => setSort(e.target.value)}
              data-testid="filter-sort"
            >
              <option value="recent">{t('sortRecent')}</option>
              <option value="best_to_worse">{t('sortBest')}</option>
              <option value="worse_to_best">{t('sortWorse')}</option>
            </select>
          </label>

          <label>
            {t('filterTarget')}
            <select
              value={targetType}
              onChange={e => setTargetType(e.target.value)}
              data-testid="filter-target-type"
            >
              <option value="">{t('filterAllTargets')}</option>
              <option value="WORKSHOP_SESSION">{t('filterWorkshop')}</option>
              <option value="RETAIL_PRODUCT">{t('filterProduct')}</option>
            </select>
          </label>

          <label>
            {t('rating')}
            <select
              value={rating}
              onChange={e => setRating(e.target.value)}
              data-testid="filter-rating"
            >
              <option value="">{t('all')}</option>
              {[5, 4, 3, 2, 1].map(v => (
                <option key={v} value={v}>
                  {v} / 5
                </option>
              ))}
            </select>
          </label>
        </div>

        <span className="muted">
          {shownItems.length} / {total}
        </span>
      </div>

      {resource.loading && <Loading />}
      <ErrorNotice
        error={resource.error}
        messages={reviewManagementMessages}
        onReload={resource.reload}
      />

      {!resource.loading && !resource.error && shownItems.length === 0 && (
        <div className="empty-state">{t('noReviews')}</div>
      )}

      <div className="review-grid">
        {shownItems.map(review => (
          <ReviewCard
            key={review.id}
            review={review}
            onOpen={item => navigate(`/reviews/${encodeURIComponent(item.id)}`)}
          />
        ))}
      </div>

      {nextCursor && (
        <div className="review-actions" style={{ justifyContent: 'center', marginTop: '16px' }}>
          <button type="button" className="button outline" onClick={loadMore} disabled={loadingMore}>
            {loadingMore ? t('loadingMore') : t('loadMore')}
          </button>
        </div>
      )}
      <ErrorNotice error={moreError} messages={reviewManagementMessages} onReload={loadMore} />
    </div>
  );
}
