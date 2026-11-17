import React from 'react';
import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { screen, waitFor, act } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { fakeSession, json, mockFetch, renderRoutes } from '../test/render';
import { workshopRoutes } from './routes';
import { clearCart } from './cartStorage';

/** After "giữ chỗ" the screen follows the server: when the hold ends and the server expires the group, the heading,
 *  the status and the cancel button all change by themselves (no stale "Đã giữ chỗ" with a cancel button). */

const SESSION_ONE = 'f81d4fae-7dec-11d0-a765-00a0c91e6bf6';
const PACKAGE = { packageId: 5, name: 'Wax Casting cơ bản', publicPrice: '500000', requiredSessionCount: 1, minParticipants: 1, maxParticipants: 4 };
const start = new Date(Date.now() + 5 * 24 * 3600 * 1000).toISOString();
const end = new Date(Date.now() + 5 * 24 * 3600 * 1000 + 7200 * 1000).toISOString();
const SESSIONS = [{ sessionReference: SESSION_ONE, scheduledStart: start, scheduledEnd: end, customerSelectable: true, remainingCapacity: 4 }];
const QUOTE = {
  quoteRef: 'quote-ref-1', expiresAt: new Date(Date.now() + 30 * 60 * 1000).toISOString(),
  grossVnd: 500000, voucherDiscountVnd: 0, pointsUsed: 0, pointDiscountVnd: 0, finalTotalVnd: 500000,
  groupCollectionVnd: 200000, remainingAtCheckoutVnd: 300000, paymentMode: 'VNPAY',
  lines: [{
    lineIndex: 0, packageId: 5, packageName: 'Wax Casting cơ bản', pricePerParticipantVnd: 500000, participantCount: 1,
    sessions: [{ sessionReference: SESSION_ONE, locationReference: 2, ordinal: 1, scheduledStart: start, scheduledEnd: end }],
    tickets: [{ participantOrdinal: 1, packagePriceVnd: 500000, voucherDiscountVnd: 0, pointsUsed: 0, pointDiscountVnd: 0, finalPriceVnd: 500000, collectedAtBookingVnd: 200000, collectionKind: 'DEPOSIT', remainingVnd: 300000 }],
  }],
};
const ticket = status => ({ ticketCode: 'WT-1', lineIndex: 0, participantOrdinal: 1, packageId: 5, packageName: 'Wax Casting cơ bản', ticketStatus: status, financialStatus: 'UNPAID', finalPriceVnd: 500000, collectedAtBookingVnd: 200000, remainingVnd: 300000, collectionKind: 'DEPOSIT' });
const group = (status, overrides = {}) => ({
  groupCode: 'WG-20261113-ABCD', groupStatus: status, paymentMode: 'VNPAY', groupCollectionVnd: 200000,
  lines: [{ lineIndex: 0, packageId: 5, packageName: 'Wax Casting cơ bản', pricePerParticipantVnd: 500000, ticketCount: 1, sessionReferences: [SESSION_ONE] }],
  tickets: [ticket(status === 'EXPIRED' ? 'EXPIRED' : 'PENDING_PAYMENT')],
  ...overrides,
});

describe('the result screen after "giữ chỗ" when the hold ends', () => {
  beforeEach(() => { clearCart(); });
  afterEach(() => { vi.unstubAllGlobals(); });

  it('reads the group again, then shows it expired and drops the cancel button', async () => {
    const user = userEvent.setup();
    // The hold is stamped when the server answers "giữ chỗ" and ends about a second later; the server has already expired the group.
    const times = () => ({ paymentDeadlineAt: new Date(Date.now() + 700).toISOString(), holdExpiresAt: new Date(Date.now() + 1300).toISOString() });
    let held = null;
    const calls = mockFetch({
      'GET /api/v1/workshop-packages/5': json(200, PACKAGE),
      'GET /api/v1/workshop-packages/5/locations': json(200, [{ id: 2, name: 'MLAND Quận 1' }]),
      'GET /api/v1/workshop-packages/5/sessions': json(200, SESSIONS),
      'POST /api/v1/workshop-cart-quotes': json(201, QUOTE),
      'POST /api/v1/workshop-booking-groups': () => { held = group('PENDING_PAYMENT', times()); return json(201, held); },
      'POST /api/v1/workshop-booking-group-lookups': () => json(200, group('EXPIRED', { paymentDeadlineAt: held.paymentDeadlineAt, holdExpiresAt: held.holdExpiresAt })),
    });
    const { router } = renderRoutes(workshopRoutes, { path: '/workshops/5', session: fakeSession('signed-out') });

    await user.click(await screen.findByText(/\d{2}\/\d{2}\/\d{4}/));
    await user.click(screen.getByRole('button', { name: 'Thêm vào giỏ' }));
    await act(async () => { await router.navigate('/workshop-booking/cart'); });
    await user.click(await screen.findByRole('checkbox', { name: 'Chọn tất cả các dòng' }));
    await user.click(await screen.findByRole('button', { name: 'Xem báo giá và đặt chỗ' }));
    await user.type(await screen.findByLabelText(/Email nhận vé/i), 'guest@mland.vn');
    await user.type(screen.getByLabelText(/Số điện thoại liên hệ/i), '0901234567');
    await user.click(screen.getByRole('button', { name: 'Xác nhận và giữ chỗ' }));

    // Held for ONE ticket: the heading says one ticket, not "a group", and the cancel button is offered.
    expect(await screen.findByRole('heading', { name: 'Đã giữ chỗ cho 1 vé' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Hủy giữ chỗ' })).toBeInTheDocument();

    // The hold ends, the screen reads the group again and follows the server.
    expect(await screen.findByRole('heading', { name: 'Giữ chỗ đã hết hạn' }, { timeout: 4000 })).toBeInTheDocument();
    await waitFor(() => expect(screen.queryByRole('button', { name: 'Hủy giữ chỗ' })).toBeNull());
    expect(screen.queryByRole('heading', { name: /Đã giữ chỗ cho/ })).toBeNull();
    const lookup = calls.find(call => call.path === '/api/v1/workshop-booking-group-lookups');
    expect(lookup.body).toMatchObject({ groupCode: 'WG-20261113-ABCD', contactEmail: 'guest@mland.vn' });
    expect(lookup.body).not.toHaveProperty('contactPhone');
  }, 15000);

  it('after the payment deadline: no cancel or refresh button, and the screen says it is waiting for the gateway', async () => {
    const user = userEvent.setup();
    // The hold is stamped when the server answers "giữ chỗ" and ends about a second later; the server has already expired the group.
    const times = () => ({ paymentDeadlineAt: new Date(Date.now() + 700).toISOString(), holdExpiresAt: new Date(Date.now() + 1300).toISOString() });
    let held = null;
    const calls = mockFetch({
      'GET /api/v1/workshop-packages/5': json(200, PACKAGE),
      'GET /api/v1/workshop-packages/5/locations': json(200, [{ id: 2, name: 'MLAND Quận 1' }]),
      'GET /api/v1/workshop-packages/5/sessions': json(200, SESSIONS),
      'POST /api/v1/workshop-cart-quotes': json(201, QUOTE),
      'POST /api/v1/workshop-booking-groups': () => { held = group('PENDING_PAYMENT', times()); return json(201, held); },
      'POST /api/v1/workshop-booking-group-lookups': () => json(200, group('PENDING_PAYMENT', { paymentDeadlineAt: held.paymentDeadlineAt, holdExpiresAt: held.holdExpiresAt })),
    });
    const { router } = renderRoutes(workshopRoutes, { path: '/workshops/5', session: fakeSession('signed-out') });

    await user.click(await screen.findByText(/\d{2}\/\d{2}\/\d{4}/));
    await user.click(screen.getByRole('button', { name: 'Thêm vào giỏ' }));
    await act(async () => { await router.navigate('/workshop-booking/cart'); });
    await user.click(await screen.findByRole('checkbox', { name: 'Chọn tất cả các dòng' }));
    await user.click(await screen.findByRole('button', { name: 'Xem báo giá và đặt chỗ' }));
    await user.type(await screen.findByLabelText(/Email nhận vé/i), 'guest@mland.vn');
    await user.type(screen.getByLabelText(/Số điện thoại liên hệ/i), '0901234567');
    await user.click(screen.getByRole('button', { name: 'Xác nhận và giữ chỗ' }));

    // Held for ONE ticket: the heading says one ticket, not "a group", and the cancel button is offered.
    expect(await screen.findByRole('heading', { name: 'Đã giữ chỗ cho 1 vé' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Hủy giữ chỗ' })).toBeInTheDocument();

    // The deadline passes: nothing can be cancelled or refreshed by hand; the page keeps waiting for the server.
    await waitFor(() => expect(screen.queryByRole('button', { name: 'Hủy giữ chỗ' })).toBeNull(), { timeout: 4000 });
    expect(screen.queryByRole('button', { name: 'Cập nhật trạng thái' })).toBeNull();
    // The customer is told why nothing has changed yet: the deadline is over and the gateway's answer is awaited.
    expect(screen.getByRole('heading', { name: 'Đang chờ kết quả thanh toán' })).toBeInTheDocument();
    expect(screen.getByText(/Đã hết thời hạn thanh toán. Hệ thống đang chờ cổng thanh toán xác nhận kết quả/)).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: /Đã giữ chỗ cho/ })).toBeNull();
  }, 15000);
});
