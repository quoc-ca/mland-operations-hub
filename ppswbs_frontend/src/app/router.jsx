import React from 'react';
import { createBrowserRouter } from 'react-router';
import { designReviewRoutes } from '../features/designreview/routes';
import { loyaltyPromotionRoutes } from '../features/loyaltypromotion/routes';
import { productManagementRoutes } from '../features/productmanagement/routes';
import { workshopRoutes } from '../workshops/routes';
import AuthPage from '../features/account/AuthPage';
import { otaConnectivityRoutes } from '../ota-connectivity/routes';
import { operationsRoutes } from '../workshop-operations/routes';
import { settlementRoutes } from '../workshop-settlement/routes';
import AppLayout from './AppLayout';
import NotImplementedPage from './NotImplementedPage';

export const routes = [
  {
    element: <AppLayout />,
    children: [
      { path: '/sign-in', element: <AuthPage mode="signin" /> },
      { path: '/login', element: <AuthPage mode="signin" /> },
      { path: '/register', element: <AuthPage mode="register" /> },
      { path: '/forgot-password', element: <AuthPage mode="forgot" /> },
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
