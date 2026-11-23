import React from 'react';
import { RouteAccessGuard } from '../../../shared/auth/RouteAccessGuard';
import CategoriesPage from '../ui/management/categories/CategoriesPage';
import ImportPage from '../ui/management/import/ImportPage';
import ManagementLayout from '../ui/management/ManagementLayout';
import ProductCreatePage from '../ui/management/ProductCreatePage';
import ProductDetailPage from '../ui/management/ProductDetailPage';
import ProductListPage from '../ui/management/ProductListPage';
import PublicProductDetailPage from '../ui/public/PublicProductDetailPage';
import PublicProductListPage from '../ui/public/PublicProductListPage';
import '../ui/product-management.css';

// Routes of 002 (rendered inside the app layout by src/app/router.jsx).
// /catalogue-management/* is a STAFF and MANAGER route family (route-access-matrix.md, FR-002): any other role is
// replaced to /404 before the page renders or calls the API. The backend still decides every permission.
export const productManagementRoutes = [
  { path: '/products', element: <PublicProductListPage /> },
  { path: '/products/:productId', element: <PublicProductDetailPage /> },
  {
    path: '/catalogue-management',
    element: <RouteAccessGuard allowedRoles={['STAFF', 'MANAGER']}><ManagementLayout /></RouteAccessGuard>,
    children: [
      { index: true, element: <ProductListPage /> },
      { path: 'products', element: <ProductListPage /> },
      { path: 'products/new', element: <ProductCreatePage /> },
      { path: 'products/:productId', element: <ProductDetailPage /> },
      { path: 'categories', element: <CategoriesPage /> },
      { path: 'imports', element: <ImportPage /> },
    ],
  },
];
