import { describe, it, expect, vi, beforeEach } from 'vitest';
import React from 'react';
import { screen, fireEvent, waitFor } from '@testing-library/react';
import { renderRoutes, json, mockFetch } from '../../../test/render';
import { routes } from '../../../app/router';
import { registerPrivateStateCleanup } from '../../../shared/auth/privateStateCleanup';

function createControlledSession(initialStatus = 'signed-in') {
  let currentListener: ((state: { status: string }) => void) | null = null;
  let currentStatus = initialStatus;

  return {
    get status() {
      return currentStatus;
    },
    watch: vi.fn(listener => {
      currentListener = listener;
      listener({ status: currentStatus });
      return () => {
        currentListener = null;
      };
    }),
    getIdToken: vi.fn(async () => (currentStatus === 'signed-in' ? 'test-bearer-token' : null)),
    signOut: vi.fn(async () => {
      currentStatus = 'signed-out';
      currentListener?.({ status: 'signed-out' });
    }),
    emitStatus(nextStatus: string) {
      currentStatus = nextStatus;
      currentListener?.({ status: nextStatus });
    },
  };
}

describe('Revision R-SL: Shared Browser Session Lifecycle & Router Boundary (FR-041 / V-13)', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    mockFetch({
      'GET /api/v1/workshops': json(200, []),
      'GET /api/v1/products': json(200, []),
      'GET /api/v1/accounts/me/profile': json(200, { fullName: 'Test Member', email: 'member@mland.vn' }),
      'GET /api/v1/members/me/loyalty-accounts/current': json(200, { balancePoints: 100 }),
      'GET /api/v1/members/me/ready-ring-orders': json(200, []),
      'GET /api/v1/accounts/me/addresses': json(200, []),
      'GET /api/v1/accounts/me/addresses/default': json(200, null),
      'GET /api/v1/accounts/me': json(200, { id: '1', role: 'MEMBER', status: 'ACTIVE', email: 'member@mland.vn', locations: [] }),
    });
  });

  describe('1. Canonical Account URLs & Compatibility Aliases (T071, T072)', () => {
    const aliasRoutes = [
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
      '/change-password',
      '/member/change-password',
      '/security/password',
      '/security/email',
    ];

    it.each(aliasRoutes)(
      'redirects guest directly accessing %s to /login using replace without returnTo',
      async path => {
        const session = createControlledSession('signed-out');
        const { router } = renderRoutes(routes, { path, session });

        await waitFor(() => {
          expect(router.state.location.pathname).toBe('/login');
          // Verified no returnTo in search or state
          expect(router.state.location.search).toBe('');
          expect(router.state.location.state).toBeNull();
        });
      }
    );

    it('compatibility route /sign-in replaces to canonical /login without returnTo', async () => {
      const session = createControlledSession('signed-out');
      const { router } = renderRoutes(routes, { path: '/sign-in', session });

      await waitFor(() => {
        expect(router.state.location.pathname).toBe('/login');
        expect(router.state.location.search).toBe('');
      });
    });
  });

  describe('2. Representative Route Groups & Guest Access Boundary (T072)', () => {
    it('allows guest to access representative public routes without redirect', async () => {
      const session = createControlledSession('signed-out');
      const publicPaths = ['/workshops', '/products', '/ai-support', '/reviews', '/workshop-booking/lookup'];

      for (const path of publicPaths) {
        const { router } = renderRoutes(routes, { path, session });
        expect(router.state.location.pathname).toBe(path);
      }
    });

    it('redirects guest accessing Member-only /design-requests/new to /login with replace', async () => {
      const session = createControlledSession('signed-out');
      const { router } = renderRoutes(routes, { path: '/design-requests/new', session });

      await waitFor(() => {
        expect(router.state.location.pathname).toBe('/login');
      });
    });

    it('renders inline session notice for Member-only /member/cart when signed out', async () => {
      const session = createControlledSession('signed-out');
      renderRoutes(routes, { path: '/member/cart', session });

      expect(await screen.findByText('Đăng nhập để xem giỏ hàng của bạn.')).toBeInTheDocument();
    });

    it('redirects guest accessing internal admin route /admin/accounts to /login with replace', async () => {
      const session = createControlledSession('signed-out');
      const { router } = renderRoutes(routes, { path: '/admin/accounts', session });

      await waitFor(() => {
        expect(router.state.location.pathname).toBe('/login');
      });
    });
  });

  describe('3. Session State Handling (loading & unconfigured) (T071)', () => {
    it('renders neutral non-PII loading state while session is loading without redirect', () => {
      const session = createControlledSession('loading');
      const { router } = renderRoutes(routes, { path: '/profile', session });

      expect(router.state.location.pathname).toBe('/profile');
      expect(document.querySelector('.session-loading-state')).toBeInTheDocument();
      // Ensure no private profile content or PII is leaked
      expect(screen.queryByText(/member@mland.vn/i)).not.toBeInTheDocument();
      expect(screen.queryByText(/Hồ sơ cá nhân/i)).not.toBeInTheDocument();
    });

    it('renders safe unavailable notice without redirect when Firebase is unconfigured', () => {
      const session = createControlledSession('unconfigured');
      const { router } = renderRoutes(routes, { path: '/profile', session });

      expect(router.state.location.pathname).toBe('/profile');
      expect(screen.getByText(/Ứng dụng chưa được cấu hình Firebase/i)).toBeInTheDocument();
    });
  });

  describe('4. Explicit User Logout & Home Transient Flash (T070, T073)', () => {
    it('Topbar logout suppresses double click, executes private cleanup, replaces to / and renders transient "Đã đăng xuất"', async () => {
      let resolveSignOut!: () => void;
      const session = createControlledSession('signed-in');
      session.signOut.mockImplementationOnce(() => new Promise<void>(res => {
        resolveSignOut = () => {
          session.emitStatus('signed-out');
          res();
        };
      }));

      let cleanupInvoked = false;
      registerPrivateStateCleanup(() => {
        cleanupInvoked = true;
      });

      const { router } = renderRoutes(routes, { path: '/', session });

      // Open Topbar dropdown
      const trigger = await screen.findByRole('button', { name: /tài khoản/i });
      fireEvent.click(trigger);

      const signOutBtn = screen.getByRole('menuitem', { name: /đăng xuất/i });
      expect(signOutBtn).not.toBeDisabled();

      // Click sign out
      fireEvent.click(signOutBtn);

      // Pending state suppresses double click
      expect(signOutBtn).toBeDisabled();

      // Resolve the pending sign out
      resolveSignOut();

      await waitFor(() => {
        expect(session.signOut).toHaveBeenCalledTimes(1);
        expect(cleanupInvoked).toBe(true);
        expect(router.state.location.pathname).toBe('/');
      });

      // Transient notice displayed on Home
      expect(await screen.findByText('Đã đăng xuất')).toBeInTheDocument();

      // Dismiss button removes the flash notice
      const dismissBtn = screen.getByRole('button', { name: /đóng thông báo/i });
      fireEvent.click(dismissBtn);
      expect(screen.queryByText('Đã đăng xuất')).not.toBeInTheDocument();
    });

    it('Topbar logout failure keeps control in place, enables retry, and does not navigate', async () => {
      const session = createControlledSession('signed-in');
      session.signOut.mockRejectedValueOnce(new Error('Firebase network failure'));

      renderRoutes(routes, { path: '/', session });

      const trigger = await screen.findByRole('button', { name: /tài khoản/i });
      fireEvent.click(trigger);

      const signOutBtn = screen.getByRole('menuitem', { name: /đăng xuất/i });
      fireEvent.click(signOutBtn);

      await waitFor(() => {
        expect(screen.getByText('Đăng xuất thất bại. Vui lòng thử lại.')).toBeInTheDocument();
        expect(signOutBtn).not.toBeDisabled();
      });
    });

    it('Sidebar logout suppresses double-click, cleans up, and replaces to Home with flash', async () => {
      let resolveSignOut!: () => void;
      const session = createControlledSession('signed-in');
      session.signOut.mockImplementationOnce(() => new Promise<void>(res => {
        resolveSignOut = () => {
          session.emitStatus('signed-out');
          res();
        };
      }));

      let cleanupInvoked = false;
      registerPrivateStateCleanup(() => {
        cleanupInvoked = true;
      });

      const { router } = renderRoutes(routes, { path: '/catalogue-management/products', session });

      const signOutBtn = await screen.findByRole('button', { name: /đăng xuất/i });
      fireEvent.click(signOutBtn);

      expect(signOutBtn).toBeDisabled();

      resolveSignOut();

      await waitFor(() => {
        expect(session.signOut).toHaveBeenCalledTimes(1);
        expect(cleanupInvoked).toBe(true);
        expect(router.state.location.pathname).toBe('/');
      });

      expect(await screen.findByText('Đã đăng xuất')).toBeInTheDocument();
    });

    it('Sidebar logout failure stays in place with safe error and enabled retry', async () => {
      const session = createControlledSession('signed-in');
      session.signOut.mockRejectedValueOnce(new Error('Sign out error'));

      renderRoutes(routes, { path: '/catalogue-management/products', session });

      const signOutBtn = await screen.findByRole('button', { name: /đăng xuất/i });
      fireEvent.click(signOutBtn);

      await waitFor(() => {
        expect(screen.getByText('Đăng xuất thất bại')).toBeInTheDocument();
        expect(signOutBtn).not.toBeDisabled();
      });
    });
  });

  describe('5. Session Loss & Deduplication (T069, T070)', () => {
    it('Firebase null after initialization emits SESSION_LOST, executes cleanup once, and replaces to / with flash', async () => {
      const session = createControlledSession('signed-in');
      let cleanupCalls = 0;
      registerPrivateStateCleanup(() => {
        cleanupCalls++;
      });

      const { router } = renderRoutes(routes, { path: '/profile', session });

      // Simulate unexpected Firebase session drop
      session.emitStatus('signed-out');

      await waitFor(() => {
        expect(router.state.location.pathname).toBe('/');
      });

      expect(await screen.findByText('Phiên đã kết thúc, vui lòng đăng nhập lại')).toBeInTheDocument();
      expect(cleanupCalls).toBe(1);

      // Duplicate session drops do not trigger duplicate flash/navigation
      session.emitStatus('signed-out');
      expect(cleanupCalls).toBe(1);
    });

    it('explicit USER_SIGN_OUT wins over following Firebase null observer callback', async () => {
      const session = createControlledSession('signed-in');
      const { router } = renderRoutes(routes, { path: '/', session });

      const trigger = await screen.findByRole('button', { name: /tài khoản/i });
      fireEvent.click(trigger);

      const signOutBtn = screen.getByRole('menuitem', { name: /đăng xuất/i });
      fireEvent.click(signOutBtn);

      await waitFor(() => {
        expect(router.state.location.pathname).toBe('/');
      });

      // Flashed notice must be "Đã đăng xuất", NOT "Phiên đã kết thúc"
      expect(await screen.findByText('Đã đăng xuất')).toBeInTheDocument();
      expect(screen.queryByText('Phiên đã kết thúc, vui lòng đăng nhập lại')).not.toBeInTheDocument();
    });
  });

  describe('6. Authorization Failure (403) Safe Recovery (T074)', () => {
    it('403 retains session, does not clear private drafts, replaces to / and shows generic safe notice', async () => {
      let draftCleaned = false;
      registerPrivateStateCleanup(() => {
        draftCleaned = true;
      });

      const session = createControlledSession('signed-in');

      // Setup backend 403 on an authenticated operation
      mockFetch({
        'GET /api/v1/accounts/me/profile': json(403, {
          code: 'FORBIDDEN',
          message: 'Target role or branch permission is unauthorized',
        }),
      });

      const { router } = renderRoutes(routes, { path: '/profile', session });

      await waitFor(() => {
        expect(router.state.location.pathname).toBe('/');
      });

      // 403 notice must be generic without disclosing role/scope
      const notice = await screen.findByText('Bạn không có quyền truy cập chức năng này.');
      expect(notice).toBeInTheDocument();
      expect(screen.queryByText(/role|branch|unauthorized/i)).not.toBeInTheDocument();

      // Session and private drafts are preserved on 403
      expect(draftCleaned).toBe(false);
      expect(session.status).toBe('signed-in');
    });
  });
});
