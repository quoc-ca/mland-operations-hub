import React from 'react';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { MemoryRouter } from 'react-router';
import { I18nProvider, useLanguage } from '../../../../shared/i18n/I18nProvider';
import ManagerPaymentReconciliationPage from './ManagerPaymentReconciliationPage';

const { list } = vi.hoisted(() => ({ list: vi.fn() }));
vi.mock('../../application/useRetailApi', () => ({ useRetailApi: () => ({ listPaymentReconciliations: list }) }));
vi.mock('../../../../shared/auth/RequireSession', () => ({ RequireSession: ({ children }) => children }));

function ToEnglish() {
  const { setLanguage } = useLanguage();
  return <button type="button" onClick={() => setLanguage('en')}>to-en</button>;
}

const renderPage = () => render(<I18nProvider initialLanguage="vi"><MemoryRouter><ManagerPaymentReconciliationPage /></MemoryRouter></I18nProvider>);

describe('ManagerPaymentReconciliationPage', () => {
  beforeEach(() => { list.mockReset(); });

  it('loads, filters and refreshes the current page', async () => {
    list.mockResolvedValue([{ checkoutId: 'checkout-1', merchantReference: 'retail-1', providerTransaction: null,
      amountVnd: 150000, reason: 'QUERY_UNRESOLVED', createdAt: '2026-11-04T10:00:00Z' }]);
    renderPage();
    expect(await screen.findByText('checkout-1')).toBeInTheDocument();
    expect(screen.getByText('Chưa có')).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText('Tìm mã đơn hoặc giao dịch'), { target: { value: 'không có' } });
    expect(screen.getByText('Không có giao dịch phù hợp với bộ lọc trên trang này.')).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText('Tìm mã đơn hoặc giao dịch'), { target: { value: '' } });
    fireEvent.click(screen.getByRole('button', { name: 'Làm mới' }));
    await waitFor(() => expect(list).toHaveBeenCalledTimes(2));
    expect(list).toHaveBeenLastCalledWith(0);
  });

  it('switches the table to English while references and codes stay as received', async () => {
    list.mockResolvedValue([{ checkoutId: 'checkout-1', merchantReference: 'retail-1', providerTransaction: null,
      amountVnd: 150000, reason: 'LATE_OR_INVALID_STATE', createdAt: '2026-11-04T10:00:00Z' }]);
    render(<I18nProvider initialLanguage="vi"><ToEnglish /><MemoryRouter><ManagerPaymentReconciliationPage /></MemoryRouter></I18nProvider>);
    expect(await screen.findByText('Thanh toán về muộn hoặc đơn không còn hợp lệ')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'to-en' }));
    expect(screen.getByText('Late payment or the order is no longer valid')).toBeInTheDocument();
    expect(screen.getByText('None')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Refresh' })).toBeInTheDocument();
    expect(screen.getByText('checkout-1')).toBeInTheDocument();
    expect(screen.getByText('retail-1')).toBeInTheDocument();
    expect(screen.queryByText('Chưa có')).not.toBeInTheDocument();
  });

  it('does not show stale data after an access error and allows retry', async () => {
    list.mockRejectedValueOnce({ status: 403, message: 'Forbidden' }).mockResolvedValueOnce([]);
    renderPage();
    expect(await screen.findByRole('alert')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Thử lại' }));
    expect(await screen.findByText('Chưa có giao dịch cần đối soát trên trang này.')).toBeInTheDocument();
  });
});
