import React from 'react';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { json, mockFetch } from '../test/render';
import { HoldPaymentPanel } from './HoldPaymentPanel';

describe('HoldPaymentPanel', () => {
  it('shows the hold countdown and starts the deposit payment from the success page', async () => {
    const calls = mockFetch({
      'POST /api/v1/workshop-bookings/BK-1/payment-attempts': json(201, { paymentUrl: '', status: 'CREATED' }),
    });
    render(<HoldPaymentPanel bookingCode="BK-1" holdExpiresAt={new Date(Date.now() + 9 * 60 * 1000).toISOString()} />);

    expect(screen.getByRole('timer')).toHaveTextContent(/Chỗ được giữ thêm 8 phút|Chỗ được giữ thêm 9 phút/);
    await userEvent.setup().click(screen.getByRole('button', { name: 'Thanh toán đặt cọc 50% qua VNPay' }));

    expect(calls.some(call => call.path === '/api/v1/workshop-bookings/BK-1/payment-attempts')).toBe(true);
  });

  it('disables paying once the hold has ended', () => {
    render(<HoldPaymentPanel bookingCode="BK-1" holdExpiresAt={new Date(Date.now() - 1000).toISOString()} />);
    expect(screen.getByRole('timer')).toHaveTextContent(/Thời hạn giữ chỗ đã kết thúc/);
    expect(screen.getByRole('button', { name: 'Thanh toán đặt cọc 50% qua VNPay' })).toBeDisabled();
  });
});
