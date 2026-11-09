import { describe, expect, it, vi } from 'vitest';
import { notificationApiClient } from '../infrastructure/notificationApiClient';

describe('notificationApiClient', () => {
  it('calls template registry and version endpoints correctly', async () => {
    const mockRequest = vi.fn().mockResolvedValue({ success: true });
    const client = notificationApiClient(mockRequest);

    // List templates
    await client.listTemplates();
    expect(mockRequest).toHaveBeenCalledWith('/api/v1/notification-templates');

    // Get template
    await client.getTemplate('BOOKING_CONFIRMED');
    expect(mockRequest).toHaveBeenCalledWith('/api/v1/notification-templates/BOOKING_CONFIRMED');

    // Create draft version
    const draftBody = {
      subjectTemplate: 'Subject {{bookingCode}}',
      htmlTemplate: '<p>Hi {{customerName}}</p>',
      plainTextTemplate: 'Hi {{customerName}}',
    };
    await client.createDraftVersion('BOOKING_CONFIRMED', draftBody);
    expect(mockRequest).toHaveBeenCalledWith(
      '/api/v1/notification-templates/BOOKING_CONFIRMED/versions',
      { method: 'POST', body: draftBody }
    );

    // Update draft version
    await client.updateDraftVersion('BOOKING_CONFIRMED', 2, draftBody);
    expect(mockRequest).toHaveBeenCalledWith(
      '/api/v1/notification-templates/BOOKING_CONFIRMED/versions/2',
      { method: 'PUT', body: draftBody }
    );

    // Activate version
    await client.activateVersion('BOOKING_CONFIRMED', 2);
    expect(mockRequest).toHaveBeenCalledWith(
      '/api/v1/notification-templates/BOOKING_CONFIRMED/versions/2/activate',
      { method: 'POST' }
    );

    // Preview template
    const previewBody = { versionNumber: 2, sampleData: { customerName: 'A' } };
    await client.previewTemplate('BOOKING_CONFIRMED', previewBody);
    expect(mockRequest).toHaveBeenCalledWith(
      '/api/v1/notification-templates/BOOKING_CONFIRMED/preview',
      { method: 'POST', body: previewBody }
    );
  });

  it('calls notification request and operation endpoints with query and idempotency key', async () => {
    const mockRequest = vi.fn().mockResolvedValue({ success: true });
    const client = notificationApiClient(mockRequest);

    // List requests with filter query
    const query = { deliveryState: 'PENDING', page: 0, size: 20 };
    await client.listNotificationRequests(query);
    expect(mockRequest).toHaveBeenCalledWith('/api/v1/notification-requests', { query });

    // Get single request
    await client.getNotificationRequest(1001);
    expect(mockRequest).toHaveBeenCalledWith('/api/v1/notification-requests/1001');

    // Resend request
    const resendBody = { reason: 'Khách hàng yêu cầu gửi lại email', duplicateRiskAccepted: false };
    await client.resendNotificationRequest(1001, resendBody, 'idem-key-1');
    expect(mockRequest).toHaveBeenCalledWith('/api/v1/notification-requests/1001/resend', {
      method: 'POST',
      body: resendBody,
      idempotencyKey: 'idem-key-1',
    });

    // Cancel request
    const cancelBody = { reason: 'Khách hàng hủy đặt chỗ' };
    await client.cancelNotificationRequest(1001, cancelBody, 'idem-key-2');
    expect(mockRequest).toHaveBeenCalledWith('/api/v1/notification-requests/1001/cancel', {
      method: 'POST',
      body: cancelBody,
      idempotencyKey: 'idem-key-2',
    });

    // Redeliver request with refreshed credential
    const redeliverBody = { reason: 'Link vé QR hết hạn' };
    await client.redeliverNotificationRequest(1001, redeliverBody, 'idem-key-3');
    expect(mockRequest).toHaveBeenCalledWith('/api/v1/notification-requests/1001/redeliver', {
      method: 'POST',
      body: redeliverBody,
      idempotencyKey: 'idem-key-3',
    });
  });
});

