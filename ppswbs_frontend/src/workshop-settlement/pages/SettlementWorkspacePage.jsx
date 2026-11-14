import React, { useState } from 'react';
import { useHttp } from '../../shared/api/useHttp';
import { useRole } from '../../shared/auth/useRole';
import { Loading, SuccessNotice, useCommand, useResource } from '../../shared/ui/components';
import { createSettlementApi } from '../api/settlementApi';
import FolioCorrectionLookup from '../FolioCorrectionLookup';
import { OpsPage } from '../OpsPage';
import { ProblemNotice, ReasonDialog, asList, isWholeVnd, money, when } from '../ui';

const METHOD_ORDER = { BOOKING_TIME: 0, VNPAY: 1, CASH: 2, BANK_TRANSFER: 3 };
const VNPAY_MINIMUM = 5000;

/** Collections in the order VNPay, cash, bank transfer (booking-time payment first); the amounts are the server's. */
export const orderedCollections = collections => [...(collections || [])]
  .map((item, index) => ({ item, index }))
  .sort((a, b) => (METHOD_ORDER[a.item.method] ?? 9) - (METHOD_ORDER[b.item.method] ?? 9) || a.index - b.index)
  .map(entry => entry.item);

/**
 * FR-033: say so BEFORE collecting when a VNPay part would be under 5,000 VND. The screen never raises or hides an
 * amount; it only warns (the server refuses a VNPay part under the minimum).
 */
export function vnpayWarning(method, amountText, balanceDue) {
  const amount = Number(amountText);
  if (!isWholeVnd(amountText)) return '';
  if (method === 'VNPAY' && amount < VNPAY_MINIMUM) {
    return 'A VNPay part must be at least 5,000 VND. Collect this amount by cash or bank transfer.';
  }
  const balance = Number(balanceDue);
  if (method !== 'VNPAY' && Number.isFinite(balance) && balance > amount && balance - amount < VNPAY_MINIMUM) {
    return 'The remainder after this part would be under 5,000 VND, so it cannot be collected by VNPay; it must be collected by cash or bank transfer.';
  }
  return '';
}

export default function SettlementWorkspacePage() {
  const http = useHttp();
  const api = createSettlementApi(http);
  const role = useRole();
  const isManager = role === 'MANAGER' || role === 'ADMIN';
  const [ticketInput, setTicketInput] = useState('');
  const [ticketCode, setTicketCode] = useState('');
  const [message, setMessage] = useState('');
  const [collection, setCollection] = useState({ method: 'CASH', amount: '', cashReceived: '', reference: '' });
  const [transferRefs, setTransferRefs] = useState({});
  const [checkout, setCheckout] = useState(null);
  const [failing, setFailing] = useState(null);
  const [dangerous, setDangerous] = useState(null); // { kind: 'debt' | 'closeDebt' | 'handover' }
  const [voiding, setVoiding] = useState(null); // { kind: 'line' | 'collection', id, label }
  const [addOn, setAddOn] = useState('');
  const [consent, setConsent] = useState(false);
  const [charge, setCharge] = useState({ description: '', amount: '', reason: '' });
  const command = useCommand();

  const folio = useResource(() => (ticketCode ? api.getFolio(ticketCode) : Promise.resolve(null)), [ticketCode]);
  const open = folio.data?.status === 'OPEN';
  const addOns = useResource(() => (ticketCode && open ? api.listAddOns(ticketCode) : Promise.resolve([])), [ticketCode, open]);

  const bill = folio.data;
  const warning = vnpayWarning(collection.method, collection.amount, bill?.balanceDue);

  async function run(send, success, onDone) {
    setMessage('');
    const out = await command.run(send);
    if (out.ok) { setMessage(success); onDone?.(out.result); folio.reload(); }
    return out;
  }

  const lookup = event => { event.preventDefault(); setTicketCode(ticketInput.trim()); setCheckout(null); setMessage(''); command.reset(); };

  const submitCollection = event => {
    event.preventDefault();
    const body = { method: collection.method, amount: collection.amount.trim() };
    if (collection.method === 'CASH' && collection.cashReceived.trim()) body.cashReceived = collection.cashReceived.trim();
    if (collection.method === 'BANK_TRANSFER' && collection.reference.trim()) body.customerTransactionReference = collection.reference.trim();
    run(key => api.recordCollection(ticketCode, body, key), 'Collection recorded.', result => {
      setCheckout(result?.paymentUrl ? { url: result.paymentUrl, expiresAt: result.paymentExpiresAt } : null);
      setCollection(v => ({ ...v, amount: '', cashReceived: '', reference: '' }));
    });
  };

  const confirmTransfer = item => run(
    key => api.confirmTransfer(ticketCode, item.collectionId, { bankTransactionReference: (transferRefs[item.collectionId] || '').trim() }, key),
    'Transfer confirmed.',
  );

  const confirmDangerous = async reason => {
    if (failing) {
      const out = await run(key => api.failCollection(ticketCode, failing.collectionId, { reason }, key), 'Collection marked as not paid.');
      if (out.ok) setFailing(null);
    } else if (voiding) {
      const send = voiding.kind === 'line' ? api.voidLine : api.voidCollection;
      const out = await run(key => send(ticketCode, voiding.id, { reason }, key), voiding.kind === 'line' ? 'Line voided.' : 'Collection voided.');
      if (out.ok) setVoiding(null);
    } else if (dangerous?.kind === 'handover') {
      // T126 (FR-035): the folio is reloaded afterwards, so the balance shown is the server's.
      const out = await run(key => api.allowHandover(ticketCode, { reason }, key),
        'Handover allowed and audited. The balance below is still owed.');
      if (out.ok) setDangerous(null);
    } else if (dangerous) {
      const send = dangerous.kind === 'debt' ? api.recordDebt : api.closeDebt;
      const out = await run(key => send(ticketCode, { reason }, key), dangerous.kind === 'debt' ? 'Debt recorded.' : 'Debt closed.');
      if (out.ok) setDangerous(null);
    }
  };

  const submitAddOn = event => {
    event.preventDefault();
    run(key => api.addCharge(ticketCode, { catalogueAddOnReference: addOn, customerConsentConfirmed: true }, key), 'Add-on added.', () => { setAddOn(''); setConsent(false); });
  };
  const submitCharge = event => {
    event.preventDefault();
    run(key => api.addCharge(ticketCode, { description: charge.description.trim(), amount: Number(charge.amount), reason: charge.reason.trim(), customerConsentConfirmed: true }, key),
      'Charge added.', () => { setCharge({ description: '', amount: '', reason: '' }); setConsent(false); });
  };

  const dialogOpen = Boolean(failing || dangerous || voiding);
  const dialogTitle = failing ? 'Mark this collection as not paid'
    : voiding ? `Void this ${voiding.kind === 'line' ? 'line' : 'collection'}`
      : dangerous?.kind === 'handover' ? 'Allow product handover before payment is verified'
        : dangerous?.kind === 'debt' ? 'Record a debt on this ticket' : 'Close this ticket’s debt';
  const unsettled = Number(bill?.balanceDue) > 0;
  const addOnOptions = asList(addOns.data);

  return (
    <OpsPage eyebrow="TICKET FOLIO" title="Ticket folio" intro="Collect, verify and finalize one ticket. Amounts and statuses come from the server only.">
      <SuccessNotice>{message}</SuccessNotice>
      <ProblemNotice error={folio.error || command.error} onRetry={command.retry} onReload={folio.reload} />
      <section className="ota-panel">
        <form className="ota-inline-form" onSubmit={lookup}>
          <label>Ticket code<input value={ticketInput} onChange={event => setTicketInput(event.target.value)} placeholder="TK-..." /></label>
          <button className="button primary" type="submit" disabled={!ticketInput.trim()}>Open folio</button>
        </form>
      </section>

      {folio.loading && ticketCode ? <Loading /> : null}
      {ticketCode && bill ? (
        <section className="settlement-grid">
          <article className="ota-panel settlement-summary">
            <div className="ota-panel-head"><h2>{bill.ticketCode || ticketCode}</h2><span className="ops-status">{bill.status}</span></div>
            <div className="settlement-amount"><span>Balance due</span><strong>{money(bill.balanceDue)}</strong></div>
            <div className="settlement-amounts">
              <div><span>Total</span><strong>{money(bill.totalAmount)}</strong></div>
              <div><span>Paid</span><strong>{money(bill.paidAmount)}</strong></div>
              <div><span>Pending verification</span><strong>{money(bill.pendingAmount)}</strong></div>
            </div>
            <div className="ops-actions">
              <button className="button primary" type="button" disabled={command.pending} onClick={() => run(key => api.finalize(ticketCode, key), 'Folio finalized.')}>Finalize folio</button>
              {isManager && <button className="button outline" type="button" onClick={() => setDangerous({ kind: 'debt' })}>Record debt</button>}
              {isManager && <button className="button outline" type="button" onClick={() => setDangerous({ kind: 'closeDebt' })}>Close debt</button>}
              {isManager && unsettled && <button className="button outline" type="button" onClick={() => setDangerous({ kind: 'handover' })}>Allow handover</button>}
            </div>
          </article>

          <article className="ota-panel">
            <div className="ota-panel-head"><h2>Collections</h2><span className="muted">VNPay, cash, then bank transfer</span></div>
            {(bill.collections || []).length === 0 ? <p className="muted">No collection recorded.</p> : (
              <table className="ops-table">
                <thead><tr><th>Method</th><th>Amount</th><th>Status</th><th>Actions</th></tr></thead>
                <tbody>
                  {orderedCollections(bill.collections).map(item => (
                    <tr key={item.collectionId}>
                      <td>{item.method}</td>
                      <td>{money(item.amount)}{item.changeReturned && item.changeReturned !== '0' ? <small> (change {money(item.changeReturned)})</small> : null}</td>
                      <td>
                        <span className="ops-status">{item.status}</span>
                        {item.status === 'PENDING_VERIFICATION' ? <small className="muted"> Awaiting verification</small> : null}
                      </td>
                      <td>
                        {item.status === 'PENDING_VERIFICATION' && item.method === 'BANK_TRANSFER' && isManager && (
                          <span className="settlement-row-form">
                            <input aria-label={`Bank reference for ${item.collectionId}`} placeholder="Bank reference" value={transferRefs[item.collectionId] || ''}
                              onChange={event => setTransferRefs(v => ({ ...v, [item.collectionId]: event.target.value }))} />
                            <button type="button" className="button outline" disabled={command.pending || !(transferRefs[item.collectionId] || '').trim()} onClick={() => confirmTransfer(item)}>Confirm transfer</button>
                          </span>
                        )}
                        {item.status === 'PENDING_VERIFICATION' && isManager && (
                          <button type="button" className="button danger" onClick={() => setFailing(item)}>Mark as failed</button>
                        )}
                        {item.status === 'PENDING_VERIFICATION' && !isManager && <small className="muted">Manager verifies</small>}
                        {open && isManager && item.status === 'CONFIRMED' && (
                          <button type="button" className="button outline" onClick={() => setVoiding({ kind: 'collection', id: item.collectionId })}>Void</button>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
            {(bill.charges || []).length > 0 && (
              <table className="ops-table" aria-label="Folio lines">
                <thead><tr><th>Line</th><th>Description</th><th>Amount</th><th>Status</th><th /></tr></thead>
                <tbody>
                  {bill.charges.map(line => (
                    <tr key={line.chargeReference}>
                      <td>{line.chargeReference}</td>
                      <td>{line.description}</td>
                      <td>{money(line.amount)}</td>
                      <td><span className="ops-status">{line.status}</span></td>
                      <td>
                        {open && isManager && line.status !== 'VOIDED' && (
                          <button type="button" className="button outline" onClick={() => setVoiding({ kind: 'line', id: line.chargeReference })}>Void line</button>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
            {checkout ? (
              <div className="settlement-payment-state pending" role="status">
                <strong>VNPay checkout created</strong>
                <p>Valid until {when(checkout.expiresAt)}. The part counts only after VNPay’s signed confirmation.</p>
                <a className="button primary" href={checkout.url} target="_blank" rel="noreferrer">Open VNPay checkout</a>
              </div>
            ) : null}
            {open && (
              <form className="ota-form settlement-form" aria-label="Record collection" onSubmit={submitCollection}>
                <h3>Record a collection</h3>
                <label>Method
                  <select value={collection.method} onChange={event => setCollection(v => ({ ...v, method: event.target.value }))}>
                    <option value="VNPAY">VNPay</option>
                    <option value="CASH">Cash</option>
                    <option value="BANK_TRANSFER">Bank transfer</option>
                  </select>
                </label>
                <label>Amount (VND)<input inputMode="numeric" value={collection.amount} onChange={event => setCollection(v => ({ ...v, amount: event.target.value }))} /></label>
                {collection.method === 'CASH' && <label>Cash received (VND)<input inputMode="numeric" value={collection.cashReceived} onChange={event => setCollection(v => ({ ...v, cashReceived: event.target.value }))} /></label>}
                {collection.method === 'BANK_TRANSFER' && <label>Customer transaction reference<input value={collection.reference} maxLength={128} onChange={event => setCollection(v => ({ ...v, reference: event.target.value }))} /></label>}
                {warning && <p className="notice warning" role="alert">{warning}</p>}
                <p className="muted small">VNPay is confirmed only by VNPay’s signed answer; cash by the collecting Staff; a transfer by a Manager after seeing it in the shop account.</p>
                <button className="button primary" type="submit" disabled={command.pending || !isWholeVnd(collection.amount)}>Record collection</button>
              </form>
            )}
          </article>

          {open && (
            <article className="ota-panel">
              <div className="ota-panel-head"><h2>Add-ons and charges</h2></div>
              <form className="ota-form settlement-form" aria-label="Catalogue add-on" onSubmit={submitAddOn}>
                <h3>Add-on from catalogue</h3>
                {addOns.loading ? <p className="muted">Loading add-ons…</p>
                  : addOns.error ? <p className="muted" role="status">Could not load the add-on catalogue for this ticket.</p>
                    : addOnOptions.length === 0 ? <p className="muted">No add-on is available for this package at this location.</p> : (
                      <fieldset className="settlement-addon-options">
                        <legend className="muted">Tick the add-on the customer agreed to.</legend>
                        {addOnOptions.map(option => (
                          <label key={option.catalogueAddOnReference} className="settlement-addon-option">
                            <input type="radio" name="catalogue-add-on" value={option.catalogueAddOnReference} checked={addOn === option.catalogueAddOnReference} onChange={() => setAddOn(option.catalogueAddOnReference)} />
                            <span>{option.name}</span><strong>{money(option.price)}</strong>
                          </label>
                        ))}
                      </fieldset>
                    )}
                <label className="settlement-addon-consent"><input type="checkbox" checked={consent} onChange={event => setConsent(event.target.checked)} /><span>Khách đã đồng ý (customer agreed)</span></label>
                <button className="button primary" type="submit" disabled={command.pending || !addOn || !consent}>Add selected add-on</button>
              </form>
              <form className="ota-form settlement-form" aria-label="Variable charge" onSubmit={submitCharge}>
                <h3>Variable charge</h3>
                <label>Description<input value={charge.description} maxLength={200} onChange={event => setCharge(v => ({ ...v, description: event.target.value }))} /></label>
                <label>Amount (VND)<input inputMode="numeric" value={charge.amount} onChange={event => setCharge(v => ({ ...v, amount: event.target.value }))} /></label>
                <label>Reason<input value={charge.reason} maxLength={255} onChange={event => setCharge(v => ({ ...v, reason: event.target.value }))} /></label>
                <button className="button outline" type="submit" disabled={command.pending || !charge.description.trim() || !isWholeVnd(charge.amount) || !charge.reason.trim() || !consent}>Add charge</button>
              </form>
            </article>
          )}

          <article className="ota-panel">
            <FolioCorrectionLookup key={ticketCode} api={api} ticketCode={ticketCode} />
          </article>
        </section>
      ) : null}
      {ticketCode && !folio.loading && !bill && !folio.error ? <div className="empty-state"><p>No folio was found for this ticket.</p></div> : null}

      <ReasonDialog open={dialogOpen} title={dialogTitle} confirmLabel="Confirm" pending={command.pending}
        onCancel={() => { setFailing(null); setDangerous(null); setVoiding(null); }} onConfirm={confirmDangerous}>
        <p>{dangerous?.kind === 'handover' ? 'The customer may take the product now. The balance stays owed and no payment is recorded; the exception is audited with your reason.' : voiding ? 'The original row is kept as an audited reversal and the balance is recomputed by the server. A collection already counted into a closed cash day is refused.' : failing ?'The part becomes FAILED so another method can take the balance. Signed VNPay money that still arrives is kept and refunded to the payer by bank transfer.' : 'This is audited and cannot be undone from this screen.'}</p>
      </ReasonDialog>
    </OpsPage>
  );
}
