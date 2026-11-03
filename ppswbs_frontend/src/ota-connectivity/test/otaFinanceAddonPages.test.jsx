import React from 'react';
import { describe, expect, it } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { json, mockFetch, renderRoutes } from '../../test/render';
import { otaConnectivityRoutes } from '../routes';

describe('OTA finance and shop add-on screens', () => {
  it('loads separated finance evidence and records a stage', async () => {
    const calls = mockFetch({
      'GET /api/v1/ota-channel-bookings/booking-1/financial-evidence': json(200, { channelBookingId: 'booking-1', customerPaymentState: 'PAID_ON_CHANNEL', payoutState: 'ACCRUED', entries: [] }),
      'POST /api/v1/ota-channel-bookings/booking-1/payout-reconciliation': json(201, { channelBookingId: 'booking-1', payoutState: 'INVOICED', entries: [] }),
    });
    const user = userEvent.setup();
    renderRoutes(otaConnectivityRoutes, { path: '/ota-connectivity/finance' });
    await user.type(screen.getByPlaceholderText('UUID'), 'booking-1');
    await user.click(screen.getByRole('button', { name: 'Load evidence' }));
    expect(await screen.findByText('PAID_ON_CHANNEL')).toBeInTheDocument();
    expect(screen.getByText('ACCRUED')).toBeInTheDocument();
    expect(calls.some(call => call.path.endsWith('/financial-evidence'))).toBe(true);
  });

  it('records a paid shop add-on, acceptance, and direct bill without OTA payment', async () => {
    mockFetch({
      'POST /api/v1/ota-channel-bookings/booking-1/add-on-quotes': json(201, { quoteId: 'quote-1', addonType: 'PAID_OPTION', amount: { amount: '50000', currency: 'VND' }, status: 'QUOTED' }),
      'POST /api/v1/ota-addon-quotes/quote-1/acceptance': json(200, { quoteId: 'quote-1', addonType: 'PAID_OPTION', amount: { amount: '50000', currency: 'VND' }, status: 'ACCEPTED' }),
      'POST /api/v1/ota-addon-quotes/quote-1/direct-bill': json(201, { quoteId: 'quote-1', settlementBillReference: 'BILL-1', correlationId: 'corr-1' }),
    });
    const user = userEvent.setup();
    renderRoutes(otaConnectivityRoutes, { path: '/ota-connectivity/add-ons' });
    await user.type(screen.getByPlaceholderText('UUID'), 'booking-1');
    await user.selectOptions(screen.getByLabelText('Add-on type'), 'PAID_OPTION');
    await user.type(screen.getByLabelText('Item code'), 'BOX');
    await user.type(screen.getByLabelText('Description'), 'Gift box');
    await user.type(screen.getByLabelText('Amount (VND)'), '50000');
    await user.click(screen.getByRole('button', { name: 'Create quote' }));
    expect(await screen.findByText('QUOTED')).toBeInTheDocument();
    await user.type(screen.getByPlaceholderText('Signed at counter'), 'COUNTER-1');
    await user.click(screen.getByRole('button', { name: 'Record acceptance' }));
    expect(await screen.findByText('ACCEPTED')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Create direct Mland bill' }));
    expect(await screen.findByText(/BILL-1/)).toBeInTheDocument();
  });
});
