import React, { useMemo, useState } from 'react';
import { Link } from 'react-router';
import { RequireSession } from '../../../../shared/auth/RequireSession';
import { useFormat } from '../../../../shared/i18n/I18nProvider';
import { ErrorNotice, Loading, SuccessNotice, useResource } from '../../../../shared/ui/components';
import { newCommandId } from '../../../../shared/api/http';
import { useRetailApi } from '../../application/useRetailApi';
import { OperationsSidebar } from '../../../../shared/ui/OperationsSidebar';
import { cardToggleProps, displayTime, orderCode, OrderMoneyRows } from '../orderCode';

/**
 * FE-08 FR-08-051: one Staff page for the branch's retail work. Tabs follow the day's tasks; each card offers only
 * the action of its tab. The former pickup-desk and shipment pages open the matching tab.
 */
export const shipmentLabels = { PREPARING: 'Đang chuẩn bị hàng', HANDED_TO_CARRIER: 'Đã giao cho GHTK', IN_TRANSIT: 'Đang vận chuyển', DELIVERED: 'Giao thành công' };
export const nextShipmentStatus = { PREPARING: 'HANDED_TO_CARRIER', HANDED_TO_CARRIER: 'IN_TRANSIT', IN_TRANSIT: 'DELIVERED' };
const nextShipmentAction = { PREPARING: 'Bàn giao GHTK', HANDED_TO_CARRIER: 'Xác nhận đang vận chuyển', IN_TRANSIT: 'Xác nhận đã giao' };
const SHIPMENT_STATUSES = Object.keys(shipmentLabels);
const TABS = [
  { key: 'AWAITING_CASH', label: 'Chờ thu tiền mặt' },
  { key: 'TO_HAND_OVER', label: 'Chờ khách nhận' },
  { key: 'TO_SHIP', label: 'Cần gửi hàng' },
  { key: 'SHIPPING', label: 'Đang giao' },
  { key: 'HISTORY', label: 'Lịch sử' },
];
const OPEN_TASK_TABS = ['AWAITING_CASH', 'TO_HAND_OVER', 'TO_SHIP'];
const DAY_MS = 24 * 60 * 60 * 1000;
const timeLeft = deadline => {
  const ms = new Date(deadline).getTime() - Date.now();
  if (ms <= 0) return 'đã quá hạn';
  const hours = Math.floor(ms / 3_600_000);
  return hours >= 24 ? `còn ${Math.floor(hours / 24)} ngày ${hours % 24} giờ` : `còn ${Math.max(hours, 0)} giờ ${Math.floor((ms % 3_600_000) / 60_000)} phút`;
};

function pickupTab(row) {
  if (row.order.paymentState === 'ACCEPTED_UNPAID') return 'AWAITING_CASH';
  return row.fulfilmentState === 'PICKED_UP' ? 'HISTORY' : 'TO_HAND_OVER';
}
function shipmentTab(row) {
  if (row.status === 'PREPARING') return 'TO_SHIP';
  return row.status === 'DELIVERED' ? 'HISTORY' : 'SHIPPING';
}

export default function StaffRetailOrdersPage({ initialTab = 'AWAITING_CASH' }) {
  return <div className="ota-workspace">
    <OperationsSidebar />
    <main className="ota-main retail-staff-main">
      <div className="ota-topline"><span className="retail-staff-crumb"><Link to="/home">← Trang chủ</Link> MLAND / ĐƠN BÁN LẺ</span><span>● Backend-authorized</span></div>
      <div className="ota-page-heading"><div><p className="ota-eyebrow">FE-08 · Bán lẻ</p><h1>Đơn bán lẻ tại chi nhánh</h1><p>Thu tiền mặt, giao hàng tại quầy và gửi hàng GHTK cho chi nhánh của bạn. Vận đơn GHTK là mô phỏng.</p></div></div>
      <RequireSession requiredText="Đăng nhập Staff hoặc Manager được phân công chi nhánh."><StaffOrders initialTab={initialTab} /></RequireSession>
    </main>
  </div>;
}

function StaffOrders({ initialTab }) {
  const api = useRetailApi();
  const format = useFormat();
  const [tab, setTab] = useState(TABS.some(item => item.key === initialTab) ? initialTab : 'AWAITING_CASH');
  const [search, setSearch] = useState('');
  const [received, setReceived] = useState({});
  const [handOverNow, setHandOverNow] = useState(true);
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
    const amount = String(received[order.id] ?? order.payableAmount).trim();
    if (!/^[1-9][0-9]*$/.test(amount) || BigInt(amount) < BigInt(order.payableAmount)) {
      throw new Error(`Tiền khách đưa phải từ ${format.money(order.payableAmount)} trở lên.`);
    }
    const result = await api.confirmCashCollection(order.id, amount, newCommandId());
    let message = `Đơn ${item.code}: đã thu ${format.money(result.appliedAmount)}, tiền thối ${format.money(result.changeReturned)}.`;
    if (handOverNow) {
      await api.prepareOrder(order.id);
      await api.handOverOrder(order.id);
      message += ' Đã giao hàng cho khách.';
    }
    setNotice(message);
  });
  const handOver = item => run(item.key, async () => {
    if (!item.row.pickupReadyConfirmed) await api.prepareOrder(item.row.order.id);
    await api.handOverOrder(item.row.order.id);
    setNotice(`Đã giao đơn ${item.code} cho khách.`);
  });
  const advance = item => {
    const next = nextShipmentStatus[item.row.status];
    if (!next || !window.confirm(`Chuyển vận đơn ${item.row.mockTrackingNumber} sang “${shipmentLabels[next]}”? Đây là thao tác mô phỏng.`)) return;
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
    {tab === 'AWAITING_CASH' && <label className="check-row"><input type="checkbox" checked={handOverNow} onChange={event => setHandOverNow(event.target.checked)} /> Giao hàng cho khách ngay sau khi thu tiền</label>}
    {notice && <SuccessNotice>{notice}</SuccessNotice>}
    {error && <ErrorNotice error={error} />}
    {visible.length === 0 && <div className="order-empty"><p>{term ? 'Không tìm thấy đơn phù hợp.' : 'Không còn việc nào ở mục này.'}</p></div>}
    {visible.map(item => item.kind === 'PICKUP'
      ? <PickupCard key={item.key} item={item} open={openKey === item.key} onToggle={() => toggle(item)} busy={busyId === item.key} format={format} branchName={branchName} received={received} setReceived={setReceived} onConfirmCash={confirmCash} onHandOver={handOver} />
      : <ShipmentCard key={item.key} item={item} open={openKey === item.key} onToggle={() => toggle(item)} busy={busyId === item.key} format={format} branchName={branchName} detail={details[item.row.id]} onAdvance={advance} />)}
  </section>;
}

function PickupCard({ item, open, onToggle, busy, format, branchName, received, setReceived, onConfirmCash, onHandOver }) {
  const { row } = item;
  const order = row.order;
  const cash = order.paymentMethod === 'CASH_AT_COUNTER';
  const amount = received[order.id] ?? order.payableAmount;
  const change = /^[0-9]+$/.test(String(amount)) && BigInt(amount) >= BigInt(order.payableAmount) ? (BigInt(amount) - BigInt(order.payableAmount)).toString() : null;
  const urgent = item.tab === 'AWAITING_CASH' && order.counterPaymentDeadline && new Date(order.counterPaymentDeadline).getTime() - Date.now() < DAY_MS;
  const pill = item.tab === 'HISTORY' ? 'Đã giao cho khách' : item.tab === 'AWAITING_CASH' ? 'Chờ thu tiền mặt' : cash ? 'Đã thu tiền mặt' : 'Đã trả VNPay';
  return <article className={`order-card${urgent ? ' urgent' : ''}${open ? ' open' : ''}`} {...cardToggleProps(open, onToggle)}>
    <div className="order-card-top">
      <span className="order-code">{item.code}</span>
      <span className="order-branch">{branchName(order.locationId)} · Nhận tại cửa hàng</span>
      <span className={`order-pill pill-${item.tab.toLowerCase()}`}>{pill}</span>
    </div>
    <ul className="order-items">{(order.items || []).map(line => <li key={`${order.id}-${line.variantId}`}>
      <span className="order-thumb" aria-hidden="true">◇</span>
      <span className="order-item-name"><strong>{line.productName || `Sản phẩm #${line.variantId}`}</strong><small>Size {line.size ?? '—'} · x{line.quantity}</small></span>
      <b>{line.lineTotal ? format.money(line.lineTotal) : ''}</b>
    </li>)}</ul>
    {item.tab === 'AWAITING_CASH' && <p className={`order-hint${urgent ? ' warn' : ''}`}>Giữ hàng đến {displayTime(order.counterPaymentDeadline)} ({timeLeft(order.counterPaymentDeadline)}).</p>}
    {open && <dl className="order-detail">
      <div><dt>Mã đơn</dt><dd>{item.code}</dd></div>
      <div><dt>Thời gian đặt</dt><dd>{displayTime(order.orderedAt)}</dd></div>
      <div><dt>Thanh toán</dt><dd>{cash ? 'Tiền mặt tại quầy' : 'VNPay'}</dd></div>
      <OrderMoneyRows order={order} format={format} />
      {row.cashCollection && <div><dt>Tiền mặt</dt><dd>Thu {format.money(row.cashCollection.amountReceived)}, thối {format.money(row.cashCollection.changeReturned)} lúc {displayTime(row.cashCollection.confirmedAt)}</dd></div>}
    </dl>}
    <div className="order-card-bottom">
      <span className="order-more">{open ? 'Thu gọn ▲' : 'Bấm để xem chi tiết ▼'}</span>
      <span className="order-sum">{item.tab === 'AWAITING_CASH' ? 'Cần thu' : 'Thành tiền'} <strong>{format.money(order.payableAmount)}</strong></span>
    </div>
    {item.tab === 'AWAITING_CASH' && <div className="order-actions">
      <label className="cash-input">Khách đưa<input inputMode="numeric" value={amount} onChange={event => setReceived(current => ({ ...current, [order.id]: event.target.value.replace(/[^0-9]/g, '') }))} /></label>
      <span className="muted">Tiền thối: {change == null ? '—' : format.money(change)}</span>
      <button type="button" className="button primary" disabled={busy} onClick={() => onConfirmCash(item)}>{busy ? 'Đang ghi nhận…' : 'Xác nhận đã thu'}</button>
    </div>}
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
  </article>;
}
