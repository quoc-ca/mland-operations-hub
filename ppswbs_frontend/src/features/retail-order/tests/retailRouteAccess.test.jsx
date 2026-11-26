import React from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import * as currentAccount from '../../accountmanagement/hooks/useCurrentAccount';
import { fakeSession, mockFetch, renderRoutes } from '../../../test/render';
import { readyRingSalesRoutes } from '../routes/routes';

// FE-08 browser allowlists with the real RouteAccessGuard (spec 008 route allowlist, route-access-matrix.md, T090).
// The page tests use src/test/passThroughGuard.jsx; who may open the page is tested here.
const notFound = { path: '/404', element: <p>not found page</p> };

function signInAs(role, status = 'ACTIVE') {
  vi.spyOn(currentAccount, 'useCurrentAccount').mockReturnValue({
    account: { accountId: 10, role, status },
    status: 'authenticated', error: null, reloadAccount: vi.fn(), signOut: vi.fn(), retry: vi.fn(),
  });
}

afterEach(() => vi.restoreAllMocks());

describe('Retail routes for a signed-in actor', () => {
  it.each([
    ['STAFF', '/carrier/orders'],
    ['MANAGER', '/carrier/orders'],
    ['MEMBER', '/carrier/orders'],
    ['ADMIN', '/carrier/orders'],
    ['MEMBER', '/carrier/stats'],
    ['STAFF', '/carrier/stats'],
    ['CARRIER', '/staff/retail-orders'],
    ['MEMBER', '/staff/retail-orders'],
    ['CARRIER', '/manager/retail-payment-reconciliations'],
    ['STAFF', '/manager/retail-payment-reconciliations'],
  ])('replaces %s on %s to /404 without calling the page API', async (role, path) => {
    signInAs(role);
    const calls = mockFetch({});
    renderRoutes([...readyRingSalesRoutes, notFound], { language: 'en', path, session: fakeSession() });

    expect(await screen.findByText('not found page')).toBeInTheDocument();
    expect(calls).toHaveLength(0);
  });

  it('replaces a suspended GHTK account to /404', async () => {
    signInAs('CARRIER', 'SUSPENDED');
    const calls = mockFetch({});
    renderRoutes([...readyRingSalesRoutes, notFound], { language: 'en', path: '/carrier/orders', session: fakeSession() });

    expect(await screen.findByText('not found page')).toBeInTheDocument();
    expect(calls).toHaveLength(0);
  });

  it.each([
    ['CARRIER', '/carrier/orders'],
    ['CARRIER', '/carrier/stats'],
    ['STAFF', '/staff/retail-orders'],
    // C2 (Thinh, 2026-11-26): a Manager opens the branch retail work; the counter buttons stay Staff only.
    ['MANAGER', '/staff/retail-orders'],
    ['MANAGER', '/staff/retail-pickups'],
  ])('lets %s open %s', async (role, path) => {
    signInAs(role);
    const calls = mockFetch({});
    const { router } = renderRoutes([...readyRingSalesRoutes, notFound], { language: 'en', path, session: fakeSession() });

    await waitFor(() => expect(calls.length).toBeGreaterThan(0));
    expect(router.state.location.pathname).toBe(path);
    expect(screen.queryByText('not found page')).toBeNull();
  });
});
