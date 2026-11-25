import { describe, expect, it } from 'vitest';
import { quantityProblem } from '../ui/cart/MemberCartPage';
import { ghtkAddressProblem } from '../ui/checkout/MemberCheckoutPage';
import { translate } from '../../../shared/i18n/I18nProvider';
import { messages } from '../messages';

const t = (key, params) => translate(messages, 'vi', key, params);

describe('FE-08 input checks', () => {
  it('explains a refused cart quantity', () => {
    expect(quantityProblem('', null, t)).toBe('Nhập số lượng từ 1 đến 99.');
    expect(quantityProblem('2.5', null, t)).toBe('Số lượng phải là số nguyên dương.');
    expect(quantityProblem('-1', null, t)).toBe('Số lượng phải là số nguyên dương.');
    expect(quantityProblem('0', null, t)).toBe('Số lượng phải từ 1 đến 99.');
    expect(quantityProblem('100', null, t)).toBe('Số lượng phải từ 1 đến 99.');
    expect(quantityProblem('5', '3', t)).toBe('Chi nhánh chỉ còn 3 sản phẩm này.');
    expect(quantityProblem(' 3 ', '3', t)).toBe('');
    expect(quantityProblem(2, null, t)).toBe('');
  });

  const good = { recipientName: 'Nguyễn Văn A', phoneNumber: '0912345678', provinceName: 'Hà Nội', communeName: 'Phường Láng', detailAddress: 'Số 3' };

  it('accepts an address GHTK can use', () => {
    expect(ghtkAddressProblem(good, t)).toBe('');
    expect(ghtkAddressProblem({ ...good, phoneNumber: '+84912345678' }, t)).toBe('');
  });

  it('flags a saved address the server would refuse for GHTK', () => {
    expect(ghtkAddressProblem({ ...good, recipientName: 'A'.repeat(101) }, t)).toMatch(/tên người nhận dài 101 ký tự/);
    expect(ghtkAddressProblem({ ...good, phoneNumber: '091234' }, t)).toMatch(/số điện thoại không hợp lệ/);
    expect(ghtkAddressProblem({ ...good, communeName: '', wardName: '' }, t)).toMatch(/thiếu tỉnh\/thành hoặc phường\/xã/);
    expect(ghtkAddressProblem({ ...good, detailAddress: '' }, t)).toMatch(/thiếu địa chỉ cụ thể/);
    expect(ghtkAddressProblem(null, t)).toMatch(/Hãy thêm và chọn một địa chỉ/);
  });
});
