// Shared HTTP client (proposal §8, 002 T089). It never caches responses, never retries on its own and never
// writes the token anywhere: the token is read from the session for each request and sent only as a header.
export const apiBaseUrl = (import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080').replace(/\/$/, '');

/** Typed failure built from common.ErrorResponse: code, safe message, correlationId, fieldErrors. */
export class ApiError extends Error {
  constructor({ status, code, message, correlationId = null, fieldErrors = [], method = 'GET' }) {
    super(message || code);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
    this.correlationId = correlationId;
    this.fieldErrors = fieldErrors;
    this.method = method;
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
  const url = new URL(`${baseUrl}${path}`);
  Object.entries(query || {}).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') url.searchParams.set(key, String(value));
  });
  return url.toString();
}

async function errorFrom(response, method) {
  const payload = await response.json().catch(() => ({}));
  return new ApiError({
    status: response.status,
    code: payload.code || `HTTP_${response.status}`,
    message: payload.message || '',
    correlationId: payload.correlationId || response.headers.get('X-Correlation-Id'),
    fieldErrors: Array.isArray(payload.fieldErrors) ? payload.fieldErrors : [],
    method,
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
 * @param onSessionExpired called once a refreshed token is still refused (401): the app signs out
 */
export function createHttpClient({ baseUrl = apiBaseUrl, getIdToken, onSessionExpired, fetchImpl } = {}) {
  const doFetch = (...args) => (fetchImpl || globalThis.fetch)(...args);

  async function send(url, { method, headers, body, signal }, token) {
    const allHeaders = { Accept: 'application/json', ...headers };
    if (token) allHeaders.Authorization = `Bearer ${token}`;
    try {
      return await doFetch(url, { method, headers: allHeaders, body, signal });
    } catch (error) {
      if (error?.name === 'AbortError') throw error;
      throw new ApiError({ status: 0, code: 'NETWORK_ERROR', message: '', method });
    }
  }

  return async function request(path, {
    method = 'GET', query, body, form, idempotencyKey, auth = true, responseType = 'json', signal,
  } = {}) {
    const url = buildUrl(baseUrl, path, query);
    const headers = {};
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
      const refreshed = await getIdToken(true);
      if (refreshed) response = await send(url, init, refreshed);
      if (response.status === 401) {
        const error = await errorFrom(response, method);
        onSessionExpired?.();
        throw error;
      }
    }
    if (!response.ok) throw await errorFrom(response, method);
    return readBody(response, responseType);
  };
}
