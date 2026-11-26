import React from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import { fakeSession, json, mockFetch, renderRoutes } from '../../test/render';
import { operationsRoutes } from '../routes';
import { settlementRoutes } from '../../workshop-settlement/routes';
import { designReviewRoutes } from '../../features/designreview/routes';
import { reviewManagementRoutes } from '../../features/reviewmanagement/routes';
import { consultationRoutes } from '../../features/consultationchat/routes';
import { readyRingSalesRoutes } from '../../features/retail-order/routes/routes';
import { loyaltyPromotionRoutes } from '../../features/loyalty-promotion/routes/loyaltyPromotionRoutes';
import { ringDesignConfigurationRoutes } from '../../features/ringdesignconfiguration/routes';
import { clearRoleCache } from '../../shared/auth/useRole';
import { signInAs } from '../../features/loyalty-promotion/tests/fixtures';

describe('Staff/Manager pages without a session', () => {
  it('offers a sign-in link that returns to the session page', async () => {
    const calls = mockFetch({});
    renderRoutes(operationsRoutes, { language: 'en', path: '/operations/sessions/abc-1', session: fakeSession('signed-out') });

    const link = await screen.findByRole('link', { name: 'Sign in' });
    expect(link).toHaveAttribute('href', '/login?returnTo=%2Foperations%2Fsessions%2Fabc-1');
    expect(screen.getByText(/Staff or Manager/)).toBeInTheDocument();
    expect(calls).toHaveLength(0);
  });

  it('does the same for the settlement workspace', async () => {
    mockFetch({});
    renderRoutes(settlementRoutes, { language: 'en', path: '/settlement', session: fakeSession('signed-out') });

    expect(await screen.findByRole('link', { name: 'Sign in' })).toHaveAttribute('href', '/login?returnTo=%2Fsettlement');
  });
});

describe('Staff/Manager pages for a signed-in actor (003.2 and 003.3 browser allowlists)', () => {
  const notFound = { path: '/404', element: <p>not found page</p> };

  afterEach(() => vi.restoreAllMocks());

  it.each([
    ['ADMIN', operationsRoutes, '/operations/sessions'],
    ['ADMIN', settlementRoutes, '/settlement/ticket-actions'],
    ['ADMIN', settlementRoutes, '/settlement/change-requests'],
    ['MEMBER', settlementRoutes, '/settlement'],
    ['CARRIER', operationsRoutes, '/operations/packages'],
    ['STAFF', operationsRoutes, '/operations/audit'],
    ['STAFF', operationsRoutes, '/operations/late-payments'],
    ['ADMIN', designReviewRoutes, '/design-review-requests'],
    ['ADMIN', designReviewRoutes, '/staff/custom-orders'],
    ['ADMIN', reviewManagementRoutes, '/review-moderation'],
    ['ADMIN', consultationRoutes, '/operations/consultations'],
    ['ADMIN', readyRingSalesRoutes, '/staff/retail-orders'],
    ['ADMIN', readyRingSalesRoutes, '/manager/retail-payment-reconciliations'],
  ])('replaces %s to /404 without calling the page API (%#)', async (role, routes, path) => {
    signInAs(role);
    const calls = mockFetch({});
    renderRoutes([...routes, notFound], { language: 'en', path, session: fakeSession() });

    expect(await screen.findByText('not found page')).toBeInTheDocument();
    expect(calls).toHaveLength(0);
  });

  it.each([
    ['STAFF', operationsRoutes, '/operations/sessions'],
    ['STAFF', settlementRoutes, '/settlement/ticket-actions'],
    ['MANAGER', operationsRoutes, '/operations/audit'],
  ])('lets %s open the page (%#)', async (role, routes, path) => {
    signInAs(role);
    mockFetch({});
    const { router } = renderRoutes([...routes, notFound], { language: 'en', path, session: fakeSession() });

    await waitFor(() => expect(document.body.textContent).not.toBe(''));
    expect(router.state.location.pathname).toBe(path);
    expect(screen.queryByText('not found page')).toBeNull();
  });
});

describe('Member routes are masked from Admin', () => {
  const notFound = { path: '/404', element: <p>not found page</p> };

  afterEach(() => { clearRoleCache(); vi.restoreAllMocks(); });

  it.each([
    [readyRingSalesRoutes, '/member/orders'],
    [loyaltyPromotionRoutes, '/me/loyalty'],
    [ringDesignConfigurationRoutes, '/my/designs'],
    [designReviewRoutes, '/account/my-custom-orders'],
    [consultationRoutes, '/member/consultations'],
  ])('replaces ADMIN to /404 (%#)', async (routes, path) => {
    // MemberOnly (006 /me/*) reads the role from the 001 Account bootstrap; MaskAdmin reads it from the profile.
    signInAs('ADMIN');
    mockFetch({ 'GET /api/v1/accounts/me/profile': json(200, { role: 'ADMIN' }) });
    renderRoutes([...routes, notFound], { language: 'en', path, session: fakeSession() });

    expect(await screen.findByText('not found page')).toBeInTheDocument();
  });
});
