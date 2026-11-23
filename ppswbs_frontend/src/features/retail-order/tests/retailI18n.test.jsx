import React from 'react';
import { describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import { I18nProvider, useLanguage } from '../../../shared/i18n/I18nProvider';
import { RetailBenefitPanel } from '../ui/checkout/RetailBenefitPanel';
import { displayTime } from '../ui/orderCode';
import { messages } from '../messages';

function LanguageToggle() {
  const { setLanguage } = useLanguage();
  return <>
    <button type="button" onClick={() => setLanguage('en')}>to-en</button>
    <button type="button" onClick={() => setLanguage('vi')}>to-vi</button>
  </>;
}

describe('Retail orders i18n', () => {
  it('keeps vi and en dictionaries on the same key set with the same placeholders', () => {
    expect(Object.keys(messages.en).sort()).toEqual(Object.keys(messages.vi).sort());
    const placeholders = text => [...text.matchAll(/\{(\w+)\}/g)].map(match => match[1]).sort();
    Object.keys(messages.vi).forEach(key => expect(placeholders(messages.en[key]), key).toEqual(placeholders(messages.vi[key])));
  });

  it('switches the benefit panel between vi and en without touching voucher codes or amounts', () => {
    window.localStorage.clear();
    const props = { selection: { choice: 'AUTO', voucherCode: '', requestedPoints: '0' }, onChange: vi.fn(), savedVouchers: [], maxPoints: '60', minRedeemPoints: '100',
      quote: null, quoteMatches: false, acknowledged: false, onAcknowledge: vi.fn(), onRecalculate: vi.fn(), onConfirm: vi.fn(), pending: false };
    render(<I18nProvider initialLanguage="vi"><LanguageToggle /><RetailBenefitPanel {...props} /></I18nProvider>);
    expect(screen.getByText('Cần tối thiểu 100 điểm để sử dụng')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Tính lại ưu đãi' })).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'to-en' }));
    expect(screen.getByText('At least 100 points are needed')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Recalculate' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Tính lại ưu đãi' })).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'to-vi' }));
    expect(screen.getByRole('button', { name: 'Tính lại ưu đãi' })).toBeInTheDocument();
  });

  it('writes the branch clock in the chosen language', () => {
    expect(displayTime('2026-11-04T10:00:00Z', 'vi')).toMatch(/17:00/);
    expect(displayTime('2026-11-04T10:00:00Z', 'en')).toMatch(/5:00/);
    expect(displayTime(null, 'en')).toBe('—');
  });
});

describe('Retail checkout refusal codes', () => {
  it('has vi and en text for the codes the order step refuses with', () => {
    ['error.RETAIL_VOUCHER_REFUSED', 'error.RETAIL_POINTS_CHANGED', 'checkout.benefitChanged'].forEach(key => {
      expect(messages.vi[key], key).toBeTruthy();
      expect(messages.en[key], key).toBeTruthy();
      expect(messages.en[key]).not.toEqual(messages.vi[key]);
    });
  });
});
