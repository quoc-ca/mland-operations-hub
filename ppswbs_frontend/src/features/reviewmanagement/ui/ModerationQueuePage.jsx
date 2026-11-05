import React from 'react';
import { Link } from 'react-router';
import { useT } from '../../../shared/i18n/I18nProvider';
import { ErrorNotice, Loading, useResource } from '../../../shared/ui/components';
import { useReviewManagementApi } from '../api/useReviewManagementApi';
import { reviewManagementMessages } from '../messages';

export default function ModerationQueuePage() { const t=useT(reviewManagementMessages); const api=useReviewManagementApi(); const resource=useResource(() => api.listModeration(), []); const items=resource.data?.items || [];
  return <div className="review-container"><header className="page-header"><h1>{t('moderationTitle')}</h1><p className="muted">{t('moderationSubtitle')}</p></header>{resource.loading&&<Loading/>}<ErrorNotice error={resource.error} messages={reviewManagementMessages} onReload={resource.reload}/>{!resource.loading&&!resource.error&&!items.length&&<div className="empty-state">{t('noReviews')}</div>}<div className="review-grid">{items.map(item=><div className="review-card" key={item.id}><ReviewCard review={item}/><Link className="button outline small" to={`/review-moderation/${encodeURIComponent(item.id)}`}>Open</Link></div>)}</div></div>; }
