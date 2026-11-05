import React, { useState } from 'react';
import { Link, useNavigate } from 'react-router';
import { useT } from '../../../shared/i18n/I18nProvider';
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

  const resource = useResource(
    () => api.listPublicReviews({ rating: rating || undefined }),
    [rating]
  );

  const page = resource.data || {
    items: [],
    aggregate: { count: 0, averageRating: 0 },
  };

  const avgRating = Number(page.aggregate?.averageRating || 0);
  const count = Number(page.aggregate?.count || 0);

  return (
    <div className="review-container">
      <header className="page-header">
        <h1>{t('publicTitle')}</h1>
        <p className="muted">{t('publicSubtitle')}</p>
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
          <Link className="button outline" to="/reviews/guest/lookup">
            {t('lookupTitle')}
          </Link>
          <Link className="button primary" to="/reviews/eligible-sources">
            {t('eligibleTitle')}
          </Link>
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
          {page.items.length} / {count}
        </span>
      </div>

      {resource.loading && <Loading />}
      <ErrorNotice
        error={resource.error}
        messages={reviewManagementMessages}
        onReload={resource.reload}
      />

      {!resource.loading && !resource.error && page.items.length === 0 && (
        <div className="empty-state">{t('noReviews')}</div>
      )}

      <div className="review-grid">
        {page.items.map(review => (
          <ReviewCard
            key={review.id}
            review={review}
            onOpen={item => navigate(`/reviews/${encodeURIComponent(item.id)}`)}
          />
        ))}
      </div>
    </div>
  );
}
