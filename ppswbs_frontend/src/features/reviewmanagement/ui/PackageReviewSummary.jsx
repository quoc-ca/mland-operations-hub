import React from 'react';
import { Link } from 'react-router';
import { useResource, Loading, ErrorNotice } from '../../../shared/ui/components';
import { useReviewManagementApi } from '../api/useReviewManagementApi';
import { formatDateTime, formatStars } from '../rules';

const SHOWN = 3;

/** First letter of a masked name ("H*** Quốc" -> "H") for the round avatar. */
const initialOf = name => {
  const letter = String(name || '').trim().charAt(0);
  return letter && letter !== '*' ? letter.toUpperCase() : '★';
};

/**
 * Public reviews shown on a workshop page: of one package (packageId) or of every workshop (allPackages). A review is still
 * about one session (written by someone who attended it); this view groups them by the package stored with each review.
 * Writing a review is not offered here: it starts from the customer's own bookings.
 */
export default function PackageReviewSummary({ packageId, packageName, allPackages = false }) {
  const api = useReviewManagementApi();
  const resource = useResource(
    () => (packageId || allPackages
      ? api.listPublicReviews({ packageId: packageId || undefined, targetType: allPackages ? 'WORKSHOP_SESSION' : undefined, limit: SHOWN })
      : Promise.resolve(null)),
    [packageId, allPackages],
  );

  if (!packageId && !allPackages) return null;

  const page = resource.data || { items: [], aggregate: { count: 0, averageRating: 0 } };
  const reviews = Array.isArray(page.items) ? page.items.slice(0, SHOWN) : [];
  const aggregate = page.aggregate || {};
  const count = Number(aggregate.count || 0);
  const average = Number(aggregate.averageRating || 0);

  return (
    <section className="pkg-reviews" aria-labelledby="package-review-title">
      <div className="pkg-reviews-head">
        <div>
          <p className="eyebrow">{allPackages ? 'ĐÁNH GIÁ WORKSHOP' : 'ĐÁNH GIÁ CỦA GÓI'}</p>
          <h2 id="package-review-title">
            {allPackages ? 'Khách hàng nói gì về các workshop của MLAND' : `Khách hàng nói gì về ${packageName ? `gói “${packageName}”` : 'gói này'}`}
          </h2>
          <p className="muted">{allPackages ? 'Đánh giá do khách đã tham dự các buổi workshop viết.' : 'Đánh giá do khách đã tham dự các buổi của gói này viết.'}</p>
        </div>
        <div className="pkg-reviews-score" aria-label="Tổng hợp đánh giá">
          <div className="pkg-reviews-score-number">{average.toFixed(1)}<small> / 5</small></div>
          <div className="pkg-reviews-score-meta">
            <span className="review-stars" aria-hidden="true">{formatStars(Math.round(average) || 0)}</span>
            <span>{count} đánh giá công khai</span>
          </div>
        </div>
      </div>

      {resource.loading && <Loading />}
      <ErrorNotice error={resource.error} messages={{ vi: {}, en: {} }} onReload={resource.reload} />

      {!resource.loading && !resource.error && reviews.length === 0 && (
        <div className="pkg-reviews-empty">{allPackages ? 'Chưa có đánh giá công khai nào.' : 'Gói này chưa có đánh giá công khai.'}</div>
      )}

      {!resource.loading && !resource.error && reviews.length > 0 && (
        <div className="pkg-reviews-grid">
          {reviews.map(review => (
            <article className="pkg-review-card" key={review.id}>
              <div className="pkg-review-top">
                <div className="pkg-review-avatar" aria-hidden="true">{initialOf(review.maskedDisplayName)}</div>
                <div className="pkg-review-who">
                  <strong>{review.maskedDisplayName || 'Khách hàng Mland'}</strong>
                  <span>{formatDateTime(review.createdAt)}</span>
                </div>
              </div>
              <div className="review-stars" aria-label={`${review.rating} / 5 sao`}>{formatStars(review.rating)}</div>
              {review.bodyText && <p className="pkg-review-text">{review.bodyText}</p>}
              {allPackages && review.packageName && <span className="pkg-review-package">{review.packageName}</span>}
            </article>
          ))}
        </div>
      )}

      {count > reviews.length && (
        <p className="pkg-reviews-more">
          <Link className="button outline" to={allPackages ? '/reviews' : `/reviews?packageId=${encodeURIComponent(packageId)}`}>
            {allPackages ? `Xem tất cả ${count} đánh giá` : `Xem tất cả ${count} đánh giá của gói`}
          </Link>
        </p>
      )}
    </section>
  );
}
