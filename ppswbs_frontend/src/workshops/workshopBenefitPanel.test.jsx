import React, { useState } from 'react';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { I18nProvider } from '../shared/i18n/I18nProvider';
import { WorkshopBenefitPanel } from './WorkshopBenefitPanel';

const quote = {
  grossAmount: '2000000', programDiscount: '200000', pointsUsed: '0', pointDiscount: '0', postPromotionAmount: '1800000',
  depositAmount: '900000', remainingAmount: '900000', maxPoints: '500', reasons: [],
};

const account = { balancePoints: '1500', heldPoints: '200', availablePoints: '1300', policy: { redeemPointValue: '100', minRedeemPoints: '10' } };

function Harness({ offers = [], quoteMatchesValue = true, maxPoints = null, quoteValue = null, accountValue = account, onRecalculate = () => {}, onSelection = () => {} }) {
  const [selection, setSelection] = useState({ choice: 'NONE', voucherCode: '', requestedPoints: '0' });
  return (
    <I18nProvider initialLanguage="vi">
      <WorkshopBenefitPanel selection={selection} onChange={next => { setSelection(next); onSelection(next); }}
        account={accountValue} offers={offers} savedVouchers={[{ code: 'WK10', name: 'Giảm 10% workshop', availability: 'ACTIVE' }, { code: 'OLD', name: 'Hết hạn', availability: 'EXPIRED' }]}
        maxPoints={maxPoints} quote={quoteValue} quoteMatches={quoteMatchesValue} acknowledged={false} onAcknowledge={() => {}} onRecalculate={onRecalculate} pending={false} />
    </I18nProvider>
  );
}

describe('WorkshopBenefitPanel (same flow as the product checkout)', () => {
  it('opens a voucher picker with Kho Voucher, applies a saved voucher and can take it off again', async () => {
    const user = userEvent.setup();
    const seen = vi.fn();
    render(<Harness onSelection={seen} />);
    expect(screen.getByText('Chọn hoặc nhập mã')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: /Voucher MLAND/ }));
    expect(screen.queryByText('Hết hạn')).toBeNull(); // only usable vouchers are offered
    await user.click(screen.getByRole('button', { name: /Giảm 10% workshop/ }));
    expect(seen).toHaveBeenLastCalledWith({ choice: 'VOUCHER', voucherCode: 'WK10', requestedPoints: '0' });
    expect(screen.getByText('Giảm 10% workshop')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: /Voucher MLAND/ }));
    await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: /Giảm 10% workshop/ }));
    expect(seen).toHaveBeenLastCalledWith({ choice: 'NONE', voucherCode: '', requestedPoints: '0' });
  });

  it('accepts a typed code', async () => {
    const user = userEvent.setup();
    const seen = vi.fn();
    render(<Harness onSelection={seen} />);
    await user.click(screen.getByRole('button', { name: /Voucher MLAND/ }));
    await user.type(screen.getByPlaceholderText('Nhập mã voucher'), ' MYCODE ');
    await user.click(screen.getByRole('button', { name: 'Áp dụng' }));
    expect(seen).toHaveBeenLastCalledWith({ choice: 'VOUCHER', voucherCode: 'MYCODE', requestedPoints: '0' });
  });

  it('keeps the points switch off until a quote gives the limit, then uses all allowed points', async () => {
    const user = userEvent.setup();
    const seen = vi.fn();
    const { rerender } = render(<Harness onSelection={seen} />);
    expect(screen.getByRole('switch', { name: 'Dùng điểm thành viên' })).toBeDisabled();
    expect(screen.getByText('Đang tính số điểm tối đa được dùng…')).toBeInTheDocument();
    rerender(<Harness maxPoints="500" quoteValue={quote} onSelection={seen} />);
    const toggle = screen.getByRole('switch', { name: 'Dùng điểm thành viên' });
    expect(toggle).toBeEnabled();
    await user.click(toggle);
    expect(seen).toHaveBeenLastCalledWith({ choice: 'NONE', voucherCode: '', requestedPoints: '500' });
    await user.click(screen.getByRole('switch', { name: 'Dùng điểm thành viên' }));
    expect(seen).toHaveBeenLastCalledWith({ choice: 'NONE', voucherCode: '', requestedPoints: '0' });
  });

  it('shows the deposit and the remaining amount from the quote', () => {
    render(<Harness maxPoints="500" quoteValue={quote} />);
    expect(screen.getByText('Tiền đặt cọc')).toBeInTheDocument();
    expect(screen.getByText('Còn lại thanh toán sau')).toBeInTheDocument();
    expect(screen.getByRole('checkbox', { name: /Tôi đã xem tổng tiền/ })).toBeEnabled();
  });
});

describe('points the Member has, shown before choosing', () => {
  it('shows available points, held points, the point value and the minimum', () => {
    render(<Harness />);
    const row = screen.getByLabelText('Điểm của bạn');
    expect(row).toHaveTextContent(/1\.300 điểm khả dụng/);
    expect(row).toHaveTextContent(/đang giữ 200 điểm cho đơn khác/);
    expect(row).toHaveTextContent(/1 điểm = 100/);
    expect(row).toHaveTextContent(/dùng tối thiểu 10 điểm/);
  });

  it('says so when the points cannot be loaded, and warns when the limit is below the minimum', () => {
    const { rerender } = render(<Harness accountValue={null} />);
    expect(screen.getByText('Chưa tải được số điểm')).toBeInTheDocument();
    rerender(<Harness maxPoints="5" quoteValue={quote} />);
    expect(screen.getByText('Cần tối thiểu 10 điểm để sử dụng')).toBeInTheDocument();
    expect(screen.getByRole('switch', { name: 'Dùng điểm thành viên' })).toBeDisabled();
  });
});

describe('automatic quote', () => {
  it('has no recalculate button while the quote is up to date, and offers a retry when it is not', async () => {
    const user = userEvent.setup();
    const retry = vi.fn();
    const { rerender } = render(<Harness maxPoints="500" quoteValue={quote} />);
    expect(screen.queryByRole('button', { name: /tính lại/i })).toBeNull();
    rerender(<Harness maxPoints="500" quoteValue={quote} quoteMatchesValue={false} onRecalculate={retry} />);
    await user.click(screen.getByRole('button', { name: 'Thử tính lại' }));
    expect(retry).toHaveBeenCalledTimes(1);
  });
});

describe('vouchers offered in the picker', () => {
  it('lists running workshop vouchers the Member has not saved, with their benefit, and applies one', async () => {
    const user = userEvent.setup();
    const seen = vi.fn();
    const offers = [
      { code: 'MLYBUFK3GW', name: 'Trời mưa vui vui', discount: { kind: 'PERCENT', percentage: '10', maxDiscountAmount: '100000' } },
      { code: 'WK10', name: 'Giảm 10% workshop', discount: { kind: 'PERCENT', percentage: '10', maxDiscountAmount: '100000' } },
    ];
    render(<Harness offers={offers} onSelection={seen} />);
    await user.click(screen.getByRole('button', { name: /Voucher MLAND/ }));
    const dialog = within(screen.getByRole('dialog'));
    expect(dialog.getByText(/MLYBUFK3GW · Giảm 10%, tối đa/)).toBeInTheDocument();
    expect(dialog.getAllByText(/WK10/)).toHaveLength(1); // a saved voucher is not listed twice
    expect(dialog.getByText(/WK10 .*Trong Kho Voucher/)).toBeInTheDocument();
    await user.click(dialog.getByRole('button', { name: /Trời mưa vui vui/ }));
    expect(seen).toHaveBeenLastCalledWith({ choice: 'VOUCHER', voucherCode: 'MLYBUFK3GW', requestedPoints: '0' });
    expect(screen.getByText('Trời mưa vui vui')).toBeInTheDocument();
  });
});
