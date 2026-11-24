import { describe, expect, it } from 'vitest';
import { fireEvent, screen, within } from '@testing-library/react';
import { json, mockFetch, renderRoutes } from '../../../test/render';
import { readyRingSalesRoutes } from '../routes/routes';

// Thinh, 2026-11-23: on a completed order the product opens its product page; only "Bấm để xem chi tiết" opens the
// order details.
const order = (id, fulfilmentState, overrides = {}) => ({
  id, locationId: 1, fulfilmentMethod: 'GHTK', paymentState: 'PAID', fulfilmentState,
  subtotal: '560000', shippingCharge: '0', payableAmount: '558714', discountAmount: '1286',
  orderedAt: '2026-11-20T03:00:00Z', paidAt: '2026-11-20T03:01:00Z', completedAt: fulfilmentState === 'DELIVERED' ? '2026-11-20T17:24:00Z' : null,
  checkoutId: `c-${id}`, paymentMethod: 'VNPAY', counterPaymentDeadline: null, orderCode: `ML${id}`, checkoutOrderCount: 1,
  paymentDeadline: null,
  items: [{ variantId: 11, productName: 'Nhẫn bạc cành lá citrine', sku: 'CL-7', size: 7, quantity: 1, unitPrice: '560000', lineTotal: '560000', productId: 42 }],
  ...overrides,
});

const render = orders => {
  mockFetch({
    'GET /api/v1/accounts/me/profile': json(200, { role: 'MEMBER', fullName: 'Khách' }),
    'GET /api/v1/members/me/ready-ring-orders': json(200, orders),
    'GET /api/v1/product-selling-locations': json(200, [{ id: 1, name: 'Mland Hà Nội – Tây Hồ' }]),
  });
  renderRoutes(readyRingSalesRoutes, { path: '/member/orders' });
};

describe('Member order card', () => {
  it('links the product of a delivered order to its product page and opens details only from the button', async () => {
    render([order('A1', 'DELIVERED')]);

    const link = await screen.findByRole('link', { name: /Nhẫn bạc cành lá citrine/ });
    expect(link).toHaveAttribute('href', '/products/42');

    const card = link.closest('article');
    fireEvent.click(within(card).getByText('Size 7 · x1').closest('li'));
    expect(within(card).queryByText('Mã đơn')).not.toBeInTheDocument();

    fireEvent.click(within(card).getByRole('button', { name: /Bấm để xem chi tiết/ }));
    expect(within(card).getByText('Mã đơn')).toBeInTheDocument();
    expect(within(card).getByRole('button', { name: /Thu gọn/ })).toHaveAttribute('aria-expanded', 'true');
  });

  it('links the product in every tab, also while shipping and after a cancellation', async () => {
    render([
      order('B2', 'HANDED_TO_CARRIER'),
      order('C3', 'CANCELLED', { paymentState: 'CANCELLED', paidAt: null, items: [{ ...order('x', 'x').items[0], productId: 43 }] }),
    ]);

    const links = await screen.findAllByRole('link', { name: /Nhẫn bạc cành lá citrine/ });
    expect(links.map(link => link.getAttribute('href'))).toEqual(['/products/42', '/products/43']);
  });
});

// Session 2026-11-23: a delivered order offers "Mua lại" and "Đánh giá"; "Đánh giá" opens one review screen for the
// whole order and turns into "Xem đánh giá" once every product of the order is reviewed (FE-11 eligible sources).
describe('Member order card review action', () => {
  const renderWithSources = (orders, sources) => {
    mockFetch({
      'GET /api/v1/accounts/me/profile': json(200, { role: 'MEMBER', fullName: 'Khách' }),
      'GET /api/v1/members/me/ready-ring-orders': json(200, orders),
      'GET /api/v1/product-selling-locations': json(200, [{ id: 1, name: 'Mland Hà Nội – Tây Hồ' }]),
      'GET /api/v1/reviews/eligible-sources': json(200, { items: sources }),
    });
    renderRoutes(readyRingSalesRoutes, { path: '/member/orders' });
  };
  const line = (orderId, extra = {}) => ({ sourceType: 'RETAIL_ORDER_LINE', sourceReference: `L-${orderId}`, targetType: 'RETAIL_PRODUCT', targetReference: '42', orderId, eligible: true, ...extra });

  it('shows "Mua lại" and "Đánh giá" on a delivered order, and "Đánh giá" opens the review of that order', async () => {
    renderWithSources([order('A1', 'DELIVERED')], [line('A1')]);

    const card = (await screen.findByText('Nhẫn bạc cành lá citrine')).closest('article');
    expect(within(card).getByRole('button', { name: 'Mua lại' })).toBeInTheDocument();
    expect(await within(card).findByRole('link', { name: 'Đánh giá' })).toHaveAttribute('href', '/reviews/orders/A1');
  });

  it('turns into "Xem đánh giá" once every product of the order is reviewed', async () => {
    renderWithSources([order('A1', 'DELIVERED')], [line('A1', { alreadyReviewed: true, reviewId: 'r1' })]);

    const card = (await screen.findByText('Nhẫn bạc cành lá citrine')).closest('article');
    expect(await within(card).findByRole('link', { name: 'Xem đánh giá' })).toHaveAttribute('href', '/reviews?tab=reviewed');
    expect(within(card).queryByRole('link', { name: 'Đánh giá' })).toBeNull();
  });

  it('offers no review on an order still being delivered or cancelled', async () => {
    renderWithSources([
      order('B2', 'HANDED_TO_CARRIER'),
      order('C3', 'CANCELLED', { paymentState: 'CANCELLED', paidAt: null }),
    ], []);

    await screen.findAllByText('Nhẫn bạc cành lá citrine');
    expect(screen.queryByRole('link', { name: 'Đánh giá' })).toBeNull();
  });
});
