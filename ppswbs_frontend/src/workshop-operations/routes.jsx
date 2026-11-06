import React from 'react';
import { RequireSession } from '../shared/auth/RequireSession';
import { useT } from '../shared/i18n/I18nProvider';
import { sharedMessages } from '../shared/ui/messages';
import OperationsDashboardPage from './pages/OperationsDashboardPage';
import OperationsSessionPage from './pages/OperationsSessionPage';
import WorkshopPackageManagementPage from './pages/WorkshopPackageManagementPage';
import OperationsAdministrationPage from './pages/OperationsAdministrationPage';
import PackageAddOnCataloguePage from './pages/PackageAddOnCataloguePage';

/** Staff/Manager area: without a session the page shows a sign-in link back to itself (the backend still decides every permission). */
function StaffArea({ children }) {
  const t = useT(sharedMessages);
  return <RequireSession requiredText={t('session.required')}>{children}</RequireSession>;
}

export const operationsRoutes = [
  { path: '/operations/sessions', element: <StaffArea><OperationsDashboardPage /></StaffArea> },
  { path: '/operations/sessions/:sessionId', element: <StaffArea><OperationsSessionPage /></StaffArea> },
  { path: '/operations/packages', element: <StaffArea><WorkshopPackageManagementPage /></StaffArea> },
  { path: '/workshop-packages', element: <StaffArea><WorkshopPackageManagementPage /></StaffArea> },
  { path: '/operations/admin', element: <StaffArea><OperationsAdministrationPage /></StaffArea> },
  { path: '/operations/locations', element: <StaffArea><OperationsAdministrationPage /></StaffArea> },
  { path: '/operations/slots', element: <StaffArea><OperationsAdministrationPage /></StaffArea> },
  { path: '/operations/package-add-ons', element: <StaffArea><PackageAddOnCataloguePage /></StaffArea> },
];

export { StaffArea };
