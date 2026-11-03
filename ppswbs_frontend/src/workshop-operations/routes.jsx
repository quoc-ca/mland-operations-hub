import React from 'react';
import OperationsDashboardPage from './pages/OperationsDashboardPage';
import OperationsSessionPage from './pages/OperationsSessionPage';
import WorkshopPackageManagementPage from './pages/WorkshopPackageManagementPage';
import OperationsAdministrationPage from './pages/OperationsAdministrationPage';
import PackageAddOnCataloguePage from './pages/PackageAddOnCataloguePage';
export const operationsRoutes = [
  { path: '/operations/sessions', element: <OperationsDashboardPage /> },
  { path: '/operations/sessions/:sessionId', element: <OperationsSessionPage /> },
  { path: '/operations/packages', element: <WorkshopPackageManagementPage /> },
  { path: '/workshop-packages', element: <WorkshopPackageManagementPage /> },
  { path: '/operations/admin', element: <OperationsAdministrationPage /> },
  { path: '/operations/locations', element: <OperationsAdministrationPage /> },
  { path: '/operations/slots', element: <OperationsAdministrationPage /> },
  { path: '/operations/package-add-ons', element: <PackageAddOnCataloguePage /> },
];
