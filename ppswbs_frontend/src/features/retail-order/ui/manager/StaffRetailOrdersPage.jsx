import React, { useEffect, useMemo, useRef, useState } from 'react';
import { Link } from 'react-router';
import { RequireSession } from '../../../../shared/auth/RequireSession';
import { useEnumLabel, useFormat, useT } from '../../../../shared/i18n/I18nProvider';
import { ErrorNotice, Loading, useResource } from '../../../../shared/ui/components';
import { FlashToast } from '../FlashToast';
import { OPEN_TASK_TABS, pickupTab, shipmentTab } from './staffTabs';
import { newCommandId } from '../../../../shared/api/http';
import { useRetailApi } from '../../application/useRetailApi';
import { cardToggleProps, orderCode, OrderMoneyRows, useDisplayTime } from '../orderCode';
import { ProductThumb } from '../ProductThumb';
import { messages as retailMessages } from '../../messages';
import { sharedMessages } from '../../../../shared/ui/messages';

/**
 * FE-08 FR-08-051: one Staff page for the branch's retail work. Tabs follow the order stages of Session 2026-11-18:
 * Đang chuẩn bị hàng → (GHTK) Chờ ĐVVC lấy hàng → Đã bàn giao cho ĐVVC → Hoàn thành, or (pickup) Chờ khách nhận →
 * Hoàn thành. Each card offers only the action of its stage. Staff only packs a GHTK order; the GHTK partner records
 * the courier pickup and the delivery on /carrier/orders (FE-01 role CARRIER, V190).
 */
// The stage names (staff.shipment.*), tab names (staff.tab.*), card pills (staff.pill.*), next actions (staff.nextAction.*)
// and waiting notes (staff.courierWaiting.*) live in the dictionary (VI/EN switch, Session 2026-11-23).
export const nextShipmentStatus = { PREPARING: 'READY_FOR_CARRIER' };
/** Stages in which Staff is only waiting for the courier. */
const COURIER_WAITING = ['READY_FOR_CARRIER', 'HANDED_TO_CARRIER', 'IN_TRANSIT'];
const SHIPMENT_STATUSES = ['PREPARING', 'READY_FOR_CARRIER', 'HANDED_TO_CARRIER', 'IN_TRANSIT', 'DELIVERED'];
// GHTK work first, then the counter (cash, pickup), then history (Thinh, 2026-11-20).
const TABS = ['PREPARING', 'AWAITING_CARRIER', 'HANDED_OVER', 'AWAITING_CASH', 'TO_HAND_OVER', 'HISTORY'];
// Addresses and bookmarks of the former tabs keep working.
const LEGACY_TABS = { TO_SHIP: 'PREPARING', SHIPPING: 'HANDED_OVER' };

const DAY_MS = 24 * 60 * 60 * 1000;
const timeLeft = (deadline, t) => {
  const ms = new Date(deadline).getTime() - Date.now();
  if (ms <= 0) return t('staff.overdue');
  const hours = Math.floor(ms / 3_600_000);
  const minutes = Math.floor((ms % 3_600_000) / 60_000);
  return hours >= 24
    ? t('staff.daysHoursLeft', { days: Math.floor(hours / 24), hours: hours % 24 })
    : t('staff.hoursMinutesLeft', { hours: Math.max(hours, 0), minutes });
};



export default function StaffRetailOrdersPage({ initialTab }) {
  const t = useT(retailMessages);
  return <div className="ota-workspace">
    
    <main className="ota-main retail-staff-main">
      <div className="ota-topline"><span className="retail-staff-crumb"><Link to="/home">{t('common.home')}</Link> {t('staff.mlandRetailOrders')}</span><span>● {t('common.backendAuthorized')}</span></div>
      <div className="ota-page-heading"><div><p>{t('staff.packGhtkParcelsCollectCounter')}</p></div></div>
      <RequireSession requiredText={t('staff.signInAsAStaff')}><StaffOrders initialTab={initialTab} /></RequireSession>
    </main>
  </div>;
}

/** The Staff order list without the page frame, also shown on the Staff home (Session 2026-11-22). */
export function StaffOrders({ initialTab }) {
  const api = useRetailApi();
  const format = useFormat();
  const t = useT(retailMessages);
  const statusLabel = useEnumLabel(retailMessages, 'staff.shipment');
  const wanted = LEGACY_TABS[initialTab] || initialTab;
  const [tab, setTab] = useState(TABS.includes(wanted) ? wanted : 'PREPARING');
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
  const branchName = locationId => branchNames.get(String(locationId)) || t('common.branchNumber', { locationId });

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
    const first = TABS.find(key => key !== 'HISTORY' && counts[key]);
    if (first) setTab(first);
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
    setNotice(t('staff.orderCollectedOrderCompleted', { code: item.code, payableAmount: format.money(order.payableAmount) }));
  });
  // Pickup: the order is packed and waits at the counter (Đang chuẩn bị hàng → Chờ khách nhận).
  const prepare = item => run(item.key, async () => {
    await api.prepareOrder(item.row.order.id);
    setNotice(t('staff.orderIsPackedAndWaits', { code: item.code }));
  });
  const handOver = item => run(item.key, async () => {
    if (!item.row.pickupReadyConfirmed) await api.prepareOrder(item.row.order.id);
    await api.handOverOrder(item.row.order.id);
    setNotice(t('staff.orderHandedToTheCustomer', { code: item.code }));
  });
  const advance = item => {
    const next = nextShipmentStatus[item.row.status];
    const label = statusLabel(next);
    if (!next || !window.confirm(t('staff.moveShipmentTo', { mockTrackingNumber: item.row.mockTrackingNumber, label }))) return;
    run(item.key, async () => {
      await api.transitionStaffShipment(item.row.id, { nextStatus: next, expectedVersion: item.row.version });
      setDetails(current => { const copy = { ...current }; delete copy[item.row.id]; return copy; });
      setNotice(t('staff.shipment', { mockTrackingNumber: item.row.mockTrackingNumber, label }));
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
      <div><span>{t('staff.cashCollectedToday')}</span><strong>{format.money(cashToday.toString())}</strong></div>
      <div className={expiringSoon ? 'warn' : ''}><span>{t('staff.holdsEndingWithin24Hours')}</span><strong>{expiringSoon}</strong></div>
      <div><span>{t('staff.openTasks')}</span><strong>{openTasks}</strong></div>
    </div>
    <div className="staff-toolbar">
      <input className="order-search" value={search} onChange={event => setSearch(event.target.value)} placeholder={t('staff.orderCodeMlTrackingNumber')} aria-label={t('staff.searchOrders')} />
      <button type="button" className="button outline" onClick={reloadAll} disabled={desk.loading || shipments.loading}>{t('common.refresh')}</button>
    </div>
    <div className="order-tabs" role="tablist" aria-label={t('staff.branchWork')}>{TABS.map(key => <button key={key} type="button" role="tab" aria-selected={tab === key} className={tab === key ? 'active' : ''} onClick={() => setTab(key)}>{t(`staff.tab.${key}`)}{key !== 'HISTORY' && counts[key] ? <span className="order-tab-count">{counts[key]}</span> : null}</button>)}</div>
    {desk.error && <ErrorNotice error={desk.error} onRetry={desk.reload} />}
    {shipments.error && <ErrorNotice error={shipments.error} onRetry={shipments.reload} />}
    {tab === 'AWAITING_CASH' && <p className="muted small">{t('staff.whenTheCustomerPaysAt')}</p>}
    <FlashToast message={notice} onDone={() => setNotice('')} />
    {error && <ErrorNotice error={error} />}
    {visible.length === 0 && <div className="order-empty"><p>{term ? t('staff.noMatchingOrder') : t('staff.nothingLeftInThisTab')}</p></div>}
    {visible.map(item => item.kind === 'PICKUP'
      ? <PickupCard key={item.key} item={item} open={openKey === item.key} onToggle={() => toggle(item)} busy={busyId === item.key} format={format} branchName={branchName} onConfirmCash={confirmCash} onPrepare={prepare} onHandOver={handOver} />
      : <ShipmentCard key={item.key} item={item} open={openKey === item.key} onToggle={() => toggle(item)} busy={busyId === item.key} format={format} branchName={branchName} detail={details[item.row.id]} onAdvance={advance} />)}
  </section>;
}

function PickupCard({ item, open, onToggle, busy, format, branchName, onConfirmCash, onPrepare, onHandOver }) {
  const t = useT(retailMessages);
  const time = useDisplayTime();
  const { row } = item;
  const order = row.order;
  const cash = order.paymentMethod === 'CASH_AT_COUNTER';
  const urgent = item.tab === 'AWAITING_CASH' && order.counterPaymentDeadline && new Date(order.counterPaymentDeadline).getTime() - Date.now() < DAY_MS;
  const pill = t(`staff.pill.${item.tab}`);
  return <article className={`order-card${urgent ? ' urgent' : ''}${open ? ' open' : ''}`} {...cardToggleProps(open, onToggle)}>
    <div className="order-card-top">
      <span className="order-code">{item.code}</span>
      <span className="order-branch">{branchName(order.locationId)} · {t('common.storePickup')}</span>
      <span className={`order-pill pill-${item.tab.toLowerCase()}`}>{pill}</span>
    </div>
    <ul className="order-items">{(order.items || []).map(line => <li key={`${order.id}-${line.variantId}`}>
      <ProductThumb className="order-thumb" productId={line.productId} />
      <span className="order-item-name"><strong>{line.productName || t('common.productNumber', { variantId: line.variantId })}</strong><small>{t('common.size', { size: line.size ?? '—' })} · x{line.quantity}</small></span>
      <b>{line.lineTotal ? format.money(line.lineTotal) : ''}</b>
    </li>)}</ul>
    {item.tab === 'AWAITING_CASH' && <p className={`order-hint${urgent ? ' warn' : ''}`}>{t('staff.heldUntil')} {time(order.counterPaymentDeadline)} ({timeLeft(order.counterPaymentDeadline, t)}).</p>}
    {open && <dl className="order-detail">
      <div><dt>{t('common.orderCode')}</dt><dd>{item.code}</dd></div>
      <div><dt>{t('common.orderedAt')}</dt><dd>{time(order.orderedAt)}</dd></div>
      <div><dt>{t('common.payment')}</dt><dd>{cash ? t('common.cashAtCounter') : 'VNPay'}</dd></div>
      {order.completedAt && <div><dt>{t('staff.handedOverAt')}</dt><dd>{time(order.completedAt)}</dd></div>}
      <OrderMoneyRows order={order} format={format} />
      {row.cashCollection && <div><dt>{t('staff.cash')}</dt><dd>{t('staff.receivedChangeAt', { amountReceived: format.money(row.cashCollection.amountReceived), changeReturned: format.money(row.cashCollection.changeReturned), confirmedAt: time(row.cashCollection.confirmedAt) })}</dd></div>}
    </dl>}
    <div className="order-card-bottom">
      <span className="order-more">{open ? t('common.showLess') : t('common.clickForDetails')}</span>
      <span className="order-sum">{item.tab === 'AWAITING_CASH' ? t('staff.toCollect') : t('common.total')} <strong>{format.money(order.payableAmount)}</strong></span>
    </div>
    {item.tab === 'AWAITING_CASH' && <div className="order-actions">
      <button type="button" className="button primary" disabled={busy} onClick={() => onConfirmCash(item)}>{busy ? t('staff.saving') : t('staff.confirmCollected', { payableAmount: format.money(order.payableAmount) })}</button>
    </div>}
    {item.tab === 'PREPARING' && <div className="order-actions"><span className="muted">{cash ? t('staff.cashCollected') : t('staff.paidByVnpay')}</span><button type="button" className="button primary" disabled={busy} onClick={() => onPrepare(item)}>{busy ? t('staff.saving') : t('staff.packedAndReady')}</button></div>}
    {item.tab === 'TO_HAND_OVER' && <div className="order-actions"><button type="button" className="button primary" disabled={busy} onClick={() => onHandOver(item)}>{busy ? t('staff.saving') : t('staff.handToCustomer')}</button></div>}
  </article>;
}

function ShipmentCard({ item, open, onToggle, busy, format, branchName, detail, onAdvance }) {
  const t = useT(retailMessages);
  const ts = useT(sharedMessages);
  const time = useDisplayTime();
  const statusLabel = useEnumLabel(retailMessages, 'staff.shipment');
  const { row } = item;
  const next = nextShipmentStatus[row.status];
  return <article className={`order-card${open ? ' open' : ''}`} {...cardToggleProps(open, onToggle)}>
    <div className="order-card-top">
      <span className="order-code">{item.code}</span>
      <span className="order-branch">{branchName(row.locationId)} · GHTK {row.mockTrackingNumber}</span>
      <span className={`order-pill pill-${item.tab.toLowerCase()}`}>{statusLabel(row.status)}</span>
    </div>
    <div className="order-recipient">
      <span className="order-thumb" aria-hidden="true">⛟</span>
      <span className="order-item-name"><strong>{row.recipientName || t('staff.noRecipientName')}</strong><small>{[row.wardName, row.districtName, row.provinceName].filter(Boolean).join(', ')}</small></span>
      {row.itemQuantity ? <b>{row.itemQuantity} {t('staff.items')}</b> : null}
    </div>
    {open && <dl className="order-detail">
      <div><dt>{t('common.orderCode')}</dt><dd>{item.code}</dd></div>
      <div><dt>{t('staff.trackingNumber')}</dt><dd>{row.mockTrackingNumber}</dd></div>
      {detail ? <>
        <div><dt>{t('staff.phone')}</dt><dd>{detail.recipientPhone || '—'}</dd></div>
        <div className="wide"><dt>{t('staff.address')}</dt><dd>{[detail.recipientAddress, detail.wardName, detail.districtName, detail.provinceName].filter(Boolean).join(', ')}</dd></div>
        <div className="wide"><dt>{t('staff.history')}</dt><dd><ol className="order-timeline">{(detail.history || []).map((entry, index) => <li key={index}><strong>{statusLabel(entry.status)}</strong> · {time(entry.createdAt)} · {entry.actorLabel}</li>)}</ol></dd></div>
      </> : <div className="wide"><dt>{t('staff.details')}</dt><dd>{ts('loading')}</dd></div>}
    </dl>}
    <div className="order-card-bottom">
      <span className="order-more">{open ? t('common.showLess') : t('common.clickForDetails')}</span>
      {row.payableAmount && <span className="order-sum">{t('common.total')} <strong>{format.money(row.payableAmount)}</strong></span>}
    </div>
    {next && <div className="order-actions"><button type="button" className="button primary" disabled={busy} onClick={() => onAdvance(item)}>{busy ? t('common.saving') : t(`staff.nextAction.${row.status}`)}</button></div>}
    {COURIER_WAITING.includes(row.status) && <p className="order-hint">{t(`staff.courierWaiting.${row.status}`)}</p>}
  </article>;
}
