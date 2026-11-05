import { describe, it, expect, vi, beforeEach } from 'vitest';
import { createOtaConnectivityClient, ALL_READINESS_CHECKS } from '../api/otaConnectivityApi';

describe('OTA Connectivity API Client Contract Tests (T025)', () => {
  let mockHttpClient;
  let client;

  beforeEach(() => {
    mockHttpClient = vi.fn();
    client = createOtaConnectivityClient(mockHttpClient);
  });

  it('1. listOtaChannels calls GET /api/v1/ota-channels and returns items', async () => {
    mockHttpClient.mockResolvedValueOnce({
      items: [
        { mappingId: 'map-1', provider: 'KLOOK', state: 'SALES_CLOSED', version: 1 },
      ],
    });

    const result = await client.listOtaChannels();

    expect(mockHttpClient).toHaveBeenCalledWith('/api/v1/ota-channels');
    expect(result).toHaveLength(1);
    expect(result[0].provider).toBe('KLOOK');
  });

  it('2. createChannelMapping calls POST /api/v1/ota-channels/{channelId}/mappings with trimmed body', async () => {
    mockHttpClient.mockResolvedValueOnce({ mappingId: 'map-new', state: 'DRAFT' });

    const result = await client.createChannelMapping('channel-101', {
      providerProductReference: ' PROD-101 ',
      providerOptionReference: ' OPT-101 ',
      workshopOfferReference: ' offer-standard ',
      sessionReference: ' sess-123 ',
    });

    expect(mockHttpClient).toHaveBeenCalledWith('/api/v1/ota-channels/channel-101/mappings', {
      method: 'POST',
      body: {
        providerProductReference: 'PROD-101',
        providerOptionReference: 'OPT-101',
        workshopOfferReference: 'offer-standard',
        sessionReference: 'sess-123',
      },
    });
    expect(result.mappingId).toBe('map-new');
  });

  it('createChannelMapping throws when channelId is missing', async () => {
    await expect(client.createChannelMapping(null, {})).rejects.toThrow('Channel ID is required');
  });

  it('3. updateChannelMapping calls PUT /api/v1/ota-channel-mappings/{mappingId} with optimistic version', async () => {
    mockHttpClient.mockResolvedValueOnce({ mappingId: 'map-101', version: 2 });

    const result = await client.updateChannelMapping('map-101', {
      providerProductReference: 'PROD-UPDATED',
      providerOptionReference: 'OPT-UPDATED',
      workshopOfferReference: 'offer-upd',
      sessionReference: 'sess-upd',
      version: 1,
    });

    expect(mockHttpClient).toHaveBeenCalledWith('/api/v1/ota-channel-mappings/map-101', {
      method: 'PUT',
      body: {
        providerProductReference: 'PROD-UPDATED',
        providerOptionReference: 'OPT-UPDATED',
        workshopOfferReference: 'offer-upd',
        sessionReference: 'sess-upd',
        version: 1,
      },
    });
    expect(result.version).toBe(2);
  });

  it('4. recordPriceTerms calls POST /api/v1/ota-channel-mappings/{mappingId}/price-terms with Money objects', async () => {
    mockHttpClient.mockResolvedValueOnce({ priceTermsId: 'pt-1', status: 'APPROVED' });

    const result = await client.recordPriceTerms('map-101', {
      retailAmount: '500000',
      retailCurrency: 'vnd',
      targetNetAmount: '400000',
      targetNetCurrency: 'vnd',
      providerConfirmationEvidenceReference: 'CONF-DOC-001',
      effectiveFrom: '2026-11-01T10:00:00Z',
      effectiveTo: '2026-12-01T10:00:00Z',
      reason: 'Confirmed seasonal pricing',
    });

    expect(mockHttpClient).toHaveBeenCalledWith('/api/v1/ota-channel-mappings/map-101/price-terms', {
      method: 'POST',
      body: {
        retailAmount: { amount: '500000', currency: 'VND' },
        targetNetAmount: { amount: '400000', currency: 'VND' },
        providerConfirmationEvidenceReference: 'CONF-DOC-001',
        effectiveFrom: '2026-11-01T10:00:00.000Z',
        effectiveTo: '2026-12-01T10:00:00.000Z',
        reason: 'Confirmed seasonal pricing',
      },
    });
    expect(result.status).toBe('APPROVED');
  });

  it('5. syncChannelMapping calls POST /api/v1/ota-channel-mappings/{mappingId}/sync with Idempotency-Key', async () => {
    mockHttpClient.mockResolvedValueOnce({ operationReference: 'op-sync-1', status: 'QUEUED' });

    const result = await client.syncChannelMapping('map-101', {
      mode: 'FULL_RESYNC',
      reason: 'Reconcile gap sequence',
    }, 'idem-sync-key-1234');

    expect(mockHttpClient).toHaveBeenCalledWith('/api/v1/ota-channel-mappings/map-101/sync', {
      method: 'POST',
      idempotencyKey: 'idem-sync-key-1234',
      body: {
        mode: 'FULL_RESYNC',
        reason: 'Reconcile gap sequence',
      },
    });
    expect(result.status).toBe('QUEUED');
  });

  it('6. closeChannelSales calls POST /api/v1/ota-channel-mappings/{mappingId}/sales-close with Idempotency-Key', async () => {
    mockHttpClient.mockResolvedValueOnce({ operationReference: 'op-close-1', status: 'QUEUED' });

    const result = await client.closeChannelSales('map-101', {
      reason: 'Emergency weather closure',
    }, 'idem-close-key-5678');

    expect(mockHttpClient).toHaveBeenCalledWith('/api/v1/ota-channel-mappings/map-101/sales-close', {
      method: 'POST',
      idempotencyKey: 'idem-close-key-5678',
      body: {
        reason: 'Emergency weather closure',
      },
    });
    expect(result.status).toBe('QUEUED');
  });

  it('7. enableBooking calls POST /api/v1/ota-channel-mappings/{mappingId}/booking-enable with 12 checks', async () => {
    mockHttpClient.mockResolvedValueOnce({ decisionId: 'dec-1', outcome: 'APPROVED' });

    const checks = ALL_READINESS_CHECKS.map((c) => ({ check: c, outcome: 'VERIFIED' }));
    const result = await client.enableBooking('map-101', {
      readinessRecordReference: 'DOC-REC-001',
      checks,
      reason: 'All 12 gates verified by Admin',
    });

    expect(mockHttpClient).toHaveBeenCalledWith('/api/v1/ota-channel-mappings/map-101/booking-enable', {
      method: 'POST',
      body: {
        readinessRecordReference: 'DOC-REC-001',
        checks,
        reason: 'All 12 gates verified by Admin',
      },
    });
    expect(result.outcome).toBe('APPROVED');
  });

  it('8. listReconciliationWorkItems calls GET /api/v1/ota-reconciliation-work-items with optional status', async () => {
    mockHttpClient.mockResolvedValueOnce({ items: [{ workItemId: 'item-1', status: 'OPEN' }] });

    const all = await client.listReconciliationWorkItems();
    expect(mockHttpClient).toHaveBeenCalledWith('/api/v1/ota-reconciliation-work-items', { query: {} });
    expect(all).toHaveLength(1);

    mockHttpClient.mockResolvedValueOnce({ items: [] });
    await client.listReconciliationWorkItems('OPEN');
    expect(mockHttpClient).toHaveBeenCalledWith('/api/v1/ota-reconciliation-work-items', { query: { status: 'OPEN' } });
  });

  it('9. resolveReconciliationWorkItem calls POST /api/v1/ota-reconciliation-work-items/{workItemId}/resolve with Idempotency-Key', async () => {
    mockHttpClient.mockResolvedValueOnce({ workItemId: 'item-1', status: 'RESOLVED' });

    const result = await client.resolveReconciliationWorkItem('item-1', {
      resolution: 'RESYNC_REQUESTED',
      reason: 'Triggered resync to fix gap',
    }, 'idem-res-key-9999');

    expect(mockHttpClient).toHaveBeenCalledWith('/api/v1/ota-reconciliation-work-items/item-1/resolve', {
      method: 'POST',
      idempotencyKey: 'idem-res-key-9999',
      body: {
        resolution: 'RESYNC_REQUESTED',
        reason: 'Triggered resync to fix gap',
      },
    });
    expect(result.status).toBe('RESOLVED');
  });

  it('10. finance evidence read and payout reconciliation use booking-scoped endpoints', async () => {
    mockHttpClient.mockResolvedValueOnce({ channelBookingId: 'booking-1', payoutState: 'ACCRUED', entries: [] });
    expect(await client.getFinancialEvidence('booking-1')).toEqual({ channelBookingId: 'booking-1', payoutState: 'ACCRUED', entries: [] });
    expect(mockHttpClient).toHaveBeenLastCalledWith('/api/v1/ota-channel-bookings/booking-1/financial-evidence');

    mockHttpClient.mockResolvedValueOnce({ payoutState: 'INVOICED' });
    const request = { kind: 'PAYOUT', evidenceType: 'INVOICE', amount: { amount: '100', currency: 'VND' }, reference: 'INV-1', reason: 'Verified', occurredAt: '2026-11-01T00:00:00Z' };
    await client.reconcilePayout('booking-1', request, 'idem-payout-1');
    expect(mockHttpClient).toHaveBeenLastCalledWith('/api/v1/ota-channel-bookings/booking-1/payout-reconciliation', { method: 'POST', idempotencyKey: 'idem-payout-1', body: request });
  });

  it('11. add-on quote, acceptance and direct bill keep the flow outside OTA checkout', async () => {
    mockHttpClient.mockResolvedValueOnce({ quoteId: 'quote-1', status: 'QUOTED' });
    const quote = await client.createAddonQuote('booking-1', { addonType: 'PAID_OPTION', itemCode: 'BOX', itemDescription: 'Gift box', amount: { amount: '50000', currency: 'VND' } });
    expect(quote.status).toBe('QUOTED');
    expect(mockHttpClient).toHaveBeenLastCalledWith('/api/v1/ota-channel-bookings/booking-1/add-on-quotes', { method: 'POST', body: { addonType: 'PAID_OPTION', itemCode: 'BOX', itemDescription: 'Gift box', amount: { amount: '50000', currency: 'VND' } } });
    mockHttpClient.mockResolvedValueOnce({ quoteId: 'quote-1', status: 'ACCEPTED' });
    await client.acceptAddonQuote('quote-1', { acceptedAt: '2026-11-01T00:00:00Z', acceptanceReference: 'COUNTER-1' });
    expect(mockHttpClient).toHaveBeenLastCalledWith('/api/v1/ota-addon-quotes/quote-1/acceptance', { method: 'POST', body: { acceptedAt: '2026-11-01T00:00:00Z', acceptanceReference: 'COUNTER-1' } });
    mockHttpClient.mockResolvedValueOnce({ settlementBillReference: 'BILL-1' });
    await client.createAddonDirectBill('quote-1');
    expect(mockHttpClient.mock.calls.at(-1)[0]).toBe('/api/v1/ota-addon-quotes/quote-1/direct-bill');
  });
});

