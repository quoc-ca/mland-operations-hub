import React from 'react';
import { json } from '../../../../test/render';
import { managerDashboardRoutes } from '../../routes';
import { managerHomeRoutes } from '../routes';

export const routes = [
  ...managerHomeRoutes,
  ...managerDashboardRoutes,
  { path: '/staff/home', element: <p>Staff home page</p> },
  { path: '/', element: <p>General home</p> },
  { path: '/admin/accounts', element: <p>Admin accounts</p> },
  { path: '/operations/audit', element: <p>Audit page</p> },
];

export const profile = (role = 'MANAGER') => json(200, { role, fullName: 'Quản lý', email: 'm@mland.test' });

/** Handlers for every read of the dashboard page; a test overrides the ones it cares about. */
export function baseHandlers(overrides = {}) {
  return {
    'GET /api/v1/accounts/me/profile': profile(),
    'GET /api/v1/workshop-day-summaries': json(200, { date: '2026-11-18', sessions: 2, cancelledSessions: 1, checkedIn: 1, expected: 3 }),
    'GET /api/v1/workshop-audit/settlement-records': json(200, { items: [{ id: 5, occurredAt: '2026-11-18T02:00:00Z', actor: { accountId: 77 }, actionCode: 'DENIED_MANAGER_REQUIRED', reasonText: 'private note' }] }),
    'GET /api/v1/workshop-sessions': json(200, [{ id: 's1', state: 'PUBLISHED' }, { id: 's2', state: 'CANCELLED' }]),
    'GET /api/v1/workshop-locations': json(200, [{ id: 'l1', name: 'Hà Nội', active: true }, { id: 'l2', name: 'Đà Nẵng', active: true }]),
    'GET /api/v1/executive-reports': json(200, { status: 'COMPLETE', scope: { periodType: 'MONTH', locationIds: [] }, totals: { totalRevenue: '12500000' }, branches: [], quality: [], developmentDemo: false }),
    'GET /api/v1/executive-reports/trends': json(200, { granularity: 'DAY', points: [], current: {}, previous: {}, unavailable: [] }),
    'GET /api/v1/workshop-audit/operations-events': call => json(200, {
      items: call.query.outcome === 'DENIED'
        ? [{ id: 'e1', occurredAt: '2026-11-18T03:00:00Z', actor: { accountId: 33, displayName: 'Staff A' }, action: 'PACKAGE_AVAILABILITY_MATRIX_READ', reason: 'secret reason', metadata: { x: 1 } }]
        : [{ id: 'e2', occurredAt: '2026-11-18T04:00:00Z', actor: { accountId: 1 }, action: 'WORKSHOP_PACKAGE_AVAILABILITY_CHANGED' }],
    }),
    ...overrides,
  };
}
