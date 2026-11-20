import React, { useEffect, useMemo, useRef, useState } from 'react';
import { Link, useLocation } from 'react-router';
import { RequireSession } from '../../../../shared/auth/RequireSession';
import { useAuth } from '../../../../shared/auth/AuthProvider';
import { useFormat } from '../../../../shared/i18n/I18nProvider';
import { ErrorNotice, Loading, useResource } from '../../../../shared/ui/components';
import { useRetailApi } from '../../application/useRetailApi';
import { displayTime } from '../orderCode';
import { TrendChart, UNIT_LABEL } from '../../../manager-dashboard';
import '../../../../ota-connectivity/ota-connectivity.css';
import './carrier.css';

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
export const carrierTab = status => TABS.find(tab => tab.statuses.includes(status))?.key || 'RECEIVED';

function CarrierLayout({ children }) {
  const { pathname } = useLocation();
  const { status, signOut } = useAuth();
  const [busy, setBusy] = useState(false);
  const leave = async () => { if (busy) return; setBusy(true); try { await signOut(); } finally { setBusy(false); } };
  return <div className="ota-workspace carrier-workspace">
    <aside className="ota-sidebar" aria-label="Điều hướng GHTK">
      <div className="ota-brand carrier-brand"><span>G</span><strong>GHTK</strong><small>đối tác vận chuyển</small></div>
      <div className="ota-sidebar-nav">
        <Link className={pathname === '/carrier' ? 'active' : ''} to="/carrier">Tài khoản</Link>
        <Link className={pathname === '/carrier/orders' ? 'active' : ''} to="/carrier/orders">Đơn hàng</Link>
        <Link className={pathname === '/carrier/stats' ? 'active' : ''} to="/carrier/stats">Thống kê</Link>
      </div>
      <div className="ota-sidebar-footer">
        {status === 'signed-in'
          ? <button type="button" className="ota-signout-btn" onClick={leave} disabled={busy}><span>{busy ? 'Đang đăng xuất…' : 'Đăng xuất'}</span></button>
          : <Link to="/login" className="ota-signin-btn">Đăng nhập</Link>}
      </div>
    </aside>
    <main className="ota-main carrier-main">
      <div className="ota-topline"><span>GHTK × MLAND / CỔNG ĐỐI TÁC VẬN CHUYỂN</span><span className="carrier-sim">Mô phỏng · không phải hệ thống GHTK thật</span></div>
      <RequireSession requiredText="Đăng nhập bằng tài khoản đối tác GHTK.">{children}</RequireSession>
    </main>
  </div>;
}

function accessText(error) {
  if (error?.status === 403) return 'Tài khoản này không phải tài khoản đối tác GHTK đang hoạt động.';
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
    <header className="ota-page-heading"><div><p className="ota-eyebrow">GHTK · Đối tác vận chuyển</p><h1>Đơn hàng cần xử lý</h1><p>Vận đơn của các chi nhánh Mland. Shop chuẩn bị xong thì đơn chuyển sang “Đang lấy hàng”.</p></div></header>
    {shipments.loading && !shipments.data && <Loading />}
    {shipments.error && (accessText(shipments.error)
      ? <div className="notice error" role="alert"><p>{accessText(shipments.error)}</p></div>
      : <ErrorNotice error={shipments.error} onRetry={shipments.reload} />)}
    {shipments.data && <section className="order-tabs-page carrier-orders">
      <div className="order-tabs" role="tablist" aria-label="Trạng thái vận đơn">{TABS.map(item => <button key={item.key} type="button" role="tab" aria-selected={tab === item.key} className={tab === item.key ? 'active' : ''} onClick={() => setTab(item.key)}>{item.label}{counts[item.key] ? <span className="order-tab-count">{counts[item.key]}</span> : null}</button>)}</div>
      <input className="order-search" value={search} onChange={event => setSearch(event.target.value)} placeholder="Mã vận đơn, mã đơn, tên hoặc SĐT người nhận" aria-label="Tìm vận đơn" />
      {error && <ErrorNotice error={error} />}
      {tab === 'RECEIVED' && visible.length > 0 && <p className="order-hint">Shop đang chuẩn bị hàng. Chưa cần đến lấy.</p>}
      {visible.length === 0 && <div className="order-empty"><p>{term ? 'Không tìm thấy vận đơn phù hợp.' : 'Không có vận đơn nào ở mục này.'}</p></div>}
      {visible.map(item => {
        const branch = branches.get(String(item.pickupLocationId));
        const busy = busyId === item.id;
        return <article className="order-card carrier-card" key={item.id}>
          <div className="order-card-top">
            <span className="order-code">{item.trackingNumber}</span>
            <span className="order-branch">Đơn {item.orderCode} · {item.itemQuantity} sản phẩm</span>
            <span className={`order-pill carrier-pill-${carrierTab(item.status).toLowerCase()}`}>{CARRIER_STATUS[item.status]}</span>
          </div>
          <div className="carrier-route">
            <div><span>Lấy hàng tại</span><strong>{branch?.name || `Chi nhánh #${item.pickupLocationId}`}</strong><small>{branch?.address || '—'}</small></div>
            <div><span>Giao đến</span><strong>{item.recipientName || '—'} · {item.recipientPhone || '—'}</strong><small>{[item.recipientAddress, item.wardName, item.districtName, item.provinceName].filter(Boolean).join(', ')}</small></div>
          </div>
          <dl className="order-detail carrier-times">
            <div><dt>Tiếp nhận</dt><dd>{displayTime(item.createdAt)}</dd></div>
            <div><dt>Shop chuẩn bị xong</dt><dd>{displayTime(item.readyAt)}</dd></div>
            <div><dt>Đã lấy hàng</dt><dd>{displayTime(item.pickedUpAt)}</dd></div>
            <div><dt>Giao thành công</dt><dd>{displayTime(item.deliveredAt)}</dd></div>
          </dl>
          {item.status === 'READY_FOR_CARRIER' && <div className="order-actions"><button type="button" className="button primary" disabled={busy} onClick={() => act(item, 'PICKED_UP')}>{busy ? 'Đang lưu…' : 'Đã lấy hàng'}</button></div>}
          {(item.status === 'HANDED_TO_CARRIER' || item.status === 'IN_TRANSIT') && <div className="order-actions">{/* Prepaid: the customer paid goods and shipping online, nothing to collect. */}<span className="carrier-cod"><strong>Thu hộ (COD): 0 ₫</strong></span><button type="button" className="button primary" disabled={busy} onClick={() => act(item, 'DELIVERED')}>{busy ? 'Đang lưu…' : 'Giao thành công'}</button></div>}
        </article>;
      })}
    </section>}
  </>;
}

// ---- Thống kê ---------------------------------------------------------------------------------------------------

const PERIODS = [{ id: 'DAY', label: 'Ngày' }, { id: 'WEEK', label: 'Tuần' }, { id: 'MONTH', label: 'Tháng' }, { id: 'YEAR', label: 'Năm' }];
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

const duration = hours => {
  if (hours == null) return '—';
  if (hours < 1) return `${Math.max(1, Math.round(hours * 60))} phút`;
  if (hours < 48) return `${hours.toLocaleString('vi-VN', { maximumFractionDigits: 1 })} giờ`;
  return `${(hours / 24).toLocaleString('vi-VN', { maximumFractionDigits: 1 })} ngày`;
};

export function CarrierStatsPage() {
  return <CarrierLayout><CarrierStats /></CarrierLayout>;
}

function CarrierStats() {
  const format = useFormat();
  const shipments = useCarrierShipments();
  const branches = useBranches();
  const [type, setType] = useState('MONTH');
  const [anchor, setAnchor] = useState(todayVn());
  const period = useMemo(() => carrierPeriod(type, anchor), [type, anchor]);
  const stats = useMemo(() => carrierStats(shipments.data || [], period), [shipments.data, period]);
  const points = period.buckets.map(bucket => ({ label: bucket.label }));
  const count = value => format.number(value);

  return <>
    <header className="ota-page-heading"><div><p className="ota-eyebrow">GHTK · Đối tác vận chuyển</p><h1>Thống kê giao hàng</h1><p>Số vận đơn tiếp nhận, lấy hàng, giao thành công và cước vận chuyển theo kỳ, giờ Việt Nam.</p></div></header>
    <section className="md-filters" aria-label="Bộ lọc thống kê">
      <div className="md-filter-group"><span className="md-filter-label">Kỳ</span>
        <div className="md-segment" role="radiogroup" aria-label="Loại kỳ">{PERIODS.map(item => <button key={item.id} type="button" role="radio" aria-checked={type === item.id} className={type === item.id ? 'active' : ''} onClick={() => setType(item.id)}>{item.label}</button>)}</div>
      </div>
      <label className="md-filter-group"><span className="md-filter-label">Ngày</span>
        <input type="date" aria-label="Chọn ngày" value={anchor} max={todayVn()} onChange={event => { if (event.target.value) { setAnchor(event.target.value); setType('DAY'); } }} />
      </label>
    </section>
    {shipments.loading && !shipments.data && <Loading />}
    {shipments.error && (accessText(shipments.error)
      ? <div className="notice error" role="alert"><p>{accessText(shipments.error)}</p></div>
      : <ErrorNotice error={shipments.error} onRetry={shipments.reload} />)}
    {shipments.data && <>
      <section className="md-kpis carrier-kpis" aria-label="Chỉ số giao hàng">
        <Kpi label="Vận đơn tiếp nhận" value={count(stats.received)} hint="Tạo trong kỳ" />
        <Kpi label="Đã lấy hàng" value={count(stats.pickedUp)} hint={`Đang chờ lấy: ${count(stats.waitingPickup)}`} />
        <Kpi label="Giao thành công" value={count(stats.delivered)} hint={`Đang giao: ${count(stats.delivering)}`} strong />
        <Kpi label="Thời gian giao trung bình" value={duration(stats.averageHours)} hint="Từ lúc lấy hàng đến lúc giao" />
        <Kpi label="Cước vận chuyển" value={format.money(stats.fees)} hint="Của các đơn giao thành công" />
      </section>
      <section className="md-panel carrier-trend">
        <div className="md-panel-head"><h2>Xu hướng theo {UNIT_LABEL[period.unit] || 'ngày'}</h2></div>
        <TrendChart title={`Vận đơn theo ${UNIT_LABEL[period.unit] || 'ngày'}`} points={points} format={count}
          series={[
            { key: 'received', label: 'Tiếp nhận', color: '#111827', values: stats.trend.received },
            { key: 'picked', label: 'Lấy hàng', color: '#f59e0b', values: stats.trend.pickedUp },
            { key: 'delivered', label: 'Giao thành công', color: '#16a34a', values: stats.trend.delivered },
          ]} />
      </section>
      <section className="md-panel" aria-label="Theo chi nhánh lấy hàng">
        <div className="md-panel-head"><h2>Theo chi nhánh lấy hàng</h2><span className="muted">{stats.byBranch.length} chi nhánh</span></div>
        {stats.byBranch.length === 0 ? <p className="md-empty">Chưa có vận đơn trong kỳ này.</p> : <div className="md-table-wrap"><table className="md-table">
          <thead><tr><th scope="col">Chi nhánh</th><th scope="col">Tiếp nhận</th><th scope="col">Giao thành công</th><th scope="col">Cước vận chuyển</th></tr></thead>
          <tbody>{stats.byBranch.map(row => <tr key={row.locationId}>
            <th scope="row" data-label="Chi nhánh">{branches.get(String(row.locationId))?.name || `Chi nhánh #${row.locationId}`}</th>
            <td data-label="Tiếp nhận">{count(row.received)}</td>
            <td data-label="Giao thành công">{count(row.delivered)}</td>
            <td data-label="Cước vận chuyển">{format.money(row.fee)}</td>
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
