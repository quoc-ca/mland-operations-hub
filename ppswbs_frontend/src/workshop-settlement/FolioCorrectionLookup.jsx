import React, { useEffect, useState } from 'react';
import { useCommand } from '../shared/ui/components';
import { ProblemNotice, money } from './ui';

function CorrectionDetails({ correction }) {
  return (
    <dl className="ops-facts" aria-label="Folio correction">
      <div><dt>Correction</dt><dd>{correction.correctionReference}</dd></div>
      <div><dt>Ticket</dt><dd>{correction.ticketCode}</dd></div>
      <div><dt>Overcharge refunded</dt><dd>{money(correction.overchargeAmount)}</dd></div>
      <div><dt>Status</dt><dd><span className="ops-status">{correction.status}</span></dd></div>
      {correction.refundId && <div><dt>Bank refund request</dt><dd>{correction.refundId}</dd></div>}
      {correction.refundStatus && <div><dt>Refund status</dt><dd><span className="ops-status">{correction.refundStatus}</span></dd></div>}
    </dl>
  );
}

/**
 * 003.3 T127 (FR-050, FR-048): read-only view of one posted post-FINALIZED correction of a ticket folio, through
 * GET /workshop-tickets/{ticketCode}/folio/corrections/{ref} with the bearer. The backend decides who may read it (the
 * Manager, the ticket's Member owner, the assigned Staff); anyone else, like a wrong reference, gets the same "not found".
 * A Guest is never sent here: a Guest reads the folio through folio-lookups with a contact proof in the body.
 * Nothing on this view changes the correction.
 */
export default function FolioCorrectionLookup({ api, ticketCode, initialReference = '' }) {
  const [reference, setReference] = useState(initialReference);
  const [correction, setCorrection] = useState(null);
  const command = useCommand();

  const read = async ref => {
    setCorrection(null);
    const out = await command.run(() => api.getFolioCorrection(ticketCode, ref));
    if (out.ok) setCorrection(out.result);
  };

  useEffect(() => {
    if (initialReference && ticketCode) read(initialReference);
    // Read once for the reference handed over; later reads come from the form.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [initialReference, ticketCode]);

  const submit = event => {
    event.preventDefault();
    if (reference.trim()) read(reference.trim());
  };

  const notFound = command.error && (command.error.status === 404 || command.error.status === 403);
  return (
    <section className="folio-correction-view" aria-label="Folio correction lookup">
      <h3>Folio correction</h3>
      <form className="ota-inline-form" onSubmit={submit}>
        <label>Correction reference
          <input value={reference} onChange={event => setReference(event.target.value)} autoComplete="off" maxLength={128} />
        </label>
        <button className="button outline" type="submit" disabled={command.pending || !ticketCode || !reference.trim()}>View correction</button>
      </form>
      {command.pending && <p role="status" className="muted small">Loading correction…</p>}
      {notFound
        ? <div className="notice error" role="alert"><p>No correction with this reference is visible for this ticket.</p></div>
        : <ProblemNotice error={command.error} onRetry={command.retry} />}
      {correction && <CorrectionDetails correction={correction} />}
    </section>
  );
}
