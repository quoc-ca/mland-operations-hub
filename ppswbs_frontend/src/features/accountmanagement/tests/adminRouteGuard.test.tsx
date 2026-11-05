import { describe, it, expect, vi, beforeEach } from 'vitest';
import React from 'react';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router';
import AdminRouteGuard from '../components/AdminRouteGuard';
import * as useCurrentAccountModule from '../hooks/useCurrentAccount';

describe('AdminRouteGuard (RBAC & UI Authorization Defense-in-depth)', () => {
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

    expect(screen.getByText('Đang xác thực quyền truy cập…')).toBeInTheDocument();
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

  it('blocks MEMBER account from accessing admin routes and renders 403 Forbidden', () => {
    vi.spyOn(useCurrentAccountModule, 'useCurrentAccount').mockReturnValue({
      account: {
        accountId: 2,
        role: 'MEMBER',
        status: 'ACTIVE',
        loginEmail: 'minikie456@gmail.com',
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
          <Route element={<AdminRouteGuard />}>
            <Route path="/admin/accounts" element={<div>Internal Admin Table & Controls</div>} />
          </Route>
        </Routes>
      </MemoryRouter>
    );

    // Verifies admin content is strictly NEVER rendered
    expect(screen.queryByText('Internal Admin Table & Controls')).not.toBeInTheDocument();

    // Verifies 403 heading & explanation
    expect(screen.getByRole('heading', { name: 'Từ chối quyền truy cập (403 Forbidden)' })).toBeInTheDocument();
    expect(screen.getByText(/Trang này chỉ dành riêng cho tài khoản Quản trị viên hệ thống \(ADMIN\)/i)).toBeInTheDocument();

    // Verifies error alert and safe back link
    expect(screen.getByRole('alert')).toBeInTheDocument();
    expect(screen.getByText(/Không thể tải danh sách tài khoản hoặc truy cập trang quản trị/i)).toBeInTheDocument();
    expect(screen.getByRole('link', { name: '← Quay lại' })).toBeInTheDocument();
  });

  it('blocks STAFF account from accessing admin routes', () => {
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
          <Route element={<AdminRouteGuard />}>
            <Route path="/admin/roles" element={<div>Internal Admin Roles Catalog</div>} />
          </Route>
        </Routes>
      </MemoryRouter>
    );

    expect(screen.queryByText('Internal Admin Roles Catalog')).not.toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Từ chối quyền truy cập (403 Forbidden)' })).toBeInTheDocument();
  });

  it('allows ADMIN account to access admin routes and renders child content', () => {
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

    // Verifies admin content is properly rendered for ADMIN
    expect(screen.getByText('Internal Admin Table & Controls')).toBeInTheDocument();
    expect(screen.queryByText('Từ chối quyền truy cập (403 Forbidden)')).not.toBeInTheDocument();
  });
});

