import React from 'react';
import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { fakeSession, json, mockFetch, renderRoutes } from '../test/render';
import { workshopRoutes } from './routes';
import { clearCart, readCart } from './cartStorage';
import { GroupHoldPanel } from './GroupHoldPanel';
import { GroupDetailCard } from './GroupDetailCard';
import { AWAITING_RESULT_MESSAGE } from './groupDisplay';

const SESSION_ONE = 'f81d4fae-7dec-11d0-a765-00a0c91e6bf6';
const GROUP_CODE = 'WG-20261113-ABCD';
const PAYMENT_PATH = `/api/v1/workshop-booking-groups/${GROUP_CODE}/payment-attempts`;
const PROOF = { contactEmail: 'guest@mland.vn' };

const ticket = (n, status = 'PENDING_PAYMENT') => ({
  ticketCode: `WT-${n}`,
  lineIndex: 0,
  participantOrdinal: n,
  packageId: 5,
  packageName: 'Wax Casting cơ bản',
  ticketStatus: status,
  financialStatus: 'UNPAID',
  finalPriceVnd: 500000,
  collectedAtBookingVnd: 200000,
  collectionKind: 'DEPOSIT',
  remainingVnd: 300000,
});

const group = (overrides = {}) => ({
  groupCode: GROUP_CODE,
  groupStatus: 'PENDING_PAYMENT',
  paymentMode: 'VNPAY',
  groupCollectionVnd: 400000,
  holdExpiresAt: new Date(Date.now() + 12 * 60 * 1000).toISOString(),
  paymentDeadlineAt: new Date(Date.now() + 10 * 60 * 1000).toISOString(),
  lines: [{ lineIndex: 0, packageId: 5, packageName: 'Wax Casting cơ bản', pricePerParticipantVnd: 500000, ticketCount: 2, sessionReferences: [SESSION_ONE] }],
  tickets: [ticket(1), ticket(2)],
  ...overrides,
});

const paymentCalls = calls => calls.filter(call => call.path === PAYMENT_PATH);
const payButton = () => screen.queryByRole('button', { name: /Thanh toán .* qua VNPay|Kiểm tra lại với cùng yêu cầu/ });

describe('003.1 FR-012b/FR-012c group payment attempt failures (T149)', () => {
  afterEach(() => { vi.unstubAllGlobals(); });

  it('treats a lost response as UNKNOWN: same key on a manual check, no automatic retry, no second URL', async () => {
    const user = userEvent.setup();
    const fetchMock = vi.fn(async () => { throw new TypeError('Failed to fetch'); });
    vi.stubGlobal('fetch', fetchMock);
    render(<GroupHoldPanel group={group()} proof={PROOF} />);

    await user.click(screen.getByRole('button', { name: /Thanh toán 400.000 ₫ qua VNPay/ }));

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('Chưa xác định kết quả');
    expect(fetchMock).toHaveBeenCalledTimes(1);
    const firstKey = fetchMock.mock.calls[0][1].headers['Idempotency-Key'];
    expect(firstKey).toMatch(/.{16,128}/);

    // Nothing retries on its own.
    await new Promise(resolve => setTimeout(resolve, 150));
    expect(fetchMock).toHaveBeenCalledTimes(1);

    // The only way forward is the customer's own re-check, and it reuses the very same key.
    await user.click(screen.getByRole('button', { name: 'Kiểm tra lại với cùng yêu cầu' }));
    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(2));
    expect(fetchMock.mock.calls[1][1].headers['Idempotency-Key']).toBe(firstKey);
    expect(fetchMock.mock.calls[1][0].toString()).toBe(fetchMock.mock.calls[0][0].toString());
  });

  it('shows 503 PAYMENT_LINK_UNAVAILABLE as a deterministic failure, not as "being checked"', async () => {
    const user = userEvent.setup();
    const calls = mockFetch({
      [`POST ${PAYMENT_PATH}`]: json(503, { code: 'PAYMENT_LINK_UNAVAILABLE', detail: 'Payment link unavailable' }),
    });
    render(<GroupHoldPanel group={group()} proof={PROOF} />);

    await user.click(screen.getByRole('button', { name: /Thanh toán 400.000 ₫ qua VNPay/ }));

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('Không thể thanh toán');
    expect(alert).toHaveTextContent('Payment link unavailable');
    expect(alert).not.toHaveTextContent('Chưa xác định kết quả');
    expect(screen.queryByRole('button', { name: 'Kiểm tra lại với cùng yêu cầu' })).toBeNull();
    await new Promise(resolve => setTimeout(resolve, 150));
    expect(paymentCalls(calls)).toHaveLength(1);
  });

  it('shows 409 PAYMENT_WINDOW_CLOSED as a deterministic failure', async () => {
    const user = userEvent.setup();
    const calls = mockFetch({
      [`POST ${PAYMENT_PATH}`]: json(409, { code: 'PAYMENT_WINDOW_CLOSED', detail: 'Payment window closed' }),
    });
    render(<GroupHoldPanel group={group()} proof={PROOF} />);

    await user.click(screen.getByRole('button', { name: /Thanh toán 400.000 ₫ qua VNPay/ }));

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('Không thể thanh toán');
    expect(alert).toHaveTextContent('Payment window closed');
    expect(alert).not.toHaveTextContent('Chưa xác định kết quả');
    await new Promise(resolve => setTimeout(resolve, 150));
    expect(paymentCalls(calls)).toHaveLength(1);
  });

  it('between paymentDeadlineAt and holdExpiresAt shows the waiting message and no pay control', () => {
    render(<GroupHoldPanel group={group({
      paymentDeadlineAt: new Date(Date.now() - 1000).toISOString(),
      holdExpiresAt: new Date(Date.now() + 60 * 1000).toISOString(),
    })} proof={PROOF} />);

    expect(screen.getByRole('status')).toHaveTextContent(AWAITING_RESULT_MESSAGE);
    expect(payButton()).toBeNull();
  });

  it.each([
    ['EXPIRED', 'EXPIRED'],
    ['CANCELLED', 'CANCELLED'],
  ])('a %s group has no pay or send-again control', (_label, groupStatus) => {
    mockFetch({});
    render(<GroupDetailCard
      group={group({ groupStatus, tickets: [ticket(1, groupStatus), ticket(2, groupStatus)] })}
      proof={PROOF}
      allowResend
      allowRelease
    />);

    expect(payButton()).toBeNull();
    expect(screen.queryByRole('button', { name: /Gửi lại email xác nhận/ })).toBeNull();
    expect(screen.queryByRole('button', { name: 'Hủy giữ chỗ' })).toBeNull();
    if (groupStatus === 'EXPIRED') {
      expect(screen.getByRole('link', { name: 'Đặt lại workshop' })).toHaveAttribute('href', '/workshops');
    }
  });
});

describe('003.1 FR-012b the payment-return page (T149)', () => {
  const originalUrl = window.location.href;
  afterEach(() => { window.history.replaceState(null, '', originalUrl); vi.unstubAllGlobals(); });

  async function openReturn(groupBody, query = '?vnp_ResponseCode=00') {
    window.history.replaceState(null, '', `/workshop-booking/${GROUP_CODE}/payment-return${query}`);
    const user = userEvent.setup();
    const calls = mockFetch({ 'POST /api/v1/workshop-booking-group-lookups': json(200, groupBody) });
    renderRoutes(workshopRoutes, { path: `/workshop-booking/${GROUP_CODE}/payment-return`, session: fakeSession('signed-out') });
    return { user, calls };
  }

  it('does not treat the browser return as a confirmation before the server group is read', async () => {
    await openReturn(group());
    expect(await screen.findByText('Cổng thanh toán báo thành công')).toBeInTheDocument();
    expect(screen.queryByText('Đã xác nhận')).toBeNull();
    expect(screen.queryByRole('button', { name: /qua VNPay/ })).toBeNull();
  });

  it('shows the server state of an expired group even when the gateway reported success', async () => {
    const { user, calls } = await openReturn(group({ groupStatus: 'EXPIRED', holdExpiresAt: null, paymentDeadlineAt: null, tickets: [ticket(1, 'EXPIRED'), ticket(2, 'EXPIRED')] }));

    await user.type(await screen.findByPlaceholderText('0901234567'), '0901234567');
    await user.click(screen.getByRole('button', { name: 'Xem trạng thái nhóm' }));

    await waitFor(() => expect(calls.some(call => call.path === '/api/v1/workshop-booking-group-lookups')).toBe(true));
    expect(await screen.findByText('Thời hạn thanh toán đã kết thúc')).toBeInTheDocument();
    expect(screen.queryByText('Cổng thanh toán báo thành công')).toBeNull();
    expect(payButton()).toBeNull();
  });

  it('shows the waiting message for a held group past its payment deadline, with no pay control', async () => {
    const { user } = await openReturn(group({
      paymentDeadlineAt: new Date(Date.now() - 1000).toISOString(),
      holdExpiresAt: new Date(Date.now() + 60 * 1000).toISOString(),
    }));

    await user.type(await screen.findByPlaceholderText('0901234567'), '0901234567');
    await user.click(screen.getByRole('button', { name: 'Xem trạng thái nhóm' }));

    const waiting = await screen.findAllByText(AWAITING_RESULT_MESSAGE);
    expect(waiting.length).toBeGreaterThan(0);
    expect(payButton()).toBeNull();
  });
});

describe('003.1 FR-031/FR-045 503 DEPENDENCY_UNAVAILABLE on quote and group creation (T152)', () => {
  const PACKAGE = { packageId: 5, name: 'Wax Casting cơ bản', publicPrice: '500000', requiredSessionCount: 1, minParticipants: 1, maxParticipants: 4 };
  const start = new Date(Date.now() + 5 * 24 * 3600 * 1000).toISOString();
  const end = new Date(Date.now() + 5 * 24 * 3600 * 1000 + 7200 * 1000).toISOString();
  const discovery = () => ({
    'GET /api/v1/workshop-packages/5': json(200, PACKAGE),
    'GET /api/v1/workshop-packages/5/locations': json(200, [{ id: 2, name: 'MLAND Quận 1' }]),
    'GET /api/v1/workshop-packages/5/sessions': json(200, [{ sessionReference: SESSION_ONE, scheduledStart: start, scheduledEnd: end, customerSelectable: true, remainingCapacity: 4 }]),
  });
  const QUOTE = {
    quoteRef: 'quote-ref-1',
    expiresAt: new Date(Date.now() + 30 * 60 * 1000).toISOString(),
    grossVnd: 1000000, voucherDiscountVnd: 0, pointsUsed: 0, pointDiscountVnd: 0, finalTotalVnd: 1000000,
    groupCollectionVnd: 400000, remainingAtCheckoutVnd: 600000, paymentMode: 'VNPAY',
    lines: [{
      lineIndex: 0, packageId: 5, packageName: 'Wax Casting cơ bản', pricePerParticipantVnd: 500000, participantCount: 2,
      sessions: [{ sessionReference: SESSION_ONE, locationReference: 2, ordinal: 1, scheduledStart: start, scheduledEnd: end }],
      tickets: [1, 2].map(n => ({ participantOrdinal: n, packagePriceVnd: 500000, voucherDiscountVnd: 0, pointsUsed: 0, pointDiscountVnd: 0, finalPriceVnd: 500000, collectedAtBookingVnd: 200000, collectionKind: 'DEPOSIT', remainingVnd: 300000 })),
    }],
  };
  const UNAVAILABLE = () => json(503, { code: 'DEPENDENCY_UNAVAILABLE', detail: 'Dependency unavailable', correlationId: 'corr-503' });
  const TRY_LATER = /thử lại sau/i;

  beforeEach(() => { clearCart(); });
  afterEach(() => { vi.unstubAllGlobals(); });

  async function pickTwoTickets(user) {
    await user.click(await screen.findByText(/\d{2}\/\d{2}\/\d{4}/));
    await user.click(await screen.findByRole('button', { name: 'Tăng số vé' }));
  }

  it('cart quote: shows "try again later", keeps the cart, creates no group, sends no retry', async () => {
    const user = userEvent.setup();
    const calls = mockFetch({ ...discovery(), 'POST /api/v1/workshop-cart-quotes': UNAVAILABLE });
    renderRoutes(workshopRoutes, { path: '/workshops/5/book', session: fakeSession('signed-out') });
    await pickTwoTickets(user);
    await user.click(screen.getByRole('button', { name: 'Thêm vào giỏ' }));
    await user.click(await screen.findByRole('link', { name: /Mở giỏ workshop/i }));
    await user.click(await screen.findByRole('button', { name: 'Báo giá giỏ' }));

    expect(await screen.findByText(TRY_LATER)).toBeInTheDocument();
    await new Promise(resolve => setTimeout(resolve, 150));
    expect(calls.filter(call => call.path === '/api/v1/workshop-cart-quotes')).toHaveLength(1);
    expect(calls.some(call => call.path === '/api/v1/workshop-booking-groups')).toBe(false);
    expect(readCart()).toHaveLength(1);
    expect(screen.getByRole('button', { name: 'Báo giá giỏ' })).toBeEnabled();
    expect(screen.queryByText(GROUP_CODE)).toBeNull();
  });

  it('cart confirm: keeps the cart, the quote and every typed contact field, creates no group view', async () => {
    const user = userEvent.setup();
    const calls = mockFetch({
      ...discovery(),
      'POST /api/v1/workshop-cart-quotes': json(201, QUOTE),
      'POST /api/v1/workshop-booking-groups': UNAVAILABLE,
    });
    renderRoutes(workshopRoutes, { path: '/workshops/5/book', session: fakeSession('signed-out') });
    await pickTwoTickets(user);
    await user.click(screen.getByRole('button', { name: 'Thêm vào giỏ' }));
    await user.click(await screen.findByRole('link', { name: /Mở giỏ workshop/i }));
    await user.click(await screen.findByRole('button', { name: 'Báo giá giỏ' }));
    await user.type(await screen.findByLabelText(/Email nhận vé/i), 'guest@mland.vn');
    await user.type(screen.getByLabelText(/Số điện thoại liên hệ/i), '0901234567');
    await user.click(screen.getByRole('button', { name: 'Xác nhận và giữ chỗ' }));

    expect(await screen.findByText(TRY_LATER)).toBeInTheDocument();
    await new Promise(resolve => setTimeout(resolve, 150));
    expect(calls.filter(call => call.path === '/api/v1/workshop-booking-groups')).toHaveLength(1);
    expect(readCart()).toHaveLength(1);
    expect(screen.getByLabelText(/Email nhận vé/i)).toHaveValue('guest@mland.vn');
    expect(screen.getByLabelText(/Số điện thoại liên hệ/i)).toHaveValue('0901234567');
    expect(screen.getByRole('button', { name: 'Xác nhận và giữ chỗ' })).toBeEnabled();
    expect(screen.queryByText(GROUP_CODE)).toBeNull();
  });

  it('"Đặt ngay" quote: shows "try again later" and keeps the chosen session and ticket count', async () => {
    const user = userEvent.setup();
    const calls = mockFetch({ ...discovery(), 'POST /api/v1/workshop-cart-quotes': UNAVAILABLE });
    renderRoutes(workshopRoutes, { path: '/workshops/5/book', session: fakeSession('signed-out') });
    await pickTwoTickets(user);
    await user.click(screen.getByRole('button', { name: 'Đặt ngay' }));

    expect(await screen.findByText(TRY_LATER)).toBeInTheDocument();
    await new Promise(resolve => setTimeout(resolve, 150));
    expect(calls.filter(call => call.path === '/api/v1/workshop-cart-quotes')).toHaveLength(1);
    expect(calls.some(call => call.path === '/api/v1/workshop-booking-groups')).toBe(false);
    expect(screen.getByRole('button', { name: 'Đặt ngay' })).toBeEnabled();
    expect(screen.getByRole('button', { name: 'Giảm số vé' })).toBeEnabled();
  });

  it('"Đặt ngay" confirm: keeps the quote and typed fields, creates no group view, no retry', async () => {
    const user = userEvent.setup();
    const calls = mockFetch({
      ...discovery(),
      'POST /api/v1/workshop-cart-quotes': json(201, QUOTE),
      'POST /api/v1/workshop-booking-groups': UNAVAILABLE,
    });
    renderRoutes(workshopRoutes, { path: '/workshops/5/book', session: fakeSession('signed-out') });
    await pickTwoTickets(user);
    await user.click(screen.getByRole('button', { name: 'Đặt ngay' }));
    await user.type(await screen.findByLabelText(/Email nhận vé/i), 'guest@mland.vn');
    await user.type(screen.getByLabelText(/Số điện thoại liên hệ/i), '0901234567');
    await user.click(screen.getByRole('button', { name: 'Xác nhận và giữ chỗ' }));

    expect(await screen.findByText(TRY_LATER)).toBeInTheDocument();
    await new Promise(resolve => setTimeout(resolve, 150));
    expect(calls.filter(call => call.path === '/api/v1/workshop-booking-groups')).toHaveLength(1);
    expect(screen.getByLabelText(/Email nhận vé/i)).toHaveValue('guest@mland.vn');
    expect(screen.getByLabelText(/Số điện thoại liên hệ/i)).toHaveValue('0901234567');
    expect(screen.getByText('Báo giá đặt ngay')).toBeInTheDocument();
    expect(screen.queryByText(GROUP_CODE)).toBeNull();
  });
});
