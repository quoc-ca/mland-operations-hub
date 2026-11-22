import React, { useEffect, useMemo, useRef, useState } from 'react';
import { Link } from 'react-router';
import { RequireSession } from '../../../../shared/auth/RequireSession';
import { useFormat } from '../../../../shared/i18n/I18nProvider';
import { ErrorNotice, Loading, useResource } from '../../../../shared/ui/components';
import { FlashToast } from '../FlashToast';
import { OPEN_TASK_TABS, pickupTab, shipmentTab } from './staffTabs';
import { newCommandId } from '../../../../shared/api/http';
import { useRetailApi } from '../../application/useRetailApi';
import { cardToggleProps, displayTime, orderCode, OrderMoneyRows } from '../orderCode';
import { ProductThumb } from '../ProductThumb';

/**
 * FE-08 FR-08-051: one Staff page for the branch's retail work. Tabs follow the order stages of Session 2026-11-18:
 * Đang chuẩn bị hàng → (GHTK) Chờ ĐVVC lấy hàng → Đã bàn giao cho ĐVVC → Hoàn thành, or (pickup) Chờ khách nhận →
 * Hoàn thành. Each card offers only the action of its stage. Staff only packs a GHTK order; the GHTK partner records
 * the courier pickup and the delivery on /carrier/orders (FE-01 role CARRIER, V190).
 */
export const shipmentLabels = {
  PREPARING: 'Đang chuẩn bị hàng', READY_FOR_CARRIER: 'Chờ ĐVVC lấy hàng', HANDED_TO_CARRIER: 'Đã bàn giao cho ĐVVC',
  IN_TRANSIT: 'Đã bàn giao cho ĐVVC', DELIVERED: 'Hoàn thành',
};
export const nextShipmentStatus = { PREPARING: 'READY_FOR_CARRIER' };
const nextShipmentAction = { PREPARING: 'Đã chuẩn bị xong' };
/** What Staff is waiting for once the parcel is out of its hands. */
const courierWaiting = {
  READY_FOR_CARRIER: 'Chờ GHTK đến lấy hàng.',
  HANDED_TO_CARRIER: 'GHTK đang giao hàng.',
  IN_TRANSIT: 'GHTK đang giao hàng.',
};
const SHIPMENT_STATUSES = Object.keys(shipmentLabels);
// GHTK work first, then the counter (cash, pickup), then history (Thinh, 2026-11-20).
const TABS = [
  { key: 'PREPARING', label: 'Đơn hàng cần chuẩn bị' },
  { key: 'AWAITING_CARRIER', label: 'Chờ ĐVVC lấy hàng' },
  { key: 'HANDED_OVER', label: 'Đã bàn giao ĐVVC' },
  { key: 'AWAITING_CASH', label: 'Chờ thu tiền mặt' },
  { key: 'TO_HAND_OVER', label: 'Chờ khách nhận' },
  { key: 'HISTORY', label: 'Hoàn thành' },
];
// Addresses and bookmarks of the former tabs keep working.
const LEGACY_TABS = { TO_SHIP: 'PREPARING', SHIPPING: 'HANDED_OVER' };

const DAY_MS = 24 * 60 * 60 * 1000;
const timeLeft = deadline => {
  const ms = new Date(deadline).getTime() - Date.now();
  if (ms <= 0) return 'đã quá hạn';
  const hours = Math.floor(ms / 3_600_000);
  return hours >= 24 ? `còn ${Math.floor(hours / 24)} ngày ${hours % 24} giờ` : `còn ${Math.max(hours, 0)} giờ ${Math.floor((ms % 3_600_000) / 60_000)} phút`;
};



export default function StaffRetailOrdersPage({ initialTab }) {
  return <div className="ota-workspace">
    
    <main className="ota-main retail-staff-main">
      <div className="ota-topline"><span className="retail-staff-crumb"><Link to="/home">← Trang chủ</Link> MLAND / ĐƠN BÁN LẺ</span><span>● Backend-authorized</span></div>
      <div className="ota-page-heading"><div><p>Chuẩn bị hàng gửi GHTK, thu tiền mặt và giao hàng tại quầy cho chi nhánh của bạn.</p></div></div>
      <RequireSession requiredText="Đăng nhập Staff hoặc Manager được phân công chi nhánh."><StaffOrders initialTab={initialTab} /></RequireSession>
    </main>
  </div>;
}

/** The Staff order list without the page frame, also shown on the Staff home (Session 2026-11-22). */
export function StaffOrders({ initialTab }) {
  const api = useRetailApi();
  const format = useFormat();
  const wanted = LEGACY_TABS[initialTab] || initialTab;
  const [tab, setTab] = useState(TABS.some(item => item.key === wanted) ? wanted : 'PREPARING');
  const [search, setSearch] = useState('');
  const [busyId, setBusyId] = useState(null);
  const [error, setError] = useState(null);
  const [notice, setNotice] = useState('');
  const [openKey, setOpenKey] = useState(null);
  const [details, setDetails] = useState({});
  const desk = useResource(() => api.listStaffPickups(), []);
  const shipments = useResource(async () => {
    const pages = await Promise.all(SHIPMENT_STATUSES.map(status => api.listStaffShipments({ page: 0, size: 50, status })));
    return pages.flatMap(page => page?.items || []);
  }, []);
  const branches = useResource(() => api.listBranches(), []);
  const branchNames = useMemo(() => new Map((branches.data?.items || branches.data || []).map(item => [String(item.id), item.name])), [branches.data]);
  const branchName = locationId => branchNames.get(String(locationId)) || `Chi nhánh #${locationId}`;

  const rows = useMemo(() => [
    ...(Array.isArray(desk.data) ? desk.data : []).map(row => ({ kind: 'PICKUP', key: `p-${row.order.id}`, tab: pickupTab(row), code: orderCode(row.order), sortAt: row.order.orderedAt, row })),
    ...(shipments.data || []).map(row => ({ kind: 'SHIPMENT', key: `s-${row.id}`, tab: shipmentTab(row), code: orderCode({ orderCode: row.orderCode, id: row.branchOrderId }), sortAt: row.updatedAt || row.createdAt, row })),
  ], [desk.data, shipments.data]);
  const counts = useMemo(() => rows.reduce((acc, item) => { acc[item.tab] = (acc[item.tab] || 0) + 1; return acc; }, {}), [rows]);
  // Opening the page without a chosen tab shows the first tab, left to right, that has orders (Thinh, 2026-11-21).
  const autoPick = useRef(!initialTab);
  useEffect(() => {
    if (!autoPick.current || !desk.data || !shipments.data) return;
    autoPick.current = false;
    const first = TABS.find(item => item.key !== 'HISTORY' && counts[item.key]);
    if (first) setTab(first.key);
  }, [desk.data, shipments.data, counts]);
  const today = new Date().toLocaleDateString('en-CA', { timeZone: 'Asia/Ho_Chi_Minh' });
  const cashToday = rows.filter(item => item.kind === 'PICKUP' && item.row.cashCollection
    && new Date(item.row.cashCollection.confirmedAt).toLocaleDateString('en-CA', { timeZone: 'Asia/Ho_Chi_Minh' }) === today)
    .reduce((sum, item) => sum + BigInt(item.row.cashCollection.appliedAmount || '0'), 0n);
  const expiringSoon = rows.filter(item => item.tab === 'AWAITING_CASH' && item.row.order.counterPaymentDeadline
    && new Date(item.row.order.counterPaymentDeadline).getTime() - Date.now() < DAY_MS).length;
  const openTasks = OPEN_TASK_TABS.reduce((sum, key) => sum + (counts[key] || 0), 0);

  const term = search.trim().toLowerCase();
  const matches = item => !term || item.code.toLowerCase().includes(term)
    || (item.kind === 'SHIPMENT' && [item.row.mockTrackingNumber, item.row.recipientName].some(value => String(value || '').toLowerCase().includes(term)));
  const visible = rows.filter(item => item.tab === tab && matches(item)).sort((a, b) => {
    if (tab === 'AWAITING_CASH') return String(a.row.order.counterPaymentDeadline || '').localeCompare(String(b.row.order.counterPaymentDeadline || ''));
    return String(b.sortAt || '').localeCompare(String(a.sortAt || ''));
  });

  const reloadAll = () => Promise.all([desk.reload(), shipments.reload()]);
  const run = async (key, action) => {
    setBusyId(key); setError(null); setNotice('');
    try { await action(); await reloadAll(); } catch (e) { setError(e); } finally { setBusyId(null); }
  };
  const confirmCash = item => run(item.key, async () => {
    const order = item.row.order;
    // Cash at the counter (Thinh, 2026-11-20): the customer pays the exact amount and one server call records the
    // payment and the handover together, so the order goes straight to "Hoàn thành". If it fails nothing is
    // recorded; Staff presses again when the connection is back.
    await api.collectCashAndHandOver(order.id, newCommandId());
    setNotice(`Đơn ${item.code}: đã thu ${format.money(order.payableAmount)}, đơn đã hoàn thành.`);
  });
  // Pickup: the order is packed and waits at the counter (Đang chuẩn bị hàng → Chờ khách nhận).
  const prepare = item => run(item.key, async () => {
    await api.prepareOrder(item.row.order.id);
    setNotice(`Đơn ${item.code} đã chuẩn bị xong, chờ khách đến nhận.`);
  });
  const handOver = item => run(item.key, async () => {
    if (!item.row.pickupReadyConfirmed) await api.prepareOrder(item.row.order.id);
    await api.handOverOrder(item.row.order.id);
    setNotice(`Đã giao đơn ${item.code} cho khách.`);
  });
  const advance = item => {
    const next = nextShipmentStatus[item.row.status];
    if (!next || !window.confirm(`Chuyển vận đơn ${item.row.mockTrackingNumber} sang “${shipmentLabels[next]}”?`)) return;
    run(item.key, async () => {
      await api.transitionStaffShipment(item.row.id, { nextStatus: next, expectedVersion: item.row.version });
      setDetails(current => { const copy = { ...current }; delete copy[item.row.id]; return copy; });
      setNotice(`Vận đơn ${item.row.mockTrackingNumber}: ${shipmentLabels[next]}.`);
    });
  };
  const toggle = async item => {
    const closing = openKey === item.key;
    setOpenKey(closing ? null : item.key);
    if (closing || item.kind !== 'SHIPMENT' || details[item.row.id]) return;
    try { const view = await api.getStaffShipment(item.row.id); setDetails(current => ({ ...current, [item.row.id]: view })); } catch (e) { setError(e); }
  };

  if ((desk.loading && !desk.data) || (shipments.loading && !shipments.data)) return <Loading />;
  return <section className="order-tabs-page staff-orders">
    <div className="staff-summary">
      <div><span>Tiền mặt đã thu hôm nay</span><strong>{format.money(cashToday.toString())}</strong></div>
      <div className={expiringSoon ? 'warn' : ''}><span>Sắp quá hạn giữ hàng (dưới 24 giờ)</span><strong>{expiringSoon}</strong></div>
      <div><span>Việc đang chờ</span><strong>{openTasks}</strong></div>
    </div>
    <div className="staff-toolbar">
      <input className="order-search" value={search} onChange={event => setSearch(event.target.value)} placeholder="Mã đơn (ML…), mã vận đơn hoặc tên người nhận" aria-label="Tìm đơn" />
      <button type="button" className="button outline" onClick={reloadAll} disabled={desk.loading || shipments.loading}>Làm mới</button>
    </div>
    <div className="order-tabs" role="tablist" aria-label="Việc của chi nhánh">{TABS.map(item => <button key={item.key} type="button" role="tab" aria-selected={tab === item.key} className={tab === item.key ? 'active' : ''} onClick={() => setTab(item.key)}>{item.label}{item.key !== 'HISTORY' && counts[item.key] ? <span className="order-tab-count">{counts[item.key]}</span> : null}</button>)}</div>
    {desk.error && <ErrorNotice error={desk.error} onRetry={desk.reload} />}
    {shipments.error && <ErrorNotice error={shipments.error} onRetry={shipments.reload} />}
    {tab === 'AWAITING_CASH' && <p className="muted small">Khách đến quầy trả tiền: bấm "Xác nhận đã thu". Đơn chuyển thẳng sang Hoàn thành.</p>}
    <FlashToast message={notice} onDone={() => setNotice('')} />
    {error && <ErrorNotice error={error} />}
    {visible.length === 0 && <div className="order-empty"><p>{term ? 'Không tìm thấy đơn phù hợp.' : 'Không còn việc nào ở mục này.'}</p></div>}
    {visible.map(item => item.kind === 'PICKUP'
      ? <PickupCard key={item.key} item={item} open={openKey === item.key} onToggle={() => toggle(item)} busy={busyId === item.key} format={format} branchName={branchName} onConfirmCash={confirmCash} onPrepare={prepare} onHandOver={handOver} />
      : <ShipmentCard key={item.key} item={item} open={openKey === item.key} onToggle={() => toggle(item)} busy={busyId === item.key} format={format} branchName={branchName} detail={details[item.row.id]} onAdvance={advance} />)}
  </section>;
}

function PickupCard({ item, open, onToggle, busy, format, branchName, onConfirmCash, onPrepare, onHandOver }) {
  const { row } = item;
  const order = row.order;
  const cash = order.paymentMethod === 'CASH_AT_COUNTER';
  const urgent = item.tab === 'AWAITING_CASH' && order.counterPaymentDeadline && new Date(order.counterPaymentDeadline).getTime() - Date.now() < DAY_MS;
  const pill = { HISTORY: 'Hoàn thành', AWAITING_CASH: 'Chờ thu tiền mặt', PREPARING: 'Đang chuẩn bị hàng', TO_HAND_OVER: 'Chờ khách nhận' }[item.tab];
  return <article className={`order-card${urgent ? ' urgent' : ''}${open ? ' open' : ''}`} {...cardToggleProps(open, onToggle)}>
    <div className="order-card-top">
      <span className="order-code">{item.code}</span>
      <span className="order-branch">{branchName(order.locationId)} · Nhận tại cửa hàng</span>
      <span className={`order-pill pill-${item.tab.toLowerCase()}`}>{pill}</span>
    </div>
    <ul className="order-items">{(order.items || []).map(line => <li key={`${order.id}-${line.variantId}`}>
      <ProductThumb className="order-thumb" productId={line.productId} />
      <span className="order-item-name"><strong>{line.productName || `Sản phẩm #${line.variantId}`}</strong><small>Size {line.size ?? '—'} · x{line.quantity}</small></span>
      <b>{line.lineTotal ? format.money(line.lineTotal) : ''}</b>
    </li>)}</ul>
    {item.tab === 'AWAITING_CASH' && <p className={`order-hint${urgent ? ' warn' : ''}`}>Giữ hàng đến {displayTime(order.counterPaymentDeadline)} ({timeLeft(order.counterPaymentDeadline)}).</p>}
    {open && <dl className="order-detail">
      <div><dt>Mã đơn</dt><dd>{item.code}</dd></div>
      <div><dt>Thời gian đặt</dt><dd>{displayTime(order.orderedAt)}</dd></div>
      <div><dt>Thanh toán</dt><dd>{cash ? 'Tiền mặt tại quầy' : 'VNPay'}</dd></div>
      {order.completedAt && <div><dt>Giao cho khách lúc</dt><dd>{displayTime(order.completedAt)}</dd></div>}
      <OrderMoneyRows order={order} format={format} />
      {row.cashCollection && <div><dt>Tiền mặt</dt><dd>Thu {format.money(row.cashCollection.amountReceived)}, thối {format.money(row.cashCollection.changeReturned)} lúc {displayTime(row.cashCollection.confirmedAt)}</dd></div>}
    </dl>}
    <div className="order-card-bottom">
      <span className="order-more">{open ? 'Thu gọn ▲' : 'Bấm để xem chi tiết ▼'}</span>
      <span className="order-sum">{item.tab === 'AWAITING_CASH' ? 'Cần thu' : 'Thành tiền'} <strong>{format.money(order.payableAmount)}</strong></span>
    </div>
    {item.tab === 'AWAITING_CASH' && <div className="order-actions">
      <button type="button" className="button primary" disabled={busy} onClick={() => onConfirmCash(item)}>{busy ? 'Đang ghi nhận…' : `Xác nhận đã thu ${format.money(order.payableAmount)}`}</button>
    </div>}
    {item.tab === 'PREPARING' && <div className="order-actions"><span className="muted">{cash ? 'Đã thu tiền mặt' : 'Đã thanh toán VNPay'}</span><button type="button" className="button primary" disabled={busy} onClick={() => onPrepare(item)}>{busy ? 'Đang ghi nhận…' : 'Đã chuẩn bị xong'}</button></div>}
    {item.tab === 'TO_HAND_OVER' && <div className="order-actions"><button type="button" className="button primary" disabled={busy} onClick={() => onHandOver(item)}>{busy ? 'Đang ghi nhận…' : 'Giao cho khách'}</button></div>}
  </article>;
}

function ShipmentCard({ item, open, onToggle, busy, format, branchName, detail, onAdvance }) {
  const { row } = item;
  const next = nextShipmentStatus[row.status];
  return <article className={`order-card${open ? ' open' : ''}`} {...cardToggleProps(open, onToggle)}>
    <div className="order-card-top">
      <span className="order-code">{item.code}</span>
      <span className="order-branch">{branchName(row.locationId)} · GHTK {row.mockTrackingNumber}</span>
      <span className={`order-pill pill-${item.tab.toLowerCase()}`}>{shipmentLabels[row.status]}</span>
    </div>
    <div className="order-recipient">
      <span className="order-thumb" aria-hidden="true">⛟</span>
      <span className="order-item-name"><strong>{row.recipientName || 'Chưa có tên người nhận'}</strong><small>{[row.wardName, row.districtName, row.provinceName].filter(Boolean).join(', ')}</small></span>
      {row.itemQuantity ? <b>{row.itemQuantity} sản phẩm</b> : null}
    </div>
    {open && <dl className="order-detail">
      <div><dt>Mã đơn</dt><dd>{item.code}</dd></div>
      <div><dt>Mã vận đơn</dt><dd>{row.mockTrackingNumber}</dd></div>
      {detail ? <>
        <div><dt>Điện thoại</dt><dd>{detail.recipientPhone || '—'}</dd></div>
        <div className="wide"><dt>Địa chỉ</dt><dd>{[detail.recipientAddress, detail.wardName, detail.districtName, detail.provinceName].filter(Boolean).join(', ')}</dd></div>
        <div className="wide"><dt>Lịch sử</dt><dd><ol className="order-timeline">{(detail.history || []).map((entry, index) => <li key={index}><strong>{shipmentLabels[entry.status]}</strong> · {displayTime(entry.createdAt)} · {entry.actorLabel}</li>)}</ol></dd></div>
      </> : <div className="wide"><dt>Chi tiết</dt><dd>Đang tải…</dd></div>}
    </dl>}
    <div className="order-card-bottom">
      <span className="order-more">{open ? 'Thu gọn ▲' : 'Bấm để xem chi tiết ▼'}</span>
      {row.payableAmount && <span className="order-sum">Thành tiền <strong>{format.money(row.payableAmount)}</strong></span>}
    </div>
    {next && <div className="order-actions"><button type="button" className="button primary" disabled={busy} onClick={() => onAdvance(item)}>{busy ? 'Đang lưu…' : nextShipmentAction[row.status]}</button></div>}
    {courierWaiting[row.status] && <p className="order-hint">{courierWaiting[row.status]}</p>}
  </article>;
}
