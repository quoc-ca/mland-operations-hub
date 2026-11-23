import React from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { I18nProvider, useLanguage } from '../../../shared/i18n/I18nProvider';
import NotificationRequestListPage from '../ui/NotificationRequestListPage';
import ResendConfirmationModal from '../ui/ResendConfirmationModal';
import { notificationMessages } from '../messages';

const mockApi = {
  listNotificationRequests: vi.fn(),
  getNotificationRequest: vi.fn(),
};

vi.mock('../application/useNotificationApi', () => ({
  useNotificationApi: () => mockApi,
}));

vi.mock('../../../shared/auth/useRole', () => ({
  useRole: () => 'MANAGER',
}));

function LanguageToggle() {
  const { setLanguage } = useLanguage();
  return <button type="button" onClick={() => setLanguage('en')}>to-en</button>;
}

describe('Notification management i18n', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    window.localStorage.clear();
    mockApi.listNotificationRequests.mockResolvedValue({
      content: [
        {
          id: 1001,
          businessEventReference: 'BK-20261106-001',
          templateCode: 'BOOKING_CONFIRMED',
          recipientAddressMasked: 'n***a@gmail.com',
          deliveryState: 'SENT',
          attemptCount: 1,
          createdAt: '2026-11-06T14:30:00Z',
        },
      ],
      totalElements: 1,
      totalPages: 1,
    });
  });

  it('keeps vi and en dictionaries on the same key set', () => {
    expect(Object.keys(notificationMessages.en).sort()).toEqual(Object.keys(notificationMessages.vi).sort());
  });

  it('switches the request list between vi and en while technical codes stay as received', async () => {
    render(
      <I18nProvider initialLanguage="vi">
        <LanguageToggle />
        <MemoryRouter>
          <NotificationRequestListPage />
        </MemoryRouter>
      </I18nProvider>
    );

    expect(await screen.findByText('BK-20261106-001')).toBeInTheDocument();
    expect(screen.getByLabelText('Trạng thái gửi')).toBeInTheDocument();
    expect(screen.getByRole('option', { name: 'Đã gửi thành công (SENT)' })).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'to-en' }));

    expect(screen.getByLabelText('Delivery status')).toBeInTheDocument();
    expect(screen.getByRole('option', { name: 'Sent successfully (SENT)' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'View details #1001' })).toBeInTheDocument();
    expect(screen.getByText('SENT')).toBeInTheDocument();
    expect(screen.getByText('BOOKING_CONFIRMED')).toBeInTheDocument();
    expect(screen.queryByText('Trạng thái gửi')).not.toBeInTheDocument();
  });

  it('shows the English permission-denied message for managers on UNKNOWN_OUTCOME', () => {
    render(
      <I18nProvider initialLanguage="en">
        <ResendConfirmationModal
          isOpen
          onClose={vi.fn()}
          requestItem={{
            id: 1003,
            businessEventReference: 'BK-1',
            templateCode: 'BOOKING_CONFIRMED',
            recipientAddressMasked: 'x***z@example.com',
            deliveryState: 'UNKNOWN_OUTCOME',
          }}
          onResend={vi.fn()}
          userRole="MANAGER"
        />
      </I18nProvider>
    );

    expect(screen.getByText(/Permission denied/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Confirm resend' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Close' })).toBeInTheDocument();
  });
});
