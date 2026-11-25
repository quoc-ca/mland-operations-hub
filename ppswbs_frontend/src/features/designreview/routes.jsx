import React from 'react';
import { RequireSession } from '../../shared/auth/RequireSession';
import PriceProposalAiPage from './pricing/PriceProposalAiPage';
import AiSupportPage from './public/AiSupportPage';
import ReviewDetailPage from './review/ReviewDetailPage';
import ReviewQueuePage from './review/ReviewQueuePage';
import DesignDetailPage from './workspace/DesignDetailPage';
import DesignWorkspacePage from './workspace/DesignWorkspacePage';
import MyCustomOrdersPage from './workspace/MyCustomOrdersPage';
import CustomOrderDetailPage from './workspace/CustomOrderDetailPage';
import StaffCustomOrdersPage from './workspace/StaffCustomOrdersPage';
import './designreview.css';

// Routes for Feature-004 Design Request and Custom Manufacturing
export const designReviewRoutes = [
  { path: '/ai-support', element: <AiSupportPage /> },
  {
    path: '/design-requests/new',
    element: <DesignWorkspacePage />,
  },
  {
    path: '/design-requests/:designRequestId',
    element: <DesignDetailPage />,
  },
  {
    path: '/account/my-custom-orders',
    element: (
      <RequireSession>
        <MyCustomOrdersPage />
      </RequireSession>
    ),
  },
  {
    path: '/account/my-custom-orders/:customOrderId',
    element: (
      <RequireSession>
        <CustomOrderDetailPage />
      </RequireSession>
    ),
  },
  {
    path: '/staff/custom-orders',
    element: (
      <RequireSession>
        <StaffCustomOrdersPage />
      </RequireSession>
    ),
  },
  {
    path: '/manager/custom-orders',
    element: (
      <RequireSession>
        <StaffCustomOrdersPage />
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

