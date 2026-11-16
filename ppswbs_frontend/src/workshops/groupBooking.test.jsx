import React from 'react';
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { fakeSession, json, mockFetch, renderRoutes } from '../test/render';
import { workshopRoutes } from './routes';
import { clearCart, readCart } from './cartStorage';
import { AWAITING_RESULT_MESSAGE, groupHoldPhase } from './groupDisplay';

const SESSION_ONE = 'f81d4fae-7dec-11d0-a765-00a0c91e6bf6';
const PACKAGE = {
  packageId: 5,
  name: 'Wax Casting cơ bản',
  publicPrice: '500000',
  requiredSessionCount: 1,
  minParticipants: 1,
  maxParticipants: 4,
};
const LOCATIONS = [{ id: 2, name: 'MLAND Quận 1' }];
const start = new Date(Date.now() + 5 * 24 * 3600 * 1000).toISOString();
const end = new Date(Date.now() + 5 * 24 * 3600 * 1000 + 7200 * 1000).toISOString();
const SESSIONS = [{
  sessionReference: SESSION_ONE,
  scheduledStart: start,
  scheduledEnd: end,
  customerSelectable: true,
  remainingCapacity: 4,
}];

const QUOTE = {
  quoteRef: 'quote-ref-1',
  expiresAt: new Date(Date.now() + 30 * 60 * 1000).toISOString(),
  grossVnd: 1000000,
  voucherDiscountVnd: 0,
  pointsUsed: 0,
  pointDiscountVnd: 0,
  finalTotalVnd: 1000000,
  groupCollectionVnd: 400000,
  remainingAtCheckoutVnd: 600000,
  paymentMode: 'VNPAY',
  lines: [{
    lineIndex: 0,
    packageId: 5,
    packageName: 'Wax Casting cơ bản',
    pricePerParticipantVnd: 500000,
    participantCount: 2,
    sessions: [{ sessionReference: SESSION_ONE, locationReference: 2, ordinal: 1, scheduledStart: start, scheduledEnd: end }],
    tickets: [
      { participantOrdinal: 1, packagePriceVnd: 500000, voucherDiscountVnd: 0, pointsUsed: 0, pointDiscountVnd: 0, finalPriceVnd: 500000, collectedAtBookingVnd: 200000, collectionKind: 'DEPOSIT', remainingVnd: 300000 },
      { participantOrdinal: 2, packagePriceVnd: 500000, voucherDiscountVnd: 0, pointsUsed: 0, pointDiscountVnd: 0, finalPriceVnd: 500000, collectedAtBookingVnd: 200000, collectionKind: 'DEPOSIT', remainingVnd: 300000 },
    ],
  }],
};

const heldGroup = (overrides = {}) => ({
  groupCode: 'WG-20261113-ABCD',
  groupStatus: 'PENDING_PAYMENT',
  paymentMode: 'VNPAY',
  groupCollectionVnd: 400000,
  holdExpiresAt: new Date(Date.now() + 12 * 60 * 1000).toISOString(),
  paymentDeadlineAt: new Date(Date.now() + 10 * 60 * 1000).toISOString(),
  lines: [{ lineIndex: 0, packageId: 5, packageName: 'Wax Casting cơ bản', pricePerParticipantVnd: 500000, ticketCount: 2, sessionReferences: [SESSION_ONE] }],
  tickets: [
    { ticketCode: 'WT-1', lineIndex: 0, participantOrdinal: 1, packageId: 5, packageName: 'Wax Casting cơ bản', ticketStatus: 'PENDING_PAYMENT', financialStatus: 'UNPAID', finalPriceVnd: 500000, collectedAtBookingVnd: 200000, collectionKind: 'DEPOSIT', remainingVnd: 300000 },
    { ticketCode: 'WT-2', lineIndex: 0, participantOrdinal: 2, packageId: 5, packageName: 'Wax Casting cơ bản', ticketStatus: 'PENDING_PAYMENT', financialStatus: 'UNPAID', finalPriceVnd: 500000, collectedAtBookingVnd: 200000, collectionKind: 'DEPOSIT', remainingVnd: 300000 },
  ],
  ...overrides,
});

const discoveryHandlers = () => ({
  'GET /api/v1/workshop-packages/5': json(200, PACKAGE),
  'GET /api/v1/workshop-packages/5/locations': json(200, LOCATIONS),
  'GET /api/v1/workshop-packages/5/sessions': json(200, SESSIONS),
});

async function pickSessionAndTwoTickets(user) {
  const sessionRow = await screen.findByText(/\d{2}\/\d{2}\/\d{4}/);
  await user.click(sessionRow);
  await user.click(await screen.findByRole('button', { name: 'Tăng số vé' }));
}

describe('003.1 FR-045 booking a group: pick on the package page, "Đặt ngay" opens the checkout', () => {
  beforeEach(() => { clearCart(); });

  it('"Đặt ngay" quotes this one line and confirms it into a booking group, with no cart page', async () => {
    const user = userEvent.setup();
    const calls = mockFetch({
      ...discoveryHandlers(),
      'POST /api/v1/workshop-cart-quotes': json(201, QUOTE),
      'POST /api/v1/workshop-booking-groups': json(201, heldGroup()),
    });
    renderRoutes(workshopRoutes, { path: '/workshops/5', session: fakeSession('signed-out') });

    await pickSessionAndTwoTickets(user);
    await user.click(screen.getByRole('button', { name: 'Đặt ngay' }));

    const quoteCall = await waitFor(() => {
      const call = calls.find(item => item.path === '/api/v1/workshop-cart-quotes');
      expect(call).toBeTruthy();
      return call;
    });
    expect(quoteCall.body).toEqual({ lines: [{ packageId: 5, sessionReferences: [SESSION_ONE], participantCount: 2 }] });

    // FR-037: each ticket is labelled and the booking-time payment is stated as non-refundable.
    expect(await screen.findByText('Báo giá đặt ngay')).toBeInTheDocument();
    expect(screen.getAllByText('Cọc').length).toBe(2);
    expect(screen.getByText(/không được hoàn lại nếu bạn tự hủy hoặc không tham dự/i)).toBeInTheDocument();

    await user.type(screen.getByLabelText(/Email nhận vé/i), 'guest@mland.vn');
    await user.type(screen.getByLabelText(/Số điện thoại liên hệ/i), '0901234567');
    await user.click(screen.getByRole('button', { name: 'Xác nhận và giữ chỗ' }));

    const createCall = await waitFor(() => {
      const call = calls.find(item => item.path === '/api/v1/workshop-booking-groups');
      expect(call).toBeTruthy();
      return call;
    });
    expect(createCall.body).toEqual({
      quoteRef: 'quote-ref-1',
      contactEmail: 'guest@mland.vn',
      contactPhone: '+84901234567',
    });
    expect(createCall.headers['Idempotency-Key']).toMatch(/.{16,128}/);

    expect(await screen.findByText('WG-20261113-ABCD')).toBeInTheDocument();
    expect(screen.getByText('WT-1')).toBeInTheDocument();
    expect(screen.getByText('WT-2')).toBeInTheDocument();
  });

  it('"Thêm vào giỏ" keeps the line in this browser and opens no booking call', async () => {
    const user = userEvent.setup();
    const calls = mockFetch(discoveryHandlers());
    renderRoutes(workshopRoutes, { path: '/workshops/5', session: fakeSession('signed-out') });

    await pickSessionAndTwoTickets(user);
    await user.click(screen.getByRole('button', { name: 'Thêm vào giỏ' }));

    expect(await screen.findByText(/Đã thêm vào giỏ/i)).toBeInTheDocument();
    expect(readCart()).toHaveLength(1);
    expect(readCart()[0]).toMatchObject({ packageId: 5, participantCount: 2, sessionReferences: [SESSION_ONE] });
    expect(calls.some(call => call.path.startsWith('/api/v1/workshop-cart-quotes'))).toBe(false);
    expect(calls.some(call => call.path.startsWith('/api/v1/workshop-booking-groups'))).toBe(false);
  });

  it('the checkout opened without a chosen session quotes nothing and sends the customer back to pick one', async () => {
    const calls = mockFetch(discoveryHandlers());
    renderRoutes(workshopRoutes, { path: '/workshops/5/book', session: fakeSession('signed-out') });

    expect(await screen.findByText('Bạn chưa chọn ca workshop nào.')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Chọn ca' })).toHaveAttribute('href', '/workshops/5');
    await new Promise(resolve => setTimeout(resolve, 100));
    expect(calls.some(call => call.path === '/api/v1/workshop-cart-quotes')).toBe(false);
  });

  it('the checkout shows the chosen session and quotes the chosen line once, on arrival', async () => {
    const calls = mockFetch({ ...discoveryHandlers(), 'POST /api/v1/workshop-cart-quotes': json(201, QUOTE) });
    renderRoutes(workshopRoutes, { path: `/workshops/5/book?locationId=2&session=${SESSION_ONE}&participants=2`, session: fakeSession('signed-out') });

    expect(await screen.findByText('Báo giá đặt ngay')).toBeInTheDocument();
    expect(screen.getByText('1. Ca workshop đã chọn')).toBeInTheDocument();
    expect(screen.getByText('MLAND Quận 1')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Thêm vào giỏ' })).toBeNull();
    const quoteCalls = calls.filter(call => call.path === '/api/v1/workshop-cart-quotes');
    expect(quoteCalls).toHaveLength(1);
    expect(quoteCalls[0].body).toEqual({ lines: [{ packageId: 5, sessionReferences: [SESSION_ONE], participantCount: 2 }] });
  });

  it('shows the counter notice and no non-refundable notice for a deferred group (FR-036, FR-037)', async () => {
    const user = userEvent.setup();
    mockFetch({
      ...discoveryHandlers(),
      'POST /api/v1/workshop-cart-quotes': json(201, {
        ...QUOTE,
        paymentMode: 'INTERNAL_DEFERRED',
        groupCollectionVnd: 0,
        remainingAtCheckoutVnd: 3000,
        lines: [{
          ...QUOTE.lines[0],
          participantCount: 1,
          tickets: [{ participantOrdinal: 1, packagePriceVnd: 3000, voucherDiscountVnd: 0, pointsUsed: 0, pointDiscountVnd: 0, finalPriceVnd: 3000, collectedAtBookingVnd: 0, collectionKind: 'DEFERRED_BELOW_VNPAY_MINIMUM', remainingVnd: 3000 }],
        }],
      }),
    });
    renderRoutes(workshopRoutes, { path: '/workshops/5', session: fakeSession('signed-out') });

    await user.click(await screen.findByText(/\d{2}\/\d{2}\/\d{4}/));
    await user.click(screen.getByRole('button', { name: 'Đặt ngay' }));

    expect(await screen.findByText(/Không cần thanh toán online, thanh toán 3.000 ₫ tại quầy khi checkout/)).toBeInTheDocument();
    expect(screen.getByText('Thanh toán tại quầy')).toBeInTheDocument();
    expect(screen.queryByText(/không được hoàn lại/i)).toBeNull();
  });
});

describe('003.1 FR-031/FR-045 the cart page', () => {
  beforeEach(() => { clearCart(); });

  it('quotes and confirms the cart with the same two calls', async () => {
    const user = userEvent.setup();
    const calls = mockFetch({
      ...discoveryHandlers(),
      'POST /api/v1/workshop-cart-quotes': json(201, QUOTE),
      'POST /api/v1/workshop-booking-groups': json(201, heldGroup()),
    });
    renderRoutes(workshopRoutes, { path: '/workshops/5', session: fakeSession('signed-out') });
    await pickSessionAndTwoTickets(user);
    await user.click(screen.getByRole('button', { name: 'Thêm vào giỏ' }));
    await user.click(await screen.findByRole('link', { name: /Mở giỏ workshop/i }));

    await user.click(await screen.findByRole('button', { name: 'Đặt chỗ ngay' }));
    expect(await screen.findByText('Báo giá giỏ workshop')).toBeInTheDocument();
    const quoteCall = calls.find(call => call.path === '/api/v1/workshop-cart-quotes');
    expect(quoteCall.body).toEqual({ lines: [{ packageId: 5, sessionReferences: [SESSION_ONE], participantCount: 2 }] });

    await user.type(screen.getByLabelText(/Email nhận vé/i), 'guest@mland.vn');
    await user.type(screen.getByLabelText(/Số điện thoại liên hệ/i), '0901234567');
    await user.click(screen.getByRole('button', { name: 'Xác nhận và giữ chỗ' }));

    expect(await screen.findByText('WG-20261113-ABCD')).toBeInTheDocument();
    // FR-045: the cart the group came from is cleared.
    expect(readCart()).toHaveLength(0);
  });

  it('names only the failing line of a refused confirmation and keeps the cart editable (FR-031)', async () => {
    const user = userEvent.setup();
    mockFetch({
      ...discoveryHandlers(),
      'POST /api/v1/workshop-cart-quotes': json(201, QUOTE),
      'POST /api/v1/workshop-booking-groups': json(409, {
        code: 'CART_HOLD_REJECTED',
        detail: 'Nothing was held',
        correlationId: 'corr-1',
        failingLines: [0],
      }),
    });
    renderRoutes(workshopRoutes, { path: '/workshops/5', session: fakeSession('signed-out') });
    await pickSessionAndTwoTickets(user);
    await user.click(screen.getByRole('button', { name: 'Thêm vào giỏ' }));
    await user.click(await screen.findByRole('link', { name: /Mở giỏ workshop/i }));
    await user.click(await screen.findByRole('button', { name: 'Đặt chỗ ngay' }));
    await user.type(await screen.findByLabelText(/Email nhận vé/i), 'guest@mland.vn');
    await user.type(screen.getByLabelText(/Số điện thoại liên hệ/i), '0901234567');
    await user.click(screen.getByRole('button', { name: 'Xác nhận và giữ chỗ' }));

    expect(await screen.findByText(/Dòng này không còn đủ chỗ/i)).toBeInTheDocument();
    expect(screen.getByText(/dòng 1/)).toBeInTheDocument();
    // Nothing was held, so the cart survives and can be edited again.
    expect(readCart()).toHaveLength(1);
    expect(screen.getByRole('button', { name: 'Đặt chỗ ngay' })).toBeEnabled();
  });
});

describe('003.1 FR-032 the hold the customer sees', () => {
  it('counts down to the 10-minute payment deadline, then waits for the result without naming the 12-minute hold', () => {
    const now = Date.parse('2026-11-13T10:00:00Z');
    const group = {
      paymentDeadlineAt: '2026-11-13T10:10:00Z',
      holdExpiresAt: '2026-11-13T10:12:00Z',
    };
    expect(groupHoldPhase(group, now)).toEqual({ phase: 'PAYABLE', secondsLeft: 600 });
    expect(groupHoldPhase(group, Date.parse('2026-11-13T10:10:30Z'))).toEqual({ phase: 'AWAITING_RESULT', secondsLeft: null });
    expect(groupHoldPhase(group, Date.parse('2026-11-13T10:12:01Z'))).toEqual({ phase: 'ENDED', secondsLeft: null });
  });

  it('shows the payment button while payable and the waiting message afterwards', async () => {
    vi.useFakeTimers();
    try {
      const { GroupHoldPanel } = await import('./GroupHoldPanel');
      const { render } = await import('@testing-library/react');
      const group = heldGroup();
      const { rerender, unmount } = render(<GroupHoldPanel group={group} />);
      expect(screen.getByRole('timer').textContent).toMatch(/9 phút|10 phút/);
      expect(screen.getByRole('button', { name: /Thanh toán 400.000 ₫ qua VNPay/ })).toBeInTheDocument();
      expect(screen.queryByText(/12 phút/)).toBeNull();

      const ended = heldGroup({
        paymentDeadlineAt: new Date(Date.now() - 1000).toISOString(),
        holdExpiresAt: new Date(Date.now() + 60 * 1000).toISOString(),
      });
      rerender(<GroupHoldPanel group={ended} />);
      expect(screen.getByRole('status').textContent).toBe(AWAITING_RESULT_MESSAGE);
      unmount();
    } finally {
      vi.useRealTimers();
    }
  });

  it('re-reads the group every 5 s after the hold ends, at most 6 times, and stops when unmounted', async () => {
    vi.useFakeTimers();
    try {
      const { GroupHoldPanel, HOLD_ENDED_POLL_MS, HOLD_ENDED_POLL_MAX } = await import('./GroupHoldPanel');
      const { render } = await import('@testing-library/react');
      const onHoldEnded = vi.fn();
      const ended = heldGroup({
        paymentDeadlineAt: new Date(Date.now() - 130_000).toISOString(),
        holdExpiresAt: new Date(Date.now() - 10_000).toISOString(),
      });
      const { unmount } = render(<GroupHoldPanel group={ended} onHoldEnded={onHoldEnded} />);
      expect(onHoldEnded).toHaveBeenCalledTimes(1);
      vi.advanceTimersByTime(HOLD_ENDED_POLL_MS * 2);
      expect(onHoldEnded).toHaveBeenCalledTimes(3);
      vi.advanceTimersByTime(HOLD_ENDED_POLL_MS * 20);
      expect(onHoldEnded).toHaveBeenCalledTimes(HOLD_ENDED_POLL_MAX);
      unmount();

      const again = vi.fn();
      const second = render(<GroupHoldPanel group={ended} onHoldEnded={again} />);
      second.unmount();
      vi.advanceTimersByTime(HOLD_ENDED_POLL_MS * 3);
      expect(again).toHaveBeenCalledTimes(1);
    } finally {
      vi.useRealTimers();
    }
  });
});
