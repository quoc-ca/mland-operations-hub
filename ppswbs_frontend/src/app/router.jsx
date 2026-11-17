import React from 'react';
import { createBrowserRouter } from 'react-router';
import { SessionNavigationBoundary } from '../shared/auth/SessionNavigationBoundary';
import { accountManagementRoutes } from '../features/accountmanagement';
import { designReviewRoutes } from '../features/designreview/routes';
import { readyRingSalesRoutes } from '../features/retail-order';
import { reviewManagementRoutes } from '../features/reviewmanagement/routes';
import { ringDesignConfigurationRoutes } from '../features/ringdesignconfiguration/routes';
import { loyaltyPromotionRoutes } from '../features/loyalty-promotion';
import { productManagementRoutes } from '../features/product-management';
import { workshopRoutes } from '../workshops/routes';
import { otaConnectivityRoutes } from '../ota-connectivity/routes';
import { operationsRoutes } from '../workshop-operations/routes';
import { settlementRoutes } from '../workshop-settlement/routes';
import { operatorHomeRoutes } from '../features/operatorhome/routes';
import { managerDashboardRoutes } from '../features/manager-dashboard';
import { notificationRoutes } from '../features/notification-management';
import HomePage from '../features/landing/HomePage';
import AppLayout from './AppLayout';
import NotImplementedPage from './NotImplementedPage';

export const routes = [
  {
    element: <SessionNavigationBoundary />,
    children: [
      // 01 · Public Landing Page (Figma Page 01 · Public: 402:2821 / 402:2980) có layout Atelier Light hoàn chỉnh với MainTopbar
      { path: '/', element: <HomePage /> },
      {
        element: <AppLayout />,
        children: [
          ...accountManagementRoutes,
          ...readyRingSalesRoutes,
          ...workshopRoutes,
          ...otaConnectivityRoutes,
          ...operationsRoutes,
          ...settlementRoutes,
          ...operatorHomeRoutes,
          ...managerDashboardRoutes,
          ...designReviewRoutes,
          ...reviewManagementRoutes,
          ...ringDesignConfigurationRoutes,
          ...productManagementRoutes,
          ...loyaltyPromotionRoutes,
          ...notificationRoutes,
          { path: '*', element: <NotImplementedPage /> },
        ],
      },
    ],
  },
];

export const router = createBrowserRouter(routes);
