import React from 'react';
import { describe, expect, it } from 'vitest';
import { act, fireEvent, screen, waitFor, within } from '@testing-library/react';
import { executePrivateStateCleanup } from '../../../shared/auth/privateStateCleanup';
import { fakeSession, json, mockFetch, renderRoutes } from '../../../test/render';
import { baseHandlers, profile, routes } from './helpers';

const open = async (handlers, options = {}) => {
  const calls = mockFetch(baseHandlers(handlers));
  renderRoutes(routes, { path: '/manager/dashboard', ...options });
  await screen.findByRole('main');
  return calls;
};
const main = () => within(screen.getByRole('main'));
const sectionOf = async title => (await main().findByRole('heading', { name: title })).closest('section');

describe('today strip', () => {
  it('counts cancelled sessions in the total and says how many were cancelled', async () => {
    await open();
    expect(await main().findByText(/trong đó 1 ca đã hủy/)).toBeInTheDocument();
    expect(within(await sectionOf('Vận hành hôm nay')).getByText('2')).toBeInTheDocument();
  });

  it('reads again only the strip when a branch is chosen, leaving the other blocks alone', async () => {
    const calls = await open();
    await main().findByText(/trong đó 1 ca đã hủy/);
    const before = calls.filter(call => call.path === '/api/v1/executive-reports').length;
    fireEvent.change(await main().findByLabelText('Chi nhánh'), { target: { value: 'l2' } });
    await waitFor(() => expect(calls.some(call => call.path === '/api/v1/workshop-day-summaries' && call.query.locationId === 'l2')).toBe(true));
    expect(calls.filter(call => call.path === '/api/v1/executive-reports')).toHaveLength(before);
  });

  it('says there are no sessions today and shows no revenue or utilization in the strip', async () => {
    await open({ 'GET /api/v1/workshop-day-summaries': json(200, { date: '2026-11-18', sessions: 0, cancelledSessions: 0, checkedIn: 0, expected: 0 }) });
    const strip = await sectionOf('Vận hành hôm nay');
    expect(await within(strip).findByText('Hôm nay không có ca nào.')).toBeInTheDocument();
    expect(within(strip).queryByText(/doanh thu|lấp đầy|₫/i)).not.toBeInTheDocument();
  });

  it('asks for today in Vietnam time', async () => {
    const calls = await open();
    await main().findByText(/trong đó 1 ca đã hủy/);
    const call = calls.find(item => item.path === '/api/v1/workshop-day-summaries');
    expect(call.query.date).toMatch(/^\d{4}-\d{2}-\d{2}$/);
  });

  it('shows check-in progress over the sessions that are not cancelled', async () => {
    await open();
    expect(await main().findByText('1/3')).toBeInTheDocument();
    expect(main().getByText(/Check-in \(không tính ca đã hủy\)/)).toBeInTheDocument();
  });

  it('still shows the session counts, without progress, when the day summary cannot be read', async () => {
    await open({ 'GET /api/v1/workshop-day-summaries': json(500, { code: 'X' }) });
    expect(await main().findByText(/trong đó 1 ca đã hủy/)).toBeInTheDocument();
    expect(main().queryByText(/Check-in \(/)).not.toBeInTheDocument();
  });

  it('shows the strip when only the branch list fails', async () => {
    await open({ 'GET /api/v1/workshop-locations': json(500, { code: 'X' }) });
    expect(await main().findByText(/trong đó 1 ca đã hủy/)).toBeInTheDocument();
    expect(await main().findByText(/Không tải được danh sách chi nhánh/)).toBeInTheDocument();
  });
});

describe('recent events', () => {
  it('merges the Operations and Settlement reads newest first and links to the audit log', async () => {
    await open();
    const section = await sectionOf('Việc gần đây cần chú ý');
    await waitFor(() => expect(within(section).getAllByRole('listitem')).toHaveLength(3));
    const items = within(section).getAllByRole('listitem');
    expect(items[0]).toHaveTextContent('WORKSHOP_PACKAGE_AVAILABILITY_CHANGED');
    expect(items[1]).toHaveTextContent('Staff A #33');
    expect(items[2]).toHaveTextContent('DENIED_MANAGER_REQUIRED');
    expect(within(section).queryByText('Nguồn Settlement chưa được bao gồm.')).not.toBeInTheDocument();
    expect(within(section).queryByText(/Một phần dữ liệu đang thiếu/)).not.toBeInTheDocument();
    expect(within(section).getByRole('link', { name: 'Mở nhật ký audit' })).toHaveAttribute('href', '/operations/audit');
    expect(document.body.textContent).not.toMatch(/secret reason/);
  });

  it('keeps the other source and notes missing data when one read fails', async () => {
    await open({
      'GET /api/v1/workshop-audit/operations-events': call => (call.query.outcome === 'DENIED'
        ? json(500, { code: 'X' })
        : json(200, { items: [{ id: 'e2', occurredAt: '2026-11-18T04:00:00Z', actor: { accountId: 1 }, action: 'WORKSHOP_PACKAGE_AVAILABILITY_CHANGED' }] })),
    });
    expect(await main().findByText(/Một phần dữ liệu đang thiếu/)).toBeInTheDocument();
    expect(main().getByText('WORKSHOP_PACKAGE_AVAILABILITY_CHANGED')).toBeInTheDocument();
  });

  it('is unavailable, with no link, when neither source can be read, and keeps the other sections', async () => {
    await open({
      'GET /api/v1/workshop-audit/operations-events': json(500, { code: 'X' }),
      'GET /api/v1/workshop-audit/settlement-records': json(500, { code: 'X' }),
    });
    const section = await sectionOf('Việc gần đây cần chú ý');
    expect(await within(section).findByText('Không tải được dữ liệu này.')).toBeInTheDocument();
    expect(within(section).queryByRole('link')).not.toBeInTheDocument();
    expect(await main().findByText(/trong đó 1 ca đã hủy/)).toBeInTheDocument();
  });

  it('says nothing recent when there are no events', async () => {
    await open({
      'GET /api/v1/workshop-audit/operations-events': json(200, { items: [] }),
      'GET /api/v1/workshop-audit/settlement-records': json(200, { items: [] }),
    });
    expect(await main().findByText('Không có gì mới trong 24 giờ qua.')).toBeInTheDocument();
  });

  it('notes missing data when the Settlement read fails but keeps the Operations events', async () => {
    await open({ 'GET /api/v1/workshop-audit/settlement-records': json(500, { code: 'X' }) });
    expect(await main().findByText(/Một phần dữ liệu đang thiếu/)).toBeInTheDocument();
    expect(main().getByText('WORKSHOP_PACKAGE_AVAILABILITY_CHANGED')).toBeInTheDocument();
  });

  it('keeps at most five events', async () => {
    const rows = Array.from({ length: 5 }, (_, i) => ({ id: `r${i}`, occurredAt: `2026-11-18T0${i}:00:00Z`, actor: { accountId: i }, action: `A${i}` }));
    await open({
      'GET /api/v1/workshop-audit/operations-events': json(200, { items: rows }),
      'GET /api/v1/workshop-audit/settlement-records': json(200, { items: [{ id: 's9', occurredAt: '2026-11-18T09:00:00Z', actor: { accountId: 9 }, actionCode: 'DENIED_X' }] }),
    });
    const section = await sectionOf('Việc gần đây cần chú ý');
    await waitFor(() => expect(within(section).getAllByRole('listitem')).toHaveLength(5));
  });
});

describe('dashboard page', () => {
  it('shows today, revenue and recent events in that order, with no queue counts and no shortcuts', async () => {
    const calls = await open();
    await main().findByText(/trong đó 1 ca đã hủy/);
    const headings = main().getAllByRole('heading', { level: 2 }).map(heading => heading.textContent);
    const order = ['Vận hành hôm nay', 'Doanh thu và hiệu suất', 'Việc gần đây cần chú ý'].map(title => headings.indexOf(title));
    expect(order.every(index => index >= 0)).toBe(true);
    expect([...order].sort((a, b) => a - b)).toEqual(order);
    expect(main().queryByRole('heading', { name: 'Việc đang chờ' })).not.toBeInTheDocument();
    expect(main().queryByRole('heading', { name: 'Lối tắt' })).not.toBeInTheDocument();
    // The waiting-work counts belong to the rail badges, not to the page.
    expect(calls.some(call => call.path.endsWith('-count') || call.path === '/api/v1/late-payment-reviews')).toBe(false);
  });

  it('reads on open only, never on a timer, and sends only GET requests', async () => {
    const calls = await open();
    await main().findByText(/trong đó 1 ca đã hủy/);
    const opened = calls.length;
    await new Promise(resolve => setTimeout(resolve, 80));
    expect(calls).toHaveLength(opened);
    expect(calls.every(call => call.method === 'GET')).toBe(true);
  });

  it('keeps the revenue block working when the operations blocks fail', async () => {
    await open({ 'GET /api/v1/workshop-day-summaries': json(500, { code: 'X' }), 'GET /api/v1/workshop-sessions': json(500, { code: 'X' }) });
    expect(await within(await sectionOf('Vận hành hôm nay')).findByText('Không tải được dữ liệu này.')).toBeInTheDocument();
    expect(await main().findByRole('region', { name: 'Chỉ số chính' })).toBeInTheDocument();
  });

  it('keeps the operations blocks working when the revenue block fails', async () => {
    await open({ 'GET /api/v1/executive-reports': json(503, { code: 'X' }) });
    expect(await main().findByText(/trong đó 1 ca đã hủy/)).toBeInTheDocument();
    expect(await main().findByText(/tạm thời không đọc được/)).toBeInTheDocument();
  });

  it('renders in English when the language is English', async () => {
    await open({}, { language: 'en' });
    expect(await main().findByRole('heading', { name: 'Operations today' })).toBeInTheDocument();
  });

  it('redirects the old Manager home address to the dashboard', async () => {
    mockFetch(baseHandlers());
    renderRoutes(routes, { path: '/manager/home' });
    expect(await screen.findByRole('main')).toBeInTheDocument();
    expect(await screen.findByRole('heading', { name: 'Vận hành hôm nay' })).toBeInTheDocument();
  });
});

describe('access', () => {
  it.each([
    ['STAFF', 'Staff home page'],
    ['MEMBER', 'General home'],
    ['ADMIN', 'General home'],
  ])('sends %s away and reads no Manager figure', async (role, destination) => {
    const calls = mockFetch(baseHandlers({ 'GET /api/v1/accounts/me/profile': profile(role) }));
    renderRoutes(routes, { path: '/manager/dashboard' });
    expect(await screen.findByText(destination)).toBeInTheDocument();
    expect(calls.map(call => call.path)).toEqual(['/api/v1/accounts/me/profile']);
  });

  it('asks a signed-out visitor to sign in and loads nothing', async () => {
    const calls = mockFetch(baseHandlers());
    renderRoutes(routes, { path: '/manager/dashboard', session: fakeSession('signed-out') });
    expect(await screen.findByText(/Đăng nhập bằng tài khoản Manager/)).toBeInTheDocument();
    expect(calls).toHaveLength(0);
  });
});

describe('session loss', () => {
  it('drops every block when the shared private-state cleanup runs', async () => {
    await open();
    expect(await main().findByText(/trong đó 1 ca đã hủy/)).toBeInTheDocument();
    act(() => executePrivateStateCleanup());
    await waitFor(() => expect(main().queryByText(/trong đó 1 ca đã hủy/)).not.toBeInTheDocument());
  });
});
