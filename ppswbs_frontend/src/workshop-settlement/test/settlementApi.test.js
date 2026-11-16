import { describe, expect, it, vi } from 'vitest';
import { createSettlementApi } from '../api/settlementApi';

const post = (body, key) => ({ method: 'POST', body, idempotencyKey: key });

describe('Workshop settlement API client (per ticket)', () => {
  it('lists cases through the route that is still served', async () => {
    const http = vi.fn().mockResolvedValue({ items: [] });
    const api = createSettlementApi(http);
    await api.listCases({ caseType: 'MLAND_CANCELLATION_REMEDY', status: 'OPEN' });
    expect(http).toHaveBeenNthCalledWith(1, '/api/v1/workshop-settlement-cases', { query: { caseType: 'MLAND_CANCELLATION_REMEDY', status: 'OPEN' } });
    expect(api.listRefundPolicies).toBeUndefined();
    expect(api.createRefundPolicy).toBeUndefined();
  });

  it('never calls a removed booking-wide route', async () => {
    const http = vi.fn().mockResolvedValue({});
    const api = createSettlementApi(http);
    // Call every client function once with harmless arguments and look at every path that was sent.
    for (const [name, fn] of Object.entries(api)) {
      if (typeof fn === 'function') await fn('A-1', 'B-2', 'C-3', {}).catch?.(() => {});
      expect(name).not.toMatch(/openCancellation|openMlandRemedy|openRequiredSessionChange|getRequiredSessionChange|getCancellation|startPayment|createFinalBill/);
    }
    const paths = http.mock.calls.map(call => call[0]);
    expect(paths.some(path => /\/workshop-bookings\//.test(path))).toBe(false);
    expect(paths.some(path => /cancellation-cases|mland-cancellation-remedy-cases|required-session-change-cases|final-bill|\/folio-corrections/.test(path))).toBe(false);
  });

  it('reads and collects on the ticket folio with an Idempotency-Key on every write', async () => {
    const http = vi.fn().mockResolvedValue({});
    const api = createSettlementApi(http);
    await api.getFolio('TK/1');
    await api.recordCollection('TK/1', { method: 'CASH', amount: '100000' }, 'key-collection-0001');
    await api.confirmTransfer('TK/1', 'col/9', { bankTransactionReference: 'REF' }, 'key-confirm-000001');
    await api.failCollection('TK/1', 'col/9', { reason: 'not received' }, 'key-failure-0000001');
    await api.finalize('TK/1', 'key-finalize-00001');
    expect(http).toHaveBeenNthCalledWith(1, '/api/v1/workshop-tickets/TK%2F1/folio', undefined);
    expect(http).toHaveBeenNthCalledWith(2, '/api/v1/workshop-tickets/TK%2F1/folio/collections', post({ method: 'CASH', amount: '100000' }, 'key-collection-0001'));
    expect(http).toHaveBeenNthCalledWith(3, '/api/v1/workshop-tickets/TK%2F1/folio/collections/col%2F9/confirmations', post({ bankTransactionReference: 'REF' }, 'key-confirm-000001'));
    expect(http).toHaveBeenNthCalledWith(4, '/api/v1/workshop-tickets/TK%2F1/folio/collections/col%2F9/failures', post({ reason: 'not received' }, 'key-failure-0000001'));
    expect(http).toHaveBeenNthCalledWith(5, '/api/v1/workshop-tickets/TK%2F1/folio/finalizations', post(undefined, 'key-finalize-00001'));
  });

  it('sends a Guest folio lookup with the proof in the body and no bearer', async () => {
    const http = vi.fn().mockResolvedValue({});
    await createSettlementApi(http).lookupFolio('TK-1', { contactEmail: 'a@b.vn' });
    expect(http).toHaveBeenCalledWith('/api/v1/workshop-tickets/TK-1/folio-lookups', { method: 'POST', body: { contactEmail: 'a@b.vn' }, auth: false });
    expect(http.mock.calls[0][0]).not.toContain('a@b.vn');
  });

  it('covers cash close, refunds and the customer account link', async () => {
    const http = vi.fn().mockResolvedValue({});
    const api = createSettlementApi(http);
    await api.getExpectedCash({ branchId: '3' });
    await api.closeCash({ locationId: 3, workingDate: '2026-11-14', countedAmount: '0' }, 'key-close-00000001');
    await api.confirmCashDifference('CLOSE-1', { reason: 'counted twice' }, 'key-diff-000000001');
    await api.listRefunds({ status: 'PENDING_APPROVAL' });
    await api.transitionRefund('R-1', { action: 'APPROVE' }, 'key-transition-0001');
    await api.putRefundAccount('R-1', { bankCode: 'VCB' }, 'key-account-000001');
    await api.resendAccountLink('R-1', 'key-resend-0000001');
    await api.submitRefundAccount('tok/en', { bankCode: 'VCB', accountNumber: '1', holderName: 'A' });
    expect(http).toHaveBeenNthCalledWith(1, '/api/v1/workshop-cash-closes/expected-cash', { query: { branchId: '3' } });
    expect(http).toHaveBeenNthCalledWith(2, '/api/v1/workshop-cash-closes', post({ locationId: 3, workingDate: '2026-11-14', countedAmount: '0' }, 'key-close-00000001'));
    expect(http).toHaveBeenNthCalledWith(3, '/api/v1/workshop-cash-closes/CLOSE-1/difference-confirmations', post({ reason: 'counted twice' }, 'key-diff-000000001'));
    expect(http).toHaveBeenNthCalledWith(4, '/api/v1/workshop-refund-requests', { query: { status: 'PENDING_APPROVAL' } });
    expect(http).toHaveBeenNthCalledWith(5, '/api/v1/workshop-refund-requests/R-1/transitions', post({ action: 'APPROVE' }, 'key-transition-0001'));
    expect(http).toHaveBeenNthCalledWith(6, '/api/v1/workshop-refund-requests/R-1/account', { method: 'PUT', body: { bankCode: 'VCB' }, idempotencyKey: 'key-account-000001' });
    expect(http).toHaveBeenNthCalledWith(7, '/api/v1/workshop-refund-requests/R-1/account-link-resends', post(undefined, 'key-resend-0000001'));
    expect(http).toHaveBeenNthCalledWith(8, '/api/v1/refund-account-links/tok%2Fen', { method: 'POST', body: { bankCode: 'VCB', accountNumber: '1', holderName: 'A' }, auth: false });
  });

  it('covers cancellation, change, the change-request queue and the Mland remedy', async () => {
    const http = vi.fn().mockResolvedValue({});
    const api = createSettlementApi(http);
    await api.cancelTicket('TK-1', { reason: 'r' }, 'key-cancel-00000001');
    await api.cancelGroupTickets('GRP-1', { reason: 'r', ticketCodes: ['TK-1', 'TK-2'] }, 'key-cancel-00000002');
    await api.changeTicket('TK-1', { type: 'ADVANCE' }, 'key-change-00000001');
    await api.changeGroupTickets('GRP-1', { type: 'ADVANCE', changes: [] }, 'key-change-00000002');
    await api.listChangeRequests({ status: 'PENDING' });
    await api.decideChangeRequest('CR-1', { decision: 'APPROVE', reason: 'ok' }, 'key-decision-000001');
    await api.getMlandRemedy('TK-1', { auth: false });
    await api.answerMlandRemedy('TK-1', { answer: 'REFUND' }, 'key-answer-0000001', { auth: false });
    const paths = http.mock.calls.map(call => call[0]);
    expect(paths).toEqual([
      '/api/v1/workshop-tickets/TK-1/cancellations',
      '/api/v1/workshop-booking-groups/GRP-1/cancellations',
      '/api/v1/workshop-tickets/TK-1/changes',
      '/api/v1/workshop-booking-groups/GRP-1/ticket-changes',
      '/api/v1/workshop-change-requests',
      '/api/v1/workshop-change-requests/CR-1/decisions',
      '/api/v1/workshop-tickets/TK-1/mland-remedy',
      '/api/v1/workshop-tickets/TK-1/mland-remedy/answers',
    ]);
    expect(http.mock.calls[7][1]).toEqual({ method: 'POST', body: { answer: 'REFUND' }, idempotencyKey: 'key-answer-0000001', auth: false });
  });
});
