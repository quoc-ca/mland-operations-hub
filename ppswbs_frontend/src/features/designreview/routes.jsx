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
import { MaskAdmin, RoleArea } from '../../shared/auth/RoleArea';

const INTERNAL = ['STAFF', 'MANAGER'];

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
        <MaskAdmin><MyCustomOrdersPage /></MaskAdmin>
      </RequireSession>
    ),
  },
  {
    path: '/account/my-custom-orders/:customOrderId',
    element: (
      <RequireSession>
        <MaskAdmin><CustomOrderDetailPage /></MaskAdmin>
      </RequireSession>
    ),
  },
  {
    path: '/staff/custom-orders',
    element: (
      <RoleArea roles={INTERNAL}>
        <StaffCustomOrdersPage />
      </RoleArea>
    ),
  },
  {
    path: '/manager/custom-orders',
    element: (
      <RoleArea roles={INTERNAL}>
        <StaffCustomOrdersPage />
      </RoleArea>
    ),
  },
  {
    path: '/design-review-requests',
    element: (
      <RoleArea roles={INTERNAL}>
        <ReviewQueuePage />
      </RoleArea>
    ),
  },
  {
    path: '/design-review-requests/:reviewRequestId',
    element: (
      <RoleArea roles={INTERNAL}>
        <ReviewDetailPage />
      </RoleArea>
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

