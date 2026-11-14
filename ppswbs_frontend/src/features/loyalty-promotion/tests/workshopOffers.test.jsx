import React from 'react';
import { render, screen } from '@testing-library/react';
import { createMemoryRouter, RouterProvider } from 'react-router';
import { describe, expect, it } from 'vitest';
import { AuthProvider } from '../../../shared/auth/AuthProvider';
import { I18nProvider } from '../../../shared/i18n/I18nProvider';
import { fakeSession } from '../../../test/render';
import { coversPackage, offersForPackage } from '../domain/workshopOffers';
import { WorkshopOfferBadges, WorkshopOfferList, WorkshopOfferPrice } from '../ui/public/WorkshopOffers';

const pkg = { packageId: 7, name: 'Silverclay 2 phiên', publicPrice: 500000 };
const program = patch => ({
  id: '1', kind: 'AUTO_DISCOUNT', scope: 'WORKSHOP', name: 'Giảm workshop', code: null,
  discount: { kind: 'PERCENT', percentage: '10', maxDiscountAmount: '80000' }, minimumEligibleAmount: '0',
  targets: { packageIds: ['7'] }, ...patch,
});
const voucher = patch => program({ id: '3', kind: 'VOUCHER', code: 'MLWS2026', name: 'Voucher workshop', targets: {}, ...patch });

describe('which programmes cover a package (FR-044, FR-045 rule 1)', () => {
  it('an automatic discount covers only the packages it names', () => {
    expect(coversPackage(program(), 7)).toBe(true);
    expect(coversPackage(program(), '8')).toBe(false);
    expect(coversPackage(program({ targets: {} }), 7)).toBe(false);
  });

  it('a voucher without packages covers every package; branch or session limits do not narrow the label', () => {
    expect(coversPackage(voucher(), 7)).toBe(true);
    expect(coversPackage(voucher({ targets: { packageIds: ['8'] } }), 7)).toBe(false);
    expect(coversPackage(voucher({ targets: { packageIds: ['7'], branchIds: ['2'], sessionIds: ['aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa'] } }), 7)).toBe(true);
  });
});

describe('offers of a package, one ticket (FR-045 rules 2-5)', () => {
  it('takes the largest automatic discount off one ticket, floored and capped, and lists vouchers as labels only', () => {
    const offers = offersForPackage(pkg, [
      program({ discount: { kind: 'PERCENT', percentage: '12.5', maxDiscountAmount: '1000000' } }),
      program({ id: '2', discount: { kind: 'FIXED', amount: '50000', maxDiscountAmount: '50000' } }),
      voucher({ discount: { kind: 'PERCENT', percentage: '30', maxDiscountAmount: '200000' } }),
    ]);
    expect(offers).toMatchObject({ price: 500000n, discount: 62500n, finalPrice: 437500n, discounted: true });
    expect(offers.auto.id).toBe('1');
    expect(offers.vouchers.map(item => item.id)).toEqual(['3']);
  });

  it('never goes above the cap or the ticket price', () => {
    expect(offersForPackage(pkg, [program({ discount: { kind: 'PERCENT', percentage: '50', maxDiscountAmount: '80000' } })]).finalPrice).toBe(420000n);
    expect(offersForPackage(pkg, [program({ discount: { kind: 'FIXED', amount: '900000', maxDiscountAmount: '900000' } })]).finalPrice).toBe(0n);
  });

  it('on equal discounts the programme created first wins (FR-015)', () => {
    const offers = offersForPackage(pkg, [
      program({ id: '9', createdAt: '2026-11-14T03:00:00Z', discount: { kind: 'FIXED', amount: '20000', maxDiscountAmount: '20000' } }),
      program({ id: '4', createdAt: '2026-11-13T03:00:00Z', discount: { kind: 'FIXED', amount: '20000', maxDiscountAmount: '20000' } }),
    ]);
    expect(offers.auto.id).toBe('4');
  });

  it('an automatic discount whose minimum fee is above one ticket is only an "orders from" offer', () => {
    const offers = offersForPackage(pkg, [program({ minimumEligibleAmount: '1000000' })]);
    expect(offers.discounted).toBe(false);
    expect(offers.finalPrice).toBe(500000n);
    expect(offers.autoForBiggerOrder.map(item => item.id)).toEqual(['1']);
  });

  it('ignores product programmes, other packages, and a package without a price', () => {
    expect(offersForPackage(pkg, [program({ scope: 'PRODUCT' }), program({ id: '5', targets: { packageIds: ['8'] } })]))
      .toMatchObject({ discounted: false, auto: null, vouchers: [] });
    expect(offersForPackage({ packageId: 7 }, [program()])).toMatchObject({ price: null, discounted: false, finalPrice: null });
    expect(offersForPackage({ id: 7, price: '500000' }, [program()]).finalPrice).toBe(450000n);
  });

  it('a failed read (no programmes) leaves the plain price', () => {
    expect(offersForPackage(pkg, [])).toMatchObject({ discounted: false, finalPrice: 500000n, vouchers: [] });
    expect(offersForPackage(pkg, undefined).discounted).toBe(false);
  });
});

function show(element, status = 'signed-out') {
  const router = createMemoryRouter([{ path: '/', element }], { initialEntries: ['/'] });
  return render(
    <AuthProvider source={fakeSession(status)}>
      <I18nProvider><RouterProvider router={router} /></I18nProvider>
    </AuthProvider>,
  );
}

describe('workshop offer display', () => {
  const offers = offersForPackage(pkg, [program(), voucher(), voucher({ id: '6' }), voucher({ id: '8' })]);

  it('shows the price after the automatic discount with the original struck, without "possible discount" wording', async () => {
    show(<WorkshopOfferPrice offers={offers} fallback={<span>giá thường</span>} />);
    expect(await screen.findByText(/450\.000/)).toBeInTheDocument();
    expect(screen.getByLabelText(/Giá gốc/).tagName).toBe('S');
    expect(screen.queryByText(/có thể/i)).toBeNull();
  });

  it('falls back to the plain price when nothing is taken off', async () => {
    show(<WorkshopOfferPrice offers={offersForPackage(pkg, [voucher()])} fallback={<span>giá thường</span>} />);
    expect(await screen.findByText('giá thường')).toBeInTheDocument();
  });

  it('shows at most two voucher labels and invites a Guest to sign in', async () => {
    show(<WorkshopOfferBadges offers={offers} />);
    expect(await screen.findByText('Giảm 10%')).toBeInTheDocument();
    expect(screen.getAllByText('Mã giảm 10%')).toHaveLength(2);
    expect(screen.getByText('Đăng nhập để dùng mã giảm')).toBeInTheDocument();
  });

  it('does not invite a signed-in Member to sign in', async () => {
    show(<WorkshopOfferList offers={offers} />, 'signed-in');
    expect(await screen.findByText('Ưu đãi cho gói này')).toBeInTheDocument();
    expect(screen.getAllByRole('link', { name: 'Xem và lưu mã' })).toHaveLength(3);
    expect(screen.queryByText(/Đăng nhập để dùng mã giảm/)).toBeNull();
  });
});
