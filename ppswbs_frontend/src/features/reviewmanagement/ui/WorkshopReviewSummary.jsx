import React from 'react';
import { useT } from '../../../shared/i18n/I18nProvider';
import { reviewManagementMessages } from '../messages';
import { useResource, Loading, ErrorNotice } from '../../../shared/ui/components';
import { useReviewManagementApi } from '../api/useReviewManagementApi';

/**
 * Public workshop-session reviews embedded by a booking/discovery screen.
 * Eligibility is intentionally supplied by the owning booking flow; this
 * component never infers it from a booking status or from a source reference.
 */
export default function WorkshopReviewSummary({
  targetReference,
  reviewEligibility = false,
  onReview,
}) {
  const t = useT(reviewManagementMessages);
  const api = useReviewManagementApi();
  const resource = useResource(
    () => targetReference
      ? api.listPublicReviews({
        targetType: 'WORKSHOP_SESSION',
        targetReference,
      })
      : Promise.resolve(null),
    [targetReference]
  );

  if (!targetReference) return null;

  const page = resource.data || { items: [], aggregate: { count: 0, averageRating: 0 } };
  const reviews = Array.isArray(page.items) ? page.items.slice(0, 2) : [];
  const aggregate = page.aggregate || {};

  return (
    <section className="workshop-review-summary" aria-labelledby="workshop-review-title">
      <div className="workshop-review-heading">
        <div>
          <p className="eyebrow">{t('wsEyebrow')}</p>
          <h2 id="workshop-review-title">{t('wsTitle')}</h2>
          <p className="muted">{t('wsNote')}</p>
        </div>
        <div className="workshop-review-aggregate" aria-label={t('wsAggregateLabel')}>
          <strong>{Number(aggregate.averageRating || 0).toFixed(1)} / 5</strong>
          <span>{t('wsCount', { count: Number(aggregate.count || 0) })}</span>
        </div>
      </div>

      {resource.loading && <Loading />}
      <ErrorNotice
        error={resource.error}
        messages={{ vi: {}, en: {} }}
        onReload={resource.reload}
      />

      {!resource.loading && !resource.error && reviews.length === 0 && (
        <div className="workshop-review-empty">{t('wsEmpty')}</div>
      )}

      {!resource.loading && !resource.error && reviews.length > 0 && (
        <div className="workshop-review-grid">
          {reviews.map(review => (
            <article className="workshop-review-card" key={review.id}>
              <div className="review-stars" aria-label={t('starsLabel', { rating: review.rating })}>
                {'★'.repeat(Math.max(0, Math.min(5, Number(review.rating) || 0)))}
              </div>
              {review.bodyText && <p>{review.bodyText}</p>}
              <strong>{review.maskedDisplayName || t('defaultReviewer')}</strong>
            </article>
          ))}
        </div>
      )}

      <button
        type="button"
        className="button primary workshop-review-cta"
        disabled={!reviewEligibility}
        aria-disabled={!reviewEligibility}
        onClick={onReview}
      >
        {reviewEligibility ? t('wsCtaEligible') : t('wsCtaLocked')}
      </button>
    </section>
  );
}
