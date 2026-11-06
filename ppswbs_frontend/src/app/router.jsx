import React from 'react';
import { createBrowserRouter } from 'react-router';
import { accountManagementRoutes } from '../features/accountmanagement';
import { designReviewRoutes } from '../features/designreview/routes';
import { readyRingSalesRoutes } from '../features/retail-order';
import { loyaltyPromotionRoutes } from '../features/loyalty-promotion';
import { productManagementRoutes } from '../features/product-management';
import { workshopRoutes } from '../workshops/routes';
import { otaConnectivityRoutes } from '../ota-connectivity/routes';
import { operationsRoutes } from '../workshop-operations/routes';
import { settlementRoutes } from '../workshop-settlement/routes';
import AppLayout from './AppLayout';
import NotImplementedPage from './NotImplementedPage';

export const routes = [
  {
    element: <AppLayout />,
    children: [
      ...accountManagementRoutes,
      ...readyRingSalesRoutes,
      ...workshopRoutes,
      ...otaConnectivityRoutes,
      ...operationsRoutes,
      ...settlementRoutes,
      ...designReviewRoutes,
      { path: '*', element: <NotImplementedPage /> },
    ],
  },
  // 002/006 screens follow Figma page "002-006" (2026-11-03): full-page layouts of their own, without the shared
  // header and footer, so they sit outside AppLayout.
  ...productManagementRoutes,
  ...loyaltyPromotionRoutes,
];

export const router = createBrowserRouter(routes);
