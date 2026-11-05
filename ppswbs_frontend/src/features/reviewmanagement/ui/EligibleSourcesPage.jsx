import React from 'react';
import { useNavigate } from 'react-router';
import { useT } from '../../../shared/i18n/I18nProvider';
import { ErrorNotice, Loading, useResource } from '../../../shared/ui/components';
import { useReviewManagementApi } from '../api/useReviewManagementApi';
import { reviewManagementMessages } from '../messages';

export default function EligibleSourcesPage() {
  const t = useT(reviewManagementMessages);
  const api = useReviewManagementApi();
  const navigate = useNavigate();

  const resource = useResource(() => api.listEligibleSources(), []);
  const items = resource.data?.items || [];

  return (
    <div className="review-container">
      <header className="page-header">
        <h1>{t('eligibleTitle')}</h1>
        <p className="muted">{t('eligibleSubtitle')}</p>
      </header>

      {resource.loading && <Loading />}
      <ErrorNotice
        error={resource.error}
        messages={reviewManagementMessages}
        onReload={resource.reload}
      />

      {!resource.loading && !resource.error && items.length === 0 && (
        <div className="empty-state">{t('noSources')}</div>
      )}

      <div className="source-list">
        {items.map(source => (
          <button
            type="button"
            className="source-card"
            key={`${source.sourceType}:${source.sourceReference}`}
            onClick={() => navigate('/reviews/new', { state: { source } })}
          >
            <strong>
              {source.label ||
                (source.targetType === 'WORKSHOP_SESSION'
                  ? t('sourceWorkshop')
                  : t('sourceRetail'))}
            </strong>
            <span>{source.sourceReference}</span>
            <span className="badge-tag badge-verified">{t('verified')}</span>
            <span className="action-label">{t('writeReview')} &rarr;</span>
          </button>
        ))}
      </div>
    </div>
  );
}
