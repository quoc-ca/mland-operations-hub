import React from 'react';
import { StaffArea } from '../workshop-operations/routes';
import SettlementWorkspacePage from './pages/SettlementWorkspacePage';
import SettlementCasesPage from './pages/SettlementCasesPage';
export const settlementRoutes = [
  { path: '/settlement', element: <StaffArea><SettlementWorkspacePage /></StaffArea> },
  { path: '/settlement/cases', element: <StaffArea><SettlementCasesPage /></StaffArea> },
];
