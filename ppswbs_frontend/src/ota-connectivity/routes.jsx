import React from 'react';
import OtaChannelConfigurationPage from './pages/OtaChannelConfigurationPage';
import OtaReconciliationPage from './pages/OtaReconciliationPage';
import OtaFinanceReconciliationPage from './pages/OtaFinanceReconciliationPage';
import OtaAddonPage from './pages/OtaAddonPage';
import { RouteAccessGuard } from '../shared/auth/RouteAccessGuard';

// 003.4 FR-004b: channel, reconciliation and finance are Manager only; shop add-ons are Staff and Manager.
// The backend decides every permission; the guard only avoids showing a page that would be refused.
const guard = (roles, page) => <RouteAccessGuard allowedRoles={roles}>{page}</RouteAccessGuard>;
const MANAGER = ['MANAGER'];

export const otaConnectivityRoutes = [
  {
    path: 'ota-connectivity/channels',
    element: guard(MANAGER, <OtaChannelConfigurationPage />),
  },
  {
    path: 'ota-connectivity/reconciliation',
    element: guard(MANAGER, <OtaReconciliationPage />),
  },
  {
    path: 'ota-connectivity/finance',
    element: guard(MANAGER, <OtaFinanceReconciliationPage />),
  },
  {
    path: 'ota-connectivity/add-ons',
    element: guard(['STAFF', 'MANAGER'], <OtaAddonPage />),
  },
];
