import React, { useState } from 'react';
import { ErrorNotice, SuccessNotice, useCommand } from '../../shared/ui/components';
import { useFormat, useT } from '../../shared/i18n/I18nProvider';
import { messages } from '../messages';
import { createSettlementApi } from '../../workshop-settlement/api/settlementApi';
import FolioCorrectionLookup from '../../workshop-settlement/FolioCorrectionLookup';

/**
 * Manager-only post-FINALIZED correction of one ticket folio (T106: POST /workshop-tickets/{ticketCode}/folio/corrections).
 * Overcharge only; the overcharge becomes a bank refund request (never a VNPay refund). The finalized folio, Loyalty
 * earn and the ticket stay unchanged, and an undercharge is absorbed by Mland and never collected.
 */
export default function StaffWorkshopAddonPanel({ http }) {
  const t = useT(messages);
  const fmt = useFormat();
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
        <p className="ota-eyebrow">{t('folio.eyebrow')}</p>
        <h2>{t('folio.title')}</h2>
        <p className="muted small">{t('folio.subtitle')}</p>
      </div>
    </div>
    <SuccessNotice>{correction ? t('folio.created', { ref: correction.correctionReference, status: correction.status, refund: correction.refundId ? ` (${correction.refundId})` : '' }) : ''}</SuccessNotice>
    <ErrorNotice error={createCommand.error} onRetry={createCommand.retry} />
    {!reason.trim() && ticketCode.trim() && Number(amountVnd) > 0 && (
      <p role="status" className="muted small">{t('folio.reasonRequired')}</p>
    )}
    {createCommand.pending && <p role="status" aria-live="polite" className="muted small">{t('folio.submitting')}</p>}
    <form className="ota-form" onSubmit={submit}>
      <label>{t('folio.ticketCode')}<input value={ticketCode} onChange={event => setTicketCode(event.target.value)} placeholder="GRP-…-L0-T1" autoComplete="off" /></label>
      <label>{t('folio.amount')}<input inputMode="numeric" value={amountVnd} onChange={event => setAmountVnd(event.target.value)} placeholder="50000" autoComplete="off" /></label>
      <label>{t('folio.reason')}<input value={reason} onChange={event => setReason(event.target.value)} placeholder={t('folio.reasonPlaceholder')} autoComplete="off" maxLength={1000} /></label>
      <button className="button primary" type="submit" disabled={createCommand.pending || !!correction || !isValid}>{createCommand.pending ? t('folio.submittingShort') : t('folio.submit')}</button>
    </form>
    {correction && <div className="staff-addon-result folio-correction-result">
      <p><strong>{t('folio.correction', { ref: correction.correctionReference })}</strong></p>
      <p>{t('folio.result', { ticket: correction.ticketCode, amount: fmt.money(correction.overchargeAmount), status: correction.status, refund: correction.refundStatus ? t('folio.refundStatus', { status: correction.refundStatus }) : '' })}</p>
      <p className="muted small">{t('folio.bankNote')}</p>
      {!viewing && <button type="button" className="button outline" onClick={() => setViewing(true)}>{t('folio.view')}</button>}
      {viewing && <FolioCorrectionLookup api={settlementApi} ticketCode={correction.ticketCode || ticketCode.trim()}
        initialReference={correction.correctionReference} />}
    </div>}
    <p className="muted small staff-addon-policy">{t('folio.policy')}</p>
  </article>;
}

export { StaffWorkshopAddonPanel as FolioCorrectionPanel };
