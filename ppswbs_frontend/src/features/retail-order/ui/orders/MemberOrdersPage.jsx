import React, { useMemo, useState } from 'react';
import { Link, useNavigate } from 'react-router';
import { RequireSession } from '../../../../shared/auth/RequireSession';
import { useFormat } from '../../../../shared/i18n/I18nProvider';
import { ErrorNotice, Loading, useCommand, useResource } from '../../../../shared/ui/components';
import { useRetailApi } from '../../application/useRetailApi';
import { cardToggleProps, displayTime, orderCode, OrderMoneyRows } from '../orderCode';

/** FE-08 FR-08-050: "Đơn mua" grouped into marketplace-style tabs, one card per branch order. */
const TABS = [
  { key: 'ALL', label: 'Tất cả' },
  { key: 'AWAITING_PAYMENT', label: 'Chờ thanh toán' },
  { key: 'AWAITING_PICKUP', label: 'Chờ nhận tại cửa hàng' },
  { key: 'SHIPPING', label: 'Đang giao' },
  { key: 'COMPLETED', label: 'Hoàn thành' },
  { key: 'CANCELLED', label: 'Đã hủy' },
];
const shippingSteps = {
  SHIPMENT_PENDING: 'Đang chuẩn bị hàng',
  HANDED_TO_CARRIER: 'Đã giao cho GHTK',
  IN_TRANSIT: 'Đang vận chuyển',
};

export function orderTab(order) {
  if (order.paymentState === 'CANCELLED' || order.paymentState === 'EXPIRED') return 'CANCELLED';
  if (order.fulfilmentState === 'PICKED_UP' || order.fulfilmentState === 'DELIVERED') return 'COMPLETED';
  const cash = order.paymentMethod === 'CASH_AT_COUNTER';
  if (!cash && order.paymentState !== 'PAID') return 'AWAITING_PAYMENT';
  return order.fulfilmentMethod === 'GHTK' ? 'SHIPPING' : 'AWAITING_PICKUP';
}

function statusLabel(order, tab) {
  if (tab === 'CANCELLED') return order.paymentState === 'EXPIRED' ? 'Đã hủy · quá hạn' : 'Đã hủy';
  if (tab === 'COMPLETED') return order.fulfilmentState === 'DELIVERED' ? 'Giao thành công' : 'Đã nhận hàng';
  if (tab === 'AWAITING_PAYMENT') return order.paymentState === 'PAYMENT_INITIATED' ? 'Chờ kết quả VNPay' : 'Chờ thanh toán';
  if (tab === 'SHIPPING') return shippingSteps[order.fulfilmentState] || 'Đang giao';
  return order.paymentState === 'PAID' ? 'Sẵn sàng nhận hàng' : 'Chờ trả tiền tại quầy';
}

export default function MemberOrdersPage() {
  return <main className="container public-page retail-page retail-shopping"><div className="retail-page-heading"><div><p className="retail-kicker">FE-08 · MEMBER</p><h1>Đơn mua của tôi</h1></div><Link to="/products" className="retail-back-link">← Tiếp tục mua sắm</Link></div><RequireSession requiredText="Đăng nhập để xem đơn mua."><OrdersContent /></RequireSession></main>;
}

function OrdersContent() {
  const api = useRetailApi();
  const format = useFormat();
  const navigate = useNavigate();
  const orders = useResource(() => api.listOrders(), []);
  const branches = useResource(() => api.listBranches(), []);
  const action = useCommand();
  const [tab, setTab] = useState('ALL');
  const [search, setSearch] = useState('');
  const [busyId, setBusyId] = useState(null);
  const [openId, setOpenId] = useState(null);
  const branchNames = useMemo(() => new Map((branches.data?.items || branches.data || []).map(item => [String(item.id), item.name])), [branches.data]);
  const branchName = locationId => branchNames.get(String(locationId)) || `Chi nhánh #${locationId}`;
  const values = useMemo(() => (Array.isArray(orders.data) ? orders.data : orders.data?.items || [])
    .slice().sort((a, b) => String(b.orderedAt || '').localeCompare(String(a.orderedAt || ''))), [orders.data]);
  const counts = useMemo(() => values.reduce((acc, order) => { const key = orderTab(order); acc[key] = (acc[key] || 0) + 1; return acc; }, { ALL: values.length }), [values]);
  const ordersPerCheckout = useMemo(() => values.reduce((acc, order) => { if (order.checkoutId) acc.set(order.checkoutId, (acc.get(order.checkoutId) || 0) + 1); return acc; }, new Map()), [values]);
  const term = search.trim().toLowerCase();
  const visible = values.filter(order => (tab === 'ALL' || orderTab(order) === tab)
    && (!term || orderCode(order).toLowerCase().includes(term) || (order.items || []).some(item => String(item.productName || '').toLowerCase().includes(term))));

  const perform = async (order, send, after) => {
    setBusyId(order.id);
    const result = await action.run(send);
    setBusyId(null);
    if (result.ok) await after(result.result);
  };
  const pay = order => perform(order, key => api.initiatePayment(order.checkoutId, key), result => {
    if (result?.paymentUrl) window.location.assign(result.paymentUrl); else orders.reload();
  });
  // A cash branch order is cancelled on its own; the other branch orders of the same checkout stay (FR-08-046).
  const cancelCashOrder = order => {
    if (!window.confirm(`Hủy đơn ${orderCode(order)}? Sản phẩm sẽ được trả lại giỏ hàng.`)) return;
    perform(order, key => api.cancelOrder(order.id, key), () => orders.reload());
  };
  const cancel = order => {
    const together = ordersPerCheckout.get(order.checkoutId) || 1;
    if (together > 1 && !window.confirm(`Đơn này được đặt cùng ${together - 1} đơn khác. Hủy sẽ hủy cả ${together} đơn của lần đặt đó. Tiếp tục?`)) return;
    perform(order, key => api.cancelCheckout(order.checkoutId, key), () => orders.reload());
  };
  const buyAgain = order => perform(order, async () => {
    for (const item of order.items || []) {
      await api.addLine({ variantId: item.variantId, locationId: order.locationId, quantity: item.quantity }); // eslint-disable-line no-await-in-loop
    }
  }, () => navigate('/member/cart'));

  if (orders.loading && !orders.data) return <Loading />;
  if (orders.error) return <ErrorNotice error={orders.error} onRetry={orders.reload} />;
  return <section className="order-tabs-page">
    <div className="order-tabs" role="tablist" aria-label="Trạng thái đơn mua">{TABS.map(item => <button key={item.key} type="button" role="tab" aria-selected={tab === item.key} className={tab === item.key ? 'active' : ''} onClick={() => setTab(item.key)}>{item.label}{item.key !== 'ALL' && counts[item.key] ? <span className="order-tab-count">{counts[item.key]}</span> : null}</button>)}</div>
    <input className="order-search" value={search} onChange={event => setSearch(event.target.value)} placeholder="Tìm theo mã đơn (ML…) hoặc tên sản phẩm" aria-label="Tìm đơn mua" />
    {action.error && <ErrorNotice error={action.error} onRetry={action.retry} onReload={orders.reload} />}
    {visible.length === 0 && <div className="order-empty"><p>{values.length ? 'Chưa có đơn hàng nào ở mục này.' : 'Bạn chưa có đơn mua nào.'}</p>{!values.length && <Link className="button primary" to="/products">Mua sắm ngay</Link>}</div>}
    {visible.map(order => {
      const current = orderTab(order);
      const cash = order.paymentMethod === 'CASH_AT_COUNTER';
      const busy = busyId === order.id;
      const open = openId === order.id;
      const together = ordersPerCheckout.get(order.checkoutId) || 1;
      return <article className={`order-card${open ? ' open' : ''}`} key={order.id} {...cardToggleProps(open, () => setOpenId(open ? null : order.id))}>
        <div className="order-card-top">
          <span className="order-code">{orderCode(order)}</span>
          <span className="order-branch">{branchName(order.locationId)}</span>
          <span className={`order-pill pill-${current.toLowerCase()}`}>{statusLabel(order, current)}</span>
        </div>
        <ul className="order-items">{(order.items || []).map(item => <li key={`${order.id}-${item.variantId}`}>
          <span className="order-thumb" aria-hidden="true">◇</span>
          <span className="order-item-name"><strong>{item.productName || `Sản phẩm #${item.variantId}`}</strong><small>Size {item.size ?? '—'} · x{item.quantity}</small></span>
          <b>{item.lineTotal ? format.money(item.lineTotal) : ''}</b>
        </li>)}</ul>
        {current === 'AWAITING_PICKUP' && !cash && <p className="order-hint">Đã thanh toán. Đọc mã <strong>{orderCode(order)}</strong> tại quầy {branchName(order.locationId)} để nhận hàng.</p>}
        {current === 'AWAITING_PICKUP' && cash && <p className="order-hint warn">Đến {branchName(order.locationId)} trả <strong>{format.money(order.payableAmount)}</strong> tiền mặt trước <strong>{displayTime(order.counterPaymentDeadline)}</strong>. Quá hạn đơn tự hủy.</p>}
        {current === 'AWAITING_PAYMENT' && together > 1 && <p className="order-hint">Thanh toán chung cho {together} đơn của cùng lần đặt.</p>}
        {open && <dl className="order-detail">
          <div><dt>Mã đơn</dt><dd>{orderCode(order)}</dd></div>
          <div><dt>Thời gian đặt</dt><dd>{displayTime(order.orderedAt)}</dd></div>
          <div><dt>Chi nhánh</dt><dd>{branchName(order.locationId)}</dd></div>
          <div><dt>Nhận hàng</dt><dd>{order.fulfilmentMethod === 'GHTK' ? 'Giao tận nơi qua GHTK' : 'Nhận tại cửa hàng'}</dd></div>
          <div><dt>Thanh toán</dt><dd>{cash ? 'Tiền mặt tại quầy' : 'VNPay'}</dd></div>
          {cash && order.counterPaymentDeadline && current !== 'COMPLETED' && <div><dt>Hạn giữ hàng</dt><dd>{displayTime(order.counterPaymentDeadline)}</dd></div>}
          <OrderMoneyRows order={order} format={format} together={together} />
        </dl>}
        <div className="order-card-bottom">
          <span className="order-more">{open ? 'Thu gọn ▲' : 'Bấm để xem chi tiết ▼'}</span>
          <span className="order-sum">Thành tiền <strong>{format.money(order.payableAmount)}</strong></span>
        </div>
        {(current === 'AWAITING_PAYMENT' || current === 'COMPLETED' || current === 'CANCELLED' || (current === 'AWAITING_PICKUP' && cash && order.paymentState === 'ACCEPTED_UNPAID')) && <div className="order-actions">
          {current === 'AWAITING_PAYMENT' && order.paymentState === 'ACCEPTED_UNPAID' && order.checkoutId && <>
            <button type="button" className="button outline" disabled={busy} onClick={() => cancel(order)}>Hủy đơn</button>
            <button type="button" className="button primary" disabled={busy} onClick={() => pay(order)}>{busy ? 'Đang xử lý…' : 'Thanh toán'}</button>
          </>}
          {current === 'AWAITING_PICKUP' && cash && order.paymentState === 'ACCEPTED_UNPAID' && <button type="button" className="button outline" disabled={busy} onClick={() => cancelCashOrder(order)}>Hủy đơn</button>}
          {(current === 'COMPLETED' || current === 'CANCELLED') && <button type="button" className={`button ${current === 'COMPLETED' ? 'primary' : 'outline'}`} disabled={busy} onClick={() => buyAgain(order)}>{busy ? 'Đang thêm…' : 'Mua lại'}</button>}
        </div>}
      </article>;
    })}
  </section>;
}
