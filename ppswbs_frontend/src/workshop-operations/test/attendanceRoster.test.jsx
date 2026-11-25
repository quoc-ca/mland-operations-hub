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
    renderRoutes(operationsRoutes, { language: 'en', path: '/operations/sessions/session-1' });

    const panel = within(await screen.findByRole('article', { name: 'Attendance roster' }));
    expect(await panel.findByText('1/3 checked in')).toBeInTheDocument();
    expect(panel.getByText('TK-1').closest('.roster-row')).toHaveTextContent('Checked in');
    expect(panel.getByText('TK-2').closest('.roster-row')).toHaveTextContent('Not checked in');
    expect(panel.getByText('TK-3').closest('.roster-row')).toHaveTextContent('No-show');
    expect(panel.getByText('BK-B')).toBeInTheDocument();
  });

  it('links each roster row to the folio of its ticket, where add-ons are sold', async () => {
    mockFetch({
      'GET /api/v1/workshop-sessions/session-1': session,
      'GET /api/v1/workshop-sessions/session-1/attendance-roster': json(200, [
        { ticketCode: 'TK-D0198954', bookingReference: 'GRP-5E849940018F442D8C9C-L1', ticketSequence: 1, attendanceState: 'CHECKED_IN', checkedInAt: new Date().toISOString() },
      ]),
    });
    renderRoutes(operationsRoutes, { language: 'en', path: '/operations/sessions/session-1' });

    const panel = within(await screen.findByRole('article', { name: 'Attendance roster' }));
    const link = await panel.findByRole('link', { name: 'Folio / Add-on' });
    expect(link).toHaveAttribute('href', '/settlement?ticket=GRP-5E849940018F442D8C9C-L1-T1');
    expect(panel.getByText('GRP-5E849940018F442D8C9C-L1-T1')).toBeInTheDocument();
  });

  it('says so when the session has no valid ticket', async () => {
    mockFetch({
      'GET /api/v1/workshop-sessions/session-1': session,
      'GET /api/v1/workshop-sessions/session-1/attendance-roster': json(200, []),
    });
    renderRoutes(operationsRoutes, { language: 'en', path: '/operations/sessions/session-1' });

    expect(await screen.findByText('This session has no valid tickets yet.')).toBeInTheDocument();
  });

  it('reloads the roster after a check-in so the ticket moves to checked in', async () => {
    let checkedIn = false;
    const calls = mockFetch({
      'GET /api/v1/workshop-sessions/session-1': session,
      'GET /api/v1/workshop-sessions/session-1/attendance-roster': () => json(200, [row('TK-1', checkedIn ? 'CHECKED_IN' : 'NOT_CHECKED_IN', checkedIn ? '2026-11-06T02:00:00Z' : null)]),
      'POST /api/v1/workshop-sessions/session-1/check-ins': () => { checkedIn = true; return json(200, { result: 'CHECKED_IN' }); },
    });
    const user = userEvent.setup();
    renderRoutes(operationsRoutes, { language: 'en', path: '/operations/sessions/session-1' });

    const panel = within(await screen.findByRole('article', { name: 'Attendance roster' }));
    expect(await panel.findByText('Not checked in')).toBeInTheDocument();

    await user.type(screen.getAllByPlaceholderText('GRP-…-L0-T1')[0], 'TK-1');
    await user.click(screen.getByRole('button', { name: 'Check in' }));

    expect(await panel.findByText('Checked in')).toBeInTheDocument();
    expect(calls.filter(call => call.path.endsWith('/attendance-roster')).length).toBeGreaterThanOrEqual(2);
  });

  it('T114: the check-in desk is usable while the session is SALES_PAUSED (the screen does not gate by state)', async () => {
    const calls = mockFetch({
      'GET /api/v1/workshop-sessions/session-1': json(200, { id: 'session-1', state: 'SALES_PAUSED', locationId: 4, capacity: 10, assignedStaffAccountIds: [] }),
      'GET /api/v1/workshop-sessions/session-1/attendance-roster': json(200, [row('TK-1', 'NOT_CHECKED_IN', null)]),
      'POST /api/v1/workshop-sessions/session-1/check-ins': json(200, { checkInResult: 'CHECKED_IN' }),
    });
    const user = userEvent.setup();
    renderRoutes(operationsRoutes, { language: 'en', path: '/operations/sessions/session-1' });
    await screen.findByText('Check-in desk');

    await user.type(screen.getAllByPlaceholderText('GRP-…-L0-T1')[0], 'TK-1');
    const button = screen.getByRole('button', { name: 'Check in' });
    expect(button).toBeEnabled();
    await user.click(button);

    expect(await screen.findByText('Check-in recorded.')).toBeInTheDocument();
    const post = calls.find(call => call.method === 'POST' && call.path.endsWith('/check-ins'));
    expect(post.body).toEqual({ ticketCode: 'TK-1' });
  });

  it('says a repeat scan is already checked in instead of reporting a new check-in', async () => {
    mockFetch({
      'GET /api/v1/workshop-sessions/session-1': session,
      'GET /api/v1/workshop-sessions/session-1/attendance-roster': json(200, [row('TK-1', 'CHECKED_IN', '2026-11-06T02:00:00Z')]),
      'POST /api/v1/workshop-sessions/session-1/check-ins': json(200, { checkInResult: 'ALREADY_CHECKED_IN', checkedInAt: '2026-11-06T02:00:00Z' }),
    });
    const user = userEvent.setup();
    renderRoutes(operationsRoutes, { language: 'en', path: '/operations/sessions/session-1' });
    await screen.findByText('Check-in desk');

    await user.type(screen.getAllByPlaceholderText('GRP-…-L0-T1')[0], 'TK-1');
    await user.click(screen.getByRole('button', { name: 'Check in' }));

    expect(await screen.findByText(/^Ticket was already checked in at .*2026/)).toBeInTheDocument();
    expect(screen.queryByText('Check-in recorded.')).not.toBeInTheDocument();
  });
});
