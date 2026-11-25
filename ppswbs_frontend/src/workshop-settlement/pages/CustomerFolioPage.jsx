import React, { useState } from 'react';
import { useParams } from 'react-router';
import { useAuth } from '../../shared/auth/AuthProvider';
import { useHttp } from '../../shared/api/useHttp';
import { Loading, useCommand, useResource } from '../../shared/ui/components';
import { createSettlementApi } from '../api/settlementApi';
import FolioCorrectionLookup from '../FolioCorrectionLookup';
import { CustomerPage } from '../OpsPage';
import { useEnumLabel, useFormat, useT } from '../../shared/i18n/I18nProvider';
import { messages } from '../messages';
import { ProblemNotice, VoucherNotRestoredNotice } from '../ui';
import { buildContactProof, EMPTY_CONTACT } from '../../workshops/contactValidation';
import { PhoneInput } from '../../workshops/PhoneInput';

function FolioView({ folio }) {
  const t = useT(messages);
  const fmt = useFormat();
  const folioStatus = useEnumLabel(messages, 'folioStatus');
  const collectionStatus = useEnumLabel(messages, 'collectionStatus');
  const methodLabel = useEnumLabel(messages, 'method');
  return (
    <section className="wsp-panel" aria-label={t('cf.folioAria')}>
      <div className="wsp-panel-head"><h2>{folio.ticketCode}</h2><span className="ops-status">{folioStatus(folio.status)}</span></div>
      <dl className="ops-facts">
        <div><dt>{t('cf.total')}</dt><dd>{fmt.money(folio.totalAmount)}</dd></div>
        <div><dt>{t('cf.paid')}</dt><dd>{fmt.money(folio.paidAmount)}</dd></div>
        {folio.pendingAmount !== undefined && <div><dt>{t('cf.awaiting')}</dt><dd>{fmt.money(folio.pendingAmount)}</dd></div>}
        <div><dt>{t('cf.balanceDue')}</dt><dd>{fmt.money(folio.balanceDue)}</dd></div>
      </dl>
      <VoucherNotRestoredNotice voucherRestoration={folio.voucherRestoration} />
      {(folio.collections || []).length > 0 && (
        <table className="ops-table">
          <thead><tr><th>{t('cf.col.method')}</th><th>{t('cf.col.amount')}</th><th>{t('cf.col.status')}</th></tr></thead>
          <tbody>
            {folio.collections.map(item => <tr key={item.collectionId}><td>{methodLabel(item.method)}</td><td>{fmt.money(item.amount)}</td><td><span className="ops-status">{collectionStatus(item.status)}</span></td></tr>)}
          </tbody>
        </table>
      )}
    </section>
  );
}

/**
 * Customer view of one ticket folio (FR-048). A signed-in Member reads it with the bearer; a Guest sends one contact of the
 * booking in the request body (never in the URL), and the contact is dropped from the screen as soon as the answer arrives.
 * A wrong ticket or a wrong contact gives the same answer.
 */
export default function CustomerFolioPage() {
  const t = useT(messages);
  const { ticketCode } = useParams();
  const { status } = useAuth();
  const member = status === 'signed-in';
  const api = createSettlementApi(useHttp());
  const [contact, setContact] = useState({ ...EMPTY_CONTACT });
  const [guestFolio, setGuestFolio] = useState(null);
  const command = useCommand();
  const own = useResource(() => (member ? api.getFolio(ticketCode) : Promise.resolve(null)), [ticketCode, member]);

  if (status === 'loading') return <CustomerPage title={t('cf.title')}><Loading /></CustomerPage>;

  // One contact only, in the normalized form the server compares by; null while it is empty or malformed.
  const { proof } = buildContactProof({ email: contact.contactEmail, phone: contact.contactPhone, phoneCountry: contact.contactPhoneCountry });
  const oneContact = Boolean(proof);
  const lookup = async event => {
    event.preventDefault();
    if (!proof) return;
    const out = await command.run(() => api.lookupFolio(ticketCode, proof));
    setContact({ ...EMPTY_CONTACT });
    if (out.ok) setGuestFolio(out.result);
  };

  if (member) {
    return (
      <CustomerPage title={t('cf.title')}>
        {own.loading ? <Loading /> : own.data ? (
          <>
            <FolioView folio={own.data} />
            {/* T127 (FR-050): the owning Member reads a posted correction with the bearer; a Guest never gets this view. */}
            <section className="wsp-panel"><FolioCorrectionLookup api={api} ticketCode={ticketCode} /></section>
          </>
        )
          : own.error && (own.error.status === 404 || own.error.status === 403)
            ? <div className="notice error" role="alert"><p>{t('cf.notFoundMember')}</p></div>
            : <ProblemNotice error={own.error} onRetry={own.reload} onReload={own.reload} />}
      </CustomerPage>
    );
  }
  const error = command.error;
  return (
    <CustomerPage title={t('cf.title')} intro={t('cf.intro')}>
      {guestFolio ? <FolioView folio={guestFolio} /> : (
        <>
          {error?.status === 404
            ? <div className="notice error" role="alert"><p>{t('cf.notFoundGuest')}</p></div>
            : <ProblemNotice error={error} onRetry={command.retry} />}
          <form className="wsp-form" aria-label={t('cf.findAria')} onSubmit={lookup}>
            <label>{t('cf.email')}<input type="email" autoComplete="off" value={contact.contactEmail} onChange={event => setContact(v => ({ ...v, contactEmail: event.target.value }))} /></label>
            <label htmlFor="folio-contact-phone">{t('cf.phone')}</label>
            <PhoneInput
              id="folio-contact-phone"
              country={contact.contactPhoneCountry}
              value={contact.contactPhone}
              onCountryChange={contactPhoneCountry => setContact(v => ({ ...v, contactPhoneCountry }))}
              onValueChange={contactPhone => setContact(v => ({ ...v, contactPhone }))}
            />
            <p className="muted small">{t('cf.useOne')}</p>
            <button className="button primary" type="submit" disabled={command.pending || !oneContact}>{t('cf.show')}</button>
          </form>
        </>
      )}
    </CustomerPage>
  );
}
