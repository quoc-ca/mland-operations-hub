import React from 'react';
import { vi, describe, expect, it } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { json, mockFetch, renderRoutes } from '../../test/render';
import { operationsRoutes } from '../routes';

vi.mock('../../shared/auth/RouteAccessGuard', () => import('../../test/passThroughGuard'));

describe('Check-in desk: booking lookup', () => {
  it.each(['DRAFT', 'ATTENDANCE_FINALIZED', 'CANCELLATION_REQUESTED', 'CANCELLED'])('in %s only looks tickets up, with their real status', async state => {
    mockFetch({
      'GET /api/v1/workshop-sessions/session-1': json(200, { id: 'session-1', state, workshopDate: '2026-12-01', locationName: 'MLAND Q1', capacity: 10 }),
      'GET /api/v1/workshop-sessions/session-1/booking-lookups/BK-1': json(200, [{ ticketCode: 'TK-1', attendanceState: 'NO_SHOW' }]),
    });
    const user = userEvent.setup();
    renderRoutes(operationsRoutes, { language: 'en', path: '/operations/sessions/session-1' });

    expect(await screen.findByRole('note')).toHaveTextContent(/check-in/i);
    expect(screen.getByText('MLAND Q1')).toBeInTheDocument();
    expect(screen.queryByText('session-1')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Scan with camera' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Check in' })).not.toBeInTheDocument();
    await user.type(screen.getAllByPlaceholderText('GRP-…')[0], 'BK-1');
    await user.click(screen.getByRole('button', { name: 'Look up' }));

    expect(await screen.findByText('No-show')).toHaveClass('roster-no_show');
    expect(screen.queryByRole('button', { name: 'Select this ticket' })).not.toBeInTheDocument();
  });


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
