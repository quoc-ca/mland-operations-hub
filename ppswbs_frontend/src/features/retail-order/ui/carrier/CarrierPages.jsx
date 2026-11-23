import React, { useEffect, useMemo, useRef, useState } from 'react';
import { RequireSession } from '../../../../shared/auth/RequireSession';
import { useFormat } from '../../../../shared/i18n/I18nProvider';
import { ErrorNotice, Loading, useResource } from '../../../../shared/ui/components';
import { useRetailApi } from '../../application/useRetailApi';
import { displayTime } from '../orderCode';
import { TrendChart, UNIT_LABEL } from '../../../manager-dashboard';
import '../../../../ota-connectivity/ota-connectivity.css';
import './carrier.css';
import { useText } from '../useText';

/**
 * Simulated GHTK partner portal (Session 2026-11-20): an account with role CARRIER sees the GHTK shipments of every
 * Mland branch and records only the courier's steps. Statuses use GHTK's own words.
 */
export const CARRIER_STATUS = {
  PREPARING: 'Đã tiếp nhận',
  READY_FOR_CARRIER: 'Đang lấy hàng',
  HANDED_TO_CARRIER: 'Đang giao hàng',
  IN_TRANSIT: 'Đang giao hàng',
  DELIVERED: 'Đã giao hàng',
};
const TABS = [
  { key: 'RECEIVED', label: 'Đã tiếp nhận', statuses: ['PREPARING'] },
  { key: 'COLLECTING', label: 'Đang lấy hàng', statuses: ['READY_FOR_CARRIER'] },
  { key: 'DELIVERING', label: 'Đang giao hàng', statuses: ['HANDED_TO_CARRIER', 'IN_TRANSIT'] },
  { key: 'DELIVERED', label: 'Đã giao hàng', statuses: ['DELIVERED'] },
];
/** English wording of the GHTK statuses and tabs (VI/EN switch, Session 2026-11-23). */
export const CARRIER_STATUS_EN = {
  PREPARING: 'Received', READY_FOR_CARRIER: 'Picking up', HANDED_TO_CARRIER: 'Delivering', IN_TRANSIT: 'Delivering', DELIVERED: 'Delivered',
};
const TAB_EN = { RECEIVED: 'Received', COLLECTING: 'Picking up', DELIVERING: 'Delivering', DELIVERED: 'Delivered' };
export const carrierTab = status => TABS.find(tab => tab.statuses.includes(status))?.key || 'RECEIVED';

/** The GHTK pages sit in the operations shell like Staff and Manager (Session 2026-11-23): the rail is the shell's. */
function CarrierLayout({ children }) {
  const t = useText();
  return <div className="carrier-main">
    <RequireSession requiredText={t('Đăng nhập bằng tài khoản đối tác GHTK.', 'Sign in with a GHTK partner account.')}>{children}</RequireSession>
  </div>;
}

function accessText(error, t = vi => vi) {
  if (error?.status === 403) return t('Tài khoản này không phải tài khoản đối tác GHTK đang hoạt động.', 'This account is not an active GHTK partner account.');
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
  const t = useText();
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
    <p className="carrier-intro">{t('Vận đơn của các chi nhánh Mland. Shop chuẩn bị xong thì đơn chuyển sang “Đang lấy hàng”.', 'Shipments of every Mland branch. Once the shop has packed, the shipment moves to “Picking up”.')}</p>
    {shipments.loading && !shipments.data && <Loading />}
    {shipments.error && (accessText(shipments.error, t)
      ? <div className="notice error" role="alert"><p>{accessText(shipments.error, t)}</p></div>
      : <ErrorNotice error={shipments.error} onRetry={shipments.reload} />)}
    {shipments.data && <section className="order-tabs-page carrier-orders">
      <div className="order-tabs" role="tablist" aria-label={t('Trạng thái vận đơn', 'Shipment status')}>{TABS.map(item => <button key={item.key} type="button" role="tab" aria-selected={tab === item.key} className={tab === item.key ? 'active' : ''} onClick={() => setTab(item.key)}>{t(item.label, TAB_EN[item.key])}{counts[item.key] ? <span className="order-tab-count">{counts[item.key]}</span> : null}</button>)}</div>
      <input className="order-search" value={search} onChange={event => setSearch(event.target.value)} placeholder={t('Mã vận đơn, mã đơn, tên hoặc SĐT người nhận', 'Tracking number, order code, recipient name or phone')} aria-label={t('Tìm vận đơn', 'Search shipments')} />
      {error && <ErrorNotice error={error} />}
      {tab === 'RECEIVED' && visible.length > 0 && <p className="order-hint">{t('Shop đang chuẩn bị hàng. Chưa cần đến lấy.', 'The shop is still packing. No pickup needed yet.')}</p>}
      {visible.length === 0 && <div className="order-empty"><p>{term ? t('Không tìm thấy vận đơn phù hợp.', 'No matching shipment.') : t('Không có vận đơn nào ở mục này.', 'No shipment in this tab.')}</p></div>}
      {visible.map(item => {
        const branch = branches.get(String(item.pickupLocationId));
        const busy = busyId === item.id;
        return <article className="order-card carrier-card" key={item.id}>
          <div className="order-card-top">
            <span className="order-code">{item.trackingNumber}</span>
            <span className="order-branch">{t(`Đơn ${item.orderCode} · ${item.itemQuantity} sản phẩm`, `Order ${item.orderCode} · ${item.itemQuantity} items`)}</span>
            <span className={`order-pill carrier-pill-${carrierTab(item.status).toLowerCase()}`}>{t(CARRIER_STATUS[item.status], CARRIER_STATUS_EN[item.status])}</span>
          </div>
          <div className="carrier-route">
            <div><span>{t('Lấy hàng tại', 'Pick up at')}</span><strong>{branch?.name || t(`Chi nhánh #${item.pickupLocationId}`, `Branch #${item.pickupLocationId}`)}</strong><small>{branch?.address || '—'}</small></div>
            <div><span>{t('Giao đến', 'Deliver to')}</span><strong>{item.recipientName || '—'} · {item.recipientPhone || '—'}</strong><small>{[item.recipientAddress, item.wardName, item.districtName, item.provinceName].filter(Boolean).join(', ')}</small></div>
          </div>
          <dl className="order-detail carrier-times">
            <div><dt>{t('Tiếp nhận', 'Received')}</dt><dd>{displayTime(item.createdAt)}</dd></div>
            <div><dt>{t('Shop chuẩn bị xong', 'Shop packed')}</dt><dd>{displayTime(item.readyAt)}</dd></div>
            <div><dt>{t('Đã lấy hàng', 'Picked up')}</dt><dd>{displayTime(item.pickedUpAt)}</dd></div>
            <div><dt>{t('Giao thành công', 'Delivered')}</dt><dd>{displayTime(item.deliveredAt)}</dd></div>
          </dl>
          {item.status === 'READY_FOR_CARRIER' && <div className="order-actions"><button type="button" className="button primary" disabled={busy} onClick={() => act(item, 'PICKED_UP')}>{busy ? t('Đang lưu…', 'Saving…') : t('Đã lấy hàng', 'Picked up')}</button></div>}
          {(item.status === 'HANDED_TO_CARRIER' || item.status === 'IN_TRANSIT') && <div className="order-actions">{/* Prepaid: the customer paid goods and shipping online, nothing to collect. */}<span className="carrier-cod"><strong>{t('Thu hộ (COD): 0 ₫', 'Cash on delivery (COD): 0 ₫')}</strong></span><button type="button" className="button primary" disabled={busy} onClick={() => act(item, 'DELIVERED')}>{busy ? t('Đang lưu…', 'Saving…') : t('Giao thành công', 'Delivered')}</button></div>}
        </article>;
      })}
    </section>}
  </>;
}

// ---- Thống kê ---------------------------------------------------------------------------------------------------

const PERIODS = [{ id: 'DAY', label: ['Ngày', 'Day'] }, { id: 'WEEK', label: ['Tuần', 'Week'] }, { id: 'MONTH', label: ['Tháng', 'Month'] }, { id: 'YEAR', label: ['Năm', 'Year'] }];
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
  delivered.forEach(item => { const row = byBranch.get(item.pickupLocationId); row.delivered += 1; row.fee += BigInt(item.shippingCharge || '0'); });
  return {
    received: received.length,
    pickedUp: picked.length,
    delivered: delivered.length,
    delivering: items.filter(item => item.status === 'HANDED_TO_CARRIER' || item.status === 'IN_TRANSIT').length,
    waitingPickup: items.filter(item => item.status === 'READY_FOR_CARRIER').length,
    averageHours: hours.length ? hours.reduce((a, b) => a + b, 0) / hours.length : null,
    fees: delivered.reduce((sum, item) => sum + BigInt(item.shippingCharge || '0'), 0n).toString(),
    trend: { received: series('createdAt'), pickedUp: series('pickedUpAt'), delivered: series('deliveredAt') },
    byBranch: [...byBranch.entries()].map(([locationId, row]) => ({ locationId, ...row, fee: row.fee.toString() })),
  };
}

const duration = (hours, t = vi => vi) => {
  if (hours == null) return '—';
  if (hours < 1) { const m = Math.max(1, Math.round(hours * 60)); return t(`${m} phút`, `${m} min`); }
  if (hours < 48) return t(`${hours.toLocaleString('vi-VN', { maximumFractionDigits: 1 })} giờ`, `${hours.toLocaleString('en-GB', { maximumFractionDigits: 1 })} h`);
  return t(`${(hours / 24).toLocaleString('vi-VN', { maximumFractionDigits: 1 })} ngày`, `${(hours / 24).toLocaleString('en-GB', { maximumFractionDigits: 1 })} days`);
};

export function CarrierStatsPage() {
  return <CarrierLayout><CarrierStats /></CarrierLayout>;
}

function CarrierStats() {
  const format = useFormat();
  const t = useText();
  const shipments = useCarrierShipments();
  const branches = useBranches();
  const [type, setType] = useState('MONTH');
  const [anchor, setAnchor] = useState(todayVn());
  const period = useMemo(() => carrierPeriod(type, anchor), [type, anchor]);
  const stats = useMemo(() => carrierStats(shipments.data || [], period), [shipments.data, period]);
  const points = period.buckets.map(bucket => ({ label: bucket.label }));
  const count = value => format.number(value);
  const unit = t(UNIT_LABEL[period.unit] || ['ngày', 'day']);

  return <>
    <p className="carrier-intro">{t('Số vận đơn tiếp nhận, lấy hàng, giao thành công và cước vận chuyển theo kỳ, giờ Việt Nam.', 'Shipments received, picked up and delivered, and shipping fees per period, Vietnam time.')}</p>
    <section className="md-filters" aria-label={t('Bộ lọc thống kê', 'Statistics filters')}>
      <div className="md-filter-group"><span className="md-filter-label">{t('Kỳ', 'Period')}</span>
        <div className="md-segment" role="radiogroup" aria-label={t('Loại kỳ', 'Period type')}>{PERIODS.map(item => <button key={item.id} type="button" role="radio" aria-checked={type === item.id} className={type === item.id ? 'active' : ''} onClick={() => setType(item.id)}>{t(item.label)}</button>)}</div>
      </div>
      <label className="md-filter-group"><span className="md-filter-label">{t('Ngày', 'Date')}</span>
        <input type="date" aria-label={t('Chọn ngày', 'Choose a date')} value={anchor} max={todayVn()} onChange={event => { if (event.target.value) { setAnchor(event.target.value); setType('DAY'); } }} />
      </label>
    </section>
    {shipments.loading && !shipments.data && <Loading />}
    {shipments.error && (accessText(shipments.error, t)
      ? <div className="notice error" role="alert"><p>{accessText(shipments.error, t)}</p></div>
      : <ErrorNotice error={shipments.error} onRetry={shipments.reload} />)}
    {shipments.data && <>
      <section className="md-kpis carrier-kpis" aria-label={t('Chỉ số giao hàng', 'Delivery figures')}>
        <Kpi label={t('Vận đơn tiếp nhận', 'Shipments received')} value={count(stats.received)} hint={t('Tạo trong kỳ', 'Created in the period')} />
        <Kpi label={t('Đã lấy hàng', 'Picked up')} value={count(stats.pickedUp)} hint={t(`Đang chờ lấy: ${count(stats.waitingPickup)}`, `Waiting for pickup: ${count(stats.waitingPickup)}`)} />
        <Kpi label={t('Giao thành công', 'Delivered')} value={count(stats.delivered)} hint={t(`Đang giao: ${count(stats.delivering)}`, `Delivering: ${count(stats.delivering)}`)} strong />
        <Kpi label={t('Thời gian giao trung bình', 'Average delivery time')} value={duration(stats.averageHours, t)} hint={t('Từ lúc lấy hàng đến lúc giao', 'From pickup to delivery')} />
        <Kpi label={t('Cước vận chuyển', 'Shipping fees')} value={format.money(stats.fees)} hint={t('Của các đơn giao thành công', 'Of the delivered shipments')} />
      </section>
      <section className="md-panel carrier-trend">
        <div className="md-panel-head"><h2>{t(`Xu hướng theo ${unit}`, `Trend by ${unit}`)}</h2></div>
        <TrendChart title={t(`Vận đơn theo ${unit}`, `Shipments by ${unit}`)} points={points} format={count}
          series={[
            { key: 'received', label: t('Tiếp nhận', 'Received'), color: '#111827', values: stats.trend.received },
            { key: 'picked', label: t('Lấy hàng', 'Picked up'), color: '#f59e0b', values: stats.trend.pickedUp },
            { key: 'delivered', label: t('Giao thành công', 'Delivered'), color: '#16a34a', values: stats.trend.delivered },
          ]} />
      </section>
      <section className="md-panel" aria-label={t('Theo chi nhánh lấy hàng', 'By pickup branch')}>
        <div className="md-panel-head"><h2>{t('Theo chi nhánh lấy hàng', 'By pickup branch')}</h2><span className="muted">{t(`${stats.byBranch.length} chi nhánh`, `${stats.byBranch.length} branches`)}</span></div>
        {stats.byBranch.length === 0 ? <p className="md-empty">{t('Chưa có vận đơn trong kỳ này.', 'No shipment in this period.')}</p> : <div className="md-table-wrap"><table className="md-table">
          <thead><tr><th scope="col">{t('Chi nhánh', 'Branch')}</th><th scope="col">{t('Tiếp nhận', 'Received')}</th><th scope="col">{t('Giao thành công', 'Delivered')}</th><th scope="col">{t('Cước vận chuyển', 'Shipping fees')}</th></tr></thead>
          <tbody>{stats.byBranch.map(row => <tr key={row.locationId}>
            <th scope="row" data-label={t('Chi nhánh', 'Branch')}>{branches.get(String(row.locationId))?.name || t(`Chi nhánh #${row.locationId}`, `Branch #${row.locationId}`)}</th>
            <td data-label={t('Tiếp nhận', 'Received')}>{count(row.received)}</td>
            <td data-label={t('Giao thành công', 'Delivered')}>{count(row.delivered)}</td>
            <td data-label={t('Cước vận chuyển', 'Shipping fees')}>{format.money(row.fee)}</td>
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
