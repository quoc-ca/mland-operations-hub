import React from 'react';
import RouteAccessGuard from './RouteAccessGuard';

export interface AdminRouteGuardProps {
  children?: React.ReactNode;
}

/**
 * AdminRouteGuard (R-404 / FR-046 / T074).
 * Delegates to RouteAccessGuard with ADMIN role allowlist.
 * Non-ADMIN actors and inactive accounts are masked and replaced to /404.
 */
export const AdminRouteGuard: React.FC<AdminRouteGuardProps> = ({ children }) => {
  return <RouteAccessGuard allowedRoles={['ADMIN']}>{children}</RouteAccessGuard>;
};

export default AdminRouteGuard;
