import React from 'react';
import OperationsDashboardPage from './pages/OperationsDashboardPage';
import OperationsSessionPage from './pages/OperationsSessionPage';
export const operationsRoutes = [{ path: '/operations/sessions', element: <OperationsDashboardPage /> }, { path: '/operations/sessions/:sessionId', element: <OperationsSessionPage /> }];
