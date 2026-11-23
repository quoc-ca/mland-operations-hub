import React from 'react';
import { Link, useLocation, useParams } from 'react-router';
import { useHttp } from '../../../shared/api/useHttp';
import { useAuth } from '../../../shared/auth/AuthProvider';
import { useT } from '../../../shared/i18n/I18nProvider';
import { ErrorNotice, Loading, useResource } from '../../../shared/ui/components';
import { useReviewManagementApi } from '../api/useReviewManagementApi';
import { reviewManagementMessages } from '../messages';
import ReviewCard from './ReviewCard';
import SourceHeader, { isRetail } from './SourceHeader';

/**
 * One review (session 2026-11-24 layout): what was reviewed on top — product photo, name and size, or the workshop —
 * then the review, then the actions in one row. The owner's own source (with the size) comes from their eligible
 * sources; anyone else sees the public product name. Nothing about the source is shown when it cannot be found.
 */
export default function ReviewDetailPage() {
  const t = useT(reviewManagementMessages);
  const { reviewId } = useParams();
  const api = useReviewManagementApi();
  const http = useHttp();
  const location = useLocation();
  const { status } = useAuth();
  const signedIn = status === 'signed-in';

  const resource = useResource(() => api.getReview(reviewId, signedIn), [reviewId, status]);
  const review = resource.data;
  const retail = isRetail(review);

  const passed = location.state?.source;
  const own = useResource(
    () => (passed || !signedIn || !review?.canEdit ? Promise.resolve(null) : api.listEligibleSources().catch(() => null)),
    [passed, signedIn, review?.canEdit]
  );
  const ownSource = passed || (own.data?.items || own.data?.sources || [])
    .find(item => String(item.reviewId || item.review?.id || '') === String(reviewId));
  const product = useResource(
    () => (ownSource || !retail || !review?.targetReference
      ? Promise.resolve(null)
      : http(`/api/v1/products/${encodeURIComponent(review.targetReference)}`, { auth: false }).catch(() => null)),
    [Boolean(ownSource), retail, review?.targetReference]
  );
  const source = ownSource
    || (retail && product.data?.name
      ? { targetType: 'RETAIL_PRODUCT', targetReference: review.targetReference, label: product.data.name }
      : null);

  if (resource.loading && !review) {
    return (
      <div className="review-container review-theme review-detail-page">
        <Loading />
      </div>
    );
  }

  if (resource.error) {
    return (
      <div className="review-container review-theme review-detail-page">
        <ErrorNotice error={resource.error} messages={reviewManagementMessages} onReload={resource.reload} />
      </div>
    );
  }

  const canEdit = signedIn && Boolean(review?.canEdit);
  const productPath = retail && review?.targetReference ? `/products/${encodeURIComponent(review.targetReference)}` : null;

  return (
    <div className="review-container review-theme review-detail-page">
      <header className="page-header">
        <h1>{t('detailTitle')}</h1>
      </header>

      <section className="review-detail-card">
        {source && (
          <div className="review-detail-source">
            <SourceHeader source={source} linkProduct />
            {productPath && (
              <Link className="button outline small" to={productPath}>{t('viewProduct')}</Link>
            )}
          </div>
        )}

        {review?.status && review.status !== 'PUBLIC' && canEdit && (
          <p className="muted small">{t('hiddenNotice')}</p>
        )}

        <ReviewCard review={review} />

        <div className="review-detail-actions">
          <Link className="button outline" to={canEdit ? '/reviews?tab=reviewed' : '/reviews'}>
            &larr; {t('back')}
          </Link>
          {canEdit && (
            <Link
              className="button primary"
              to={`/reviews/${encodeURIComponent(reviewId)}/edit`}
              state={source ? { source } : undefined}
            >
              {t('editOwnReview')}
            </Link>
          )}
        </div>
      </section>
    </div>
  );
}
