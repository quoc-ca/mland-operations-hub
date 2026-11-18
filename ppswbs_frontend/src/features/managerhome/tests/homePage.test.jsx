import React from 'react';
import { describe, expect, it } from 'vitest';
import { act, fireEvent, screen, waitFor, within } from '@testing-library/react';
import { executePrivateStateCleanup } from '../../../shared/auth/privateStateCleanup';
import { fakeSession, json, mockFetch, renderRoutes } from '../../../test/render';
import { baseHandlers, profile, routes } from './helpers';

const open = async (handlers, options = {}) => {
  const calls = mockFetch(baseHandlers(handlers));
  renderRoutes(routes, { path: '/manager/home', ...options });
  await screen.findByRole('main');
  return calls;
};
const main = () => within(screen.getByRole('main'));
const sectionOf = async title => (await main().findByRole('heading', { name: title })).closest('section');

describe('waiting work', () => {
  it('shows the three wave-1 counts, each linking to its own list', async () => {
    await open();
    expect(await main().findByRole('link', { name: /Thanh toán về trễ: 3/ })).toHaveAttribute('href', '/operations/late-payments');
    expect(main().getByRole('link', { name: /Yêu cầu đổi vé chờ duyệt: 2/ })).toHaveAttribute('href', '/settlement/change-requests?status=PENDING');
    expect(main().getByRole('link', { name: /Thiết kế khách chờ duyệt: 1/ })).toHaveAttribute('href', '/design-review-requests');
  });

  it('shows the refund and OTA cards from the owners counts, each opening the list that returns exactly that set', async () => {
    await open();
    expect(await main().findByRole('link', { name: /Hàng đợi hoàn tiền: 4/ })).toHaveAttribute('href', '/settlement/refunds?status=WAITING');
    expect(main().getByRole('link', { name: /Đối soát OTA cần xử lý: 2/ })).toHaveAttribute('href', '/ota-connectivity/reconciliation?status=NEEDS_ATTENTION');
  });

  it('shows the cash-close card, opening the confirmation view of the cash-close screen', async () => {
    await open();
    expect(await main().findByRole('link', { name: /Chênh lệch chốt sổ tiền mặt: 1/ })).toHaveAttribute('href', '/settlement/cash-close?view=confirmation');
  });

  it('hides a refund or OTA card whose count cannot be trusted instead of showing 0', async () => {
    await open({ 'GET /api/v1/workshop-refund-requests/waiting-count': json(200, {}) });
    const card = (await main().findByText('Hàng đợi hoàn tiền')).closest('li');
    expect(await within(card).findByText('Không tải được dữ liệu này.')).toBeInTheDocument();
    expect(within(card).queryByText('0')).not.toBeInTheDocument();
  });

  it('shows no card at all for a queue without a count', async () => {
    await open();
    await main().findByRole('link', { name: /Thanh toán về trễ/ });
    ['Đánh giá chờ kiểm duyệt'].forEach(name => {
      expect(main().queryByText(new RegExp(name))).not.toBeInTheDocument();
    });
    expect(main().queryByText('Không tải được dữ liệu này.')).not.toBeInTheDocument();
  });

  it('dims and groups a zero card without claiming the whole page is empty', async () => {
    await open({ 'GET /api/v1/late-payment-reviews': json(200, []) });
    expect(await main().findByRole('heading', { name: 'Không có việc tồn đọng' })).toBeInTheDocument();
    expect(main().queryByText('Không có việc tồn đọng trong các hàng đợi đang hiển thị')).not.toBeInTheDocument();
  });

  it('shows the single no-work state, limited to the shown queues, when every shown queue is zero', async () => {
    await open({
      'GET /api/v1/late-payment-reviews': json(200, []),
      'GET /api/v1/workshop-change-requests': json(200, []),
      'GET /api/v1/design-review-requests': json(200, { items: [] }),
      'GET /api/v1/workshop-refund-requests/waiting-count': json(200, { waiting: 0 }),
      'GET /api/v1/ota-reconciliation-work-items/needing-attention-count': json(200, { waiting: 0 }),
      'GET /api/v1/workshop-cash-closes/awaiting-confirmation-count': json(200, { waiting: 0 }),
    });
    expect(await main().findByText('Không có việc tồn đọng trong các hàng đợi đang hiển thị')).toBeInTheDocument();
  });

  it('lets one failing read fail only its own card, with its own retry and never a 0', async () => {
    let fail = true;
    await open({ 'GET /api/v1/workshop-change-requests': () => (fail ? json(500, { code: 'X' }) : json(200, [1, 2])) });
    expect(await main().findByRole('link', { name: /Thanh toán về trễ: 3/ })).toBeInTheDocument();
    const card = (await main().findByText('Yêu cầu đổi vé chờ duyệt')).closest('li');
    expect(await within(card).findByText('Không tải được dữ liệu này.')).toBeInTheDocument();
    expect(within(card).queryByText('0')).not.toBeInTheDocument();
    fail = false;
    fireEvent.click(within(card).getByRole('button', { name: 'Thử lại' }));
    expect(await main().findByRole('link', { name: /Yêu cầu đổi vé chờ duyệt: 2/ })).toBeInTheDocument();
  });

  it('shows no number when the read is refused with 403', async () => {
    await open({ 'GET /api/v1/design-review-requests': json(403, { code: 'FORBIDDEN' }) });
    const card = (await main().findByText('Thiết kế khách chờ duyệt')).closest('li');
    expect(await within(card).findByText('Không tải được dữ liệu này.')).toBeInTheDocument();
    expect(within(card).queryByRole('link')).not.toBeInTheDocument();
  });
});

describe('today strip', () => {
  it('counts cancelled sessions in the total and says how many were cancelled', async () => {
    await open();
    expect(await main().findByText(/trong đó 1 ca đã hủy/)).toBeInTheDocument();
    expect(within(await sectionOf('Vận hành hôm nay')).getByText('2')).toBeInTheDocument();
  });

  it('reads again only the strip when a branch is chosen, leaving the queue cards alone', async () => {
    const calls = await open();
    await main().findByText(/trong đó 1 ca đã hủy/);
    const before = calls.filter(call => call.path === '/api/v1/late-payment-reviews').length;
    fireEvent.change(await main().findByLabelText('Chi nhánh'), { target: { value: 'l2' } });
    await waitFor(() => expect(calls.some(call => call.path === '/api/v1/workshop-day-summaries' && call.query.locationId === 'l2')).toBe(true));
    expect(calls.filter(call => call.path === '/api/v1/late-payment-reviews')).toHaveLength(before);
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

describe('revenue card', () => {
  it('shows the dashboard figure in VND and opens the dashboard', async () => {
    await open();
    expect(await main().findByText(/12\.500\.000/)).toBeInTheDocument();
    fireEvent.click(main().getByRole('link', { name: 'Mở dashboard điều hành' }));
    expect(await screen.findByText('Dashboard page')).toBeInTheDocument();
  });

  it.each([
    ['PARTIAL', 'Dữ liệu doanh thu mới có một phần nên chưa hiển thị con số.'],
    ['UNAVAILABLE', 'Doanh thu tạm thời không đọc được.'],
    ['EMPTY', 'Chưa có doanh thu đã xác minh trong tháng này.'],
  ])('shows a notice and no number when the report is %s', async (status, text) => {
    await open({ 'GET /api/v1/executive-reports': json(200, { status, totals: { totalRevenue: '99' } }) });
    expect(await main().findByText(text)).toBeInTheDocument();
    expect(main().queryByText(/99/)).not.toBeInTheDocument();
  });

  it('shows the card as unavailable on HTTP 503 without disturbing the other sections', async () => {
    await open({ 'GET /api/v1/executive-reports': json(503, { code: 'UNAVAILABLE' }) });
    expect(await main().findByText('Doanh thu tạm thời không đọc được.')).toBeInTheDocument();
    expect(await main().findByRole('link', { name: /Thanh toán về trễ: 3/ })).toBeInTheDocument();
  });

  it('carries the demo marker', async () => {
    await open({ 'GET /api/v1/executive-reports': json(200, { status: 'COMPLETE', totals: { totalRevenue: '1' }, developmentDemo: true }) });
    expect(await main().findByText(/Dữ liệu demo/)).toBeInTheDocument();
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
    expect(await main().findByRole('link', { name: /Thanh toán về trễ: 3/ })).toBeInTheDocument();
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

describe('shortcuts', () => {
  it.each([
    ['Gói workshop', '/operations/packages'],
    ['Hiệu lực gói theo chi nhánh', '/operations/package-availability'],
    ['Chi nhánh và khung giờ', '/operations/admin'],
    ['Bảng điều phối ca', '/operations/sessions'],
  ])('%s opens %s', async (name, href) => {
    await open();
    expect(await main().findByRole('link', { name })).toHaveAttribute('href', href);
  });
});

describe('page behaviour', () => {
  it('puts the sections in the order of the spec', async () => {
    await open();
    await main().findByRole('link', { name: /Thanh toán về trễ/ });
    const headings = main().getAllByRole('heading', { level: 2 }).map(heading => heading.textContent);
    expect(headings).toEqual(['Việc đang chờ', 'Vận hành hôm nay', 'Doanh thu tháng này', 'Việc gần đây cần chú ý', 'Lối tắt']);
  });

  it('reads on open and on explicit refresh only, and shows the update time in Vietnam time', async () => {
    const calls = await open();
    await main().findByText(/Cập nhật lúc .* \(giờ Việt Nam\)/);
    const opened = calls.length;
    await new Promise(resolve => setTimeout(resolve, 80));
    expect(calls).toHaveLength(opened);
    fireEvent.click(main().getByRole('button', { name: 'Làm mới' }));
    await waitFor(() => expect(calls.length).toBeGreaterThan(opened));
  });

  it('sends only GET requests', async () => {
    const calls = await open();
    await main().findByText(/Cập nhật lúc/);
    fireEvent.click(main().getByRole('button', { name: 'Làm mới' }));
    await waitFor(() => expect(calls.length).toBeGreaterThan(10));
    expect(calls.every(call => call.method === 'GET')).toBe(true);
  });

  it('renders in English when the language is English', async () => {
    await open({}, { language: 'en' });
    expect(await main().findByRole('heading', { name: 'Waiting for you' })).toBeInTheDocument();
  });
});

describe('access', () => {
  it.each([
    ['STAFF', 'Staff home page'],
    ['MEMBER', 'General home'],
    ['ADMIN', 'General home'],
  ])('sends %s away and reads no Manager figure', async (role, destination) => {
    const calls = mockFetch(baseHandlers({ 'GET /api/v1/accounts/me/profile': profile(role) }));
    renderRoutes(routes, { path: '/manager/home' });
    expect(await screen.findByText(destination)).toBeInTheDocument();
    expect(calls.map(call => call.path)).toEqual(['/api/v1/accounts/me/profile']);
  });

  it('asks a signed-out visitor to sign in and loads nothing', async () => {
    const calls = mockFetch(baseHandlers());
    renderRoutes(routes, { path: '/manager/home', session: fakeSession('signed-out') });
    expect(await screen.findByText(/Đăng nhập bằng tài khoản Manager/)).toBeInTheDocument();
    expect(calls).toHaveLength(0);
  });
});

describe('session loss', () => {
  it('drops every figure when the shared private-state cleanup runs', async () => {
    await open();
    expect(await main().findByRole('link', { name: /Thanh toán về trễ: 3/ })).toBeInTheDocument();
    act(() => executePrivateStateCleanup());
    await waitFor(() => expect(main().queryByRole('link', { name: /Thanh toán về trễ/ })).not.toBeInTheDocument());
    expect(document.body.textContent).not.toMatch(/12\.500\.000/);
  });
});
