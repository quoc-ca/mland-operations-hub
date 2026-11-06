import React from 'react';
import { render, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it } from 'vitest';
import { MemoryRouter } from 'react-router';
import { I18nProvider } from '../shared/i18n/I18nProvider';
import { AuthProvider } from '../shared/auth/AuthProvider';
import { fakeSession, json, mockFetch } from '../test/render';
import { WorkshopPaymentReturnPage } from './WorkshopPaymentReturnPage';

function renderReturn(role) {
  mockFetch({ 'GET /api/v1/accounts/me/profile': json(200, { role }) });
  window.history.replaceState(null, '', '/workshop-booking/BK-1/payment-return?vnp_ResponseCode=00&vnp_TransactionNo=1');
  return render(
    <I18nProvider initialLanguage="vi">
      <AuthProvider source={fakeSession()}>
        <MemoryRouter><WorkshopPaymentReturnPage bookingCode="BK-1" /></MemoryRouter>
      </AuthProvider>
    </I18nProvider>,
  );
}

describe('payment return opened in a staff session', () => {
  beforeEach(() => window.history.replaceState(null, '', '/'));

  it('tells a Staff where to continue instead of asking for a Member sign-in', async () => {
    renderReturn('STAFF');

    expect(await screen.findByText(/Bạn đang đăng nhập bằng tài khoản nhân viên \(STAFF\)/)).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Quay lại Hóa đơn quyết toán' })).toHaveAttribute('href', '/settlement');
    expect(screen.queryByText('Member verification unavailable')).toBeNull();
    expect(screen.queryByText(/Sign in again with a Member account/)).toBeNull();
  });

  it('does the same for a Manager', async () => {
    renderReturn('MANAGER');

    expect(await screen.findByText(/tài khoản nhân viên \(MANAGER\)/)).toBeInTheDocument();
  });

  it('keeps the Member check for a Member', async () => {
    renderReturn('MEMBER');

    expect(await screen.findByRole('button', { name: 'Check booking status' })).toBeInTheDocument();
    expect(screen.queryByText(/tài khoản nhân viên/)).toBeNull();
  });
});
