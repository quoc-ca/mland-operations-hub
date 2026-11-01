import { describe, expect, it, vi } from 'vitest';
import { designReviewApi } from '../api/designReviewApi';

describe('Feature-004 REST operations', () => {
  const request = vi.fn(async () => ({}));
  const api = designReviewApi(request);
  const last = () => request.mock.calls.at(-1);

  it('askAiSupport supports guest and member calls', async () => {
    await api.askAiSupport({ message: 'Hello AI', auth: false });
    expect(last()).toEqual([
      '/api/v1/ai-support/messages',
      { method: 'POST', body: { message: 'Hello AI' }, auth: false },
    ]);

    await api.askAiSupport({ message: 'Hello Member', auth: true });
    expect(last()).toEqual([
      '/api/v1/ai-support/messages',
      { method: 'POST', body: { message: 'Hello Member' }, auth: true },
    ]);
  });

  it('submitDesignRequest sends multipart FormData with Idempotency-Key', async () => {
    const file = new File(['fake-image-bytes'], 'ring.png', { type: 'image/png' });
    await api.submitDesignRequest('key-123', {
      description: 'Nhẫn bạc đính đá',
      catalogueReference: 'REF-001',
      consentVersion: 'v1.0',
      consentPurpose: 'INDEPENDENT_DESIGN_IMAGE_ANALYSIS',
      designVersion: 1,
      image: file,
    });

    const [path, options] = last();
    expect(path).toBe('/api/v1/design-requests');
    expect(options.method).toBe('POST');
    expect(options.idempotencyKey).toBe('key-123');
    expect(options.auth).toBe(true);

    const form = options.form;
    expect(form.get('description')).toBe('Nhẫn bạc đính đá');
    expect(form.get('catalogueReference')).toBe('REF-001');
    expect(form.get('consentVersion')).toBe('v1.0');
    expect(form.get('consentPurpose')).toBe('INDEPENDENT_DESIGN_IMAGE_ANALYSIS');
    expect(form.get('designVersion')).toBe('1');
    expect(form.get('image')).toBe(file);
  });

  it('getDesignRequest calls GET /api/v1/design-requests/:id with auth', async () => {
    await api.getDesignRequest('req-456');
    expect(last()).toEqual([
      '/api/v1/design-requests/req-456',
      { method: 'GET', auth: true },
    ]);
  });

  it('getReviewQueue calls GET /api/v1/design-review-requests with auth', async () => {
    await api.getReviewQueue();
    expect(last()).toEqual([
      '/api/v1/design-review-requests',
      { method: 'GET', auth: true },
    ]);
  });

  it('recordReviewDecision posts human decision with optimistic designVersion', async () => {
    await api.recordReviewDecision('req-789', {
      decision: 'ACCEPTED',
      designVersion: 2,
      reason: 'Thiết kế đạt tiêu chuẩn sản xuất.',
    });
    expect(last()).toEqual([
      '/api/v1/design-review-requests/req-789/decisions',
      {
        method: 'POST',
        body: {
          decision: 'ACCEPTED',
          designVersion: 2,
          reason: 'Thiết kế đạt tiêu chuẩn sản xuất.',
        },
        auth: true,
      },
    ]);
  });

  it('requestPriceSuggestion sends targetType as query and bodyContext', async () => {
    await api.requestPriceSuggestion('prop-101', {
      targetType: 'WORKSHOP_PACKAGE',
      bodyContext: '{"estimatedCost": 500000}',
    });
    expect(last()).toEqual([
      '/api/v1/price-proposals/prop-101/ai-suggestions',
      {
        method: 'POST',
        query: { targetType: 'WORKSHOP_PACKAGE' },
        body: '{"estimatedCost": 500000}',
        auth: true,
      },
    ]);
  });

  it('getPriceSuggestion calls GET /api/v1/price-proposals/:id/ai-suggestions with auth', async () => {
    await api.getPriceSuggestion('prop-101');
    expect(last()).toEqual([
      '/api/v1/price-proposals/prop-101/ai-suggestions',
      { method: 'GET', auth: true },
    ]);
  });

  it('recordPriceDecision posts manager decision and numeric finalPrice', async () => {
    await api.recordPriceDecision('prop-101', {
      decision: 'ADJUSTED',
      finalPrice: '1200000',
      note: 'Điều chỉnh theo chi phí nguyên liệu mới.',
    });
    expect(last()).toEqual([
      '/api/v1/price-proposals/prop-101/ai-suggestions/decisions',
      {
        method: 'POST',
        body: {
          decision: 'ADJUSTED',
          finalPrice: 1200000,
          note: 'Điều chỉnh theo chi phí nguyên liệu mới.',
        },
        auth: true,
      },
    ]);
  });
});

