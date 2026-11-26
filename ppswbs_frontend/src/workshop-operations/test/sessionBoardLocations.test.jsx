import React from 'react';
import { vi, beforeEach, describe, expect, it } from 'vitest';
import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { json, mockFetch, renderRoutes } from '../../test/render';
import { clearRoleCache } from '../../shared/auth/useRole';
import { operationsRoutes } from '../routes';
import { sessionTimeRange } from '../pages/OperationsDashboardPage';

vi.mock('../../shared/auth/RouteAccessGuard', () => import('../../test/passThroughGuard'));

const SESSIONS = [
  { id: 'aaaaaaaa-1', slotId: 's1', workshopDate: '2026-11-17', locationId: 2, capacity: 8, state: 'PUBLISHED', scheduledStartAt: '2026-11-17T02:30:00Z', scheduledEndAt: '2026-11-17T05:00:00Z' },
  { id: 'bbbbbbbb-2', slotId: 's2', workshopDate: '2026-11-18', locationId: 99, capacity: 8, state: 'PUBLISHED' },
];

describe('session board shows the location name', () => {
  beforeEach(() => clearRoleCache());

  it('names the location in each row, falls back to "Branch #id" for an unknown one, and filters by a dropdown', async () => {
    const calls = mockFetch({
      'GET /api/v1/accounts/me/profile': json(200, { role: 'MANAGER' }),
      'GET /api/v1/workshop-sessions': json(200, SESSIONS),
      'GET /api/v1/workshop-locations': json(200, [{ id: 1, name: 'MLAND Hoàn Kiếm' }, { id: 2, name: 'MLAND Tây Hồ' }]),
    });
    renderRoutes(operationsRoutes, { language: 'en', path: '/operations/sessions?from=' });

    const rows = await screen.findAllByRole('row');
    expect(within(rows[1]).getByText('MLAND Tây Hồ')).toBeInTheDocument();
    expect(within(rows[2]).getByText('Branch #99')).toBeInTheDocument();

    // Two sessions of one day are told apart by their time.
    expect(within(rows[1]).getByText(sessionTimeRange(SESSIONS[0]))).toBeInTheDocument();
    expect(sessionTimeRange(SESSIONS[0])).toMatch(/^\d{2}:\d{2} – \d{2}:\d{2}$/);
    expect(within(rows[2]).getByText('—', { selector: 'strong' })).toBeInTheDocument();

    const select = screen.getByLabelText('Location');
    expect(within(select).getAllByRole('option').map(option => option.textContent)).toEqual(['All locations', 'MLAND Hoàn Kiếm', 'MLAND Tây Hồ']);
    await userEvent.setup().selectOptions(select, 'MLAND Tây Hồ');
    await screen.findAllByRole('row');
    const last = calls.filter(call => call.path === '/api/v1/workshop-sessions').at(-1);
    expect(String(last.query?.locationId ?? last.search ?? '')).toContain('2');
  });
});

describe('Staff sees which locations they work at', () => {
  beforeEach(() => clearRoleCache());

  it('names the Staff member\'s locations under the title', async () => {
    mockFetch({
      'GET /api/v1/accounts/me/profile': json(200, { role: 'STAFF' }),
      'GET /api/v1/workshop-sessions': json(200, []),
      'GET /api/v1/workshop-locations': json(200, [{ id: 1, name: 'MLAND Hoàn Kiếm' }]),
    });
    renderRoutes(operationsRoutes, { language: 'en', path: '/operations/sessions' });
    expect(await screen.findByText('MLAND Hoàn Kiếm', { selector: 'strong' })).toBeInTheDocument();
    expect(screen.getByText(/Your branches:/)).toBeInTheDocument();
  });
});
