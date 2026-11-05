import React from 'react';
import { Link, useLocation, useNavigate } from 'react-router';
import { useAuth } from '../../../shared/auth/AuthProvider';
import { useReviewManagementApi } from '../api/useReviewManagementApi';
import { reviewManagementMessages } from '../messages';
import ReviewForm from './ReviewForm';

export default function CreateReviewPage() {
  const location = useLocation(); const navigate = useNavigate(); const api = useReviewManagementApi(); const { status } = useAuth(); const { source, handle } = location.state || {};
  if (!source) return <div className="review-container"><div className="notice error">Select an eligible source first.</div><Link to="/reviews">Back</Link></div>;
  return <div className="review-container"><header className="page-header"><h1>Share your experience</h1><p className="muted">{source.label || source.targetType}</p></header><div className="review-panel"><ReviewForm source={source} handle={handle} onSaved={({ key, ...body }) => api.createReview(key, { ...body, sourceType: source.sourceType, sourceReference: source.sourceReference, targetType: source.targetType, targetReference: source.targetReference, guestEligibilityHandle: handle }, status === 'signed-in').then(result => { navigate(`/reviews/${encodeURIComponent(result.id)}`); return result; })} /></div></div>;
}
