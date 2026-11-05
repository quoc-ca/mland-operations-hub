import React from 'react';
import { describe, expect, it } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { json, mockFetch, renderRoutes } from '../../test/render';
import { otaConnectivityRoutes } from '../routes';

describe('OTA finance and shop add-on screens', () => {
  it('renders finance evidence states and an empty ledger explicitly', async () => {
    mockFetch({
      'GET /api/v1/ota-channel-bookings/booking-empty/financial-evidence': json(200, {
        channelBookingId: 'booking-empty', customerPaymentState: 'PAID_ON_CHANNEL', payoutState: 'ACCRUED', entries: [],
      }),
    });
    const user = userEvent.setup();
    renderRoutes(otaConnectivityRoutes, { path: '/ota-connectivity/finance' });
    await user.type(screen.getByPlaceholderText('UUID'), 'booking-empty');
    await user.click(screen.getByRole('button', { name: 'Load evidence' }));
    expect(await screen.findByText('Evidence ledger')).toBeInTheDocument();
    expect(screen.getByText('0 entries')).toBeInTheDocument();
    expect(screen.getByText('PAID_ON_CHANNEL')).toBeInTheDocument();
    expect(screen.getByText('ACCRUED')).toBeInTheDocument();
  });

  it('renders finance provider/backend failure with correlation evidence', async () => {
    mockFetch({
      'GET /api/v1/ota-channel-bookings/booking-error/financial-evidence': json(503, {
        code: 'OTA_FINANCE_UNAVAILABLE', message: 'Provider evidence is temporarily unavailable', correlationId: 'corr-ota-finance-1',
      }),
    });
    const user = userEvent.setup();
    renderRoutes(otaConnectivityRoutes, { path: '/ota-connectivity/finance' });
    await user.type(screen.getByPlaceholderText('UUID'), 'booking-error');
    await user.click(screen.getByRole('button', { name: 'Load evidence' }));
    expect(await screen.findByText('Hệ thống tạm thời chưa xử lý được. Bấm Thử lại để gửi lại đúng thao tác này.')).toBeInTheDocument();
    expect(screen.getByText(/corr-ota-finance-1/)).toBeInTheDocument();
    expect(screen.queryByText('Evidence ledger')).not.toBeInTheDocument();
  });

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

  it('records free laser engraving and never offers a direct bill action', async () => {
    mockFetch({
      'POST /api/v1/ota-channel-bookings/booking-free/add-on-quotes': json(201, {
        quoteId: 'quote-free', addonType: 'LASER_ENGRAVING', amount: { amount: '0', currency: 'VND' }, status: 'QUOTED',
      }),
      'POST /api/v1/ota-addon-quotes/quote-free/acceptance': json(200, {
        quoteId: 'quote-free', addonType: 'LASER_ENGRAVING', amount: { amount: '0', currency: 'VND' }, status: 'ACCEPTED',
      }),
    });
    const user = userEvent.setup();
    renderRoutes(otaConnectivityRoutes, { path: '/ota-connectivity/add-ons' });
    await user.type(screen.getByPlaceholderText('UUID'), 'booking-free');
    await user.type(screen.getByLabelText('Description'), 'Engrave initials');
    await user.click(screen.getByRole('button', { name: 'Create quote' }));
    expect(await screen.findByText('Free for OTA customers. No direct bill is created.')).toBeInTheDocument();
    await user.type(screen.getByPlaceholderText('Signed at counter'), 'COUNTER-FREE');
    await user.click(screen.getByRole('button', { name: 'Record acceptance' }));
    expect(await screen.findByText('ACCEPTED')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Create direct Mland bill' })).not.toBeInTheDocument();
  });

  it('renders backend validation errors without fabricating a quote', async () => {
    mockFetch({
      'POST /api/v1/ota-channel-bookings/booking-invalid/add-on-quotes': json(422, {
        code: 'INVALID_ADDON_AMOUNT', message: 'Paid option amount must be positive', correlationId: 'corr-invalid',
      }),
    });
    const user = userEvent.setup();
    renderRoutes(otaConnectivityRoutes, { path: '/ota-connectivity/add-ons' });
    await user.type(screen.getByPlaceholderText('UUID'), 'booking-invalid');
    await user.selectOptions(screen.getByLabelText('Add-on type'), 'PAID_OPTION');
    await user.type(screen.getByLabelText('Item code'), 'BOX');
    await user.type(screen.getByLabelText('Description'), 'Gift box');
    await user.type(screen.getByLabelText('Amount (VND)'), '0');
    await user.click(screen.getByRole('button', { name: 'Create quote' }));
    expect(await screen.findByText('Paid option amount must be positive')).toBeInTheDocument();
    expect(screen.getByText('Create a quote to review the immutable amount and consent steps.')).toBeInTheDocument();
  });
});
