import React from 'react';
import { Link, useParams } from 'react-router';
import { useAuth } from '../../../shared/auth/AuthProvider';
import { useT } from '../../../shared/i18n/I18nProvider';
import { ErrorNotice, Loading, useResource } from '../../../shared/ui/components';
import { useReviewManagementApi } from '../api/useReviewManagementApi';
import { reviewManagementMessages } from '../messages';
import ReviewCard from './ReviewCard';

export default function ReviewDetailPage() {
  const t = useT(reviewManagementMessages);
  const { reviewId } = useParams();
  const api = useReviewManagementApi();
  const { status } = useAuth();

  const resource = useResource(() => api.getReview(reviewId), [reviewId]);

  if (resource.loading) {
    return (
      <div className="review-container">
        <Loading />
      </div>
    );
  }

  if (resource.error) {
    return (
      <div className="review-container">
        <ErrorNotice
          error={resource.error}
          messages={reviewManagementMessages}
          onReload={resource.reload}
        />
      </div>
    );
  }

  const review = resource.data;

  return (
    <div className="review-container">
      <header className="page-header">
        <h1>{t('detailTitle')}</h1>
      </header>

      <ReviewCard review={review} />

      <div className="review-panel" style={{ marginTop: '1.25rem' }}>
        {status === 'signed-in' && (
          <Link
            className="button outline"
            to={`/reviews/${encodeURIComponent(reviewId)}/edit`}
          >
            {t('editOwnReview')}
          </Link>
        )}
      </div>

      <div className="review-actions">
        <Link className="button outline" to="/reviews">
          &larr; {t('back')}
        </Link>
      </div>
    </div>
  );
}
