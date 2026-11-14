import React, { useState } from 'react';
import { useParams } from 'react-router';
import { useAuth } from '../../shared/auth/AuthProvider';
import { useHttp } from '../../shared/api/useHttp';
import { Loading, useCommand, useResource } from '../../shared/ui/components';
import { createSettlementApi } from '../api/settlementApi';
import { CustomerPage } from '../OpsPage';
import { ProblemNotice, money } from '../ui';

function FolioView({ folio }) {
  return (
    <section className="ota-panel" aria-label="Ticket folio">
      <div className="ota-panel-head"><h2>{folio.ticketCode}</h2><span className="ops-status">{folio.status}</span></div>
      <dl className="ops-facts">
        <div><dt>Total</dt><dd>{money(folio.totalAmount)}</dd></div>
        <div><dt>Paid</dt><dd>{money(folio.paidAmount)}</dd></div>
        {folio.pendingAmount !== undefined && <div><dt>Awaiting verification</dt><dd>{money(folio.pendingAmount)}</dd></div>}
        <div><dt>Balance due</dt><dd>{money(folio.balanceDue)}</dd></div>
      </dl>
      {(folio.collections || []).length > 0 && (
        <table className="ops-table">
          <thead><tr><th>Method</th><th>Amount</th><th>Status</th></tr></thead>
          <tbody>
            {folio.collections.map(item => <tr key={item.collectionId}><td>{item.method}</td><td>{money(item.amount)}</td><td><span className="ops-status">{item.status}</span></td></tr>)}
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
  const { ticketCode } = useParams();
  const { status } = useAuth();
  const member = status === 'signed-in';
  const api = createSettlementApi(useHttp());
  const [contact, setContact] = useState({ contactEmail: '', contactPhone: '' });
  const [guestFolio, setGuestFolio] = useState(null);
  const command = useCommand();
  const own = useResource(() => (member ? api.getFolio(ticketCode) : Promise.resolve(null)), [ticketCode, member]);

  if (status === 'loading') return <CustomerPage title="Ticket payment"><Loading /></CustomerPage>;

  const oneContact = Boolean(contact.contactEmail.trim()) !== Boolean(contact.contactPhone.trim());
  const lookup = async event => {
    event.preventDefault();
    const proof = contact.contactEmail.trim() ? { contactEmail: contact.contactEmail.trim() } : { contactPhone: contact.contactPhone.trim() };
    const out = await command.run(() => api.lookupFolio(ticketCode, proof));
    setContact({ contactEmail: '', contactPhone: '' });
    if (out.ok) setGuestFolio(out.result);
  };

  if (member) {
    return (
      <CustomerPage title="Ticket payment">
        {own.loading ? <Loading /> : own.data ? <FolioView folio={own.data} />
          : own.error && (own.error.status === 404 || own.error.status === 403)
            ? <div className="notice error" role="alert"><p>We could not find this ticket for your account.</p></div>
            : <ProblemNotice error={own.error} onRetry={own.reload} onReload={own.reload} />}
      </CustomerPage>
    );
  }
  const error = command.error;
  return (
    <CustomerPage title="Ticket payment" intro="Enter the email or phone number you used to book. It is sent securely and is not kept on this page.">
      {guestFolio ? <FolioView folio={guestFolio} /> : (
        <>
          {error?.status === 404
            ? <div className="notice error" role="alert"><p>We could not find a ticket with this code and contact.</p></div>
            : <ProblemNotice error={error} onRetry={command.retry} />}
          <form className="ota-form" aria-label="Find ticket" onSubmit={lookup}>
            <label>Email<input type="email" autoComplete="off" value={contact.contactEmail} onChange={event => setContact(v => ({ ...v, contactEmail: event.target.value }))} /></label>
            <label>Phone<input autoComplete="off" value={contact.contactPhone} onChange={event => setContact(v => ({ ...v, contactPhone: event.target.value }))} /></label>
            <p className="muted small">Use one of the two.</p>
            <button className="button primary" type="submit" disabled={command.pending || !oneContact}>Show my ticket</button>
          </form>
        </>
      )}
    </CustomerPage>
  );
}
