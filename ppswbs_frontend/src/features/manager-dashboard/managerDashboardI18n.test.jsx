import React from 'react';
import { describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, within } from '@testing-library/react';
import { I18nProvider, useLanguage } from '../../shared/i18n/I18nProvider';
import { json, mockFetch, renderRoutes } from '../../test/render';
import { managerDashboardMessages } from './messages';
import { managerDashboardRoutes } from './routes';
import { Change, TrendChart } from './TrendCharts';

vi.mock('../../shared/auth/RouteAccessGuard', () => import('../../test/passThroughGuard'));

function LanguageToggle() {
  const { setLanguage } = useLanguage();
  return <>
    <button type="button" onClick={() => setLanguage('en')}>to-en</button>
    <button type="button" onClick={() => setLanguage('vi')}>to-vi</button>
  </>;
}

const values = { retailRevenue: '120000000', workshopRevenue: '45000000', totalRevenue: '165000000', paidRetailOrderCount: 7, checkedInParticipants: 44, completedSessionCapacity: 80, workshopUtilizationPercent: 55 };
const tv = (o = {}) => ({ retailPlaced: 0, retailPaid: 0, retailCancelled: 0, retailExpired: 0, retailRevenue: '0', workshopBooked: 0, workshopConfirmed: 0, workshopCancelled: 0, workshopAbandoned: 0, placesRegistered: 0, placesCheckedIn: 0, placesNoShow: 0, placesCancelled: 0, workshopRevenue: '0', ...o });

describe('Manager dashboard i18n', () => {
  it('keeps vi and en dictionaries on the same key set with the same placeholders', () => {
    const { vi, en } = managerDashboardMessages;
    expect(Object.keys(en).sort()).toEqual(Object.keys(vi).sort());
    const placeholders = text => [...text.matchAll(/\{(\w+)\}/g)].map(match => match[1]).sort();
    Object.keys(vi).forEach(key => expect(placeholders(en[key]), key).toEqual(placeholders(vi[key])));
  });

  it('switches the change line and the chart wording between vi and en', () => {
    window.localStorage.clear();
    render(<I18nProvider initialLanguage="vi"><LanguageToggle />
      <Change current="6" previous="4" />
      <TrendChart title="t" points={[{ label: '01/11' }]} series={[{ key: 'a', label: 'A', color: '#000', values: [0] }]} />
    </I18nProvider>);
    expect(screen.getByText('▲ 50% so với kỳ trước')).toBeInTheDocument();
    expect(screen.getByText('Chưa có dữ liệu trong kỳ này.')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'to-en' }));
    expect(screen.getByText('▲ 50% vs previous period')).toBeInTheDocument();
    expect(screen.getByText('No data in this period.')).toBeInTheDocument();
    expect(screen.queryByText('Chưa có dữ liệu trong kỳ này.')).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'to-vi' }));
    expect(screen.getByText('▲ 50% so với kỳ trước')).toBeInTheDocument();
  });

  it('shows the whole dashboard in English with English money and number grouping', async () => {
    mockFetch({
      'GET /api/v1/accounts/me/profile': json(200, { role: 'MANAGER', fullName: 'Manager' }),
      'GET /api/v1/executive-reports': json(200, {
        scope: { periodType: 'WEEK', startAt: '2026-10-31T17:00:00Z', endAt: '2026-11-14T03:00:00Z', locationIds: [1] },
        status: 'PARTIAL', totals: values,
        branches: [{ locationId: 1, branchName: 'MLAND Hoan Kiem', values }],
        quality: [{ source: 'WORKSHOP_REVENUE', status: 'UNAVAILABLE', safeReasonCode: 'SOURCE_UNAVAILABLE' }],
        lastUpdatedAt: '2026-11-14T03:00:00Z',
      }),
      'GET /api/v1/executive-reports/trends': json(200, {
        granularity: 'DAY', points: [{ label: '01/11', values: tv({ retailPlaced: 6 }) }],
        current: tv({ retailPlaced: 6, retailPaid: 4, retailCancelled: 1, retailExpired: 1 }), previous: tv({ retailPlaced: 4 }), unavailable: ['ATTENDANCE'],
      }),
    });
    renderRoutes(managerDashboardRoutes, { path: '/manager/dashboard', language: 'en' });

    const kpis = within(await screen.findByRole('region', { name: 'Key figures' }));
    expect(kpis.getByText('Total revenue')).toBeInTheDocument();
    expect(kpis.getByText('165,000,000 ₫')).toBeInTheDocument();
    expect(screen.getByRole('radio', { name: 'Week' })).toBeInTheDocument();
    expect(screen.getByText(/Figures are incomplete/)).toBeInTheDocument();
    expect(screen.getByText(/Workshop revenue: temporarily unavailable/)).toBeInTheDocument();
    expect(await screen.findByText(/Workshop check-in temporarily unavailable/)).toBeInTheDocument();
    expect(screen.getByRole('img', { name: 'Retail revenue by day' })).toBeInTheDocument();
    expect(screen.getByRole('columnheader', { name: 'Paid orders' })).toBeInTheDocument();
    expect(screen.getByText(/1 branch · Vietnam time/)).toBeInTheDocument();
    expect(screen.getByText('Revenue and performance')).toBeInTheDocument();
  });
});
