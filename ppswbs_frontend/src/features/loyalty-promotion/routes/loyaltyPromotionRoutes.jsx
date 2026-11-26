import React from 'react';
import { RouteAccessGuard } from '../../../shared/auth/RouteAccessGuard';
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
import { MemberOnly } from '../../../shared/auth/RoleArea';

// Routes of 006 (rendered inside the app layout by src/app/router.jsx).
// /me/loyalty and /me/saved-vouchers read Member-only APIs: a signed-in Staff, Manager, Admin or Carrier is replaced to
// /404 before the page calls them (Session 2026-11-26, route-access-matrix); a Guest keeps the page's sign-in prompt.
// /promotion-management/* is a MANAGER-only route family: any other role is replaced to /404 before the page renders
// or calls the API (spec "Route masking synchronization (2026-11-20)"). The backend still decides every permission.
export const loyaltyPromotionRoutes = [
  { path: '/promotions', element: <PromotionListPage /> },
  { path: '/promotions/:promotionId', element: <PromotionDetailPage /> },
  { path: '/me/loyalty', element: <MemberOnly><MyLoyaltyPage /></MemberOnly> },
  { path: '/me/saved-vouchers', element: <MemberOnly><SavedVouchersPage /></MemberOnly> },
  {
    path: '/promotion-management',
    element: <RouteAccessGuard allowedRoles={['MANAGER']}><PromotionManagementLayout /></RouteAccessGuard>,
    children: [
      { index: true, element: <ProgramListPage /> },
      { path: 'programs', element: <ProgramListPage /> },
      { path: 'programs/new', element: <ProgramCreatePage /> },
      { path: 'programs/:programId', element: <ProgramDetailPage /> },
      { path: 'point-policy', element: <PointPolicyPage /> },
    ],
  },
];
