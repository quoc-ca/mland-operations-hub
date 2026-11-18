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
    expect(sidebar().getByRole('link', { name: 'Folio vé (Ticket folio)' })).toBeInTheDocument();
    expect(sidebar().getByRole('link', { name: 'Add-on tại xưởng (Shop)' })).toBeInTheDocument();
    expect(sidebar().getByRole('link', { name: 'Trang chủ' })).toHaveAttribute('href', '/home');
    // Staff and Manager both manage products; promotions and the rest stay with the Manager.
    expect(sidebar().getByRole('link', { name: 'Danh sách sản phẩm' })).toBeInTheDocument();
    for (const hidden of ['Chương trình ưu đãi', 'Gói workshop', 'Add-on gói', 'Chi nhánh & khung giờ',
      'Kênh đối tác (Channels)', 'Sự vụ & bồi hoàn (Cases)',
      'Mẫu thông báo email', 'Nhật ký gửi thư']) {
      expect(sidebar().queryByRole('link', { name: hidden })).not.toBeInTheDocument();
    }
  });

  it('shows a Manager the management areas as well', async () => {
    open('MANAGER');

    expect(await sidebar().findByRole('link', { name: 'Gói workshop' })).toBeInTheDocument();
    for (const shown of ['Bảng điều phối ca', 'Danh sách sản phẩm', 'Chương trình ưu đãi', 'Add-on gói', 'Chi nhánh & khung giờ',
      'Kênh đối tác (Channels)', 'Sự vụ & bồi hoàn (Cases)',
      'Mẫu thông báo email', 'Nhật ký gửi thư']) {
      expect(sidebar().getByRole('link', { name: shown })).toBeInTheDocument();
    }
  });

  it('shows the package availability link to a Manager only', async () => {
    open('MANAGER');
    const link = await sidebar().findByRole('link', { name: 'Ma trận gói × chi nhánh' });
    expect(link).toHaveAttribute('href', '/operations/package-availability');
  });

  it.each(['STAFF', 'ADMIN'])('hides the package availability link from %s', async role => {
    open(role);
    await waitFor(() => expect(sidebar().getByRole('link', { name: /Ca của tôi|Bảng điều phối ca/ })).toBeInTheDocument());
    expect(sidebar().queryByRole('link', { name: 'Ma trận gói × chi nhánh' })).not.toBeInTheDocument();
  });

  it('hides the package availability link while the role is unknown', async () => {
    mockFetch({ 'GET /api/v1/workshop-sessions': json(200, []) });
    renderRoutes(operationsRoutes, { path: '/operations/sessions' });
    expect(await sidebar().findByRole('link', { name: 'Bảng điều phối ca' })).toBeInTheDocument();
    expect(sidebar().queryByRole('link', { name: 'Ma trận gói × chi nhánh' })).not.toBeInTheDocument();
  });

  it('renders the matrix page at its route for a Manager and the denial page on a backend 403', async () => {
    mockFetch({
      'GET /api/v1/accounts/me/profile': profile('MANAGER'),
      'GET /api/v1/workshop-package-availability': json(200, {
        packages: [{ id: 1, name: 'Brass', lifecycleState: 'PUBLISHED' }],
        locations: [{ id: 10, name: 'Hoàn Kiếm', active: true }],
        cells: [{ packageId: 1, locationId: 10, state: 'ACTIVE', version: 1 }],
      }),
    });
    renderRoutes(operationsRoutes, { path: '/operations/package-availability' });
    expect(await screen.findByRole('checkbox', { name: 'Brass tại Hoàn Kiếm' })).toBeChecked();
    expect(sidebar().getByRole('link', { name: 'Ma trận gói × chi nhánh' })).toHaveClass('active');
  });

  it.each(['STAFF', 'ADMIN'])('shows the denial page to %s when the backend answers 403', async role => {
    mockFetch({
      'GET /api/v1/accounts/me/profile': profile(role),
      'GET /api/v1/workshop-package-availability': json(403, { code: 'FORBIDDEN', message: 'no' }),
    });
    renderRoutes(operationsRoutes, { path: '/operations/package-availability' });
    expect(await screen.findByText('Bạn không có quyền xem trang này')).toBeInTheDocument();
    expect(screen.queryByRole('checkbox')).not.toBeInTheDocument();
    expect(sidebar().queryByRole('link', { name: 'Ma trận gói × chi nhánh' })).not.toBeInTheDocument();
  });

  it('shows only the daily work while the role is unknown', async () => {
    mockFetch({ 'GET /api/v1/workshop-sessions': json(200, []) });
    renderRoutes(operationsRoutes, { path: '/operations/sessions' });

    expect(await sidebar().findByRole('link', { name: 'Bảng điều phối ca' })).toBeInTheDocument();
    await waitFor(() => expect(sidebar().queryByRole('link', { name: 'Gói workshop' })).not.toBeInTheDocument());
  });
});

describe('Late payments sidebar link', () => {
  beforeEach(() => clearRoleCache());

  it('shows a Manager the late payments link', async () => {
    open('MANAGER');
    const link = await sidebar().findByRole('link', { name: 'Thanh toán về trễ' });
    expect(link).toHaveAttribute('href', '/operations/late-payments');
  });

  it.each(['STAFF', 'ADMIN'])('hides the late payments link from %s', async role => {
    open(role);
    await waitFor(() => expect(sidebar().getByRole('link', { name: /Ca của tôi|Bảng điều phối ca/ })).toBeInTheDocument());
    expect(sidebar().queryByRole('link', { name: 'Thanh toán về trễ' })).not.toBeInTheDocument();
  });
});

describe('Audit log sidebar link (003.2 US7)', () => {
  beforeEach(() => clearRoleCache());

  it('shows a Manager the audit log link, right after the package availability matrix', async () => {
    open('MANAGER');
    const link = await sidebar().findByRole('link', { name: 'Nhật ký audit' });
    expect(link).toHaveAttribute('href', '/operations/audit');
    const matrix = sidebar().getByRole('link', { name: 'Ma trận gói × chi nhánh' });
    expect(matrix.compareDocumentPosition(link) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    const between = Array.from(matrix.parentElement.children);
    expect(between.indexOf(link) - between.indexOf(matrix)).toBe(1);
  });

  it.each(['STAFF', 'ADMIN'])('hides the audit log link from %s', async role => {
    open(role);
    await waitFor(() => expect(sidebar().getByRole('link', { name: /Ca của tôi|Bảng điều phối ca/ })).toBeInTheDocument());
    expect(sidebar().queryByRole('link', { name: 'Nhật ký audit' })).not.toBeInTheDocument();
  });

  it('hides the audit log link while the role is unknown', async () => {
    mockFetch({ 'GET /api/v1/workshop-sessions': json(200, []) });
    renderRoutes(operationsRoutes, { path: '/operations/sessions' });
    expect(await sidebar().findByRole('link', { name: 'Bảng điều phối ca' })).toBeInTheDocument();
    expect(sidebar().queryByRole('link', { name: 'Nhật ký audit' })).not.toBeInTheDocument();
  });

  it('renders the audit page at /operations/audit for a Manager with its entry highlighted', async () => {
    mockFetch({
      'GET /api/v1/accounts/me/profile': profile('MANAGER'),
      'GET /api/v1/workshop-audit/operations-events': json(200, { items: [], nextCursor: null }),
    });
    renderRoutes(operationsRoutes, { path: '/operations/audit' });
    expect(await screen.findByRole('heading', { name: 'Nhật ký audit workshop' })).toBeInTheDocument();
    expect(await sidebar().findByRole('link', { name: 'Nhật ký audit' })).toHaveClass('active');
  });

  it('shows a Staff who types the URL no audit rows: the backend answers 403', async () => {
    mockFetch({
      'GET /api/v1/accounts/me/profile': profile('STAFF'),
      'GET /api/v1/workshop-audit/operations-events': json(403, { code: 'FORBIDDEN', message: 'no' }),
    });
    renderRoutes(operationsRoutes, { path: '/operations/audit' });
    expect(await screen.findByRole('alert')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Chi tiết' })).not.toBeInTheDocument();
    expect(sidebar().queryByRole('link', { name: 'Nhật ký audit' })).not.toBeInTheDocument();
  });
});

describe('Change requests sidebar link', () => {
  beforeEach(() => clearRoleCache());

  it.each(['STAFF', 'MANAGER'])('shows the change requests link to %s', async role => {
    open(role);
    expect(await sidebar().findByRole('link', { name: 'Yêu cầu đổi vé' })).toHaveAttribute('href', '/settlement/change-requests');
  });

  it('hides the change requests link from an Admin', async () => {
    open('ADMIN');
    await waitFor(() => expect(sidebar().getByRole('link', { name: 'Trang chủ' })).toBeInTheDocument());
    expect(sidebar().queryByRole('link', { name: 'Yêu cầu đổi vé' })).not.toBeInTheDocument();
  });
});

describe('Sidebar current entry', () => {
  beforeEach(() => clearRoleCache());

  it('marks the current screen as active and brings it into view', async () => {
    const original = Element.prototype.scrollIntoView;
    const scrolled = [];
    Element.prototype.scrollIntoView = function track(options) { scrolled.push({ text: this.textContent, options }); };
    try {
      open('MANAGER');
      const current = await sidebar().findByRole('link', { name: 'Bảng điều phối ca' });
      expect(current).toHaveClass('active');
      expect(sidebar().getByRole('link', { name: 'Gói workshop' })).not.toHaveClass('active');
      expect(scrolled.some(item => item.text.includes('Bảng điều phối ca') && item.options?.block === 'center')).toBe(true);
    } finally {
      Element.prototype.scrollIntoView = original;
    }
  });
});
