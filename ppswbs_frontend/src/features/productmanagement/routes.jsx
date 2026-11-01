import React from 'react';
import CategoriesPage from './management/categories/CategoriesPage';
import ImportPage from './management/import/ImportPage';
import ManagementLayout from './management/ManagementLayout';
import ProductCreatePage from './management/ProductCreatePage';
import ProductDetailPage from './management/ProductDetailPage';
import ProductListPage from './management/ProductListPage';
import PublicProductDetailPage from './public/PublicProductDetailPage';
import PublicProductListPage from './public/PublicProductListPage';
import './productmanagement.css';

// Routes of 002 (rendered inside the app layout by src/app/router.jsx).
export const productManagementRoutes = [
  { path: '/products', element: <PublicProductListPage /> },
  { path: '/products/:productId', element: <PublicProductDetailPage /> },
  {
    path: '/catalogue-management',
    element: <ManagementLayout />,
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
