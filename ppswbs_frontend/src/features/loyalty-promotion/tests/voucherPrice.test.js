import { describe, expect, it } from 'vitest';
import { countsForPrice, needsSaving, offersForProduct } from '../domain/productOffers';
import { offersForPackage } from '../domain/workshopOffers';
import { mergeWallet } from '../application/usePublicProductPrograms';

// Session 2026-11-22, FR-046/FR-048: which vouchers the displayed price counts.

const product = {
  id: '20', name: 'Nhẫn bạc',
  variants: [{ id: '201', size: 12, appliedPrice: '1000000' }, { id: '202', size: 14, appliedPrice: '2000000' }],
};
const auto = { id: '1', kind: 'AUTO_DISCOUNT', scope: 'PRODUCT', code: null, name: 'Giảm 10%', minimumEligibleAmount: '0',
  discount: { kind: 'PERCENT', percentage: '10', maxDiscountAmount: '5000000' }, targets: { productIds: ['20'] } };
const voucher = patch => ({ id: '9', kind: 'VOUCHER', scope: 'PRODUCT', code: 'V9', name: 'Mã V9', minimumEligibleAmount: '0',
  distribution: 'PUBLIC', availability: 'ACTIVE', discount: { kind: 'FIXED', amount: '50000', maxDiscountAmount: '50000' },
  targets: { productIds: ['20'] }, ...patch });

describe('countsForPrice (FR-048)', () => {
  it('counts a usable PUBLIC voucher and a voucher ACTIVE in the wallet, nothing else', () => {
    expect(countsForPrice(voucher())).toBe(true);
    expect(countsForPrice(voucher({ availability: 'NO_USES_LEFT' }))).toBe(false);
    expect(countsForPrice(voucher({ distribution: 'CLAIM_REQUIRED' }))).toBe(false);
    expect(countsForPrice(voucher({ distribution: 'CLAIM_REQUIRED', availability: 'NO_USES_LEFT', saved: true, walletAvailability: 'ACTIVE' }))).toBe(true);
    expect(countsForPrice(voucher({ saved: true, walletAvailability: 'MEMBER_LIMIT_REACHED' }))).toBe(false);
    expect(countsForPrice(auto)).toBe(false);
  });

  it('marks a CLAIM_REQUIRED voucher not saved as one to save', () => {
    expect(needsSaving(voucher({ distribution: 'CLAIM_REQUIRED' }))).toBe(true);
    expect(needsSaving(voucher({ distribution: 'CLAIM_REQUIRED', saved: true }))).toBe(false);
    expect(needsSaving(voucher())).toBe(false);
  });
});

describe('product price after the automatic discount and the best voucher', () => {
  it('takes the voucher off the price after the automatic discount and names it', () => {
    const offers = offersForProduct(product, [auto, voucher()]);
    expect(offers.sizes['201']).toMatchObject({ finalPrice: 900000n, voucherDiscount: 50000n, priceAfterVoucher: 850000n });
    expect(offers.afterVoucher).toEqual({ minimum: 850000n, maximum: 1750000n });
    expect(offers.bestVoucher.code).toBe('V9');
    expect(offers.vouchers[0].code).toBe('V9');
  });

  it('measures the voucher minimum on the price after the automatic discount', () => {
    // 1.000.000 − 10% = 900.000 < 950.000: not on size 12; size 14 (1.800.000) reaches it.
    const offers = offersForProduct(product, [auto, voucher({ minimumEligibleAmount: '950000' })]);
    expect(offers.sizes['201'].voucher).toBeNull();
    expect(offers.sizes['202'].priceAfterVoucher).toBe(1750000n);
  });

  it('never counts a voucher that must be saved and is not', () => {
    const offers = offersForProduct(product, [auto, voucher({ id: '8', distribution: 'CLAIM_REQUIRED' })]);
    expect(offers.bestVoucher).toBeNull();
    expect(offers.vouchers).toHaveLength(1);
  });

  it('counts a voucher limited to branches on the card, and only at its branch once a branch is chosen (Session 2026-11-23)', () => {
    const branchVoucher = voucher({ id: '7', code: 'MLHK80', targets: { productIds: ['20'], branchIds: ['3'] },
      discount: { kind: 'FIXED', amount: '80000', maxDiscountAmount: '80000' } });
    const everywhere = voucher();
    // Card: the branch voucher (80.000) beats the one for every branch (50.000).
    expect(offersForProduct(product, [auto, branchVoucher, everywhere]).bestVoucher.code).toBe('MLHK80');
    // Detail page at branch 3: still MLHK80; at branch 4 it does not apply, so the price uses V9.
    expect(offersForProduct(product, [auto, branchVoucher, everywhere], { branchId: '3' }).sizes['201'].priceAfterVoucher).toBe(820000n);
    const elsewhere = offersForProduct(product, [auto, branchVoucher, everywhere], { branchId: 4 });
    expect(elsewhere.bestVoucher.code).toBe('V9');
    expect(elsewhere.sizes['201'].priceAfterVoucher).toBe(850000n);
    // A size sold only at other branches is not covered by the branch voucher at all.
    const notSoldThere = { ...product, variants: [{ id: '201', size: 12, appliedPrice: '1000000', assignedToBranches: true, branches: [{ locationId: 4 }] }] };
    expect(offersForProduct(notSoldThere, [auto, branchVoucher]).bestVoucher).toBeNull();
  });

  it('counts a saved voucher that beats every public one (Member view)', () => {
    const saved = voucher({ id: '8', code: 'LUU8', distribution: 'CLAIM_REQUIRED', saved: true, walletAvailability: 'ACTIVE',
      discount: { kind: 'FIXED', amount: '80000', maxDiscountAmount: '80000' } });
    expect(offersForProduct(product, [auto, voucher(), saved]).bestVoucher.code).toBe('LUU8');
    // Guest view: the same voucher not saved is not counted, so the public one is.
    const unsaved = { ...saved, saved: undefined, walletAvailability: undefined };
    expect(offersForProduct(product, [auto, voucher(), unsaved]).bestVoucher.code).toBe('V9');
  });
});

describe('workshop ticket price (FR-045(4) as changed by FR-048)', () => {
  const pkg = { packageId: '5', publicPrice: '500000' };
  const ws = patch => voucher({ scope: 'WORKSHOP', targets: { packageIds: ['5'] }, ...patch });
  it('counts the saved voucher when it beats the public one, and skips one that must be saved', () => {
    const publicOne = ws({ id: '1', code: 'PUB' });
    const claim = ws({ id: '2', code: 'LUU', distribution: 'CLAIM_REQUIRED', discount: { kind: 'FIXED', amount: '90000', maxDiscountAmount: '90000' } });
    expect(offersForPackage(pkg, [publicOne, claim]).bestVoucher.program.code).toBe('PUB');
    expect(offersForPackage(pkg, [publicOne, { ...claim, saved: true, walletAvailability: 'ACTIVE' }]).bestVoucher.program.code).toBe('LUU');
  });
});

describe('mergeWallet', () => {
  it('marks saved vouchers and adds an ACTIVE saved voucher the public list did not return', () => {
    const merged = mergeWallet([voucher({ id: '11' }), auto], [
      { promotion: voucher({ id: '11' }), availability: 'MEMBER_LIMIT_REACHED' },
      { promotion: voucher({ id: '13', code: 'MORE' }), availability: 'ACTIVE' },
      { promotion: voucher({ id: '14' }), availability: 'EXPIRED' },
    ]);
    expect(merged.find(p => p.id === '11')).toMatchObject({ saved: true, walletAvailability: 'MEMBER_LIMIT_REACHED' });
    expect(merged.find(p => p.id === '13')).toMatchObject({ saved: true, code: 'MORE' });
    expect(merged.find(p => p.id === '14')).toBeUndefined();
    expect(merged.find(p => p.kind === 'AUTO_DISCOUNT').saved).toBeUndefined();
  });
});

describe('workshop voucher labels are per account', () => {
  const pkg = { packageId: 5, publicPrice: '900000' };
  const program = patch => voucher({ scope: 'WORKSHOP', targets: {}, ...patch });
  const open = program({ id: '1', code: 'OPEN' });
  const mine = program({ id: '2', code: 'MINE' });
  const soldOut = program({ id: '3', code: 'SOLDOUT', availability: 'NO_USES_LEFT' });
  const claim = program({ id: '4', code: 'CLAIM', distribution: 'CLAIM_REQUIRED' });
  const claimGone = program({ id: '5', code: 'CLAIMGONE', distribution: 'CLAIM_REQUIRED', availability: 'NO_USES_LEFT' });
  const bigOrder = program({ id: '6', code: 'BIG', minimumEligibleAmount: '1000000' });
  const all = [open, mine, soldOut, claim, claimGone, bigOrder];
  const codes = programs => offersForPackage(pkg, programs).vouchers.map(item => item.code).sort();
  const wallet = availability => [{ promotion: mine, availability }];

  it('hides a used-up voucher only from the account that used it up', () => {
    expect(codes(mergeWallet(all, wallet('MEMBER_LIMIT_REACHED')))).toEqual(['BIG', 'CLAIM', 'OPEN']);
    // Another account, and a Guest, still see it.
    expect(codes(mergeWallet(all, wallet('ACTIVE')))).toEqual(['BIG', 'CLAIM', 'MINE', 'OPEN']);
    expect(codes(mergeWallet(all, []))).toEqual(['BIG', 'CLAIM', 'MINE', 'OPEN']);
  });

  it('keeps a saved claim voucher whose last use is held by this account', () => {
    const saved = mergeWallet(all, [{ promotion: claimGone, availability: 'ACTIVE' }]);
    expect(codes(saved)).toContain('CLAIMGONE');
  });
});
