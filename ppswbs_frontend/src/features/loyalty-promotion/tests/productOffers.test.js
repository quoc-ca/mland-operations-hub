import { describe, expect, it } from 'vitest';
import { covers, offersForProduct, unitDiscount } from '../domain/productOffers';

const product = {
  id: '20', name: 'Nhẫn bạc khắc',
  variants: [{ id: '201', size: 12, appliedPrice: '106000' }, { id: '202', size: 14, appliedPrice: '200000' }],
};
const program = patch => ({
  id: '1', kind: 'AUTO_DISCOUNT', scope: 'PRODUCT', name: 'Giảm 11.11', code: null,
  discount: { kind: 'PERCENT', percentage: '10', maxDiscountAmount: '50000' }, minimumEligibleAmount: '0',
  targets: { productIds: ['20'] }, ...patch,
});

describe('unit discount (same rules as the retail quote, one item)', () => {
  it('floors a percentage, keeps it under the cap and never above the price', () => {
    expect(unitDiscount(program(), '106000')).toBe(10600n);
    expect(unitDiscount(program({ discount: { kind: 'PERCENT', percentage: '12.5', maxDiscountAmount: '1000000' } }), '99999')).toBe(12499n);
    expect(unitDiscount(program({ discount: { kind: 'PERCENT', percentage: '50', maxDiscountAmount: '20000' } }), '106000')).toBe(20000n);
    expect(unitDiscount(program({ discount: { kind: 'FIXED', amount: '500000', maxDiscountAmount: '500000' } }), '106000')).toBe(106000n);
  });

  it('gives nothing below the minimum order amount', () => {
    expect(unitDiscount(program({ minimumEligibleAmount: '150000' }), '106000')).toBe(0n);
    expect(unitDiscount(program({ minimumEligibleAmount: '150000' }), '200000')).toBe(20000n);
  });

  it('covers a size only when it matches every dimension used', () => {
    expect(covers(program(), product, product.variants[0])).toBe(true);
    expect(covers(program({ targets: { variantIds: ['202'] } }), product, product.variants[0])).toBe(false);
    expect(covers(program({ targets: { productIds: ['20'], variantIds: ['202'] } }), product, product.variants[1])).toBe(true);
    expect(covers(program({ targets: { productIds: ['99'] } }), product, product.variants[0])).toBe(false);
  });

  it('a voucher limited to branches covers only sizes sold at one of them (Session 2026-11-09)', () => {
    const limited = program({ kind: 'VOUCHER', targets: { productIds: ['20'], branchIds: ['7'] } });
    const at = (...ids) => ({ id: '201', size: 12, appliedPrice: '106000', assignedToBranches: true, branches: ids.map(locationId => ({ locationId, canBuy: true })) });
    expect(covers(limited, product, at('7', '8'))).toBe(true);
    expect(covers(limited, product, at('8'))).toBe(false);
    // Not yet assigned to branches, or no branch facts on the page: not narrowed.
    expect(covers(limited, product, { ...at(), assignedToBranches: false })).toBe(true);
    expect(covers(limited, product, product.variants[0])).toBe(true);
    expect(offersForProduct({ ...product, variants: [at('8')] }, [limited]).vouchers).toHaveLength(0);
  });
});

describe('offers of a product', () => {
  it('takes the largest automatic discount off each size and lists vouchers as badges only', () => {
    const offers = offersForProduct(product, [
      program(),
      program({ id: '2', discount: { kind: 'FIXED', amount: '15000', maxDiscountAmount: '15000' } }),
      program({ id: '3', kind: 'VOUCHER', code: 'ABC', discount: { kind: 'PERCENT', percentage: '20', maxDiscountAmount: '100000' } }),
    ]);
    expect(offers.sizes['201']).toMatchObject({ discount: 15000n, finalPrice: 91000n });
    expect(offers.sizes['202']).toMatchObject({ discount: 20000n, finalPrice: 180000n });
    expect(offers.original).toEqual({ minimum: 106000n, maximum: 200000n });
    expect(offers.final).toEqual({ minimum: 91000n, maximum: 180000n });
    expect(offers.auto.id).toBe('1');
    expect(offers.vouchers.map(item => item.id)).toEqual(['3']);
  });

  it('tells which sizes a programme covers when it is not every size', () => {
    const offers = offersForProduct(product, [
      program({ targets: { variantIds: ['202'] } }),
      program({ id: '3', kind: 'VOUCHER', code: 'ABC', targets: { productIds: ['20'] } }),
    ]);
    expect(offers.sizes['201'].discount).toBe(0n);
    expect(offers.coverage['1']).toEqual({ all: false, sizes: [14] });
    expect(offers.coverage['3']).toEqual({ all: true, sizes: [12, 14] });
  });

  it('shows an automatic discount it cannot take off one item as one for a bigger order', () => {
    const offers = offersForProduct(product, [program({ minimumEligibleAmount: '500000' })]);
    expect(offers.discounted).toBe(false);
    expect(offers.autoForBiggerOrder.map(item => item.id)).toEqual(['1']);
  });

  it('ignores workshop programmes and products out of scope', () => {
    const offers = offersForProduct(product, [program({ scope: 'WORKSHOP' }), program({ id: '5', targets: { productIds: ['99'] } })]);
    expect(offers.discounted).toBe(false);
    expect(offers.vouchers).toEqual([]);
    expect(offers.autoForBiggerOrder).toEqual([]);
  });
});
