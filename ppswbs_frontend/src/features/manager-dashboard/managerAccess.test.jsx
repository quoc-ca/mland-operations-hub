import React from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { screen } from '@testing-library/react';
import { fakeSession, mockFetch, renderRoutes } from '../../test/render';
import { signInAs } from '../loyalty-promotion/tests/fixtures';
import { managerHomeRoutes } from './operations/routes';
import { managerDashboardRoutes } from './routes';

const routes = [...managerHomeRoutes, ...managerDashboardRoutes, { path: '/404', element: <p>not found page</p> }];

describe('Manager-only routes (010 and 014 allowlists)', () => {
  afterEach(() => vi.restoreAllMocks());

  it.each(['MEMBER', 'STAFF', 'ADMIN', 'CARRIER'].flatMap(role => [[role, '/manager/dashboard'], [role, '/manager/home']]))(
    'replaces %s to /404 from %s before any request',
    async (role, path) => {
      signInAs(role);
      const calls = mockFetch({});
      renderRoutes(routes, { path, session: fakeSession() });

      expect(await screen.findByText('not found page')).toBeInTheDocument();
      expect(calls).toHaveLength(0);
    },
  );

  it.each(['/manager/dashboard', '/manager/home'])('asks a guest to sign in on %s and loads nothing', async path => {
    const calls = mockFetch({});
    renderRoutes(routes, { path, session: fakeSession('signed-out') });

    expect(await screen.findByText(/Đăng nhập bằng tài khoản Manager/)).toBeInTheDocument();
    expect(calls).toHaveLength(0);
  });
});
