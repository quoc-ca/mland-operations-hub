import React from 'react';
import { vi, beforeEach, describe, expect, it } from 'vitest';
import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { json, mockFetch, renderRoutes } from '../../test/render';
import { clearRoleCache } from '../../shared/auth/useRole';
import { operationsRoutes } from '../routes';

vi.mock('../../shared/auth/RouteAccessGuard', () => import('../../test/passThroughGuard'));

const INCIDENT = 'POST /api/v1/workshop-booking-groups/GRP-A/magic-link-incidents';

beforeEach(() => clearRoleCache());

const ticket = (ticketCode, bookingReference, ticketSequence) => ({ ticketCode, bookingReference, ticketSequence, attendanceState: 'NOT_CHECKED_IN', checkedInAt: null });

function open(rows, handlers = {}) {
  const calls = mockFetch({
    'GET /api/v1/accounts/me/profile': json(200, { role: 'MANAGER' }),
    'GET /api/v1/workshop-sessions/session-1': json(200, { id: 'session-1', state: 'PUBLISHED', locationId: 4, capacity: 10, assignedStaffAccountIds: [] }),
    'GET /api/v1/workshop-sessions/session-1/attendance-roster': json(200, rows),
    ...handlers,
  });
  renderRoutes(operationsRoutes, { language: 'en', path: '/operations/sessions/session-1' });
  return calls;
}

describe('Booking recovery: revoke or rotate the magic link', () => {
  it.each([
    ['REVOKE', 'Revoke magic link', 'Confirm revocation', 'was revoked'],
    ['ROTATE', 'Rotate magic link', 'Confirm rotation', 'was rotated'],
  ])('asks before %s and reports the outcome in words', async (action, button, confirm, done) => {
    const calls = open([ticket('TK-1', 'GRP-A-L0', 1)], {
      [INCIDENT]: json(200, { groupCode: 'GRP-A', action: `MAGIC_LINK_${action}`, outcome: `${action}D`, correlationId: 'c-1' }),
    });
    const user = userEvent.setup();
    const picker = await screen.findByLabelText('Booking code to handle *');
    await screen.findByRole('option', { name: 'GRP-A' });
    expect(screen.getByRole('button', { name: button })).toBeDisabled();
    await user.selectOptions(picker, 'GRP-A');
    const sent = () => calls.filter(call => call.path.endsWith('/magic-link-incidents'));

    await user.click(screen.getByRole('button', { name: button }));
    await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Cancel' }));
    expect(sent()).toHaveLength(0);

    await user.click(screen.getByRole('button', { name: button }));
    await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: confirm }));

    expect(await screen.findByText(new RegExp(`GRP-A ${done}`))).toBeInTheDocument();
    expect(sent()).toHaveLength(1);
    expect(sent()[0].body.action).toBe(action);
    expect(screen.queryByText(/correlationId|MAGIC_LINK_/)).not.toBeInTheDocument();
  });

  it('offers only the magic link reasons and sends the first one by default', async () => {
    const calls = open([ticket('TK-1', 'GRP-A-L0', 1)], { [INCIDENT]: json(200, { groupCode: 'GRP-A' }) });
    const user = userEvent.setup();
    await screen.findByRole('option', { name: 'GRP-A' });
    const reason = screen.getByLabelText('Reason for the action *');
    expect(within(reason).getAllByRole('option').map(option => option.value))
      .toEqual(['LINK_COMPROMISED_SECURITY', 'CUSTOMER_REQUESTED_NEW_LINK', 'OTHER_REQUIRES_NOTE']);
    await user.selectOptions(screen.getByLabelText('Booking code to handle *'), 'GRP-A');
    await user.click(screen.getByRole('button', { name: 'Revoke magic link' }));
    await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Confirm revocation' }));
    await screen.findByText(/GRP-A was revoked/);
    expect(calls.find(call => call.path.endsWith('/magic-link-incidents')).body.reason).toBe('LINK_COMPROMISED_SECURITY');
  });

  it('offers each booking of the roster once and no free text', async () => {
    open([ticket('TK-1', 'GRP-A-L0', 1), ticket('TK-2', 'GRP-A-L1', 1), ticket('TK-3', 'BK-DIRECT')]);
    await screen.findByRole('option', { name: 'GRP-A' });
    const picker = screen.getByLabelText('Booking code to handle *');
    expect(picker.tagName).toBe('SELECT');
    expect(within(picker).getAllByRole('option').map(option => option.textContent)).toEqual(['Choose a booking code', 'GRP-A', 'BK-DIRECT']);
  });

  it('says that an empty roster leaves nothing to choose', async () => {
    open([]);
    expect(await screen.findByText('No booking is on the attendance roster of this session yet.')).toBeInTheDocument();
    expect(screen.getByLabelText('Booking code to handle *')).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Revoke magic link' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Rotate magic link' })).toBeDisabled();
  });

  it('does not call a failed roster read an empty roster', async () => {
    open([], { 'GET /api/v1/workshop-sessions/session-1/attendance-roster': json(503, { code: 'UNAVAILABLE' }) });
    expect(await screen.findByText('The attendance roster could not be loaded, so no booking code can be chosen.')).toBeInTheDocument();
  });
});
