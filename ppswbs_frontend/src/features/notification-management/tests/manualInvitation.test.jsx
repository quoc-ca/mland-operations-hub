import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { NotificationRequestDetailDrawer } from '../ui/NotificationRequestDetailDrawer';
import { notificationApiClient } from '../infrastructure/notificationApiClient';
import { I18nProvider } from '../../../shared/i18n/I18nProvider';

const mockRequest = vi.fn();
const mockApi = {
  getNotificationRequest: vi.fn(),
  resendNotificationRequest: vi.fn(),
  cancelNotificationRequest: vi.fn(),
  redeliverNotificationRequest: vi.fn(),
  getManualInvitation: vi.fn(),
};

vi.mock('../application/useNotificationApi', () => ({
  useNotificationApi: () => mockApi,
}));

describe('FE-007 R-INV Manual Invitation & Isolation (FR-033, FR-034, T042)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('notificationApiClient calls GET /api/v1/admin/notification-requests/{id}/manual-invitation', async () => {
    mockRequest.mockResolvedValue({
      requestId: 555,
      invitationVersion: 1,
      subject: 'Tiêu đề thư mời',
      html: '<p>Link</p>',
      plainText: 'Link: https://mland.vn/invitations/setup?secret=test',
      expiresAt: '2026-11-27T10:00:00Z',
      serverNow: '2026-11-25T10:00:00Z',
    });

    const client = notificationApiClient(mockRequest);
    const result = await client.getManualInvitation(555);

    expect(mockRequest).toHaveBeenCalledWith(
      '/api/v1/admin/notification-requests/555/manual-invitation'
    );
    expect(result.requestId).toBe(555);
  });

  it('NotificationRequestDetailDrawer excludes generic resend/cancel/redeliver for INTERNAL_ACCOUNT_INVITATION', async () => {
    const mockDetail = {
      id: 901,
      templateCode: 'INTERNAL_ACCOUNT_INVITATION',
      deliveryState: 'FAILED',
      attempts: 2,
      recipientSnapshot: { email: 'staff.invite@mland.vn' },
      audits: [],
      deliveryAttempts: [
        { attemptNumber: 1, state: 'FAILED', failureReason: 'SMTP connection drop', attemptedAt: '2026-11-25T01:00:00Z' },
        { attemptNumber: 2, state: 'FAILED', failureReason: 'SMTP connection drop', attemptedAt: '2026-11-25T01:01:00Z' },
      ],
    };

    mockApi.getNotificationRequest.mockResolvedValue(mockDetail);

    render(
      <I18nProvider initialLocale="vi">
        <NotificationRequestDetailDrawer
          isOpen={true}
          onClose={() => {}}
          requestId={901}
          userRole="ADMIN"
        />
      </I18nProvider>
    );

    // Wait for drawer to render
    expect(await screen.findByTestId('internal-invitation-notice')).toBeInTheDocument();
    expect(screen.getByText(/Lời mời tài khoản nội bộ \(R-INV\)/i)).toBeInTheDocument();

    // Verify generic action buttons are disabled for INTERNAL_ACCOUNT_INVITATION
    const resendBtn = screen.getByRole('button', { name: 'Gửi lại thư' });
    const cancelBtn = screen.getByRole('button', { name: 'Hủy gửi' });
    const redeliverBtn = screen.getByRole('button', { name: 'Gửi lại với link/vé mới' });

    expect(resendBtn).toBeDisabled();
    expect(cancelBtn).toBeDisabled();
    expect(redeliverBtn).toBeDisabled();

    // Manual invitation button is present for ADMIN when state is FAILED
    const manualBtn = screen.getByTestId('manual-invitation-btn');
    expect(manualBtn).toBeInTheDocument();
    expect(manualBtn).not.toBeDisabled();
  });

  it('allows ADMIN to fetch and copy manual invitation content when state is FAILED', async () => {
    const mockDetail = {
      id: 902,
      templateCode: 'INTERNAL_ACCOUNT_INVITATION',
      deliveryState: 'FAILED',
      attempts: 2,
      recipientSnapshot: { email: 'staff.manual@mland.vn' },
      audits: [],
      deliveryAttempts: [],
    };

    const mockManualContent = {
      requestId: 902,
      invitationVersion: 1,
      subject: '[Mland] Mời kích hoạt tài khoản nhân viên',
      html: '<p>Link: https://mland.vn/invitations/setup?secret=manual_sec_999</p>',
      plainText: 'Link kích hoạt: https://mland.vn/invitations/setup?secret=manual_sec_999',
      expiresAt: '2026-11-27T10:00:00Z',
      serverNow: '2026-11-25T10:00:00Z',
    };

    mockApi.getNotificationRequest.mockResolvedValue(mockDetail);
    mockApi.getManualInvitation.mockResolvedValue(mockManualContent);

    render(
      <I18nProvider initialLocale="vi">
        <NotificationRequestDetailDrawer
          isOpen={true}
          onClose={() => {}}
          requestId={902}
          userRole="ADMIN"
        />
      </I18nProvider>
    );

    // Wait for detail
    await waitFor(() => {
      expect(screen.getByTestId('manual-invitation-btn')).toBeInTheDocument();
    });

    // Click manual invitation button
    await userEvent.click(screen.getByTestId('manual-invitation-btn'));

    // Manual modal appears with content
    expect(await screen.findByText(/Sao chép nội dung thư mời thủ công \(ADMIN\)/i)).toBeInTheDocument();
    expect(screen.getByText(/\[Mland\] Mời kích hoạt tài khoản nhân viên/i)).toBeInTheDocument();
    expect(screen.getByDisplayValue(/https:\/\/mland\.vn\/invitations\/setup\?secret=manual_sec_999/)).toBeInTheDocument();
  });
});
