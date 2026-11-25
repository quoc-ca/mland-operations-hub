import React, { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router';
import { QRCodeSVG } from 'qrcode.react';
import { useHttp } from '../../shared/api/useHttp';
import { useRole } from '../../shared/auth/useRole';
import { Field, Loading, SuccessNotice, useCommand, useResource } from '../../shared/ui/components';
import { createSettlementApi } from '../api/settlementApi';
import FolioCorrectionLookup from '../FolioCorrectionLookup';
import { OpsPage } from '../OpsPage';
import { useEnumLabel, useFormat, useT } from '../../shared/i18n/I18nProvider';
import { sharedMessages } from '../../shared/ui/messages';
import { messages } from '../messages';
import { ProblemNotice, ReasonDialog, asList, isWholeVnd, signedMoney } from '../ui';

const METHOD_ORDER = { BOOKING_TIME: 0, VNPAY: 1, CASH: 2, BANK_TRANSFER: 3 };
const VNPAY_MINIMUM = 5000;

// Dev-only: when VITE_PAYMENT_SIMULATOR_BASE_URL (a public tunnel to the backend) is set, the customer scans a QR that
// opens the backend's fake VNPay page instead of the staff browser being taken to VNPay.
// The simulator is opt-in: VITE_PAYMENT_QR_MODE=simulator. Otherwise the QR carries the real VNPay checkout link, so the
// customer pays on their own phone and VNPay (sandbox) records the transaction.
const SIMULATOR_BASE_URL = import.meta.env.VITE_PAYMENT_QR_MODE === 'simulator'
  ? (import.meta.env.VITE_PAYMENT_SIMULATOR_BASE_URL || '').replace(/\/$/, '') : '';
export function paymentQrValue(paymentUrl, simulatorBaseUrl = SIMULATOR_BASE_URL) {
  return simulatorLink(paymentUrl, simulatorBaseUrl) || paymentUrl || '';
}
export function simulatorLink(paymentUrl, baseUrl = SIMULATOR_BASE_URL) {
  if (!baseUrl || !paymentUrl) return '';
  try {
    const ref = new URL(paymentUrl).searchParams.get('vnp_TxnRef');
    return ref ? `${baseUrl.replace(/\/$/, '')}/api/v1/integrations/payments/simulator/${encodeURIComponent(ref)}` : '';
  } catch { return ''; }
}
// T145: the 409 codes of a cash reversal (FR-036/FR-053) and of an absorbed undercharge; their text is ws.problem.<CODE>.
const WORKSPACE_PROBLEMS = [
  'FINAL_BILL_NOT_OPEN', 'COLLECTION_UNDER_REFUND', 'CASH_DAY_NOT_CLOSED', 'COLLECTION_NOT_CASH', 'COLLECTION_ALREADY_VOIDED',
  'COLLECTION_ALREADY_REVERSED', 'COLLECTION_NOT_CONFIRMED', 'FOLIO_NOT_FINALIZED', 'UNDERCHARGE_EXCEEDS_FOLIO_TOTAL',
];

/** Collections in the order VNPay, cash, bank transfer (booking-time payment first); the amounts are the server's. */
export const orderedCollections = collections => [...(collections || [])]
  .map((item, index) => ({ item, index }))
  .sort((a, b) => (METHOD_ORDER[a.item.method] ?? 9) - (METHOD_ORDER[b.item.method] ?? 9) || a.index - b.index)
  .map(entry => entry.item);

/**
 * FR-033: say so BEFORE collecting when a VNPay part would be under 5,000 VND. The screen never raises or hides an
 * amount; it only warns (the server refuses a VNPay part under the minimum). `t` is the module translator.
 */
export function vnpayWarning(method, amountText, balanceDue, t) {
  const amount = Number(amountText);
  if (!isWholeVnd(amountText)) return '';
  if (method === 'VNPAY' && amount < VNPAY_MINIMUM) {
    return t('ws.warn.vnpayMin');
  }
  const balance = Number(balanceDue);
  if (method !== 'VNPAY' && Number.isFinite(balance) && balance > amount && balance - amount < VNPAY_MINIMUM) {
    return t('ws.warn.remainder');
  }
  return '';
}

/**
 * FR-060, T187: a Manager sees the catalogue add-ons sold at a location the ticket no longer belongs to. Read only;
 * rendered only when the list is not empty. A non-Manager never calls the route.
 */
function PreviousLocationAddOns({ api, ticketCode, enabled }) {
  const t = useT(messages);
  const fmt = useFormat();
  const list = useResource(() => (enabled && ticketCode ? api.listPreviousLocationAddOns(ticketCode) : Promise.resolve([])), [ticketCode, enabled]);
  if (!enabled) return null;
  if (list.loading) return <p className="muted small" role="status">{t('ws.prev.loading')}</p>;
  if (list.error) {
    return (
      <div className="notice error" role="note">
        <p>{t('ws.prev.error')}</p>
        <button type="button" className="button outline small" onClick={list.reload}>{t('ws.prev.retry')}</button>
      </div>
    );
  }
  const items = asList(list.data);
  if (items.length === 0) return null;
  return (
    <section aria-label={t('ws.prev.aria')} className="ota-panel">
      <h3>{t('ws.prev.title')}</h3>
      <ul>
        {items.map(item => (
          <li key={item.chargeReference}>
            {t('ws.prev.item', { description: item.description, amount: fmt.money(item.amount), from: item.addOnLocationReference, to: item.ticketLocationReference })}
          </li>
        ))}
      </ul>
    </section>
  );
}

/** A long reference (SETTLE-CHARGE-<uuid>) shortened for a table cell; the full value stays in the hover text. */
export const shortReference = value => {
  const text = String(value || '');
  return text.length > 22 ? `${text.slice(0, 22)}…` : text;
};

export default function SettlementWorkspacePage() {
  const tShared = useT(sharedMessages);
  const t = useT(messages);
  const fmt = useFormat();
  const folioStatus = useEnumLabel(messages, 'folioStatus');
  const collectionStatus = useEnumLabel(messages, 'collectionStatus');
  const methodLabel = useEnumLabel(messages, 'method');
  const lineStatus = useEnumLabel(messages, 'lineStatus');
  const underStatus = useEnumLabel(messages, 'underStatus');
  const http = useHttp();
  const api = createSettlementApi(http);
  const role = useRole();
  const isManager = role === 'MANAGER' || role === 'ADMIN';
  // A link from another screen (the attendance roster) opens the folio of a ticket: ?ticket=GRP-…-L1-T1.
  const [searchParams, setSearchParams] = useSearchParams();
  const linkedTicket = (searchParams.get('ticket') || '').trim();
  const [ticketInput, setTicketInput] = useState(linkedTicket);
  const [ticketCode, setTicketCode] = useState(linkedTicket);
  const [message, setMessage] = useState('');
  const [collection, setCollection] = useState({ method: 'VNPAY', amount: '', cashReceived: '', reference: '' });
  const [transferRefs, setTransferRefs] = useState({});
  const [checkout, setCheckout] = useState(null);
  const [failing, setFailing] = useState(null);
  const [dangerous, setDangerous] = useState(null); // { kind: 'debt' | 'closeDebt' | 'handover' }
  const [voiding, setVoiding] = useState(null); // { kind: 'line' | 'collection', id, label }
  const [reversing, setReversing] = useState(null); // the CONFIRMED cash collection of a closed day
  const [undercharge, setUndercharge] = useState(null); // { amount, amountError } while the record dialog is open
  const [voidingUndercharge, setVoidingUndercharge] = useState(null);
  const [undercharges, setUndercharges] = useState([]); // records made or voided on this screen for this ticket
  const [addOn, setAddOn] = useState([]); // catalogue references the customer agreed to
  const [consent, setConsent] = useState(false);
  const [charge, setCharge] = useState({ description: '', amount: '', reason: '' });
  const command = useCommand();

  const folio = useResource(() => (ticketCode ? api.getFolio(ticketCode) : Promise.resolve(null)), [ticketCode]);
  const open = folio.data?.status === 'OPEN';
  const addOns = useResource(() => (ticketCode && open ? api.listAddOns(ticketCode) : Promise.resolve([])), [ticketCode, open]);

  const bill = folio.data;
  const warning = vnpayWarning(collection.method, collection.amount, bill?.balanceDue, t);

  // The amount defaults to what is still unassigned (balance due minus pending parts), so it is never typed by hand.
  const unassignedDue = bill ? Math.max(0, Number(bill.balanceDue) - Number(bill.pendingAmount || 0)) : 0;
  useEffect(() => {
    setCollection(v => ({ ...v, amount: unassignedDue > 0 ? String(unassignedDue) : '' }));
  }, [ticketCode, unassignedDue]);

  // While a VNPay QR is on screen the customer pays on their phone, so read the folio quietly every few seconds
  // (no loading flash) and drop the QR as soon as that collection leaves PENDING_VERIFICATION.
  const checkoutId = checkout?.collectionId;
  useEffect(() => {
    if (!checkout || !ticketCode) return undefined;
    let stopped = false;
    const timer = setInterval(async () => {
      try {
        const next = await api.getFolio(ticketCode);
        if (stopped || !next) return;
        folio.setData(next);
        const mine = checkoutId ? (next.collections || []).find(c => c.collectionId === checkoutId) : null;
        if (mine && mine.status !== 'PENDING_VERIFICATION') {
          setCheckout(null);
          if (mine.status === 'CONFIRMED') setMessage(t('ws.msg.vnpayPaid'));
        }
      } catch { /* keep the QR; the next tick retries */ }
    }, 4000);
    return () => { stopped = true; clearInterval(timer); };
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [checkout, checkoutId, ticketCode]);

  async function run(send, success, onDone) {
    setMessage('');
    const out = await command.run(send);
    if (out.ok) { setMessage(success); onDone?.(out.result); folio.reload(); }
    return out;
  }

  const lookup = event => { event.preventDefault(); setSearchParams(ticketInput.trim() ? { ticket: ticketInput.trim() } : {}, { replace: true }); setTicketCode(ticketInput.trim()); setCheckout(null); setMessage(''); setUndercharges([]); command.reset(); };

  const submitCollection = event => {
    event.preventDefault();
    const body = { method: collection.method, amount: collection.amount.trim() };
    if (collection.method === 'CASH' && collection.cashReceived.trim()) body.cashReceived = collection.cashReceived.trim();
    if (collection.method === 'BANK_TRANSFER' && collection.reference.trim()) body.customerTransactionReference = collection.reference.trim();
    run(key => api.recordCollection(ticketCode, body, key), t('ws.msg.collectionRecorded'), result => {
      setCheckout(result?.paymentUrl ? { url: result.paymentUrl, expiresAt: result.paymentExpiresAt, collectionId: result.collectionId } : null);
      setCollection(v => ({ ...v, amount: '', cashReceived: '', reference: '' }));
    });
  };

  const confirmTransfer = item => run(
    key => api.confirmTransfer(ticketCode, item.collectionId, { bankTransactionReference: (transferRefs[item.collectionId] || '').trim() }, key),
    t('ws.msg.transferConfirmed'),
  );

  const confirmReversal = async reason => {
    const out = await run(key => api.reverseCashCollection(ticketCode, reversing.collectionId, { reason }, key), t('ws.msg.cashReversed'),
      result => setMessage(t('ws.msg.cashReversedDetail', { amount: signedMoney(fmt, result?.amountVnd), date: result?.workingDate })));
    if (out.ok) setReversing(null);
  };

  const confirmUndercharge = async reason => {
    if (!isWholeVnd(undercharge.amount)) { setUndercharge(v => ({ ...v, amountError: t('ws.underAmountError') })); return; }
    const out = await run(key => api.recordAbsorbedUndercharge(ticketCode, { amountVnd: undercharge.amount.trim(), reason }, key), t('ws.msg.underRecorded'),
      result => result && setUndercharges(list => [...list, result]));
    if (out.ok) setUndercharge(null);
  };

  const confirmVoidUndercharge = async reason => {
    const out = await run(key => api.voidAbsorbedUndercharge(ticketCode, voidingUndercharge.underchargeReference, { reason }, key), t('ws.msg.underVoided'),
      result => result && setUndercharges(list => list.map(item => (item.underchargeReference === result.underchargeReference ? result : item))));
    if (out.ok) setVoidingUndercharge(null);
  };

  const confirmDangerous = async reason => {
    if (failing) {
      const out = await run(key => api.failCollection(ticketCode, failing.collectionId, { reason }, key), t('ws.msg.notPaid'));
      if (out.ok) setFailing(null);
    } else if (voiding) {
      const send = voiding.kind === 'line' ? api.voidLine : api.voidCollection;
      const out = await run(key => send(ticketCode, voiding.id, { reason }, key), voiding.kind === 'line' ? t('ws.msg.lineVoided') : t('ws.msg.collectionVoided'));
      if (out.ok) setVoiding(null);
    } else if (dangerous?.kind === 'handover') {
      // T126 (FR-035): the folio is reloaded afterwards, so the balance shown is the server's.
      const out = await run(key => api.allowHandover(ticketCode, { reason }, key),
        t('ws.msg.handover'));
      if (out.ok) setDangerous(null);
    } else if (dangerous) {
      const send = dangerous.kind === 'debt' ? api.recordDebt : api.closeDebt;
      const out = await run(key => send(ticketCode, { reason }, key), dangerous.kind === 'debt' ? t('ws.msg.debtRecorded') : t('ws.msg.debtClosed'));
      if (out.ok) setDangerous(null);
    }
  };

  const submitAddOn = event => { event.preventDefault(); submitAddOns(); };
  // One charge per add-on (the contract takes a single reference), each with its own Idempotency-Key; stops at the first refusal.
  async function submitAddOns() {
    setMessage('');
    const added = [];
    for (const reference of addOn) {
      const out = await command.run(key => api.addCharge(ticketCode, { catalogueAddOnReference: reference, customerConsentConfirmed: true }, key));
      if (!out.ok) break;
      added.push(reference);
    }
    if (added.length > 0) {
      setMessage(added.length === 1 ? t('ws.msg.addonAdded') : t('ws.msg.addonsAdded', { count: added.length }));
      setAddOn(v => v.filter(reference => !added.includes(reference)));
      if (added.length === addOn.length) setConsent(false);
      folio.reload();
    }
  }
  const submitCharge = event => {
    event.preventDefault();
    run(key => api.addCharge(ticketCode, { description: charge.description.trim(), amount: Number(charge.amount), reason: charge.reason.trim(), customerConsentConfirmed: true }, key),
      t('ws.msg.chargeAdded'), () => { setCharge({ description: '', amount: '', reason: '' }); setConsent(false); });
  };

  const dialogOpen = Boolean(failing || dangerous || voiding);
  const dialogTitle = failing ? t('ws.dlg.failTitle')
    : voiding ? t(voiding.kind === 'line' ? 'ws.dlg.voidLine' : 'ws.dlg.voidCollection')
      : dangerous?.kind === 'handover' ? t('ws.dlg.handoverTitle')
        : dangerous?.kind === 'debt' ? t('ws.dlg.debtTitle') : t('ws.dlg.closeDebtTitle');
  const unsettled = Number(bill?.balanceDue) > 0;
  const addOnOptions = asList(addOns.data);

  return (
    <OpsPage title={t('ws.title')} intro={t('ws.intro')}>
      <SuccessNotice>{message}</SuccessNotice>
      {WORKSPACE_PROBLEMS.includes(command.error?.code) && (
        <div className="notice error" role="alert"><p>{t(`ws.problem.${command.error.code}`)}</p></div>
      )}
      <ProblemNotice error={folio.error || command.error} onRetry={command.retry} onReload={folio.reload} />
      <section className="ota-panel">
        <form className="ota-inline-form settlement-lookup" onSubmit={lookup}>
          <label>{t('ws.ticketCode')}<input value={ticketInput} onChange={event => setTicketInput(event.target.value)} placeholder="GRP-…-L0-T1" /></label>
          <button className="button primary" type="submit" disabled={!ticketInput.trim()}>{t('ws.openFolio')}</button>
        </form>
      </section>

      {folio.loading && ticketCode ? <Loading /> : null}
      {ticketCode && bill ? <PreviousLocationAddOns api={api} ticketCode={ticketCode} enabled={isManager} /> : null}
      {ticketCode && bill ? (
        <section className="settlement-grid">
          <article className="ota-panel settlement-summary">
            <div className="ota-panel-head"><h2>{bill.ticketCode || ticketCode}</h2><span className="ops-status">{folioStatus(bill.status)}</span></div>
            <div className="settlement-amount"><span>{t('ws.balanceDue')}</span><strong>{fmt.money(bill.balanceDue)}</strong></div>
            <div className="settlement-amounts">
              <div><span>{t('ws.total')}</span><strong>{fmt.money(bill.totalAmount)}</strong></div>
              <div><span>{t('ws.paid')}</span><strong>{fmt.money(bill.paidAmount)}</strong></div>
              <div><span>{t('ws.pending')}</span><strong>{fmt.money(bill.pendingAmount)}</strong></div>
            </div>
            <div className="ops-actions">
              <button className="button primary" type="button" disabled={command.pending} onClick={() => run(key => api.finalize(ticketCode, key), t('ws.msg.finalized'))}>{t('ws.finalize')}</button>
              {isManager && <button className="button outline" type="button" onClick={() => setDangerous({ kind: 'debt' })}>{t('ws.recordDebt')}</button>}
              {isManager && <button className="button outline" type="button" onClick={() => setDangerous({ kind: 'closeDebt' })}>{t('ws.closeDebt')}</button>}
              {isManager && unsettled && <button className="button outline" type="button" onClick={() => setDangerous({ kind: 'handover' })}>{t('ws.allowHandover')}</button>}
              {isManager && bill.status === 'FINALIZED' && <button className="button outline" type="button" onClick={() => setUndercharge({ amount: '', amountError: '' })}>{t('ws.recordUndercharge')}</button>}
            </div>
          </article>

          <article className="ota-panel">
            <div className="ota-panel-head"><h2>{t('ws.collections')}</h2><span className="muted">{t('ws.collectionsHint')}</span></div>
            {(bill.collections || []).length === 0 ? <p className="muted">{t('ws.noCollection')}</p> : (
              <div className="ops-table-wrap"><table className="ops-table fl-table">
                <colgroup><col className="c-main" /><col className="c-desc" /><col className="c-amount" /><col className="c-status" /><col className="c-actions" /></colgroup>
                <thead><tr><th colSpan={2}>{t('ws.col.method')}</th><th>{t('ws.col.amount')}</th><th>{t('ws.col.status')}</th><th>{t('ws.col.actions')}</th></tr></thead>
                <tbody>
                  {orderedCollections(bill.collections).map(item => (
                    <tr key={item.collectionId}>
                      <td colSpan={2}>{methodLabel(item.method)}{item.method === 'BOOKING_TIME' && <small className="muted"> {t('ws.bookingTimeNote')}</small>}</td>
                      <td>{fmt.money(item.amount)}{item.changeReturned && item.changeReturned !== '0' ? <small> ({t('ws.change', { amount: fmt.money(item.changeReturned) })})</small> : null}</td>
                      <td>
                        <span className="ops-status">{collectionStatus(item.status)}</span>
                        {item.status === 'PENDING_VERIFICATION' ? <small className="muted"> {t('ws.awaitingVerification')}</small> : null}
                      </td>
                      <td>
                        {item.status === 'PENDING_VERIFICATION' && item.method === 'BANK_TRANSFER' && isManager && (
                          <span className="settlement-row-form">
                            <input aria-label={t('ws.bankRefAria', { id: item.collectionId })} placeholder={t('ws.bankRef')} value={transferRefs[item.collectionId] || ''}
                              onChange={event => setTransferRefs(v => ({ ...v, [item.collectionId]: event.target.value }))} />
                            <button type="button" className="button outline" disabled={command.pending || !(transferRefs[item.collectionId] || '').trim()} onClick={() => confirmTransfer(item)}>{t('ws.confirmTransfer')}</button>
                          </span>
                        )}
                        {item.status === 'PENDING_VERIFICATION' && isManager && (
                          <button type="button" className="button danger" onClick={() => setFailing(item)}>{t('ws.markFailed')}</button>
                        )}
                        {item.status === 'PENDING_VERIFICATION' && !isManager && <small className="muted">{t('ws.managerVerifies')}</small>}
                        {isManager && item.status === 'CONFIRMED' && item.method === 'CASH' && item.cashDayClosed && (
                          <button type="button" className="button outline" onClick={() => setReversing(item)}>{t('ws.reverseCash')}</button>
                        )}
                        {open && isManager && item.method !== 'BOOKING_TIME' && item.status === 'CONFIRMED' && !item.cashDayClosed && (
                          <button type="button" className="button outline" onClick={() => setVoiding({ kind: 'collection', id: item.collectionId })}>{t('ws.void')}</button>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table></div>
            )}
            {(bill.charges || []).length > 0 && (
              <div className="ops-table-wrap"><table className="ops-table fl-table" aria-label={t('ws.lines')}>
                <colgroup><col className="c-main" /><col className="c-desc" /><col className="c-amount" /><col className="c-status" /><col className="c-actions" /></colgroup>
                <thead><tr><th>{t('ws.col.line')}</th><th>{t('ws.col.description')}</th><th>{t('ws.col.amount')}</th><th>{t('ws.col.status')}</th><th /></tr></thead>
                <tbody>
                  {bill.charges.map(line => (
                    <tr key={line.chargeReference}>
                      <td title={line.chargeReference}><code className="fl-ref">{shortReference(line.chargeReference)}</code></td>
                      <td className="fl-wrap">{line.description}</td>
                      <td>{fmt.money(line.amount)}</td>
                      <td><span className="ops-status">{lineStatus(line.status)}</span></td>
                      <td>
                        {open && isManager && line.status !== 'VOIDED' && (
                          <button type="button" className="button outline" onClick={() => setVoiding({ kind: 'line', id: line.chargeReference })}>{t('ws.voidLine')}</button>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table></div>
            )}
            {checkout ? (
              <div className="settlement-payment-state pending" role="status">
                <strong>{t('ws.checkoutCreated')}</strong>
                <p>{t('ws.checkoutValid', { time: fmt.dateTime(checkout.expiresAt) })}</p>
                {paymentQrValue(checkout.url) ? (
                  <div className="settlement-sim-qr">
                    <QRCodeSVG value={paymentQrValue(checkout.url)} size={200} includeMargin />
                    <p>{t('ws.checkoutScan')}</p>
                  </div>
                ) : null}
                <a className="button primary" href={checkout.url} target="_blank" rel="noreferrer">{t('ws.checkoutOpen')}</a>
              </div>
            ) : null}
            {open && (
              <form className="ota-form settlement-form" aria-label={t('ws.recordCollectionAria')} onSubmit={submitCollection}>
                <h3>{t('ws.recordCollection')}</h3>
                <label>{t('ws.method')}
                  <select value={collection.method} onChange={event => setCollection(v => ({ ...v, method: event.target.value }))}>
                    <option value="VNPAY">{methodLabel('VNPAY')}</option>
                    <option value="CASH">{methodLabel('CASH')}</option>
                    <option value="BANK_TRANSFER">{methodLabel('BANK_TRANSFER')}</option>
                  </select>
                </label>
                <label>{t('ws.amountVnd')}<input inputMode="numeric" value={collection.amount} onChange={event => setCollection(v => ({ ...v, amount: event.target.value }))} /></label>
                {collection.method === 'CASH' && <label>{t('ws.cashReceived')}<input inputMode="numeric" value={collection.cashReceived} onChange={event => setCollection(v => ({ ...v, cashReceived: event.target.value }))} /></label>}
                {collection.method === 'BANK_TRANSFER' && <label>{t('ws.customerTxnRef')}<input value={collection.reference} maxLength={128} onChange={event => setCollection(v => ({ ...v, reference: event.target.value }))} /></label>}
                {warning && <p className="notice warning" role="alert">{warning}</p>}
                <p className="muted small">{t('ws.collectionNote')}</p>
                <button className="button primary" type="submit" disabled={command.pending || !isWholeVnd(collection.amount)}>{t('ws.recordCollectionAria')}</button>
              </form>
            )}
          </article>

          {open && (
            <article className="ota-panel settlement-addons">
              <div className="ota-panel-head"><h2>{t('ws.addonsTitle')}</h2></div>
              <form className="ota-form settlement-form" aria-label={t('ws.catalogueAria')} onSubmit={submitAddOn}>
                <h3>{t('ws.catalogueTitle')}</h3>
                {addOns.loading ? <p className="muted">{t('ws.addonsLoading')}</p>
                  : addOns.error ? <p className="muted" role="status">{t('ws.addonsError')}</p>
                    : addOnOptions.length === 0 ? <p className="muted">{t('ws.addonsNone')}</p> : (
                      <fieldset className="settlement-addon-options">
                        <legend className="muted">{t('ws.addonsTick')}</legend>
                        {addOnOptions.map(option => (
                          <label key={option.catalogueAddOnReference} className="settlement-addon-option">
                            <input type="checkbox" name="catalogue-add-on" value={option.catalogueAddOnReference} checked={addOn.includes(option.catalogueAddOnReference)} onChange={event => setAddOn(v => (event.target.checked ? [...v, option.catalogueAddOnReference] : v.filter(reference => reference !== option.catalogueAddOnReference)))} />
                            <span>{option.name}</span><strong>{fmt.money(option.price)}</strong>
                          </label>
                        ))}
                      </fieldset>
                    )}
                <label className="settlement-addon-consent"><input type="checkbox" checked={consent} onChange={event => setConsent(event.target.checked)} /><span>{t('ws.consent')}</span></label>
                <button className="button primary" type="submit" disabled={command.pending || addOn.length === 0 || !consent}>{t('ws.addSelected')}</button>
              </form>
              <form className="ota-form settlement-form" aria-label={t('ws.chargeAria')} onSubmit={submitCharge}>
                <h3>{t('ws.chargeTitle')}</h3>
                <p className="muted small">{t('ws.chargeHint')}</p>
                <label>{t('ws.description')}<input value={charge.description} maxLength={200} onChange={event => setCharge(v => ({ ...v, description: event.target.value }))} /></label>
                <label>{t('ws.amountVnd')}<input inputMode="numeric" value={charge.amount} onChange={event => setCharge(v => ({ ...v, amount: event.target.value }))} /></label>
                <label>{t('ws.reasonField')}<input value={charge.reason} maxLength={255} onChange={event => setCharge(v => ({ ...v, reason: event.target.value }))} /></label>
                <button className="button outline" type="submit" disabled={command.pending || !charge.description.trim() || !isWholeVnd(charge.amount) || !charge.reason.trim() || !consent}>{t('ws.addCharge')}</button>
              </form>
            </article>
          )}

          {isManager && undercharges.length > 0 && (
            <article className="ota-panel" aria-label={t('ws.underAria')}>
              <div className="ota-panel-head"><h2>{t('ws.underTitle')}</h2><span className="muted">{t('ws.underHint')}</span></div>
              <table className="ops-table">
                <thead><tr><th>{t('ws.col.reference')}</th><th>{t('ws.col.amount')}</th><th>{t('ws.col.reason')}</th><th>{t('ws.col.status')}</th><th /></tr></thead>
                <tbody>
                  {undercharges.map(record => (
                    <tr key={record.underchargeReference}>
                      <td>{record.underchargeReference}</td>
                      <td>{fmt.money(record.amountVnd)}</td>
                      <td>{record.reason}</td>
                      <td><span className="ops-status">{underStatus(record.status)}</span></td>
                      <td>{record.status === 'ACTIVE' && <button type="button" className="button outline" onClick={() => setVoidingUndercharge(record)}>{t('ws.voidRecord')}</button>}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
              <p className="muted small">{t('ws.underNote')}</p>
            </article>
          )}

          <article className="ota-panel">
            <FolioCorrectionLookup key={ticketCode} api={api} ticketCode={ticketCode} />
          </article>
        </section>
      ) : null}
      {ticketCode && !folio.loading && !bill && !folio.error ? <div className="empty-state"><p>{t('ws.noFolio')}</p></div> : null}

      <ReasonDialog open={Boolean(reversing)} title={t('ws.reverseTitle')} confirmLabel={t('ws.reverseCash')} pending={command.pending} onCancel={() => setReversing(null)} onConfirm={confirmReversal}>
        <p>{t('ws.reverseBody')}</p>
      </ReasonDialog>
      <ReasonDialog open={Boolean(undercharge)} title={t('ws.underDialogTitle')} confirmLabel={t('ws.underDialogConfirm')} pending={command.pending} onCancel={() => setUndercharge(null)} onConfirm={confirmUndercharge}>
        <p>{t('ws.underDialogBody')}</p>
        <Field label={t('ws.amountVnd')} error={undercharge?.amountError || undefined}>
          <input inputMode="numeric" value={undercharge?.amount || ''} onChange={event => setUndercharge(v => ({ ...v, amount: event.target.value, amountError: '' }))} />
        </Field>
      </ReasonDialog>
      <ReasonDialog open={Boolean(voidingUndercharge)} title={t('ws.voidUnderTitle')} confirmLabel={t('ws.voidRecord')} pending={command.pending} onCancel={() => setVoidingUndercharge(null)} onConfirm={confirmVoidUndercharge}>
        <p>{t('ws.voidUnderBody')}</p>
      </ReasonDialog>
      <ReasonDialog open={dialogOpen} title={dialogTitle} confirmLabel={tShared('confirm')} pending={command.pending}
        onCancel={() => { setFailing(null); setDangerous(null); setVoiding(null); }} onConfirm={confirmDangerous}>
        <p>{dangerous?.kind === 'handover' ? t('ws.dlg.handoverBody') : voiding ? t('ws.dlg.voidBody') : failing ? t('ws.dlg.failBody') : t('ws.dlg.defaultBody')}</p>
      </ReasonDialog>
    </OpsPage>
  );
}
