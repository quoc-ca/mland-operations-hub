import { describe, it, expect, vi, beforeEach } from 'vitest';
import React from 'react';
import { screen, waitFor, fireEvent, cleanup } from '@testing-library/react';
import { renderRoutes, json, mockFetch } from '../../../test/render';
import { routes } from '../../../app/router';
import * as useCurrentAccountModule from '../hooks/useCurrentAccount';

describe('Revision R-404 / FR-046 / T074 & T076: RouteAccessGuard & Browser Route Masking (Scenario V-15)', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    mockFetch({
      'GET /api/v1/workshops': json(200, []),
      'GET /api/v1/products': json(200, []),
      'GET /api/v1/accounts/me/profile': json(200, { fullName: 'Test User', email: 'user@mland.vn' }),
      'GET /api/v1/accounts/me/addresses': json(200, []),
      'GET /api/v1/accounts/me/addresses/default': json(200, null),
      'GET /api/v1/admin/accounts': json(200, { items: [], total: 0 }),
      'GET /api/v1/roles': json(200, []),
    });
  });

  function mockAccountSession(role: string, status = 'ACTIVE', authStatus: any = 'authenticated') {
    return {
      account: {
        accountId: 10,
        role: role as any,
        status: status as any,
        loginEmail: `${role.toLowerCase()}@mland.vn`,
        revocationStatus: 'NOT_APPLICABLE',
        createdAt: '2026-11-01',
        updatedAt: '2026-11-20',
      },
      status: authStatus,
      error: null,
      reloadAccount: vi.fn(),
      signOut: vi.fn(),
      retry: vi.fn(),
    };
  }

  function mockControlledAuth(authStatus = 'signed-in') {
    return {
      get status() {
        return authStatus;
      },
      watch: vi.fn(listener => {
        listener({ status: authStatus });
        return () => {};
      }),
      getIdToken: vi.fn(async () => (authStatus === 'signed-in' ? 'test-bearer-token' : null)),
      signOut: vi.fn(),
      emitStatus: vi.fn(),
    };
  }

  describe('1. FE-001 Self-service allowlist for MEMBER, STAFF, MANAGER, ADMIN (V-15.1)', () => {
    const internalRoles = ['MEMBER', 'STAFF', 'MANAGER', 'ADMIN'];

    for (const role of internalRoles) {
      it(`allows active ${role} to access Profile, Address Book, Email, and Password routes`, async () => {
        vi.spyOn(useCurrentAccountModule, 'useCurrentAccount').mockReturnValue(mockAccountSession(role));
        const session = mockControlledAuth('signed-in');

        const selfServicePaths = [
          '/profile',
          '/profile/addresses',
          '/security/email',
          '/security/password',
          '/personal-profile',
          '/member/profile',
          '/me/profile',
          '/member/addresses',
          '/addresses',
          '/change-password',
        ];

        for (const path of selfServicePaths) {
          cleanup();
          const { router } = renderRoutes(routes, { path, session });
          expect(router.state.location.pathname).toBe(path);
          expect(screen.queryByText('404 Not Found')).not.toBeInTheDocument();
          expect(screen.queryByText('Trang không tồn tại')).not.toBeInTheDocument();
        }
      });
    }
  });

  describe('2. CARRIER boundary: password-only self-service & portal isolation (V-15.2)', () => {
    it('allows CARRIER to access /security/password and /carrier', async () => {
      vi.spyOn(useCurrentAccountModule, 'useCurrentAccount').mockReturnValue(mockAccountSession('CARRIER'));
      const session = mockControlledAuth('signed-in');

      const allowedPaths = ['/security/password', '/change-password', '/carrier'];
      for (const path of allowedPaths) {
        cleanup();
        const { router } = renderRoutes(routes, { path, session });
        expect(router.state.location.pathname).toBe(path);
        expect(screen.queryByText('Trang không tồn tại')).not.toBeInTheDocument();
      }
    });

    it('masks and replaces to /404 when CARRIER attempts to open Profile, Address or Email routes (V-15.2)', async () => {
      vi.spyOn(useCurrentAccountModule, 'useCurrentAccount').mockReturnValue(mockAccountSession('CARRIER'));
      const session = mockControlledAuth('signed-in');

      const maskedPaths = [
        '/profile',
        '/personal-profile',
        '/member/personal-profile',
        '/member/profile',
        '/me/profile',
        '/profile/addresses',
        '/member/addresses',
        '/addresses',
        '/edit-profile',
        '/member/edit-profile',
        '/security/email',
      ];

      for (const path of maskedPaths) {
        cleanup();
        const { router } = renderRoutes(routes, { path, session });
        expect(router.state.location.pathname).toBe('/404');
        expect(screen.getByText('Trang không tồn tại')).toBeInTheDocument();
        expect(screen.getByText('404 Not Found')).toBeInTheDocument();
        // Verifies no protected profile DOM is leaked
        expect(screen.queryByText('Thông tin tài khoản')).not.toBeInTheDocument();
        expect(screen.queryByText('Sổ địa chỉ')).not.toBeInTheDocument();
      }
    });

    it('masks and replaces to /404 when CARRIER attempts to open Admin routes', async () => {
      vi.spyOn(useCurrentAccountModule, 'useCurrentAccount').mockReturnValue(mockAccountSession('CARRIER'));
      const session = mockControlledAuth('signed-in');

      const { router } = renderRoutes(routes, { path: '/admin/accounts', session });
      expect(router.state.location.pathname).toBe('/404');
      expect(screen.getByText('Trang không tồn tại')).toBeInTheDocument();
      expect(screen.queryByText('Quản lý tài khoản')).not.toBeInTheDocument();
    });
  });

  describe('3. Admin route allowlist & masking (V-15.3)', () => {
    it('allows active ADMIN to access /admin/accounts and /admin/roles', async () => {
      vi.spyOn(useCurrentAccountModule, 'useCurrentAccount').mockReturnValue(mockAccountSession('ADMIN'));
      const session = mockControlledAuth('signed-in');

      const adminPaths = ['/admin/accounts', '/admin/roles'];
      for (const path of adminPaths) {
        cleanup();
        const { router } = renderRoutes(routes, { path, session });
        expect(router.state.location.pathname).toBe(path);
        expect(screen.queryByText('Trang không tồn tại')).not.toBeInTheDocument();
      }
    });

    const nonAdminRoles = ['MEMBER', 'STAFF', 'MANAGER', 'CARRIER'];
    for (const role of nonAdminRoles) {
      it(`masks and replaces to /404 when ${role} attempts to access /admin/accounts or /admin/roles`, async () => {
        vi.spyOn(useCurrentAccountModule, 'useCurrentAccount').mockReturnValue(mockAccountSession(role));
        const session = mockControlledAuth('signed-in');

        const { router } = renderRoutes(routes, { path: '/admin/accounts', session });
        expect(router.state.location.pathname).toBe('/404');
        expect(screen.getByText('Trang không tồn tại')).toBeInTheDocument();
        expect(screen.queryByText('Quản lý tài khoản')).not.toBeInTheDocument();
      });
    }
  });

  describe('4. Carrier portal allowlist & masking (V-15)', () => {
    const nonCarrierRoles = ['MEMBER', 'STAFF', 'MANAGER', 'ADMIN'];
    for (const role of nonCarrierRoles) {
      it(`masks and replaces to /404 when ${role} attempts to access /carrier`, async () => {
        vi.spyOn(useCurrentAccountModule, 'useCurrentAccount').mockReturnValue(mockAccountSession(role));
        const session = mockControlledAuth('signed-in');

        const { router } = renderRoutes(routes, { path: '/carrier', session });
        expect(router.state.location.pathname).toBe('/404');
        expect(screen.getByText('Trang không tồn tại')).toBeInTheDocument();
        expect(screen.queryByText('Cổng thông tin đối tác giao hàng')).not.toBeInTheDocument();
      });
    }
  });

  describe('5. Inactive Account status masking (SUSPENDED / PENDING_SETUP) (V-15.4)', () => {
    const inactiveStatuses = ['SUSPENDED', 'PENDING_SETUP'];

    for (const st of inactiveStatuses) {
      it(`masks and replaces to /404 when ${st} account accesses internal routes`, async () => {
        vi.spyOn(useCurrentAccountModule, 'useCurrentAccount').mockReturnValue(mockAccountSession('MEMBER', st));
        const session = mockControlledAuth('signed-in');

        const targetPaths = ['/profile', '/security/password'];
        for (const path of targetPaths) {
          cleanup();
          const { router } = renderRoutes(routes, { path, session });
          expect(router.state.location.pathname).toBe('/404');
          expect(screen.getByText('Trang không tồn tại')).toBeInTheDocument();
        }
      });
    }
  });

  describe('6. Account-bootstrap technical unavailability with retry (V-15.5)', () => {
    it('keeps original URL, shows non-PII unavailable state and explicit retry without routing to /404', async () => {
      const retryMock = vi.fn();
      vi.spyOn(useCurrentAccountModule, 'useCurrentAccount').mockReturnValue({
        account: null,
        status: 'error',
        error: new Error('Backend service unavailable (503)'),
        reloadAccount: vi.fn(),
        signOut: vi.fn(),
        retry: retryMock,
      });
      const session = mockControlledAuth('signed-in');

      const { router } = renderRoutes(routes, { path: '/profile', session });

      // Verifies original URL is preserved
      expect(router.state.location.pathname).toBe('/profile');

      // Verifies NOT routed to /404
      expect(screen.queryByText('Trang không tồn tại')).not.toBeInTheDocument();

      // Verifies safe unavailable state
      expect(screen.getByText(/Không thể kết nối hoặc xác thực thông tin tài khoản/i)).toBeInTheDocument();

      // Verifies explicit retry button
      const retryButton = screen.getByRole('button', { name: /Thử lại/i });
      expect(retryButton).toBeInTheDocument();
      fireEvent.click(retryButton);
      expect(retryMock).toHaveBeenCalledTimes(1);
    });
  });

  describe('7. Generic /404 NotFoundPage without disclosure (FR-046 / T074)', () => {
    it('renders generic 404 with only Home link and no role/status/URL disclosure', async () => {
      const session = mockControlledAuth('signed-in');
      const { router } = renderRoutes(routes, { path: '/404', session });

      expect(router.state.location.pathname).toBe('/404');
      expect(screen.getByText('404 Not Found')).toBeInTheDocument();
      expect(screen.getByRole('heading', { name: 'Trang không tồn tại' })).toBeInTheDocument();
      expect(screen.getByText(/Địa chỉ bạn yêu cầu không tồn tại hoặc đã được chuyển hướng/i)).toBeInTheDocument();

      // Verifies single Home link only
      const homeLink = screen.getByRole('link', { name: 'Về trang chủ' });
      expect(homeLink).toBeInTheDocument();
      expect(homeLink).toHaveAttribute('href', '/');

      // Verifies no role, scope, or forbidden disclosure
      expect(screen.queryByText(/403|Forbidden|role|scope|admin|carrier/i)).not.toBeInTheDocument();
    });
  });

  describe('8. Guest access to guarded routes (FR-046 / T074)', () => {
    it('redirects unauthenticated guest to /login with replace', async () => {
      const session = mockControlledAuth('signed-out');
      vi.spyOn(useCurrentAccountModule, 'useCurrentAccount').mockReturnValue({
        account: null,
        status: 'unauthenticated',
        error: null,
        reloadAccount: vi.fn(),
        signOut: vi.fn(),
        retry: vi.fn(),
      });

      const { router } = renderRoutes(routes, { path: '/profile', session });
      await waitFor(() => {
        expect(router.state.location.pathname).toBe('/login');
      });
    });
  });
});
