import React, { useEffect, useState } from 'react';
import { useReviewManagementApi, ReviewCard, ReviewForm, formatStars } from '../../../reviewmanagement';
import { useRole } from '../../../../shared/auth/useRole';
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
  // Only a Member can review (011's eligible sources are Member-only): asking for another role gets a 403, which leaves
  // the page. Staff, Manager, Admin and Carrier see the public reviews without the review action.
  const isMember = useRole() === 'MEMBER';
  const [eligibleItem, setEligibleItem] = useState(null);
  const [showReviewModal, setShowReviewModal] = useState(false);

  useEffect(() => {
    const fetchEligible = api?.listEligibleSources || api?.getMemberEligibleSources;
    if (!productId || !isMember || typeof fetchEligible !== 'function') {
      setEligibleItem(null);
      return;
    }
    let active = true;
    fetchEligible.call(api)
      .then((res) => {
        if (!active || !res || !Array.isArray(res.sources)) return;
        const matching = res.sources.find(
          (s) => (s.targetType === 'RETAIL_PRODUCT' || !s.targetType) && String(s.targetReference) === String(productId) && s.eligible
        );
        setEligibleItem(matching || null);
      })
      .catch(() => {
        // Not eligible or error, ignore
      });
    return () => {
      active = false;
    };
  }, [api, productId, isMember, attempt]);

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

        <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-end', gap: '0.65rem' }}>
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

          {eligibleItem && !eligibleItem.alreadyReviewed && (
            <button
              type="button"
              className="button primary"
              style={{
                display: 'inline-flex',
                alignItems: 'center',
                gap: '0.4rem',
                padding: '0.45rem 0.9rem',
                fontSize: '0.85rem',
                fontWeight: 600,
                backgroundColor: '#b45309',
                borderColor: '#b45309',
                color: '#ffffff',
                borderRadius: '6px',
                cursor: 'pointer',
              }}
              onClick={() => setShowReviewModal(true)}
              data-testid="write-product-review-btn"
            >
              ✍️ Viết đánh giá sản phẩm
            </button>
          )}

          {eligibleItem && eligibleItem.alreadyReviewed && (
            <div
              style={{
                fontSize: '0.8rem',
                color: '#16a34a',
                fontWeight: 600,
                background: '#f0fdf4',
                padding: '0.3rem 0.65rem',
                borderRadius: '6px',
                border: '1px solid #bbf7d0',
              }}
              data-testid="already-reviewed-badge"
            >
              ✓ Bạn đã gửi đánh giá cho sản phẩm này
            </div>
          )}
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

      {showReviewModal && eligibleItem && (
        <div
          role="dialog"
          aria-modal="true"
          data-testid="product-review-modal"
          style={{
            position: 'fixed',
            inset: 0,
            backgroundColor: 'rgba(0, 0, 0, 0.5)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            zIndex: 1000,
            padding: '1rem',
          }}
        >
          <div
            style={{
              backgroundColor: '#ffffff',
              borderRadius: '12px',
              padding: '1.5rem',
              maxWidth: '560px',
              width: '100%',
              maxHeight: '90vh',
              overflowY: 'auto',
              boxShadow: '0 10px 25px rgba(0, 0, 0, 0.2)',
            }}
          >
            <div
              style={{
                display: 'flex',
                justifyContent: 'space-between',
                alignItems: 'center',
                marginBottom: '1rem',
                borderBottom: '1px solid #e2e8f0',
                paddingBottom: '0.75rem',
              }}
            >
              <h3 style={{ margin: 0, fontSize: '1.15rem', color: '#0f172a' }}>
                Đánh giá: {productName || 'Sản phẩm'}
              </h3>
              <button
                type="button"
                onClick={() => setShowReviewModal(false)}
                style={{
                  background: 'none',
                  border: 'none',
                  fontSize: '1.25rem',
                  cursor: 'pointer',
                  color: '#64748b',
                }}
                data-testid="close-review-modal-btn"
              >
                ✕
              </button>
            </div>

            <ReviewForm
              source={{
                sourceType: eligibleItem.sourceType || 'RETAIL_ORDER_LINE',
                sourceReference: eligibleItem.sourceReference,
                targetType: 'RETAIL_PRODUCT',
                targetReference: String(productId),
                label: productName,
              }}
              handle={null}
              onSaved={async ({ key, ...body }) => {
                const res = await api.createReview(key, body, true);
                setShowReviewModal(false);
                setAttempt((v) => v + 1);
                setEligibleItem((prev) => (prev ? { ...prev, alreadyReviewed: true } : prev));
                return res;
              }}
            />
          </div>
        </div>
      )}
    </section>
  );
}
