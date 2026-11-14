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

  let response;
  try {
    response = await fetch(url, {
      method,
      headers: requestHeaders,
      body: body !== null && body !== undefined ? (typeof body === 'string' ? body : JSON.stringify(body)) : undefined,
    });
  } catch (error) {
    throw new WorkshopApiError('Không nhận được phản hồi từ backend.', {
      status: 0,
      code: error?.name === 'AbortError' ? 'REQUEST_TIMEOUT' : 'NETWORK_ERROR',
      detail: 'Không nhận được phản hồi từ backend.',
    });
  }

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

export async function listWorkshopLocations(packageId, { from, to } = {}) {
  if (packageId === undefined || packageId === null || packageId === '') {
    throw new Error('WORKSHOP_PACKAGE_ID_REQUIRED');
  }
  return request(`/api/v1/workshop-packages/${encodeURIComponent(packageId)}/locations`, {
    params: { from, to },
  });
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

/* ------------------------------------------------------------------------- *
 * Booking group routes (003.1 FR-044–FR-051, decision 2026-11-13).
 * A direct booking is only a booking group: the cart is quoted, then held as
 * one group with one ticket booking per person. The single-booking routes were
 * deleted (FR-046), so nothing here may call them.
 * Contract: contracts/workshop-booking-cart.openapi.yaml. Field names follow
 * the implemented responses (groupStatus, …Vnd), which T104 aligns the
 * contract to.
 * ------------------------------------------------------------------------- */

/** A command key of 16 to 128 characters, as the contract's Idempotency-Key requires. */
function commandKey(provided) {
  const key = provided
    || (typeof crypto !== 'undefined' && crypto.randomUUID ? crypto.randomUUID() : null)
    || `cmd-${Date.now()}-${Math.random().toString(16).slice(2)}-${Math.random().toString(16).slice(2)}`;
  if (typeof key !== 'string' || key.length < 16 || key.length > 128) {
    throw new Error('IDEMPOTENCY_KEY_REQUIRED');
  }
  return key;
}

function commandHeaders({ idempotencyKey, correlationId, authorization } = {}) {
  const headers = { 'Idempotency-Key': commandKey(idempotencyKey) };
  if (correlationId) headers['X-Correlation-Id'] = correlationId;
  if (authorization) headers.Authorization = authorization;
  return headers;
}

/** One cart line: a package, its complete required-session set and the number of people (FR-030). */
function normalizeCartLine(line, index) {
  const packageId = line?.packageId;
  if (packageId === undefined || packageId === null || packageId === '') {
    throw new Error(`WORKSHOP_PACKAGE_ID_REQUIRED: line ${index}`);
  }
  const sessionReferences = line?.sessionReferences;
  if (!Array.isArray(sessionReferences) || sessionReferences.length === 0) {
    throw new Error(`SESSION_REFERENCES_REQUIRED: line ${index}`);
  }
  sessionReferences.forEach(ref => {
    if (!isValidUuid(ref)) throw new Error(`INVALID_SESSION_REFERENCE_UUID: ${ref}`);
  });
  const participantCount = Number(line?.participantCount);
  if (!Number.isInteger(participantCount) || participantCount < 1) {
    throw new Error(`PARTICIPANT_COUNT_INVALID: line ${index}`);
  }
  return { packageId: Number(packageId), sessionReferences, participantCount };
}

/**
 * Quotes a cart of one or more lines (FR-030, FR-033 to FR-037). Guest sends no Authorization; a Member sends the
 * bearer so the voucher and points of that account are allocated. Nothing is held and no capacity is reserved. The
 * server owns every amount: the screen shows the returned per-ticket facts and passes quoteRef to createBookingGroup.
 */
export async function quoteWorkshopCart({ lines, benefitSelection, authorization } = {}) {
  if (!Array.isArray(lines) || lines.length === 0) {
    throw new Error('CART_LINES_REQUIRED');
  }
  const body = { lines: lines.map(normalizeCartLine) };
  if (benefitSelection) {
    body.benefitSelection = {
      choice: benefitSelection.choice || 'NONE',
      voucherCode: benefitSelection.choice === 'VOUCHER' && benefitSelection.voucherCode?.trim()
        ? benefitSelection.voucherCode.trim()
        : null,
      requestedPoints: String(benefitSelection.requestedPoints ?? '0').trim() || '0',
    };
  }
  const headers = {};
  if (authorization) headers.Authorization = authorization;
  return request('/api/v1/workshop-cart-quotes', { method: 'POST', headers, body });
}

/**
 * Confirms a quoted cart into one booking group, holding every line all or nothing (FR-031). A capacity failure
 * answers 409 with failingLines and holds nothing, so the caller keeps the cart editable.
 */
export async function createBookingGroup({
  quoteRef,
  contactEmail,
  contactPhone,
  idempotencyKey,
  correlationId,
  authorization,
} = {}) {
  if (!quoteRef || typeof quoteRef !== 'string' || !quoteRef.trim()) {
    throw new Error('QUOTE_REF_REQUIRED');
  }
  if (!contactEmail || typeof contactEmail !== 'string' || !contactEmail.includes('@')) {
    throw new Error('CONTACT_EMAIL_INVALID');
  }
  if (!contactPhone || typeof contactPhone !== 'string' || contactPhone.trim().length < 7) {
    throw new Error('CONTACT_PHONE_INVALID');
  }
  return request('/api/v1/workshop-booking-groups', {
    method: 'POST',
    headers: commandHeaders({ idempotencyKey, correlationId, authorization }),
    body: {
      quoteRef: quoteRef.trim(),
      contactEmail: contactEmail.trim(),
      contactPhone: contactPhone.trim(),
    },
  });
}

/**
 * Starts or retries the single VNPay payment of the whole group (FR-032, FR-038). A retry never extends the hold; the
 * returned expiresAt is the customer-visible 10-minute deadline.
 */
export async function initiateGroupPayment(groupCode, { idempotencyKey, correlationId, authorization } = {}) {
  if (!groupCode || typeof groupCode !== 'string' || !groupCode.trim()) {
    throw new Error('GROUP_CODE_REQUIRED');
  }
  return request(`/api/v1/workshop-booking-groups/${encodeURIComponent(groupCode.trim())}/payment-attempts`, {
    method: 'POST',
    headers: commandHeaders({ idempotencyKey, correlationId, authorization }),
  });
}

/** Reads the owning Member's group with its tickets (FR-048). A group the Member does not own answers 404. */
export async function getOwnedBookingGroup(groupCode, { authorization } = {}) {
  if (!groupCode || typeof groupCode !== 'string' || !groupCode.trim()) {
    throw new Error('GROUP_CODE_REQUIRED');
  }
  const headers = {};
  if (authorization) headers.Authorization = authorization;
  return request(`/api/v1/workshop-booking-groups/${encodeURIComponent(groupCode.trim())}`, {
    method: 'GET',
    headers,
  });
}

/** The signed-in Member's own groups, newest first (FR-046, FR-048); at most 30. */
export async function listMemberBookingGroups({ authorization } = {}) {
  const headers = {};
  if (authorization) headers.Authorization = authorization;
  const result = await request('/api/v1/members/me/workshop-booking-groups', { method: 'GET', headers });
  return Array.isArray(result) ? result : [];
}

/**
 * Guest lookup of a group by group code and exactly one stored contact value (FR-048). Contact proof travels in the
 * body, never in a URL, and the answer never says which part failed.
 */
export async function lookupGuestBookingGroup({ groupCode, contactEmail, contactPhone } = {}) {
  if (!groupCode || typeof groupCode !== 'string' || !groupCode.trim()) {
    throw new Error('GROUP_CODE_REQUIRED');
  }
  const hasEmail = Boolean(contactEmail && typeof contactEmail === 'string' && contactEmail.includes('@'));
  const hasPhone = Boolean(contactPhone && typeof contactPhone === 'string' && contactPhone.trim().length >= 7);
  if ((hasEmail && hasPhone) || (!hasEmail && !hasPhone)) {
    throw new Error('EXACTLY_ONE_CONTACT_PROOF_REQUIRED');
  }
  const body = { groupCode: groupCode.trim() };
  if (hasEmail) body.contactEmail = contactEmail.trim();
  if (hasPhone) body.contactPhone = contactPhone.trim();
  return request('/api/v1/workshop-booking-group-lookups', { method: 'POST', body });
}

/**
 * Exchanges the group Magic-Link token (read strictly from the URL fragment) for the read-only group projection
 * (FR-048). The token is never persisted or logged; expired, revoked and unknown tokens answer the same 404.
 */
export async function exchangeGroupMagicLinkToken(token) {
  if (!token || typeof token !== 'string' || !token.trim()) {
    throw new Error('MAGIC_LINK_TOKEN_REQUIRED');
  }
  return request('/api/v1/workshop-booking-group-magic-links/exchanges', {
    method: 'POST',
    body: { token: token.trim() },
  });
}

/**
 * Releases a PENDING_PAYMENT group's holds before payment (FR-048): a Guest sends exactly one stored contact proof and
 * no bearer, the owning Member sends the bearer and no proof; both give an audit reason. Refused with 409
 * PAYMENT_ATTEMPT_EXISTS once a group payment attempt exists.
 */
export async function releaseGroupPendingHold(groupCode, {
  contactEmail,
  contactPhone,
  reason,
  authorization,
  idempotencyKey,
  correlationId,
} = {}) {
  if (!groupCode || typeof groupCode !== 'string' || !groupCode.trim()) {
    throw new Error('GROUP_CODE_REQUIRED');
  }
  if (!reason || typeof reason !== 'string' || !reason.trim()) {
    throw new Error('RELEASE_REASON_REQUIRED');
  }
  const body = { reason: reason.trim() };
  if (contactEmail) body.contactEmail = contactEmail.trim();
  if (contactPhone) body.contactPhone = contactPhone.trim();
  return request(`/api/v1/workshop-booking-groups/${encodeURIComponent(groupCode.trim())}/pending-hold-release`, {
    method: 'POST',
    headers: commandHeaders({ idempotencyKey, correlationId, authorization }),
    body,
  });
}

/** Resends the buyer confirmation (every ticket) of a CONFIRMED group to the stored contact email only (FR-048). */
export async function requestGroupConfirmationDelivery(groupCode, {
  contactEmail,
  contactPhone,
  authorization,
  idempotencyKey,
  correlationId,
} = {}) {
  if (!groupCode || typeof groupCode !== 'string' || !groupCode.trim()) {
    throw new Error('GROUP_CODE_REQUIRED');
  }
  const body = {};
  if (contactEmail) body.contactEmail = contactEmail.trim();
  if (contactPhone) body.contactPhone = contactPhone.trim();
  return request(`/api/v1/workshop-booking-groups/${encodeURIComponent(groupCode.trim())}/confirmation-deliveries`, {
    method: 'POST',
    headers: commandHeaders({ idempotencyKey, correlationId, authorization }),
    body: Object.keys(body).length > 0 ? body : undefined,
  });
}

/** Manager revokes or rotates the group Magic Link (FR-048). */
export async function submitGroupMagicLinkIncident(groupCode, {
  action,
  reason,
  authorization,
  idempotencyKey,
  correlationId,
} = {}) {
  if (!groupCode?.trim()) throw new Error('GROUP_CODE_REQUIRED');
  if (!['REVOKE', 'ROTATE'].includes(action)) throw new Error('MAGIC_LINK_INCIDENT_ACTION_INVALID');
  if (!reason?.trim()) throw new Error('INCIDENT_REASON_REQUIRED');
  return request(`/api/v1/workshop-booking-groups/${encodeURIComponent(groupCode.trim())}/magic-link-incidents`, {
    method: 'POST',
    headers: commandHeaders({ idempotencyKey, correlationId, authorization }),
    body: { action, reason: reason.trim() },
  });
}

/**
 * Sends a buyer-verification OTP to the group's original contact email (FR-041). The caller proves the booking with
 * exactly one stored contact value; the response never reveals whether the contact matched.
 */
export async function requestBuyerVerification(groupCode, { contactEmail, contactPhone, correlationId } = {}) {
  if (!groupCode?.trim()) throw new Error('GROUP_CODE_REQUIRED');
  const hasEmail = Boolean(contactEmail && contactEmail.includes('@'));
  const hasPhone = Boolean(contactPhone && contactPhone.trim().length >= 7);
  if ((hasEmail && hasPhone) || (!hasEmail && !hasPhone)) {
    throw new Error('EXACTLY_ONE_CONTACT_PROOF_REQUIRED');
  }
  const headers = {};
  if (correlationId) headers['X-Correlation-Id'] = correlationId;
  const body = {};
  if (hasEmail) body.contactEmail = contactEmail.trim();
  if (hasPhone) body.contactPhone = contactPhone.trim();
  return request(`/api/v1/workshop-booking-groups/${encodeURIComponent(groupCode.trim())}/buyer-verifications`, {
    method: 'POST',
    headers,
    body,
  });
}

/** Confirms the OTP and returns the single-use buyerVerificationReference of the group (FR-041). */
export async function confirmBuyerVerification(groupCode, verificationId, { otp } = {}) {
  if (!groupCode?.trim()) throw new Error('GROUP_CODE_REQUIRED');
  if (!verificationId || typeof verificationId !== 'string' || !verificationId.trim()) {
    throw new Error('VERIFICATION_ID_REQUIRED');
  }
  if (!/^[0-9]{6}$/.test(String(otp || '').trim())) throw new Error('OTP_INVALID');
  return request(
    `/api/v1/workshop-booking-groups/${encodeURIComponent(groupCode.trim())}`
      + `/buyer-verifications/${encodeURIComponent(verificationId.trim())}/confirmations`,
    { method: 'POST', body: { otp: String(otp).trim() } },
  );
}

/**
 * Sends one ticket to a recipient email, or changes its recipient (FR-041). A Member buyer sends the bearer; a Guest
 * buyer sends buyerVerificationReference; Staff or Manager sends a bearer plus reference and reason. Changing to a
 * different email counts as one of at most 3 online changes.
 */
export async function changeTicketRecipient(ticketCode, {
  recipientEmail,
  buyerVerificationReference,
  reason,
  authorization,
  idempotencyKey,
  correlationId,
} = {}) {
  if (!ticketCode || typeof ticketCode !== 'string' || !ticketCode.trim()) {
    throw new Error('TICKET_CODE_REQUIRED');
  }
  if (!recipientEmail || typeof recipientEmail !== 'string' || !recipientEmail.includes('@')) {
    throw new Error('RECIPIENT_EMAIL_INVALID');
  }
  const body = { recipientEmail: recipientEmail.trim() };
  if (buyerVerificationReference) body.buyerVerificationReference = buyerVerificationReference.trim();
  if (reason?.trim()) body.reason = reason.trim();
  return request(`/api/v1/workshop-tickets/${encodeURIComponent(ticketCode.trim())}/recipient`, {
    method: 'PUT',
    headers: commandHeaders({ idempotencyKey, correlationId, authorization }),
    body,
  });
}

/** Manager queue of late-payment reviews (FR-040a); it carries no buyer contact data. */
export async function listLatePaymentReviews({ status, authorization } = {}) {
  const headers = {};
  if (authorization) headers.Authorization = authorization;
  const result = await request('/api/v1/late-payment-reviews', {
    method: 'GET',
    headers,
    params: { status },
  });
  return Array.isArray(result) ? result : [];
}

/**
 * Manager decision on a late group payment (FR-040, FR-040a): CONFIRM only while the review allows it, otherwise
 * REFUND, which opens a 100% bank refund in 003.3.
 */
export async function decideLatePaymentReview(groupCode, reviewId, {
  decision,
  reason,
  authorization,
  idempotencyKey,
  correlationId,
} = {}) {
  if (!groupCode?.trim()) throw new Error('GROUP_CODE_REQUIRED');
  if (!reviewId || typeof reviewId !== 'string' || !reviewId.trim()) throw new Error('REVIEW_ID_REQUIRED');
  if (!['CONFIRM', 'REFUND'].includes(decision)) throw new Error('LATE_PAYMENT_DECISION_INVALID');
  if (!reason?.trim()) throw new Error('DECISION_REASON_REQUIRED');
  return request(
    `/api/v1/workshop-booking-groups/${encodeURIComponent(groupCode.trim())}`
      + `/late-payment-reviews/${encodeURIComponent(reviewId.trim())}/decisions`,
    {
      method: 'POST',
      headers: commandHeaders({ idempotencyKey, correlationId, authorization }),
      body: { decision, reason: reason.trim() },
    },
  );
}
