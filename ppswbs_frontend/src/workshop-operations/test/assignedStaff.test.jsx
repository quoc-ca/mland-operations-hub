import React from 'react';
import { vi, beforeEach, describe, expect, it } from 'vitest';
import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { json, mockFetch, renderRoutes } from '../../test/render';
import { clearRoleCache } from '../../shared/auth/useRole';
import { operationsRoutes } from '../routes';

vi.mock('../../shared/auth/RouteAccessGuard', () => import('../../test/passThroughGuard'));

// The staff assignment panel is a Manager panel: it appears once the profile says the viewer is a Manager.
const asManager = handlers => mockFetch({ 'GET /api/v1/accounts/me/profile': json(200, { role: 'MANAGER' }), ...handlers });
beforeEach(() => clearRoleCache());

const session = assigned => json(200, { id: 'session-1', state: 'IN_PROGRESS', locationId: 4, capacity: 10, assignedStaffAccountIds: assigned });
const staffList = json(200, { data: [
  { accountId: 33, fullName: 'First Staff' },
  { accountId: 41, fullName: 'Second Staff' },
  { accountId: 52, fullName: 'Free Staff' },
] });

describe('Assigned Staff on the session page', () => {
  it('lists every assigned Staff, and the session facts only count them', async () => {
    asManager({
      'GET /api/v1/workshop-sessions/session-1': session([33, 41]),
      'GET /api/v1/accounts': staffList,
    });
    renderRoutes(operationsRoutes, { language: 'en', path: '/operations/sessions/session-1' });

    expect(await screen.findByText('2 Staff')).toBeInTheDocument();
    const list = within(await screen.findByRole('region', { name: 'Staff in charge' }));
    expect(await list.findByText(/Second Staff/)).toBeInTheDocument();
    expect(list.getByText(/First Staff/)).toBeInTheDocument();
    expect(list.getAllByRole('button', { name: 'Remove' })).toHaveLength(2);
    expect(screen.queryByText('Operational lead')).not.toBeInTheDocument();
    // The Manager list carries no email: the picker shows the name and Account ID, without those already assigned.
    expect(screen.getByRole('option', { name: /^Free Staff — .*52/ })).toBeInTheDocument();
    expect(screen.queryByRole('option', { name: /Second Staff|First Staff/ })).not.toBeInTheDocument();
    // Removing is done on each person only.
    expect(screen.getAllByRole('button', { name: 'Remove' })).toHaveLength(2);
  });

  it('removes one Staff from the list without touching the others', async () => {
    const calls = asManager({
      'GET /api/v1/workshop-sessions/session-1': session([33, 41]),
      'GET /api/v1/accounts': staffList,
      'DELETE /api/v1/workshop-sessions/session-1/staff-assignments': json(204),
    });
    const user = userEvent.setup();
    renderRoutes(operationsRoutes, { language: 'en', path: '/operations/sessions/session-1' });

    const list = within(await screen.findByRole('region', { name: 'Staff in charge' }));
    const secondRow = (await list.findByText(/Second Staff/)).closest('li');
    await user.click(within(secondRow).getByRole('button', { name: 'Remove' }));

    const removal = calls.find(call => call.method === 'DELETE');
    expect(removal.query).toEqual({ accountId: '41' });
  });

  it('reports a failed Staff list instead of offering a stand-in account', async () => {
    asManager({
      'GET /api/v1/workshop-sessions/session-1': session([]),
      'GET /api/v1/accounts': json(500, { code: 'INTERNAL_ERROR' }),
    });
    renderRoutes(operationsRoutes, { language: 'en', path: '/operations/sessions/session-1' });

    expect(await screen.findByText(/The Staff list could not be loaded/)).toBeInTheDocument();
    expect(screen.queryByRole('option', { name: /Local Test Staff|33/ })).not.toBeInTheDocument();
  });

  it.each(['ATTENDANCE_FINALIZED', 'CANCELLATION_REQUESTED', 'CANCELLED'])('offers no assignment in %s, but keeps the list', async state => {
    asManager({
      'GET /api/v1/workshop-sessions/session-1': json(200, { id: 'session-1', state, locationId: 4, capacity: 10, assignedStaffAccountIds: [33] }),
      'GET /api/v1/accounts': staffList,
    });
    renderRoutes(operationsRoutes, { language: 'en', path: '/operations/sessions/session-1' });

    expect(await screen.findByText(/No Staff can be assigned/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Remove' })).toBeInTheDocument();
    expect(screen.queryByLabelText(/Select a Staff member/)).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Assign' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Request cancellation' })).not.toBeInTheDocument();
  });

  it('says so when nobody is assigned', async () => {
    asManager({ 'GET /api/v1/workshop-sessions/session-1': session([]) });
    renderRoutes(operationsRoutes, { language: 'en', path: '/operations/sessions/session-1' });

    expect(await screen.findByText('Not assigned yet')).toBeInTheDocument();
    expect(screen.getByText('Unassigned')).toBeInTheDocument();
  });
});
