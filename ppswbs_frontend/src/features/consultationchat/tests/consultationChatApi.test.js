import { describe, it, expect, vi } from 'vitest';
import { consultationChatApi } from '../api/consultationChatApi';

describe('Feature-013 consultationChatApi adapter', () => {
  it('calls getContextOptions with auth: true', async () => {
    const mockRequest = vi.fn().mockResolvedValue({ items: [] });
    const api = consultationChatApi(mockRequest);

    await api.getContextOptions();
    expect(mockRequest).toHaveBeenCalledWith('/api/v1/consultations/context-options', {
      method: 'GET',
      auth: true,
    });
  });

  it('calls createConversation with Idempotency-Key and payload', async () => {
    const mockRequest = vi.fn().mockResolvedValue({ conversationReference: 'CONV-123' });
    const api = consultationChatApi(mockRequest);

    const body = {
      contextType: 'WORKSHOP_RING_DESIGN',
      contextReference: 'DSG-1',
      contextVersion: 1,
      initialMessage: 'Xin chào',
    };
    await api.createConversation('key-1234567890123456', body);

    expect(mockRequest).toHaveBeenCalledWith('/api/v1/consultations', {
      method: 'POST',
      body,
      idempotencyKey: 'key-1234567890123456',
      headers: undefined,
      auth: true,
    });
  });

  it('calls claim with If-Match-Version header', async () => {
    const mockRequest = vi.fn().mockResolvedValue({ aggregateVersion: 2 });
    const api = consultationChatApi(mockRequest);

    await api.claim('key-claim', 1, 'CONV-123');

    expect(mockRequest).toHaveBeenCalledWith('/api/v1/consultations/CONV-123/claim', {
      method: 'POST',
      body: undefined,
      idempotencyKey: 'key-claim',
      headers: { 'If-Match-Version': '1' },
      auth: true,
    });
  });

  it('calls assign with locationReference and If-Match-Version', async () => {
    const mockRequest = vi.fn().mockResolvedValue({ aggregateVersion: 2 });
    const api = consultationChatApi(mockRequest);

    await api.assign('key-assign', 1, 'CONV-123', { locationReference: 'HN-HOANKIEM' });

    expect(mockRequest).toHaveBeenCalledWith('/api/v1/consultations/CONV-123/assign', {
      method: 'POST',
      body: { locationReference: 'HN-HOANKIEM' },
      idempotencyKey: 'key-assign',
      headers: { 'If-Match-Version': '1' },
      auth: true,
    });
  });

  it('calls paginated list with cursor and limit', async () => {
    const mockRequest = vi.fn().mockResolvedValue({ items: [], hasMore: false });
    const api = consultationChatApi(mockRequest);

    await api.listConversations({ cursor: 'opaque-cursor', limit: 20 });

    expect(mockRequest).toHaveBeenCalledWith('/api/v1/consultations', {
      method: 'GET',
      query: { status: undefined, cursor: 'opaque-cursor', limit: 20 },
      auth: true,
    });
  });

  it('soft hides a member conversation with idempotency and version headers', async () => {
    const mockRequest = vi.fn().mockResolvedValue(undefined);
    const api = consultationChatApi(mockRequest);

    await api.hideForMember('hide-key-123456789012', 4, 'CONV-123');

    expect(mockRequest).toHaveBeenCalledWith('/api/v1/consultations/CONV-123', {
      method: 'DELETE',
      body: undefined,
      idempotencyKey: 'hide-key-123456789012',
      headers: { 'If-Match-Version': '4' },
      auth: true,
    });
  });
});
