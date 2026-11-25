import React from 'react';
import { useLocation, useNavigate, useParams } from 'react-router';
import { useT } from '../../../shared/i18n/I18nProvider';
import { ErrorNotice, Loading, useResource } from '../../../shared/ui/components';
import { useReviewManagementApi } from '../api/useReviewManagementApi';
import { reviewManagementMessages } from '../messages';
import ReviewForm from './ReviewForm';
import SourceHeader from './SourceHeader';

export default function EditReviewPage() {
  const t = useT(reviewManagementMessages);
  const { reviewId } = useParams();
  const api = useReviewManagementApi();
  const navigate = useNavigate();
  const location = useLocation();

  const resource = useResource(() => api.getReview(reviewId, true), [reviewId]);
  // What is being reviewed (photo, name, size) — passed from "Sửa đánh giá", else found among the Member's own sources
  // by review id (GET /reviews/{id} carries no product name or size). Not found: the form shows without it.
  const passed = location.state?.source;
  const sources = useResource(
    () => (passed ? Promise.resolve(null) : api.listEligibleSources().catch(() => null)),
    [passed]
  );
  const source = passed || (sources.data?.items || sources.data?.sources || [])
    .find(item => String(item.reviewId || item.review?.id || '') === String(reviewId));

  if (resource.loading) {
    return (
      <div className="review-container review-theme">
        <Loading />
      </div>
    );
  }

  if (resource.error) {
    return (
      <div className="review-container review-theme">
        <ErrorNotice
          error={resource.error}
          messages={reviewManagementMessages}
          onReload={resource.reload}
        />
      </div>
    );
  }

  if (!resource.data?.canEdit) {
    return (
      <div className="review-container review-theme">
        <header className="page-header">
          <h1>{t('editTitle')}</h1>
        </header>

        <div className="notice error" role="alert" data-testid="edit-review-forbidden">
          {t('notAuthorizedToEdit')}
        </div>

        <div className="review-actions">
          <button
            type="button"
            className="button outline"
            onClick={() => navigate('/reviews')}
          >
            &larr; {t('back')}
          </button>
          <button
            type="button"
            className="button primary"
            onClick={() => navigate(`/reviews/${encodeURIComponent(reviewId)}`)}
          >
            {t('open')}
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="review-container review-theme">
      <header className="page-header">
        <h1>{t('editTitle')}</h1>
        <p className="muted">{t('editSubtitle')}</p>
      </header>

      <div className="info-banner">
        {t('editDisclaimer')}
      </div>

      {source && (
        <div className="source-info-card">
          <SourceHeader source={source} linkProduct />
        </div>
      )}

      <div className="review-panel">
        <ReviewForm
          existing={resource.data}
          onSaved={({ key, rating, bodyText, imageUrls, version }) =>
            api
              .editReview(key, reviewId, { rating, bodyText, imageUrls }, version)
              .then(result => {
                navigate(`/reviews/${encodeURIComponent(result.id)}`);
                return result;
              })
          }
        />
      </div>

      {/* Back to where the edit was opened from (the review or "Đã đánh giá"); opened directly, to the review. */}
      <div className="review-form-back">
        <button
          type="button"
          className="button outline"
          onClick={() => (location.key !== 'default'
            ? navigate(-1)
            : navigate(`/reviews/${encodeURIComponent(reviewId)}`))}
        >
          &larr; {t('back')}
        </button>
      </div>
    </div>
  );
}
