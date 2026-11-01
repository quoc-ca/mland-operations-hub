import React from 'react';
import { createBrowserRouter } from 'react-router';
import { loyaltyPromotionRoutes } from '../features/loyaltypromotion/routes';
import { productManagementRoutes } from '../features/productmanagement/routes';
import AppLayout from './AppLayout';
import NotImplementedPage from './NotImplementedPage';

export const routes = [
  {
    element: <AppLayout />,
    children: [
      ...productManagementRoutes,
      ...loyaltyPromotionRoutes,
      { path: '*', element: <NotImplementedPage /> },
    ],
  },
];

export const router = createBrowserRouter(routes);
