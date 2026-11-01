import React from 'react';
import PointPolicyPage from './management/PointPolicyPage';
import ProgramCreatePage from './management/ProgramCreatePage';
import ProgramDetailPage from './management/ProgramDetailPage';
import ProgramListPage from './management/ProgramListPage';
import PromotionManagementLayout from './management/PromotionManagementLayout';
import MyLoyaltyPage from './member/MyLoyaltyPage';
import SavedVouchersPage from './member/SavedVouchersPage';
import PromotionDetailPage from './public/PromotionDetailPage';
import PromotionListPage from './public/PromotionListPage';
import './loyaltypromotion.css';

// Routes of 006 (rendered inside the app layout by src/app/router.jsx).
export const loyaltyPromotionRoutes = [
  { path: '/promotions', element: <PromotionListPage /> },
  { path: '/promotions/:promotionId', element: <PromotionDetailPage /> },
  { path: '/me/loyalty', element: <MyLoyaltyPage /> },
  { path: '/me/saved-vouchers', element: <SavedVouchersPage /> },
  {
    path: '/promotion-management',
    element: <PromotionManagementLayout />,
    children: [
      { index: true, element: <ProgramListPage /> },
      { path: 'programs', element: <ProgramListPage /> },
      { path: 'programs/new', element: <ProgramCreatePage /> },
      { path: 'programs/:programId', element: <ProgramDetailPage /> },
      { path: 'point-policy', element: <PointPolicyPage /> },
    ],
  },
];
