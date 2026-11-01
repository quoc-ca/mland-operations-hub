import React from 'react';
import { createBrowserRouter } from 'react-router';
import { designReviewRoutes } from '../features/designreview/routes';
import { loyaltyPromotionRoutes } from '../features/loyaltypromotion/routes';
import { productManagementRoutes } from '../features/productmanagement/routes';
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
      ...productManagementRoutes,
      ...loyaltyPromotionRoutes,
      ...workshopRoutes,
      ...otaConnectivityRoutes,
      ...operationsRoutes,
      ...settlementRoutes,
      ...designReviewRoutes,
      { path: '*', element: <NotImplementedPage /> },
    ],
  },
];

export const router = createBrowserRouter(routes);
