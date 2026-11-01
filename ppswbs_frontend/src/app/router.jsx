import React from 'react';
import { createBrowserRouter } from 'react-router';
import { designReviewRoutes } from '../features/designreview/routes';
import { loyaltyPromotionRoutes } from '../features/loyaltypromotion/routes';
import { productManagementRoutes } from '../features/productmanagement/routes';
import { workshopRoutes } from '../workshops/routes';
import AppLayout from './AppLayout';
import NotImplementedPage from './NotImplementedPage';

export const routes = [
  {
    element: <AppLayout />,
    children: [
      ...productManagementRoutes,
      ...loyaltyPromotionRoutes,
      ...workshopRoutes,
      ...designReviewRoutes,
      { path: '*', element: <NotImplementedPage /> },
    ],
  },
];

export const router = createBrowserRouter(routes);
