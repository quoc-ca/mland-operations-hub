import React, { useMemo, useState } from 'react';
import { RequireSession } from '../../../../shared/auth/RequireSession';
import { useFormat } from '../../../../shared/i18n/I18nProvider';
import { ErrorNotice, Loading, SuccessNotice, useResource } from '../../../../shared/ui/components';
import { newCommandId } from '../../../../shared/api/http';
import { useRetailApi } from '../../application/useRetailApi';

/** FE-08 Staff pickup desk: confirm counter cash (FR-08-045) and record the handover. */
const tabs = [
  { key: 'AWAITING_CASH', label: 'Chờ thu tiền' },
  { key: 'TO_HAND_OVER', label: 'Chờ giao khách' },
  { key: 'DONE', label: 'Đã giao' },
];
const displayTime = value => value
  ? new Date(value).toLocaleString('vi-VN', { timeZone: 'Asia/Ho_Chi_Minh', dateStyle: 'short', timeStyle: 'short' })
  : '—';
const shortCode = value => String(value || '').slice(0, 8).toUpperCase();
const tabOf = row => {
  if (row.order.paymentState === 'ACCEPTED_UNPAID') return 'AWAITING_CASH';
  return row.fulfilmentState === 'PICKED_UP' ? 'DONE' : 'TO_HAND_OVER';
};

export default function StaffPickupPage() {
  return <main className="container public-page retail-page retail-shopping"><div className="retail-page-heading"><div><p className="retail-kicker">FE-08 · STAFF</p><h1>Đơn nhận tại cửa hàng</h1><p>Thu tiền mặt tại quầy và giao hàng cho khách tại chi nhánh của bạn.</p></div></div>
    <RequireSession requiredText="Đăng nhập Staff hoặc Manager được phân công chi nhánh."><PickupDesk /></RequireSession></main>;
}

function PickupDesk() {
  const api = useRetailApi();
  const format = useFormat();
  const [tab, setTab] = useState('AWAITING_CASH');
  const [search, setSearch] = useState('');
  const [received, setReceived] = useState({});
  const [handOverNow, setHandOverNow] = useState(true);
  const [busyId, setBusyId] = useState(null);
  const [error, setError] = useState(null);
  const [notice, setNotice] = useState('');
  const desk = useResource(() => api.listStaffPickups(), []);
  const branches = useResource(() => api.listBranches(), []);
  const branchNames = useMemo(() => new Map((branches.data?.items || branches.data || []).map(item => [String(item.id), item.name])), [branches.data]);
  const rows = Array.isArray(desk.data) ? desk.data : [];
  const term = search.trim().toUpperCase();
  const visible = rows.filter(row => tabOf(row) === tab && (!term || shortCode(row.order.id).includes(term) || String(row.order.id).toUpperCase().includes(term)));
  const counts = tabs.reduce((acc, item) => ({ ...acc, [item.key]: rows.filter(row => tabOf(row) === item.key).length }), {});

  const run = async (row, action) => {
    setBusyId(row.order.id); setError(null); setNotice('');
    try { await action(); await desk.reload(); } catch (e) { setError(e); } finally { setBusyId(null); }
  };
  const confirmCash = row => run(row, async () => {
    const amount = (received[row.order.id] ?? row.order.payableAmount).trim();
    if (!/^[1-9][0-9]*$/.test(amount) || BigInt(amount) < BigInt(row.order.payableAmount)) {
      throw new Error(`Tiền khách đưa phải từ ${format.money(row.order.payableAmount)} trở lên.`);
    }
    const result = await api.confirmCashCollection(row.order.id, amount, newCommandId());
    let message = `Đã thu ${format.money(result.appliedAmount)}. Tiền thối: ${format.money(result.changeReturned)}.`;
    if (handOverNow) {
      await api.prepareOrder(row.order.id);
      await api.handOverOrder(row.order.id);
      message += ' Đã giao hàng cho khách.';
    }
    setNotice(message);
  });
  const handOver = row => run(row, async () => {
    if (!row.pickupReadyConfirmed) await api.prepareOrder(row.order.id);
    await api.handOverOrder(row.order.id);
    setNotice(`Đã giao đơn #${shortCode(row.order.id)} cho khách.`);
  });

  if (desk.loading && !desk.data) return <Loading />;
  if (desk.error) return <ErrorNotice error={desk.error} onRetry={desk.reload} />;
  return <section className="pickup-desk">
    <div className="pickup-desk-toolbar">
      <div className="pickup-desk-tabs" role="tablist">{tabs.map(item => <button key={item.key} type="button" role="tab" aria-selected={tab === item.key} className={tab === item.key ? 'active' : ''} onClick={() => setTab(item.key)}>{item.label} ({counts[item.key] || 0})</button>)}</div>
      <input className="pickup-desk-search" value={search} onChange={event => setSearch(event.target.value)} placeholder="Tìm mã đơn (8 ký tự)" aria-label="Tìm mã đơn" />
      <button type="button" className="button outline" onClick={desk.reload} disabled={desk.loading}>Làm mới</button>
    </div>
    {tab === 'AWAITING_CASH' && <label className="check-row"><input type="checkbox" checked={handOverNow} onChange={event => setHandOverNow(event.target.checked)} /> Giao hàng cho khách ngay sau khi thu tiền</label>}
    {notice && <SuccessNotice>{notice}</SuccessNotice>}
    {error && <ErrorNotice error={error} />}
    {visible.length === 0 && <p className="workspace-card empty-state">Không có đơn nào ở mục này.</p>}
    {visible.map(row => {
      const order = row.order;
      const amount = received[order.id] ?? order.payableAmount;
      const change = /^[0-9]+$/.test(amount) && BigInt(amount) >= BigInt(order.payableAmount) ? (BigInt(amount) - BigInt(order.payableAmount)).toString() : null;
      return <article className="workspace-card pickup-order" key={order.id}>
        <div className="pickup-order-head"><strong>Đơn #{shortCode(order.id)}</strong><span className="muted">{branchNames.get(String(order.locationId)) || `Chi nhánh #${order.locationId}`} · Đặt lúc {displayTime(order.orderedAt)}</span><span className="status">{order.paymentMethod === 'CASH_AT_COUNTER' ? 'Tiền mặt tại quầy' : 'VNPay'}</span></div>
        <ul>{(order.items || []).map(item => <li key={`${order.id}-${item.variantId}`}>{item.productName || `Sản phẩm #${item.variantId}`} · Size {item.size ?? '—'} × {item.quantity}</li>)}</ul>
        <div className="pickup-order-amount">Cần thu: <strong>{format.money(order.payableAmount)}</strong>{order.counterPaymentDeadline && tab === 'AWAITING_CASH' && <span className="muted"> · Giữ hàng đến {displayTime(order.counterPaymentDeadline)}</span>}</div>
        {tab === 'AWAITING_CASH' && <div className="pickup-order-actions">
          <label>Tiền khách đưa<input inputMode="numeric" value={amount} onChange={event => setReceived(current => ({ ...current, [order.id]: event.target.value.replace(/[^0-9]/g, '') }))} /></label>
          <span className="muted">Tiền thối: {change == null ? '—' : format.money(change)}</span>
          <button type="button" className="button primary" disabled={busyId === order.id} onClick={() => confirmCash(row)}>{busyId === order.id ? 'Đang ghi nhận…' : 'Đã thu tiền mặt'}</button>
        </div>}
        {tab === 'TO_HAND_OVER' && <div className="pickup-order-actions">
          {row.cashCollection && <span className="muted">Đã thu {format.money(row.cashCollection.appliedAmount)} lúc {displayTime(row.cashCollection.confirmedAt)}</span>}
          <button type="button" className="button primary" disabled={busyId === order.id} onClick={() => handOver(row)}>{busyId === order.id ? 'Đang ghi nhận…' : 'Đã giao cho khách'}</button>
        </div>}
        {tab === 'DONE' && row.cashCollection && <p className="muted">Tiền mặt: thu {format.money(row.cashCollection.amountReceived)}, thối {format.money(row.cashCollection.changeReturned)} lúc {displayTime(row.cashCollection.confirmedAt)}</p>}
      </article>;
    })}
  </section>;
}
