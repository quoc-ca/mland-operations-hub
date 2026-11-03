import React from 'react';
import PointPolicyPage from '../ui/management/PointPolicyPage';
import ProgramCreatePage from '../ui/management/ProgramCreatePage';
import ProgramDetailPage from '../ui/management/ProgramDetailPage';
import ProgramListPage from '../ui/management/ProgramListPage';
import PromotionManagementLayout from '../ui/management/PromotionManagementLayout';
import MyLoyaltyPage from '../ui/member/MyLoyaltyPage';
import SavedVouchersPage from '../ui/member/SavedVouchersPage';
import PromotionDetailPage from '../ui/public/PromotionDetailPage';
import PromotionListPage from '../ui/public/PromotionListPage';
import '../ui/loyalty-promotion.css';

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
