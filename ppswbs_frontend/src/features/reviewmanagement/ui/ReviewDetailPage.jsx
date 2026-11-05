import React from 'react';
import { Link, useParams } from 'react-router';
import { useAuth } from '../../../shared/auth/AuthProvider';
import { ErrorNotice, Loading, useResource } from '../../../shared/ui/components';
import { useReviewManagementApi } from '../api/useReviewManagementApi';
import { reviewManagementMessages } from '../messages';
import ReviewCard from './ReviewCard';

export default function ReviewDetailPage() {
  const { reviewId } = useParams(); const api = useReviewManagementApi(); const { status } = useAuth(); const resource = useResource(() => api.getReview(reviewId), [reviewId]);
  if (resource.loading) return <div className="review-container"><Loading /></div>;
  if (resource.error) return <div className="review-container"><ErrorNotice error={resource.error} messages={reviewManagementMessages} onReload={resource.reload} /></div>;
  return <div className="review-container"><header className="page-header"><h1>Review detail</h1></header><ReviewCard review={resource.data} /><div className="review-panel">{resource.data.reply && <><h2>Reply</h2><p>{resource.data.reply.bodyText}</p></>}{status === 'signed-in' && <Link className="button outline" to={`/reviews/${encodeURIComponent(reviewId)}/edit`}>Edit own review</Link>}</div></div>;
}
