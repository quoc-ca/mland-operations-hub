import React, { useState } from 'react';
import { ErrorNotice, SuccessNotice, useCommand } from '../../shared/ui/components';
import { createSettlementApi } from '../../workshop-settlement/api/settlementApi';
import FolioCorrectionLookup from '../../workshop-settlement/FolioCorrectionLookup';

/**
 * Manager-only post-FINALIZED correction of one ticket folio (T106: POST /workshop-tickets/{ticketCode}/folio/corrections).
 * Overcharge only; the overcharge becomes a bank refund request (never a VNPay refund). The finalized folio, Loyalty
 * earn and the ticket stay unchanged, and an undercharge is absorbed by Mland and never collected.
 */
export default function StaffWorkshopAddonPanel({ http }) {
  const [ticketCode, setTicketCode] = useState('');
  const [amountVnd, setAmountVnd] = useState('');
  const [reason, setReason] = useState('');
  const [correction, setCorrection] = useState(null);
  const [viewing, setViewing] = useState(false);
  const createCommand = useCommand();
  const settlementApi = createSettlementApi(http);

  const isValid = ticketCode.trim() && /^[1-9][0-9]*$/.test(amountVnd.trim()) && reason.trim();

  async function submit(event) {
    event.preventDefault();
    setCorrection(null);
    setViewing(false);
    if (!isValid) return;
    const outcome = await createCommand.run(key => settlementApi.createFolioCorrection(
      ticketCode.trim(),
      { overchargeAmount: amountVnd.trim(), reason: reason.trim() },
      key,
    ));
    if (outcome.ok) setCorrection(outcome.result);
  }

  return <article className="ota-panel staff-addon-panel folio-correction-panel">
    <div className="ota-panel-head">
      <div>
        <p className="ota-eyebrow">MANAGER / FOLIO CORRECTION</p>
        <h2>Manager folio correction</h2>
        <p className="muted small">Overcharge refund for a finalized ticket folio. The finalized folio, Loyalty points, ticket and capacity remain immutable.</p>
      </div>
    </div>
    <SuccessNotice>{correction ? `Folio correction ${correction.correctionReference} created (${correction.status}). A bank refund request was opened${correction.refundId ? ` (${correction.refundId})` : ''}.` : ''}</SuccessNotice>
    <ErrorNotice error={createCommand.error} onRetry={createCommand.retry} />
    {!reason.trim() && ticketCode.trim() && Number(amountVnd) > 0 && (
      <p role="status" className="muted small">A manager correction reason is required before submitting.</p>
    )}
    {createCommand.pending && <p role="status" aria-live="polite" className="muted small">Submitting folio correction...</p>}
    <form className="ota-form" onSubmit={submit}>
      <label>Folio ticket code<input value={ticketCode} onChange={event => setTicketCode(event.target.value)} placeholder="TK-..." autoComplete="off" /></label>
      <label>Overcharge refund amount (VND)<input inputMode="numeric" value={amountVnd} onChange={event => setAmountVnd(event.target.value)} placeholder="50000" autoComplete="off" /></label>
      <label>Correction reason<input value={reason} onChange={event => setReason(event.target.value)} placeholder="Staff overcharge at counter..." autoComplete="off" maxLength={1000} /></label>
      <button className="button primary" type="submit" disabled={createCommand.pending || !!correction || !isValid}>{createCommand.pending ? 'Submitting...' : 'Submit folio correction'}</button>
    </form>
    {correction && <div className="staff-addon-result folio-correction-result">
      <p><strong>Correction: {correction.correctionReference}</strong></p>
      <p>Ticket {correction.ticketCode} · {correction.overchargeAmount} VND · {correction.status}{correction.refundStatus ? ` · refund ${correction.refundStatus}` : ''}</p>
      <p className="muted small">The refund is paid by bank transfer after a Manager approves it in the refund queue; an undercharge is never collected.</p>
      {!viewing && <button type="button" className="button outline" onClick={() => setViewing(true)}>View correction</button>}
      {viewing && <FolioCorrectionLookup api={settlementApi} ticketCode={correction.ticketCode || ticketCode.trim()}
        initialReference={correction.correctionReference} />}
    </div>}
    <p className="muted small staff-addon-policy">Manager-only overcharge correction on finalized folios. Never alters original invoice lines, Loyalty earn, tickets, or capacity.</p>
  </article>;
}

export { StaffWorkshopAddonPanel as FolioCorrectionPanel };
