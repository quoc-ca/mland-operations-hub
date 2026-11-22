import React, { useEffect, useMemo, useRef, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router';
import { RequireSession } from '../../../../shared/auth/RequireSession';
import { useFormat } from '../../../../shared/i18n/I18nProvider';
import { ErrorNotice, Loading, useCommand, useResource } from '../../../../shared/ui/components';
import { FlashToast } from '../FlashToast';
import { useRetailApi } from '../../application/useRetailApi';
import { cardToggleProps, displayTime, orderCode, OrderMoneyRows } from '../orderCode';
import { ProductThumb } from '../ProductThumb';

/**
 * FE-08 FR-08-050: "Đơn mua" in marketplace-style tabs, one card per branch order, newest first (Session 2026-11-18).
 * GHTK orders go Chờ lấy hàng → Chờ giao hàng → Đã giao; pickup orders wait in Chờ nhận tại cửa hàng.
 */
const TABS = [
  { key: 'ALL', label: 'Tất cả' },
  { key: 'AWAITING_PAYMENT', label: 'Chờ thanh toán' },
  { key: 'AWAITING_COLLECTION', label: 'Chờ lấy hàng' },
  { key: 'AWAITING_DELIVERY', label: 'Chờ giao hàng' },
  { key: 'AWAITING_PICKUP', label: 'Chờ nhận tại cửa hàng' },
  { key: 'COMPLETED', label: 'Đã giao' },
  { key: 'CANCELLED', label: 'Đã hủy' },
];
const DELIVERING = ['HANDED_TO_CARRIER', 'IN_TRANSIT'];

export function orderTab(order) {
  if (order.paymentState === 'CANCELLED' || order.paymentState === 'EXPIRED') return 'CANCELLED';
  if (order.fulfilmentState === 'PICKED_UP' || order.fulfilmentState === 'DELIVERED') return 'COMPLETED';
  const cash = order.paymentMethod === 'CASH_AT_COUNTER';
  if (!cash && order.paymentState !== 'PAID') return 'AWAITING_PAYMENT';
  if (order.fulfilmentMethod === 'GHTK') return DELIVERING.includes(order.fulfilmentState) ? 'AWAITING_DELIVERY' : 'AWAITING_COLLECTION';
  return 'AWAITING_PICKUP';
}

export function statusLabel(order, tab = orderTab(order)) {
  if (tab === 'CANCELLED') return order.paymentState === 'EXPIRED' ? 'Đã hủy · quá hạn' : 'Đã hủy';
  if (tab === 'COMPLETED') return order.fulfilmentState === 'DELIVERED' ? 'Giao thành công' : 'Đã nhận tại cửa hàng';
  if (tab === 'AWAITING_PAYMENT') return 'Chờ thanh toán';
  if (tab === 'AWAITING_COLLECTION') return order.fulfilmentState === 'READY_FOR_CARRIER' ? 'Chờ GHTK lấy hàng' : 'Đang chuẩn bị hàng';
  if (tab === 'AWAITING_DELIVERY') return 'Đang giao hàng';
  const ready = order.fulfilmentState === 'READY_FOR_PICKUP';
  if (order.paymentState !== 'PAID') return ready ? 'Sẵn sàng · trả tiền khi nhận' : 'Trả tiền khi nhận';
  return ready ? 'Sẵn sàng nhận hàng' : 'Đang chuẩn bị hàng';
}

/** The tab to open after an order was placed or paid: its delivery kind when all its branch orders agree, else "Tất cả". */
export function tabAfterPlacing(placed) {
  if (!placed.length) return 'ALL';
  const kinds = new Set(placed.map(order => (order.fulfilmentMethod === 'GHTK' ? 'AWAITING_COLLECTION' : 'AWAITING_PICKUP')));
  return kinds.size === 1 ? [...kinds][0] : 'ALL';
}

const countdown = (deadline, now) => {
  const ms = new Date(deadline).getTime() - now;
  if (!Number.isFinite(ms) || ms <= 0) return null;
  const minutes = Math.floor(ms / 60_000);
  const seconds = Math.floor((ms % 60_000) / 1000);
  return `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`;
};

export default function MemberOrdersPage() {
  return <main className="container public-page retail-page retail-shopping"><div className="retail-page-heading"><div><p className="retail-kicker">FE-08 · MEMBER</p><h1>Đơn mua của tôi</h1></div><Link to="/products" className="retail-back-link">← Tiếp tục mua sắm</Link></div><RequireSession requiredText="Đăng nhập để xem đơn mua."><OrdersContent /></RequireSession></main>;
}

function OrdersContent() {
  const api = useRetailApi();
  const format = useFormat();
  const navigate = useNavigate();
  const [params, setParams] = useSearchParams();
  const orders = useResource(() => api.listOrders(), []);
  const branches = useResource(() => api.listBranches(), []);
  const action = useCommand();
  const [tab, setTab] = useState('ALL');
  const [search, setSearch] = useState('');
  const [busyId, setBusyId] = useState(null);
  const [openId, setOpenId] = useState(null);
  const [notice, setNotice] = useState(null);
  const [placedCheckout, setPlacedCheckout] = useState(null);
  const [now, setNow] = useState(() => Date.now());
  const arrival = useRef(null);
  const branchNames = useMemo(() => new Map((branches.data?.items || branches.data || []).map(item => [String(item.id), item.name])), [branches.data]);
  const branchName = locationId => branchNames.get(String(locationId)) || `Chi nhánh #${locationId}`;
  // Newest first (Session 2026-11-18).
  const values = useMemo(() => (Array.isArray(orders.data) ? orders.data : orders.data?.items || [])
    .slice().sort((a, b) => String(b.orderedAt || '').localeCompare(String(a.orderedAt || ''))), [orders.data]);
  const counts = useMemo(() => values.reduce((acc, order) => { const key = orderTab(order); acc[key] = (acc[key] || 0) + 1; return acc; }, { ALL: values.length }), [values]);
  const ordersPerCheckout = useMemo(() => values.reduce((acc, order) => { if (order.checkoutId) acc.set(order.checkoutId, (acc.get(order.checkoutId) || 0) + 1); return acc; }, new Map()), [values]);

  // Coming back from VNPay (vnp_TxnRef, vnp_ResponseCode) or from placing an order (?checkout=): open the tab of
  // that order instead of "Tất cả". A Member who left VNPay without paying lands in "Chờ thanh toán".
  useEffect(() => {
    if (arrival.current === null) {
      const txnRef = params.get('vnp_TxnRef');
      const checkout = params.get('checkout');
      arrival.current = txnRef || checkout ? { txnRef, checkout, code: params.get('vnp_ResponseCode'), tries: 0 } : false;
      if (arrival.current) setParams({}, { replace: true });
    }
    const info = arrival.current;
    if (!info || !orders.data) return undefined;
    const placed = values.filter(order => order.checkoutId && (order.checkoutId === info.checkout || (info.txnRef && info.txnRef.includes(order.checkoutId))));
    if (!placed.length) { arrival.current = false; return undefined; }
    setPlacedCheckout(placed[0].checkoutId);
    if (info.txnRef && info.code !== '00') {
      arrival.current = false;
      setTab('AWAITING_PAYMENT');
      const deadline = placed[0].paymentDeadline;
      setNotice({ kind: 'warn', text: deadline && placed[0].paymentState !== 'PAID'
        ? `Bạn chưa hoàn tất thanh toán. Đơn và ưu đãi được giữ đến ${displayTime(deadline)}, bấm Thanh toán để tiếp tục.`
        : 'Bạn chưa hoàn tất thanh toán.' });
      return undefined;
    }
    // Back from VNPay without paying (browser Back) or an order not paid yet: show it in "Chờ thanh toán".
    if (!info.txnRef && placed.some(order => orderTab(order) === 'AWAITING_PAYMENT')) {
      arrival.current = false;
      setTab('AWAITING_PAYMENT');
      return undefined;
    }
    setTab(tabAfterPlacing(placed));
    // A cash order just placed: say where and until when to pay (the checkout page no longer does).
    const cashPlaced = placed.filter(order => order.paymentMethod === 'CASH_AT_COUNTER' && order.paymentState === 'ACCEPTED_UNPAID');
    if (!info.txnRef && cashPlaced.length) {
      arrival.current = false;
      const total = cashPlaced.reduce((sum, order) => sum + BigInt(order.payableAmount || '0'), 0n).toString();
      setNotice({ kind: 'success', text: `Đã đặt hàng. Đến ${cashPlaced.length > 1 ? `${cashPlaced.length} chi nhánh` : branchName(cashPlaced[0].locationId)} trước ${displayTime(cashPlaced[0].counterPaymentDeadline)} để trả ${format.money(total)} tiền mặt và nhận hàng.` });
      return undefined;
    }
    // VNPay may confirm a moment after the browser returns: look again a few times before showing the result.
    if (info.txnRef && placed.some(order => order.paymentState !== 'PAID') && info.tries < 3) {
      info.tries += 1;
      setNotice({ kind: 'info', text: 'Đang xác nhận thanh toán với VNPay…' });
      const timer = setTimeout(() => orders.reload(), 2000);
      return () => clearTimeout(timer);
    }
    arrival.current = false;
    if (info.txnRef) setNotice(placed.every(order => order.paymentState === 'PAID')
      ? { kind: 'success', text: 'Thanh toán thành công. Mland đang chuẩn bị hàng cho bạn.' }
      : { kind: 'warn', text: 'Chưa nhận được kết quả thanh toán từ VNPay. Trạng thái đơn sẽ tự cập nhật, bạn có thể tải lại trang sau ít phút.' });
    return undefined;
  }, [orders.data]); // eslint-disable-line react-hooks/exhaustive-deps

  // The "Chờ thanh toán" countdown ticks only while such an order exists.
  const anyAwaitingPayment = values.some(order => orderTab(order) === 'AWAITING_PAYMENT' && order.paymentDeadline);
  useEffect(() => {
    if (!anyAwaitingPayment) return undefined;
    const timer = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(timer);
  }, [anyAwaitingPayment]);

  const term = search.trim().toLowerCase();
  const visible = values.filter(order => (tab === 'ALL' || orderTab(order) === tab)
    && (!term || orderCode(order).toLowerCase().includes(term) || (order.items || []).some(item => String(item.productName || '').toLowerCase().includes(term))));

  const perform = async (order, send, after) => {
    setBusyId(order.id);
    const result = await action.run(send);
    setBusyId(null);
    if (result.ok) await after(result.result);
  };
  // "Thanh toán" reopens the VNPay link of the order while it is held (Session 2026-11-18).
  const pay = order => perform(order, key => api.initiatePayment(order.checkoutId, key), result => {
    if (result?.paymentUrl) {
      window.history.replaceState(null, '', `/member/orders?checkout=${encodeURIComponent(order.checkoutId)}`);
      window.location.assign(result.paymentUrl);
    } else orders.reload();
  });
  // A cash branch order is cancelled on its own; the other branch orders of the same checkout stay (FR-08-046).
  const cancelCashOrder = order => {
    if (!window.confirm(`Hủy đơn ${orderCode(order)}? Sản phẩm sẽ được trả lại giỏ hàng.`)) return;
    perform(order, key => api.cancelOrder(order.id, key), () => orders.reload());
  };
  const cancel = order => {
    const together = ordersPerCheckout.get(order.checkoutId) || 1;
    const text = together > 1
      ? `Đơn này được đặt cùng ${together - 1} đơn khác. Hủy sẽ hủy cả ${together} đơn của lần đặt đó, trả hàng về giỏ và hoàn lại voucher, điểm. Tiếp tục?`
      : `Hủy đơn ${orderCode(order)}? Sản phẩm sẽ được trả lại giỏ hàng, voucher và điểm được hoàn lại.`;
    if (!window.confirm(text)) return;
    perform(order, key => api.cancelCheckout(order.checkoutId, key), () => { setNotice(null); orders.reload(); });
  };
  // "Mua lại" puts the rings back in the cart and opens it with exactly those rings ticked (Session 2026-11-20). A ring
  // already in the cart is only ticked, not added twice; a ring that cannot be added (sold out, no longer sold) is
  // skipped and named in the cart instead of failing the whole action.
  const buyAgain = order => perform(order, async () => {
    let cart = await api.getCart();
    const lineOf = (current, item) => (current?.lines || []).find(line => String(line.locationId) === String(order.locationId)
      && String(line.variantId) === String(item.variantId));
    const skipped = [];
    for (const item of order.items || []) {
      if (lineOf(cart, item)) continue;
      try {
        cart = await api.addLine({ variantId: item.variantId, locationId: order.locationId, quantity: item.quantity }); // eslint-disable-line no-await-in-loop
      } catch {
        skipped.push(`${item.productName || `Sản phẩm #${item.variantId}`} (size ${item.size ?? '—'})`);
      }
    }
    const lineIds = (order.items || []).map(item => lineOf(cart, item)).filter(Boolean).map(line => String(line.id));
    return { lineIds, skipped };
  }, result => navigate('/member/cart', { state: { selectLineIds: result?.lineIds || [], skipped: result?.skipped || [] } }));

  if (orders.loading && !orders.data) return <Loading />;
  if (orders.error) return <ErrorNotice error={orders.error} onRetry={orders.reload} />;
  return <section className="order-tabs-page">
    <div className="order-tabs" role="tablist" aria-label="Trạng thái đơn mua">{TABS.map(item => <button key={item.key} type="button" role="tab" aria-selected={tab === item.key} className={tab === item.key ? 'active' : ''} onClick={() => setTab(item.key)}>{item.label}{item.key !== 'ALL' && counts[item.key] ? <span className="order-tab-count">{counts[item.key]}</span> : null}</button>)}</div>
    <input className="order-search" value={search} onChange={event => setSearch(event.target.value)} placeholder="Tìm theo mã đơn (ML…) hoặc tên sản phẩm" aria-label="Tìm đơn mua" />
    <FlashToast message={notice} onDone={() => setNotice(null)} />
    {action.error && <ErrorNotice error={action.error} onRetry={action.retry} onReload={orders.reload} />}
    {visible.length === 0 && <div className="order-empty"><p>{values.length ? 'Chưa có đơn hàng nào ở mục này.' : 'Bạn chưa có đơn mua nào.'}</p>{!values.length && <Link className="button primary" to="/products">Mua sắm ngay</Link>}</div>}
    {visible.map(order => {
      const current = orderTab(order);
      const cash = order.paymentMethod === 'CASH_AT_COUNTER';
      const busy = busyId === order.id;
      const open = openId === order.id;
      const together = ordersPerCheckout.get(order.checkoutId) || 1;
      const left = current === 'AWAITING_PAYMENT' && order.paymentDeadline ? countdown(order.paymentDeadline, now) : null;
      const unpaidVnpay = current === 'AWAITING_PAYMENT' && order.checkoutId && ['ACCEPTED_UNPAID', 'PAYMENT_INITIATED'].includes(order.paymentState);
      const held = unpaidVnpay && (left !== null || !order.paymentDeadline);
      const ready = order.fulfilmentState === 'READY_FOR_PICKUP';
      return <article className={`order-card${open ? ' open' : ''}${order.checkoutId && order.checkoutId === placedCheckout ? ' just-placed' : ''}`} key={order.id} {...cardToggleProps(open, () => setOpenId(open ? null : order.id))}>
        <div className="order-card-top">
          <span className="order-code">{orderCode(order)}</span>
          <span className="order-branch">{branchName(order.locationId)}{order.fulfilmentMethod === 'GHTK' ? ' · Giao qua GHTK' : ' · Nhận tại cửa hàng'}</span>
          <span className={`order-pill pill-${current.toLowerCase()}`}>{statusLabel(order, current)}</span>
        </div>
        <ul className="order-items">{(order.items || []).map(item => <li key={`${order.id}-${item.variantId}`}>
          <ProductThumb className="order-thumb" productId={item.productId} />
          <span className="order-item-name"><strong>{item.productName || `Sản phẩm #${item.variantId}`}</strong><small>Size {item.size ?? '—'} · x{item.quantity}</small></span>
          <b>{item.lineTotal ? format.money(item.lineTotal) : ''}</b>
        </li>)}</ul>
        {unpaidVnpay && (held
          ? <p className="order-hint pay-countdown">Thanh toán trong <strong className="countdown">{left || '15:00'}</strong> bằng VNPay để giữ hàng và ưu đãi.{together > 1 ? ` Thanh toán chung cho ${together} đơn của cùng lần đặt.` : ''}</p>
          : <p className="order-hint warn">Đã hết thời gian giữ hàng. Đơn sẽ tự hủy và hoàn lại voucher, điểm.</p>)}
        {current === 'AWAITING_PICKUP' && !cash && <p className="order-hint">{ready ? <>Hàng đã sẵn sàng. Đọc mã <strong>{orderCode(order)}</strong> tại quầy {branchName(order.locationId)} để nhận hàng.</> : 'Đã thanh toán. Mland đang chuẩn bị hàng, khi trạng thái là “Sẵn sàng nhận hàng” bạn có thể đến lấy.'}</p>}
        {current === 'AWAITING_PICKUP' && cash && <p className="order-hint warn">Đến {branchName(order.locationId)} trả <strong>{format.money(order.payableAmount)}</strong> tiền mặt trước <strong>{displayTime(order.counterPaymentDeadline)}</strong>. Quá hạn đơn tự hủy.</p>}
        {current === 'COMPLETED' && order.completedAt && <p className="order-hint">{order.fulfilmentMethod === 'GHTK' ? 'Giao thành công' : 'Đã nhận tại cửa hàng'} lúc <strong>{displayTime(order.completedAt)}</strong>.</p>}
        {current === 'AWAITING_COLLECTION' && <p className="order-hint">{order.fulfilmentState === 'READY_FOR_CARRIER' ? 'Hàng đã đóng gói, đang chờ GHTK đến lấy.' : 'Mland đang chuẩn bị hàng để giao cho GHTK.'}</p>}
        {open && <dl className="order-detail">
          <div><dt>Mã đơn</dt><dd>{orderCode(order)}</dd></div>
          <div><dt>Thời gian đặt</dt><dd>{displayTime(order.orderedAt)}</dd></div>
          <div><dt>Chi nhánh</dt><dd>{branchName(order.locationId)}</dd></div>
          <div><dt>Nhận hàng</dt><dd>{order.fulfilmentMethod === 'GHTK' ? 'Giao tận nơi qua GHTK' : 'Nhận tại cửa hàng'}</dd></div>
          <div><dt>Thanh toán</dt><dd>{cash ? 'Tiền mặt tại quầy' : 'VNPay'}</dd></div>
          {order.paidAt && <div><dt>Thời gian thanh toán</dt><dd>{displayTime(order.paidAt)}</dd></div>}
          {order.completedAt && <div><dt>{order.fulfilmentMethod === 'GHTK' ? 'Giao thành công lúc' : 'Nhận hàng lúc'}</dt><dd>{displayTime(order.completedAt)}</dd></div>}
          {cash && order.counterPaymentDeadline && current !== 'COMPLETED' && current !== 'CANCELLED' && <div><dt>Hạn giữ hàng</dt><dd>{displayTime(order.counterPaymentDeadline)}</dd></div>}
          {unpaidVnpay && order.paymentDeadline && <div><dt>Hạn thanh toán</dt><dd>{displayTime(order.paymentDeadline)}</dd></div>}
          <OrderMoneyRows order={order} format={format} together={together} />
        </dl>}
        <div className="order-card-bottom">
          <span className="order-more">{open ? 'Thu gọn ▲' : 'Bấm để xem chi tiết ▼'}</span>
          <span className="order-sum">Thành tiền <strong>{format.money(order.payableAmount)}</strong></span>
        </div>
        {(unpaidVnpay || current === 'COMPLETED' || current === 'CANCELLED' || (current === 'AWAITING_PICKUP' && cash && order.paymentState === 'ACCEPTED_UNPAID')) && <div className="order-actions">
          {unpaidVnpay && <>
            <button type="button" className="button outline" disabled={busy} onClick={() => cancel(order)}>Hủy đơn</button>
            {held && <button type="button" className="button primary" disabled={busy} onClick={() => pay(order)}>{busy ? 'Đang mở VNPay…' : 'Thanh toán'}</button>}
          </>}
          {current === 'AWAITING_PICKUP' && cash && order.paymentState === 'ACCEPTED_UNPAID' && <button type="button" className="button outline" disabled={busy} onClick={() => cancelCashOrder(order)}>Hủy đơn</button>}
          {(current === 'COMPLETED' || current === 'CANCELLED') && <button type="button" className={`button ${current === 'COMPLETED' ? 'primary' : 'outline'}`} disabled={busy} onClick={() => buyAgain(order)}>{busy ? 'Đang thêm…' : 'Mua lại'}</button>}
        </div>}
      </article>;
    })}
  </section>;
}
