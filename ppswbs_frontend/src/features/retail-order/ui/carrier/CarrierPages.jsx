import React, { useEffect, useMemo, useRef, useState } from 'react';
import { RequireSession } from '../../../../shared/auth/RequireSession';
import { useEnumLabel, useFormat, useLanguage, useT } from '../../../../shared/i18n/I18nProvider';
import { ErrorNotice, Loading, useResource } from '../../../../shared/ui/components';
import { useRetailApi } from '../../application/useRetailApi';
import { useDisplayTime } from '../orderCode';
import { TrendChart } from '../../../manager-dashboard';
import '../../../../shared/ui/workspace.css';
import './carrier.css';
import { messages as retailMessages } from '../../messages';

/**
 * Simulated GHTK partner portal (Session 2026-11-20): an account with role CARRIER sees the GHTK shipments of every
 * Mland branch and records only the courier's steps. Statuses use GHTK's own words.
 */
// The wording of each status (carrier.status.*) and tab (carrier.tab.*) is in the dictionary, in GHTK's own words (VI/EN switch, Session 2026-11-23).
const TABS = [
  { key: 'RECEIVED', statuses: ['PREPARING'] },
  { key: 'COLLECTING', statuses: ['READY_FOR_CARRIER'] },
  { key: 'DELIVERING', statuses: ['HANDED_TO_CARRIER', 'IN_TRANSIT'] },
  { key: 'DELIVERED', statuses: ['DELIVERED'] },
];
export const carrierTab = status => TABS.find(tab => tab.statuses.includes(status))?.key || 'RECEIVED';

/** The GHTK pages sit in the operations shell like Staff and Manager (Session 2026-11-23): the rail is the shell's. */
function CarrierLayout({ children }) {
  const t = useT(retailMessages);
  return <div className="carrier-main">
    <RequireSession requiredText={t('carrier.signInWithAGhtk')}>{children}</RequireSession>
  </div>;
}

function accessText(error, t) {
  if (error?.status === 403) return t('carrier.thisAccountIsNotAn');
  return null;
}

/** All GHTK shipments, newest first (at most a few hundred in this project). */
function useCarrierShipments() {
  const api = useRetailApi();
  return useResource(async () => {
    const items = [];
    for (let page = 0; page < 10; page += 1) {
      const result = await api.listCarrierShipments({ page, size: 200 }); // eslint-disable-line no-await-in-loop
      items.push(...(result?.items || []));
      if (!result || page + 1 >= (result.totalPages || 0)) break;
    }
    return items;
  }, []);
}

function useBranches() {
  const api = useRetailApi();
  const branches = useResource(() => api.listBranches(), []);
  return useMemo(() => new Map((branches.data?.items || branches.data || []).map(item => [String(item.id), item])), [branches.data]);
}

export function CarrierOrdersPage() {
  return <CarrierLayout><CarrierOrders /></CarrierLayout>;
}

function CarrierOrders() {
  const api = useRetailApi();
  const format = useFormat();
  const t = useT(retailMessages);
  const time = useDisplayTime();
  const statusLabel = useEnumLabel(retailMessages, 'carrier.status');
  const shipments = useCarrierShipments();
  const branches = useBranches();
  const [tab, setTab] = useState('COLLECTING');
  const autoPick = useRef(true);
  const [search, setSearch] = useState('');
  const [busyId, setBusyId] = useState(null);
  const [error, setError] = useState(null);
  const items = shipments.data || [];
  const counts = items.reduce((acc, item) => { const key = carrierTab(item.status); acc[key] = (acc[key] || 0) + 1; return acc; }, {});
  // On entry show the first tab, left to right, that has shipments (Thinh, 2026-11-21); the last tab is history.
  useEffect(() => {
    if (!autoPick.current || !shipments.data) return;
    autoPick.current = false;
    const first = TABS.slice(0, -1).find(item => counts[item.key]);
    if (first) setTab(first.key);
  }, [shipments.data]); // eslint-disable-line react-hooks/exhaustive-deps
  const term = search.trim().toLowerCase();
  const visible = items.filter(item => carrierTab(item.status) === tab && (!term
    || [item.trackingNumber, item.orderCode, item.recipientName, item.recipientPhone].some(value => String(value || '').toLowerCase().includes(term))));

  const act = async (item, action) => {
    // No confirm dialog and no success message: the shipment simply moves to its next tab. Only errors are shown.
    setBusyId(item.id); setError(null);
    try {
      await api.transitionCarrierShipment(item.id, { action, expectedVersion: item.version });
      await shipments.reload();
    } catch (e) { setError(e); } finally { setBusyId(null); }
  };

  return <>
    <p className="carrier-intro">{t('carrier.shipmentsOfEveryMlandBranch')}</p>
    {shipments.loading && !shipments.data && <Loading />}
    {shipments.error && (accessText(shipments.error, t)
      ? <div className="notice error" role="alert"><p>{accessText(shipments.error, t)}</p></div>
      : <ErrorNotice error={shipments.error} onRetry={shipments.reload} />)}
    {shipments.data && <section className="order-tabs-page carrier-orders">
      <div className="order-tabs" role="tablist" aria-label={t('carrier.shipmentStatus')}>{TABS.map(item => <button key={item.key} type="button" role="tab" aria-selected={tab === item.key} className={tab === item.key ? 'active' : ''} onClick={() => setTab(item.key)}>{t(`carrier.tab.${item.key}`)}{counts[item.key] ? <span className="order-tab-count">{counts[item.key]}</span> : null}</button>)}</div>
      <input className="order-search" value={search} onChange={event => setSearch(event.target.value)} placeholder={t('carrier.trackingNumberOrderCodeRecipient')} aria-label={t('carrier.searchShipments')} />
      {error && <ErrorNotice error={error} />}
      {tab === 'RECEIVED' && visible.length > 0 && <p className="order-hint">{t('carrier.theShopIsStillPacking')}</p>}
      {visible.length === 0 && <div className="order-empty"><p>{term ? t('carrier.noMatchingShipment') : t('carrier.noShipmentInThisTab')}</p></div>}
      {visible.map(item => {
        const branch = branches.get(String(item.pickupLocationId));
        const busy = busyId === item.id;
        return <article className="order-card carrier-card" key={item.id}>
          <div className="order-card-top">
            <span className="order-code">{item.trackingNumber}</span>
            <span className="order-branch">{t('carrier.orderItems', { orderCode: item.orderCode, itemQuantity: item.itemQuantity })}</span>
            <span className={`order-pill carrier-pill-${carrierTab(item.status).toLowerCase()}`}>{statusLabel(item.status)}</span>
          </div>
          <div className="carrier-route">
            <div><span>{t('carrier.pickUpAt')}</span><strong>{branch?.name || t('common.branchNumber', { locationId: item.pickupLocationId })}</strong><small>{branch?.address || '—'}</small></div>
            <div><span>{t('carrier.deliverTo')}</span><strong>{item.recipientName || '—'} · {item.recipientPhone || '—'}</strong><small>{[item.recipientAddress, item.wardName, item.districtName, item.provinceName].filter(Boolean).join(', ')}</small></div>
          </div>
          <dl className="order-detail carrier-times">
            <div><dt>{t('carrier.received')}</dt><dd>{time(item.createdAt)}</dd></div>
            <div><dt>{t('carrier.shopPacked')}</dt><dd>{time(item.readyAt)}</dd></div>
            <div><dt>{t('carrier.pickedUp')}</dt><dd>{time(item.pickedUpAt)}</dd></div>
            <div><dt>{t('common.delivered')}</dt><dd>{time(item.deliveredAt)}</dd></div>
          </dl>
          {item.status === 'READY_FOR_CARRIER' && <div className="order-actions"><button type="button" className="button primary" disabled={busy} onClick={() => act(item, 'PICKED_UP')}>{busy ? t('common.saving') : t('carrier.pickedUp')}</button></div>}
          {(item.status === 'HANDED_TO_CARRIER' || item.status === 'IN_TRANSIT') && <div className="order-actions">{/* Prepaid: the customer paid goods and shipping online, nothing to collect. */}<span className="carrier-cod"><strong>{t('carrier.cashOnDeliveryCod0')}</strong></span><button type="button" className="button primary" disabled={busy} onClick={() => act(item, 'DELIVERED')}>{busy ? t('common.saving') : t('common.delivered')}</button></div>}
        </article>;
      })}
    </section>}
  </>;
}

// ---- Thống kê ---------------------------------------------------------------------------------------------------

const PERIODS = ['DAY', 'WEEK', 'MONTH', 'YEAR'];
const VN = 7 * 3_600_000;
const todayVn = () => new Date(Date.now() + VN).toISOString().slice(0, 10);
const vnParts = ms => { const d = new Date(ms + VN); return { y: d.getUTCFullYear(), m: d.getUTCMonth(), d: d.getUTCDate(), h: d.getUTCHours(), dow: d.getUTCDay() }; };
const vnMidnight = (y, m, d) => Date.UTC(y, m, d) - VN;

/** The period in Vietnam time as [start, end) in ms, end capped at now, with its buckets. */
export function carrierPeriod(type, anchor, now = Date.now()) {
  const [y, m, d] = anchor.split('-').map(Number);
  let start;
  let next;
  if (type === 'DAY') { start = vnMidnight(y, m - 1, d); next = vnMidnight(y, m - 1, d + 1); }
  else if (type === 'WEEK') { const dow = (new Date(Date.UTC(y, m - 1, d)).getUTCDay() + 6) % 7; start = vnMidnight(y, m - 1, d - dow); next = vnMidnight(y, m - 1, d - dow + 7); }
  else if (type === 'YEAR') { start = vnMidnight(y, 0, 1); next = vnMidnight(y + 1, 0, 1); }
  else { start = vnMidnight(y, m - 1, 1); next = vnMidnight(y, m, 1); }
  const end = Math.min(next, now);
  const unit = type === 'DAY' ? 'HOUR' : type === 'YEAR' ? 'MONTH' : 'DAY';
  const buckets = [];
  const s = vnParts(start);
  for (let i = 0; ; i += 1) {
    const at = unit === 'HOUR' ? start + i * 3_600_000 : unit === 'MONTH' ? vnMidnight(s.y, s.m + i, 1) : vnMidnight(s.y, s.m, s.d + i);
    if (at >= end) break;
    const p = vnParts(at);
    buckets.push({ at, label: unit === 'HOUR' ? `${String(p.h).padStart(2, '0')}h` : unit === 'MONTH' ? `T${p.m + 1}` : `${String(p.d).padStart(2, '0')}/${String(p.m + 1).padStart(2, '0')}` });
  }
  return { start, end, unit, buckets };
}

const inPeriod = (value, period) => { const t = value ? new Date(value).getTime() : NaN; return t >= period.start && t < period.end; };
const bucketIndex = (value, period) => {
  const t = new Date(value).getTime();
  let index = -1;
  period.buckets.forEach((bucket, i) => { if (t >= bucket.at) index = i; });
  return index;
};

/** A shipment fee as VND; anything that is not a whole amount counts as 0 so one bad row cannot break the page. */
const feeOf = item => (/^\d+$/.test(String(item.shippingCharge ?? '')) ? BigInt(item.shippingCharge) : 0n);

/** What the GHTK statistics page shows for a period. */
export function carrierStats(items, period) {
  const received = items.filter(item => inPeriod(item.createdAt, period));
  const picked = items.filter(item => inPeriod(item.pickedUpAt, period));
  const delivered = items.filter(item => inPeriod(item.deliveredAt, period));
  const hours = delivered.filter(item => item.pickedUpAt).map(item => (new Date(item.deliveredAt) - new Date(item.pickedUpAt)) / 3_600_000);
  const series = key => { const values = period.buckets.map(() => 0); items.forEach(item => { if (inPeriod(item[key], period)) { const i = bucketIndex(item[key], period); if (i >= 0) values[i] += 1; } }); return values; };
  const byBranch = new Map();
  [...received, ...delivered].forEach(item => { if (!byBranch.has(item.pickupLocationId)) byBranch.set(item.pickupLocationId, { received: 0, delivered: 0, fee: 0n }); });
  received.forEach(item => { byBranch.get(item.pickupLocationId).received += 1; });
  delivered.forEach(item => { const row = byBranch.get(item.pickupLocationId); row.delivered += 1; row.fee += feeOf(item); });
  return {
    received: received.length,
    pickedUp: picked.length,
    delivered: delivered.length,
    delivering: items.filter(item => item.status === 'HANDED_TO_CARRIER' || item.status === 'IN_TRANSIT').length,
    waitingPickup: items.filter(item => item.status === 'READY_FOR_CARRIER').length,
    averageHours: hours.length ? hours.reduce((a, b) => a + b, 0) / hours.length : null,
    fees: delivered.reduce((sum, item) => sum + feeOf(item), 0n).toString(),
    trend: { received: series('createdAt'), pickedUp: series('pickedUpAt'), delivered: series('deliveredAt') },
    byBranch: [...byBranch.entries()].map(([locationId, row]) => ({ locationId, ...row, fee: row.fee.toString() })),
  };
}

const NUMBER_LOCALES = { vi: 'vi-VN', en: 'en-GB' };
const duration = (hours, t, language) => {
  if (hours == null) return '—';
  const digits = { maximumFractionDigits: 1 };
  const locale = NUMBER_LOCALES[language] || NUMBER_LOCALES.vi;
  if (hours < 1) return t('carrier.minutes', { minutes: Math.max(1, Math.round(hours * 60)) });
  if (hours < 48) return t('carrier.hours', { value: hours.toLocaleString(locale, digits) });
  return t('carrier.days', { value: (hours / 24).toLocaleString(locale, digits) });
};

export function CarrierStatsPage() {
  return <CarrierLayout><CarrierStats /></CarrierLayout>;
}

function CarrierStats() {
  const format = useFormat();
  const { language } = useLanguage();
  const t = useT(retailMessages);
  const shipments = useCarrierShipments();
  const branches = useBranches();
  const [type, setType] = useState('MONTH');
  const [anchor, setAnchor] = useState(todayVn());
  const period = useMemo(() => carrierPeriod(type, anchor), [type, anchor]);
  const stats = useMemo(() => carrierStats(shipments.data || [], period), [shipments.data, period]);
  const points = period.buckets.map(bucket => ({ label: bucket.label }));
  const count = value => format.number(value);
  const unit = t(`carrier.unit.${period.unit}`);

  return <>
    <p className="carrier-intro">{t('carrier.shipmentsReceivedPickedUpAnd')}</p>
    <section className="md-filters" aria-label={t('carrier.statisticsFilters')}>
      <div className="md-filter-group"><span className="md-filter-label">{t('carrier.period')}</span>
        <div className="md-segment" role="radiogroup" aria-label={t('carrier.periodType')}>{PERIODS.map(id => <button key={id} type="button" role="radio" aria-checked={type === id} className={type === id ? 'active' : ''} onClick={() => setType(id)}>{t(`carrier.period.${id}`)}</button>)}</div>
      </div>
      <label className="md-filter-group"><span className="md-filter-label">{t('common.date')}</span>
        <input type="date" aria-label={t('carrier.chooseADate')} value={anchor} max={todayVn()} onChange={event => { if (event.target.value) { setAnchor(event.target.value); setType('DAY'); } }} />
      </label>
    </section>
    {shipments.loading && !shipments.data && <Loading />}
    {shipments.error && (accessText(shipments.error, t)
      ? <div className="notice error" role="alert"><p>{accessText(shipments.error, t)}</p></div>
      : <ErrorNotice error={shipments.error} onRetry={shipments.reload} />)}
    {shipments.data && <>
      <section className="md-kpis carrier-kpis" aria-label={t('carrier.deliveryFigures')}>
        <Kpi label={t('carrier.shipmentsReceived')} value={count(stats.received)} hint={t('carrier.createdInThePeriod')} />
        <Kpi label={t('carrier.pickedUp')} value={count(stats.pickedUp)} hint={t('carrier.waitingForPickup', { waitingPickup: count(stats.waitingPickup) })} />
        <Kpi label={t('common.delivered')} value={count(stats.delivered)} hint={t('carrier.delivering', { delivering: count(stats.delivering) })} strong />
        <Kpi label={t('carrier.averageDeliveryTime')} value={duration(stats.averageHours, t, language)} hint={t('carrier.fromPickupToDelivery')} />
        <Kpi label={t('carrier.shippingFees')} value={format.money(stats.fees)} hint={t('carrier.ofTheDeliveredShipments')} />
      </section>
      <section className="md-panel carrier-trend">
        <div className="md-panel-head"><h2>{t('carrier.trendBy', { unit })}</h2></div>
        <TrendChart title={t('carrier.shipmentsBy', { unit })} points={points} format={count}
          series={[
            { key: 'received', label: t('carrier.received'), color: '#111827', values: stats.trend.received },
            { key: 'picked', label: t('carrier.pickedUp2'), color: '#f59e0b', values: stats.trend.pickedUp },
            { key: 'delivered', label: t('common.delivered'), color: '#16a34a', values: stats.trend.delivered },
          ]} />
      </section>
      <section className="md-panel" aria-label={t('carrier.byPickupBranch')}>
        <div className="md-panel-head"><h2>{t('carrier.byPickupBranch')}</h2><span className="muted">{t('common.branches', { count: stats.byBranch.length })}</span></div>
        {stats.byBranch.length === 0 ? <p className="md-empty">{t('carrier.noShipmentInThisPeriod')}</p> : <div className="md-table-wrap"><table className="md-table">
          <thead><tr><th scope="col">{t('common.branch')}</th><th scope="col">{t('carrier.received')}</th><th scope="col">{t('common.delivered')}</th><th scope="col">{t('carrier.shippingFees')}</th></tr></thead>
          <tbody>{stats.byBranch.map(row => <tr key={row.locationId}>
            <th scope="row" data-label={t('common.branch')}>{branches.get(String(row.locationId))?.name || t('common.branchNumber', { locationId: row.locationId })}</th>
            <td data-label={t('carrier.received')}>{count(row.received)}</td>
            <td data-label={t('common.delivered')}>{count(row.delivered)}</td>
            <td data-label={t('carrier.shippingFees')}>{format.money(row.fee)}</td>
          </tr>)}</tbody>
        </table></div>}
      </section>
    </>}
  </>;
}

function Kpi({ label, value, hint, strong }) {
  return <article className={`md-kpi${strong ? ' md-kpi-strong' : ''}`}>
    <span className="md-kpi-label">{label}</span>
    <strong className="md-kpi-value">{value}</strong>
    <small>{hint}</small>
  </article>;
}
