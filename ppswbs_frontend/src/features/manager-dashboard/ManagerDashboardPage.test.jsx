import { describe, expect, it, vi } from 'vitest';
import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import { json, mockFetch, renderRoutes } from '../../test/render';
import { managerDashboardRoutes } from './routes';

const values = (retail, workshop, orders, checkedIn, capacity) => ({
  retailRevenue: retail, workshopRevenue: workshop,
  totalRevenue: retail == null || workshop == null ? null : String(Number(retail) + Number(workshop)),
  paidRetailOrderCount: orders, checkedInParticipants: checkedIn, completedSessionCapacity: capacity,
  workshopUtilizationPercent: capacity ? checkedIn * 100 / capacity : null,
});

const report = (overrides = {}) => ({
  scope: { periodType: 'MONTH', startAt: '2026-10-31T17:00:00Z', endAt: '2026-11-14T03:00:00Z', timezone: 'Asia/Ho_Chi_Minh', locationIds: [1, 2] },
  status: 'COMPLETE', currency: 'VND',
  totals: values('120761634', '45000000', 7, 44, 80),
  branches: [
    { locationId: 1, branchName: 'MLAND Hoan Kiem', currency: 'VND', values: values('120000000', '45000000', 6, 28, 50) },
    { locationId: 2, branchName: 'MLAND Cau Giay', currency: 'VND', values: values('761634', '0', 1, 16, 30) },
  ],
  quality: [
    { source: 'RETAIL', status: 'COMPLETE', asOf: '2026-11-14T03:00:00Z', safeReasonCode: null },
    { source: 'WORKSHOP_REVENUE', status: 'COMPLETE', asOf: '2026-11-14T03:00:00Z', safeReasonCode: null },
    { source: 'WORKSHOP_CAPACITY', status: 'COMPLETE', asOf: '2026-11-14T03:00:00Z', safeReasonCode: null },
    { source: 'ATTENDANCE', status: 'COMPLETE', asOf: '2026-11-14T03:00:00Z', safeReasonCode: null },
  ],
  lastUpdatedAt: '2026-11-14T03:00:00Z', developmentDemo: false,
  ...overrides,
});

const tv = (o = {}) => ({
  retailPlaced: 0, retailPaid: 0, retailCancelled: 0, retailExpired: 0, retailRevenue: '0',
  workshopBooked: 0, workshopConfirmed: 0, workshopCancelled: 0, workshopAbandoned: 0,
  placesRegistered: 0, placesCheckedIn: 0, placesNoShow: 0, placesCancelled: 0, workshopRevenue: '0', ...o,
});

const trend = (overrides = {}) => ({
  scope: { periodType: 'MONTH', startAt: '2026-10-31T17:00:00Z', endAt: '2026-11-14T03:00:00Z', timezone: 'Asia/Ho_Chi_Minh', locationIds: [1, 2] },
  granularity: 'DAY',
  points: [
    { startAt: '2026-10-31T17:00:00Z', label: '01/11', values: tv({ retailPlaced: 6, retailPaid: 4, retailCancelled: 1, retailExpired: 1, retailRevenue: '900000' }) },
    { startAt: '2026-11-01T17:00:00Z', label: '02/11', values: tv({ placesRegistered: 10, placesCheckedIn: 8, placesNoShow: 2, workshopBooked: 4 }) },
  ],
  current: tv({ retailPlaced: 6, retailPaid: 4, retailCancelled: 1, retailExpired: 1, retailRevenue: '900000',
    placesRegistered: 10, placesCheckedIn: 8, placesNoShow: 2, workshopBooked: 4 }),
  previous: tv({ retailPlaced: 4, retailPaid: 4, placesCheckedIn: 8, placesNoShow: 0 }),
  previousStartAt: '2026-09-30T17:00:00Z', previousEndAt: '2026-10-14T03:00:00Z',
  unavailable: [], lastUpdatedAt: '2026-11-14T03:00:00Z',
  ...overrides,
});

const routes = (reportBody = report(), trendBody = trend()) => ({
  'GET /api/v1/executive-reports': json(200, reportBody),
  'GET /api/v1/executive-reports/trends': json(200, trendBody),
});

// The sidebar also asks for the profile; count only the dashboard reads.
const reads = calls => calls.filter(call => call.path === '/api/v1/executive-reports');
const kpis = () => within(screen.getByRole('region', { name: 'Chỉ số chính' }));

describe('FE-10 Manager dashboard', () => {
  it('loads the current month for every assigned branch and shows exactly five KPI cards', async () => {
    const calls = mockFetch(routes());
    renderRoutes(managerDashboardRoutes, { path: '/manager/dashboard' });

    expect(await screen.findByRole('heading', { name: 'Dashboard điều hành' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Quản lý đánh giá' })).toHaveAttribute('href', '/review-moderation');
    expect(await kpis().findByText('Tổng doanh thu')).toBeInTheDocument();
    expect(kpis().getAllByRole('article')).toHaveLength(5);
    expect(kpis().getByText('55%')).toBeInTheDocument();
    expect(screen.getByRole('rowheader', { name: 'MLAND Cau Giay' })).toBeInTheDocument();
    expect(reads(calls)[0].query).toEqual({ periodType: 'MONTH' });
    // No custom date range and no operational navigation cards (FR-005, FR-016).
    expect(screen.queryByLabelText(/Từ ngày|Đến ngày/)).not.toBeInTheDocument();
  });

  it('applies the period, the branch and a picked day as soon as they are chosen', async () => {
    const calls = mockFetch(routes());
    renderRoutes(managerDashboardRoutes, { path: '/manager/dashboard' });
    await screen.findByRole('rowheader', { name: 'MLAND Hoan Kiem' });

    fireEvent.click(screen.getByRole('radio', { name: 'Tuần' }));
    await waitFor(() => expect(reads(calls)).toHaveLength(2));
    expect(reads(calls)[1].query.periodType).toBe('WEEK');

    fireEvent.click(screen.getByRole('button', { name: 'MLAND Cau Giay' }));
    await waitFor(() => expect(reads(calls)).toHaveLength(3));
    expect(reads(calls)[2].query.locationId).toBe('2');

    fireEvent.change(screen.getByLabelText('Chọn ngày'), { target: { value: '2026-11-10' } });
    await waitFor(() => expect(reads(calls)).toHaveLength(4));
    expect(reads(calls)[3].query).toMatchObject({ periodType: 'DAY', anchorDate: '2026-11-10', locationId: '2' });
    expect(screen.queryByRole('button', { name: 'Áp dụng' })).not.toBeInTheDocument();
  });

  it('shows unknown values as a dash with a partial-data notice, never as zero', async () => {
    mockFetch({ 'GET /api/v1/executive-reports': json(200, report({
      status: 'PARTIAL',
      totals: values('500000', null, 1, 0, 0),
      branches: [{ locationId: 1, branchName: 'MLAND Hoan Kiem', currency: 'VND', values: values('500000', null, 1, 0, 0) }],
      quality: [
        { source: 'RETAIL', status: 'COMPLETE', asOf: 'x', safeReasonCode: null },
        { source: 'WORKSHOP_REVENUE', status: 'UNAVAILABLE', asOf: 'x', safeReasonCode: 'SOURCE_UNAVAILABLE' },
      ],
    })) });
    renderRoutes(managerDashboardRoutes, { path: '/manager/dashboard' });

    expect(await screen.findByText(/Số liệu chưa đầy đủ/)).toBeInTheDocument();
    expect(screen.getByText(/Doanh thu workshop: tạm thời không đọc được/)).toBeInTheDocument();
    expect(kpis().getAllByText('Chưa có dữ liệu').length).toBeGreaterThanOrEqual(2);
  });

  it('explains a refused scope without showing any revenue', async () => {
    mockFetch({ 'GET /api/v1/executive-reports': json(403, { code: 'MANAGER_REPORTING_FORBIDDEN', message: 'no' }) });
    renderRoutes(managerDashboardRoutes, { path: '/manager/dashboard' });

    expect(await screen.findByText(/Chỉ Manager được phân công chi nhánh/)).toBeInTheDocument();
    expect(screen.queryByRole('region', { name: 'Chỉ số chính' })).not.toBeInTheDocument();
  });

  it('never reloads on a timer and has no refresh button (F5 refreshes)', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    const calls = mockFetch(routes());
    renderRoutes(managerDashboardRoutes, { path: '/manager/dashboard' });
    await screen.findByRole('rowheader', { name: 'MLAND Hoan Kiem' });

    vi.advanceTimersByTime(10 * 60 * 1000);
    expect(reads(calls)).toHaveLength(1);
    expect(screen.queryByRole('button', { name: 'Làm mới' })).not.toBeInTheDocument();
    vi.useRealTimers();
  });

  it('shows retail and workshop trends against the previous period with the same filters', async () => {
    const calls = mockFetch(routes());
    renderRoutes(managerDashboardRoutes, { path: '/manager/dashboard' });

    const trends = within(await screen.findByRole('region', { name: 'Xu hướng' }));
    expect(await trends.findByRole('img', { name: 'Doanh thu bán lẻ theo ngày' })).toBeInTheDocument();
    expect(trends.getByRole('img', { name: 'Đơn đặt và đơn bị hủy theo ngày' })).toBeInTheDocument();
    expect(trends.getByRole('img', { name: 'Người tham gia theo ngày diễn ra buổi học' })).toBeInTheDocument();
    expect(trends.getByRole('img', { name: 'Vé đặt và vé bị hủy theo ngày đặt chỗ' })).toBeInTheDocument();
    // 6 orders against 4 the period before; 2 of 6 dropped; 2 no-shows of 10 who were due.
    expect(trends.getByText('▲ 50% so với kỳ trước')).toBeInTheDocument();
    expect(trends.getByText('Tỷ lệ 33,3%')).toBeInTheDocument();
    expect(trends.getByText('20%')).toBeInTheDocument();
    expect(calls.filter(c => c.path === '/api/v1/executive-reports/trends')[0].query).toEqual({ periodType: 'MONTH' });

    fireEvent.click(screen.getByRole('radio', { name: 'Năm' }));
    await waitFor(() => expect(calls.filter(c => c.path === '/api/v1/executive-reports/trends')).toHaveLength(2));
    expect(calls.filter(c => c.path === '/api/v1/executive-reports/trends')[1].query.periodType).toBe('YEAR');
  });

  it('names a trend source that could not be read and draws it as unknown', async () => {
    mockFetch(routes(report(), trend({
      unavailable: ['ATTENDANCE'],
      current: tv({ placesRegistered: null, placesCheckedIn: null, placesNoShow: null, placesCancelled: null }),
    })));
    renderRoutes(managerDashboardRoutes, { path: '/manager/dashboard' });

    expect(await screen.findByText(/Check-in workshop tạm thời không đọc được/)).toBeInTheDocument();
    const stat = screen.getByText('Lượt check-in').closest('article');
    expect(within(stat).getByText('—', { selector: 'strong' })).toBeInTheDocument();
  });
});
