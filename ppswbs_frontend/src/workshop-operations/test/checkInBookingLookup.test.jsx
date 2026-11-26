import React from 'react';
import { vi, describe, expect, it } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { json, mockFetch, renderRoutes } from '../../test/render';
import { operationsRoutes } from '../routes';

vi.mock('../../shared/auth/RouteAccessGuard', () => import('../../test/passThroughGuard'));

describe('Check-in desk: booking lookup', () => {
  it('lists the booking tickets and lets the desk pick one for check-in', async () => {
    mockFetch({
      'GET /api/v1/workshop-sessions/session-1': json(200, { id: 'session-1', state: 'IN_PROGRESS', locationId: 4, capacity: 10 }),
      'GET /api/v1/workshop-sessions/session-1/booking-lookups/BK-1': json(200, [{ ticketCode: 'TK-1', participantName: 'Guest', sessions: [] }]),
    });
    const user = userEvent.setup();
    renderRoutes(operationsRoutes, { language: 'en', path: '/operations/sessions/session-1' });
    await screen.findByText('Check-in desk');

    // The check-in lookup is the first booking-code field on the page (ticket resend has another).
    await user.type(screen.getAllByPlaceholderText('GRP-…')[0], 'BK-1');
    await user.click(screen.getByRole('button', { name: 'Look up' }));

    expect(await screen.findByText(/Found 1 ticket\(s\) for booking BK-1/)).toBeInTheDocument();
    expect(screen.queryByDisplayValue('TK-1')).not.toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Select this ticket' }));
    expect(screen.getAllByDisplayValue('TK-1').length).toBeGreaterThan(0);
  });
});
