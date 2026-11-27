// REST client for Feature-004 AI Chatbox Analysis and Suggestion
// Reuses the shared HTTP client contract.

export function designReviewApi(request) {
  return {
    /**
     * Basic AI Support (F04-AI-CHAT).
     * Guest access does not send auth token (auth: false); authenticated members may send token.
     * Manages X-Guest-AI-Session in sessionStorage.
     */
    async askAiSupport({ message, auth = false }) {
      const headers = {};
      if (!auth && typeof sessionStorage !== 'undefined') {
        const guestSession = sessionStorage.getItem('mland_guest_ai_session');
        if (guestSession) {
          headers['X-Guest-AI-Session'] = guestSession;
        }
      }

      try {
        const result = await request('/api/v1/ai-support/messages', {
          method: 'POST',
          body: { message },
          auth,
          headers,
          suppressAccessDenied: true,
        });

        if (result?.guestSessionToken && typeof sessionStorage !== 'undefined') {
          sessionStorage.setItem('mland_guest_ai_session', result.guestSessionToken);
        }

        return result;
      } catch (err) {
        if (err.guestSessionToken && typeof sessionStorage !== 'undefined') {
          sessionStorage.setItem('mland_guest_ai_session', err.guestSessionToken);
        }
        if (err.status === 401 && typeof sessionStorage !== 'undefined') {
          sessionStorage.removeItem('mland_guest_ai_session');
        }
        throw err;
      }
    },

    /**
     * Get current AI support prompt quota status.
     */
    async getAiQuota({ auth = false } = {}) {
      const headers = {};
      if (!auth && typeof sessionStorage !== 'undefined') {
        const guestSession = sessionStorage.getItem('mland_guest_ai_session');
        if (guestSession) {
          headers['X-Guest-AI-Session'] = guestSession;
        }
      }
      return request('/api/v1/ai-support/quota', {
        method: 'GET',
        auth,
        headers,
        suppressAccessDenied: true,
      });
    },

    /**
     * Submit Independent Design Request (F04-DESIGN-WORKSPACE / F04-IMAGE-UPLOAD / F04-CONSENT / HUMAN REVIEW).
     * Uses multipart/form-data with Idempotency-Key.
     * Supports both Guests (auth = false) and Members (auth = true), and up to 5 images.
     */
    async submitDesignRequest(key, {
      description,
      catalogueReference,
      designReference,
      designRingVersion,
      consentVersion,
      consentPurpose,
      designVersion = 1,
      image,
      images = [],
      guestSessionId,
    }, auth = true) {
      const form = new FormData();
      if (description !== undefined && description !== null && description !== '') {
        form.append('description', description);
      }
      if (catalogueReference !== undefined && catalogueReference !== null && catalogueReference !== '') {
        form.append('catalogueReference', catalogueReference);
      }
      if (designReference !== undefined && designReference !== null && designReference !== '') {
        form.append('designReference', designReference);
      }
      if (designRingVersion !== undefined && designRingVersion !== null) {
        form.append('designRingVersion', String(designRingVersion));
      }
      if (consentVersion !== undefined && consentVersion !== null && consentVersion !== '') {
        form.append('consentVersion', consentVersion);
      }
      if (consentPurpose !== undefined && consentPurpose !== null && consentPurpose !== '') {
        form.append('consentPurpose', consentPurpose);
      }
      form.append('designVersion', String(designVersion || 1));

      if (Array.isArray(images) && images.length > 0) {
        images.forEach(file => {
          if (file) {
            form.append('images', file);
          }
        });
      } else if (image) {
        form.append('image', image);
      }

      const headers = {};
      if (!auth) {
        let gSession = guestSessionId;
        if (!gSession && typeof sessionStorage !== 'undefined') {
          gSession = sessionStorage.getItem('mland_guest_design_session');
          if (!gSession) {
            gSession = 'guest_' + crypto.randomUUID();
            sessionStorage.setItem('mland_guest_design_session', gSession);
          }
        }
        if (gSession) {
          headers['X-Guest-Design-Session'] = gSession;
        }
      }

      const options = {
        method: 'POST',
        form,
        idempotencyKey: key,
        auth,
      };
      if (Object.keys(headers).length > 0) {
        options.headers = headers;
      }

      const res = await request('/api/v1/design-requests', options);

      if (res?.guestTrackingToken && typeof sessionStorage !== 'undefined') {
        sessionStorage.setItem(`mland_tracking_${res.id}`, res.guestTrackingToken);
      }

      return res;
    },

    /**
     * Read owner-safe Design Request projection (F04-ANALYSIS-STATUS / F04-RESULT).
     * Accessible by authenticated Member, Staff/Manager, or public guest lookup with tracking token.
     */
    getDesignRequest(designRequestId, authOrOptions = true, maybeToken = null) {
      let auth = true;
      let token = null;

      if (typeof authOrOptions === 'boolean') {
        auth = authOrOptions;
        token = maybeToken;
      } else if (authOrOptions && typeof authOrOptions === 'object') {
        auth = authOrOptions.auth !== false;
        token = authOrOptions.token || null;
      }

      if (!token && typeof sessionStorage !== 'undefined') {
        token = sessionStorage.getItem(`mland_tracking_${designRequestId}`);
      }

      const options = {
        method: 'GET',
        auth,
      };

      if (token) {
        options.headers = { 'X-Guest-Tracking-Token': token };
        options.query = { token };
      }

      return request(`/api/v1/design-requests/${encodeURIComponent(designRequestId)}`, options);
    },

    /**
     * Direct guest tracking endpoint.
     */
    getDesignRequestTracking(designRequestId, token) {
      return request(`/api/v1/design-requests/${encodeURIComponent(designRequestId)}/tracking`, {
        method: 'GET',
        query: { token },
        headers: { 'X-Guest-Tracking-Token': token },
        auth: false,
      });
    },

    /**
     * Read list of custom design requests owned by the authenticated member.
     */
    getMyDesignRequests() {
      return request('/api/v1/design-requests', {
        method: 'GET',
        auth: true,
      });
    },

    /**
     * Read Staff / Manager review queue (F04-REVIEW-QUEUE).
     * Optionally filtered by review status.
     */
    getReviewQueue(status) {
      const query = status ? `?status=${encodeURIComponent(status)}` : '';
      return request(`/api/v1/design-review-requests${query}`, {
        method: 'GET',
        auth: true,
      });
    },

    /**
     * Record human review decision (F04-REVIEW-DETAIL).
     * Uses requestVersion/designVersion for optimistic concurrency control.
     */
    recordReviewDecision(reviewRequestId, { decision, designVersion, requestVersion, explanation, reason }) {
      const ver = requestVersion ?? designVersion;
      const text = reason !== undefined ? reason : (explanation !== undefined ? explanation : '');
      const body = { decision, requestVersion: ver, designVersion: ver, reason: text };
      if (explanation !== undefined && explanation !== text) {
        body.explanation = explanation;
      }

      return request(`/api/v1/design-review-requests/${encodeURIComponent(reviewRequestId)}/decisions`, {
        method: 'POST',
        body,
        auth: true,
      });
    },

    /**
     * Request AI price suggestion for a workshop package or retail product proposal (F04-PRICE-PROPOSAL).
     * Manager / Owner only.
     */
    requestPriceSuggestion(proposalId, { targetType = 'WORKSHOP_PACKAGE', materials = [], components = [], pricingContext = {}, bodyContext } = {}) {
      const body = bodyContext !== undefined ? bodyContext : { targetType, materials, components, pricingContext };
      return request(`/api/v1/price-proposals/${encodeURIComponent(proposalId)}/ai-suggestions`, {
        method: 'POST',
        query: { targetType },
        body,
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

    /**
     * Submit revision after clarification request (Feature-004 Revision Flow).
     */
    reviseCustomDesignRequest(requestId, { description, designReference, designVersion, clarificationResponse }, { idempotencyKey, ifMatchVersion } = {}) {
      const headers = {};
      if (ifMatchVersion) {
        headers['If-Match-Version'] = String(ifMatchVersion);
      }
      return request(`/api/v1/custom-design-requests/${encodeURIComponent(requestId)}`, {
        method: 'POST',
        body: { description, designReference, designVersion, clarificationResponse },
        idempotencyKey,
        headers,
        auth: true,
      });
    },

    /**
     * Staff or Manager prepares quote for an accepted design request.
     */
    prepareCustomDesignQuote(requestId, { totalAmount, currency = 'VND', quoteExpiresAt, managerReason }, { idempotencyKey } = {}) {
      return request(`/api/v1/custom-design-requests/${encodeURIComponent(requestId)}/quote`, {
        method: 'POST',
        body: { totalAmount: String(totalAmount), currency, quoteExpiresAt, managerReason },
        idempotencyKey,
        auth: true,
      });
    },

    /**
     * Get latest quote for an accepted design request.
     */
    getCustomDesignQuote(requestId) {
      return request(`/api/v1/custom-design-requests/${encodeURIComponent(requestId)}/quote`, {
        method: 'GET',
        auth: true,
      });
    },

    /**
     * Manager approves custom design quote by designRequestId.
     */
    approveCustomDesignQuoteByRequestId(requestId, { managerReason } = {}) {
      return request(`/api/v1/custom-design-requests/${encodeURIComponent(requestId)}/quote/approve`, {
        method: 'POST',
        body: { managerReason: managerReason || 'Phê duyệt báo giá đơn hàng gia công tùy chỉnh' },
        auth: true,
      });
    },

    /**
     * Read list of Custom Orders owned by the Member.
     */
    listMyCustomOrders() {
      return request('/api/v1/members/me/custom-orders', {
        method: 'GET',
        auth: true,
      });
    },

    /**
     * Read detail of a Custom Order owned by the Member.
     */
    getMyCustomOrder(customOrderId) {
      return request(`/api/v1/members/me/custom-orders/${encodeURIComponent(customOrderId)}`, {
        method: 'GET',
        auth: true,
      });
    },

    /**
     * Initiate full payment for a Custom Order quote (100% full payment, NO cart, NO deposit).
     */
    startCustomOrderPayment(customOrderId, { idempotencyKey } = {}) {
      return request(`/api/v1/members/me/custom-orders/${encodeURIComponent(customOrderId)}/payment`, {
        method: 'POST',
        idempotencyKey: idempotencyKey || 'cust_pay_' + Date.now(),
        auth: true,
      });
    },

    /**
     * Request a new quote after previous quote has expired.
     */
    requestNewCustomOrderQuote(customOrderId, { idempotencyKey } = {}) {
      return request(`/api/v1/members/me/custom-orders/${encodeURIComponent(customOrderId)}/request-new-quote`, {
        method: 'POST',
        idempotencyKey: idempotencyKey || 'cust_req_' + Date.now(),
        auth: true,
      });
    },

    /**
     * Manager approves custom order quote.
     */
    approveCustomOrderQuote(customOrderId, quoteId, { managerReason } = {}) {
      const path = quoteId
        ? `/api/v1/custom-orders/${encodeURIComponent(customOrderId)}/quotes/${encodeURIComponent(quoteId)}/approve`
        : `/api/v1/custom-orders/${encodeURIComponent(customOrderId)}/quotes/approve`;
      return request(path, {
        method: 'POST',
        body: { managerReason: managerReason || 'Phê duyệt báo giá' },
        auth: true,
      });
    },

    /**
     * List custom orders for staff/manager operations queue.
     * Can be filtered by status: PAID, IN_PRODUCTION, READY_FOR_FULFILMENT, CLOSED, ALL.
     */
    async listOperationalCustomOrders({ status = '' } = {}, auth = true) {
      const query = status ? `?status=${encodeURIComponent(status)}` : '';
      return request(`/api/v1/custom-orders${query}`, {
        method: 'GET',
        auth,
      });
    },

    /**
     * Get operational custom order detail by ID or reference.
     */
    async getOperationalCustomOrder(customOrderId, auth = true) {
      return request(`/api/v1/custom-orders/${encodeURIComponent(customOrderId)}`, {
        method: 'GET',
        auth,
      });
    },

    /** Private reference image bytes; only the owner, Staff or a Manager receive them. */
    async getDesignImage(imageReference) {
      return request(`/api/v1/design-requests/images/${encodeURIComponent(imageReference)}`, { responseType: 'blob' });
    },

    /**
     * Update custom order production and fulfilment status (Staff / Manager).
     */
    async updateCustomOrderStatus(customOrderId, { productionStatus, fulfilmentStatus, status } = {}, auth = true) {
      return request(`/api/v1/custom-orders/${encodeURIComponent(customOrderId)}/status`, {
        method: 'POST',
        body: {
          ...(productionStatus ? { productionStatus } : {}),
          ...(fulfilmentStatus ? { fulfilmentStatus } : {}),
          ...(status ? { status } : {}),
        },
        auth,
      });
    },

    /**
     * Advance custom order shipment to READY_FOR_CARRIER (Staff handoff).
     */
    async advanceCustomOrderShipmentToReadyForCarrier(customOrderId, auth = true) {
      return request(`/api/v1/custom-orders/${encodeURIComponent(customOrderId)}/shipment/ready-for-carrier`, {
        method: 'POST',
        auth,
      });
    },
  };
}
