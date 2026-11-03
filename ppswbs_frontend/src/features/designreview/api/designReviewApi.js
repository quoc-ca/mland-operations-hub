// REST client for Feature-004 AI Chatbox Analysis and Suggestion
// Reuses the shared HTTP client contract.

export function designReviewApi(request) {
  return {
    /**
     * Basic AI Support (F04-AI-CHAT).
     * Guest access does not send auth token (auth: false); authenticated members may send token.
     */
    askAiSupport({ message, auth = false }) {
      return request('/api/v1/ai-support/messages', {
        method: 'POST',
        body: { message },
        auth,
      });
    },

    /**
     * Submit Independent Design Request (F04-DESIGN-WORKSPACE / F04-IMAGE-UPLOAD / F04-CONSENT).
     * Uses multipart/form-data with Idempotency-Key.
     */
    submitDesignRequest(key, { description, catalogueReference, consentVersion, consentPurpose, designVersion = 1, image }) {
      const form = new FormData();
      if (description !== undefined && description !== null && description !== '') {
        form.append('description', description);
      }
      if (catalogueReference !== undefined && catalogueReference !== null && catalogueReference !== '') {
        form.append('catalogueReference', catalogueReference);
      }
      if (consentVersion !== undefined && consentVersion !== null && consentVersion !== '') {
        form.append('consentVersion', consentVersion);
      }
      if (consentPurpose !== undefined && consentPurpose !== null && consentPurpose !== '') {
        form.append('consentPurpose', consentPurpose);
      }
      form.append('designVersion', String(designVersion || 1));
      if (image) {
        form.append('image', image);
      }

      return request('/api/v1/design-requests', {
        method: 'POST',
        form,
        idempotencyKey: key,
        auth: true,
      });
    },

    /**
     * Read owner-safe Design Request projection (F04-ANALYSIS-STATUS / F04-RESULT).
     */
    getDesignRequest(designRequestId) {
      return request(`/api/v1/design-requests/${encodeURIComponent(designRequestId)}`, {
        method: 'GET',
        auth: true,
      });
    },

    /**
     * Read Staff / Manager review queue (F04-REVIEW-QUEUE).
     */
    getReviewQueue() {
      return request('/api/v1/design-review-requests', {
        method: 'GET',
        auth: true,
      });
    },

    /**
     * Record human review decision (F04-REVIEW-DETAIL).
     * Uses designVersion for optimistic concurrency control.
     */
    recordReviewDecision(reviewRequestId, { decision, designVersion, reason }) {
      return request(`/api/v1/design-review-requests/${encodeURIComponent(reviewRequestId)}/decisions`, {
        method: 'POST',
        body: { decision, designVersion, reason },
        auth: true,
      });
    },

    /**
     * Request AI price suggestion for a workshop package or retail product proposal (F04-PRICE-PROPOSAL).
     * Manager / Owner only.
     */
    requestPriceSuggestion(proposalId, { targetType = 'WORKSHOP_PACKAGE', bodyContext } = {}) {
      return request(`/api/v1/price-proposals/${encodeURIComponent(proposalId)}/ai-suggestions`, {
        method: 'POST',
        query: { targetType },
        body: bodyContext !== undefined ? bodyContext : '',
        auth: true,
      });
    },

    /**
     * Read AI price suggestion (F04-PRICE-PROPOSAL / F04-ADMIN-AUDIT).
     * Returns commercial advisory pricing to Manager, technical audit projection to Admin.
     */
    getPriceSuggestion(proposalId) {
      return request(`/api/v1/price-proposals/${encodeURIComponent(proposalId)}/ai-suggestions`, {
        method: 'GET',
        auth: true,
      });
    },

    /**
     * Record Manager decision on advisory price suggestion (F04-PRICE-PROPOSAL).
     */
    recordPriceDecision(proposalId, { decision, finalPrice, note }) {
      return request(`/api/v1/price-proposals/${encodeURIComponent(proposalId)}/ai-suggestions/decisions`, {
        method: 'POST',
        body: {
          decision,
          finalPrice: finalPrice !== undefined && finalPrice !== null && finalPrice !== '' ? Number(finalPrice) : null,
          note: note || '',
        },
        auth: true,
      });
    },
  };
}

