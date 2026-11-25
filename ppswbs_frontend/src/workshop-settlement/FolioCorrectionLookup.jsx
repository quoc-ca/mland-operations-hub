import React, { useEffect, useState } from 'react';
import { useCommand } from '../shared/ui/components';
import { useEnumLabel, useFormat, useT } from '../shared/i18n/I18nProvider';
import { messages } from './messages';
import { ProblemNotice } from './ui';

function CorrectionDetails({ correction }) {
  const t = useT(messages);
  const fmt = useFormat();
  const statusLabel = useEnumLabel(messages, 'correctionStatus');
  const refundLabel = useEnumLabel(messages, 'refundStatus');
  return (
    <dl className="ops-facts" aria-label={t('correction.details')}>
      <div><dt>{t('correction.reference')}</dt><dd>{correction.correctionReference}</dd></div>
      <div><dt>{t('correction.ticket')}</dt><dd>{correction.ticketCode}</dd></div>
      <div><dt>{t('correction.overcharge')}</dt><dd>{fmt.money(correction.overchargeAmount)}</dd></div>
      <div><dt>{t('correction.status')}</dt><dd><span className="ops-status">{statusLabel(correction.status)}</span></dd></div>
      {correction.refundId && <div><dt>{t('correction.refundId')}</dt><dd>{correction.refundId}</dd></div>}
      {correction.refundStatus && <div><dt>{t('correction.refundStatus')}</dt><dd><span className="ops-status">{refundLabel(correction.refundStatus)}</span></dd></div>}
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
  const t = useT(messages);
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
    <section className="folio-correction-view" aria-label={t('correction.lookupAria')}>
      <h3>{t('correction.title')}</h3>
      <form className="ota-inline-form" onSubmit={submit}>
        <label>{t('correction.inputLabel')}
          <input value={reference} onChange={event => setReference(event.target.value)} autoComplete="off" maxLength={128} />
        </label>
        <button className="button outline" type="submit" disabled={command.pending || !ticketCode || !reference.trim()}>{t('correction.view')}</button>
      </form>
      {command.pending && <p role="status" className="muted small">{t('correction.loading')}</p>}
      {notFound
        ? <div className="notice error" role="alert"><p>{t('correction.notFound')}</p></div>
        : <ProblemNotice error={command.error} onRetry={command.retry} />}
      {correction && <CorrectionDetails correction={correction} />}
    </section>
  );
}
