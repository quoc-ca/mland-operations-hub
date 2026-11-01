import React from 'react';
import { createBrowserRouter } from 'react-router';
import { designReviewRoutes } from '../features/designreview/routes';
import { loyaltyPromotionRoutes } from '../features/loyaltypromotion/routes';
import { productManagementRoutes } from '../features/productmanagement/routes';
import PrototypeApp from '../main';
import AppLayout from './AppLayout';

// Feature routes use real paths inside the app layout; every other path falls back to the hash-based prototype
// screens (e7657ff) until their owners move them into src/features/<feature>/.
export const routes = [
  { element: <AppLayout />, children: [...productManagementRoutes, ...loyaltyPromotionRoutes, ...designReviewRoutes] },
  { path: '*', element: <PrototypeApp /> },
];

export const router = createBrowserRouter(routes);
