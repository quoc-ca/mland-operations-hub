import React from 'react';
import { RouteObject } from 'react-router';
import AuthPage from './components/AuthPage';
import ProfilePage from './components/ProfilePage';
import AdminAccountListPage from './components/AdminAccountListPage';
import AdminAccountDetailPage from './components/AdminAccountDetailPage';
import InternalAccountCreatePage from './components/InternalAccountCreatePage';
import RoleCataloguePage from './components/RoleCataloguePage';

export const accountManagementRoutes: RouteObject[] = [
  // Authentication routes
  { path: '/sign-in', element: <AuthPage mode="signin" /> },
  { path: '/login', element: <AuthPage mode="signin" /> },
  { path: '/register', element: <AuthPage mode="register" /> },
  { path: '/forgot-password', element: <AuthPage mode="forgot" /> },

  // Personal Profile routes (screen.md: Profile / Personal Profile)
  { path: '/profile', element: <ProfilePage initialMode="overview" /> },
  { path: '/personal-profile', element: <ProfilePage initialMode="overview" /> },
  { path: '/member/personal-profile', element: <ProfilePage initialMode="overview" /> },
  { path: '/member/profile', element: <ProfilePage initialMode="overview" /> },
  { path: '/me/profile', element: <ProfilePage initialMode="overview" /> },

  // Edit Profile routes (screen.md: Edit Profile — form)
  { path: '/edit-profile', element: <ProfilePage initialMode="edit" /> },
  { path: '/member/edit-profile', element: <ProfilePage initialMode="edit" /> },

  // Security & Password routes (screen.md: Change Password — form)
  { path: '/change-password', element: <ProfilePage initialMode="password" /> },
  { path: '/member/change-password', element: <ProfilePage initialMode="password" /> },
  { path: '/security/password', element: <ProfilePage initialMode="password" /> },
  { path: '/security/email', element: <ProfilePage initialMode="email" /> },

  // Admin Account Management routes
  { path: '/admin/accounts', element: <AdminAccountListPage /> },
  { path: '/admin/accounts/new', element: <InternalAccountCreatePage /> },
  { path: '/admin/accounts/:accountId', element: <AdminAccountDetailPage /> },
  { path: '/admin/roles', element: <RoleCataloguePage /> },
];

export default accountManagementRoutes;
