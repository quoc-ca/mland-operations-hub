import React from 'react';
import SettlementWorkspacePage from './pages/SettlementWorkspacePage';
import SettlementCasesPage from './pages/SettlementCasesPage';
export const settlementRoutes = [{ path: '/settlement', element: <SettlementWorkspacePage /> }, { path: '/settlement/cases', element: <SettlementCasesPage /> }];
