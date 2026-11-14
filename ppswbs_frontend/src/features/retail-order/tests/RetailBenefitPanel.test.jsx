import React from 'react';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { I18nProvider } from '../../../shared/i18n/I18nProvider';
import { RetailBenefitPanel } from '../ui/checkout/RetailBenefitPanel';

function show(overrides = {}) {
  const props = { selection: { choice: 'AUTO', voucherCode: '', requestedPoints: '0' }, onChange: vi.fn(),
    savedVouchers: [], maxPoints: null, minRedeemPoints: null, quote: null, quoteMatches: false, acknowledged: false,
    onAcknowledge: vi.fn(), onRecalculate: vi.fn(), onConfirm: vi.fn(), pending: false, ...overrides };
  render(<I18nProvider initialLanguage="vi"><RetailBenefitPanel {...props} /></I18nProvider>);
  return props;
}

describe('FE-08 benefit selection', () => {
  it('uses the points limit returned by the quote and never enables a zero limit', async () => {
    const props = show({ maxPoints: '20', minRedeemPoints: '10' });
    await userEvent.setup().click(screen.getByRole('checkbox'));
    expect(props.onChange).toHaveBeenCalledWith({ choice: 'AUTO', voucherCode: '', requestedPoints: '20' });
  });

  it('deselects a selected saved voucher when clicked again', async () => {
    const props = show({ selection: { choice: 'VOUCHER', voucherCode: 'SALE', requestedPoints: '0' }, savedVouchers: [{ code: 'SALE', name: 'Giảm giá', availability: 'ACTIVE' }] });
    const user = userEvent.setup();
    await user.click(screen.getByRole('button', { name: /Voucher MLAND/ }));
    await user.click(within(screen.getByRole('dialog')).getByRole('button', { pressed: true }));
    expect(props.onChange).toHaveBeenCalledWith({ choice: 'NONE', voucherCode: '', requestedPoints: '0' });
  });

  it('allows a manually entered voucher to be deselected', async () => {
    const props = show({ selection: { choice: 'VOUCHER', voucherCode: 'NHAPMA', requestedPoints: '0' } });
    const user = userEvent.setup();
    await user.click(screen.getByRole('button', { name: /Voucher MLAND/ }));
    await user.click(within(screen.getByRole('dialog')).getByRole('button', { pressed: true }));
    expect(props.onChange).toHaveBeenCalledWith({ choice: 'NONE', voucherCode: '', requestedPoints: '0' });
  });

  it('requires a server limit before points can be selected', () => {
    show();
    expect(screen.getByRole('checkbox')).toBeDisabled();
    expect(screen.getByText('Tính lại ưu đãi để xem điểm tối đa')).toBeInTheDocument();
  });

  it('disables points when the maximum is below the required minimum', () => {
    show({ maxPoints: '60', minRedeemPoints: '100' });
    expect(screen.getByRole('checkbox')).toBeDisabled();
    expect(screen.getByText('Cần tối thiểu 100 điểm để sử dụng')).toBeInTheDocument();
  });

  it('allows points when the maximum equals the required minimum', () => {
    show({ maxPoints: '100', minRedeemPoints: '100' });
    expect(screen.getByRole('checkbox')).toBeEnabled();
  });
});
