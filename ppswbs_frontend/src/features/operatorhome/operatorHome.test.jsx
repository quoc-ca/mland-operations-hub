import React from 'react';
import { describe, expect, it } from 'vitest';
import { screen, within } from '@testing-library/react';
import { fakeSession, json, mockFetch, renderRoutes } from '../../test/render';
import { operatorHomeRoutes } from './routes';

const routes = [...operatorHomeRoutes, { path: '/', element: <p>General home</p> }];
// The shared sidebar lists every area for everyone; the home page content itself is what differs per role.
const main = () => within(screen.getByRole('main'));
const profile = role => json(200, { role, fullName: 'Test User', email: 'user@mland.test' });

describe('Staff and Manager home pages', () => {
  it('shows a Staff their daily work but none of the management sections', async () => {
    mockFetch({ 'GET /api/v1/accounts/me/profile': profile('STAFF') });
    renderRoutes(routes, { path: '/staff/home' });

    expect(await screen.findByRole('heading', { name: /Trang chủ nhân viên/ })).toBeInTheDocument();
    expect(screen.getByText(/Xin chào, Test User/)).toBeInTheDocument();
    expect(main().getByRole('link', { name: /Ca của tôi/ })).toHaveAttribute('href', '/operations/sessions');
    expect(main().getByRole('link', { name: /Hóa đơn quyết toán/ })).toHaveAttribute('href', '/settlement');
    // Staff and Manager both manage products.
    expect(main().getByRole('link', { name: /^Sản phẩm/ })).toHaveAttribute('href', '/catalogue-management/products');
    expect(main().queryByRole('link', { name: /Chương trình ưu đãi/ })).not.toBeInTheDocument();
    expect(main().queryByRole('link', { name: /Gói workshop/ })).not.toBeInTheDocument();
    expect(main().queryByRole('link', { name: /Chính sách hoàn tiền/ })).not.toBeInTheDocument();
  });

  it('shows a Manager the daily work and the management sections', async () => {
    mockFetch({ 'GET /api/v1/accounts/me/profile': profile('MANAGER') });
    renderRoutes(routes, { path: '/manager/home' });

    expect(await screen.findByRole('heading', { name: /Trang chủ Manager/ })).toBeInTheDocument();
    expect(main().getByRole('link', { name: /Bảng điều phối ca/ })).toBeInTheDocument();
    expect(main().getByRole('link', { name: /Gói workshop/ })).toHaveAttribute('href', '/operations/packages');
    expect(main().getByRole('link', { name: /Chính sách hoàn tiền/ })).toHaveAttribute('href', '/settlement/cases?tab=policies');
    expect(main().getByRole('link', { name: /Kênh đối tác/ })).toHaveAttribute('href', '/ota-connectivity/channels');
  });

  it('sends a Staff who opens the Manager home to their own home', async () => {
    mockFetch({ 'GET /api/v1/accounts/me/profile': profile('STAFF') });
    renderRoutes(routes, { path: '/manager/home' });

    expect(await screen.findByRole('heading', { name: /Trang chủ nhân viên/ })).toBeInTheDocument();
    expect(main().queryByRole('link', { name: /Gói workshop/ })).not.toBeInTheDocument();
  });

  it('sends a Member who opens an internal home to the general home', async () => {
    mockFetch({ 'GET /api/v1/accounts/me/profile': profile('MEMBER') });
    renderRoutes(routes, { path: '/staff/home' });

    expect(await screen.findByText('General home')).toBeInTheDocument();
  });

  it('offers sign-in instead of loading anything when there is no session', async () => {
    const calls = mockFetch({});
    renderRoutes(routes, { path: '/manager/home', session: fakeSession('signed-out') });

    expect(await screen.findByText(/Đăng nhập bằng tài khoản Staff hoặc Manager/)).toBeInTheDocument();
    expect(calls).toHaveLength(0);
  });

  it('/home goes to the home of the signed-in role', async () => {
    mockFetch({ 'GET /api/v1/accounts/me/profile': profile('MANAGER') });
    renderRoutes(routes, { path: '/home' });

    expect(await screen.findByRole('heading', { name: /Trang chủ Manager/ })).toBeInTheDocument();
  });
});
