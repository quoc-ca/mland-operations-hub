import React from 'react';
import { describe, expect, it } from 'vitest';
import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { json, mockFetch, renderRoutes } from '../../test/render';
import { operationsRoutes } from '../routes';

const session = json(200, { id: 'session-1', state: 'IN_PROGRESS', locationId: 4, capacity: 10, assignedStaffAccountIds: [] });
const row = (ticketCode, state, checkedInAt = null, bookingReference = 'BK-A') => ({ ticketCode, bookingReference, ticketSequence: 1, attendanceState: state, checkedInAt });

describe('Attendance roster on the session page', () => {
  it('shows which tickets have checked in and which have not', async () => {
    mockFetch({
      'GET /api/v1/workshop-sessions/session-1': session,
      'GET /api/v1/workshop-sessions/session-1/attendance-roster': json(200, [
        row('TK-1', 'CHECKED_IN', '2026-11-06T02:00:00Z'),
        row('TK-2', 'NOT_CHECKED_IN'),
        row('TK-3', 'NO_SHOW', null, 'BK-B'),
      ]),
    });
    renderRoutes(operationsRoutes, { path: '/operations/sessions/session-1' });

    const panel = within(await screen.findByRole('article', { name: 'Attendance roster' }));
    expect(await panel.findByText('1/3 đã check-in')).toBeInTheDocument();
    expect(panel.getByText('TK-1').closest('.roster-row')).toHaveTextContent('Đã check-in');
    expect(panel.getByText('TK-2').closest('.roster-row')).toHaveTextContent('Chưa check-in');
    expect(panel.getByText('TK-3').closest('.roster-row')).toHaveTextContent('Vắng mặt');
    expect(panel.getByText('BK-B')).toBeInTheDocument();
  });

  it('says so when the session has no valid ticket', async () => {
    mockFetch({
      'GET /api/v1/workshop-sessions/session-1': session,
      'GET /api/v1/workshop-sessions/session-1/attendance-roster': json(200, []),
    });
    renderRoutes(operationsRoutes, { path: '/operations/sessions/session-1' });

    expect(await screen.findByText('Buổi này chưa có vé hợp lệ.')).toBeInTheDocument();
  });

  it('reloads the roster after a check-in so the ticket moves to checked in', async () => {
    let checkedIn = false;
    const calls = mockFetch({
      'GET /api/v1/workshop-sessions/session-1': session,
      'GET /api/v1/workshop-sessions/session-1/attendance-roster': () => json(200, [row('TK-1', checkedIn ? 'CHECKED_IN' : 'NOT_CHECKED_IN', checkedIn ? '2026-11-06T02:00:00Z' : null)]),
      'POST /api/v1/workshop-sessions/session-1/check-ins': () => { checkedIn = true; return json(200, { result: 'CHECKED_IN' }); },
    });
    const user = userEvent.setup();
    renderRoutes(operationsRoutes, { path: '/operations/sessions/session-1' });

    const panel = within(await screen.findByRole('article', { name: 'Attendance roster' }));
    expect(await panel.findByText('Chưa check-in')).toBeInTheDocument();

    await user.type(screen.getAllByPlaceholderText('TK-...')[0], 'TK-1');
    await user.click(screen.getByRole('button', { name: 'Check in' }));

    expect(await panel.findByText('Đã check-in')).toBeInTheDocument();
    expect(calls.filter(call => call.path.endsWith('/attendance-roster')).length).toBeGreaterThanOrEqual(2);
  });
});
