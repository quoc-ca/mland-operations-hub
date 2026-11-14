import React from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import NotificationRequestListPage from '../ui/NotificationRequestListPage';
import NotificationRequestDetailDrawer from '../ui/NotificationRequestDetailDrawer';
import ResendConfirmationModal from '../ui/ResendConfirmationModal';
import CancelConfirmationModal from '../ui/CancelConfirmationModal';

const mockApi = {
  listNotificationRequests: vi.fn(),
  getNotificationRequest: vi.fn(),
  resendNotificationRequest: vi.fn(),
  cancelNotificationRequest: vi.fn(),
  redeliverNotificationRequest: vi.fn(),
};

vi.mock('../application/useNotificationApi', () => ({
  useNotificationApi: () => mockApi,
}));

vi.mock('../../../shared/ui/OperationsSidebar', () => ({
  default: () => <aside data-testid="sidebar">Sidebar</aside>,
}));

vi.mock('../../../shared/auth/useRole', () => ({
  useRole: () => 'MANAGER',
}));

const sampleRequests = {
  content: [
    {
      id: 1001,
      businessEventReference: 'BK-20261106-001',
      templateCode: 'BOOKING_CONFIRMED',
      recipientAddressMasked: 'n***a@gmail.com',
      deliveryState: 'SENT',
      attemptCount: 1,
      createdAt: '2026-11-06T14:30:00Z',
      terminalAt: '2026-11-06T14:30:25Z',
    },
    {
      id: 1002,
      businessEventReference: 'ORD-20261106-042',
      templateCode: 'RETAIL_ORDER_CONFIRMED',
      recipientAddressMasked: 't***b@yahoo.com',
      deliveryState: 'PENDING',
      attemptCount: 0,
      createdAt: '2026-11-06T14:31:00Z',
      terminalAt: null,
    },
  ],
  totalElements: 2,
  totalPages: 1,
};

const sampleDetail1001 = {
  id: 1001,
  businessEventReference: 'BK-20261106-001',
  templateCode: 'BOOKING_CONFIRMED',
  templateVersionId: 2,
  recipientRole: 'CUSTOMER',
  recipientAddressMasked: 'n***a@gmail.com',
  deliveryState: 'SENT',
  attemptCount: 1,
  createdAt: '2026-11-06T14:30:00Z',
  terminalAt: '2026-11-06T14:30:25Z',
  attempts: [
    {
      attemptNumber: 1,
      startedAt: '2026-11-06T14:30:05Z',
      completedAt: '2026-11-06T14:30:25Z',
      outcomeCategory: 'SUCCESS',
      providerMessageId: 'msg-abc-123',
      safeErrorSummary: null,
    },
  ],
  audits: [],
};

const sampleUnknownOutcome = {
  id: 1003,
  businessEventReference: 'BK-20261106-999',
  templateCode: 'BOOKING_CONFIRMED',
  recipientAddressMasked: 'x***z@example.com',
  deliveryState: 'UNKNOWN_OUTCOME',
  attemptCount: 1,
};

describe('Notification Operations UI', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders NotificationRequestListPage and filters items', async () => {
    mockApi.listNotificationRequests.mockResolvedValue(sampleRequests);

    render(
      <MemoryRouter>
        <NotificationRequestListPage />
      </MemoryRouter>
    );

    expect(await screen.findByText('BK-20261106-001')).toBeInTheDocument();
    expect(screen.getByText('ORD-20261106-042')).toBeInTheDocument();
    expect(screen.getByText('n***a@gmail.com')).toBeInTheDocument();
    expect(screen.getByText('SENT')).toBeInTheDocument();
    expect(screen.getByText('PENDING')).toBeInTheDocument();

    // Change state filter
    const stateSelect = screen.getByLabelText('Trạng thái gửi');
    fireEvent.change(stateSelect, { target: { value: 'SENT' } });

    await waitFor(() => {
      expect(mockApi.listNotificationRequests).toHaveBeenCalledWith(
        expect.objectContaining({ deliveryState: 'SENT' })
      );
    });
  });

  it('renders NotificationRequestDetailDrawer and allows Resend for SENT request', async () => {
    mockApi.getNotificationRequest.mockResolvedValue(sampleDetail1001);
    mockApi.resendNotificationRequest.mockResolvedValue({
      newRequestId: 1005,
      originalRequestId: 1001,
      deliveryState: 'PENDING',
    });

    render(
      <NotificationRequestDetailDrawer
        isOpen={true}
        onClose={vi.fn()}
        requestId={1001}
        userRole="MANAGER"
        onActionSuccess={vi.fn()}
      />
    );

    expect(await screen.findByText('Chi tiết yêu cầu gửi thư #1001')).toBeInTheDocument();
    expect(screen.getByText('msg-abc-123')).toBeInTheDocument();
    expect(screen.getByText('SUCCESS')).toBeInTheDocument();

    // Click "Gửi lại thư"
    const resendBtn = screen.getByRole('button', { name: 'Gửi lại thư' });
    expect(resendBtn).not.toBeDisabled();
    fireEvent.click(resendBtn);

    // Modal opens
    expect(screen.getByText('Gửi lại email thông báo #1001')).toBeInTheDocument();

    // Fill valid reason (>= 10 chars)
    const reasonInput = screen.getByLabelText(/Lý do gửi lại/i);
    fireEvent.change(reasonInput, {
      target: { value: 'Khách hàng liên hệ chưa nhận được mail' },
    });

    const confirmBtn = screen.getByRole('button', { name: 'Xác nhận gửi lại' });
    expect(confirmBtn).not.toBeDisabled();
    fireEvent.click(confirmBtn);

    await waitFor(() => {
      expect(mockApi.resendNotificationRequest).toHaveBeenCalledWith(
        1001,
        {
          reason: 'Khách hàng liên hệ chưa nhận được mail',
          duplicateRiskAccepted: false,
        }
      );
    });
  });

  it('enforces cancellation validation and calls cancel endpoint', async () => {
    const onCancel = vi.fn().mockResolvedValue({});
    const onClose = vi.fn();

    render(
      <CancelConfirmationModal
        isOpen={true}
        onClose={onClose}
        requestItem={{
          id: 1002,
          businessEventReference: 'ORD-20261106-042',
          templateCode: 'RETAIL_ORDER_CONFIRMED',
          recipientAddressMasked: 't***b@yahoo.com',
          deliveryState: 'PENDING',
        }}
        onCancel={onCancel}
      />
    );

    expect(screen.getByText('Hủy gửi yêu cầu thông báo #1002')).toBeInTheDocument();

    const confirmBtn = screen.getByRole('button', { name: 'Xác nhận hủy gửi' });
    expect(confirmBtn).toBeDisabled();

    const reasonInput = screen.getByLabelText(/Lý do hủy gửi/i);
    fireEvent.change(reasonInput, { target: { value: 'Khách hàng hủy đơn' } });

    expect(confirmBtn).not.toBeDisabled();
    fireEvent.click(confirmBtn);

    await waitFor(() => {
      expect(onCancel).toHaveBeenCalledWith(1002, 'Khách hàng hủy đơn');
    });
  });

  it('blocks Manager from resending UNKNOWN_OUTCOME and requires Admin risk check', () => {
    // 1. As Manager: should be blocked
    const { rerender } = render(
      <ResendConfirmationModal
        isOpen={true}
        onClose={vi.fn()}
        requestItem={sampleUnknownOutcome}
        onResend={vi.fn()}
        userRole="MANAGER"
      />
    );

    expect(screen.getByText(/Từ chối quyền/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Xác nhận gửi lại' })).toBeDisabled();

    // 2. As Admin: shows duplicate risk checkbox and requires it
    rerender(
      <ResendConfirmationModal
        isOpen={true}
        onClose={vi.fn()}
        requestItem={sampleUnknownOutcome}
        onResend={vi.fn()}
        userRole="ADMIN"
      />
    );

    expect(
      screen.getByText(/Tôi đã xác nhận với dịch vụ mail và chấp nhận rủi ro/i)
    ).toBeInTheDocument();

    const reasonInput = screen.getByLabelText(/Lý do gửi lại/i);
    fireEvent.change(reasonInput, {
      target: { value: 'Admin xác nhận kiểm tra server mail' },
    });

    const confirmBtn = screen.getByRole('button', { name: 'Xác nhận gửi lại' });
    // Still disabled because checkbox not checked
    expect(confirmBtn).toBeDisabled();

    const checkbox = screen.getByRole('checkbox');
    fireEvent.click(checkbox);

    // Now enabled
    expect(confirmBtn).not.toBeDisabled();
  });
});
