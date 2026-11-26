import React from 'react';
import { installStackedTableLabels } from './stackedTables';
import { RoleArea } from '../shared/auth/RoleArea';
import { useT } from '../shared/i18n/I18nProvider';
import { sharedMessages } from '../shared/ui/messages';
import OperationsDashboardPage from './pages/OperationsDashboardPage';
import OperationsSessionPage from './pages/OperationsSessionPage';
import WorkshopPackageManagementPage from './pages/WorkshopPackageManagementPage';
import OperationsAdministrationPage from './pages/OperationsAdministrationPage';
import PackageAddOnCataloguePage from './pages/PackageAddOnCataloguePage';
import PackageAvailabilityMatrixPage from './pages/PackageAvailabilityMatrixPage';
import LatePaymentReviewsPage from './pages/LatePaymentReviewsPage';
import AuditReviewPage from './pages/AuditReviewPage';
installStackedTableLabels();

/**
 * Browser allowlists of 003.2 and 003.3 ("Route masking synchronization"): without a session the page shows a sign-in
 * link back to itself; a signed-in actor outside the set is replaced to /404 before the page renders or calls the API.
 * The backend still decides every permission.
 */
function Area({ roles, children }) {
  const t = useT(sharedMessages);
  return <RoleArea roles={roles} requiredText={t('session.required')}>{children}</RoleArea>;
}

function StaffArea({ children }) {
  return <Area roles={['STAFF', 'MANAGER']}>{children}</Area>;
}

function ManagerArea({ children }) {
  return <Area roles={['MANAGER']}>{children}</Area>;
}

export const operationsRoutes = [
  { path: '/operations/sessions', element: <StaffArea><OperationsDashboardPage /></StaffArea> },
  { path: '/operations/sessions/:sessionId', element: <StaffArea><OperationsSessionPage /></StaffArea> },
  { path: '/operations/packages', element: <StaffArea><WorkshopPackageManagementPage /></StaffArea> },
  { path: '/workshop-packages', element: <StaffArea><WorkshopPackageManagementPage /></StaffArea> },
  { path: '/operations/admin', element: <ManagerArea><OperationsAdministrationPage /></ManagerArea> },
  { path: '/operations/locations', element: <ManagerArea><OperationsAdministrationPage /></ManagerArea> },
  { path: '/operations/slots', element: <ManagerArea><OperationsAdministrationPage /></ManagerArea> },
  { path: '/operations/package-add-ons', element: <StaffArea><PackageAddOnCataloguePage /></StaffArea> },
  { path: '/operations/package-availability', element: <ManagerArea><PackageAvailabilityMatrixPage /></ManagerArea> },
  { path: '/operations/late-payments', element: <ManagerArea><LatePaymentReviewsPage /></ManagerArea> },
  { path: '/operations/audit', element: <ManagerArea><AuditReviewPage /></ManagerArea> },
];

export { StaffArea };
