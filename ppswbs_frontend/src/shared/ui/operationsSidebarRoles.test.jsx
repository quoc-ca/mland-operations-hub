import React from 'react';
import { beforeEach, describe, expect, it } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import { json, mockFetch, renderRoutes } from '../../test/render';
import { operationsRoutes } from '../../workshop-operations/routes';
import { clearRoleCache } from '../auth/useRole';

const profile = role => json(200, { role, fullName: 'Test User' });
const sidebar = () => within(screen.getByRole('complementary', { name: 'Operations Navigation' }));

function open(role) {
  mockFetch({
    'GET /api/v1/accounts/me/profile': profile(role),
    'GET /api/v1/workshop-sessions': json(200, []),
  });
  renderRoutes(operationsRoutes, { path: '/operations/sessions' });
}

describe('Operations sidebar by role', () => {
  beforeEach(() => clearRoleCache());

  it('shows a Staff only the daily work', async () => {
    open('STAFF');

    expect(await sidebar().findByRole('link', { name: 'Ca của tôi' })).toBeInTheDocument();
    expect(sidebar().getByRole('link', { name: 'Hóa đơn quyết toán (Bills)' })).toBeInTheDocument();
    expect(sidebar().getByRole('link', { name: 'Add-on tại xưởng (Shop)' })).toBeInTheDocument();
    expect(sidebar().getByRole('link', { name: 'Trang chủ' })).toHaveAttribute('href', '/home');
    // Staff and Manager both manage products; promotions and the rest stay with the Manager.
    expect(sidebar().getByRole('link', { name: 'Danh sách sản phẩm' })).toBeInTheDocument();
    for (const hidden of ['Chương trình ưu đãi', 'Gói workshop', 'Add-on gói', 'Chi nhánh & khung giờ',
      'Kênh đối tác (Channels)', 'Sự vụ & bồi hoàn (Cases)', 'Chính sách hoàn tiền']) {
      expect(sidebar().queryByRole('link', { name: hidden })).not.toBeInTheDocument();
    }
  });

  it('shows a Manager the management areas as well', async () => {
    open('MANAGER');

    expect(await sidebar().findByRole('link', { name: 'Gói workshop' })).toBeInTheDocument();
    for (const shown of ['Bảng điều phối ca', 'Danh sách sản phẩm', 'Chương trình ưu đãi', 'Add-on gói', 'Chi nhánh & khung giờ',
      'Kênh đối tác (Channels)', 'Sự vụ & bồi hoàn (Cases)', 'Chính sách hoàn tiền']) {
      expect(sidebar().getByRole('link', { name: shown })).toBeInTheDocument();
    }
  });

  it('shows only the daily work while the role is unknown', async () => {
    mockFetch({ 'GET /api/v1/workshop-sessions': json(200, []) });
    renderRoutes(operationsRoutes, { path: '/operations/sessions' });

    expect(await sidebar().findByRole('link', { name: 'Bảng điều phối ca' })).toBeInTheDocument();
    await waitFor(() => expect(sidebar().queryByRole('link', { name: 'Gói workshop' })).not.toBeInTheDocument());
  });
});
