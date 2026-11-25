import React, { useState } from 'react';
import { useHttp } from '../../shared/api/useHttp';
import { useEnumLabel, useFormat, useLanguage, useT } from '../../shared/i18n/I18nProvider';
import { Loading, useResource } from '../../shared/ui/components';
import { Change, TrendChart, UNIT_LABEL, rate } from './TrendCharts';
import { managerDashboardMessages } from './messages';
import TopSellers from './TopSellers';

export const PERIODS = ['DAY', 'WEEK', 'MONTH', 'YEAR'];

// Backend codes that have a label; any other code is shown as it came.
const SOURCE_KNOWN = new Set(['RETAIL', 'WORKSHOP_REVENUE', 'WORKSHOP_CAPACITY', 'ATTENDANCE']);
const REASON_KNOWN = new Set(['SOURCE_UNAVAILABLE', 'NON_VND_EXCLUDED', 'WORKSHOP_LOCATION_UNRESOLVED']);

const todayInVietnam = () => new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Ho_Chi_Minh' }).format(new Date());

// Dates and percentages here are pinned to Vietnam time and one decimal, so they take the locale only (not useFormat).
const vnTime = (value, withTime = true, en = false) => {
  if (!value) return '—';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '—';
  return new Intl.DateTimeFormat(en ? 'en-GB' : 'vi-VN', withTime
    ? { dateStyle: 'short', timeStyle: 'short', timeZone: 'Asia/Ho_Chi_Minh' }
    : { dateStyle: 'short', timeZone: 'Asia/Ho_Chi_Minh' }).format(date);
};

/** The inclusive last day of a half-open period, for display only. */
const lastDay = (scope, en = false) => {
  if (!scope?.endAt) return '—';
  const end = new Date(scope.endAt);
  const now = Date.now();
  // A finished period ends at 00:00 of the next day; show the day before. A current period ends now.
  return end.getTime() < now - 1000 ? vnTime(new Date(end.getTime() - 1), false, en) : vnTime(end, true, en);
};

export function percent(value, en = false) {
  if (value === null || value === undefined) return '—';
  return `${Number(value).toLocaleString(en ? 'en-US' : 'vi-VN', { maximumFractionDigits: 1 })}%`;
}

function errorText(error, t) {
  if (!error) return '';
  if ([400, 401, 403, 503].includes(error.status)) return t(`error.${error.status}`);
  return t('error.generic');
}

/** FE-10 revenue block: five read-only KPI cards, period and branch filters, trends, branch rows and data quality. Loads and fails on its own. */
export default function RevenueReport() {
  const http = useHttp();
  const format = useFormat();
  const t = useT(managerDashboardMessages);
  const { language } = useLanguage();
  const en = language === 'en';
  // Every filter applies as soon as it is chosen; reloading the page refreshes (FR-023). No timer.
  const [filter, setFilter] = useState({ period: 'MONTH', anchor: null, locations: [] }); // [] = every assigned branch
  const [branchOptions, setBranchOptions] = useState([]);

  const query = {
    periodType: filter.period,
    anchorDate: filter.anchor || undefined,
    locationId: filter.locations.length ? filter.locations.join(',') : undefined,
  };
  const report = useResource(async () => {
    const data = await http('/api/v1/executive-reports', { query });
    // The default view lists every branch the Manager may filter (Session 2026-11-14).
    if (!filter.locations.length && data?.branches) {
      setBranchOptions(data.branches.map(branch => ({ id: branch.locationId, name: branch.branchName })));
    }
    return data;
  }, [filter]);
  // Trends read the same filters on their own, so a slow trend never holds back the KPI cards.
  const trend = useResource(() => http('/api/v1/executive-reports/trends', { query }), [filter]);
  // Best sellers (Thinh, 2026-11-23) read the same filters on their own as well.
  const topSellers = useResource(() => http('/api/v1/executive-reports/top-sellers', { query }), [filter]);

  const data = report.data;
  const anchor = filter.anchor || todayInVietnam();
  const choosePeriod = period => setFilter(current => ({ ...current, period }));
  // Picking a date shows exactly that day.
  const chooseDate = value => { if (value) setFilter(current => ({ ...current, period: 'DAY', anchor: value })); };
  // One click shows exactly that branch (Session 2026-11-16); clicking the shown branch again goes back to "all".
  const toggleBranch = id => setFilter(current => ({
    ...current, locations: current.locations.length === 1 && current.locations[0] === id ? [] : [id],
  }));

  return <>
    <p className="md-updated">{t('updatedAt')} <strong>{vnTime(data?.lastUpdatedAt, true, en)}</strong></p>

    <section className="md-filters" aria-label={t('filters.label')} aria-busy={report.loading}>
      <div className="md-filter-group">
        <span className="md-filter-label">{t('filters.period')}</span>
        <div className="md-segment" role="radiogroup" aria-label={t('filters.periodType')}>
          {PERIODS.map(id => <button key={id} type="button" role="radio" aria-checked={filter.period === id}
            className={filter.period === id ? 'active' : ''} onClick={() => choosePeriod(id)}>{t(`period.${id}`)}</button>)}
        </div>
      </div>
      <label className="md-filter-group">
        <span className="md-filter-label">{t('filters.date')}</span>
        <input type="date" aria-label={t('filters.pickDate')} value={anchor} max={todayInVietnam()} onChange={event => chooseDate(event.target.value)} />
      </label>
      <div className="md-filter-group md-branches">
        <span className="md-filter-label">{t('filters.branch')}</span>
        <div className="md-chips">
          <button type="button" aria-pressed={filter.locations.length === 0} className={filter.locations.length === 0 ? 'md-chip active' : 'md-chip'}
            onClick={() => setFilter(current => ({ ...current, locations: [] }))}>{t('filters.all')}</button>
          {branchOptions.map(branch => <button key={branch.id} type="button" aria-pressed={filter.locations.includes(branch.id)}
            className={filter.locations.includes(branch.id) ? 'md-chip active' : 'md-chip'} onClick={() => toggleBranch(branch.id)}>{branch.name}</button>)}
        </div>
      </div>
    </section>

    {report.error && <div className="notice error md-notice" role="alert">
      <p>{errorText(report.error, t)}</p>
      {report.error.status !== 401 && report.error.status !== 403 && <button type="button" className="button small outline" onClick={report.reload}>{t('retry')}</button>}
    </div>}
    {report.loading && !data && <Loading />}
    {data && <div className={report.loading ? 'md-reloading' : ''}><Report data={data} format={format} trend={trend} topSellers={topSellers} /></div>}
  </>;
}

function Report({ data, format, trend, topSellers }) {
  const t = useT(managerDashboardMessages);
  const sourceLabel = useEnumLabel(managerDashboardMessages, 'source');
  const reasonLabel = useEnumLabel(managerDashboardMessages, 'reason');
  const { language } = useLanguage();
  const en = language === 'en';
  const totals = data.totals || {};
  const scope = data.scope || {};
  const branchCount = (scope.locationIds || []).length;
  const problems = (data.quality || []).filter(item => item.status === 'UNAVAILABLE' || item.safeReasonCode);
  return <>
    <p className="md-scope">
      <span>{PERIODS.includes(scope.periodType) ? t(`period.${scope.periodType}`) : scope.periodType}</span>
      {vnTime(scope.startAt, false, en)} – {lastDay(scope, en)} · {t(branchCount === 1 ? 'scope.branchOne' : 'scope.branchMany', { count: branchCount })}
    </p>

    {data.status === 'PARTIAL' && <div className="md-quality warn" role="status">
      <strong>{t('quality.partialTitle')}</strong> {t('quality.partialBody')}
      <ul>{problems.map(item => <li key={item.source}>{SOURCE_KNOWN.has(item.source) ? sourceLabel(item.source) : item.source}: {REASON_KNOWN.has(item.safeReasonCode) ? reasonLabel(item.safeReasonCode) : t('reason.SOURCE_UNAVAILABLE')}</li>)}</ul>
    </div>}
    {data.status === 'EMPTY' && <div className="md-quality" role="status">
      <strong>{t('quality.emptyTitle')}</strong> {t('quality.emptyBody')}
    </div>}

    <section className="md-kpis" aria-label={t('kpi.label')}>
      <Kpi tone="amber" label={t('kpi.retail')} value={format.money(totals.retailRevenue)} hint={t('kpi.retailHint')} missing={totals.retailRevenue == null} />
      <Kpi tone="blue" label={t('kpi.workshop')} value={format.money(totals.workshopRevenue)} hint={t('kpi.workshopHint')} missing={totals.workshopRevenue == null} />
      <Kpi label={t('kpi.total')} value={format.money(totals.totalRevenue)} hint={t('kpi.totalHint')} missing={totals.totalRevenue == null} strong />
      <Kpi tone="green" label={t('kpi.paidOrders')} value={totals.paidRetailOrderCount == null ? '—' : format.number(totals.paidRetailOrderCount)} hint={t('kpi.paidOrdersHint')} missing={totals.paidRetailOrderCount == null} />
      <Kpi tone="violet" label={t('kpi.utilization')} value={percent(totals.workshopUtilizationPercent, en)}
        hint={totals.completedSessionCapacity ? t('kpi.utilizationHint', { checkedIn: format.number(totals.checkedInParticipants), capacity: format.number(totals.completedSessionCapacity) }) : t('kpi.noSession')}
        missing={totals.completedSessionCapacity == null} meter={totals.workshopUtilizationPercent} />
    </section>

    <Trends trend={trend} format={format} />

    <TopSellers resource={topSellers} format={format} />

    <BranchTable branches={data.branches || []} total={totals.totalRevenue} format={format} />
  </>;
}

const C = { dark: '#111827', green: '#16a34a', red: '#dc2626', amber: '#f59e0b', gray: '#9ca3af', blue: '#2563eb' };

/** FE-10 trends (Session 2026-11-16): retail and workshop, by hour, day or month, against the previous period. */
function Trends({ trend, format }) {
  const t = useT(managerDashboardMessages);
  const trendSourceLabel = useEnumLabel(managerDashboardMessages, 'trendSource');
  const { language } = useLanguage();
  const en = language === 'en';
  const data = trend.data;
  if (trend.error) return <section className="md-panel md-trends" aria-label={t('trends.title')}>
    <div className="md-panel-head"><h2>{t('trends.title')}</h2></div>
    <div className="notice error md-notice" role="alert"><p>{t('trends.error')}</p>
      <button type="button" className="button small outline" onClick={trend.reload}>{t('retry')}</button></div>
  </section>;
  if (!data) return <section className="md-panel md-trends" aria-label={t('trends.title')} aria-busy="true">
    <div className="md-panel-head"><h2>{t('trends.title')}</h2></div><Loading />
  </section>;

  const unit = t(UNIT_LABEL[data.granularity] || 'unit.DAY');
  const points = data.points || [];
  const col = key => points.map(p => p.values?.[key] ?? null);
  const cur = data.current || {};
  const prev = data.previous || {};
  const money = value => format.money(value);
  const count = value => format.number(value);
  const pct = value => percent(value, en);
  const retailDropped = v => v.retailCancelled == null ? null : Number(v.retailCancelled) + Number(v.retailExpired);
  const workshopDropped = v => v.workshopCancelled == null ? null : Number(v.workshopCancelled) + Number(v.workshopAbandoned);
  const attended = v => v.placesCheckedIn == null ? null : Number(v.placesCheckedIn) + Number(v.placesNoShow);

  const per = t('trends.per', { unit });
  return <section className={`md-trends${trend.loading ? ' md-reloading' : ''}`} aria-label={t('trends.title')}>
    {(data.unavailable || []).length > 0 && <div className="md-quality warn" role="status">
      <strong>{t('trends.missing')}</strong> {data.unavailable.map(s => trendSourceLabel(s)).join(', ')} {t('trends.unavailable')}
    </div>}

    <div className="md-panel md-panel-tone tone-amber">
      <div className="md-panel-head"><h2>{t('trends.retailTitle')}</h2><span className="muted">{per}</span></div>
      <div className="md-trend-stats">
        <Stat label={t('trends.ordersPlaced')} value={cur.retailPlaced} show={count}><Change current={cur.retailPlaced} previous={prev.retailPlaced} /></Stat>
        <Stat label={t('trends.ordersPaid')} value={cur.retailPaid} show={count}><Change current={cur.retailPaid} previous={prev.retailPaid} /></Stat>
        <Stat label={t('trends.droppedOrders')} value={retailDropped(cur)} show={count}
          note={t('trends.rate', { value: pct(rate(retailDropped(cur), cur.retailPlaced)) })}><Change current={retailDropped(cur)} previous={retailDropped(prev)} inverse /></Stat>
        <Stat label={t('trends.retailRevenue')} value={cur.retailRevenue} show={money}><Change current={cur.retailRevenue} previous={prev.retailRevenue} /></Stat>
      </div>
      <div className="md-chart-grid">
        <TrendChart title={t('trends.retailRevenueBy', { unit })} kind="bar" points={points} format={money}
          series={[{ key: 'r', label: t('trends.revenue'), color: C.amber, values: col('retailRevenue') }]} />
        <TrendChart title={t('trends.ordersBy', { unit })} points={points} format={count}
          series={[
            { key: 'placed', label: t('trends.placed'), color: C.dark, values: col('retailPlaced') },
            { key: 'paid', label: t('trends.paid'), color: C.green, values: col('retailPaid') },
            { key: 'cancelled', label: t('trends.cancelled'), color: C.red, values: col('retailCancelled') },
            { key: 'expired', label: t('trends.paymentExpired'), color: C.amber, values: col('retailExpired') },
          ]} />
      </div>
    </div>

    <div className="md-panel md-panel-tone tone-blue">
      <div className="md-panel-head"><h2>{t('trends.workshopTitle')}</h2><span className="muted">{per}</span></div>
      <div className="md-trend-stats">
        <Stat label={t('trends.ticketsBooked')} value={cur.workshopBooked} show={count}
          note={t('trends.ticketsDropped', { count: count(workshopDropped(cur) ?? 0) })}><Change current={cur.workshopBooked} previous={prev.workshopBooked} /></Stat>
        <Stat label={t('trends.checkIns')} value={cur.placesCheckedIn} show={count}
          note={t('trends.checkInsOf', { count: count(cur.placesRegistered ?? 0) })}><Change current={cur.placesCheckedIn} previous={prev.placesCheckedIn} /></Stat>
        <Stat label={t('trends.noShowRate')} value={rate(cur.placesNoShow, attended(cur))} show={pct}
          note={t('trends.noShows', { count: count(cur.placesNoShow ?? 0) })}><Change current={rate(cur.placesNoShow, attended(cur))} previous={rate(prev.placesNoShow, attended(prev))} inverse /></Stat>
        <Stat label={t('trends.workshopRevenue')} value={cur.workshopRevenue} show={money}><Change current={cur.workshopRevenue} previous={prev.workshopRevenue} /></Stat>
      </div>
      <div className="md-chart-grid">
        <TrendChart title={t('trends.workshopRevenueBy', { unit })} kind="bar" points={points} format={money}
          empty={t('trends.workshopRevenueEmpty')}
          series={[{ key: 'w', label: t('trends.revenue'), color: C.dark, values: col('workshopRevenue') }]} />
        <TrendChart title={t('trends.participantsBy', { unit })} points={points} format={count}
          series={[
            { key: 'reg', label: t('trends.registered'), color: C.blue, values: col('placesRegistered') },
            { key: 'in', label: t('trends.checkInSeries'), color: C.green, values: col('placesCheckedIn') },
            { key: 'no', label: t('trends.noShow'), color: C.amber, values: col('placesNoShow') },
            { key: 'cx', label: t('trends.ticketCancelled'), color: C.red, values: col('placesCancelled') },
          ]} />
        <TrendChart title={t('trends.ticketsBy', { unit })} points={points} format={count}
          series={[
            { key: 'booked', label: t('trends.booked'), color: C.dark, values: col('workshopBooked') },
            { key: 'ok', label: t('trends.paid'), color: C.green, values: col('workshopConfirmed') },
            { key: 'cx', label: t('trends.cancelled'), color: C.red, values: col('workshopCancelled') },
            { key: 'ab', label: t('trends.abandoned'), color: C.amber, values: col('workshopAbandoned') },
          ]} />
      </div>
      <p className="md-footnote">{t('trends.footnote')}</p>
    </div>
  </section>;
}

function Stat({ label, value, show, note, children }) {
  return <article className="md-stat">
    <span className="md-kpi-label">{label}</span>
    <strong>{value == null ? '—' : show(value)}</strong>
    {children}
    {note && value != null && <small className="md-stat-note">{note}</small>}
  </article>;
}

function Kpi({ label, value, hint, missing, strong, meter, tone }) {
  const t = useT(managerDashboardMessages);
  return <article className={`md-kpi${strong ? ' md-kpi-strong' : ''}${tone ? ` tone-${tone}` : ''}`}>
    <span className="md-kpi-label">{label}</span>
    <strong className="md-kpi-value">{missing ? '—' : value}</strong>
    {meter != null && <span className="md-meter" aria-hidden="true"><span style={{ width: `${Math.min(100, meter)}%` }} /></span>}
    <small>{missing ? t('kpi.noData') : hint}</small>
  </article>;
}

function BranchTable({ branches, total, format }) {
  const t = useT(managerDashboardMessages);
  const { language } = useLanguage();
  const en = language === 'en';
  const totalNumber = Number(total || 0);
  return <section className="md-panel md-panel-tone tone-dark" aria-label={t('branch.section')}>
    <div className="md-panel-head"><h2>{t('branch.title')}</h2><span className="muted">{t(branches.length === 1 ? 'branch.countOne' : 'branch.countMany', { count: branches.length })}</span></div>
    {branches.length === 0 ? <p className="md-empty">{t('branch.none')}</p> :
      <div className="md-table-wrap"><table className="md-table">
        <thead><tr>
          <th scope="col">{t('branch.col.branch')}</th><th scope="col">{t('branch.col.retail')}</th><th scope="col">{t('branch.col.workshop')}</th>
          <th scope="col">{t('branch.col.total')}</th><th scope="col">{t('branch.col.paidOrders')}</th><th scope="col">{t('branch.col.utilization')}</th>
        </tr></thead>
        <tbody>{branches.map(branch => {
          const v = branch.values || {};
          const share = totalNumber > 0 && v.totalRevenue != null ? Number(v.totalRevenue) * 100 / totalNumber : null;
          return <tr key={branch.locationId}>
            <th scope="row" data-label={t('branch.col.branch')}>{branch.branchName}</th>
            <td data-label={t('branch.col.retail')}>{format.money(v.retailRevenue)}</td>
            <td data-label={t('branch.col.workshop')}>{format.money(v.workshopRevenue)}</td>
            <td data-label={t('branch.col.total')}><strong>{format.money(v.totalRevenue)}</strong>
              {share != null && <span className="md-share" title={t('branch.share', { percent: percent(share, en) })}><span style={{ width: `${share}%` }} /></span>}</td>
            <td data-label={t('branch.col.paidOrders')}>{v.paidRetailOrderCount == null ? '—' : format.number(v.paidRetailOrderCount)}</td>
            <td data-label={t('branch.col.utilization')}>{v.workshopUtilizationPercent == null ? <span className="muted">{v.completedSessionCapacity === 0 ? t('kpi.noSession') : '—'}</span>
              : <span className="md-util"><span className="md-meter small"><span style={{ width: `${Math.min(100, v.workshopUtilizationPercent)}%` }} /></span>{percent(v.workshopUtilizationPercent, en)}</span>}</td>
          </tr>;
        })}</tbody>
      </table></div>}
  </section>;
}
