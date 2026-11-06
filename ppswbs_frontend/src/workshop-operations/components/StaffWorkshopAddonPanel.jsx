import React, { useState } from 'react';
import { ErrorNotice, SuccessNotice, useCommand } from '../../shared/ui/components';
import { createSettlementApi } from '../../workshop-settlement/api/settlementApi';

/**
 * Manager-only post-FINALIZED folio correction for direct Mland bookings.
 * Overcharge refund only; executed via VNPay partial refund & QueryDR.
 * Folio totals, line items, Loyalty earn, and booking status remain unchanged.
 */
export default function StaffWorkshopAddonPanel({ http }) {
  const [bookingCode, setBookingCode] = useState('');
  const [amountVnd, setAmountVnd] = useState('');
  const [reason, setReason] = useState('');
  const [correction, setCorrection] = useState(null);
  const createCommand = useCommand();
  const settlementApi = createSettlementApi(http);

  async function submit(event) {
    event.preventDefault();
    setCorrection(null);
    const parsedAmount = Number(amountVnd);
    if (!bookingCode.trim() || !parsedAmount || parsedAmount <= 0 || !reason.trim()) return;
    const outcome = await createCommand.run(key => settlementApi.createFolioCorrection(
      bookingCode.trim(),
      { amountVnd: parsedAmount, reason: reason.trim() },
      key,
    ));
    if (outcome.ok) setCorrection(outcome.result);
  }

  const isValid = bookingCode.trim() && Number(amountVnd) > 0 && reason.trim();

  return <article className="ota-panel staff-addon-panel folio-correction-panel">
    <div className="ota-panel-head">
      <div>
        <p className="ota-eyebrow">MANAGER / FOLIO CORRECTION</p>
        <h2>Manager folio correction</h2>
        <p className="muted small">Overcharge refund for finalized direct bookings. The original finalized bill, Loyalty points, tickets, and capacity remain immutable.</p>
      </div>
    </div>
    <SuccessNotice>{correction ? `Folio correction ${correction.correctionReference} created (${correction.lifecycleStatus || 'PENDING_PROVIDER'}). Refund ${correction.amountVnd} VND allocated oldest payment first.` : ''}</SuccessNotice>
    <ErrorNotice error={createCommand.error} onRetry={createCommand.retry} />
    {!reason.trim() && bookingCode.trim() && Number(amountVnd) > 0 && (
      <p role="status" className="muted small">A manager correction reason is required before submitting.</p>
    )}
    {createCommand.pending && <p role="status" aria-live="polite" className="muted small">Submitting folio correction...</p>}
    <form className="ota-form" onSubmit={submit}>
      <label>Booking code<input value={bookingCode} onChange={event => setBookingCode(event.target.value)} placeholder="BK-..." autoComplete="off" /></label>
      <label>Overcharge refund amount (VND)<input type="number" min="1" value={amountVnd} onChange={event => setAmountVnd(event.target.value)} placeholder="50000" autoComplete="off" /></label>
      <label>Correction reason<input value={reason} onChange={event => setReason(event.target.value)} placeholder="Staff overcharge at counter..." autoComplete="off" maxLength={255} /></label>
      <button className="button primary" type="submit" disabled={createCommand.pending || !!correction || !isValid}>{createCommand.pending ? 'Submitting...' : 'Submit folio correction'}</button>
    </form>
    {correction && <div className="staff-addon-result folio-correction-result">
      <p><strong>Correction: {correction.correctionReference}</strong></p>
      <p>{correction.amountVnd} {correction.currencyCode || 'VND'} · Case {correction.caseReference} ({correction.lifecycleStatus})</p>
      <p className="muted small">Reason: {correction.reason}</p>
      <p className="muted small">Awaiting verified QueryDR reconciliation; undercharge is never collected.</p>
    </div>}
    <p className="muted small staff-addon-policy">Manager-only overcharge correction on finalized folios. Never alters original invoice lines, Loyalty earn, tickets, or capacity.</p>
  </article>;
}

export { StaffWorkshopAddonPanel as FolioCorrectionPanel };
