import React from 'react';
import { RequireSession } from '../../shared/auth/RequireSession';
import PriceProposalAiPage from './pricing/PriceProposalAiPage';
import AiSupportPage from './public/AiSupportPage';
import ReviewDetailPage from './review/ReviewDetailPage';
import ReviewQueuePage from './review/ReviewQueuePage';
import DesignDetailPage from './workspace/DesignDetailPage';
import DesignWorkspacePage from './workspace/DesignWorkspacePage';
import './designreview.css';

// Routes for Feature-004 AI Chatbox Analysis and Suggestion
export const designReviewRoutes = [
  { path: '/ai-support', element: <AiSupportPage /> },
  {
    path: '/design-requests/new',
    element: (
      <RequireSession>
        <DesignWorkspacePage />
      </RequireSession>
    ),
  },
  {
    path: '/design-requests/:designRequestId',
    element: (
      <RequireSession>
        <DesignDetailPage />
      </RequireSession>
    ),
  },
  {
    path: '/design-review-requests',
    element: (
      <RequireSession>
        <ReviewQueuePage />
      </RequireSession>
    ),
  },
  {
    path: '/design-review-requests/:reviewRequestId',
    element: (
      <RequireSession>
        <ReviewDetailPage />
      </RequireSession>
    ),
  },
  {
    path: '/price-proposals/:proposalId/ai-suggestions',
    element: (
      <RequireSession>
        <PriceProposalAiPage />
      </RequireSession>
    ),
  },
];

