import React, { useState } from 'react';
import { Link, useNavigate } from 'react-router';
import { ErrorNotice, Loading, useResource } from '../../../shared/ui/components';
import { useT } from '../../../shared/i18n/I18nProvider';
import { useReviewManagementApi } from '../api/useReviewManagementApi';
import { reviewManagementMessages } from '../messages';
import ReviewCard from './ReviewCard';

export default function PublicReviewListPage() {
  const t = useT(reviewManagementMessages); const api = useReviewManagementApi(); const navigate = useNavigate();
  const [rating, setRating] = useState('');
  const resource = useResource(() => api.listPublicReviews({ rating: rating || undefined }), [rating]);
  const page = resource.data || { items: [], aggregate: { count: 0, averageRating: 0 } };
  return <div className="review-container">
    <header className="page-header"><h1>{t('publicTitle')}</h1><p className="muted">{t('publicSubtitle')}</p></header>
    <div className="review-toolbar"><span><strong>{page.aggregate?.averageRating || 0}</strong> / 5 · {page.aggregate?.count || 0}</span><label>{t('rating')} <select value={rating} onChange={e => setRating(e.target.value)}><option value="">All</option>{[5,4,3,2,1].map(v => <option key={v} value={v}>{v}</option>)}</select></label></div>
    {resource.loading && <Loading />}
    <ErrorNotice error={resource.error} messages={reviewManagementMessages} onReload={resource.reload} />
    {!resource.loading && !resource.error && page.items.length === 0 && <div className="empty-state">{t('noReviews')}</div>}
    <div className="review-grid">{page.items.map(review => <ReviewCard key={review.id} review={review} onOpen={item => navigate(`/reviews/${encodeURIComponent(item.id)}`)} />)}</div>
    <p className="review-actions"><Link className="button outline" to="/reviews/guest/lookup">{t('lookupTitle')}</Link><Link className="button primary" to="/reviews/eligible-sources">{t('eligibleTitle')}</Link></p>
  </div>;
}
