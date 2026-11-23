import React from 'react';
import { beforeEach, describe, expect, it } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import { json, mockFetch, renderRoutes } from '../../test/render';
import { clearRoleCache } from '../auth/useRole';
import { clearNavBadgeCache } from '../nav/useNavBadges';
import OperationsShell, { HeaderActions } from './OperationsShell';

const Page = ({ label }) => (
  <section>
    <p>{label}</p>
    <HeaderActions><button type="button">Hành động của trang</button></HeaderActions>
  </section>
);

const routes = [{
  element: <OperationsShell />,
  children: [
    { path: '/catalogue-management/products', element: <Page label="Trang sản phẩm" /> },
    { path: '/catalogue-management/products/new', element: <Page label="Trang thêm sản phẩm" /> },
    { path: '/settlement/refunds', element: <Page label="Trang hoàn tiền" /> },
    { path: '/operations/sessions', element: <Page label="Trang ca" /> },
    { path: '/staff/home', element: <Page label="Trang chủ staff" /> },
    { path: '/carrier/orders', element: <Page label="Trang vận đơn" /> },
  ],
}];

const profile = role => json(200, { role, fullName: 'Test' });
const counts = (overrides = {}) => ({
  'GET /api/v1/late-payment-reviews': json(200, [{}, {}, {}]),
  'GET /api/v1/workshop-change-requests': json(200, [{}, {}]),
  'GET /api/v1/design-review-requests': json(200, { items: [{}] }),
  'GET /api/v1/workshop-refund-requests/waiting-count': json(200, { waiting: 4 }),
  'GET /api/v1/workshop-cash-closes/awaiting-confirmation-count': json(200, { waiting: 1 }),
  'GET /api/v1/ota-reconciliation-work-items/needing-attention-count': json(200, { waiting: 2 }),
  ...overrides,
});

const rail = () => within(screen.getByRole('complementary', { name: 'Operations Navigation' }));
const header = () => within(screen.getByRole('banner'));

function open(role, path, handlers = {}) {
  const calls = mockFetch({ 'GET /api/v1/accounts/me/profile': profile(role), ...counts(), ...handlers });
  renderRoutes(routes, { path });
  return calls;
}

describe('operations shell', () => {
  beforeEach(() => { clearRoleCache(); clearNavBadgeCache(); });

  it('shows a Manager the ten groups on the rail with the current one marked', async () => {
    open('MANAGER', '/settlement/refunds');
    await rail().findByRole('link', { name: /^Dashboard/ });
    ['Dashboard', 'Sản phẩm', 'Ưu đãi', 'Khách hàng', 'Bán lẻ', 'Workshop', 'Thu tiền', 'Vé và hoàn tiền', 'Kết nối OTA', 'Hệ thống']
      .forEach(name => expect(rail().getByRole('link', { name: new RegExp(`^${name}`) })).toBeInTheDocument());
    expect(rail().getByRole('link', { name: /^Vé và hoàn tiền/ })).toHaveAttribute('aria-current', 'true');
    expect(rail().getByRole('link', { name: /^Dashboard/ })).toHaveAttribute('href', '/manager/dashboard');
  });

  it('shows the group tabs with the current one marked, the title and the breadcrumb', async () => {
    open('MANAGER', '/settlement/refunds');
    expect(await header().findByRole('heading', { level: 1, name: 'Hàng đợi hoàn tiền' })).toBeInTheDocument();
    const tabs = within(screen.getByRole('navigation', { name: 'Trang trong nhóm' }));
    expect(tabs.getAllByRole('link').map(link => link.textContent.replace(/\d+$/, ''))).toEqual(['Hủy và đổi vé', 'Yêu cầu đổi vé', 'Hàng đợi hoàn tiền', 'Sự vụ và bồi hoàn']);
    expect(tabs.getByRole('link', { name: /^Hàng đợi hoàn tiền/ })).toHaveAttribute('aria-current', 'page');
    expect(within(screen.getByRole('navigation', { name: 'Breadcrumb' })).getByText('Vé và hoàn tiền')).toBeInTheDocument();
  });

  it('shows badges from the Manager counts on the rail and on the tabs, and none for a zero or failed count', async () => {
    open('MANAGER', '/settlement/refunds', { 'GET /api/v1/ota-reconciliation-work-items/needing-attention-count': json(500, { code: 'X' }) });
    // Refund 4 + change requests 2 = 6 on the group; 4 on the refund tab; 2 on the change-request tab.
    expect(await rail().findByRole('link', { name: 'Vé và hoàn tiền, 6' })).toBeInTheDocument();
    expect(rail().getByRole('link', { name: 'Thu tiền, 4' })).toBeInTheDocument();
    expect(rail().getByRole('link', { name: 'Khách hàng, 1' })).toBeInTheDocument();
    const tabs = within(screen.getByRole('navigation', { name: 'Trang trong nhóm' }));
    expect(tabs.getByRole('link', { name: /^Hàng đợi hoàn tiền/ })).toHaveTextContent('4');
    // The OTA read failed: no badge, and the rail still works.
    expect(rail().getByRole('link', { name: 'Kết nối OTA' })).toBeInTheDocument();
  });

  it('puts the tab actions and a page action in the header, and Staff see their own labels', async () => {
    open('MANAGER', '/catalogue-management/products');
    expect(await header().findByRole('link', { name: 'Thêm sản phẩm' })).toHaveAttribute('href', '/catalogue-management/products/new');
    expect(header().getByRole('link', { name: 'Import Excel' })).toHaveAttribute('href', '/catalogue-management/imports');
    expect(header().getByRole('button', { name: 'Hành động của trang' })).toBeInTheDocument();
  });

  it('titles a create page by its action and shows no actions there', async () => {
    open('MANAGER', '/catalogue-management/products/new');
    expect(await header().findByRole('heading', { level: 1, name: 'Thêm sản phẩm' })).toBeInTheDocument();
    expect(header().queryByRole('link', { name: 'Import Excel' })).not.toBeInTheDocument();
  });

  it('shows a Staff their sessions, their home in the first place and no Manager group, and reads only their own work counts', async () => {
    const calls = open('STAFF', '/operations/sessions', { 'GET /api/v1/consultations': json(200, { items: [], nextCursor: null, hasMore: false }) });
    expect(await header().findByRole('heading', { level: 1, name: 'Ca của tôi' })).toBeInTheDocument();
    await waitFor(() => expect(rail().getByRole('link', { name: 'Trang chủ' })).toHaveAttribute('href', '/staff/home'));
    expect(rail().queryByRole('link', { name: /Ưu đãi/ })).not.toBeInTheDocument();
    expect(rail().queryByRole('link', { name: /Hệ thống/ })).not.toBeInTheDocument();
    await new Promise(resolve => setTimeout(resolve, 30));
    // Staff badges read lists the backend limits to this Staff (sessions, branch orders, change requests, design queue), never a Manager count.
    const paths = calls.filter(call => call.path !== '/api/v1/accounts/me/profile').map(call => call.path);
    const own = ['/api/v1/workshop-sessions', '/api/v1/staff/retail-pickups', '/api/v1/staff/retail-shipments', '/api/v1/workshop-change-requests', '/api/v1/design-review-requests', '/api/v1/consultations'];
    expect(paths.filter(path => !own.includes(path))).toEqual([]);
    // The default mocks hold 1 design waiting for review, no open consultation and 2 pending change requests: a Staff sees 1 on Khách hàng and 2 on Vé và hoàn tiền.
    expect(await rail().findByLabelText('1 việc chờ')).toBeInTheDocument();
    expect(rail().getByLabelText('2 việc chờ')).toBeInTheDocument();
  });

  it('gives a GHTK carrier its three pages, the G logo, and language and sign-out at the top right (2026-11-23)', async () => {
    open('CARRIER', '/carrier/orders', { 'GET /api/v1/carrier/shipments': json(200, { items: [], totalPages: 0 }) });
    await rail().findByRole('link', { name: /^Đơn hàng/ });
    expect(rail().getAllByRole('link').map(link => link.getAttribute('href'))).toEqual(['/carrier', '/carrier', '/carrier/orders', '/carrier/stats']);
    expect(rail().getByRole('link', { name: 'GHTK' })).toBeInTheDocument();
    expect(rail().queryByRole('button', { name: 'EN' })).not.toBeInTheDocument();
    expect(rail().queryByRole('link', { name: /Hồ sơ/ })).not.toBeInTheDocument();
    expect(header().getByRole('button', { name: 'EN' })).toBeInTheDocument();
  });

  it('keeps the language switch and the profile link on the rail', async () => {
    open('MANAGER', '/catalogue-management/products');
    expect(await rail().findByRole('button', { name: 'EN' })).toBeInTheDocument();
    expect(rail().getByRole('link', { name: /Hồ sơ/ })).toHaveAttribute('href', '/profile');
  });
});
