import React from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { screen } from '@testing-library/react';
import { fakeSession, mockFetch, renderRoutes } from '../../test/render';
import { signInAs } from '../../features/loyalty-promotion/tests/fixtures';
import * as currentAccount from '../../features/accountmanagement/hooks/useCurrentAccount';
import { MemberOnly } from './RoleArea';

function Page() {
  return <p>member page</p>;
}

const routes = [
  { path: '/member-page', element: <MemberOnly><Page /></MemberOnly> },
  { path: '/404', element: <p>not found page</p> },
];

describe('MemberOnly', () => {
  afterEach(() => vi.restoreAllMocks());

  it.each(['STAFF', 'MANAGER', 'ADMIN', 'CARRIER'])('replaces %s to /404', async role => {
    signInAs(role);
    mockFetch({});
    renderRoutes(routes, { path: '/member-page', session: fakeSession() });

    expect(await screen.findByText('not found page')).toBeInTheDocument();
    expect(screen.queryByText('member page')).toBeNull();
  });

  it('opens the page for a Member', async () => {
    signInAs('MEMBER');
    mockFetch({});
    renderRoutes(routes, { path: '/member-page', session: fakeSession() });

    expect(await screen.findByText('member page')).toBeInTheDocument();
  });

  it('does not mount the page while the role is unknown', () => {
    vi.spyOn(currentAccount, 'useCurrentAccount').mockReturnValue({ account: null, status: 'loading' });
    mockFetch({});
    renderRoutes(routes, { path: '/member-page', session: fakeSession() });

    expect(screen.queryByText('member page')).toBeNull();
    expect(screen.queryByText('not found page')).toBeNull();
  });

  it('keeps the page for a guest without reading any account', async () => {
    const calls = mockFetch({});
    renderRoutes(routes, { path: '/member-page', session: fakeSession('signed-out') });

    expect(await screen.findByText('member page')).toBeInTheDocument();
    expect(calls).toHaveLength(0);
  });
});
