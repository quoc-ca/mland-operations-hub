import React from 'react';
import { Link, useLocation, useNavigate } from 'react-router';
import { useAuth } from '../../../shared/auth/AuthProvider';
import { useT } from '../../../shared/i18n/I18nProvider';
import { useReviewManagementApi } from '../api/useReviewManagementApi';
import { reviewManagementMessages } from '../messages';
import ReviewForm from './ReviewForm';

export default function CreateReviewPage() {
  const t = useT(reviewManagementMessages);
  const location = useLocation();
  const navigate = useNavigate();
  const api = useReviewManagementApi();
  const { status } = useAuth();
  const { source, handle } = location.state || {};

  if (!source) {
    return (
      <div className="review-container">
        <div className="notice error" role="alert">
          {t('selectSourceFirst')}
        </div>
        <div className="review-actions">
          <Link className="button outline" to="/reviews">
            {t('back')}
          </Link>
          <Link className="button outline" to="/reviews/guest/lookup">
            {t('lookupTitle')}
          </Link>
          <Link className="button primary" to="/reviews/eligible-sources">
            {t('eligibleTitle')}
          </Link>
        </div>
      </div>
    );
  }

  return (
    <div className="review-container">
      <header className="page-header">
        <h1>{t('createTitle')}</h1>
        <p className="muted">{t('createSubtitle')}</p>
      </header>

      {handle && (
        <div className="info-banner">
          {t('guestDisclaimer')}
        </div>
      )}

      <div className="source-info-card">
        <strong>{source.label || (source.targetType === 'WORKSHOP_SESSION' ? t('sourceWorkshop') : t('sourceRetail'))}</strong>
        <span>{t('sourceReference')}: {source.sourceReference}</span>
      </div>

      <div className="review-panel">
        <ReviewForm
          source={source}
          handle={handle}
          onSaved={({ key, ...body }) =>
            api
              .createReview(
                key,
                {
                  ...body,
                  sourceType: source.sourceType,
                  sourceReference: source.sourceReference,
                  targetType: source.targetType,
                  targetReference: source.targetReference,
                  guestEligibilityHandle: handle,
                },
                status === 'signed-in'
              )
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
