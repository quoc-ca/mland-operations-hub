const UUID_REGEX = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export class WorkshopApiError extends Error {
  constructor(message, { status, code, correlationId, detail, data } = {}) {
    super(message || detail || 'Workshop API error');
    this.name = 'WorkshopApiError';
    this.status = status;
    this.code = code || (status ? `WORKSHOP_${status}` : 'WORKSHOP_ERROR');
    this.correlationId = correlationId || null;
    this.detail = detail || message;
    this.data = data || null;
  }
}

export function isValidUuid(val) {
  return typeof val === 'string' && UUID_REGEX.test(val);
}

export function getApiBaseUrl() {
  const envUrl = (typeof import.meta !== 'undefined' && import.meta?.env?.VITE_API_BASE_URL)
    || (typeof process !== 'undefined' && process?.env?.VITE_API_BASE_URL);
  return (envUrl || 'http://localhost:8080').replace(/\/$/, '');
}

async function request(path, { params = {}, method = 'GET', headers = {}, body = null } = {}) {
  const base = getApiBaseUrl();
  const url = new URL(`${base}${path}`);
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') {
      url.searchParams.set(key, String(value));
    }
  });

  const requestHeaders = {
    Accept: 'application/json, application/problem+json',
    ...headers,
  };
  if (body !== null && body !== undefined && !requestHeaders['Content-Type']) {
    requestHeaders['Content-Type'] = 'application/json';
  }

  const response = await fetch(url, {
    method,
    headers: requestHeaders,
    body: body !== null && body !== undefined ? (typeof body === 'string' ? body : JSON.stringify(body)) : undefined,
  });

  if (!response.ok) {
    let errorBody = null;
    try {
      errorBody = await response.json();
    } catch (_) {
      // not JSON
    }
    const code = errorBody?.code || `WORKSHOP_${response.status}`;
    const detail = errorBody?.detail || errorBody?.title || errorBody?.message || `HTTP ${response.status}`;
    const correlationId = errorBody?.correlationId || response.headers?.get?.('x-correlation-id') || null;
    throw new WorkshopApiError(detail, {
      status: response.status,
      code,
      correlationId,
      detail,
      data: errorBody,
    });
  }

  if (response.status === 204) return null;
  return response.json();
}

/** Public discovery owned by Workshop Operations (003.2); capacity is only a snapshot. */
export async function listWorkshopPackages() {
  return request('/api/v1/workshop-packages');
}

export async function getWorkshopPackage(packageId) {
  if (packageId === undefined || packageId === null || packageId === '') {
    throw new Error('WORKSHOP_PACKAGE_ID_REQUIRED');
  }
  return request(`/api/v1/workshop-packages/${encodeURIComponent(packageId)}`);
}

export async function listWorkshopSessions(packageId, { locationId, from, to } = {}) {
  if (packageId === undefined || packageId === null || packageId === '') {
    throw new Error('WORKSHOP_PACKAGE_ID_REQUIRED');
  }
  if (locationId === undefined || locationId === null || locationId === '') {
    throw new Error('WORKSHOP_LOCATION_ID_REQUIRED');
  }
  return request(`/api/v1/workshop-packages/${encodeURIComponent(packageId)}/sessions`, {
    params: { locationId, from, to },
  });
}

/**
 * Previews a Member workshop quote with the selected loyalty benefits.
 * The server returns the immutable quote facts, including maxPoints; the
 * client must display those facts and pass quoteRef to createWorkshopBooking.
 */
export async function previewWorkshopBookingQuote({
  packageId,
  sessionReferences,
  participantCount,
  benefitSelection,
  authorization,
} = {}) {
  if (packageId === undefined || packageId === null || packageId === '') {
    throw new Error('WORKSHOP_PACKAGE_ID_REQUIRED');
  }
  if (!Array.isArray(sessionReferences) || sessionReferences.length === 0) {
    throw new Error('SESSION_REFERENCES_REQUIRED');
  }
  sessionReferences.forEach((ref) => {
    if (!isValidUuid(ref)) throw new Error(`INVALID_SESSION_REFERENCE_UUID: ${ref}`);
  });
  const count = Number(participantCount);
  if (!Number.isInteger(count) || count < 1) {
    throw new Error('PARTICIPANT_COUNT_INVALID');
  }
  if (!benefitSelection || typeof benefitSelection !== 'object') {
    throw new Error('BENEFIT_SELECTION_REQUIRED');
  }
  const headers = {};
  if (authorization) headers.Authorization = authorization;
  return request('/api/v1/workshop-booking-quotes', {
    method: 'POST',
    headers,
    body: {
      packageId: Number(packageId),
      sessionReferences,
      participantCount: count,
      benefitSelection,
    },
  });
}

/**
 * Creates one workshop booking atomically (003.1).
 * Supports Guest bookings (packageId, sessionReferences, participantCount, contactEmail, contactPhone)
 * and Member Quote bookings (quoteRef, contactEmail, contactPhone).
 * Never exposes credentials or PII in client error handling.
 */
export async function createWorkshopBooking({
  packageId,
  sessionReferences,
  participantCount,
  contactEmail,
  contactPhone,
  quoteRef,
  idempotencyKey,
  correlationId,
  authorization,
} = {}) {
  const key = idempotencyKey || (typeof crypto !== 'undefined' && crypto.randomUUID ? crypto.randomUUID() : null);
  if (!key) {
    throw new Error('IDEMPOTENCY_KEY_REQUIRED');
  }

  if (!contactEmail || typeof contactEmail !== 'string' || !contactEmail.includes('@')) {
    throw new Error('CONTACT_EMAIL_INVALID');
  }
  if (!contactPhone || typeof contactPhone !== 'string' || contactPhone.trim().length < 7) {
    throw new Error('CONTACT_PHONE_INVALID');
  }

  let body;
  if (quoteRef) {
    if (!isValidUuid(quoteRef)) {
      throw new Error('QUOTE_REF_INVALID_UUID');
    }
    body = {
      quoteRef,
      contactEmail: contactEmail.trim(),
      contactPhone: contactPhone.trim(),
    };
  } else {
    if (packageId === undefined || packageId === null || packageId === '') {
      throw new Error('WORKSHOP_PACKAGE_ID_REQUIRED');
    }
    if (!Array.isArray(sessionReferences) || sessionReferences.length === 0) {
      throw new Error('SESSION_REFERENCES_REQUIRED');
    }
    for (const ref of sessionReferences) {
      if (!isValidUuid(ref)) {
        throw new Error(`INVALID_SESSION_REFERENCE_UUID: ${ref}`);
      }
    }
    const count = Number(participantCount);
    if (!count || count < 1) {
      throw new Error('PARTICIPANT_COUNT_INVALID');
    }
    body = {
      packageId: Number(packageId),
      sessionReferences,
      participantCount: count,
      contactEmail: contactEmail.trim(),
      contactPhone: contactPhone.trim(),
    };
  }

  const headers = {
    'Idempotency-Key': key,
  };
  if (correlationId) {
    headers['X-Correlation-Id'] = correlationId;
  }
  if (authorization) {
    headers['Authorization'] = authorization;
  }

  return request('/api/v1/workshop-bookings', {
    method: 'POST',
    headers,
    body,
  });
}

/**
 * Initiates or retries the 50 percent deposit payment attempt for an active hold (003.1 US3).
 * Must not create a new attempt if the current attempt is still pending.
 * Returns { attemptReference, merchantReference, amountVnd, currency, paymentUrl, expiresAt, status }.
 */
export async function initiateDepositPayment(bookingCode, { idempotencyKey, correlationId } = {}) {
  if (!bookingCode || typeof bookingCode !== 'string' || !bookingCode.trim()) {
    throw new Error('BOOKING_CODE_REQUIRED');
  }
  const key = idempotencyKey || (typeof crypto !== 'undefined' && crypto.randomUUID ? crypto.randomUUID() : null);
  if (!key) {
    throw new Error('IDEMPOTENCY_KEY_REQUIRED');
  }
  const headers = {
    'Idempotency-Key': key,
  };
  if (correlationId) headers['X-Correlation-Id'] = correlationId;

  return request(`/api/v1/workshop-bookings/${encodeURIComponent(bookingCode.trim())}/payment-attempts`, {
    method: 'POST',
    headers,
  });
}

/**
 * Looks up a Guest booking projection using the bookingCode and exactly one stored contact proof (003.1 US4).
 * Returns BookingSummary with safe projection (never discloses raw PII, payment secrets, or internal IDs).
 */
export async function lookupGuestBooking({ bookingCode, contactEmail, contactPhone } = {}) {
  if (!bookingCode || typeof bookingCode !== 'string' || !bookingCode.trim()) {
    throw new Error('BOOKING_CODE_REQUIRED');
  }
  const hasEmail = Boolean(contactEmail && typeof contactEmail === 'string' && contactEmail.includes('@'));
  const hasPhone = Boolean(contactPhone && typeof contactPhone === 'string' && contactPhone.trim().length >= 7);

  if ((hasEmail && hasPhone) || (!hasEmail && !hasPhone)) {
    throw new Error('EXACTLY_ONE_CONTACT_PROOF_REQUIRED');
  }

  const body = {
    bookingCode: bookingCode.trim(),
  };
  if (hasEmail) body.contactEmail = contactEmail.trim();
  if (hasPhone) body.contactPhone = contactPhone.trim();

  return request('/api/v1/booking-lookups', {
    method: 'POST',
    body,
  });
}

/**
 * Reads an owned Member booking projection using Bearer authorization (003.1 US4).
 */
export async function lookupMemberBooking(bookingCode, { authorization } = {}) {
  if (!bookingCode || typeof bookingCode !== 'string' || !bookingCode.trim()) {
    throw new Error('BOOKING_CODE_REQUIRED');
  }
  const headers = {};
  if (authorization) headers['Authorization'] = authorization;

  return request(`/api/v1/members/me/workshop-bookings/${encodeURIComponent(bookingCode.trim())}`, {
    method: 'GET',
    headers,
  });
}

/**
 * Exchanges a protected Magic-Link bearer token (extracted strictly from URL fragment) (003.1 US4).
 * The token is never logged, never persisted, and only stored in memory-only state.
 */
export async function exchangeMagicLinkToken(token) {
  if (!token || typeof token !== 'string' || !token.trim()) {
    throw new Error('MAGIC_LINK_TOKEN_REQUIRED');
  }
  return request('/api/v1/booking-magic-links/exchanges', {
    method: 'POST',
    body: { token: token.trim() },
  });
}

/**
 * Releases an active pending hold from lookup (003.1 US4).
 * Requires audit-safe reason and exactly one contact proof (Guest) or Bearer authorization (Member).
 */
export async function releasePendingHold(bookingCode, { contactEmail, contactPhone, reason, authorization, idempotencyKey, correlationId } = {}) {
  if (!bookingCode || typeof bookingCode !== 'string' || !bookingCode.trim()) {
    throw new Error('BOOKING_CODE_REQUIRED');
  }
  if (!reason || typeof reason !== 'string' || !reason.trim()) {
    throw new Error('RELEASE_REASON_REQUIRED');
  }
  const key = idempotencyKey || (typeof crypto !== 'undefined' && crypto.randomUUID ? crypto.randomUUID() : null);
  if (!key) {
    throw new Error('IDEMPOTENCY_KEY_REQUIRED');
  }

  const headers = { 'Idempotency-Key': key };
  if (correlationId) headers['X-Correlation-Id'] = correlationId;
  if (authorization) headers['Authorization'] = authorization;

  const body = {
    reason: reason.trim(),
  };
  if (contactEmail) body.contactEmail = contactEmail.trim();
  if (contactPhone) body.contactPhone = contactPhone.trim();

  return request(`/api/v1/workshop-bookings/${encodeURIComponent(bookingCode.trim())}/pending-hold-release`, {
    method: 'POST',
    headers,
    body,
  });
}

/**
 * Requests confirmation resend to the stored contact (003.1 US4).
 */
export async function requestConfirmationDelivery(bookingCode, { contactEmail, contactPhone, authorization, idempotencyKey, correlationId } = {}) {
  if (!bookingCode || typeof bookingCode !== 'string' || !bookingCode.trim()) {
    throw new Error('BOOKING_CODE_REQUIRED');
  }
  const key = idempotencyKey || (typeof crypto !== 'undefined' && crypto.randomUUID ? crypto.randomUUID() : null);
  if (!key) {
    throw new Error('IDEMPOTENCY_KEY_REQUIRED');
  }

  const headers = { 'Idempotency-Key': key };
  if (correlationId) headers['X-Correlation-Id'] = correlationId;
  if (authorization) headers['Authorization'] = authorization;

  const body = {};
  if (contactEmail) body.contactEmail = contactEmail.trim();
  if (contactPhone) body.contactPhone = contactPhone.trim();

  return request(`/api/v1/workshop-bookings/${encodeURIComponent(bookingCode.trim())}/confirmation-deliveries`, {
    method: 'POST',
    headers,
    body: Object.keys(body).length > 0 ? body : undefined,
  });
}
