// Shared HTTP client (proposal §8, 002 T089). It never caches responses, never retries on its own and never
// writes the token anywhere: the token is read from the session for each request and sent only as a header.
export const apiBaseUrl = (typeof window !== 'undefined' ? window.location.origin : 'http://localhost:8080');

/** Typed failure built from common.ErrorResponse: code, safe message, correlationId, fieldErrors. */
export class ApiError extends Error {
  constructor({ status, code, message, correlationId = null, fieldErrors = [], method = 'GET', failingTicketCodes = [], reason = null, retryAfterSeconds = null, checkInOpensAt = null, quota = null, guestSessionToken = null }) {
    super(message || code);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
    this.correlationId = correlationId;
    this.fieldErrors = fieldErrors;
    this.method = method;
    this.failingTicketCodes = failingTicketCodes;
    // Closed `reason` of a 409 INVALID_STATE / STAFF_NOT_ELIGIBLE and whole seconds of a 429 Retry-After header.
    this.reason = reason;
    this.retryAfterSeconds = retryAfterSeconds;
    // Only with 409 CHECK_IN_NOT_OPEN: ISO instant when normal check-in opens (T126).
    this.checkInOpensAt = checkInOpensAt;
    this.quota = quota;
    this.guestSessionToken = guestSessionToken;
  }

  /** 503 and lost responses may be retried with the same Idempotency-Key. */
  get retryable() {
    return this.status === 503 || this.status === 0;
  }

  get stale() {
    return this.status === 409;
  }

  fieldError(field) {
    return this.fieldErrors.find(item => item.field === field)?.message;
  }
}

/** One key per user action; a retry of the same action reuses it. */
export function newCommandId() {
  return crypto.randomUUID();
}

function buildUrl(baseUrl, path, query) {
  const origin = baseUrl || (typeof window !== 'undefined' ? window.location.origin : 'http://localhost:8080');
  const normalizedPath = path.startsWith('/') ? path : `/${path}`;
  const url = new URL(`${origin}${normalizedPath}`);
  Object.entries(query || {}).forEach(([key, value]) => {
    if (Array.isArray(value)) value.forEach(item => url.searchParams.append(key, String(item)));
    else if (value !== undefined && value !== null && value !== '') url.searchParams.set(key, String(value));
  });
  return url.toString();
}

/** Retry-After as whole seconds (>= 1); an HTTP date or junk is ignored. */
function retryAfterOf(value) {
  if (!value || !/^\d+$/.test(value.trim())) return null;
  const seconds = Number(value.trim());
  return seconds >= 1 ? seconds : null;
}

async function errorFrom(response, method) {
  const payload = await response.json().catch(() => ({}));
  return new ApiError({
    status: response.status,
    code: payload.code || `HTTP_${response.status}`,
    message: payload.message || payload.detail || '',
    correlationId: payload.correlationId || response.headers.get('X-Correlation-Id'),
    fieldErrors: Array.isArray(payload.fieldErrors) ? payload.fieldErrors : [],
    method,
    failingTicketCodes: Array.isArray(payload.failingTicketCodes) ? payload.failingTicketCodes : [],
    reason: typeof payload.reason === 'string' ? payload.reason : null,
    retryAfterSeconds: retryAfterOf(response.headers.get('Retry-After')),
    checkInOpensAt: typeof payload.checkInOpensAt === 'string' ? payload.checkInOpensAt : null,
    quota: payload.quota || null,
    guestSessionToken: payload.guestSessionToken || response.headers.get('X-Guest-AI-Session'),
  });
}

async function readBody(response, responseType) {
  if (response.status === 204) return undefined;
  if (responseType === 'blob') {
    return { blob: await response.blob(), fileName: fileNameOf(response.headers.get('Content-Disposition')) };
  }
  const text = await response.text();
  return text ? JSON.parse(text) : undefined;
}

function fileNameOf(disposition) {
  if (!disposition) return null;
  const encoded = /filename\*=UTF-8''([^;]+)/i.exec(disposition);
  if (encoded) return decodeURIComponent(encoded[1]);
  return /filename="?([^";]+)"?/i.exec(disposition)?.[1] || null;
}

/**
 * @param getIdToken (forceRefresh) => Promise<string|null>, from the session
 * @param onSessionExpired called once a refreshed token is still refused (401): session lost
 * @param onAccessDenied called on 403 authorization refusal: safe home routing
 */
export function createHttpClient({ baseUrl = apiBaseUrl, getIdToken, onSessionExpired, onAccessDenied, fetchImpl } = {}) {
  const doFetch = (...args) => (fetchImpl || globalThis.fetch)(...args);

  async function send(url, { method, headers, body, signal }, token) {
    // 'ngrok-skip-browser-warning' skips ngrok's free-tier warning page, which has no CORS headers and breaks every call.
    const allHeaders = { Accept: 'application/json', 'ngrok-skip-browser-warning': '1', ...headers };
    if (token) allHeaders.Authorization = `Bearer ${token}`;
    try {
      return await doFetch(url, { method, headers: allHeaders, body, signal });
    } catch (error) {
      if (error?.name === 'AbortError') throw error;
      throw new ApiError({ status: 0, code: 'NETWORK_ERROR', message: '', method });
    }
  }

  return async function request(path, {
    method = 'GET', query, body, form, idempotencyKey, auth = true, responseType = 'json', signal, headers: customHeaders, suppressAccessDenied = false,
  } = {}) {
    const url = buildUrl(baseUrl, path, query);
    const headers = { ...(customHeaders || {}) };
    let payload;
    if (form) payload = form;
    else if (body !== undefined) { headers['Content-Type'] = 'application/json'; payload = JSON.stringify(body); }
    if (idempotencyKey) headers['Idempotency-Key'] = idempotencyKey;
    const init = { method, headers, body: payload, signal };

    let token = null;
    if (auth) {
      token = getIdToken ? await getIdToken(false) : null;
      if (!token) throw new ApiError({ status: 401, code: 'AUTHENTICATION_REQUIRED', message: '', method });
    }
    let response = await send(url, init, token);
    if (auth && response.status === 401) {
      let initialError = null;
      try {
        const cloned = typeof response.clone === 'function' ? response.clone() : response;
        initialError = await errorFrom(cloned, method);
      } catch {
        initialError = null;
      }
      if (initialError?.code === 'ACCOUNT_NOT_FOUND') {
        // FR-046: 401 ACCOUNT_NOT_FOUND is terminal; MUST NOT retry getIdToken(true)
        onSessionExpired?.(initialError);
        throw initialError;
      }
      const refreshed = await getIdToken(true);
      if (refreshed) response = await send(url, init, refreshed);
      if (response.status === 401) {
        const error = await errorFrom(response, method);
        onSessionExpired?.(error);
        throw error;
      }
    }
    if (!response.ok) {
      // A command that explains its own refusal on its screen (suppressAccessDenied) is not a reason to leave the page.
      if (response.status === 403 && !suppressAccessDenied) {
        onAccessDenied?.();
      }
      throw await errorFrom(response, method);
    }
    return readBody(response, responseType);
  };
}
