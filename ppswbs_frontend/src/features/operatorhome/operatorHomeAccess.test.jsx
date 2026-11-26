import React from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { screen } from '@testing-library/react';
import { fakeSession, mockFetch, renderRoutes } from '../../test/render';
import { signInAs } from '../loyalty-promotion/tests/fixtures';
import { operatorHomeRoutes } from './routes';

const routes = [...operatorHomeRoutes, { path: '/404', element: <p>not found page</p> }];

describe('/staff/home allowlist (014)', () => {
  afterEach(() => vi.restoreAllMocks());

  it.each(['MEMBER', 'MANAGER', 'ADMIN', 'CARRIER'])('replaces %s to /404 before any request', async role => {
    signInAs(role);
    const calls = mockFetch({});
    renderRoutes(routes, { path: '/staff/home', session: fakeSession() });

    expect(await screen.findByText('not found page')).toBeInTheDocument();
    expect(calls).toHaveLength(0);
  });
});
