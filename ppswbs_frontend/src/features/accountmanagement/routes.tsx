import React from 'react';
import { Navigate, Outlet, RouteObject } from 'react-router';
import RequireSession from '../../shared/auth/RequireSession';
import AuthPage from './components/AuthPage';
import ProfilePage from './components/ProfilePage';
import AdminAccountListPage from './components/AdminAccountListPage';
import AdminAccountDetailPage from './components/AdminAccountDetailPage';
import InternalAccountCreatePage from './components/InternalAccountCreatePage';
import RoleCataloguePage from './components/RoleCataloguePage';
import AddressBookPage from './components/AddressBookPage';
import AdminRouteGuard from './components/AdminRouteGuard';

export const accountManagementRoutes: RouteObject[] = [
  // Compatibility sign-in route (FR-041 / T071: replaces to canonical /login)
  { path: '/sign-in', element: <Navigate to="/login" replace /> },

  // Canonical authentication routes (public)
  { path: '/login', element: <AuthPage mode="signin" /> },
  { path: '/register', element: <AuthPage mode="register" /> },
  { path: '/forgot-password', element: <AuthPage mode="forgot" /> },

  // Guarded Account & Profile canonical routes and compatibility aliases (T071, T072)
  {
    element: (
      <RequireSession>
        <Outlet />
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

      // Security & Password canonical routes & aliases
      { path: '/change-password', element: <ProfilePage initialMode="password" /> },
      { path: '/member/change-password', element: <ProfilePage initialMode="password" /> },
      { path: '/security/password', element: <ProfilePage initialMode="password" /> },
      { path: '/security/email', element: <ProfilePage initialMode="email" /> },
    ],
  },

  // Admin Account Management routes (guarded by RequireSession + AdminRouteGuard)
  {
    element: (
      <RequireSession>
        <AdminRouteGuard />
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
