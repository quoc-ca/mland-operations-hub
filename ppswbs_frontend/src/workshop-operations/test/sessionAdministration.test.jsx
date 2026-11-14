import React from 'react';
import { beforeEach, describe, expect, it } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { json, mockFetch, renderRoutes } from '../../test/render';
import { clearRoleCache } from '../../shared/auth/useRole';
import { operationsRoutes } from '../routes';

const SLOT_A = '11111111-1111-1111-1111-111111111111';
const SLOT_B = '22222222-2222-2222-2222-222222222222';
const SESSION = {
  id: 'session-1', locationId: 4, slotId: SLOT_A, workshopDate: '2026-12-05', capacity: 10, state: 'PUBLISHED',
  scheduledStartAt: '2026-12-05T02:00:00Z', scheduledEndAt: '2026-12-05T05:00:00Z',
  checkInOpensAt: '2026-12-05T01:00:00Z', checkInOpeningSource: 'DEFAULT',
  rescheduleQuota: 2, rescheduleQuotaOverridden: false, rescheduleQuotaUsed: 1,
};
const problem = (status, code, detail = '') => new Response(JSON.stringify({ type: 'about:blank', title: code, status, code, detail }), { status, headers: { 'Content-Type': 'application/problem+json' } });

function adminHandlers(role, extra = {}) {
  return {
    'GET /api/v1/accounts/me/profile': json(200, { role }),
    'GET /api/v1/workshop-locations': json(200, [{ id: 4, code: 'Q1', name: 'MLAND Quận 1', active: true }]),
    'GET /api/v1/workshop-locations/4/slots': json(200, [
      { id: SLOT_A, locationId: 4, label: 'Morning', startsAtLocal: '09:00', endsAtLocal: '12:00', defaultCapacity: 10, active: true },
      { id: SLOT_B, locationId: 4, label: 'Afternoon', startsAtLocal: '14:00', endsAtLocal: '17:00', defaultCapacity: 10, active: true },
    ]),
    'GET /api/v1/workshop-sessions': json(200, [SESSION]),
    ...extra,
  };
}

const openAdmin = (role, extra) => {
  const calls = mockFetch(adminHandlers(role, extra));
  renderRoutes(operationsRoutes, { path: '/operations/admin?tab=sessions' });
  return calls;
};

describe('003.2 T108 Manager bulk session generation', () => {
  beforeEach(() => clearRoleCache());

  it('sends the BulkSessionRequest with an Idempotency-Key and shows created and skipped counts', async () => {
    const user = userEvent.setup();
    const calls = openAdmin('MANAGER', {
      'POST /api/v1/workshop-sessions/bulk': json(201, { createdSessionIds: ['s-1', 's-2', 's-3'], skippedDuplicates: ['2026-12-07 Morning'] }),
    });
    const form = await screen.findByRole('form', { name: 'Generate sessions' });
    await user.selectOptions(within(form).getByLabelText('Location'), '4');
    await user.click(await within(form).findByLabelText(/Morning/));
    await user.click(within(form).getByLabelText(/Afternoon/));
    await user.type(within(form).getByLabelText('From'), '2026-12-01');
    await user.type(within(form).getByLabelText('To'), '2026-12-31');
    await user.click(within(form).getByLabelText('Sat'));
    await user.click(within(form).getByLabelText('Sun'));
    await user.selectOptions(within(form).getByLabelText('Initial state'), 'PUBLISHED');
    await user.click(within(form).getByRole('button', { name: 'Generate sessions' }));

    expect(await screen.findByText('3 sessions created; 1 skipped as duplicates.')).toBeInTheDocument();
    expect(within(screen.getByRole('list', { name: 'Skipped duplicates' })).getByText('2026-12-07 Morning')).toBeInTheDocument();
    const post = calls.find(call => call.method === 'POST' && call.path === '/api/v1/workshop-sessions/bulk');
    expect(post.body).toEqual({ locationId: 4, slotIds: [SLOT_A, SLOT_B], from: '2026-12-01', to: '2026-12-31', weekdays: ['SATURDAY', 'SUNDAY'], initialState: 'PUBLISHED' });
    expect(post.headers['Idempotency-Key'].length).toBeGreaterThanOrEqual(16);
    // The session list is read again after generation.
    expect(calls.filter(call => call.method === 'GET' && call.path === '/api/v1/workshop-sessions').length).toBeGreaterThan(1);
  });

  it('refuses an incomplete or reversed range on the screen and sends nothing', async () => {
    const user = userEvent.setup();
    const calls = openAdmin('MANAGER');
    const form = await screen.findByRole('form', { name: 'Generate sessions' });
    await user.click(within(form).getByRole('button', { name: 'Generate sessions' }));
    expect(await screen.findByText(/at least one slot, a date range and at least one weekday/)).toBeInTheDocument();

    await user.selectOptions(within(form).getByLabelText('Location'), '4');
    await user.click(await within(form).findByLabelText(/Morning/));
    await user.type(within(form).getByLabelText('From'), '2026-12-31');
    await user.type(within(form).getByLabelText('To'), '2026-12-01');
    await user.click(within(form).getByLabelText('Mon'));
    await user.click(within(form).getByRole('button', { name: 'Generate sessions' }));
    expect(await screen.findByText('The start date must not be after the end date.')).toBeInTheDocument();
    expect(calls.some(call => call.path === '/api/v1/workshop-sessions/bulk')).toBe(false);
  });

  it('is not offered to a Staff', async () => {
    openAdmin('STAFF');
    expect(await screen.findByText('Session queue')).toBeInTheDocument();
    await new Promise(resolve => setTimeout(resolve, 50));
    expect(screen.queryByRole('form', { name: 'Generate sessions' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Edit' })).not.toBeInTheDocument();
  });
});

describe('003.2 T112 Manager session edit (capacity and check-in opening)', () => {
  beforeEach(() => clearRoleCache());

  it('patches the capacity with the session\'s own location, slot and date and an Idempotency-Key', async () => {
    const user = userEvent.setup();
    const calls = openAdmin('MANAGER', {
      'PATCH /api/v1/workshop-sessions/session-1': json(200, { ...SESSION, capacity: 14 }),
    });
    await user.click(await screen.findByRole('button', { name: 'Edit' }));
    const form = screen.getByRole('form', { name: 'Edit session session-1' });
    await user.clear(within(form).getByLabelText('Capacity'));
    await user.type(within(form).getByLabelText('Capacity'), '14');
    await user.click(within(form).getByRole('button', { name: 'Save session' }));

    expect(await screen.findByText('Session updated.')).toBeInTheDocument();
    const patch = calls.find(call => call.method === 'PATCH');
    expect(patch.body).toEqual({ locationId: 4, slotId: SLOT_A, workshopDate: '2026-12-05', capacity: 14 });
    expect(patch.headers['Idempotency-Key']).toBeTruthy();
  });

  it('sends a changed check-in opening time as an instant', async () => {
    const user = userEvent.setup();
    const calls = openAdmin('MANAGER', {
      'PATCH /api/v1/workshop-sessions/session-1': json(200, SESSION),
    });
    await user.click(await screen.findByRole('button', { name: 'Edit' }));
    const form = screen.getByRole('form', { name: 'Edit session session-1' });
    const opening = within(form).getByLabelText('Check-in opens at');
    await user.clear(opening);
    await user.type(opening, '2026-12-05T07:30');
    await user.click(within(form).getByRole('button', { name: 'Save session' }));

    await waitFor(() => expect(calls.some(call => call.method === 'PATCH')).toBe(true));
    expect(calls.find(call => call.method === 'PATCH').body.checkInOpensAt).toBe(new Date('2026-12-05T07:30').toISOString());
  });

  it('shows the capacity-below-commitments refusal and keeps the typed value', async () => {
    const user = userEvent.setup();
    openAdmin('MANAGER', {
      'PATCH /api/v1/workshop-sessions/session-1': problem(409, 'INSUFFICIENT_CAPACITY', 'Capacity 2 is below 6 committed'),
    });
    await user.click(await screen.findByRole('button', { name: 'Edit' }));
    const form = screen.getByRole('form', { name: 'Edit session session-1' });
    await user.clear(within(form).getByLabelText('Capacity'));
    await user.type(within(form).getByLabelText('Capacity'), '2');
    await user.click(within(form).getByRole('button', { name: 'Save session' }));

    expect(await within(form).findByText(/thấp hơn số vé đã xác nhận/)).toBeInTheDocument();
    expect(within(form).getByLabelText('Capacity')).toHaveValue(2);
  });

  it('shows a stale-version conflict with a button that reloads the session list', async () => {
    const user = userEvent.setup();
    const calls = openAdmin('MANAGER', {
      'PATCH /api/v1/workshop-sessions/session-1': problem(409, 'OPTIMISTIC_LOCK_CONFLICT', 'The record was updated by another transaction. Please retry.'),
    });
    await user.click(await screen.findByRole('button', { name: 'Edit' }));
    const form = screen.getByRole('form', { name: 'Edit session session-1' });
    await user.click(within(form).getByRole('button', { name: 'Save session' }));

    expect(await within(form).findByText(/Dữ liệu đã thay đổi/)).toBeInTheDocument();
    const reads = calls.filter(call => call.method === 'GET' && call.path === '/api/v1/workshop-sessions').length;
    await user.click(within(form).getByRole('button', { name: 'Tải bản mới nhất' }));
    await waitFor(() => expect(calls.filter(call => call.method === 'GET' && call.path === '/api/v1/workshop-sessions').length).toBe(reads + 1));
  });
});

describe('003.2 T113 Manager reschedule quota on the session page', () => {
  beforeEach(() => clearRoleCache());

  const openSession = (role, session = SESSION, extra = {}) => {
    const calls = mockFetch({
      'GET /api/v1/accounts/me/profile': json(200, { role }),
      'GET /api/v1/workshop-sessions/session-1': json(200, session),
      ...extra,
    });
    renderRoutes(operationsRoutes, { path: '/operations/sessions/session-1' });
    return calls;
  };

  it('shows the effective, overridden and used values from the server', async () => {
    openSession('MANAGER');
    const panel = (await screen.findByRole('heading', { name: 'Reschedule quota' })).closest('article');
    expect(within(panel).getByText('Effective quota').nextSibling).toHaveTextContent('2');
    expect(within(panel).getByText('Default (25% of capacity, at least 1)')).toBeInTheDocument();
    expect(within(panel).getByText('Used').nextSibling).toHaveTextContent('1');
    // Restoring the default means nothing while the session already follows it.
    expect(within(panel).getByRole('button', { name: 'Restore default' })).toBeDisabled();
  });

  it('requires a reason, then PUTs quota 0 with the reason and an Idempotency-Key', async () => {
    const user = userEvent.setup();
    const calls = openSession('MANAGER', SESSION, {
      'PUT /api/v1/workshop-sessions/session-1/reschedule-quota': json(200, { ...SESSION, rescheduleQuota: 0, rescheduleQuotaOverridden: true }),
    });
    const panel = (await screen.findByRole('heading', { name: 'Reschedule quota' })).closest('article');
    await user.type(within(panel).getByLabelText('New quota'), '0');
    await user.click(within(panel).getByRole('button', { name: 'Set quota' }));
    expect(await within(panel).findByText('A reason is required to change the reschedule quota.')).toBeInTheDocument();
    expect(calls.some(call => call.method === 'PUT')).toBe(false);

    await user.type(within(panel).getByLabelText('Reason'), 'Lớp kín lịch giáo viên');
    await user.click(within(panel).getByRole('button', { name: 'Set quota' }));
    expect(await within(panel).findByText('Session closed to customer changes.')).toBeInTheDocument();
    const put = calls.find(call => call.method === 'PUT');
    expect(put.path).toBe('/api/v1/workshop-sessions/session-1/reschedule-quota');
    expect(put.body).toEqual({ quota: 0, reason: 'Lớp kín lịch giáo viên' });
    expect(put.headers['Idempotency-Key'].length).toBeGreaterThanOrEqual(16);
  });

  it('restores the default by sending quota null', async () => {
    const user = userEvent.setup();
    const overridden = { ...SESSION, rescheduleQuota: 5, rescheduleQuotaOverridden: true };
    const calls = openSession('MANAGER', overridden, {
      'PUT /api/v1/workshop-sessions/session-1/reschedule-quota': json(200, SESSION),
    });
    const panel = (await screen.findByRole('heading', { name: 'Reschedule quota' })).closest('article');
    expect(within(panel).getByText('Set by a Manager')).toBeInTheDocument();
    await user.type(within(panel).getByLabelText('Reason'), 'Về mặc định');
    await user.click(within(panel).getByRole('button', { name: 'Restore default' }));
    expect(await within(panel).findByText('Reschedule quota restored to the default.')).toBeInTheDocument();
    expect(calls.find(call => call.method === 'PUT').body).toEqual({ quota: null, reason: 'Về mặc định' });
  });

  it('keeps the form when the server refuses, and offers no change on a finalized session', async () => {
    const user = userEvent.setup();
    openSession('MANAGER', SESSION, {
      'PUT /api/v1/workshop-sessions/session-1/reschedule-quota': problem(400, 'VALIDATION_ERROR', 'quota must not be negative'),
    });
    const panel = (await screen.findByRole('heading', { name: 'Reschedule quota' })).closest('article');
    await user.type(within(panel).getByLabelText('New quota'), '3');
    await user.type(within(panel).getByLabelText('Reason'), 'Thử');
    await user.click(within(panel).getByRole('button', { name: 'Set quota' }));
    expect(await within(panel).findByRole('alert')).toBeInTheDocument();
    expect(within(panel).getByLabelText('New quota')).toHaveValue(3);
    expect(within(panel).getByLabelText('Reason')).toHaveValue('Thử');
  });

  it('shows the values but no form once the session is finalized', async () => {
    openSession('MANAGER', { ...SESSION, state: 'ATTENDANCE_FINALIZED' });
    const panel = (await screen.findByRole('heading', { name: 'Reschedule quota' })).closest('article');
    expect(within(panel).getByText(/cannot change once the session is finalized/)).toBeInTheDocument();
    expect(within(panel).queryByRole('button', { name: 'Set quota' })).not.toBeInTheDocument();
  });

  it('is not shown to a Staff', async () => {
    openSession('STAFF');
    expect(await screen.findByText('Check-in desk')).toBeInTheDocument();
    await new Promise(resolve => setTimeout(resolve, 50));
    expect(screen.queryByRole('heading', { name: 'Reschedule quota' })).not.toBeInTheDocument();
  });
});
