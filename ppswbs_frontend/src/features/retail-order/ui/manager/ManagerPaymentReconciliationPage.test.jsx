import React from 'react';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import ManagerPaymentReconciliationPage from './ManagerPaymentReconciliationPage';

const { list } = vi.hoisted(() => ({ list: vi.fn() }));
vi.mock('../../application/useRetailApi', () => ({ useRetailApi: () => ({ listPaymentReconciliations: list }) }));
vi.mock('../../../../shared/auth/RequireSession', () => ({ RequireSession: ({ children }) => children }));
vi.mock('../../../../shared/i18n/I18nProvider', () => ({
  LANGUAGES: ['vi'], useLanguage: () => ({ language: 'vi' }), useT: () => key => key,
  useFormat: () => ({ money: value => `${value} đ` }),
}));

describe('ManagerPaymentReconciliationPage', () => {
  beforeEach(() => { list.mockReset(); });

  it('loads, filters and refreshes the current page', async () => {
    list.mockResolvedValue([{ checkoutId: 'checkout-1', merchantReference: 'retail-1', providerTransaction: null,
      amountVnd: 150000, reason: 'QUERY_UNRESOLVED', createdAt: '2026-11-04T10:00:00Z' }]);
    render(<ManagerPaymentReconciliationPage />);
    expect(await screen.findByText('checkout-1')).toBeInTheDocument();
    expect(screen.getByText('Chưa có')).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText('Tìm mã đơn hoặc giao dịch'), { target: { value: 'không có' } });
    expect(screen.getByText('Không có giao dịch phù hợp với bộ lọc trên trang này.')).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText('Tìm mã đơn hoặc giao dịch'), { target: { value: '' } });
    fireEvent.click(screen.getByRole('button', { name: 'Làm mới' }));
    await waitFor(() => expect(list).toHaveBeenCalledTimes(2));
    expect(list).toHaveBeenLastCalledWith(0);
  });

  it('does not show stale data after an access error and allows retry', async () => {
    list.mockRejectedValueOnce({ status: 403, message: 'Forbidden' }).mockResolvedValueOnce([]);
    render(<ManagerPaymentReconciliationPage />);
    expect(await screen.findByRole('alert')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Thử lại' }));
    expect(await screen.findByText('Chưa có giao dịch cần đối soát trên trang này.')).toBeInTheDocument();
  });
});
