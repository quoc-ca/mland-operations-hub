import { describe, expect, it, vi } from 'vitest';
import { ApiError, createHttpClient } from './http';

const response = (status, body, headers = {}) => new Response(body === undefined ? null : JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json', ...headers } });

function client({ responses, token = 'token-1', refreshed = 'token-2' }) {
  const fetchImpl = vi.fn();
  responses.forEach(item => fetchImpl.mockImplementationOnce(async () => (item instanceof Error ? Promise.reject(item) : item)));
  const getIdToken = vi.fn(async force => (force ? refreshed : token));
  const onSessionExpired = vi.fn();
  const request = createHttpClient({ baseUrl: 'http://api.test', getIdToken, fetchImpl, onSessionExpired });
  return { request, fetchImpl, getIdToken, onSessionExpired };
}

describe('shared HTTP client (002 T089)', () => {
  it('sends the bearer token, the Idempotency-Key and a JSON body', async () => {
    const { request, fetchImpl } = client({ responses: [response(200, { ok: true })] });
    await request('/api/v1/x', { method: 'PUT', idempotencyKey: 'key-1', body: { expectedVersion: 3 } });
    const [url, init] = fetchImpl.mock.calls[0];
    expect(url).toBe('http://api.test/api/v1/x');
    expect(init.headers.Authorization).toBe('Bearer token-1');
    expect(init.headers['Idempotency-Key']).toBe('key-1');
    expect(init.headers['Content-Type']).toBe('application/json');
    expect(JSON.parse(init.body)).toEqual({ expectedVersion: 3 });
  });

  it('never puts the token in the URL and skips empty query values', async () => {
    const { request, fetchImpl } = client({ responses: [response(200, {})] });
    await request('/api/v1/x', { query: { q: 'nhẫn', categoryId: '', page: undefined, published: false } });
    const [url] = fetchImpl.mock.calls[0];
    expect(url).toBe('http://api.test/api/v1/x?q=nh%E1%BA%ABn&published=false');
    expect(url).not.toContain('token');
  });

  it('public reads send no Authorization header', async () => {
    const { request, fetchImpl, getIdToken } = client({ responses: [response(200, {})] });
    await request('/api/v1/products', { auth: false });
    expect(fetchImpl.mock.calls[0][1].headers.Authorization).toBeUndefined();
    expect(getIdToken).not.toHaveBeenCalled();
  });

  it('without a session it fails with AUTHENTICATION_REQUIRED and sends nothing', async () => {
    const { request, fetchImpl } = client({ responses: [], token: null });
    await expect(request('/api/v1/x')).rejects.toMatchObject({ status: 401, code: 'AUTHENTICATION_REQUIRED' });
    expect(fetchImpl).not.toHaveBeenCalled();
  });

  it('on 401 refreshes the token once and resends', async () => {
    const { request, fetchImpl, onSessionExpired } = client({ responses: [response(401, { code: 'AUTHENTICATION_REQUIRED' }), response(200, { done: 1 })] });
    await expect(request('/api/v1/x', { method: 'POST', idempotencyKey: 'k' })).resolves.toEqual({ done: 1 });
    expect(fetchImpl).toHaveBeenCalledTimes(2);
    expect(fetchImpl.mock.calls[1][1].headers.Authorization).toBe('Bearer token-2');
    expect(fetchImpl.mock.calls[1][1].headers['Idempotency-Key']).toBe('k');
    expect(onSessionExpired).not.toHaveBeenCalled();
  });

  it('a second 401 ends the session and reports the typed error', async () => {
    const { request, onSessionExpired } = client({ responses: [response(401, { code: 'INVALID_CREDENTIAL' }), response(401, { code: 'INVALID_CREDENTIAL', correlationId: 'c-1' })] });
    await expect(request('/api/v1/x')).rejects.toMatchObject({ status: 401, code: 'INVALID_CREDENTIAL', correlationId: 'c-1' });
    expect(onSessionExpired).toHaveBeenCalledTimes(1);
  });

  it.each([
    [400, 'VALIDATION_ERROR'], [403, 'CATALOGUE_ACCESS_DENIED'], [404, 'PRODUCT_NOT_FOUND'], [409, 'STALE_VERSION'],
    [409, 'QUANTITY_BELOW_HELD'], [413, 'MEDIA_TOO_LARGE'], [415, 'MEDIA_TYPE_UNSUPPORTED'], [503, 'CATALOGUE_BUSY'],
  ])('parses %s %s from common.ErrorResponse', async (status, code) => {
    const body = { code, message: 'Safe text.', correlationId: 'corr-9', timestamp: '2026-11-01T00:00:00Z', fieldErrors: [{ field: 'name', message: 'Required.' }] };
    const { request } = client({ responses: [response(status, body)] });
    const error = await request('/api/v1/x').catch(caught => caught);
    expect(error).toBeInstanceOf(ApiError);
    expect(error).toMatchObject({ status, code, message: 'Safe text.', correlationId: 'corr-9' });
    expect(error.fieldError('name')).toBe('Required.');
    expect(error.retryable).toBe(status === 503);
    expect(error.stale).toBe(status === 409);
  });

  it('keeps the correlation id header when the body is not JSON', async () => {
    const { request } = client({ responses: [new Response('busy', { status: 503, headers: { 'X-Correlation-Id': 'hdr-1' } })] });
    await expect(request('/api/v1/x')).rejects.toMatchObject({ status: 503, code: 'HTTP_503', correlationId: 'hdr-1' });
  });

  it('a lost response becomes a retryable NETWORK_ERROR and is not retried by the client', async () => {
    const { request, fetchImpl } = client({ responses: [new TypeError('Failed to fetch')] });
    const error = await request('/api/v1/x', { method: 'POST', idempotencyKey: 'k' }).catch(caught => caught);
    expect(error).toMatchObject({ status: 0, code: 'NETWORK_ERROR', retryable: true });
    expect(fetchImpl).toHaveBeenCalledTimes(1);
  });

  it('downloads a file with its name', async () => {
    const file = new Response('xlsx-bytes', { status: 200, headers: { 'Content-Disposition': "attachment; filename*=UTF-8''mau-import-san-pham.xlsx" } });
    const { request } = client({ responses: [file] });
    const result = await request('/api/v1/t', { responseType: 'blob' });
    expect(result.fileName).toBe('mau-import-san-pham.xlsx');
    expect(result.blob.size).toBe(10);
  });
});
