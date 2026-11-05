import { describe, expect, it, vi } from 'vitest';
import { reviewManagementApi } from '../api/reviewManagementApi';

describe('Feature-011 reviewManagementApi', () => {
  it('uses an idempotency key for guest lookup and does not send Firebase auth', async () => {
    const request = vi.fn().mockResolvedValue({ items: [], eligibilityHandle: 'opaque-handle' });
    const api = reviewManagementApi(request);
    await api.lookupGuestEligibility('lookup-key-123456', { contactType: 'EMAIL', contactValue: 'guest@example.com' });
    expect(request).toHaveBeenCalledWith('/api/v1/review-eligibility/lookup', expect.objectContaining({
      method: 'POST', auth: false, idempotencyKey: 'lookup-key-123456',
    }));
  });

  it('maps owner edit to If-Match-Version and preserves the command key', async () => {
    const request = vi.fn().mockResolvedValue({ id: 'REV-1' });
    const api = reviewManagementApi(request);
    await api.editReview('edit-key-123456', 'REV-1', { rating: 5, bodyText: 'Updated' }, 4);
    expect(request).toHaveBeenCalledWith('/api/v1/reviews/REV-1', expect.objectContaining({
      method: 'PATCH', idempotencyKey: 'edit-key-123456', auth: true,
      headers: { 'If-Match-Version': '4' },
    }));
  });

  it('keeps moderation and reply commands on the same explicit API boundary', async () => {
    const request = vi.fn().mockResolvedValue({ id: 'REV-1' });
    const api = reviewManagementApi(request);
    await api.changeVisibility('hide-key-123456', 'REV-1', { status: 'HIDDEN', reason: 'Policy' }, 2);
    await api.createReply('reply-key-123456', 'REV-1', { bodyText: 'Thank you.' });
    expect(request).toHaveBeenNthCalledWith(1, '/api/v1/reviews/REV-1/visibility', expect.objectContaining({
      method: 'PATCH', idempotencyKey: 'hide-key-123456', headers: { 'If-Match-Version': '2' },
    }));
    expect(request).toHaveBeenNthCalledWith(2, '/api/v1/reviews/REV-1/reply', expect.objectContaining({
      method: 'POST', idempotencyKey: 'reply-key-123456', auth: true,
    }));
  });

  it('keeps public reads unauthenticated and supports public filters', async () => {
    const request = vi.fn().mockResolvedValue({ items: [], aggregate: { count: 0, averageRating: 0 } });
    const api = reviewManagementApi(request);
    await api.listPublicReviews({ targetType: 'RETAIL_PRODUCT', rating: 5 });
    expect(request).toHaveBeenCalledWith('/api/v1/reviews', expect.objectContaining({
      method: 'GET', auth: false, query: { targetType: 'RETAIL_PRODUCT', targetReference: undefined, rating: 5, cursor: undefined },
    }));
  });
});
