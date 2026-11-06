import React from 'react';
import { Navigate } from 'react-router';
import { useAuth } from '../../shared/auth/AuthProvider';
import { useHttp } from '../../shared/api/useHttp';
import { roleHomePath } from '../../shared/auth/roleHome';
import { Loading, useResource } from '../../shared/ui/components';
import OperatorHomePage from './OperatorHomePage';

/** `/home` sends a signed-in user to the home page of their role (the sidebar link points here). */
function RoleHomeRedirect() {
  const { status } = useAuth();
  const http = useHttp();
  const profile = useResource(() => (status === 'signed-in' ? http('/api/v1/accounts/me/profile') : Promise.resolve(null)), [status]);
  if (status === 'loading' || profile.loading) return <Loading />;
  return <Navigate to={roleHomePath(profile.data?.role)} replace />;
}

export const operatorHomeRoutes = [
  { path: '/staff/home', element: <OperatorHomePage role="STAFF" /> },
  { path: '/manager/home', element: <OperatorHomePage role="MANAGER" /> },
  { path: '/home', element: <RoleHomeRedirect /> },
];
