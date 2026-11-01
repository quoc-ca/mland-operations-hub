import React from 'react';
import { createBrowserRouter } from 'react-router';
import { loyaltyPromotionRoutes } from '../features/loyaltypromotion/routes';
import { productManagementRoutes } from '../features/productmanagement/routes';
import { workshopRoutes } from '../workshops/routes';
import AuthPage from '../features/account/AuthPage';
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
      { path: '*', element: <NotImplementedPage /> },
    ],
  },
];

export const router = createBrowserRouter(routes);
