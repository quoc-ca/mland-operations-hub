import React, { useState } from 'react';
import { Link } from 'react-router';
import { useT } from '../../../shared/i18n/I18nProvider';
import { ErrorNotice, Loading, useResource } from '../../../shared/ui/components';
import { useReviewManagementApi } from '../api/useReviewManagementApi';
import { reviewManagementMessages } from '../messages';
import ReviewCard from './ReviewCard';

export default function ModerationQueuePage() {
  const t = useT(reviewManagementMessages);
  const api = useReviewManagementApi();
  const [statusFilter, setStatusFilter] = useState('ALL');

  const resource = useResource(() => api.listModeration(), []);
  const allItems = resource.data?.items || [];

  const items = statusFilter === 'ALL'
    ? allItems
    : allItems.filter(item => item.status === statusFilter);

  return (
    <div className="review-container">
      <header className="page-header">
        <h1>{t('moderationTitle')}</h1>
        <p className="muted">{t('moderationSubtitle')}</p>
      </header>

      <div className="warning-banner">
        {t('staffNotice')}
      </div>

      <div className="review-toolbar">
        <div className="toolbar-filters">
          <label>
            {t('filterStatus')}
            <select
              value={statusFilter}
              onChange={e => setStatusFilter(e.target.value)}
            >
              <option value="ALL">{t('all')}</option>
              <option value="PUBLIC">{t('status_PUBLIC')}</option>
              <option value="HIDDEN">{t('status_HIDDEN')}</option>
              <option value="REMOVED">{t('status_REMOVED')}</option>
            </select>
          </label>
        </div>
        <span className="muted">
          {items.length} / {allItems.length}
        </span>
      </div>

      {resource.loading && <Loading />}
      <ErrorNotice
        error={resource.error}
        messages={reviewManagementMessages}
        onReload={resource.reload}
      />

      {!resource.loading && !resource.error && items.length === 0 && (
        <div className="empty-state">{t('noReviews')}</div>
      )}

      <div className="review-grid">
        {items.map(item => (
          <div className="review-card" key={item.id}>
            <ReviewCard review={item} />
            <Link
              className="button outline small"
              to={`/review-moderation/${encodeURIComponent(item.id)}`}
            >
              {t('open')}
            </Link>
          </div>
        ))}
      </div>
    </div>
  );
}
