import React from 'react';
import { useNavigate, useParams } from 'react-router';
import { ErrorNotice, Loading, useResource } from '../../../shared/ui/components';
import { useReviewManagementApi } from '../api/useReviewManagementApi';
import ReviewForm from './ReviewForm';

export default function EditReviewPage() {
  const { reviewId } = useParams(); const api = useReviewManagementApi(); const navigate = useNavigate(); const resource = useResource(() => api.getReview(reviewId, true), [reviewId]);
  if (resource.loading) return <div className="review-container"><Loading /></div>;
  if (resource.error) return <div className="review-container"><ErrorNotice error={resource.error} messages={{ vi: {}, en: {} }} onReload={resource.reload} /></div>;
  return <div className="review-container"><header className="page-header"><h1>Edit review</h1><p className="muted">PUBLIC status is preserved.</p></header><div className="review-panel"><ReviewForm existing={resource.data} onSaved={({ key, rating, bodyText, version }) => api.editReview(key, reviewId, { rating, bodyText }, version).then(result => { navigate(`/reviews/${encodeURIComponent(result.id)}`); return result; })} /></div></div>;
}
