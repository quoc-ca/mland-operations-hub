import React from 'react';
import { Navigate } from 'react-router';
import { RequireSession } from './RequireSession';
import { RouteAccessGuard } from './RouteAccessGuard';
import { useRole } from './useRole';

/**
 * Internal route family (ppswbs-spec/route-access-matrix.md): without a session the usual sign-in recovery applies; a
 * signed-in actor outside `roles` is replaced to /404 before the page renders. The backend still decides every permission.
 */
export function RoleArea({ roles, requiredText, children }) {
  return (
    <RequireSession requiredText={requiredText}>
      <RouteAccessGuard allowedRoles={roles}>{children}</RouteAccessGuard>
    </RequireSession>
  );
}

/** Member route that Admin may not open (ADMIN is not a Member). Guests and other roles keep the page's own behaviour. */
export function MaskAdmin({ children }) {
  return useRole() === 'ADMIN' ? <Navigate to="/404" replace /> : <>{children}</>;
}
