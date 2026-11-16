import React, { useState } from 'react';
import { Link } from 'react-router';
import { useT } from '../../../shared/i18n/I18nProvider';
import { useRole } from '../../../shared/auth/useRole';
import { ErrorNotice, Loading, useResource } from '../../../shared/ui/components';
import { useReviewManagementApi } from '../api/useReviewManagementApi';
import { reviewManagementMessages } from '../messages';
import ReviewCard from './ReviewCard';

export default function ModerationQueuePage() {
  const t = useT(reviewManagementMessages);
  const api = useReviewManagementApi();
  const role = useRole();
  const [statusFilter, setStatusFilter] = useState('ALL');

  const resource = useResource(() => api.listModeration(), []);
  const allItems = resource.data?.items || [];

  const items = statusFilter === 'ALL'
    ? allItems
    : allItems.filter(item => item.status === statusFilter);

  return (
    <div className="review-container">
      <header className="page-header">
        <div style={{ marginBottom: '0.75rem' }}>
          <Link to="/home" className="button outline small" style={{ textDecoration: 'none' }}>
            ← Quay lại Trang chủ
          </Link>
        </div>
        <h1>{t('moderationTitle')}</h1>
        <p className="muted">{t('moderationSubtitle')}</p>
      </header>

      <div className="warning-banner">
        {t('staffNotice')}
      </div>

      {role && (
        <div style={{ margin: '0.75rem 0', display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
          <span style={{ fontSize: '0.875rem', color: '#6b7280' }}>Vai trò hiện tại:</span>
          <span className={`badge-tag ${role === 'MANAGER' ? 'badge-status' : 'badge-status-hidden'}`}>
            {role === 'MANAGER' ? 'Quản lý (MANAGER)' : 'Nhân viên (STAFF)'}
          </span>
        </div>
      )}

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
