import React from 'react';
import { render, screen, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { beforeEach, describe, expect, it } from 'vitest';
import { AuthProvider } from '../shared/auth/AuthProvider';
import { I18nProvider } from '../shared/i18n/I18nProvider';
import { fakeSession, json, mockFetch } from '../test/render';
import HomePage from '../features/landing/HomePage';
import { addCartLine, clearCart } from './cartStorage';
import { WorkshopBookingPage } from './WorkshopBookingPage';
import { WorkshopCartPage } from './WorkshopCartPage';
import { WorkshopDetailPage } from './WorkshopDetailPage';
import { WorkshopListPage } from './WorkshopListPage';

/**
 * 003.1 T106 / FR-052: the five public package surfaces show one ticket's price after the best automatic discount,
 * drawn by Feature 006's public gate (publicOffers.js), never computed here. Package 7 costs 500 000 ₫; a 10 %
 * automatic discount capped at 80 000 ₫ makes it 450 000 ₫.
 */
const PACKAGE = { packageId: 7, name: 'Silverclay 2 phiên', description: 'Mô tả', publicPrice: 500000, requiredSessionCount: 1, minParticipants: 1, maxParticipants: 4 };
const auto = patch => ({
  id: '1', kind: 'AUTO_DISCOUNT', scope: 'WORKSHOP', name: 'Giảm workshop', code: null, createdAt: '2026-11-01T00:00:00Z',
  discount: { kind: 'PERCENT', percentage: '10', maxDiscountAmount: '80000' }, minimumEligibleAmount: '0',
  targets: { packageIds: ['7'] }, ...patch,
});
const voucher = patch => auto({ id: '3', kind: 'VOUCHER', code: 'MLWS2026', name: 'Voucher workshop', targets: {}, discount: { kind: 'PERCENT', percentage: '30', maxDiscountAmount: '200000' }, ...patch });

const DISCOUNTED = /450[.,\s]?000/;
const ORIGINAL = /500[.,\s]?000/;

function renderPage(element, handlers, session = fakeSession('signed-out')) {
  const calls = mockFetch(handlers);
  render(
    <I18nProvider initialLanguage="vi">
      <AuthProvider source={session}>
        <MemoryRouter>{element}</MemoryRouter>
      </AuthProvider>
    </I18nProvider>,
  );
  return calls;
}

const promotions = items => ({ 'GET /api/v1/promotions': json(200, { items }) });
const promotionsDown = { 'GET /api/v1/promotions': json(503, { code: 'UNAVAILABLE' }) };

async function expectDiscounted(container) {
  const now = await within(container).findByText(DISCOUNTED, { selector: '.offer-now' });
  expect(now).toBeInTheDocument();
  expect(within(container).getByText(ORIGINAL, { selector: 's' })).toHaveAccessibleName(/Giá gốc/);
}

describe('003.1 FR-052 discounted price on the public package surfaces', () => {
  beforeEach(() => { clearCart(); });

  it('package card: discounted price with the original struck through, voucher label and sign-in invitation', async () => {
    renderPage(<WorkshopListPage />, {
      'GET /api/v1/workshop-packages': json(200, [PACKAGE]),
      ...promotions([auto(), voucher()]),
    });
    const card = (await screen.findByRole('heading', { name: 'Silverclay 2 phiên' })).closest('article');
    await expectDiscounted(card);
    expect(within(card).getByText('Giảm 10%')).toBeInTheDocument();
    expect(within(card).getByText('Mã giảm 30%')).toBeInTheDocument();
    expect(within(card).getByText('Đăng nhập để dùng mã giảm')).toBeInTheDocument();
  });

  it('package card: plain price and no offer when the public offers cannot be read', async () => {
    const calls = renderPage(<WorkshopListPage />, {
      'GET /api/v1/workshop-packages': json(200, [PACKAGE]),
      ...promotionsDown,
    });
    const card = (await screen.findByRole('heading', { name: 'Silverclay 2 phiên' })).closest('article');
    expect(await within(card).findByText(ORIGINAL)).toBeInTheDocument();
    expect(calls.some(call => call.path === '/api/v1/promotions')).toBe(true);
    expect(card.querySelector('s')).toBeNull();
    expect(card.querySelector('.offer-badge')).toBeNull();
  });

  it('package detail: discounted price, voucher chips linking to the public voucher page and "cho đơn từ …"', async () => {
    renderPage(<WorkshopDetailPage packageId="7" />, {
      'GET /api/v1/workshop-packages/7': json(200, PACKAGE),
      'GET /api/v1/workshop-packages/7/locations': json(200, []),
      ...promotions([
        auto(),
        auto({ id: '2', name: 'Giảm đơn lớn', createdAt: '2026-11-02T00:00:00Z', minimumEligibleAmount: '1000000', discount: { kind: 'FIXED', amount: '100000', maxDiscountAmount: '100000' } }),
        voucher(),
      ]),
    });
    const main = await screen.findByRole('main');
    await expectDiscounted(main);
    const list = await screen.findByRole('region', { name: 'Ưu đãi cho gói này' });
    expect(within(list).getByText(/Giảm đơn lớn: giảm .* cho đơn từ/)).toBeInTheDocument();
    expect(within(list).getByRole('link', { name: 'Xem và lưu mã' })).toHaveAttribute('href', '/promotions/3');
    expect(within(list).getByRole('link', { name: 'Đăng nhập để dùng mã giảm cho thành viên' })).toBeInTheDocument();
  });

  it('package detail: plain price when the offers cannot be read', async () => {
    renderPage(<WorkshopDetailPage packageId="7" />, {
      'GET /api/v1/workshop-packages/7': json(200, PACKAGE),
      'GET /api/v1/workshop-packages/7/locations': json(200, []),
      ...promotionsDown,
    });
    expect(await screen.findByText(ORIGINAL)).toBeInTheDocument();
    expect(screen.queryByRole('region', { name: 'Ưu đãi cho gói này' })).toBeNull();
    expect(document.querySelector('s')).toBeNull();
  });

  it('booking screen: the package summary shows the discounted unit price and no quote is computed on screen', async () => {
    const calls = renderPage(<WorkshopBookingPage packageId="7" />, {
      'GET /api/v1/workshop-packages/7': json(200, PACKAGE),
      'GET /api/v1/workshop-packages/7/locations': json(200, []),
      ...promotions([auto()]),
    });
    const summary = (await screen.findByText(/Đơn giá:/)).closest('p');
    await expectDiscounted(summary);
    expect(screen.getByText('Giảm 10%')).toBeInTheDocument();
    expect(calls.some(call => call.path === '/api/v1/workshop-cart-quotes')).toBe(false);
  });

  it('cart: every line shows its discounted per-ticket price', async () => {
    addCartLine({ packageId: 7, packageName: 'Silverclay 2 phiên', pricePerParticipantVnd: 500000, sessionReferences: ['s-1'], participantCount: 2 });
    addCartLine({ packageId: 8, packageName: 'Wax Casting', pricePerParticipantVnd: 300000, sessionReferences: ['s-2'], participantCount: 1 });
    renderPage(<WorkshopCartPage />, promotions([auto()]));
    const [first, second] = await screen.findAllByRole('listitem').then(items => items.filter(item => item.querySelector('strong')));
    await expectDiscounted(first);
    expect(within(second).getByText(/300[.,\s]?000/)).toBeInTheDocument();
    expect(second.querySelector('s')).toBeNull();
  });

  it('home page strip: the workshop card shows the discounted price', async () => {
    renderPage(<HomePage />, {
      'GET /api/v1/workshop-packages': json(200, [PACKAGE]),
      ...promotions([auto()]),
    });
    const card = (await screen.findByRole('heading', { name: 'Silverclay 2 phiên' })).closest('article');
    await expectDiscounted(card);
    expect(within(card).getByText('Giảm 10%')).toBeInTheDocument();
  });
});
