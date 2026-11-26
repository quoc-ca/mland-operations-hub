import React from 'react';
import { vi, beforeEach, describe, expect, it } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { json, mockFetch, renderRoutes } from '../../test/render';
import { clearRoleCache } from '../../shared/auth/useRole';
import { operationsRoutes } from '../routes';

vi.mock('../../shared/auth/RouteAccessGuard', () => import('../../test/passThroughGuard'));

// 003.2 T115 (missingStaff + last-assignment confirmation), T130 (STAFF_NOT_ELIGIBLE reasons) and T129 (no Admin
// business authority in the Operations UI). The backend decides every permission; these tests cover what the UI shows.
beforeEach(() => clearRoleCache());

const problem = (status, code, extra = {}) => new Response(
  JSON.stringify({ type: 'about:blank', title: code, status, code, detail: `${code} detail`, ...extra }),
  { status, headers: { 'Content-Type': 'application/problem+json' } },
);
const profile = role => json(200, { role });
const session = (extra = {}) => json(200, {
  id: 'session-1', state: 'IN_PROGRESS', locationId: 4, capacity: 10, assignedStaffAccountIds: [33], missingStaff: false, ...extra,
});
const staffList = json(200, [{ accountId: 33, fullName: 'Local Test Staff', loginEmail: 'test.staff@mland.local' }]);

function openSession(role, handlers) {
  const calls = mockFetch({ 'GET /api/v1/accounts/me/profile': profile(role), 'GET /api/v1/accounts': staffList, ...handlers });
  renderRoutes(operationsRoutes, { language: 'en', path: '/operations/sessions/session-1' });
  return calls;
}

describe('T115 missingStaff warning', () => {
  it('warns a Manager when the session has no Staff after check-in opened', async () => {
    openSession('MANAGER', { 'GET /api/v1/workshop-sessions/session-1': session({ assignedStaffAccountIds: [], missingStaff: true }) });

    expect(await screen.findByText(/no Staff is assigned/)).toBeInTheDocument();
  });

  it('shows no warning while missingStaff is false', async () => {
    openSession('MANAGER', { 'GET /api/v1/workshop-sessions/session-1': session() });

    expect(await screen.findByText('Staff assignment')).toBeInTheDocument();
    expect(screen.queryByText(/no Staff is assigned/)).not.toBeInTheDocument();
  });

  it('does not show the Manager warning to a Staff', async () => {
    openSession('STAFF', { 'GET /api/v1/workshop-sessions/session-1': session({ missingStaff: true }) });

    expect(await screen.findByText('Check-in desk')).toBeInTheDocument();
    await new Promise(resolve => setTimeout(resolve, 50));
    expect(screen.queryByText(/no Staff is assigned/)).not.toBeInTheDocument();
  });
});

describe('T115 last-assignment confirmation', () => {
  const removeRegion = () => screen.findByRole('region', { name: 'Staff in charge' });

  it('asks for confirmation, then repeats the removal with the flag and a new Idempotency-Key', async () => {
    let attempt = 0;
    const calls = openSession('MANAGER', {
      'GET /api/v1/workshop-sessions/session-1': session(),
      'DELETE /api/v1/workshop-sessions/session-1/staff-assignments': () => (++attempt === 1
        ? problem(409, 'LAST_STAFF_ASSIGNMENT_CONFIRMATION_REQUIRED')
        : json(204)),
    });
    const user = userEvent.setup();

    await user.click(within(await removeRegion()).getByRole('button', { name: 'Remove' }));
    const dialog = await screen.findByRole('alertdialog');
    expect(within(dialog).getByText('Remove the last Staff member of the session?')).toBeInTheDocument();
    // The refusal is explained by the dialog, not by a second generic error box.
    expect(screen.queryByText('LAST_STAFF_ASSIGNMENT_CONFIRMATION_REQUIRED detail')).not.toBeInTheDocument();

    await user.click(within(dialog).getByRole('button', { name: 'Confirm removal' }));

    await waitFor(() => expect(calls.filter(call => call.method === 'DELETE')).toHaveLength(2));
    const [first, second] = calls.filter(call => call.method === 'DELETE');
    expect(first.query).toEqual({ accountId: '33' });
    expect(second.query).toEqual({ accountId: '33', confirmLastAssignmentRemoval: 'true' });
    expect(second.headers['Idempotency-Key']).toBeTruthy();
    expect(second.headers['Idempotency-Key']).not.toEqual(first.headers['Idempotency-Key']);
    await waitFor(() => expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument());
  });

  it('sends nothing more when the Manager cancels the confirmation', async () => {
    const calls = openSession('MANAGER', {
      'GET /api/v1/workshop-sessions/session-1': session(),
      'DELETE /api/v1/workshop-sessions/session-1/staff-assignments': () => problem(409, 'LAST_STAFF_ASSIGNMENT_CONFIRMATION_REQUIRED'),
    });
    const user = userEvent.setup();

    await user.click(within(await removeRegion()).getByRole('button', { name: 'Remove' }));
    await user.click(within(await screen.findByRole('alertdialog')).getByRole('button', { name: 'Cancel' }));

    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument();
    expect(calls.filter(call => call.method === 'DELETE')).toHaveLength(1);
  });

  it('does not ask when the removal succeeds at once', async () => {
    openSession('MANAGER', {
      'GET /api/v1/workshop-sessions/session-1': session(),
      'DELETE /api/v1/workshop-sessions/session-1/staff-assignments': json(204),
    });
    const user = userEvent.setup();

    await user.click(within(await removeRegion()).getByRole('button', { name: 'Remove' }));

    expect(await screen.findByText('Staff assignment removed.')).toBeInTheDocument();
    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument();
  });
});

describe('T130 STAFF_NOT_ELIGIBLE messages', () => {
  const MESSAGES = {
    ACCOUNT_NOT_FOUND: 'This account was not found',
    NOT_STAFF: 'is not a Staff member',
    NOT_ACTIVE: 'is no longer active',
    LOCATION_NOT_IN_SCOPE: 'does not belong to the location of the session',
  };

  it.each(Object.entries(MESSAGES))('shows its own message for %s', async (reason, text) => {
    const calls = openSession('MANAGER', {
      'GET /api/v1/workshop-sessions/session-1': session(),
      'PUT /api/v1/workshop-sessions/session-1/staff-assignments': () => problem(409, 'STAFF_NOT_ELIGIBLE', { reason }),
    });
    const user = userEvent.setup();

    await screen.findByText('Staff assignment');
    await user.selectOptions(screen.getByLabelText(/Select a Staff member/), '__OTHER__');
    await user.type(await screen.findByLabelText(/Staff account ID manually/), '77');
    await user.click(screen.getByRole('button', { name: 'Assign' }));

    expect(await screen.findByText(new RegExp(text))).toBeInTheDocument();
    expect(calls.find(call => call.method === 'PUT').body).toEqual({ accountId: 77 });
    expect(screen.queryByText('STAFF_NOT_ELIGIBLE detail')).not.toBeInTheDocument();
  });
});

describe('T129 no Admin business authority', () => {
  const locations = json(200, [{ id: 4, code: 'Q1', name: 'MLAND Quận 1', active: true }]);
  const openAdmin = role => {
    mockFetch({ 'GET /api/v1/accounts/me/profile': profile(role), 'GET /api/v1/workshop-locations': locations });
    renderRoutes(operationsRoutes, { language: 'en', path: '/operations/admin?tab=locations' });
  };

  it('shows the location create action to a Manager', async () => {
    openAdmin('MANAGER');

    expect(await screen.findByRole('button', { name: 'Create location' })).toBeInTheDocument();
  });

  it.each(['ADMIN', 'STAFF'])('hides the location create action from %s', async role => {
    openAdmin(role);

    expect(await screen.findByText('Workshop locations')).toBeInTheDocument();
    await new Promise(resolve => setTimeout(resolve, 50));
    expect(screen.queryByRole('button', { name: 'Create location' })).not.toBeInTheDocument();
  });

  it('shows no Staff assignment or cancellation panel on the session page to an Admin', async () => {
    openSession('ADMIN', { 'GET /api/v1/workshop-sessions/session-1': session() });

    expect(await screen.findByText('Check-in desk')).toBeInTheDocument();
    await new Promise(resolve => setTimeout(resolve, 50));
    for (const hidden of ['Staff assignment', 'Manager booking recovery', 'Cancellation handoff']) {
      expect(screen.queryByText(hidden)).not.toBeInTheDocument();
    }
  });
});
