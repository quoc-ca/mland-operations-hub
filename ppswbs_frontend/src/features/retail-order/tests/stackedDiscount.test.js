import { describe, expect, it } from 'vitest';
import { discountLines } from '../ui/checkout/MemberCheckoutPage';
import { reasonsInVietnamese } from '../ui/checkout/voucherOptions';

// FE-06 stacks the automatic discount with one voucher (Session 2026-11-22). Example of the FE-06 note:
// A 600,000 + B 400,000, 10% automatic on both (100,000), a 20% voucher on B after it (72,000).
describe('checkout discount lines (Shopee order)', () => {
  it('shows the automatic discount and the voucher on their own lines and adds them up as savings', () => {
    const lines = discountLines({ discountAmount: '172000', autoDiscountAmount: '100000', voucherDiscountAmount: '72000', pointsDiscountAmount: '5000', appliedVoucherCode: 'VC20' });
    expect(lines).toEqual({ product: '100000', voucher: '72000', points: '5000', saved: '177000' });
  });

  it('shows only the automatic discount when no voucher is chosen', () => {
    expect(discountLines({ discountAmount: '100000', autoDiscountAmount: '100000', voucherDiscountAmount: '0', pointsDiscountAmount: '0' }))
      .toEqual({ product: '100000', voucher: null, points: null, saved: '100000' });
  });

  it('labels an unsplit discount by whether a voucher code was applied', () => {
    expect(discountLines({ discountAmount: '30000', pointsDiscountAmount: '0' }).product).toBe('30000');
    expect(discountLines({ discountAmount: '30000', pointsDiscountAmount: '0', appliedVoucherCode: 'X' }).voucher).toBe('30000');
  });

  it('says in Vietnamese why a voucher was refused', () => {
    expect(reasonsInVietnamese(['Save this voucher in Kho Voucher before using it.', 'Other']))
      .toEqual(['Hãy lưu voucher này ở Kho Voucher trước khi dùng.', 'Other']);
    expect(reasonsInVietnamese(['The automatic discount already covers these items; this voucher gives nothing more.'])[0])
      .toBe('Giảm tự động đã trừ hết phần voucher này áp dụng, mã không giảm thêm được.');
  });
});

describe('voucher picker reason (2026-11-23)', () => {
  it('says why FE-06 refused a voucher, in Vietnamese', async () => {
    const { voucherReason } = await import('../ui/checkout/voucherOptions');
    expect(voucherReason(['No automatic discount applies to this cart.', 'This voucher does not apply at the branch of these items.']))
      .toBe('Voucher chỉ áp dụng tại chi nhánh khác, không phải chi nhánh bạn chọn mua.');
    expect(voucherReason([])).toBeNull();
  });
});
