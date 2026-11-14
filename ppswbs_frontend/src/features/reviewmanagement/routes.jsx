import React from 'react';
import { RequireSession } from '../../shared/auth/RequireSession';
import PublicReviewListPage from './ui/PublicReviewListPage';
import GuestLookupPage from './ui/GuestLookupPage';
import EligibleSourcesPage from './ui/EligibleSourcesPage';
import CreateReviewPage from './ui/CreateReviewPage';
import ReviewDetailPage from './ui/ReviewDetailPage';
import EditReviewPage from './ui/EditReviewPage';
import ModerationQueuePage from './ui/ModerationQueuePage';
import ModerationDetailPage from './ui/ModerationDetailPage';
import './reviewmanagement.css';

export const reviewManagementRoutes = [
  { path: '/reviews', element: <PublicReviewListPage /> },
  { path: '/reviews/:reviewId', element: <ReviewDetailPage /> },
  { path: '/reviews/guest/lookup', element: <GuestLookupPage /> },
  { path: '/reviews/new', element: <CreateReviewPage /> },
  { path: '/reviews/:reviewId/edit', element: <RequireSession><EditReviewPage /></RequireSession> },
  { path: '/reviews/eligible-sources', element: <RequireSession><EligibleSourcesPage /></RequireSession> },
  { path: '/review-moderation', element: <RequireSession><ModerationQueuePage /></RequireSession> },
  { path: '/review-moderation/:reviewId', element: <RequireSession><ModerationDetailPage /></RequireSession> },
];
