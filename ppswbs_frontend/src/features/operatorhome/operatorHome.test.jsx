import React from 'react';
import { describe, expect, it, vi } from 'vitest';
import { screen, within } from '@testing-library/react';
import { fakeSession, json, mockFetch, renderRoutes } from '../../test/render';
import { operatorHomeRoutes } from './routes';

vi.mock('../../shared/auth/RouteAccessGuard', () => import('../../test/passThroughGuard'));

const routes = [...operatorHomeRoutes, { path: '/manager/home', element: <p>Manager home page</p> }, { path: '/', element: <p>General home</p> }];
// The shared sidebar lists every area for everyone; the home page content itself is what differs per role.
const main = () => within(screen.getByRole('main'));
const profile = role => json(200, { role, fullName: 'Test User', email: 'user@mland.test' });

describe('Staff and Manager home pages', () => {
  it('shows a Staff the Workshop and Orders cards with what waits on each side (Session 2026-11-22)', async () => {
    const now = Date.now();
    const iso = offsetMinutes => new Date(now + offsetMinutes * 60_000).toISOString();
    mockFetch({
      'GET /api/v1/accounts/me/profile': profile('STAFF'),
      'GET /api/v1/workshop-sessions': json(200, [
        { id: 'late', state: 'IN_PROGRESS', locationName: 'Hoàn Kiếm', scheduledStartAt: iso(-180), scheduledEndAt: iso(-60), checkInOpensAt: iso(-210) },
        { id: 'done', state: 'ATTENDANCE_FINALIZED', scheduledStartAt: iso(-300), scheduledEndAt: iso(-240) },
      ]),
      'GET /api/v1/staff/retail-pickups': json(200, [
        { order: { id: 'o1', paymentState: 'ACCEPTED_UNPAID' }, pickupReadyConfirmed: false },
        { order: { id: 'o2', paymentState: 'PAID' }, pickupReadyConfirmed: true },
      ]),
      'GET /api/v1/staff/retail-shipments': json(200, { items: [] }),
    });
    renderRoutes(routes, { path: '/staff/home' });

    expect(await screen.findByRole('heading', { name: /Xin chào/ })).toBeInTheDocument();
    expect(screen.getByText(/Xin chào, Test User/)).toBeInTheDocument();
    expect(main().getByRole('tab', { name: /Workshop/ })).toHaveAttribute('aria-selected', 'true');
    // The finished session needs nothing; the one that ended without finalizing is listed first.
    expect(await main().findByRole('link', { name: /chưa chốt điểm danh/ })).toHaveAttribute('href', '/operations/sessions/late');
    // The other side says in red how much waits there: one cash order to collect, one pickup to hand over.
    expect(await main().findByText('2 đơn cần giải quyết')).toBeInTheDocument();
    expect(main().getByRole('link', { name: 'Hóa đơn quyết toán' })).toHaveAttribute('href', '/settlement');
  });

  it('sends a Member who opens an internal home to the general home', async () => {
    mockFetch({ 'GET /api/v1/accounts/me/profile': profile('MEMBER') });
    renderRoutes(routes, { path: '/staff/home' });

    expect(await screen.findByText('General home')).toBeInTheDocument();
  });

  it('offers sign-in instead of loading anything when there is no session', async () => {
    const calls = mockFetch({});
    renderRoutes(routes, { path: '/staff/home', session: fakeSession('signed-out') });

    expect(await screen.findByText(/Đăng nhập bằng tài khoản Staff hoặc Manager/)).toBeInTheDocument();
    expect(calls).toHaveLength(0);
  });

  it('/home goes to the home of the signed-in role', async () => {
    mockFetch({ 'GET /api/v1/accounts/me/profile': profile('MANAGER') });
    renderRoutes(routes, { path: '/home' });

    expect(await screen.findByText('Manager home page')).toBeInTheDocument();
  });
});

describe('/home access', () => {
  it('sends a guest to sign in without loading anything', async () => {
    const calls = mockFetch({});
    renderRoutes([...routes, { path: '/login', element: <p>Login page</p> }], { path: '/home', session: fakeSession('signed-out') });

    expect(await screen.findByText('Login page')).toBeInTheDocument();
    expect(calls).toHaveLength(0);
  });

  it('shows a retry when the profile cannot be read', async () => {
    mockFetch({ 'GET /api/v1/accounts/me/profile': json(500, { code: 'X' }) });
    renderRoutes(routes, { path: '/home' });

    expect(await screen.findByRole('button', { name: /Thử lại/ })).toBeInTheDocument();
  });
});
