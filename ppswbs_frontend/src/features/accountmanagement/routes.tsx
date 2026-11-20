import React from 'react';
import { Navigate, Outlet, RouteObject } from 'react-router';
import RequireSession from '../../shared/auth/RequireSession';
import RouteAccessGuard from './components/RouteAccessGuard';
import AuthPage from './components/AuthPage';
import ProfilePage from './components/ProfilePage';
import AdminAccountListPage from './components/AdminAccountListPage';
import AdminAccountDetailPage from './components/AdminAccountDetailPage';
import InternalAccountCreatePage from './components/InternalAccountCreatePage';
import RoleCataloguePage from './components/RoleCataloguePage';
import AddressBookPage from './components/AddressBookPage';
import CarrierPortalPage from './components/CarrierPortalPage';

// Self-service allowlists per R-404 / FR-046 / route-access-matrix.md
const PROFILE_ALLOWED_ROLES = ['MEMBER', 'STAFF', 'MANAGER', 'ADMIN'];
const PASSWORD_ALLOWED_ROLES = ['MEMBER', 'STAFF', 'MANAGER', 'ADMIN', 'CARRIER'];
const CARRIER_ALLOWED_ROLES = ['CARRIER'];
const ADMIN_ALLOWED_ROLES = ['ADMIN'];

export const accountManagementRoutes: RouteObject[] = [
  // Compatibility sign-in route (FR-041 / T071: replaces to canonical /login)
  { path: '/sign-in', element: <Navigate to="/login" replace /> },

  // Canonical authentication routes (public)
  { path: '/login', element: <AuthPage mode="signin" /> },
  { path: '/register', element: <AuthPage mode="register" /> },
  { path: '/forgot-password', element: <AuthPage mode="forgot" /> },

  // CARRIER portal route (T087 / R-CAR / R-404)
  {
    element: (
      <RequireSession>
        <RouteAccessGuard allowedRoles={CARRIER_ALLOWED_ROLES}>
          <Outlet />
        </RouteAccessGuard>
      </RequireSession>
    ),
    children: [
      { path: '/carrier', element: <CarrierPortalPage /> },
    ],
  },

  // Password-only self-service routes (available to all 5 roles including CARRIER per R-404)
  {
    element: (
      <RequireSession>
        <RouteAccessGuard allowedRoles={PASSWORD_ALLOWED_ROLES}>
          <Outlet />
        </RouteAccessGuard>
      </RequireSession>
    ),
    children: [
      { path: '/change-password', element: <ProfilePage initialMode="password" /> },
      { path: '/member/change-password', element: <ProfilePage initialMode="password" /> },
      { path: '/security/password', element: <ProfilePage initialMode="password" /> },
    ],
  },

  // Self-service Profile, Address & Email routes (MEMBER, STAFF, MANAGER, ADMIN; CARRIER masked to /404 per R-404)
  {
    element: (
      <RequireSession>
        <RouteAccessGuard allowedRoles={PROFILE_ALLOWED_ROLES}>
          <Outlet />
        </RouteAccessGuard>
      </RequireSession>
    ),
    children: [
      // Personal Profile canonical route & aliases
      { path: '/profile', element: <ProfilePage initialMode="overview" /> },
      { path: '/personal-profile', element: <ProfilePage initialMode="overview" /> },
      { path: '/member/personal-profile', element: <ProfilePage initialMode="overview" /> },
      { path: '/member/profile', element: <ProfilePage initialMode="overview" /> },
      { path: '/me/profile', element: <ProfilePage initialMode="overview" /> },

      // Shipping Address Book canonical route & aliases (WF-09 / US6)
      { path: '/profile/addresses', element: <AddressBookPage /> },
      { path: '/member/addresses', element: <AddressBookPage /> },
      { path: '/addresses', element: <AddressBookPage /> },

      // Edit Profile routes
      { path: '/edit-profile', element: <ProfilePage initialMode="edit" /> },
      { path: '/member/edit-profile', element: <ProfilePage initialMode="edit" /> },

      // Security Email route
      { path: '/security/email', element: <ProfilePage initialMode="email" /> },
    ],
  },

  // Admin Account Management routes (ADMIN only per R-404)
  {
    element: (
      <RequireSession>
        <RouteAccessGuard allowedRoles={ADMIN_ALLOWED_ROLES}>
          <Outlet />
        </RouteAccessGuard>
      </RequireSession>
    ),
    children: [
      { path: '/admin/accounts', element: <AdminAccountListPage /> },
      { path: '/admin/accounts/new', element: <InternalAccountCreatePage /> },
      { path: '/admin/accounts/:accountId', element: <AdminAccountDetailPage /> },
      { path: '/admin/roles', element: <RoleCataloguePage /> },
    ],
  },
];

export default accountManagementRoutes;
