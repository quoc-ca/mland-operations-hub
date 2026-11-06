import React from 'react';
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
          <p className="eyebrow">WORKSHOP REVIEW · ĐÁNH GIÁ WORKSHOP</p>
          <h2 id="workshop-review-title">Khách hàng nói gì về buổi workshop</h2>
          <p className="muted">Review được tính theo workshop session. Chi nhánh chỉ là thông tin ngữ cảnh.</p>
        </div>
        <div className="workshop-review-aggregate" aria-label="Workshop review aggregate">
          <strong>{Number(aggregate.averageRating || 0).toFixed(1)} / 5</strong>
          <span>{Number(aggregate.count || 0)} review công khai</span>
        </div>
      </div>

      {resource.loading && <Loading />}
      <ErrorNotice
        error={resource.error}
        messages={{ vi: {}, en: {} }}
        onReload={resource.reload}
      />

      {!resource.loading && !resource.error && reviews.length === 0 && (
        <div className="workshop-review-empty">Chưa có review công khai cho buổi workshop này.</div>
      )}

      {!resource.loading && !resource.error && reviews.length > 0 && (
        <div className="workshop-review-grid">
          {reviews.map(review => (
            <article className="workshop-review-card" key={review.id}>
              <div className="review-stars" aria-label={`${review.rating} / 5 stars`}>
                {'★'.repeat(Math.max(0, Math.min(5, Number(review.rating) || 0)))}
              </div>
              {review.bodyText && <p>{review.bodyText}</p>}
              <strong>{review.maskedDisplayName || 'Khách hàng Mland'}</strong>
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
        {reviewEligibility ? 'Review workshop · Đánh giá ngay' : 'Available after attendance · Sau khi tham gia'}
      </button>
    </section>
  );
}
