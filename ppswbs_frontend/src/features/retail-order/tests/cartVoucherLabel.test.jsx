import { vi, describe, expect, it } from 'vitest';
import { fireEvent, screen, waitFor } from '@testing-library/react';
import { json, mockFetch, renderRoutes } from '../../../test/render';
import { readyRingSalesRoutes } from '../routes/routes';

vi.mock('../../../shared/auth/RouteAccessGuard', () => import('../../../test/passThroughGuard'));

// BUG-INT-0002 (2026-11-23): with an automatic discount stacked on a voucher, the "Voucher MLAND" row of the cart must
// name only the voucher's part, like checkout does, not the total of both.
const line = {
  id: 'line-1', variantId: 11, locationId: 1, quantity: 2, productName: 'Nhẫn bạc Moon', sku: 'MOON-12', size: 12,
  indicativePrice: '700000', currency: 'VND', availableQuantity: '5', saleEligible: true, productId: 7,
};

const quote = {
  previewId: null, currency: 'VND', merchandiseSubtotal: '1400000', shippingExcluded: true,
  discountAmount: '260000', autoDiscountAmount: '60000', voucherDiscountAmount: '200000', pointsDiscountAmount: '0',
  payableAmount: '1140000', expiresAt: null, shippingTotal: '0', eligible: true, reasons: [], maxPoints: '0',
  minRedeemPoints: '0', eligibleCartLineIds: ['line-1'], appliedVoucherCode: 'ML9KRGVFW3',
};

describe('Member cart voucher row', () => {
  it('shows the voucher saving alone when an automatic discount is stacked on it', async () => {
    mockFetch({
      'GET /api/v1/accounts/me/profile': json(200, { role: 'MEMBER', fullName: 'Khách' }),
      'GET /api/v1/members/me/ready-ring-cart': json(200, { version: 1, lines: [line] }),
      'GET /api/v1/product-selling-locations': json(200, [{ id: 1, name: 'MLAND Hoan Kiem' }]),
      'GET /api/v1/members/me/loyalty-accounts/current': json(200, { availablePoints: 0 }),
      'GET /api/v1/members/me/saved-vouchers': json(200, { items: [
        { code: 'ML9KRGVFW3', name: 'Voucher 200K', availability: 'ACTIVE', scope: 'PRODUCT' },
      ] }),
      'GET /api/v1/promotions': json(200, { items: [] }),
      'POST /api/v1/ready-ring-benefit-previews': json(200, quote),
    });
    renderRoutes(readyRingSalesRoutes, { path: '/member/cart' });

    fireEvent.click((await screen.findAllByRole('checkbox', { name: /Chọn tất cả/ }))[0]);
    await waitFor(() => expect(screen.getByText(/ML9KRGVFW3 · −/)).toBeInTheDocument());
    const row = screen.getByText(/ML9KRGVFW3 · −/);
    expect(row.textContent).toMatch(/200\.000/);
    expect(row.textContent).not.toMatch(/260\.000/);
  });
});

// BUG-INT-0002 note: each line's price follows FE-06's own share of that line (`cartLineDiscounts`), not a
// proportional split of the total. Line A 600,000 and B 400,000 both get 10% off automatically; the voucher covers
// only B (72,000). Proportional would show 496.800 / 331.200; the branch orders really get 540.000 / 288.000.
describe('Member cart per-line price', () => {
  it('uses FE-06 per-line shares when the voucher covers only some lines', async () => {
    const lineA = { ...line, id: 'line-a', variantId: 21, quantity: 1, indicativePrice: '600000', productName: 'Nhẫn A', sku: 'A-1' };
    const lineB = { ...line, id: 'line-b', variantId: 22, quantity: 1, indicativePrice: '400000', productName: 'Nhẫn B', sku: 'B-1' };
    mockFetch({
      'GET /api/v1/accounts/me/profile': json(200, { role: 'MEMBER', fullName: 'Khách' }),
      'GET /api/v1/members/me/ready-ring-cart': json(200, { version: 1, lines: [lineA, lineB] }),
      'GET /api/v1/product-selling-locations': json(200, [{ id: 1, name: 'MLAND Hoan Kiem' }]),
      'GET /api/v1/members/me/loyalty-accounts/current': json(200, { availablePoints: 0 }),
      'GET /api/v1/members/me/saved-vouchers': json(200, { items: [
        { code: 'VC20', name: 'Voucher 20%', availability: 'ACTIVE', scope: 'PRODUCT' },
      ] }),
      'GET /api/v1/promotions': json(200, { items: [] }),
      'POST /api/v1/ready-ring-benefit-previews': json(200, {
        ...quote, merchandiseSubtotal: '1000000', discountAmount: '172000', autoDiscountAmount: '100000',
        voucherDiscountAmount: '72000', payableAmount: '828000', eligibleCartLineIds: ['line-a', 'line-b'],
        appliedVoucherCode: 'VC20',
        cartLineDiscounts: [
          { cartLineId: 'line-a', autoDiscountAmount: '60000', voucherDiscountAmount: '0' },
          { cartLineId: 'line-b', autoDiscountAmount: '40000', voucherDiscountAmount: '72000' },
        ],
      }),
    });
    renderRoutes(readyRingSalesRoutes, { path: '/member/cart' });

    fireEvent.click((await screen.findAllByRole('checkbox', { name: /Chọn tất cả/ }))[0]);
    await waitFor(() => expect(screen.getAllByText(/540\.000/).length).toBeGreaterThan(0));
    expect(screen.getAllByText(/288\.000/).length).toBeGreaterThan(0);
    expect(screen.queryByText(/496\.800/)).toBeNull();
    expect(screen.queryByText(/331\.200/)).toBeNull();
  });
});
