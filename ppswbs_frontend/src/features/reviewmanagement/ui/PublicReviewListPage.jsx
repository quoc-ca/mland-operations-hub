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
    () => api.listPublicReviews({ rating: rating || undefined, packageId }),
    [rating, packageId]
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
      const next = await api.listPublicReviews({ rating: rating || undefined, packageId, cursor: nextCursor });
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
    <div className="review-container">
      <header className="page-header">
        <h1>{packageName ? `${t('packageReviewsTitle')}: ${packageName}` : t('publicTitle')}</h1>
        <p className="muted">{t('publicSubtitle')}</p>
        {packageName && (
          <p>
            <Link to="/reviews">{t('showAllPackages')}</Link>
          </p>
        )}
      </header>

      <div className="review-aggregate-card">
        <div className="aggregate-score-box">
          <div className="aggregate-score-large">
            {avgRating > 0 ? avgRating.toFixed(1) : '0.0'}
          </div>
          <div className="aggregate-score-details">
            <span className="review-stars" style={{ fontSize: '1.25rem' }}>
              {formatStars(avgRating || 5)}
            </span>
            <span className="muted" style={{ fontSize: '0.9rem' }}>
              {count} {t('review').toLowerCase()}
            </span>
          </div>
        </div>

        <div className="review-actions" style={{ margin: 0 }}>
          {isGuest && (
            <Link className="button outline" to="/reviews/guest/lookup">
              {t('lookupTitle')}
            </Link>
          )}
          {isMember && (
            <Link className="button primary" to="/reviews/eligible-sources">
              {t('eligibleTitle')}
            </Link>
          )}
        </div>
      </div>

      <div className="review-toolbar">
        <div className="toolbar-filters">
          <label>
            {t('rating')}
            <select
              value={rating}
              onChange={e => setRating(e.target.value)}
            >
              <option value="">{t('all')}</option>
              {[5, 4, 3, 2, 1].map(v => (
                <option key={v} value={v}>
                  {'★'.repeat(v)} ({v})
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
