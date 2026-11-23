import React, { useEffect, useMemo, useRef, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router';
import { RequireSession } from '../../../../shared/auth/RequireSession';
import { useFormat, useT } from '../../../../shared/i18n/I18nProvider';
import { ErrorNotice, Loading, useCommand, useResource } from '../../../../shared/ui/components';
import { FlashToast } from '../FlashToast';
import { useRetailApi } from '../../application/useRetailApi';
import { cardToggleProps, orderCode, OrderMoneyRows, useDisplayTime } from '../orderCode';
import { ProductThumb } from '../ProductThumb';
import { messages as retailMessages } from '../../messages';

/**
 * FE-08 FR-08-050: "Đơn mua" in marketplace-style tabs, one card per branch order, newest first (Session 2026-11-18).
 * GHTK orders go Chờ lấy hàng → Chờ giao hàng → Đã giao; pickup orders wait in Chờ nhận tại cửa hàng.
 */
const TABS = ['ALL', 'AWAITING_PAYMENT', 'AWAITING_COLLECTION', 'AWAITING_DELIVERY', 'AWAITING_PICKUP', 'COMPLETED', 'CANCELLED'];
const DELIVERING = ['HANDED_TO_CARRIER', 'IN_TRANSIT'];

export function orderTab(order) {
  if (order.paymentState === 'CANCELLED' || order.paymentState === 'EXPIRED') return 'CANCELLED';
  if (order.fulfilmentState === 'PICKED_UP' || order.fulfilmentState === 'DELIVERED') return 'COMPLETED';
  const cash = order.paymentMethod === 'CASH_AT_COUNTER';
  if (!cash && order.paymentState !== 'PAID') return 'AWAITING_PAYMENT';
  if (order.fulfilmentMethod === 'GHTK') return DELIVERING.includes(order.fulfilmentState) ? 'AWAITING_DELIVERY' : 'AWAITING_COLLECTION';
  return 'AWAITING_PICKUP';
}

/** The status wording of a branch order in the language of `t`. */
export function statusLabel(order, t, tab = orderTab(order)) {
  if (tab === 'CANCELLED') return order.paymentState === 'EXPIRED' ? t('orders.status.cancelledExpired') : t('orders.tab.CANCELLED');
  if (tab === 'COMPLETED') return order.fulfilmentState === 'DELIVERED' ? t('common.delivered') : t('orders.pickedUpInStore');
  if (tab === 'AWAITING_PAYMENT') return t('orders.status.toPay');
  if (tab === 'AWAITING_COLLECTION') return order.fulfilmentState === 'READY_FOR_CARRIER' ? t('orders.status.waitingGhtkPickup') : t('orders.status.preparing');
  if (tab === 'AWAITING_DELIVERY') return t('orders.status.beingDelivered');
  const ready = order.fulfilmentState === 'READY_FOR_PICKUP';
  if (order.paymentState !== 'PAID') return ready ? t('orders.status.readyPayOnPickup') : t('orders.status.payOnPickup');
  return ready ? t('orders.status.readyForPickup') : t('orders.status.preparing');
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
  const t = useT(retailMessages);
  return <main className="container public-page retail-page retail-shopping"><div className="retail-page-heading"><div><p className="retail-kicker">{t('common.memberKicker')}</p><h1>{t('orders.myOrders')}</h1></div><Link to="/products" className="retail-back-link">{t('common.continueShopping')}</Link></div><RequireSession requiredText={t('orders.signInToSeeYour')}><OrdersContent /></RequireSession></main>;
}

function OrdersContent() {
  const api = useRetailApi();
  const format = useFormat();
  const t = useT(retailMessages);
  const time = useDisplayTime();
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
  const branchName = locationId => branchNames.get(String(locationId)) || t('common.branchNumber', { locationId });
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
        ? t('orders.paymentIsNotCompleteThe', { deadline: time(deadline) })
        : t('orders.paymentIsNotComplete') });
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
      const where = cashPlaced.length > 1 ? t('common.branches', { count: cashPlaced.length }) : branchName(cashPlaced[0].locationId);
      setNotice({ kind: 'success', text: t('orders.orderPlacedGoToBefore', { where, counterPaymentDeadline: time(cashPlaced[0].counterPaymentDeadline), total: format.money(total) }) });
      return undefined;
    }
    // VNPay may confirm a moment after the browser returns: look again a few times before showing the result.
    if (info.txnRef && placed.some(order => order.paymentState !== 'PAID') && info.tries < 3) {
      info.tries += 1;
      setNotice({ kind: 'info', text: t('orders.confirmingThePaymentWithVnpay') });
      const timer = setTimeout(() => orders.reload(), 2000);
      return () => clearTimeout(timer);
    }
    arrival.current = false;
    if (info.txnRef) setNotice(placed.every(order => order.paymentState === 'PAID')
      ? { kind: 'success', text: t('orders.paymentSuccessfulMlandIsPreparing') }
      : { kind: 'warn', text: t('orders.noPaymentResultFromVnpay') });
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
    if (!window.confirm(t('orders.cancelOrderTheItemsGo', { order: orderCode(order) }))) return;
    perform(order, key => api.cancelOrder(order.id, key), () => orders.reload());
  };
  const cancel = order => {
    const together = ordersPerCheckout.get(order.checkoutId) || 1;
    const text = together > 1
      ? t('orders.thisOrderWasPlacedWith', { others: together - 1, together })
      : t('orders.cancelOrderTheItemsGo2', { order: orderCode(order) });
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
        skipped.push(t('orders.skippedItem', { name: item.productName || t('common.productNumber', { variantId: item.variantId }), size: item.size ?? '—' }));
      }
    }
    const lineIds = (order.items || []).map(item => lineOf(cart, item)).filter(Boolean).map(line => String(line.id));
    return { lineIds, skipped };
  }, result => navigate('/member/cart', { state: { selectLineIds: result?.lineIds || [], skipped: result?.skipped || [] } }));

  if (orders.loading && !orders.data) return <Loading />;
  if (orders.error) return <ErrorNotice error={orders.error} onRetry={orders.reload} />;
  return <section className="order-tabs-page">
    <div className="order-tabs" role="tablist" aria-label={t('orders.orderStatus')}>{TABS.map(key => <button key={key} type="button" role="tab" aria-selected={tab === key} className={tab === key ? 'active' : ''} onClick={() => setTab(key)}>{t(`orders.tab.${key}`)}{key !== 'ALL' && counts[key] ? <span className="order-tab-count">{counts[key]}</span> : null}</button>)}</div>
    <input className="order-search" value={search} onChange={event => setSearch(event.target.value)} placeholder={t('orders.searchByOrderCodeMl')} aria-label={t('orders.searchOrders')} />
    <FlashToast message={notice} onDone={() => setNotice(null)} />
    {action.error && <ErrorNotice error={action.error} onRetry={action.retry} onReload={orders.reload} />}
    {visible.length === 0 && <div className="order-empty"><p>{values.length ? t('orders.noOrdersInThisTab') : t('orders.youHaveNoOrdersYet')}</p>{!values.length && <Link className="button primary" to="/products">{t('orders.shopNow')}</Link>}</div>}
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
          <span className="order-branch">{branchName(order.locationId)}{order.fulfilmentMethod === 'GHTK' ? t('orders.ghtkDelivery') : t('orders.storePickup')}</span>
          <span className={`order-pill pill-${current.toLowerCase()}`}>{statusLabel(order, t, current)}</span>
        </div>
        <ul className="order-items">{(order.items || []).map(item => <li key={`${order.id}-${item.variantId}`}>
          <ProductThumb className="order-thumb" productId={item.productId} />
          <span className="order-item-name"><strong>{item.productName || t('common.productNumber', { variantId: item.variantId })}</strong><small>{t('common.size', { size: item.size ?? '—' })} · x{item.quantity}</small></span>
          <b>{item.lineTotal ? format.money(item.lineTotal) : ''}</b>
        </li>)}</ul>
        {unpaidVnpay && (held
          ? <p className="order-hint pay-countdown">{t('orders.payWithin')} <strong className="countdown">{left || '10:00'}</strong> {t('orders.byVnpayToKeepThe')}{together > 1 ? t('orders.onePaymentForTheOrders', { together }) : ''}</p>
          : <p className="order-hint warn">{t('orders.theHoldHasEndedThe')}</p>)}
        {current === 'AWAITING_PICKUP' && !cash && <p className="order-hint">{ready ? <>{t('orders.yourOrderIsReadyGive')} <strong>{orderCode(order)}</strong> {t('orders.atTheCounterToCollect', { branch: branchName(order.locationId) })}</> : t('orders.paidMlandIsPreparingYour')}</p>}
        {current === 'AWAITING_PICKUP' && cash && <p className="order-hint warn">{t('orders.goToAndPay', { branch: branchName(order.locationId) })} <strong>{format.money(order.payableAmount)}</strong> {t('orders.inCashBefore')} <strong>{time(order.counterPaymentDeadline)}</strong>. {t('orders.afterThatTheOrderCancels')}</p>}
        {current === 'COMPLETED' && order.completedAt && <p className="order-hint">{order.fulfilmentMethod === 'GHTK' ? t('common.delivered') : t('orders.pickedUpInStore')} {t('orders.at')} <strong>{time(order.completedAt)}</strong>.</p>}
        {current === 'AWAITING_COLLECTION' && <p className="order-hint">{order.fulfilmentState === 'READY_FOR_CARRIER' ? t('orders.packedAndWaitingForGhtk') : t('orders.mlandIsPreparingYourOrder')}</p>}
        {open && <dl className="order-detail">
          <div><dt>{t('common.orderCode')}</dt><dd>{orderCode(order)}</dd></div>
          <div><dt>{t('common.orderedAt')}</dt><dd>{time(order.orderedAt)}</dd></div>
          <div><dt>{t('common.branch')}</dt><dd>{branchName(order.locationId)}</dd></div>
          <div><dt>{t('orders.delivery')}</dt><dd>{order.fulfilmentMethod === 'GHTK' ? t('common.homeDeliveryByGhtk') : t('common.storePickup')}</dd></div>
          <div><dt>{t('common.payment')}</dt><dd>{cash ? t('common.cashAtCounter') : 'VNPay'}</dd></div>
          {order.paidAt && <div><dt>{t('orders.paidAt')}</dt><dd>{time(order.paidAt)}</dd></div>}
          {order.completedAt && <div><dt>{order.fulfilmentMethod === 'GHTK' ? t('orders.deliveredAt') : t('orders.pickedUpAt')}</dt><dd>{time(order.completedAt)}</dd></div>}
          {cash && order.counterPaymentDeadline && current !== 'COMPLETED' && current !== 'CANCELLED' && <div><dt>{t('orders.heldUntil')}</dt><dd>{time(order.counterPaymentDeadline)}</dd></div>}
          {unpaidVnpay && order.paymentDeadline && <div><dt>{t('orders.payBy')}</dt><dd>{time(order.paymentDeadline)}</dd></div>}
          <OrderMoneyRows order={order} format={format} together={together} />
        </dl>}
        <div className="order-card-bottom">
          <span className="order-more">{open ? t('common.showLess') : t('common.clickForDetails')}</span>
          <span className="order-sum">{t('common.total')} <strong>{format.money(order.payableAmount)}</strong></span>
        </div>
        {(unpaidVnpay || current === 'COMPLETED' || current === 'CANCELLED' || (current === 'AWAITING_PICKUP' && cash && order.paymentState === 'ACCEPTED_UNPAID')) && <div className="order-actions">
          {unpaidVnpay && <>
            <button type="button" className="button outline" disabled={busy} onClick={() => cancel(order)}>{t('orders.cancelOrder')}</button>
            {held && <button type="button" className="button primary" disabled={busy} onClick={() => pay(order)}>{busy ? t('orders.openingVnpay') : t('orders.pay')}</button>}
          </>}
          {current === 'AWAITING_PICKUP' && cash && order.paymentState === 'ACCEPTED_UNPAID' && <button type="button" className="button outline" disabled={busy} onClick={() => cancelCashOrder(order)}>{t('orders.cancelOrder')}</button>}
          {(current === 'COMPLETED' || current === 'CANCELLED') && <button type="button" className={`button ${current === 'COMPLETED' ? 'primary' : 'outline'}`} disabled={busy} onClick={() => buyAgain(order)}>{busy ? t('orders.adding') : t('orders.buyAgain')}</button>}
        </div>}
      </article>;
    })}
  </section>;
}
