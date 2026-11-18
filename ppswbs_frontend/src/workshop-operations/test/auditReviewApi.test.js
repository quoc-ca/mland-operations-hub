import { describe, expect, it, vi } from 'vitest';
import { OPERATIONS_AUDIT_PATH, SETTLEMENT_AUDIT_PATH, createAuditReviewApi } from '../api/auditReviewApi';

describe('Audit review API client (003.2 US7)', () => {
  it('reads Operations rows with GET on the exact path and passes every filter, cursor and limit as query', async () => {
    const http = vi.fn().mockResolvedValue({ items: [], nextCursor: null });
    await createAuditReviewApi(http).readOperationsEvents({
      action: 'SESSION_CREATED', outcome: 'DENIED', actorId: '12', sessionId: '34',
      from: '2026-11-10T00:00:00.000Z', to: '2026-11-17T00:00:00.000Z', cursor: 'abc', limit: 50,
    });

    expect(OPERATIONS_AUDIT_PATH).toBe('/api/v1/workshop-audit/operations-events');
    expect(http).toHaveBeenCalledWith(OPERATIONS_AUDIT_PATH, {
      query: {
        action: 'SESSION_CREATED', outcome: 'DENIED', actorId: '12', sessionId: '34',
        from: '2026-11-10T00:00:00.000Z', to: '2026-11-17T00:00:00.000Z', cursor: 'abc', limit: 50,
      },
    });
  });

  it('reads Settlement rows from its own route with its own filters', async () => {
    const http = vi.fn().mockResolvedValue({ items: [], nextCursor: null });
    await createAuditReviewApi(http).readSettlementRecords({
      actionCode: 'REFUND_APPROVED', actorId: '12', aggregateType: 'SETTLEMENT_REFUND_REQUEST', aggregateReference: 'RR-1',
    });

    expect(SETTLEMENT_AUDIT_PATH).toBe('/api/v1/workshop-audit/settlement-records');
    expect(http).toHaveBeenCalledWith(SETTLEMENT_AUDIT_PATH, {
      query: {
        actionCode: 'REFUND_APPROVED', actorId: '12', aggregateType: 'SETTLEMENT_REFUND_REQUEST', aggregateReference: 'RR-1',
        from: undefined, to: undefined, cursor: undefined, limit: undefined,
      },
    });
  });

  it('never sends a body, a write method or an idempotency key: the screen only reads', async () => {
    const http = vi.fn().mockResolvedValue({ items: [] });
    const api = createAuditReviewApi(http);
    await api.readOperationsEvents();
    await api.readSettlementRecords();

    for (const [, options] of http.mock.calls) {
      expect(options.method).toBeUndefined();
      expect(options.body).toBeUndefined();
      expect(options.idempotencyKey).toBeUndefined();
    }
  });
});
