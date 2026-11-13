import { describe, it, expect, beforeEach } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { fakeSession, json, mockFetch, renderRoutes } from '../test/render';
import { workshopRoutes } from './routes';
import { clearCart } from './cartStorage';

const SESSION_ONE = 'f81d4fae-7dec-11d0-a765-00a0c91e6bf6';
const PACKAGE = {
  packageId: 5,
  name: 'Wax Casting cơ bản',
  publicPrice: '500000',
  requiredSessionCount: 1,
  minParticipants: 1,
  maxParticipants: 4,
};
const start = new Date(Date.now() + 5 * 24 * 3600 * 1000).toISOString();
const end = new Date(Date.now() + 5 * 24 * 3600 * 1000 + 7200 * 1000).toISOString();

const quote = (overrides = {}) => ({
  quoteRef: 'quote-ref-1',
  expiresAt: new Date(Date.now() + 30 * 60 * 1000).toISOString(),
  grossVnd: 500000,
  voucherDiscountVnd: 0,
  pointsUsed: 0,
  pointDiscountVnd: 0,
  finalTotalVnd: 500000,
  groupCollectionVnd: 200000,
  remainingAtCheckoutVnd: 300000,
  paymentMode: 'VNPAY',
  maxPoints: 5000,
  minRedeemPoints: 100,
  pointValueVnd: 10,
  reasons: [],
  lines: [{
    lineIndex: 0,
    packageId: 5,
    packageName: 'Wax Casting cơ bản',
    pricePerParticipantVnd: 500000,
    participantCount: 1,
    sessions: [{ sessionReference: SESSION_ONE, locationReference: 2, ordinal: 1, scheduledStart: start, scheduledEnd: end }],
    tickets: [{ participantOrdinal: 1, packagePriceVnd: 500000, voucherDiscountVnd: 0, pointsUsed: 0, pointDiscountVnd: 0, finalPriceVnd: 500000, collectedAtBookingVnd: 200000, collectionKind: 'DEPOSIT', remainingVnd: 300000 }],
  }],
  ...overrides,
});

const memberHandlers = (quoteHandler) => ({
  'GET /api/v1/accounts/me/profile': json(200, { accountId: 11, role: 'MEMBER', email: 'member@mland.vn', phone: '0901234567' }),
  'GET /api/v1/workshop-packages/5': json(200, PACKAGE),
  'GET /api/v1/workshop-packages/5/locations': json(200, [{ id: 2, name: 'MLAND Quận 1' }]),
  'GET /api/v1/workshop-packages/5/sessions': json(200, [{
    sessionReference: SESSION_ONE,
    scheduledStart: start,
    scheduledEnd: end,
    customerSelectable: true,
    remainingCapacity: 4,
  }]),
  'GET /api/v1/members/me/saved-vouchers': json(200, { items: [] }),
  'GET /api/v1/promotions': json(200, { items: [] }),
  'GET /api/v1/members/me/loyalty-accounts/current': json(200, {
    availablePoints: '12000',
    heldPoints: '0',
    policy: { redeemPointValue: '10', minRedeemPoints: '100' },
  }),
  'POST /api/v1/workshop-cart-quotes': quoteHandler,
});

/**
 * 003.1 FR-034: the point limit is a quote fact. The switch offers exactly the `maxPoints` of the current quote and
 * the screen never computes a cap of its own; `reasons` is Feature 006's own text for a benefit it did not apply.
 */
describe('003.1 FR-033/FR-034 cart benefits of a Member', () => {
  beforeEach(() => { clearCart(); });

  it('offers no point amount before a quote, then exactly the maxPoints of the quote', async () => {
    const user = userEvent.setup();
    const calls = mockFetch(memberHandlers(call => {
      const asked = Number(call.body?.benefitSelection?.requestedPoints || 0);
      return asked > 0
        ? json(201, quote({ pointsUsed: asked, pointDiscountVnd: asked * 10, finalTotalVnd: 450000, groupCollectionVnd: 200000, remainingAtCheckoutVnd: 250000 }))
        : json(201, quote());
    }));
    renderRoutes(workshopRoutes, { path: '/workshops/5/book', session: fakeSession('signed-in') });

    const pointSwitch = await screen.findByRole('switch', { name: /Dùng điểm thành viên/i });
    // No quote yet, so no cap is known and nothing is offered.
    expect(pointSwitch).toBeDisabled();
    expect(screen.getByText(/Báo giá giỏ để hệ thống tính số điểm tối đa/i)).toBeInTheDocument();

    await user.click(await screen.findByText(/\d{2}\/\d{2}\/\d{4}/));
    await user.click(screen.getByRole('button', { name: 'Đặt ngay' }));
    await screen.findByText('Báo giá đặt ngay');

    expect(await screen.findByText(/Dùng 5.000 điểm cho giỏ này/)).toBeInTheDocument();
    const enabledSwitch = screen.getByRole('switch', { name: /Dùng điểm thành viên/i });
    expect(enabledSwitch).toBeEnabled();

    // Turning it on asks for exactly maxPoints and the quote is replaced at once.
    await user.click(enabledSwitch);
    await waitFor(() => {
      const quoteCalls = calls.filter(item => item.path === '/api/v1/workshop-cart-quotes');
      expect(quoteCalls).toHaveLength(2);
      expect(quoteCalls[1].body.benefitSelection).toEqual({ choice: 'NONE', voucherCode: null, requestedPoints: '5000' });
    });
    expect(await screen.findByText(/Giảm bằng 5.000 điểm/)).toBeInTheDocument();
  });

  it('keeps the switch off when the quote allows fewer points than the minimum', async () => {
    const user = userEvent.setup();
    mockFetch(memberHandlers(json(201, quote({ maxPoints: 40, minRedeemPoints: 100 }))));
    renderRoutes(workshopRoutes, { path: '/workshops/5/book', session: fakeSession('signed-in') });

    await user.click(await screen.findByText(/\d{2}\/\d{2}\/\d{4}/));
    await user.click(screen.getByRole('button', { name: 'Đặt ngay' }));
    await screen.findByText('Báo giá đặt ngay');

    expect(await screen.findByText(/Cần tối thiểu 100 điểm để sử dụng/)).toBeInTheDocument();
    expect(screen.getByRole('switch', { name: /Dùng điểm thành viên/i })).toBeDisabled();
  });

  it("shows Feature 006's own reason when a benefit was not applied", async () => {
    const user = userEvent.setup();
    mockFetch(memberHandlers(json(201, quote({
      maxPoints: 0,
      reasons: ['Voucher không áp dụng cho gói workshop này.'],
    }))));
    renderRoutes(workshopRoutes, { path: '/workshops/5/book', session: fakeSession('signed-in') });

    await user.click(await screen.findByText(/\d{2}\/\d{2}\/\d{4}/));
    await user.click(screen.getByRole('button', { name: 'Đặt ngay' }));

    expect(await screen.findByText('Voucher không áp dụng cho gói workshop này.')).toBeInTheDocument();
    expect(screen.getByText(/Bạn chưa có điểm dùng được cho giỏ này/)).toBeInTheDocument();
  });

  it('offers the same voucher and point picker on the cart page (FR-033)', async () => {
    const user = userEvent.setup();
    const calls = mockFetch(memberHandlers(json(201, quote())));
    renderRoutes(workshopRoutes, { path: '/workshops/5/book', session: fakeSession('signed-in') });

    await user.click(await screen.findByText(/\d{2}\/\d{2}\/\d{4}/));
    await user.click(screen.getByRole('button', { name: 'Thêm vào giỏ' }));
    await user.click(await screen.findByRole('link', { name: /Mở giỏ workshop/i }));

    expect(await screen.findByRole('button', { name: /Voucher MLAND/i })).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Báo giá giỏ' }));

    await waitFor(() => {
      const quoteCall = calls.filter(item => item.path === '/api/v1/workshop-cart-quotes').at(-1);
      expect(quoteCall.body.benefitSelection).toEqual({ choice: 'NONE', voucherCode: null, requestedPoints: '0' });
    });
    expect(await screen.findByText(/Dùng 5.000 điểm cho giỏ này/)).toBeInTheDocument();
  });
});
