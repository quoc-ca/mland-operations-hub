import { newCommandId } from '../../shared/api/http';

/**
 * Client API for OTA Channel Configuration and Reconciliation.
 * Adheres strictly to ppswbs-spec/features/003-workshop-management/003.4-workshop-ota-connectivity/contracts/ota-connectivity.openapi.yaml
 */
export function createOtaConnectivityClient(httpClient) {
  return {
    /**
     * 1. GET /api/v1/ota-channels
     * List channel connections and mappings visible to the authorized actor.
     */
    async listOtaChannels() {
      const response = await httpClient('/api/v1/ota-channels');
      return response?.items || [];
    },

    /**
     * 2. POST /api/v1/ota-channels/{channelId}/mappings
     * Create a draft channel mapping.
     */
    async createChannelMapping(channelId, {
      providerProductReference,
      providerOptionReference,
      workshopOfferReference,
      sessionReference,
    }) {
      if (!channelId) throw new Error('Channel ID is required');
      return await httpClient(`/api/v1/ota-channels/${encodeURIComponent(channelId)}/mappings`, {
        method: 'POST',
        body: {
          providerProductReference: providerProductReference?.trim(),
          providerOptionReference: providerOptionReference?.trim(),
          workshopOfferReference: workshopOfferReference?.trim(),
          sessionReference: sessionReference?.trim(),
        },
      });
    },

    /**
     * 3. PUT /api/v1/ota-channel-mappings/{mappingId}
     * Update a draft or sales-closed mapping using optimistic versioning.
     */
    async updateChannelMapping(mappingId, {
      providerProductReference,
      providerOptionReference,
      workshopOfferReference,
      sessionReference,
      version,
    }) {
      if (!mappingId) throw new Error('Mapping ID is required');
      return await httpClient(`/api/v1/ota-channel-mappings/${encodeURIComponent(mappingId)}`, {
        method: 'PUT',
        body: {
          providerProductReference: providerProductReference?.trim(),
          providerOptionReference: providerOptionReference?.trim(),
          workshopOfferReference: workshopOfferReference?.trim(),
          sessionReference: sessionReference?.trim(),
          version: Number(version),
        },
      });
    },

    /**
     * 4. POST /api/v1/ota-channel-mappings/{mappingId}/price-terms
     * Record externally confirmed price terms as an approved immutable snapshot.
     */
    async recordPriceTerms(mappingId, {
      retailAmount,
      retailCurrency = 'VND',
      targetNetAmount,
      targetNetCurrency = 'VND',
      providerConfirmationEvidenceReference,
      effectiveFrom,
      effectiveTo,
      reason,
    }) {
      if (!mappingId) throw new Error('Mapping ID is required');
      return await httpClient(`/api/v1/ota-channel-mappings/${encodeURIComponent(mappingId)}/price-terms`, {
        method: 'POST',
        body: {
          retailAmount: {
            amount: String(retailAmount).trim(),
            currency: retailCurrency.trim().toUpperCase(),
          },
          targetNetAmount: {
            amount: String(targetNetAmount).trim(),
            currency: targetNetCurrency.trim().toUpperCase(),
          },
          providerConfirmationEvidenceReference: providerConfirmationEvidenceReference?.trim(),
          effectiveFrom: effectiveFrom ? new Date(effectiveFrom).toISOString() : new Date().toISOString(),
          effectiveTo: effectiveTo ? new Date(effectiveTo).toISOString() : null,
          reason: reason?.trim(),
        },
      });
    },

    /**
     * 5. POST /api/v1/ota-channel-mappings/{mappingId}/sync
     * Request preparation or full re-sync for a channel mapping.
     */
    async syncChannelMapping(mappingId, { mode = 'PREPARATION', reason }, idempotencyKey = newCommandId()) {
      if (!mappingId) throw new Error('Mapping ID is required');
      return await httpClient(`/api/v1/ota-channel-mappings/${encodeURIComponent(mappingId)}/sync`, {
        method: 'POST',
        idempotencyKey,
        body: {
          mode,
          reason: reason?.trim(),
        },
      });
    },

    /**
     * 6. POST /api/v1/ota-channel-mappings/{mappingId}/sales-close
     * Request an emergency or planned sales close on the channel.
     */
    async closeChannelSales(mappingId, { reason }, idempotencyKey = newCommandId()) {
      if (!mappingId) throw new Error('Mapping ID is required');
      return await httpClient(`/api/v1/ota-channel-mappings/${encodeURIComponent(mappingId)}/sales-close`, {
        method: 'POST',
        idempotencyKey,
        body: {
          reason: reason?.trim(),
        },
      });
    },

    /**
     * 7. POST /api/v1/ota-channel-mappings/{mappingId}/booking-enable
     * Admin-only: Enable bookings for an audited, verified mapping.
     */
    async enableBooking(mappingId, { readinessRecordReference, checks, reason }) {
      if (!mappingId) throw new Error('Mapping ID is required');
      return await httpClient(`/api/v1/ota-channel-mappings/${encodeURIComponent(mappingId)}/booking-enable`, {
        method: 'POST',
        body: {
          readinessRecordReference: readinessRecordReference?.trim(),
          checks,
          reason: reason?.trim(),
        },
      });
    },

    /**
     * 8. GET /api/v1/ota-reconciliation-work-items
     * List authorized mapping sync and recovery work items.
     */
    async listReconciliationWorkItems(status = null) {
      const query = status && status !== 'ALL' ? { status } : {};
      const response = await httpClient('/api/v1/ota-reconciliation-work-items', { query });
      return response?.items || [];
    },

    /**
     * 9. POST /api/v1/ota-reconciliation-work-items/{workItemId}/resolve
     * Record an authorized, auditable reconciliation decision.
     */
    async resolveReconciliationWorkItem(workItemId, { resolution, reason }, idempotencyKey = newCommandId()) {
      if (!workItemId) throw new Error('Work Item ID is required');
      return await httpClient(`/api/v1/ota-reconciliation-work-items/${encodeURIComponent(workItemId)}/resolve`, {
        method: 'POST',
        idempotencyKey,
        body: {
          resolution,
          reason: reason?.trim(),
        },
      });
    },

    async getFinancialEvidence(channelBookingId) {
      if (!channelBookingId) throw new Error('Channel booking ID is required');
      return await httpClient(`/api/v1/ota-channel-bookings/${encodeURIComponent(channelBookingId)}/financial-evidence`);
    },

    async reconcilePayout(channelBookingId, request, idempotencyKey = newCommandId()) {
      if (!channelBookingId) throw new Error('Channel booking ID is required');
      return await httpClient(`/api/v1/ota-channel-bookings/${encodeURIComponent(channelBookingId)}/payout-reconciliation`, {
        method: 'POST', idempotencyKey, body: request,
      });
    },

    async createAddonQuote(channelBookingId, request) {
      if (!channelBookingId) throw new Error('Channel booking ID is required');
      return await httpClient(`/api/v1/ota-channel-bookings/${encodeURIComponent(channelBookingId)}/add-on-quotes`, {
        method: 'POST', body: request,
      });
    },

    async acceptAddonQuote(quoteId, request) {
      if (!quoteId) throw new Error('Quote ID is required');
      return await httpClient(`/api/v1/ota-addon-quotes/${encodeURIComponent(quoteId)}/acceptance`, {
        method: 'POST', body: request,
      });
    },

    async createAddonDirectBill(quoteId) {
      if (!quoteId) throw new Error('Quote ID is required');
      return await httpClient(`/api/v1/ota-addon-quotes/${encodeURIComponent(quoteId)}/direct-bill`, {
        method: 'POST', idempotencyKey: newCommandId(),
      });
    },
  };
}

export const ALL_READINESS_CHECKS = [
  'CONTRACT_ONBOARDING',
  'COMMERCIAL_TERMS',
  'CONNECTION_OWNERSHIP',
  'CALLBACK_AUTHENTICATION',
  'QUOTA_DELIVERY',
  'PRODUCT_SESSION_MAPPING',
  'DATA_ALLOWLIST',
  'PREPARATION_SYNC',
  'TICKET_DELIVERY_SCAN',
  'CANCELLATION_REFUND',
  'LATE_BOOKING_CAPACITY',
  'FAILURE_RECONCILIATION',
];
