import React from 'react';
import { RouteAccessGuard as BaseGuard } from '../../../shared/auth/RouteAccessGuard';
import { AccountRole } from '../api/AccountManagementApiClient';

export interface RouteAccessGuardProps {
  allowedRoles?: (AccountRole | string)[];
  children?: React.ReactNode;
}

export const RouteAccessGuard: React.FC<RouteAccessGuardProps> = ({ allowedRoles, children }) => {
  return <BaseGuard allowedRoles={allowedRoles}>{children}</BaseGuard>;
};

export default RouteAccessGuard;
