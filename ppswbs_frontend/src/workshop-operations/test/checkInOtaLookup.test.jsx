import React from 'react';
import { describe, expect, it } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { json, mockFetch, renderRoutes } from '../../test/render';
import { operationsRoutes } from '../routes';

const session = id => json(200, { id, state: 'IN_PROGRESS', locationId: 4, capacity: 10 });
const tickets = json(200, [{ ticketCode: 'TK-1', participantName: 'Guest', sessions: [] }]);

async function lookUp(user, bookingCode) {
  // The check-in lookup is the first booking-code field on the page (ticket resend has another).
  await user.type(screen.getAllByPlaceholderText('BK-...')[0], bookingCode);
  await user.click(screen.getByRole('button', { name: 'Look up' }));
}

describe('Check-in desk: OTA booking recognition', () => {
  it('shows the OTA badge and an Add-on link prefilled with the channel booking when the booking came through an OTA', async () => {
    const calls = mockFetch({
      'GET /api/v1/workshop-sessions/session-1': session('session-1'),
      'GET /api/v1/workshop-sessions/session-1/booking-lookups/BK-OTA-1': tickets,
      'GET /api/v1/ota-channel-bookings/lookups': json(200, {
        channelBookingId: 'chan-uuid-1', providerCode: 'KLOOK', locationId: '4',
        mlandBookingReference: 'BK-OTA-1', canSellAddon: true,
      }),
    });
    const user = userEvent.setup();
    renderRoutes(operationsRoutes, { path: '/operations/sessions/session-1' });
    await screen.findByText('Check-in desk');

    await lookUp(user, 'BK-OTA-1');

    expect(await screen.findByText('OTA · KLOOK')).toBeInTheDocument();
    const addOn = screen.getByRole('link', { name: 'Add-on' });
    expect(addOn).toHaveAttribute('href', '/ota-connectivity/add-ons?channelBookingId=chan-uuid-1');
    const otaLookup = calls.find(call => call.path === '/api/v1/ota-channel-bookings/lookups');
    expect(otaLookup.query).toEqual({ mlandBookingReference: 'BK-OTA-1' });
  });

  it('shows no OTA badge or Add-on link for a direct Mland booking', async () => {
    mockFetch({
      'GET /api/v1/workshop-sessions/session-1': session('session-1'),
      'GET /api/v1/workshop-sessions/session-1/booking-lookups/BK-DIRECT-1': tickets,
      'GET /api/v1/ota-channel-bookings/lookups': json(404, { code: 'NOT_FOUND', message: 'OTA Channel Booking not found' }),
    });
    const user = userEvent.setup();
    renderRoutes(operationsRoutes, { path: '/operations/sessions/session-1' });
    await screen.findByText('Check-in desk');

    await lookUp(user, 'BK-DIRECT-1');

    expect(await screen.findByText(/Tìm thấy 1 vé cho đơn BK-DIRECT-1/)).toBeInTheDocument();
    expect(screen.queryByText(/^OTA ·/)).not.toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Add-on' })).not.toBeInTheDocument();
  });

  it('keeps check-in usable and shows no OTA badge when the OTA lookup fails', async () => {
    mockFetch({
      'GET /api/v1/workshop-sessions/session-1': session('session-1'),
      'GET /api/v1/workshop-sessions/session-1/booking-lookups/BK-2': tickets,
      'GET /api/v1/ota-channel-bookings/lookups': json(503, { code: 'OTA_UNAVAILABLE', message: 'OTA unavailable' }),
    });
    const user = userEvent.setup();
    renderRoutes(operationsRoutes, { path: '/operations/sessions/session-1' });
    await screen.findByText('Check-in desk');

    await lookUp(user, 'BK-2');

    expect(await screen.findByText(/Tìm thấy 1 vé cho đơn BK-2/)).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Add-on' })).not.toBeInTheDocument();
  });
});
