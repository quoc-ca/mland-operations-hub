import React from 'react';
import { Navigate } from 'react-router';
import { RequireSession } from '../../shared/auth/RequireSession';
import ReviewsHomePage from './ui/ReviewsHomePage';
import GuestLookupPage from './ui/GuestLookupPage';
import CreateReviewPage from './ui/CreateReviewPage';
import OrderReviewPage from './ui/OrderReviewPage';
import ReviewDetailPage from './ui/ReviewDetailPage';
import EditReviewPage from './ui/EditReviewPage';
import ModerationQueuePage from './ui/ModerationQueuePage';
import ModerationDetailPage from './ui/ModerationDetailPage';
import './reviewmanagement.css';

export const reviewManagementRoutes = [
  { path: '/reviews', element: <ReviewsHomePage /> },
  { path: '/reviews/:reviewId', element: <ReviewDetailPage /> },
  { path: '/reviews/guest/lookup', element: <Navigate to="/reviews" replace /> },
  { path: '/reviews/new', element: <RequireSession><CreateReviewPage /></RequireSession> },
  { path: '/reviews/orders/:orderId', element: <RequireSession><OrderReviewPage /></RequireSession> },
  { path: '/reviews/:reviewId/edit', element: <RequireSession><EditReviewPage /></RequireSession> },
  // The separate "Các sản phẩm, trải nghiệm có thể đánh giá" page was folded into the "Chưa đánh giá" tab (2026-11-23).
  { path: '/reviews/eligible-sources', element: <Navigate to="/reviews" replace /> },
  { path: '/review-moderation', element: <RequireSession><ModerationQueuePage /></RequireSession> },
  { path: '/review-moderation/:reviewId', element: <RequireSession><ModerationDetailPage /></RequireSession> },
];
