import React, { useState } from 'react';
import { Link, useSearchParams } from 'react-router';
import { useHttp } from '../../shared/api/useHttp';
import { ConfirmDialog, Field, SuccessNotice, useCommand } from '../../shared/ui/components';
import { createSettlementApi } from '../api/settlementApi';
import { OpsPage } from '../OpsPage';
import { useRole } from '../../shared/auth/useRole';
import { BuyerProof, ProblemNotice, ReasonDialog, VoucherNotRestoredNotice, money, splitList } from '../ui';

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

/**
 * One change per line: "TICKET-CODE: from-session>target-session[, from-session>target-session]". Each pair names the
 * ticket's current session being replaced and its replacement (FR-044, T141): the server needs both, one from per target.
 */
export function parseChangeLines(text) {
  return text.split('\n').map(line => line.trim()).filter(Boolean).map(line => {
    const [ticketCode, sessions = ''] = line.split(':');
    const pairs = splitList(sessions.replace(/\s*>\s*/g, '>')).map(pair => {
      const [from, target = ''] = pair.split('>');
      return { from, target };
    });
    return {
      ticketCode: ticketCode.trim(),
      fromSessionReferences: pairs.map(pair => pair.from),
      targetSessionReferences: pairs.map(pair => pair.target),
    };
  });
}

const validChanges = changes => changes.length > 0 && changes.every(change => change.ticketCode
  && change.targetSessionReferences.length >= 1 && change.targetSessionReferences.length <= 2
  && change.fromSessionReferences.length === change.targetSessionReferences.length
  && [...change.fromSessionReferences, ...change.targetSessionReferences].every(ref => UUID.test(ref)));

/**
 * Staff or Manager cancels or changes tickets for the buyer (FR-041–FR-045, FR-055): one ticket on the ticket route, several
 * tickets of one group all or none on the group route. The buyer proof comes from the code sent to the booking's contact.
 * Prices, outcomes and queue status are the server's answer; a price increase is shown first and repeated with confirmation.
 */
export default function TicketActionsPage() {
  const api = createSettlementApi(useHttp());
  const [params] = useSearchParams();
  const tab = params.get('tab') === 'change' ? 'change' : 'cancel';
  const [groupCode, setGroupCode] = useState('');
  const [ticketText, setTicketText] = useState('');
  const [changeText, setChangeText] = useState('');
  const [changeType, setChangeType] = useState('ADVANCE');
  const [reason, setReason] = useState('');
  const [proof, setProof] = useState(null);
  const [confirmCancel, setConfirmCancel] = useState(false);
  const role = useRole();
  const isManager = role === 'MANAGER' || role === 'ADMIN';
  const [allowanceTicket, setAllowanceTicket] = useState('');
  const [granting, setGranting] = useState(false);
  const [result, setResult] = useState(null);
  const [message, setMessage] = useState('');
  const command = useCommand();

  const tickets = splitList(ticketText);
  const changes = parseChangeLines(changeText);
  const proofBody = proof ? { buyerVerificationReference: proof } : {};
  const needsGroup = tab === 'cancel' ? tickets.length > 1 : changes.length > 1;
  const groupReady = Boolean(groupCode.trim());

  const cancel = async () => {
    setMessage(''); setResult(null);
    const body = { reason: reason.trim(), ...proofBody };
    const out = await command.run(key => (tickets.length === 1
      ? api.cancelTicket(tickets[0], body, key)
      : api.cancelGroupTickets(groupCode.trim(), { ...body, ticketCodes: tickets }, key)));
    if (out.ok) {
      setConfirmCancel(false); setProof(null);
      setResult({ kind: 'cancel', tickets: out.result.tickets || [out.result] });
      setMessage('Cancellation recorded.');
    }
  };

  const change = async confirmIncrease => {
    setMessage(''); setResult(null);
    const base = { type: changeType, reason: reason.trim(), ...proofBody, ...(confirmIncrease ? { confirmIncrease: true } : {}) };
    const out = await command.run(key => (changes.length === 1
      ? api.changeTicket(changes[0].ticketCode, { ...base, fromSessionReferences: changes[0].fromSessionReferences, targetSessionReferences: changes[0].targetSessionReferences }, key)
      : api.changeGroupTickets(groupCode.trim(), { ...base, changes }, key)));
    if (!out.ok) return;
    const data = out.result;
    setResult({ kind: 'change', data, grouped: changes.length > 1 });
    if (data.applied) { setProof(null); setMessage('Change applied.'); }
    else if (data.changeRequestReference) { setProof(null); setMessage('Queued for a Manager decision.'); }
  };

  const grant = async grantReason => {
    setMessage(''); setResult(null);
    const out = await command.run(key => api.grantChangeAllowance(allowanceTicket.trim(), { reason: grantReason }, key));
    if (out.ok) {
      setGranting(false); setAllowanceTicket('');
      setMessage(`One extra change granted for ${out.result.ticketCode} (${out.result.allowanceReference}). The change itself is still to be made.`);
    }
  };

  const awaiting =result?.kind === 'change' && !result.data.applied && result.data.confirmIncreaseRequired;
  const increase = result?.grouped ? result.data.totalPriceIncrease : result?.data?.priceIncreaseAmount;
  const cancelReady = tickets.length > 0 && reason.trim() && (tickets.length === 1 || groupReady);
  const changeReady = validChanges(changes) && reason.trim() && (changes.length === 1 || groupReady);

  return (
    <OpsPage eyebrow="TICKET CANCELLATION AND CHANGE" title={tab === 'change' ? 'Change tickets' : 'Cancel tickets'}
      intro="Act for the buyer at the counter. The buyer is verified with a code sent to the booking's contact; the reason is required and audited.">
      <nav className="ota-nav-tabs" aria-label="Ticket actions">
        <Link className={`ota-nav-tab ${tab === 'cancel' ? 'active' : ''}`} to="/settlement/ticket-actions?tab=cancel">Cancel</Link>
        <Link className={`ota-nav-tab ${tab === 'change' ? 'active' : ''}`} to="/settlement/ticket-actions?tab=change">Change</Link>
      </nav>
      <SuccessNotice>{message}</SuccessNotice>
      <ProblemNotice error={command.error} onRetry={command.retry} />
      <section className="ota-panel">
        <div className="ota-form">
          <Field label="Booking group code" hint={needsGroup ? 'Required for several tickets and for the buyer code.' : 'Needed to send the buyer code.'}>
            <input value={groupCode} onChange={event => setGroupCode(event.target.value)} />
          </Field>
          {tab === 'cancel' ? (
            <Field label="Ticket codes" hint="One ticket, or several of the same group (all or none).">
              <textarea value={ticketText} onChange={event => setTicketText(event.target.value)} placeholder="TK-1, TK-2" />
            </Field>
          ) : (<>
            <Field label="Change type">
              <select value={changeType} onChange={event => setChangeType(event.target.value)}>
                <option value="ADVANCE">Advance (before the session)</option>
                <option value="LATE_ARRIVAL">Late arrival</option>
              </select>
            </Field>
            <Field label="Tickets and target sessions" hint="One line per ticket: ticket code, colon, then one or two pairs 'current session>new session' (session references, no spaces inside a pair). The current session must be one of the ticket's sessions.">
              <textarea value={changeText} onChange={event => setChangeText(event.target.value)} placeholder="TK-1: 7c1f0c2e-0000-4000-8000-000000000001>7c1f0c2e-0000-4000-8000-000000000002" />
            </Field>
          </>)}
          <Field label="Reason (required)"><textarea value={reason} maxLength={1000} onChange={event => setReason(event.target.value)} /></Field>
          <BuyerProof groupCode={groupCode.trim()} onReference={setProof} />
          {tab === 'cancel'
            ? <button className="button danger" type="button" disabled={command.pending || !cancelReady || !proof} onClick={() => setConfirmCancel(true)}>Cancel selected tickets</button>
            : <button className="button primary" type="button" disabled={command.pending || !changeReady || !proof} onClick={() => change(false)}>Change tickets</button>}
        </div>
      </section>

      {tab === 'change' && isManager && (
        <section className="ota-panel" aria-label="Extra change allowance">
          <h2>Grant one extra change</h2>
          <p className="muted small">Gives a ticket one more counted change (FR-054). It never performs the change; the buyer or Staff on the buyer's behalf still changes the ticket above.</p>
          <div className="ota-inline-form">
            <label>Ticket code<input value={allowanceTicket} onChange={event => setAllowanceTicket(event.target.value)} placeholder="TK-..." /></label>
            <button className="button outline" type="button" disabled={command.pending || !allowanceTicket.trim()} onClick={() => setGranting(true)}>Grant extra change</button>
          </div>
        </section>
      )}
      {awaiting && (
        <section className="ota-panel" role="status" aria-label="Price increase">
          <h2>Price increase to confirm</h2>
          <p>This change raises the price by <strong>{money(increase)}</strong>. Nothing has changed yet. Ask the buyer to confirm the new schedule and amount.</p>
          <button className="button primary" type="button" disabled={command.pending} onClick={() => change(true)}>Buyer confirms the increase</button>
        </section>
      )}
      {result?.kind === 'change' && result.data.changeRequestReference && (
        <section className="ota-panel" role="status"><p>Request <strong>{result.data.changeRequestReference}</strong> waits for a Manager in the <Link to="/settlement/change-requests">change request queue</Link>.</p></section>
      )}
      {result?.kind === 'change' && result.data.applied && (
        <section className="ota-panel" aria-label="Change result">
          <h2>Change applied</h2>
          <ul>
            {(result.grouped ? result.data.tickets : [result.data]).map(item => (
              <li key={item.ticketCode}>{item.ticketCode}: balance due {money(item.balanceDue)}{item.priceIncreaseAmount && item.priceIncreaseAmount !== '0' ? `, increase ${money(item.priceIncreaseAmount)}` : ''}</li>
            ))}
          </ul>
        </section>
      )}
      {result?.kind === 'cancel' && (
        <section className="ota-panel" aria-label="Cancellation result">
          <h2>Cancelled</h2>
          <ul>
            {result.tickets.map(item => (
              <li key={item.ticketCode}>
                {item.ticketCode}: {item.benefitOutcome === 'VALID_CANCELLATION' ? 'valid cancellation (24 hours or more before the first session)' : 'late cancellation (counts as a no-show for benefits)'}; points returned {item.pointsReturned}{item.voucherRestored ? '; voucher restored' : ''}
                <VoucherNotRestoredNotice voucherRestoration={item.voucherRestoration} />
              </li>
            ))}
          </ul>
        </section>
      )}

      <ReasonDialog open={granting} title="Grant one extra change" confirmLabel="Grant" pending={command.pending} onCancel={() => setGranting(false)} onConfirm={grant}>
        <p>Ticket {allowanceTicket.trim()} gets one more counted change. This is audited.</p>
      </ReasonDialog>
      <ConfirmDialog open={confirmCancel} title="Cancel these tickets" danger confirmLabel="Cancel tickets" pending={command.pending} onCancel={() => setConfirmCancel(false)} onConfirm={cancel}>
        <p>{tickets.length} ticket(s) will be cancelled{tickets.length > 1 ? ' all or none' : ''}. This cannot be undone. Any refund goes to the bank account the payer provides.</p>
      </ConfirmDialog>
    </OpsPage>
  );
}
