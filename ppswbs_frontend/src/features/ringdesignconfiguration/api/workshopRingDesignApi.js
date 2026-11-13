// Feature-012 Workshop Ring Design Configuration API Adapter
// Source of truth: contracts/workshop-ring-design.openapi.yaml

export const GUEST_SESSION_KEY = 'mland_guest_design_session';

export function getStoredGuestSession() {
  try {
    return sessionStorage.getItem(GUEST_SESSION_KEY) || null;
  } catch {
    return null;
  }
}

export function setStoredGuestSession(token) {
  try {
    if (token) {
      sessionStorage.setItem(GUEST_SESSION_KEY, token);
    } else {
      sessionStorage.removeItem(GUEST_SESSION_KEY);
    }
  } catch {
    // Ignore storage restrictions
  }
}

export function clearStoredGuestSession() {
  try {
    sessionStorage.removeItem(GUEST_SESSION_KEY);
  } catch {
    // Ignore storage restrictions
  }
}

export function workshopRingDesignApi(request) {
  function buildHeaders(guestSession, version) {
    const headers = {};
    const effectiveGuestSession = guestSession || getStoredGuestSession();
    if (effectiveGuestSession) {
      headers['X-Guest-Design-Session'] = effectiveGuestSession;
    }
    if (version !== undefined && version !== null) {
      headers['If-Match-Version'] = String(version);
    }
    return Object.keys(headers).length > 0 ? headers : undefined;
  }

  return {
    getDesignOptions(packageReference, branchReference) {
      return request('/api/v1/workshop-design-options', {
        method: 'GET',
        query: { packageReference, branchReference },
        auth: false,
      });
    },

    createDesign(key, body, guestSession, isAuthenticated = false) {
      return request('/api/v1/workshop-designs', {
        method: 'POST',
        body,
        idempotencyKey: key,
        headers: buildHeaders(guestSession),
        auth: isAuthenticated,
      });
    },

    getDesign(designId, guestSession, isAuthenticated = false) {
      return request(`/api/v1/workshop-designs/${encodeURIComponent(designId)}`, {
        method: 'GET',
        headers: buildHeaders(guestSession),
        auth: isAuthenticated,
      });
    },

    saveVersion(key, designId, body, version, guestSession, isAuthenticated = false) {
      return request(`/api/v1/workshop-designs/${encodeURIComponent(designId)}/versions`, {
        method: 'POST',
        body,
        idempotencyKey: key,
        headers: buildHeaders(guestSession, version),
        auth: isAuthenticated,
      });
    },

    evaluateDesign(key, designId, body, version, guestSession, isAuthenticated = false) {
      return request(`/api/v1/workshop-designs/${encodeURIComponent(designId)}/evaluate`, {
        method: 'POST',
        body,
        idempotencyKey: key,
        headers: buildHeaders(guestSession, version),
        auth: isAuthenticated,
      });
    },

    archiveDesign(key, designId, version, guestSession, isAuthenticated = false) {
      return request(`/api/v1/workshop-designs/${encodeURIComponent(designId)}/archive`, {
        method: 'POST',
        idempotencyKey: key,
        headers: buildHeaders(guestSession, version),
        auth: isAuthenticated,
      });
    },

    attachToBooking(key, designId, body, version, guestSession, isAuthenticated = false) {
      return request(`/api/v1/workshop-designs/${encodeURIComponent(designId)}/attach-to-booking`, {
        method: 'POST',
        body,
        idempotencyKey: key,
        headers: buildHeaders(guestSession, version),
        auth: isAuthenticated,
      });
    },
  };
}

