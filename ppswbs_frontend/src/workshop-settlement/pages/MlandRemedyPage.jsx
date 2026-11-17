import React, { useState } from 'react';
import { useParams } from 'react-router';
import { useAuth } from '../../shared/auth/AuthProvider';
import { useHttp } from '../../shared/api/useHttp';
import { ConfirmDialog, Loading, SuccessNotice, useCommand, useResource } from '../../shared/ui/components';
import { createSettlementApi } from '../api/settlementApi';
import { CustomerPage } from '../OpsPage';
import { BuyerProof, ProblemNotice, VoucherNotRestoredNotice, when } from '../ui';

const CODE_TEXT = {
  REPLACEMENT_SESSION_UNAVAILABLE: 'That session has no seat left or is no longer sold at that location. Choose another session or ask for a refund.',
  REPLACEMENT_SESSION_NOT_OFFERED: 'That session is not one of the offered sessions.',
  ANSWER_DEADLINE_EXPIRED: 'The time to answer has passed. The ticket is refunded.',
};

/**
 * Buyer answer to an Mland cancellation (FR-043): move to one offered equivalent session or take a refund. A Member is
 * proven by the sign-in; a Guest verifies with a code sent to the booking's contact. Only the buyer can answer, and the
 * offer carries no money; a refund goes to the bank account the buyer gives later through the emailed link.
 */
export default function MlandRemedyPage() {
  const { ticketCode } = useParams();
  const { status } = useAuth();
  const member = status === 'signed-in';
  const opts = { auth: member };
  const api = createSettlementApi(useHttp());
  const [choice, setChoice] = useState('');
  const [proof, setProof] = useState(null);
  const [confirming, setConfirming] = useState(false);
  const [message, setMessage] = useState('');
  const command = useCommand();
  const offer = useResource(() => (status === 'loading' ? Promise.resolve(null) : api.getMlandRemedy(ticketCode, opts)), [ticketCode, status]);

  if (status === 'loading' || offer.loading) return <CustomerPage title="Your workshop ticket"><Loading /></CustomerPage>;
  const data = offer.data;
  if (offer.error || !data) {
    return (
      <CustomerPage title="Your workshop ticket">
        {offer.error?.status === 404 || offer.error?.status === 403
          ? <div className="notice error" role="alert"><p>We could not find an offer for this ticket.</p></div>
          : <ProblemNotice error={offer.error} onRetry={offer.reload} onReload={offer.reload} />}
      </CustomerPage>
    );
  }

  const pending = data.outcome === 'PENDING_ANSWER';
  const refund = choice === 'REFUND';
  const chosenLocation = refund ? null : (data.offeredSessions || []).find(session => session.sessionReference === choice)?.locationName;
  const canSend = pending && (refund || choice) && (member || proof);

  const send = async () => {
    const body = refund ? { answer: 'REFUND' } : { answer: 'ACCEPT_REPLACEMENT', replacementSessionReference: choice };
    if (proof) body.buyerVerificationReference = proof;
    const out = await command.run(key => api.answerMlandRemedy(ticketCode, body, key, opts));
    if (out.ok) { setConfirming(false); setProof(null); setMessage(refund ? 'Your refund request was recorded.' : 'Your new session is booked.'); offer.reload(); }
    else setConfirming(false);
  };

  const error = command.error;
  const known = error && CODE_TEXT[error.code];
  return (
    <CustomerPage title="Your session was cancelled" intro="We had to cancel a session of your ticket. Choose a replacement session or a refund.">
      <SuccessNotice>{message}</SuccessNotice>
      {known ? <div className="notice error" role="alert"><p>{known}</p></div> : <ProblemNotice error={error} onRetry={command.retry} onReload={offer.reload} />}
      <section className="ota-panel">
        <p>Ticket {data.ticketCode}</p>
        {pending ? <p>Answer before <strong>{when(data.answerDeadlineAt)}</strong>. Without an answer the ticket is refunded.</p>
          : <p role="status">{data.outcome === 'MOVED' ? 'Your ticket was moved to the replacement session.' : 'Your ticket is being refunded.'}</p>}
        <VoucherNotRestoredNotice voucherRestoration={data.voucherRestoration} />
        {pending && (
          <fieldset className="settlement-addon-options">
            <legend>Choose one</legend>
            {(data.offeredSessions || []).map(session => (
              <label key={session.sessionReference} className="settlement-addon-option">
                <input type="radio" name="remedy" value={session.sessionReference} disabled={!session.selectable}
                  checked={choice === session.sessionReference} onChange={() => setChoice(session.sessionReference)} />
                <span>{session.locationName ? `${session.locationName} – ` : ''}{when(session.startAt)}{session.endAt ? ` – ${when(session.endAt)}` : ''}{session.selectable ? '' : ' (not available)'}</span>
              </label>
            ))}
            <label className="settlement-addon-option">
              <input type="radio" name="remedy" value="REFUND" checked={refund} onChange={() => setChoice('REFUND')} />
              <span>Refund this ticket (100%) to my bank account</span>
            </label>
          </fieldset>
        )}
        {pending && !member && <BuyerProof groupCode={data.groupCode} onReference={setProof} />}
        {pending && <button className="button primary" type="button" disabled={command.pending || !canSend} onClick={() => setConfirming(true)}>Send my answer</button>}
      </section>
      <ConfirmDialog open={confirming} title="Confirm your answer" confirmLabel="Confirm" pending={command.pending} onCancel={() => setConfirming(false)} onConfirm={send}>
        <p>{refund ? 'The ticket will be refunded to your bank account.' : `Your ticket moves to the chosen session${chosenLocation ? ` at ${chosenLocation}` : ''}.`} Your answer cannot be changed afterwards.</p>
      </ConfirmDialog>
    </CustomerPage>
  );
}
