import React from 'react';
import { beforeEach, describe, expect, it } from 'vitest';
import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { json, mockFetch, renderRoutes } from '../../test/render';
import { clearRoleCache } from '../../shared/auth/useRole';
import { operationsRoutes } from '../routes';

// The staff assignment panel is a Manager panel: it appears once the profile says the viewer is a Manager.
const asManager = handlers => mockFetch({ 'GET /api/v1/accounts/me/profile': json(200, { role: 'MANAGER' }), ...handlers });
beforeEach(() => clearRoleCache());

const session = assigned => json(200, { id: 'session-1', state: 'IN_PROGRESS', locationId: 4, capacity: 10, assignedStaffAccountIds: assigned });
const staffList = json(200, [
  { accountId: 33, fullName: 'Local Test Staff', loginEmail: 'test.staff@mland.local' },
  { accountId: 41, fullName: 'Second Staff', loginEmail: 'second@mland.local' },
]);

describe('Assigned Staff on the session page', () => {
  it('lists every assigned Staff, and the session facts only count them', async () => {
    asManager({
      'GET /api/v1/workshop-sessions/session-1': session([33, 41]),
      'GET /api/v1/accounts': staffList,
    });
    renderRoutes(operationsRoutes, { path: '/operations/sessions/session-1' });

    expect(await screen.findByText('2 Staff')).toBeInTheDocument();
    const list = within(await screen.findByRole('region', { name: 'Staff đang phụ trách' }));
    expect(await list.findByText(/Second Staff/)).toBeInTheDocument();
    expect(list.getByText(/Local Test Staff/)).toBeInTheDocument();
    expect(list.getAllByRole('button', { name: 'Remove' })).toHaveLength(2);
    expect(screen.queryByText('Operational lead')).not.toBeInTheDocument();
  });

  it('removes one Staff from the list without touching the others', async () => {
    const calls = asManager({
      'GET /api/v1/workshop-sessions/session-1': session([33, 41]),
      'GET /api/v1/accounts': staffList,
      'DELETE /api/v1/workshop-sessions/session-1/staff-assignments': json(204),
    });
    const user = userEvent.setup();
    renderRoutes(operationsRoutes, { path: '/operations/sessions/session-1' });

    const list = within(await screen.findByRole('region', { name: 'Staff đang phụ trách' }));
    const secondRow = (await list.findByText(/Second Staff/)).closest('li');
    await user.click(within(secondRow).getByRole('button', { name: 'Remove' }));

    const removal = calls.find(call => call.method === 'DELETE');
    expect(removal.query).toEqual({ accountId: '41' });
  });

  it('says so when nobody is assigned', async () => {
    asManager({ 'GET /api/v1/workshop-sessions/session-1': session([]) });
    renderRoutes(operationsRoutes, { path: '/operations/sessions/session-1' });

    expect(await screen.findByText('Chưa phân công')).toBeInTheDocument();
    expect(screen.getByText('Unassigned')).toBeInTheDocument();
  });
});
