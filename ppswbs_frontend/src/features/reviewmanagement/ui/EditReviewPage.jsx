import React from 'react';
import { useNavigate, useParams } from 'react-router';
import { useT } from '../../../shared/i18n/I18nProvider';
import { ErrorNotice, Loading, useResource } from '../../../shared/ui/components';
import { useReviewManagementApi } from '../api/useReviewManagementApi';
import { reviewManagementMessages } from '../messages';
import ReviewForm from './ReviewForm';

export default function EditReviewPage() {
  const t = useT(reviewManagementMessages);
  const { reviewId } = useParams();
  const api = useReviewManagementApi();
  const navigate = useNavigate();

  const resource = useResource(() => api.getReview(reviewId, true), [reviewId]);

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

  return (
    <div className="review-container">
      <header className="page-header">
        <h1>{t('editTitle')}</h1>
        <p className="muted">{t('editSubtitle')}</p>
      </header>

      <div className="info-banner">
        {t('editDisclaimer')}
      </div>

      <div className="review-panel">
        <ReviewForm
          existing={resource.data}
          onSaved={({ key, rating, bodyText, version }) =>
            api
              .editReview(key, reviewId, { rating, bodyText }, version)
              .then(result => {
                navigate(`/reviews/${encodeURIComponent(result.id)}`);
                return result;
              })
          }
        />
      </div>
    </div>
  );
}
