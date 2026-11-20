import { describe, it, expect, vi, beforeEach } from 'vitest';
import React from 'react';
import { render, screen, fireEvent } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router';
import AdminRouteGuard from '../components/AdminRouteGuard';
import * as useCurrentAccountModule from '../hooks/useCurrentAccount';

describe('AdminRouteGuard (R-404 / FR-046 / T074 Browser Route Masking)', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('renders loading state while account authorization is loading', () => {
    vi.spyOn(useCurrentAccountModule, 'useCurrentAccount').mockReturnValue({
      account: null,
      status: 'loading',
      error: null,
      reloadAccount: vi.fn(),
      signOut: vi.fn(),
      retry: vi.fn(),
    });

    render(
      <MemoryRouter initialEntries={['/admin/accounts']}>
        <Routes>
          <Route element={<AdminRouteGuard />}>
            <Route path="/admin/accounts" element={<div>Protected Admin Content</div>} />
          </Route>
        </Routes>
      </MemoryRouter>
    );

    expect(screen.getByRole('status')).toBeInTheDocument();
    expect(screen.queryByText('Protected Admin Content')).not.toBeInTheDocument();
  });

  it('redirects to /login when visitor is unauthenticated', () => {
    vi.spyOn(useCurrentAccountModule, 'useCurrentAccount').mockReturnValue({
      account: null,
      status: 'unauthenticated',
      error: null,
      reloadAccount: vi.fn(),
      signOut: vi.fn(),
      retry: vi.fn(),
    });

    render(
      <MemoryRouter initialEntries={['/admin/accounts']}>
        <Routes>
          <Route path="/login" element={<div>Login Page</div>} />
          <Route element={<AdminRouteGuard />}>
            <Route path="/admin/accounts" element={<div>Protected Admin Content</div>} />
          </Route>
        </Routes>
      </MemoryRouter>
    );

    expect(screen.getByText('Login Page')).toBeInTheDocument();
    expect(screen.queryByText('Protected Admin Content')).not.toBeInTheDocument();
  });

  it('masks and replaces to /404 when MEMBER attempts to access admin routes (R-404)', () => {
    vi.spyOn(useCurrentAccountModule, 'useCurrentAccount').mockReturnValue({
      account: {
        accountId: 2,
        role: 'MEMBER',
        status: 'ACTIVE',
        loginEmail: 'member@mland.vn',
        revocationStatus: 'NOT_APPLICABLE',
        createdAt: '2026-11-01',
        updatedAt: '2026-11-04',
      },
      status: 'authenticated',
      error: null,
      reloadAccount: vi.fn(),
      signOut: vi.fn(),
      retry: vi.fn(),
    });

    render(
      <MemoryRouter initialEntries={['/admin/accounts']}>
        <Routes>
          <Route path="/404" element={<div>404 Not Found Page</div>} />
          <Route element={<AdminRouteGuard />}>
            <Route path="/admin/accounts" element={<div>Internal Admin Table & Controls</div>} />
          </Route>
        </Routes>
      </MemoryRouter>
    );

    // Verifies admin content is strictly NEVER rendered
    expect(screen.queryByText('Internal Admin Table & Controls')).not.toBeInTheDocument();

    // Verifies masked transition to /404 without disclosure
    expect(screen.getByText('404 Not Found Page')).toBeInTheDocument();
  });

  it('masks and replaces to /404 when STAFF attempts to access admin routes (R-404)', () => {
    vi.spyOn(useCurrentAccountModule, 'useCurrentAccount').mockReturnValue({
      account: {
        accountId: 3,
        role: 'STAFF',
        status: 'ACTIVE',
        loginEmail: 'staff@mland.vn',
        revocationStatus: 'NOT_APPLICABLE',
        createdAt: '2026-11-01',
        updatedAt: '2026-11-04',
      },
      status: 'authenticated',
      error: null,
      reloadAccount: vi.fn(),
      signOut: vi.fn(),
      retry: vi.fn(),
    });

    render(
      <MemoryRouter initialEntries={['/admin/roles']}>
        <Routes>
          <Route path="/404" element={<div>404 Not Found Page</div>} />
          <Route element={<AdminRouteGuard />}>
            <Route path="/admin/roles" element={<div>Internal Admin Roles Catalog</div>} />
          </Route>
        </Routes>
      </MemoryRouter>
    );

    expect(screen.queryByText('Internal Admin Roles Catalog')).not.toBeInTheDocument();
    expect(screen.getByText('404 Not Found Page')).toBeInTheDocument();
  });

  it('masks and replaces to /404 when CARRIER attempts to access admin routes (R-404)', () => {
    vi.spyOn(useCurrentAccountModule, 'useCurrentAccount').mockReturnValue({
      account: {
        accountId: 5,
        role: 'CARRIER',
        status: 'ACTIVE',
        loginEmail: 'carrier@mland.vn',
        revocationStatus: 'NOT_APPLICABLE',
        createdAt: '2026-11-20',
        updatedAt: '2026-11-20',
      },
      status: 'authenticated',
      error: null,
      reloadAccount: vi.fn(),
      signOut: vi.fn(),
      retry: vi.fn(),
    });

    render(
      <MemoryRouter initialEntries={['/admin/accounts']}>
        <Routes>
          <Route path="/404" element={<div>404 Not Found Page</div>} />
          <Route element={<AdminRouteGuard />}>
            <Route path="/admin/accounts" element={<div>Internal Admin Table & Controls</div>} />
          </Route>
        </Routes>
      </MemoryRouter>
    );

    expect(screen.queryByText('Internal Admin Table & Controls')).not.toBeInTheDocument();
    expect(screen.getByText('404 Not Found Page')).toBeInTheDocument();
  });

  it('masks and replaces to /404 when SUSPENDED ADMIN attempts to access admin routes (R-404)', () => {
    vi.spyOn(useCurrentAccountModule, 'useCurrentAccount').mockReturnValue({
      account: {
        accountId: 1,
        role: 'ADMIN',
        status: 'SUSPENDED',
        loginEmail: 'admin@mland.vn',
        revocationStatus: 'REVOKED',
        createdAt: '2026-10-01',
        updatedAt: '2026-11-20',
      },
      status: 'authenticated',
      error: null,
      reloadAccount: vi.fn(),
      signOut: vi.fn(),
      retry: vi.fn(),
    });

    render(
      <MemoryRouter initialEntries={['/admin/accounts']}>
        <Routes>
          <Route path="/404" element={<div>404 Not Found Page</div>} />
          <Route element={<AdminRouteGuard />}>
            <Route path="/admin/accounts" element={<div>Internal Admin Table & Controls</div>} />
          </Route>
        </Routes>
      </MemoryRouter>
    );

    expect(screen.queryByText('Internal Admin Table & Controls')).not.toBeInTheDocument();
    expect(screen.getByText('404 Not Found Page')).toBeInTheDocument();
  });

  it('allows active ADMIN account to access admin routes and renders child content', () => {
    vi.spyOn(useCurrentAccountModule, 'useCurrentAccount').mockReturnValue({
      account: {
        accountId: 1,
        role: 'ADMIN',
        status: 'ACTIVE',
        loginEmail: 'admin@mland.vn',
        revocationStatus: 'NOT_APPLICABLE',
        createdAt: '2026-10-01',
        updatedAt: '2026-11-04',
      },
      status: 'authenticated',
      error: null,
      reloadAccount: vi.fn(),
      signOut: vi.fn(),
      retry: vi.fn(),
    });

    render(
      <MemoryRouter initialEntries={['/admin/accounts']}>
        <Routes>
          <Route element={<AdminRouteGuard />}>
            <Route path="/admin/accounts" element={<div>Internal Admin Table & Controls</div>} />
          </Route>
        </Routes>
      </MemoryRouter>
    );

    // Verifies admin content is properly rendered for active ADMIN
    expect(screen.getByText('Internal Admin Table & Controls')).toBeInTheDocument();
  });

  it('handles bootstrap technical failure by keeping current URL, showing unavailable state and offering retry (R-404)', () => {
    const retryMock = vi.fn();
    vi.spyOn(useCurrentAccountModule, 'useCurrentAccount').mockReturnValue({
      account: null,
      status: 'error',
      error: new Error('Bootstrap failed'),
      reloadAccount: vi.fn(),
      signOut: vi.fn(),
      retry: retryMock,
    });

    render(
      <MemoryRouter initialEntries={['/admin/accounts']}>
        <Routes>
          <Route path="/404" element={<div>404 Not Found Page</div>} />
          <Route element={<AdminRouteGuard />}>
            <Route path="/admin/accounts" element={<div>Internal Admin Table & Controls</div>} />
          </Route>
        </Routes>
      </MemoryRouter>
    );

    expect(screen.queryByText('Internal Admin Table & Controls')).not.toBeInTheDocument();
    expect(screen.queryByText('404 Not Found Page')).not.toBeInTheDocument();
    expect(screen.getByText(/Không thể kết nối hoặc xác thực thông tin tài khoản/i)).toBeInTheDocument();

    const retryBtn = screen.getByRole('button', { name: /Thử lại/i });
    expect(retryBtn).toBeInTheDocument();
    fireEvent.click(retryBtn);
    expect(retryMock).toHaveBeenCalledTimes(1);
  });
});
