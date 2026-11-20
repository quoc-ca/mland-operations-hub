import { describe, expect, it } from 'vitest';
import { quantityProblem } from '../ui/cart/MemberCartPage';
import { ghtkAddressProblem } from '../ui/checkout/MemberCheckoutPage';

describe('FE-08 input checks', () => {
  it('explains a refused cart quantity', () => {
    expect(quantityProblem('', null)).toBe('Nhập số lượng từ 1 đến 99.');
    expect(quantityProblem('2.5', null)).toBe('Số lượng phải là số nguyên dương.');
    expect(quantityProblem('-1', null)).toBe('Số lượng phải là số nguyên dương.');
    expect(quantityProblem('0', null)).toBe('Số lượng phải từ 1 đến 99.');
    expect(quantityProblem('100', null)).toBe('Số lượng phải từ 1 đến 99.');
    expect(quantityProblem('5', '3')).toBe('Chi nhánh chỉ còn 3 sản phẩm này.');
    expect(quantityProblem(' 3 ', '3')).toBe('');
    expect(quantityProblem(2, null)).toBe('');
  });

  const good = { recipientName: 'Nguyễn Văn A', phoneNumber: '0912345678', provinceName: 'Hà Nội', communeName: 'Phường Láng', detailAddress: 'Số 3' };

  it('accepts an address GHTK can use', () => {
    expect(ghtkAddressProblem(good)).toBe('');
    expect(ghtkAddressProblem({ ...good, phoneNumber: '+84912345678' })).toBe('');
  });

  it('flags a saved address the server would refuse for GHTK', () => {
    expect(ghtkAddressProblem({ ...good, recipientName: 'A'.repeat(101) })).toMatch(/tên người nhận dài 101 ký tự/);
    expect(ghtkAddressProblem({ ...good, phoneNumber: '091234' })).toMatch(/số điện thoại không hợp lệ/);
    expect(ghtkAddressProblem({ ...good, communeName: '', wardName: '' })).toMatch(/thiếu tỉnh\/thành hoặc phường\/xã/);
    expect(ghtkAddressProblem({ ...good, detailAddress: '' })).toMatch(/thiếu địa chỉ cụ thể/);
    expect(ghtkAddressProblem(null)).toMatch(/Hãy thêm và chọn một địa chỉ/);
  });
});
