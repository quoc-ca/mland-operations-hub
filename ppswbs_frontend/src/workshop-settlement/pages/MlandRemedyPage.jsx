import React, { useState } from 'react';
import { useParams } from 'react-router';
import { useAuth } from '../../shared/auth/AuthProvider';
import { useRole } from '../../shared/auth/useRole';
import { useHttp } from '../../shared/api/useHttp';
import { ConfirmDialog, Loading, SuccessNotice, useCommand, useResource } from '../../shared/ui/components';
import { createSettlementApi } from '../api/settlementApi';
import { CustomerPage } from '../OpsPage';
import { useFormat, useT } from '../../shared/i18n/I18nProvider';
import { sharedMessages } from '../../shared/ui/messages';
import { messages } from '../messages';
import { BuyerProof, ProblemNotice, VoucherNotRestoredNotice } from '../ui';

// The text of each refusal is mr.problem.<CODE>.
const KNOWN_CODES = ['REPLACEMENT_SESSION_UNAVAILABLE', 'REPLACEMENT_SESSION_NOT_OFFERED', 'ANSWER_DEADLINE_EXPIRED'];

/**
 * Buyer answer to an Mland cancellation (FR-043): move to one offered equivalent session or take a refund. A Member is
 * proven by the sign-in; a Guest verifies with a code sent to the booking's contact. Only the buyer can answer, and the
 * offer carries no money; a refund goes to the bank account the buyer gives later through the emailed link.
 */
export default function MlandRemedyPage() {
  const t = useT(messages);
  const tShared = useT(sharedMessages);
  const fmt = useFormat();
  const { ticketCode } = useParams();
  const { status } = useAuth();
  const role = useRole();
  const authenticated = status === 'signed-in';
  const member = authenticated && role === 'MEMBER';
  const operator = authenticated && (role === 'STAFF' || role === 'MANAGER');
  const opts = { auth: authenticated };
  const api = createSettlementApi(useHttp());
  const [choice, setChoice] = useState('');
  const [proof, setProof] = useState(null);
  const [confirming, setConfirming] = useState(false);
  const [message, setMessage] = useState('');
  const command = useCommand();
  const offer = useResource(() => (status === 'loading' ? Promise.resolve(null) : api.getMlandRemedy(ticketCode, opts)), [ticketCode, status]);

  if (status === 'loading' || offer.loading) return <CustomerPage title={t('mr.titleTicket')}><Loading /></CustomerPage>;
  const data = offer.data;
  if (offer.error || !data) {
    return (
      <CustomerPage title={t('mr.titleTicket')}>
        {offer.error?.status === 404 || offer.error?.status === 403
          ? <div className="notice error" role="alert"><p>{t('mr.notFound')}</p></div>
          : <ProblemNotice error={offer.error} onRetry={offer.reload} onReload={offer.reload} />}
      </CustomerPage>
    );
  }

  const pending = data.outcome === 'PENDING_ANSWER';
  const refund = choice === 'REFUND';
  const chosenLocation = refund ? null : (data.offeredSessions || []).find(session => session.sessionReference === choice)?.locationName;
  const canSend = pending && (refund || choice) && (member || ((operator || !authenticated) && proof));

  const send = async () => {
    const body = refund ? { answer: 'REFUND' } : { answer: 'ACCEPT_REPLACEMENT', replacementSessionReference: choice };
    if (proof) body.buyerVerificationReference = proof;
    const out = await command.run(key => api.answerMlandRemedy(ticketCode, body, key, opts));
    if (out.ok) { setConfirming(false); setProof(null); setMessage(t(refund ? 'mr.msg.refund' : 'mr.msg.moved')); offer.reload(); }
    else setConfirming(false);
  };

  const error = command.error;
  const known = error && KNOWN_CODES.includes(error.code) ? t(`mr.problem.${error.code}`) : '';
  return (
    <CustomerPage title={t('mr.titleCancelled')} intro={t('mr.intro')}>
      <SuccessNotice>{message}</SuccessNotice>
      {known ? <div className="notice error" role="alert"><p>{known}</p></div> : <ProblemNotice error={error} onRetry={command.retry} onReload={offer.reload} />}
      <section className="wsp-panel">
        <p>{t('mr.ticket', { code: data.ticketCode })}</p>
        {pending ? <p>{t('mr.answerBefore')}<strong>{fmt.dateTime(data.answerDeadlineAt)}</strong>{t('mr.answerAfter')}</p>
          : <p role="status">{t(data.outcome === 'MOVED' ? 'mr.outcomeMoved' : 'mr.outcomeRefund')}</p>}
        <VoucherNotRestoredNotice voucherRestoration={data.voucherRestoration} />
        {pending && (
          <fieldset className="settlement-addon-options">
            <legend>{t('mr.chooseOne')}</legend>
            {(data.offeredSessions || []).map(session => (
              <label key={session.sessionReference} className="settlement-addon-option">
                <input type="radio" name="remedy" value={session.sessionReference} disabled={!session.selectable}
                  checked={choice === session.sessionReference} onChange={() => setChoice(session.sessionReference)} />
                <span>{session.locationName ? `${session.locationName} – ` : ''}{fmt.dateTime(session.startAt)}{session.endAt ? ` – ${fmt.dateTime(session.endAt)}` : ''}{session.selectable ? '' : t('mr.notAvailable')}</span>
              </label>
            ))}
            <label className="settlement-addon-option">
              <input type="radio" name="remedy" value="REFUND" checked={refund} onChange={() => setChoice('REFUND')} />
              <span>{t('mr.refundOption')}</span>
            </label>
          </fieldset>
        )}
        {pending && !member && <BuyerProof groupCode={data.groupCode} onReference={setProof} />}
        {pending && <button className="button primary" type="button" disabled={command.pending || !canSend} onClick={() => setConfirming(true)}>{t('mr.send')}</button>}
      </section>
      <ConfirmDialog open={confirming} title={t('mr.dialogTitle')} confirmLabel={tShared('confirm')} pending={command.pending} onCancel={() => setConfirming(false)} onConfirm={send}>
        <p>{refund ? t('mr.dialogRefund') : t('mr.dialogMove', { place: chosenLocation ? t('mr.dialogPlace', { place: chosenLocation }) : '' })}{t('mr.dialogFinal')}</p>
      </ConfirmDialog>
    </CustomerPage>
  );
}
