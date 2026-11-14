import { describe, expect, it } from 'vitest';
import { checkCreate, checkDiscount, discountBody, effectiveFixedAmount } from '../domain/promotionRules';

// Session 2026-11-14: a fixed amount has no cap the Manager enters; only a percentage does.
const form = overrides => ({ discountKind: 'FIXED', percentage: '', amount: '200000', maxDiscountAmount: '', ...overrides });

describe('fixed discount without a cap (Session 2026-11-14)', () => {
  it('sends a fixed amount alone and a percentage with its cap', () => {
    expect(discountBody(form({ maxDiscountAmount: '150000' }))).toEqual({ kind: 'FIXED', amount: '200000' });
    expect(discountBody(form({ discountKind: 'PERCENT', percentage: '10', maxDiscountAmount: '50000' })))
      .toEqual({ kind: 'PERCENT', percentage: '10', maxDiscountAmount: '50000' });
  });

  it('asks for a cap only with a percentage', () => {
    const fixed = {};
    checkDiscount(form(), fixed);
    expect(fixed).toEqual({});
    const percent = {};
    checkDiscount(form({ discountKind: 'PERCENT', percentage: '10' }), percent);
    expect(percent).toEqual({ 'discount.maxDiscountAmount': 'validation.positive' });
    const noAmount = {};
    checkDiscount(form({ amount: '0' }), noAmount);
    expect(noAmount).toEqual({ 'discount.amount': 'validation.positive' });
  });

  it('does not flag the hidden cap on the create form', () => {
    const errors = checkCreate({
      kind: 'AUTO_DISCOUNT', scope: 'PRODUCT', name: 'Giảm 200k', startAt: '2026-11-15T00:00', endAt: '2026-12-15T00:00',
      ...form(), minimumEligibleAmount: '0', productIds: '42', variantIds: '', code: '', totalLimit: '', perMemberLimit: '',
      branchMode: 'ALL', packageIds: '', branchIds: '', sessionIds: '', workshopDates: '',
    });
    expect(errors['discount.maxDiscountAmount']).toBeUndefined();
    expect(errors['discount.amount']).toBeUndefined();
  });

  it('shows what an older fixed revision with a smaller cap actually takes off', () => {
    expect(effectiveFixedAmount({ kind: 'FIXED', amount: '200000', maxDiscountAmount: '150000' })).toBe(150000n);
    expect(effectiveFixedAmount({ kind: 'FIXED', amount: '200000', maxDiscountAmount: '200000' })).toBe(200000n);
    expect(effectiveFixedAmount({ kind: 'FIXED', amount: '200000' })).toBe(200000n);
  });
});
