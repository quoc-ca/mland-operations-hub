import React, { useEffect, useMemo, useRef, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router';
import { RequireSession } from '../../../../shared/auth/RequireSession';
import { useFormat } from '../../../../shared/i18n/I18nProvider';
import { ErrorNotice, Loading, useCommand, useResource } from '../../../../shared/ui/components';
import { FlashToast } from '../FlashToast';
import { useRetailApi } from '../../application/useRetailApi';
import { cardToggleProps, displayTime, orderCode, OrderMoneyRows } from '../orderCode';
import { ProductThumb } from '../ProductThumb';
import { useText } from '../useText';

const VI_ONLY = vi => (Array.isArray(vi) ? vi[0] : vi);

/**
 * FE-08 FR-08-050: "Đơn mua" in marketplace-style tabs, one card per branch order, newest first (Session 2026-11-18).
 * GHTK orders go Chờ lấy hàng → Chờ giao hàng → Đã giao; pickup orders wait in Chờ nhận tại cửa hàng.
 */
const TABS = [
  { key: 'ALL', label: ['Tất cả', 'All'] },
  { key: 'AWAITING_PAYMENT', label: ['Chờ thanh toán', 'To pay'] },
  { key: 'AWAITING_COLLECTION', label: ['Chờ lấy hàng', 'To ship'] },
  { key: 'AWAITING_DELIVERY', label: ['Chờ giao hàng', 'To receive'] },
  { key: 'AWAITING_PICKUP', label: ['Chờ nhận tại cửa hàng', 'Store pickup'] },
  { key: 'COMPLETED', label: ['Đã giao', 'Completed'] },
  { key: 'CANCELLED', label: ['Đã hủy', 'Cancelled'] },
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

export function statusLabel(order, tab = orderTab(order), t = VI_ONLY) {
  if (tab === 'CANCELLED') return order.paymentState === 'EXPIRED' ? t(['Đã hủy · quá hạn', 'Cancelled · expired']) : t(['Đã hủy', 'Cancelled']);
  if (tab === 'COMPLETED') return order.fulfilmentState === 'DELIVERED' ? t(['Giao thành công', 'Delivered']) : t(['Đã nhận tại cửa hàng', 'Picked up in store']);
  if (tab === 'AWAITING_PAYMENT') return t(['Chờ thanh toán', 'To pay']);
  if (tab === 'AWAITING_COLLECTION') return order.fulfilmentState === 'READY_FOR_CARRIER' ? t(['Chờ GHTK lấy hàng', 'Waiting for GHTK pickup']) : t(['Đang chuẩn bị hàng', 'Preparing']);
  if (tab === 'AWAITING_DELIVERY') return t(['Đang giao hàng', 'Being delivered']);
  const ready = order.fulfilmentState === 'READY_FOR_PICKUP';
  if (order.paymentState !== 'PAID') return ready ? t(['Sẵn sàng · trả tiền khi nhận', 'Ready · pay on pickup']) : t(['Trả tiền khi nhận', 'Pay on pickup']);
  return ready ? t(['Sẵn sàng nhận hàng', 'Ready for pickup']) : t(['Đang chuẩn bị hàng', 'Preparing']);
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
  const t = useText();
  return <main className="container public-page retail-page retail-shopping"><div className="retail-page-heading"><div><p className="retail-kicker">FE-08 · MEMBER</p><h1>{t('Đơn mua của tôi', 'My orders')}</h1></div><Link to="/products" className="retail-back-link">{t('← Tiếp tục mua sắm', '← Continue shopping')}</Link></div><RequireSession requiredText={t('Đăng nhập để xem đơn mua.', 'Sign in to see your orders.')}><OrdersContent /></RequireSession></main>;
}

function OrdersContent() {
  const api = useRetailApi();
  const format = useFormat();
  const t = useText();
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
  const branchName = locationId => branchNames.get(String(locationId)) || t(`Chi nhánh #${locationId}`, `Branch #${locationId}`);
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
        ? t(`Bạn chưa hoàn tất thanh toán. Đơn và ưu đãi được giữ đến ${displayTime(deadline)}, bấm Thanh toán để tiếp tục.`, `Payment is not complete. The order and its discounts are held until ${displayTime(deadline)}; press Pay to continue.`)
        : t('Bạn chưa hoàn tất thanh toán.', 'Payment is not complete.') });
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
      const where = cashPlaced.length > 1 ? t(`${cashPlaced.length} chi nhánh`, `${cashPlaced.length} branches`) : branchName(cashPlaced[0].locationId);
      setNotice({ kind: 'success', text: t(`Đã đặt hàng. Đến ${where} trước ${displayTime(cashPlaced[0].counterPaymentDeadline)} để trả ${format.money(total)} tiền mặt và nhận hàng.`, `Order placed. Go to ${where} before ${displayTime(cashPlaced[0].counterPaymentDeadline)} to pay ${format.money(total)} in cash and collect it.`) });
      return undefined;
    }
    // VNPay may confirm a moment after the browser returns: look again a few times before showing the result.
    if (info.txnRef && placed.some(order => order.paymentState !== 'PAID') && info.tries < 3) {
      info.tries += 1;
      setNotice({ kind: 'info', text: t('Đang xác nhận thanh toán với VNPay…', 'Confirming the payment with VNPay…') });
      const timer = setTimeout(() => orders.reload(), 2000);
      return () => clearTimeout(timer);
    }
    arrival.current = false;
    if (info.txnRef) setNotice(placed.every(order => order.paymentState === 'PAID')
      ? { kind: 'success', text: t('Thanh toán thành công. Mland đang chuẩn bị hàng cho bạn.', 'Payment successful. Mland is preparing your order.') }
      : { kind: 'warn', text: t('Chưa nhận được kết quả thanh toán từ VNPay. Trạng thái đơn sẽ tự cập nhật, bạn có thể tải lại trang sau ít phút.', 'No payment result from VNPay yet. The order will update by itself; you can reload in a few minutes.') });
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
    if (!window.confirm(t(`Hủy đơn ${orderCode(order)}? Sản phẩm sẽ được trả lại giỏ hàng.`, `Cancel order ${orderCode(order)}? The items go back to your cart.`))) return;
    perform(order, key => api.cancelOrder(order.id, key), () => orders.reload());
  };
  const cancel = order => {
    const together = ordersPerCheckout.get(order.checkoutId) || 1;
    const text = together > 1
      ? t(`Đơn này được đặt cùng ${together - 1} đơn khác. Hủy sẽ hủy cả ${together} đơn của lần đặt đó, trả hàng về giỏ và hoàn lại voucher, điểm. Tiếp tục?`, `This order was placed with ${together - 1} other order(s). Cancelling cancels all ${together} orders of that checkout, returns the items to your cart and gives back the voucher and points. Continue?`)
      : t(`Hủy đơn ${orderCode(order)}? Sản phẩm sẽ được trả lại giỏ hàng, voucher và điểm được hoàn lại.`, `Cancel order ${orderCode(order)}? The items go back to your cart; the voucher and points are given back.`);
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
        skipped.push(`${item.productName || t(`Sản phẩm #${item.variantId}`, `Product #${item.variantId}`)} (size ${item.size ?? '—'})`);
      }
    }
    const lineIds = (order.items || []).map(item => lineOf(cart, item)).filter(Boolean).map(line => String(line.id));
    return { lineIds, skipped };
  }, result => navigate('/member/cart', { state: { selectLineIds: result?.lineIds || [], skipped: result?.skipped || [] } }));

  if (orders.loading && !orders.data) return <Loading />;
  if (orders.error) return <ErrorNotice error={orders.error} onRetry={orders.reload} />;
  return <section className="order-tabs-page">
    <div className="order-tabs" role="tablist" aria-label={t('Trạng thái đơn mua', 'Order status')}>{TABS.map(item => <button key={item.key} type="button" role="tab" aria-selected={tab === item.key} className={tab === item.key ? 'active' : ''} onClick={() => setTab(item.key)}>{t(item.label)}{item.key !== 'ALL' && counts[item.key] ? <span className="order-tab-count">{counts[item.key]}</span> : null}</button>)}</div>
    <input className="order-search" value={search} onChange={event => setSearch(event.target.value)} placeholder={t('Tìm theo mã đơn (ML…) hoặc tên sản phẩm', 'Search by order code (ML…) or product name')} aria-label={t('Tìm đơn mua', 'Search orders')} />
    <FlashToast message={notice} onDone={() => setNotice(null)} />
    {action.error && <ErrorNotice error={action.error} onRetry={action.retry} onReload={orders.reload} />}
    {visible.length === 0 && <div className="order-empty"><p>{values.length ? t('Chưa có đơn hàng nào ở mục này.', 'No orders in this tab.') : t('Bạn chưa có đơn mua nào.', 'You have no orders yet.')}</p>{!values.length && <Link className="button primary" to="/products">{t('Mua sắm ngay', 'Shop now')}</Link>}</div>}
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
          <span className="order-branch">{branchName(order.locationId)}{order.fulfilmentMethod === 'GHTK' ? t(' · Giao qua GHTK', ' · GHTK delivery') : t(' · Nhận tại cửa hàng', ' · Store pickup')}</span>
          <span className={`order-pill pill-${current.toLowerCase()}`}>{statusLabel(order, current, t)}</span>
        </div>
        <ul className="order-items">{(order.items || []).map(item => <li key={`${order.id}-${item.variantId}`}>
          <ProductThumb className="order-thumb" productId={item.productId} />
          <span className="order-item-name"><strong>{item.productName || t(`Sản phẩm #${item.variantId}`, `Product #${item.variantId}`)}</strong><small>Size {item.size ?? '—'} · x{item.quantity}</small></span>
          <b>{item.lineTotal ? format.money(item.lineTotal) : ''}</b>
        </li>)}</ul>
        {unpaidVnpay && (held
          ? <p className="order-hint pay-countdown">{t('Thanh toán trong', 'Pay within')} <strong className="countdown">{left || '10:00'}</strong> {t('bằng VNPay để giữ hàng và ưu đãi.', 'by VNPay to keep the items and discounts.')}{together > 1 ? t(` Thanh toán chung cho ${together} đơn của cùng lần đặt.`, ` One payment for the ${together} orders of this checkout.`) : ''}</p>
          : <p className="order-hint warn">{t('Đã hết thời gian giữ hàng. Đơn sẽ tự hủy và hoàn lại voucher, điểm.', 'The hold has ended. The order will cancel itself and give back the voucher and points.')}</p>)}
        {current === 'AWAITING_PICKUP' && !cash && <p className="order-hint">{ready ? <>{t('Hàng đã sẵn sàng. Đọc mã', 'Your order is ready. Give the code')} <strong>{orderCode(order)}</strong> {t(`tại quầy ${branchName(order.locationId)} để nhận hàng.`, `at the ${branchName(order.locationId)} counter to collect it.`)}</> : t('Đã thanh toán. Mland đang chuẩn bị hàng, khi trạng thái là “Sẵn sàng nhận hàng” bạn có thể đến lấy.', 'Paid. Mland is preparing your order; come and collect it once it shows “Ready for pickup”.')}</p>}
        {current === 'AWAITING_PICKUP' && cash && <p className="order-hint warn">{t(`Đến ${branchName(order.locationId)} trả`, `Go to ${branchName(order.locationId)} and pay`)} <strong>{format.money(order.payableAmount)}</strong> {t('tiền mặt trước', 'in cash before')} <strong>{displayTime(order.counterPaymentDeadline)}</strong>. {t('Quá hạn đơn tự hủy.', 'After that the order cancels itself.')}</p>}
        {current === 'COMPLETED' && order.completedAt && <p className="order-hint">{order.fulfilmentMethod === 'GHTK' ? t('Giao thành công', 'Delivered') : t('Đã nhận tại cửa hàng', 'Picked up in store')} {t('lúc', 'at')} <strong>{displayTime(order.completedAt)}</strong>.</p>}
        {current === 'AWAITING_COLLECTION' && <p className="order-hint">{order.fulfilmentState === 'READY_FOR_CARRIER' ? t('Hàng đã đóng gói, đang chờ GHTK đến lấy.', 'Packed and waiting for GHTK to pick it up.') : t('Mland đang chuẩn bị hàng để giao cho GHTK.', 'Mland is preparing your order for GHTK.')}</p>}
        {open && <dl className="order-detail">
          <div><dt>{t('Mã đơn', 'Order code')}</dt><dd>{orderCode(order)}</dd></div>
          <div><dt>{t('Thời gian đặt', 'Ordered at')}</dt><dd>{displayTime(order.orderedAt)}</dd></div>
          <div><dt>{t('Chi nhánh', 'Branch')}</dt><dd>{branchName(order.locationId)}</dd></div>
          <div><dt>{t('Nhận hàng', 'Delivery')}</dt><dd>{order.fulfilmentMethod === 'GHTK' ? t('Giao tận nơi qua GHTK', 'Home delivery by GHTK') : t('Nhận tại cửa hàng', 'Store pickup')}</dd></div>
          <div><dt>{t('Thanh toán', 'Payment')}</dt><dd>{cash ? t('Tiền mặt tại quầy', 'Cash at counter') : 'VNPay'}</dd></div>
          {order.paidAt && <div><dt>{t('Thời gian thanh toán', 'Paid at')}</dt><dd>{displayTime(order.paidAt)}</dd></div>}
          {order.completedAt && <div><dt>{order.fulfilmentMethod === 'GHTK' ? t('Giao thành công lúc', 'Delivered at') : t('Nhận hàng lúc', 'Picked up at')}</dt><dd>{displayTime(order.completedAt)}</dd></div>}
          {cash && order.counterPaymentDeadline && current !== 'COMPLETED' && current !== 'CANCELLED' && <div><dt>{t('Hạn giữ hàng', 'Held until')}</dt><dd>{displayTime(order.counterPaymentDeadline)}</dd></div>}
          {unpaidVnpay && order.paymentDeadline && <div><dt>{t('Hạn thanh toán', 'Pay by')}</dt><dd>{displayTime(order.paymentDeadline)}</dd></div>}
          <OrderMoneyRows order={order} format={format} together={together} />
        </dl>}
        <div className="order-card-bottom">
          <span className="order-more">{open ? t('Thu gọn ▲', 'Show less ▲') : t('Bấm để xem chi tiết ▼', 'Click for details ▼')}</span>
          <span className="order-sum">{t('Thành tiền', 'Total')} <strong>{format.money(order.payableAmount)}</strong></span>
        </div>
        {(unpaidVnpay || current === 'COMPLETED' || current === 'CANCELLED' || (current === 'AWAITING_PICKUP' && cash && order.paymentState === 'ACCEPTED_UNPAID')) && <div className="order-actions">
          {unpaidVnpay && <>
            <button type="button" className="button outline" disabled={busy} onClick={() => cancel(order)}>{t('Hủy đơn', 'Cancel order')}</button>
            {held && <button type="button" className="button primary" disabled={busy} onClick={() => pay(order)}>{busy ? t('Đang mở VNPay…', 'Opening VNPay…') : t('Thanh toán', 'Pay')}</button>}
          </>}
          {current === 'AWAITING_PICKUP' && cash && order.paymentState === 'ACCEPTED_UNPAID' && <button type="button" className="button outline" disabled={busy} onClick={() => cancelCashOrder(order)}>{t('Hủy đơn', 'Cancel order')}</button>}
          {(current === 'COMPLETED' || current === 'CANCELLED') && <button type="button" className={`button ${current === 'COMPLETED' ? 'primary' : 'outline'}`} disabled={busy} onClick={() => buyAgain(order)}>{busy ? t('Đang thêm…', 'Adding…') : t('Mua lại', 'Buy again')}</button>}
        </div>}
      </article>;
    })}
  </section>;
}
