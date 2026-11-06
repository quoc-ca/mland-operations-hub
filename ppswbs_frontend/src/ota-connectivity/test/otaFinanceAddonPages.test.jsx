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
    const calls = mockFetch({
      'POST /api/v1/ota-channel-bookings/booking-1/add-on-quotes': json(201, {
        quoteId: 'quote-1', addonType: 'PAID_OPTION', itemCode: 'addon-ref-1', amount: { amount: '50000', currency: 'VND' }, status: 'QUOTED',
      }),
      'POST /api/v1/ota-addon-quotes/quote-1/acceptance': json(200, {
        quoteId: 'quote-1', addonType: 'PAID_OPTION', itemCode: 'addon-ref-1', amount: { amount: '50000', currency: 'VND' }, status: 'ACCEPTED',
      }),
      'POST /api/v1/ota-addon-quotes/quote-1/direct-bill': json(201, {
        quoteId: 'quote-1', settlementBillReference: 'BILL-1', correlationId: 'corr-1',
      }),
    });
    const user = userEvent.setup();
    renderRoutes(otaConnectivityRoutes, { path: '/ota-connectivity/add-ons' });
    await user.type(screen.getByPlaceholderText('UUID'), 'booking-1');
    await user.selectOptions(screen.getByLabelText('Add-on type'), 'PAID_OPTION');
    await user.type(screen.getByLabelText('Catalogue add-on reference'), 'addon-ref-1');
    await user.click(screen.getByRole('button', { name: 'Create quote' }));
    expect(await screen.findByText('QUOTED')).toBeInTheDocument();
    const quoteCall = calls.find(call => call.path.includes('/add-on-quotes'));
    expect(quoteCall.body).toEqual({
      addonType: 'PAID_OPTION',
      catalogueAddOnReference: 'addon-ref-1',
    });
    await user.type(screen.getByPlaceholderText('Signed at counter'), 'COUNTER-1');
    await user.click(screen.getByRole('button', { name: 'Record acceptance' }));
    expect(await screen.findByText('ACCEPTED')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Create direct Mland bill' }));
    expect(await screen.findByText(/BILL-1/)).toBeInTheDocument();
  });

  it('prefills the channel booking from the check-in Add-on link', async () => {
    mockFetch({});
    renderRoutes(otaConnectivityRoutes, { path: '/ota-connectivity/add-ons?channelBookingId=booking-from-checkin' });
    expect(screen.getByPlaceholderText('UUID')).toHaveValue('booking-from-checkin');
  });

  describe('collecting the paid add-on through VNPay', () => {
    async function billAPaidAddOn(user) {
      await user.type(screen.getByPlaceholderText('UUID'), 'booking-pay');
      await user.selectOptions(screen.getByLabelText('Add-on type'), 'PAID_OPTION');
      await user.type(screen.getByLabelText('Catalogue add-on reference'), 'addon-ref-1');
      await user.click(screen.getByRole('button', { name: 'Create quote' }));
      await screen.findByText('QUOTED');
      await user.type(screen.getByPlaceholderText('Signed at counter'), 'COUNTER-1');
      await user.click(screen.getByRole('button', { name: 'Record acceptance' }));
      await screen.findByText('ACCEPTED');
      await user.click(screen.getByRole('button', { name: 'Create direct Mland bill' }));
      await screen.findByText(/BILL-PAY/);
    }

    const handlers = (status, attemptBody) => ({
      'POST /api/v1/ota-channel-bookings/booking-pay/add-on-quotes': json(201, {
        quoteId: 'quote-pay', addonType: 'PAID_OPTION', itemCode: 'addon-ref-1', amount: { amount: '50000', currency: 'VND' }, status: 'QUOTED',
      }),
      'POST /api/v1/ota-addon-quotes/quote-pay/acceptance': json(200, {
        quoteId: 'quote-pay', addonType: 'PAID_OPTION', itemCode: 'addon-ref-1', amount: { amount: '50000', currency: 'VND' }, status: 'ACCEPTED',
      }),
      'POST /api/v1/ota-addon-quotes/quote-pay/direct-bill': json(201, { quoteId: 'quote-pay', settlementBillReference: 'BILL-PAY', correlationId: 'corr-pay' }),
      'GET /api/v1/ota-addon-bills/BILL-PAY': () => json(200, { billReference: 'BILL-PAY', lifecycleStatus: status.current, refunds: [] }),
      'POST /api/v1/ota-addon-bills/BILL-PAY/payment-attempts': json(201, attemptBody),
    });

    it('creates one idempotent VNPay payment attempt and offers the payment link only while it is valid', async () => {
      const status = { current: 'OPEN' };
      const calls = mockFetch(handlers(status, {
        attemptReference: 'OAA-1', billReference: 'BILL-PAY', amountVnd: 50000, currency: 'VND', status: 'PENDING',
        paymentUrl: 'https://pay.example.invalid/OTA-1', expiresAt: new Date(Date.now() + 10 * 60 * 1000).toISOString(),
      }));
      const user = userEvent.setup();
      renderRoutes(otaConnectivityRoutes, { path: '/ota-connectivity/add-ons' });
      await billAPaidAddOn(user);
      expect(screen.queryByRole('link', { name: 'Mở cổng thanh toán VNPay' })).not.toBeInTheDocument();

      await user.click(screen.getByRole('button', { name: 'Thu tiền (VNPay)' }));

      const link = await screen.findByRole('link', { name: 'Mở cổng thanh toán VNPay' });
      expect(link).toHaveAttribute('href', 'https://pay.example.invalid/OTA-1');
      const attemptCalls = calls.filter(call => call.path === '/api/v1/ota-addon-bills/BILL-PAY/payment-attempts');
      expect(attemptCalls).toHaveLength(1);
      expect(attemptCalls[0].method).toBe('POST');
      expect(attemptCalls[0].headers['Idempotency-Key']).toBeTruthy();
    });

    it('never offers an expired payment link', async () => {
      const status = { current: 'OPEN' };
      mockFetch(handlers(status, {
        attemptReference: 'OAA-2', billReference: 'BILL-PAY', amountVnd: 50000, currency: 'VND', status: 'PENDING',
        paymentUrl: 'https://pay.example.invalid/OTA-2', expiresAt: new Date(Date.now() - 60 * 1000).toISOString(),
      }));
      const user = userEvent.setup();
      renderRoutes(otaConnectivityRoutes, { path: '/ota-connectivity/add-ons' });
      await billAPaidAddOn(user);

      await user.click(screen.getByRole('button', { name: 'Thu tiền (VNPay)' }));

      expect(await screen.findByText('Đã tạo liên kết thanh toán VNPay.')).toBeInTheDocument();
      expect(screen.queryByRole('link', { name: 'Mở cổng thanh toán VNPay' })).not.toBeInTheDocument();
    });

    it('polls the bill until it is PAID, then hides the collect action and stops polling', async () => {
      const status = { current: 'OPEN' };
      const calls = mockFetch(handlers(status, {
        attemptReference: 'OAA-3', billReference: 'BILL-PAY', amountVnd: 50000, currency: 'VND', status: 'PENDING',
        paymentUrl: 'https://pay.example.invalid/OTA-3', expiresAt: new Date(Date.now() + 10 * 60 * 1000).toISOString(),
      }));
      const user = userEvent.setup();
      renderRoutes(otaConnectivityRoutes, { path: '/ota-connectivity/add-ons' });
      await billAPaidAddOn(user);
      await user.click(screen.getByRole('button', { name: 'Thu tiền (VNPay)' }));
      await screen.findByRole('link', { name: 'Mở cổng thanh toán VNPay' });

      status.current = 'PAID'; // the verified VNPay evidence arrives

      expect(await screen.findByText('Đã thanh toán (PAID)', {}, { timeout: 6000 })).toBeInTheDocument();
      expect(screen.queryByRole('button', { name: 'Thu tiền (VNPay)' })).not.toBeInTheDocument();
      expect(screen.getByText('Thanh toán add-on đã được hoàn tất và ghi nhận thành công.')).toBeInTheDocument();
      const polledWhenPaid = calls.filter(call => call.path === '/api/v1/ota-addon-bills/BILL-PAY').length;
      await new Promise(resolve => setTimeout(resolve, 3500));
      const polledAfter = calls.filter(call => call.path === '/api/v1/ota-addon-bills/BILL-PAY').length;
      expect(polledAfter - polledWhenPaid).toBeLessThanOrEqual(1);
    }, 15000);
  });

  it('records free laser engraving and never offers a direct bill action', async () => {
    const calls = mockFetch({
      'POST /api/v1/ota-channel-bookings/booking-free/add-on-quotes': json(201, {
        quoteId: 'quote-free', addonType: 'LASER_ENGRAVING', amount: { amount: '0', currency: 'VND' }, status: 'RECORDED_FREE',
      }),
    });
    const user = userEvent.setup();
    renderRoutes(otaConnectivityRoutes, { path: '/ota-connectivity/add-ons' });
    await user.type(screen.getByPlaceholderText('UUID'), 'booking-free');
    await user.type(screen.getByLabelText('Description'), 'Engrave initials');
    await user.click(screen.getByRole('button', { name: 'Create quote' }));
    expect(await screen.findByText('Free for OTA customers. No direct bill is created.')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Create direct Mland bill' })).not.toBeInTheDocument();
    const quoteCall = calls.find(call => call.path.includes('/add-on-quotes'));
    expect(quoteCall.body).toEqual({
      addonType: 'LASER_ENGRAVING',
      itemDescription: 'Engrave initials',
      amount: { amount: '0', currency: 'VND' },
    });
  });

  it('renders backend validation errors without fabricating a quote', async () => {
    mockFetch({
      'POST /api/v1/ota-channel-bookings/booking-invalid/add-on-quotes': json(422, {
        code: 'UNPROCESSABLE', message: 'Catalogue add-on is missing, inactive, or not valid for this booking\'s package and location', correlationId: 'corr-invalid',
      }),
    });
    const user = userEvent.setup();
    renderRoutes(otaConnectivityRoutes, { path: '/ota-connectivity/add-ons' });
    await user.type(screen.getByPlaceholderText('UUID'), 'booking-invalid');
    await user.selectOptions(screen.getByLabelText('Add-on type'), 'PAID_OPTION');
    await user.type(screen.getByLabelText('Catalogue add-on reference'), 'addon-invalid');
    await user.click(screen.getByRole('button', { name: 'Create quote' }));
    expect(await screen.findByText('Catalogue add-on is missing, inactive, or not valid for this booking\'s package and location')).toBeInTheDocument();
    expect(screen.getByText('Create a quote to review the immutable amount and consent steps.')).toBeInTheDocument();
  });
});
