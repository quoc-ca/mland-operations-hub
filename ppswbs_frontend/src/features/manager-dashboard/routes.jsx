import React from 'react';
import { RoleArea } from '../../shared/auth/RoleArea';
import { useT } from '../../shared/i18n/I18nProvider';
import ManagerDashboardPage from './ManagerDashboardPage';
import { managerDashboardMessages } from './messages';

/** Manager-only browser route (010 "Route masking synchronization", 014): everyone else signed in is replaced to /404. */
export function ManagerOnly({ children }) {
  const t = useT(managerDashboardMessages);
  return <RoleArea roles={['MANAGER']} requiredText={t('page.signIn')}>{children}</RoleArea>;
}

export const managerDashboardRoutes = [
  { path: '/manager/dashboard', element: <ManagerOnly><ManagerDashboardPage /></ManagerOnly> },
];
