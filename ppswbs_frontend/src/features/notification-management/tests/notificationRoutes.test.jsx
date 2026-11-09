import React from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import { fakeSession, json, mockFetch, renderRoutes } from '../../../test/render';
import { notificationRoutes } from '../routes/notificationRoutes';
import { clearRoleCache } from '../../../shared/auth/useRole';

const profile = (role) => json(200, { role, fullName: 'Test User' });

describe('Notification Management Routes and Guard', () => {
  beforeEach(() => {
    clearRoleCache();
  });

  it('denies access to STAFF role', async () => {
    mockFetch({
      'GET /api/v1/accounts/me/profile': profile('STAFF'),
    });

    renderRoutes(notificationRoutes, {
      path: '/admin/notifications/templates',
      session: fakeSession('signed-in'),
    });

    expect(
      await screen.findByText(/Yêu cầu quyền Quản trị viên \(Admin\) hoặc Quản lý \(Manager\)/i)
    ).toBeInTheDocument();
  });

  it('denies access to MEMBER role', async () => {
    mockFetch({
      'GET /api/v1/accounts/me/profile': profile('MEMBER'),
    });

    renderRoutes(notificationRoutes, {
      path: '/admin/notifications/templates',
      session: fakeSession('signed-in'),
    });

    expect(
      await screen.findByText(/Yêu cầu quyền Quản trị viên \(Admin\) hoặc Quản lý \(Manager\)/i)
    ).toBeInTheDocument();
  });

  it('allows access to MANAGER role', async () => {
    mockFetch({
      'GET /api/v1/accounts/me/profile': profile('MANAGER'),
      'GET /api/v1/notification-templates': json(200, []),
    });

    renderRoutes(notificationRoutes, {
      path: '/admin/notifications/templates',
      session: fakeSession('signed-in'),
    });

    expect(
      await screen.findByRole('heading', { name: 'Quản lý mẫu thông báo email' })
    ).toBeInTheDocument();
  });

  it('allows access to ADMIN role', async () => {
    mockFetch({
      'GET /api/v1/accounts/me/profile': profile('ADMIN'),
      'GET /api/v1/notification-requests': json(200, { content: [], totalElements: 0, totalPages: 0 }),
    });

    renderRoutes(notificationRoutes, {
      path: '/admin/notifications/requests',
      session: fakeSession('signed-in'),
    });

    expect(
      await screen.findByRole('heading', { name: 'Nhật ký gửi thông báo (Delivery Logs)' })
    ).toBeInTheDocument();
  });

  it('redirects unauthenticated guest accessing notification route to /login?redirect=...', async () => {
    const { router } = renderRoutes(notificationRoutes, {
      path: '/admin/notifications/requests',
      session: fakeSession('signed-out'),
    });

    await waitFor(() => {
      expect(router.state.location.pathname).toBe('/login');
      expect(router.state.location.search).toBe('?redirect=%2Fadmin%2Fnotifications%2Frequests');
    });
  });
});
