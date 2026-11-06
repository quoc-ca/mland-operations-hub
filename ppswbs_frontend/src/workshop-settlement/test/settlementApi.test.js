import { describe, expect, it, vi } from 'vitest';
import { createSettlementApi } from '../api/settlementApi';

describe('Workshop settlement API client', () => {
  it('lists cases with exact manager filters', async () => {
    const http = vi.fn().mockResolvedValue({ items: [] });
    const api = createSettlementApi(http);

    await api.listCases({ caseType: 'REQUIRED_SESSION_CHANGE', status: 'OPEN', limit: 25 });

    expect(http).toHaveBeenCalledWith('/api/v1/workshop-settlement-cases', {
      query: { caseType: 'REQUIRED_SESSION_CHANGE', status: 'OPEN', limit: 25 },
    });
  });

  it('resolves case detail routes without leaking identifiers into query construction', async () => {
    const http = vi.fn().mockResolvedValue({});
    const api = createSettlementApi(http);

    await api.getCancellation('BK/001', 'cancel/1', { proof: 'contact' });
    await api.getRequiredSessionChange('BK/001', 'change/1');
    await api.getMlandRemedy('BK/001', 'remedy/1', { view: 'safe' });

    expect(http).toHaveBeenNthCalledWith(1, '/api/v1/workshop-bookings/BK%2F001/cancellation-cases/cancel%2F1', {
      query: { proof: 'contact' },
    });
    expect(http).toHaveBeenNthCalledWith(2, '/api/v1/workshop-bookings/BK%2F001/required-session-change-cases/change%2F1', {
      query: undefined,
    });
    expect(http).toHaveBeenNthCalledWith(3, '/api/v1/workshop-bookings/BK%2F001/mland-cancellation-remedy-cases/remedy%2F1', {
      query: { view: 'safe' },
    });
  });

  it('opens required session change idempotently', async () => {
    const http = vi.fn().mockResolvedValue({ caseReference: 'CHANGE-1' });
    const api = createSettlementApi(http);
    const body = { targetRequiredSessionReferences: ['session-a', 'session-b'], handoffReference: 'ops-1' };

    await api.openRequiredSessionChange('BK-001', body, 'change-command-1');

    expect(http).toHaveBeenCalledWith('/api/v1/workshop-bookings/BK-001/required-session-change-cases', {
      method: 'POST',
      body,
      idempotencyKey: 'change-command-1',
    });
  });

  it('lists and creates refund policy revisions through the policy contract', async () => {
    const http = vi.fn().mockResolvedValue({ policyReference: 'POL-1' });
    const api = createSettlementApi(http);
    const body = { policyCode: 'MLAND_REMEDY_V1', refundPercent: 100, effectiveAt: '2026-11-03T00:00:00Z' };

    await api.listRefundPolicies();
    await api.createRefundPolicy(body, 'policy-create-1');

    expect(http).toHaveBeenNthCalledWith(1, '/api/v1/workshop-refund-policies');
    expect(http).toHaveBeenNthCalledWith(2, '/api/v1/workshop-refund-policies', {
      method: 'POST',
      body,
      idempotencyKey: 'policy-create-1',
    });
  });

  it('covers final bill, finalization and recovery payment endpoints', async () => {
    const http = vi.fn().mockResolvedValue({});
    const api = createSettlementApi(http);

    await api.getFinalBill('BK/001');
    await api.createFinalBill('BK/001', { currency: 'VND' }, 'bill-create-1');
    await api.addCharge('BK/001', { code: 'ADDON', amount: 150000 }, 'charge-1');
    await api.finalize('BK/001', 'finalize-1');
    await api.startPayment('BK/001', 'payment-attempt-1');

    expect(http).toHaveBeenNthCalledWith(1, '/api/v1/workshop-bookings/BK%2F001/final-bill/operator');
    expect(http).toHaveBeenNthCalledWith(2, '/api/v1/workshop-bookings/BK%2F001/final-bill', {
      method: 'POST',
      body: { currency: 'VND' },
      idempotencyKey: 'bill-create-1',
    });
    expect(http).toHaveBeenNthCalledWith(3, '/api/v1/workshop-bookings/BK%2F001/final-bill/charges', {
      method: 'POST',
      body: { code: 'ADDON', amount: 150000 },
      idempotencyKey: 'charge-1',
    });
    expect(http).toHaveBeenNthCalledWith(4, '/api/v1/workshop-bookings/BK%2F001/final-bill/finalizations', {
      method: 'POST', body: {}, idempotencyKey: 'finalize-1',
    });
    expect(http).toHaveBeenNthCalledWith(5, '/api/v1/workshop-bookings/BK%2F001/final-bill/payment-attempts', {
      method: 'POST', body: {}, idempotencyKey: 'payment-attempt-1',
    });
  });

  it('uses generated idempotency keys for cancellation and remedy commands', async () => {
    const http = vi.fn().mockResolvedValue({});
    const api = createSettlementApi(http);

    await api.openCancellation('BK-001', { reasonCode: 'CUSTOMER_CHANGE_OF_MIND' });
    await api.openMlandRemedy('BK-001', { reasonCode: 'PROVIDER_FAILURE' });

    expect(http).toHaveBeenCalledTimes(2);
    for (const call of http.mock.calls) {
      expect(call[1].method).toBe('POST');
      expect(call[1].idempotencyKey).toEqual(expect.any(String));
      expect(call[1].idempotencyKey.length).toBeGreaterThan(0);
    }
  });

  it('covers folio correction creation and customer-safe read endpoints', async () => {
    const http = vi.fn().mockResolvedValue({});
    const api = createSettlementApi(http);

    await api.createFolioCorrection('BK-001', { amountVnd: 50000, reason: 'Staff typo' }, 'corr-key-1');
    await api.getFolioCorrection('BK-001', 'CORR-123', { proof: 'contact' });

    expect(http).toHaveBeenNthCalledWith(1, '/api/v1/workshop-bookings/BK-001/folio-corrections', {
      method: 'POST',
      body: { amountVnd: 50000, reason: 'Staff typo' },
      idempotencyKey: 'corr-key-1',
    });
    expect(http).toHaveBeenNthCalledWith(2, '/api/v1/workshop-bookings/BK-001/folio-corrections/CORR-123', {
      query: { proof: 'contact' },
    });
  });
});
