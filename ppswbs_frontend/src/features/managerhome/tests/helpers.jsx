import React from 'react';
import { json } from '../../../test/render';
import { managerHomeRoutes } from '../routes';

export const routes = [
  ...managerHomeRoutes,
  { path: '/staff/home', element: <p>Staff home page</p> },
  { path: '/', element: <p>General home</p> },
  { path: '/manager/dashboard', element: <p>Dashboard page</p> },
  { path: '/operations/audit', element: <p>Audit page</p> },
];

export const profile = (role = 'MANAGER') => json(200, { role, fullName: 'Quản lý', email: 'm@mland.test' });

/** Handlers for every read of wave 1; a test overrides the ones it cares about. */
export function baseHandlers(overrides = {}) {
  return {
    'GET /api/v1/accounts/me/profile': profile(),
    'GET /api/v1/late-payment-reviews': json(200, [{ id: 'a' }, { id: 'b' }, { id: 'c' }]),
    'GET /api/v1/workshop-change-requests': json(200, [{ id: 'x' }, { id: 'y' }]),
    'GET /api/v1/design-review-requests': json(200, { items: [{ id: 'd' }] }),
    'GET /api/v1/workshop-refund-requests/waiting-count': json(200, { waiting: 4 }),
    'GET /api/v1/workshop-cash-closes/awaiting-confirmation-count': json(200, { waiting: 1 }),
    'GET /api/v1/ota-reconciliation-work-items/needing-attention-count': json(200, { waiting: 2 }),
    'GET /api/v1/workshop-day-summaries': json(200, { date: '2026-11-18', sessions: 2, cancelledSessions: 1, checkedIn: 1, expected: 3 }),
    'GET /api/v1/workshop-audit/settlement-records': json(200, { items: [{ id: 5, occurredAt: '2026-11-18T02:00:00Z', actor: { accountId: 77 }, actionCode: 'DENIED_MANAGER_REQUIRED', reasonText: 'private note' }] }),
    'GET /api/v1/workshop-sessions': json(200, [{ id: 's1', state: 'PUBLISHED' }, { id: 's2', state: 'CANCELLED' }]),
    'GET /api/v1/workshop-locations': json(200, [{ id: 'l1', name: 'Hà Nội', active: true }, { id: 'l2', name: 'Đà Nẵng', active: true }]),
    'GET /api/v1/executive-reports': json(200, { status: 'COMPLETE', totals: { totalRevenue: '12500000' }, developmentDemo: false }),
    'GET /api/v1/workshop-audit/operations-events': call => json(200, {
      items: call.query.outcome === 'DENIED'
        ? [{ id: 'e1', occurredAt: '2026-11-18T03:00:00Z', actor: { accountId: 33, displayName: 'Staff A' }, action: 'PACKAGE_AVAILABILITY_MATRIX_READ', reason: 'secret reason', metadata: { x: 1 } }]
        : [{ id: 'e2', occurredAt: '2026-11-18T04:00:00Z', actor: { accountId: 1 }, action: 'WORKSHOP_PACKAGE_AVAILABILITY_CHANGED' }],
    }),
    ...overrides,
  };
}
