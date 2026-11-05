import React, { useState } from 'react';
import { ErrorNotice, SuccessNotice, useCommand } from '../../shared/ui/components';
import { createSettlementApi } from '../../workshop-settlement/api/settlementApi';

/**
 * Staff-only counter action for a direct Mland booking.
 * The server derives the price from the active package/location catalogue;
 * the browser never submits an amount or customer PII.
 */
export default function StaffWorkshopAddonPanel({ http }) {
  const [bookingCode, setBookingCode] = useState('');
  const [addOnReference, setAddOnReference] = useState('');
  const [consentReference, setConsentReference] = useState('');
  const [bill, setBill] = useState(null);
  const [payment, setPayment] = useState(null);
  const createCommand = useCommand();
  const paymentCommand = useCommand();
  const settlementApi = createSettlementApi(http);

  async function submit(event) {
    event.preventDefault();
    setBill(null);
    setPayment(null);
    if (!bookingCode.trim() || !addOnReference.trim() || !consentReference.trim()) return;
    const outcome = await createCommand.run(key => settlementApi.createDirectAddonBill(
      bookingCode.trim(),
      { catalogueAddOnReference: addOnReference.trim(), customerConsentReference: consentReference.trim() },
      key,
    ));
    if (outcome.ok) setBill(outcome.result);
  }

  async function startPayment() {
    if (!bill || !bookingCode.trim()) return;
    const outcome = await paymentCommand.run(key => settlementApi.startDirectAddonPayment(bookingCode.trim(), bill.billReference, key));
    if (outcome.ok) setPayment(outcome.result);
  }

  return <article className="ota-panel staff-addon-panel">
    <div className="ota-panel-head">
      <div>
        <p className="ota-eyebrow">STAFF / DIRECT MLAND ADD-ON</p>
        <h2>Create separate add-on bill</h2>
        <p className="muted small">Use at the workshop counter after customer consent. The original finalized bill stays immutable; the backend resolves the catalogue price and enforces location scope.</p>
      </div>
    </div>
    <SuccessNotice>{bill ? `Separate add-on bill ${bill.billReference} created (${bill.lifecycleStatus || 'PENDING_PAYMENT'}).` : ''}</SuccessNotice>
    <ErrorNotice error={createCommand.error || paymentCommand.error} onRetry={createCommand.error ? createCommand.retry : paymentCommand.retry} />
    {!consentReference.trim() && bookingCode.trim() && addOnReference.trim() && (
      <p role="status" className="muted small">A customer consent reference is required before creating the bill.</p>
    )}
    {createCommand.pending && <p role="status" aria-live="polite" className="muted small">Creating separate add-on bill...</p>}
    {paymentCommand.pending && <p role="status" aria-live="polite" className="muted small">Starting payment checkout...</p>}
    <form className="ota-form" onSubmit={submit}>
      <label>Booking code<input value={bookingCode} onChange={event => setBookingCode(event.target.value)} placeholder="BK-..." autoComplete="off" /></label>
      <label>Catalogue add-on reference<input value={addOnReference} onChange={event => setAddOnReference(event.target.value)} placeholder="ADD-..." autoComplete="off" /></label>
      <label>Customer consent reference<input value={consentReference} onChange={event => setConsentReference(event.target.value)} placeholder="CONSENT-..." autoComplete="off" /></label>
      <button className="button primary" type="submit" disabled={createCommand.pending || !!bill || !bookingCode.trim() || !addOnReference.trim() || !consentReference.trim()}>{createCommand.pending ? 'Creating...' : 'Create separate add-on bill'}</button>
    </form>
    {bill && <div className="staff-addon-result">
      <p><strong>{bill.description || bill.addOnReference}</strong></p>
      <p>{bill.amountVnd} {bill.currency} · {bill.lifecycleStatus}</p>
      {!payment && <button type="button" className="button primary" onClick={startPayment} disabled={paymentCommand.pending}>Start add-on payment</button>}
      {payment && <div role="status">
        <p>Payment attempt {payment.attemptReference} is {payment.status || 'PENDING'}.</p>
        <p className="muted small">Awaiting verified IPN/QueryDR evidence; do not mark paid from browser return alone.</p>
        {payment.expiresAt && <p className="muted small">Checkout expires at {payment.expiresAt}.</p>}
        {payment.paymentUrl && (
          <a
            className="button primary"
            style={{ marginTop: '10px', display: 'inline-flex' }}
            href={payment.paymentUrl}
            target="_blank"
            rel="noreferrer"
          >
            Open payment checkout
          </a>
        )}
      </div>}
    </div>}
    <p className="muted small staff-addon-policy">No amount, currency, email or phone is accepted from this screen. Replay uses the same idempotency command; changed commands are rejected by the backend.</p>
  </article>;
}
