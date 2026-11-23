import { describe, expect, it } from 'vitest';
import { fireEvent, screen, waitFor } from '@testing-library/react';
import { json, mockFetch, renderRoutes } from '../../../test/render';
import { readyRingSalesRoutes } from '../routes/routes';

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
