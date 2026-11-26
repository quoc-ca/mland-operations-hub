import React from 'react';
import { vi, beforeEach, describe, expect, it } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { json, mockFetch, renderRoutes } from '../../test/render';
import { clearRoleCache } from '../../shared/auth/useRole';
import { operationsRoutes } from '../routes';

vi.mock('../../shared/auth/RouteAccessGuard', () => import('../../test/passThroughGuard'));

// 003.2 T127 (Retry-After on the 429), T139 (the two 409 INVALID_STATE reasons) and T140 (the control is locked while
// the request is pending).
const RESEND = 'POST /api/v1/workshop-sessions/session-1/ticket-resends';
const session = () => json(200, { id: 'session-1', state: 'PUBLISHED', locationId: 4, capacity: 10 });
const resendCalls = calls => calls.filter(call => call.method === 'POST' && call.path.endsWith('/ticket-resends'));

beforeEach(() => clearRoleCache());

async function openAndType(user) {
  renderRoutes(operationsRoutes, { language: 'en', path: '/operations/sessions/session-1' });
  await user.type(await screen.findByPlaceholderText('GRP-… (Booking code)'), 'BK-1');
  return screen.getByRole('button', { name: 'Resend ticket' });
}

describe('Ticket resend refusals', () => {
  it('shows the wait from Retry-After and suggests the TICKET_REDELIVERY_UNAVAILABLE override on 429', async () => {
    const calls = mockFetch({
      'GET /api/v1/workshop-sessions/session-1': session(),
      [RESEND]: json(429, { code: 'REDELIVERY_THROTTLED', detail: 'limit', correlationId: 'c-1' }, { 'Retry-After': '421' }),
    });
    const user = userEvent.setup();
    await user.click(await openAndType(user));

    const alert = await screen.findByText(/try again in 421 seconds/);
    expect(alert).toHaveTextContent('TICKET_REDELIVERY_UNAVAILABLE');
    expect(resendCalls(calls)).toHaveLength(1);
    // The generic notice does not repeat it, and the button is usable again.
    expect(screen.getAllByRole('alert')).toHaveLength(1);
    expect(screen.getByRole('button', { name: 'Resend ticket' })).toBeEnabled();
  });

  it('omits the wait when the 429 carries no usable Retry-After', async () => {
    mockFetch({
      'GET /api/v1/workshop-sessions/session-1': session(),
      [RESEND]: json(429, { code: 'REDELIVERY_THROTTLED' }, { 'Retry-After': 'Wed, 21 Oct 2026 07:28:00 GMT' }),
    });
    const user = userEvent.setup();
    await user.click(await openAndType(user));

    const alert = await screen.findByText(/3 times in 15 minutes/);
    expect(alert).not.toHaveTextContent('seconds');
    expect(alert).toHaveTextContent('TICKET_REDELIVERY_UNAVAILABLE');
  });

  it.each([
    ['NO_ELIGIBLE_TICKET', /no valid unused ticket/],
    ['REDELIVERY_REJECTED_BY_BOOKING', /refused to resend the tickets/],
  ])('explains the 409 INVALID_STATE reason %s', async (reason, text) => {
    mockFetch({
      'GET /api/v1/workshop-sessions/session-1': session(),
      [RESEND]: json(409, { code: 'INVALID_STATE', reason, detail: 'refused' }),
    });
    const user = userEvent.setup();
    await user.click(await openAndType(user));

    expect(await screen.findByText(text)).toBeInTheDocument();
    expect(screen.getAllByRole('alert')).toHaveLength(1);
  });

  it('keeps the generic notice for any other failure and clears the refusal text on success', async () => {
    let attempt = 0;
    mockFetch({
      'GET /api/v1/workshop-sessions/session-1': session(),
      [RESEND]: () => (++attempt === 1
        ? json(409, { code: 'INVALID_STATE', reason: 'NO_ELIGIBLE_TICKET', detail: 'refused' })
        : new Response(null, { status: 202 })),
    });
    const user = userEvent.setup();
    const button = await openAndType(user);
    await user.click(button);
    expect(await screen.findByText(/no valid unused ticket/)).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Resend ticket' }));
    expect(await screen.findByText('Ticket resend requested.')).toBeInTheDocument();
    expect(screen.queryByText(/no valid unused ticket/)).not.toBeInTheDocument();
  });
});

describe('Operational commands are locked while a request is pending (T140)', () => {
  it('sends one resend request however often the button is clicked before the answer arrives', async () => {
    let release;
    const gate = new Promise(resolve => { release = resolve; });
    const calls = mockFetch({
      'GET /api/v1/workshop-sessions/session-1': session(),
      [RESEND]: async () => { await gate; return new Response(null, { status: 202 }); },
    });
    const user = userEvent.setup();
    const button = await openAndType(user);

    await user.click(button);
    await waitFor(() => expect(screen.getByRole('button', { name: 'Resend ticket' })).toBeDisabled());
    await user.click(screen.getByRole('button', { name: 'Resend ticket' }));
    await user.click(screen.getByRole('button', { name: 'Resend ticket' }));
    release();

    expect(await screen.findByText('Ticket resend requested.')).toBeInTheDocument();
    expect(resendCalls(calls)).toHaveLength(1);
  });

  it('disables every write button of the session screen while one command is pending', async () => {
    let release;
    const gate = new Promise(resolve => { release = resolve; });
    mockFetch({
      'GET /api/v1/workshop-sessions/session-1': json(200, { id: 'session-1', state: 'IN_PROGRESS', locationId: 4, capacity: 10 }),
      'POST /api/v1/workshop-sessions/session-1/attendance-finalizations': async () => { await gate; return json(200, { id: 'session-1', state: 'ATTENDANCE_FINALIZED' }); },
    });
    const user = userEvent.setup();
    renderRoutes(operationsRoutes, { language: 'en', path: '/operations/sessions/session-1' });
    await user.click(await screen.findByRole('button', { name: 'Finalize attendance' }));
    await user.click(screen.getByRole('button', { name: 'Confirm finalization' }));

    await waitFor(() => expect(screen.getByRole('button', { name: 'Finalize attendance' })).toBeDisabled());
    // Typed input alone must not re-enable a command button while the request is pending.
    await user.type(screen.getAllByPlaceholderText('GRP-…-L0-T1')[0], 'TK-1');
    for (const name of ['Override check-in', 'Mark checked-in', 'Reverse check-in']) {
      expect(screen.getByRole('button', { name })).toBeDisabled();
    }
    await user.type(screen.getByPlaceholderText('GRP-… (Booking code)'), 'BK-1');
    expect(screen.getByRole('button', { name: 'Resend ticket' })).toBeDisabled();
    release();
    await waitFor(() => expect(screen.getByRole('button', { name: 'Resend ticket' })).toBeEnabled());
  });
});
