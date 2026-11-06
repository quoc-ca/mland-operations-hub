import React from 'react';
import { render, screen, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';
import { json, mockFetch } from '../test/render';
import { WorkshopAccessPage } from './WorkshopAccessPage';

const EXCHANGE = 'POST /api/v1/booking-magic-links/exchanges';
const booking = { bookingCode: 'BK-1', state: 'CONFIRMED', tickets: [] };

afterEach(() => { window.history.replaceState(null, '', '/'); });

describe('booking access by Magic Link', () => {
  it('exchanges the token from an email link once (even when the page effect runs twice) and cleans the address', async () => {
    window.history.replaceState(null, '', '/workshop-booking/access#c4c9404b-473c-4880-aa4c-4ad2651ccc31');
    const calls = mockFetch({ [EXCHANGE]: json(200, booking) });
    render(<React.StrictMode><WorkshopAccessPage /></React.StrictMode>);

    await waitFor(() => expect(calls.filter(call => call.path === '/api/v1/booking-magic-links/exchanges')).toHaveLength(1));
    expect(calls.find(call => call.method === 'POST').body).toEqual({ token: 'c4c9404b-473c-4880-aa4c-4ad2651ccc31' });
    expect(screen.queryByText(/Không tìm thấy token/)).toBeNull();
    expect(window.location.hash).toBe('');
    expect(window.location.pathname).toBe('/workshop-booking/access');
  });

  it('says so when the link has no token', async () => {
    window.history.replaceState(null, '', '/workshop-booking/access');
    const calls = mockFetch({});
    render(<WorkshopAccessPage />);

    expect(await screen.findByText(/Không tìm thấy token/)).toBeInTheDocument();
    expect(calls).toHaveLength(0);
  });
});
