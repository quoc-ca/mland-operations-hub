import React from 'react';
import { createBrowserRouter } from 'react-router';
import { accountManagementRoutes } from '../features/accountmanagement';
import { designReviewRoutes } from '../features/designreview/routes';
import { readyRingSalesRoutes } from '../features/readyringsales/routes';
import { loyaltyPromotionRoutes } from '../features/loyalty-promotion';
import { productManagementRoutes } from '../features/product-management';
import { workshopRoutes } from '../workshops/routes';
import { otaConnectivityRoutes } from '../ota-connectivity/routes';
import { operationsRoutes } from '../workshop-operations/routes';
import { settlementRoutes } from '../workshop-settlement/routes';
import HomePage from '../features/landing/HomePage';
import AppLayout from './AppLayout';
import NotImplementedPage from './NotImplementedPage';

export const routes = [
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
      ...designReviewRoutes,
      ...productManagementRoutes,
      ...loyaltyPromotionRoutes,
      { path: '*', element: <NotImplementedPage /> },
    ],
  },
];

export const router = createBrowserRouter(routes);
