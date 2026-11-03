import React from 'react';
import { render } from '@testing-library/react';
import { createMemoryRouter, RouterProvider } from 'react-router';
import { vi } from 'vitest';
import { AuthProvider } from '../shared/auth/AuthProvider';
import { I18nProvider } from '../shared/i18n/I18nProvider';

/** Session stand-in: no Firebase in tests; the token is a fixed test value. */
export function fakeSession(status = 'signed-in') {
  return {
    watch: listener => { listener({ status }); return () => {}; },
    getIdToken: vi.fn(async () => (status === 'signed-in' ? 'test-token' : null)),
    signOut: vi.fn(async () => {}),
  };
}

export function json(status, body, headers = {}) {
  return new Response(body === undefined ? null : JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json', ...headers } });
}

/** Routes fetch calls by "METHOD path" (path without origin/query) to handlers returning a Response. */
export function mockFetch(handlers) {
  const calls = [];
  const fetchMock = vi.fn(async (url, init = {}) => {
    const parsed = new URL(url);
    const method = init.method || 'GET';
    const call = { method, path: parsed.pathname, query: Object.fromEntries(parsed.searchParams), headers: init.headers || {}, body: init.body ? safeJson(init.body) : undefined };
    calls.push(call);
    const handler = handlers[`${method} ${parsed.pathname}`];
    if (!handler) return json(404, { code: 'NOT_MOCKED', message: `${method} ${parsed.pathname}` });
    return typeof handler === 'function' ? handler(call) : handler.clone();
  });
  vi.stubGlobal('fetch', fetchMock);
  return calls;
}

function safeJson(body) {
  if (typeof body !== 'string') return body;
  try { return JSON.parse(body); } catch { return body; }
}

export function renderRoutes(routes, { path, session = fakeSession(), language = 'vi' } = {}) {
  const router = createMemoryRouter(routes, { initialEntries: [path] });
  const utils = render(
    <I18nProvider initialLanguage={language}>
      <AuthProvider source={session}>
        <RouterProvider router={router} />
      </AuthProvider>
    </I18nProvider>,
  );
  return { ...utils, router, session };
}
