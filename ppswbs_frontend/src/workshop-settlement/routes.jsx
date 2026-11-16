import React from 'react';
import { StaffArea } from '../workshop-operations/routes';
import SettlementWorkspacePage from './pages/SettlementWorkspacePage';
import SettlementCasesPage from './pages/SettlementCasesPage';
import CashClosePage from './pages/CashClosePage';
import RefundQueuePage from './pages/RefundQueuePage';
import ChangeRequestsPage from './pages/ChangeRequestsPage';
import TicketActionsPage from './pages/TicketActionsPage';
import RefundAccountPage from './pages/RefundAccountPage';
import MlandRemedyPage from './pages/MlandRemedyPage';
import CustomerFolioPage from './pages/CustomerFolioPage';
import ReconciliationPage from './pages/ReconciliationPage';
import AbsorbedUnderchargesPage from './pages/AbsorbedUnderchargesPage';

// Staff/Manager screens (the backend decides every permission) and the public customer screens of 003.3.
export const settlementRoutes = [
  { path: '/settlement', element: <StaffArea><SettlementWorkspacePage /></StaffArea> },
  { path: '/settlement/cases', element: <StaffArea><SettlementCasesPage /></StaffArea> },
  { path: '/settlement/cash-close', element: <StaffArea><CashClosePage /></StaffArea> },
  { path: '/settlement/refunds', element: <StaffArea><RefundQueuePage /></StaffArea> },
  { path: '/settlement/reconciliation', element: <StaffArea><ReconciliationPage /></StaffArea> },
  { path: '/settlement/absorbed-undercharges', element: <StaffArea><AbsorbedUnderchargesPage /></StaffArea> },
  { path: '/settlement/change-requests', element: <StaffArea><ChangeRequestsPage /></StaffArea> },
  { path: '/settlement/ticket-actions', element: <StaffArea><TicketActionsPage /></StaffArea> },
  // Customer screens: no sign-in is required; a Member is recognized by the session, a Guest by proof in a request body.
  { path: '/refund-account', element: <RefundAccountPage /> },
  { path: '/workshop-tickets/:ticketCode/remedy', element: <MlandRemedyPage /> },
  { path: '/workshop-tickets/:ticketCode/folio', element: <CustomerFolioPage /> },
];
