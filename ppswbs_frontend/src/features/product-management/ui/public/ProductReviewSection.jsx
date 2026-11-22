import React, { useEffect, useState } from 'react';
import { useReviewManagementApi, ReviewCard, formatStars } from '../../../reviewmanagement';
import { useT } from '../../../../shared/i18n/I18nProvider';
import { Loading } from '../../../../shared/ui/components';
import { messages } from '../messages';
import './product-reviews.css';

const SORTS = ['recent', 'best_to_worse', 'worse_to_best'];

/**
 * FR-063 (Session 2026-11-20, T172): customer reviews on the public product page. The reviews belong to 011 and are
 * read from its public API; 002 only shows them. The score is the one of every public review of the product, whatever
 * the star filter; without any review it says so instead of showing a score (no default 5.0).
 */
export function reviewScore(aggregate) {
  const count = Number(aggregate?.count || 0);
  const average = Number(aggregate?.averageRating || 0);
  if (count <= 0 || !(average > 0)) return { count: 0, average: null, stars: '☆☆☆☆☆' };
  return { count, average: average.toFixed(1), stars: formatStars(average) };
}

export default function ProductReviewSection({ productId, productName }) {
  const t = useT(messages);
  const api = useReviewManagementApi();
  const [sort, setSort] = useState('recent');
  const [ratingFilter, setRatingFilter] = useState(null); // null = every rating, otherwise 1–5
  const [data, setData] = useState({ items: [], aggregate: { count: 0, averageRating: 0 } });
  const [loading, setLoading] = useState(true);
  const [failed, setFailed] = useState(false);
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    if (!productId) return undefined;
    let active = true;
    setLoading(true);
    setFailed(false);
    api.listPublicReviews({
      targetType: 'RETAIL_PRODUCT',
      targetReference: String(productId),
      rating: ratingFilter || undefined,
      sort,
      limit: 50,
    }).then(result => {
      if (active && result) setData(result);
    }).catch(() => {
      if (active) setFailed(true);
    }).finally(() => {
      if (active) setLoading(false);
    });
    return () => { active = false; };
  }, [api, productId, sort, ratingFilter, attempt]);

  const score = reviewScore(data.aggregate);
  // Only this product's public reviews, even if the source sent something else.
  const reviews = (data.items || []).filter(review =>
    (!review.targetType || review.targetType === 'RETAIL_PRODUCT')
    && (!review.targetReference || String(review.targetReference) === String(productId)));
  const emptyKey = score.count > 0 && ratingFilter ? 'customerReview.noneForFilter' : 'customerReview.none';

  return (
    <section className="product-reviews-section" data-testid="product-reviews-section" aria-labelledby="customer-reviews-title">
      <div className="product-reviews-head">
        <div>
          <span className="eyebrow" style={{ color: '#d97706', fontWeight: 600, letterSpacing: '0.05em' }}>
            {t('customerReview.eyebrow')}
          </span>
          <h2 id="customer-reviews-title" className="section-title product-reviews-title">
            {productName ? t('customerReview.titleFor', { name: productName }) : t('customerReview.title')}
          </h2>
          <p className="muted product-reviews-lead">{t('customerReview.lead')}</p>
        </div>

        <div className="product-reviews-score-card" data-testid="product-reviews-score">
          {score.average !== null ? (
            <div className="product-score-number">
              {score.average}
              <small> / 5</small>
            </div>
          ) : (
            <div className="product-score-number product-score-none">{t('customerReview.noScore')}</div>
          )}
          <div className="product-score-meta">
            <span className={score.average !== null ? 'review-stars' : 'review-stars empty'}
              role="img" aria-label={score.average !== null ? t('customerReview.starsLabel', { score: score.average }) : t('customerReview.noScore')}>
              {score.stars}
            </span>
            <span className="product-score-count">{t('customerReview.count', { count: score.count })}</span>
          </div>
        </div>
      </div>

      <div className="product-reviews-controls">
        <div className="sort-group">
          <label htmlFor="review-sort-select">
            {t('customerReview.sortBy')}
          </label>
          <select id="review-sort-select" value={sort} onChange={event => setSort(event.target.value)}
            className="review-sort-select" data-testid="review-sort-select">
            {SORTS.map(value => <option key={value} value={value}>{t(`customerReview.sort.${value}`)}</option>)}
          </select>
        </div>

        <div className="filter-stars-pills" role="group" aria-label={t('customerReview.filterLabel')}>
          <button type="button" className={`filter-pill ${ratingFilter === null ? 'active' : ''}`} aria-pressed={ratingFilter === null}
            onClick={() => setRatingFilter(null)} data-testid="filter-star-all">
            {t('customerReview.all')}
          </button>
          {[5, 4, 3, 2, 1].map(star => (
            <button key={star} type="button" className={`filter-pill ${ratingFilter === star ? 'active' : ''}`} aria-pressed={ratingFilter === star}
              aria-label={t('customerReview.starFilter', { star })}
              onClick={() => setRatingFilter(ratingFilter === star ? null : star)} data-testid={`filter-star-${star}`}>
              {star} ★
            </button>
          ))}
        </div>
      </div>

      {loading && <div style={{ textAlign: 'center', padding: '2.5rem' }}><Loading /></div>}

      {!loading && failed && (
        // A secondary part of the page: announced politely, so it does not compete with the buying panel's alerts.
        <div className="review-error-box" role="status" data-testid="product-reviews-error" style={{ background: '#fef2f2', color: '#b91c1c', padding: '1rem', borderRadius: '8px' }}>
          {t('customerReview.loadFailed')}{' '}
          <button type="button" className="text-button" onClick={() => setAttempt(value => value + 1)}>{t('customerReview.retry')}</button>
        </div>
      )}

      {!loading && !failed && reviews.length === 0 && (
        <div className="product-reviews-empty" data-testid="product-reviews-empty">
          <div className="product-reviews-empty-icon" aria-hidden="true">💬</div>
          <h3>{t(emptyKey, { star: ratingFilter })}</h3>
          {emptyKey === 'customerReview.none' && (
            <p>{t('customerReview.noneHint')}</p>
          )}
        </div>
      )}

      {!loading && !failed && reviews.length > 0 && (
        <div className="product-reviews-list" data-testid="product-reviews-list">
          {reviews.map(review => <ReviewCard key={review.id} review={review} />)}
        </div>
      )}
    </section>
  );
}
