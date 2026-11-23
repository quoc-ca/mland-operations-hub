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
import { useText } from '../useText';

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
/** English wording of the same stages (VI/EN switch, Session 2026-11-23). */
export const shipmentLabelsEn = {
  PREPARING: 'Preparing', READY_FOR_CARRIER: 'Waiting for courier pickup', HANDED_TO_CARRIER: 'Handed to courier',
  IN_TRANSIT: 'Handed to courier', DELIVERED: 'Completed',
};
export const nextShipmentStatus = { PREPARING: 'READY_FOR_CARRIER' };
const nextShipmentAction = { PREPARING: ['Đã chuẩn bị xong', 'Packed and ready'] };
/** What Staff is waiting for once the parcel is out of its hands. */
const courierWaiting = {
  READY_FOR_CARRIER: ['Chờ GHTK đến lấy hàng.', 'Waiting for GHTK to pick up.'],
  HANDED_TO_CARRIER: ['GHTK đang giao hàng.', 'GHTK is delivering.'],
  IN_TRANSIT: ['GHTK đang giao hàng.', 'GHTK is delivering.'],
};
const SHIPMENT_STATUSES = Object.keys(shipmentLabels);
// GHTK work first, then the counter (cash, pickup), then history (Thinh, 2026-11-20).
const TABS = [
  { key: 'PREPARING', label: ['Đơn hàng cần chuẩn bị', 'Orders to prepare'] },
  { key: 'AWAITING_CARRIER', label: ['Chờ ĐVVC lấy hàng', 'Waiting for courier'] },
  { key: 'HANDED_OVER', label: ['Đã bàn giao ĐVVC', 'Handed to courier'] },
  { key: 'AWAITING_CASH', label: ['Chờ thu tiền mặt', 'Awaiting cash'] },
  { key: 'TO_HAND_OVER', label: ['Chờ khách nhận', 'Awaiting pickup'] },
  { key: 'HISTORY', label: ['Hoàn thành', 'Completed'] },
];
// Addresses and bookmarks of the former tabs keep working.
const LEGACY_TABS = { TO_SHIP: 'PREPARING', SHIPPING: 'HANDED_OVER' };

const DAY_MS = 24 * 60 * 60 * 1000;
const timeLeft = (deadline, t) => {
  const ms = new Date(deadline).getTime() - Date.now();
  if (ms <= 0) return t('đã quá hạn', 'overdue');
  const hours = Math.floor(ms / 3_600_000);
  const minutes = Math.floor((ms % 3_600_000) / 60_000);
  return hours >= 24
    ? t(`còn ${Math.floor(hours / 24)} ngày ${hours % 24} giờ`, `${Math.floor(hours / 24)} d ${hours % 24} h left`)
    : t(`còn ${Math.max(hours, 0)} giờ ${minutes} phút`, `${Math.max(hours, 0)} h ${minutes} min left`);
};



export default function StaffRetailOrdersPage({ initialTab }) {
  const t = useText();
  return <div className="ota-workspace">
    
    <main className="ota-main retail-staff-main">
      <div className="ota-topline"><span className="retail-staff-crumb"><Link to="/home">{t('← Trang chủ', '← Home')}</Link> {t('MLAND / ĐƠN BÁN LẺ', 'MLAND / RETAIL ORDERS')}</span><span>● Backend-authorized</span></div>
      <div className="ota-page-heading"><div><p>{t('Chuẩn bị hàng gửi GHTK, thu tiền mặt và giao hàng tại quầy cho chi nhánh của bạn.', 'Pack GHTK parcels, collect counter cash and hand over pickups for your branch.')}</p></div></div>
      <RequireSession requiredText={t('Đăng nhập Staff hoặc Manager được phân công chi nhánh.', 'Sign in as a Staff or Manager assigned to a branch.')}><StaffOrders initialTab={initialTab} /></RequireSession>
    </main>
  </div>;
}

/** The Staff order list without the page frame, also shown on the Staff home (Session 2026-11-22). */
export function StaffOrders({ initialTab }) {
  const api = useRetailApi();
  const format = useFormat();
  const t = useText();
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
  const branchName = locationId => branchNames.get(String(locationId)) || t(`Chi nhánh #${locationId}`, `Branch #${locationId}`);

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
    setNotice(t(`Đơn ${item.code}: đã thu ${format.money(order.payableAmount)}, đơn đã hoàn thành.`, `Order ${item.code}: ${format.money(order.payableAmount)} collected, order completed.`));
  });
  // Pickup: the order is packed and waits at the counter (Đang chuẩn bị hàng → Chờ khách nhận).
  const prepare = item => run(item.key, async () => {
    await api.prepareOrder(item.row.order.id);
    setNotice(t(`Đơn ${item.code} đã chuẩn bị xong, chờ khách đến nhận.`, `Order ${item.code} is packed and waits for the customer.`));
  });
  const handOver = item => run(item.key, async () => {
    if (!item.row.pickupReadyConfirmed) await api.prepareOrder(item.row.order.id);
    await api.handOverOrder(item.row.order.id);
    setNotice(t(`Đã giao đơn ${item.code} cho khách.`, `Order ${item.code} handed to the customer.`));
  });
  const advance = item => {
    const next = nextShipmentStatus[item.row.status];
    const label = t(shipmentLabels[next], shipmentLabelsEn[next]);
    if (!next || !window.confirm(t(`Chuyển vận đơn ${item.row.mockTrackingNumber} sang “${label}”?`, `Move shipment ${item.row.mockTrackingNumber} to “${label}”?`))) return;
    run(item.key, async () => {
      await api.transitionStaffShipment(item.row.id, { nextStatus: next, expectedVersion: item.row.version });
      setDetails(current => { const copy = { ...current }; delete copy[item.row.id]; return copy; });
      setNotice(t(`Vận đơn ${item.row.mockTrackingNumber}: ${label}.`, `Shipment ${item.row.mockTrackingNumber}: ${label}.`));
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
      <div><span>{t('Tiền mặt đã thu hôm nay', 'Cash collected today')}</span><strong>{format.money(cashToday.toString())}</strong></div>
      <div className={expiringSoon ? 'warn' : ''}><span>{t('Sắp quá hạn giữ hàng (dưới 24 giờ)', 'Holds ending within 24 hours')}</span><strong>{expiringSoon}</strong></div>
      <div><span>{t('Việc đang chờ', 'Open tasks')}</span><strong>{openTasks}</strong></div>
    </div>
    <div className="staff-toolbar">
      <input className="order-search" value={search} onChange={event => setSearch(event.target.value)} placeholder={t('Mã đơn (ML…), mã vận đơn hoặc tên người nhận', 'Order code (ML…), tracking number or recipient name')} aria-label={t('Tìm đơn', 'Search orders')} />
      <button type="button" className="button outline" onClick={reloadAll} disabled={desk.loading || shipments.loading}>{t('Làm mới', 'Refresh')}</button>
    </div>
    <div className="order-tabs" role="tablist" aria-label={t('Việc của chi nhánh', 'Branch work')}>{TABS.map(item => <button key={item.key} type="button" role="tab" aria-selected={tab === item.key} className={tab === item.key ? 'active' : ''} onClick={() => setTab(item.key)}>{t(item.label)}{item.key !== 'HISTORY' && counts[item.key] ? <span className="order-tab-count">{counts[item.key]}</span> : null}</button>)}</div>
    {desk.error && <ErrorNotice error={desk.error} onRetry={desk.reload} />}
    {shipments.error && <ErrorNotice error={shipments.error} onRetry={shipments.reload} />}
    {tab === 'AWAITING_CASH' && <p className="muted small">{t('Khách đến quầy trả tiền: bấm "Xác nhận đã thu". Đơn chuyển thẳng sang Hoàn thành.', 'When the customer pays at the counter, press "Confirm collected". The order goes straight to Completed.')}</p>}
    <FlashToast message={notice} onDone={() => setNotice('')} />
    {error && <ErrorNotice error={error} />}
    {visible.length === 0 && <div className="order-empty"><p>{term ? t('Không tìm thấy đơn phù hợp.', 'No matching order.') : t('Không còn việc nào ở mục này.', 'Nothing left in this tab.')}</p></div>}
    {visible.map(item => item.kind === 'PICKUP'
      ? <PickupCard key={item.key} item={item} open={openKey === item.key} onToggle={() => toggle(item)} busy={busyId === item.key} format={format} branchName={branchName} onConfirmCash={confirmCash} onPrepare={prepare} onHandOver={handOver} />
      : <ShipmentCard key={item.key} item={item} open={openKey === item.key} onToggle={() => toggle(item)} busy={busyId === item.key} format={format} branchName={branchName} detail={details[item.row.id]} onAdvance={advance} />)}
  </section>;
}

function PickupCard({ item, open, onToggle, busy, format, branchName, onConfirmCash, onPrepare, onHandOver }) {
  const t = useText();
  const { row } = item;
  const order = row.order;
  const cash = order.paymentMethod === 'CASH_AT_COUNTER';
  const urgent = item.tab === 'AWAITING_CASH' && order.counterPaymentDeadline && new Date(order.counterPaymentDeadline).getTime() - Date.now() < DAY_MS;
  const pill = t({ HISTORY: ['Hoàn thành', 'Completed'], AWAITING_CASH: ['Chờ thu tiền mặt', 'Awaiting cash'], PREPARING: ['Đang chuẩn bị hàng', 'Preparing'], TO_HAND_OVER: ['Chờ khách nhận', 'Awaiting pickup'] }[item.tab]);
  return <article className={`order-card${urgent ? ' urgent' : ''}${open ? ' open' : ''}`} {...cardToggleProps(open, onToggle)}>
    <div className="order-card-top">
      <span className="order-code">{item.code}</span>
      <span className="order-branch">{branchName(order.locationId)} · {t('Nhận tại cửa hàng', 'Store pickup')}</span>
      <span className={`order-pill pill-${item.tab.toLowerCase()}`}>{pill}</span>
    </div>
    <ul className="order-items">{(order.items || []).map(line => <li key={`${order.id}-${line.variantId}`}>
      <ProductThumb className="order-thumb" productId={line.productId} />
      <span className="order-item-name"><strong>{line.productName || t(`Sản phẩm #${line.variantId}`, `Product #${line.variantId}`)}</strong><small>Size {line.size ?? '—'} · x{line.quantity}</small></span>
      <b>{line.lineTotal ? format.money(line.lineTotal) : ''}</b>
    </li>)}</ul>
    {item.tab === 'AWAITING_CASH' && <p className={`order-hint${urgent ? ' warn' : ''}`}>{t('Giữ hàng đến', 'Held until')} {displayTime(order.counterPaymentDeadline)} ({timeLeft(order.counterPaymentDeadline, t)}).</p>}
    {open && <dl className="order-detail">
      <div><dt>{t('Mã đơn', 'Order code')}</dt><dd>{item.code}</dd></div>
      <div><dt>{t('Thời gian đặt', 'Ordered at')}</dt><dd>{displayTime(order.orderedAt)}</dd></div>
      <div><dt>{t('Thanh toán', 'Payment')}</dt><dd>{cash ? t('Tiền mặt tại quầy', 'Cash at counter') : 'VNPay'}</dd></div>
      {order.completedAt && <div><dt>{t('Giao cho khách lúc', 'Handed over at')}</dt><dd>{displayTime(order.completedAt)}</dd></div>}
      <OrderMoneyRows order={order} format={format} />
      {row.cashCollection && <div><dt>{t('Tiền mặt', 'Cash')}</dt><dd>{t(`Thu ${format.money(row.cashCollection.amountReceived)}, thối ${format.money(row.cashCollection.changeReturned)} lúc ${displayTime(row.cashCollection.confirmedAt)}`, `Received ${format.money(row.cashCollection.amountReceived)}, change ${format.money(row.cashCollection.changeReturned)} at ${displayTime(row.cashCollection.confirmedAt)}`)}</dd></div>}
    </dl>}
    <div className="order-card-bottom">
      <span className="order-more">{open ? t('Thu gọn ▲', 'Show less ▲') : t('Bấm để xem chi tiết ▼', 'Click for details ▼')}</span>
      <span className="order-sum">{item.tab === 'AWAITING_CASH' ? t('Cần thu', 'To collect') : t('Thành tiền', 'Total')} <strong>{format.money(order.payableAmount)}</strong></span>
    </div>
    {item.tab === 'AWAITING_CASH' && <div className="order-actions">
      <button type="button" className="button primary" disabled={busy} onClick={() => onConfirmCash(item)}>{busy ? t('Đang ghi nhận…', 'Saving…') : t(`Xác nhận đã thu ${format.money(order.payableAmount)}`, `Confirm ${format.money(order.payableAmount)} collected`)}</button>
    </div>}
    {item.tab === 'PREPARING' && <div className="order-actions"><span className="muted">{cash ? t('Đã thu tiền mặt', 'Cash collected') : t('Đã thanh toán VNPay', 'Paid by VNPay')}</span><button type="button" className="button primary" disabled={busy} onClick={() => onPrepare(item)}>{busy ? t('Đang ghi nhận…', 'Saving…') : t('Đã chuẩn bị xong', 'Packed and ready')}</button></div>}
    {item.tab === 'TO_HAND_OVER' && <div className="order-actions"><button type="button" className="button primary" disabled={busy} onClick={() => onHandOver(item)}>{busy ? t('Đang ghi nhận…', 'Saving…') : t('Giao cho khách', 'Hand to customer')}</button></div>}
  </article>;
}

function ShipmentCard({ item, open, onToggle, busy, format, branchName, detail, onAdvance }) {
  const t = useText();
  const { row } = item;
  const next = nextShipmentStatus[row.status];
  const statusLabel = status => t(shipmentLabels[status], shipmentLabelsEn[status]);
  return <article className={`order-card${open ? ' open' : ''}`} {...cardToggleProps(open, onToggle)}>
    <div className="order-card-top">
      <span className="order-code">{item.code}</span>
      <span className="order-branch">{branchName(row.locationId)} · GHTK {row.mockTrackingNumber}</span>
      <span className={`order-pill pill-${item.tab.toLowerCase()}`}>{statusLabel(row.status)}</span>
    </div>
    <div className="order-recipient">
      <span className="order-thumb" aria-hidden="true">⛟</span>
      <span className="order-item-name"><strong>{row.recipientName || t('Chưa có tên người nhận', 'No recipient name')}</strong><small>{[row.wardName, row.districtName, row.provinceName].filter(Boolean).join(', ')}</small></span>
      {row.itemQuantity ? <b>{row.itemQuantity} {t('sản phẩm', 'items')}</b> : null}
    </div>
    {open && <dl className="order-detail">
      <div><dt>{t('Mã đơn', 'Order code')}</dt><dd>{item.code}</dd></div>
      <div><dt>{t('Mã vận đơn', 'Tracking number')}</dt><dd>{row.mockTrackingNumber}</dd></div>
      {detail ? <>
        <div><dt>{t('Điện thoại', 'Phone')}</dt><dd>{detail.recipientPhone || '—'}</dd></div>
        <div className="wide"><dt>{t('Địa chỉ', 'Address')}</dt><dd>{[detail.recipientAddress, detail.wardName, detail.districtName, detail.provinceName].filter(Boolean).join(', ')}</dd></div>
        <div className="wide"><dt>{t('Lịch sử', 'History')}</dt><dd><ol className="order-timeline">{(detail.history || []).map((entry, index) => <li key={index}><strong>{statusLabel(entry.status)}</strong> · {displayTime(entry.createdAt)} · {entry.actorLabel}</li>)}</ol></dd></div>
      </> : <div className="wide"><dt>{t('Chi tiết', 'Details')}</dt><dd>{t('Đang tải…', 'Loading…')}</dd></div>}
    </dl>}
    <div className="order-card-bottom">
      <span className="order-more">{open ? t('Thu gọn ▲', 'Show less ▲') : t('Bấm để xem chi tiết ▼', 'Click for details ▼')}</span>
      {row.payableAmount && <span className="order-sum">{t('Thành tiền', 'Total')} <strong>{format.money(row.payableAmount)}</strong></span>}
    </div>
    {next && <div className="order-actions"><button type="button" className="button primary" disabled={busy} onClick={() => onAdvance(item)}>{busy ? t('Đang lưu…', 'Saving…') : t(nextShipmentAction[row.status])}</button></div>}
    {courierWaiting[row.status] && <p className="order-hint">{t(courierWaiting[row.status])}</p>}
  </article>;
}
