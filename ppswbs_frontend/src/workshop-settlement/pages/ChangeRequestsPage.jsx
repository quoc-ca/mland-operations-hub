import React, { Fragment, useEffect, useMemo, useState } from 'react';
import { useUrlFilters } from '../../shared/ui/useUrlFilters';
import { useHttp } from '../../shared/api/useHttp';
import { useRole } from '../../shared/auth/useRole';
import { Loading, SuccessNotice, useCommand, useResource } from '../../shared/ui/components';
import { createSettlementApi } from '../api/settlementApi';
import { createOperationsApi } from '../../workshop-operations/api/operationsApi';
import { OpsPage } from '../OpsPage';

const sessionCache = new Map();

/** Date, start–end and branch of one session, read through the Operations session endpoint (read-only, cached per reference). */
function SessionTime({ reference, operations }) {
  const [state, setState] = useState(() => ({ loading: !sessionCache.has(reference), data: sessionCache.get(reference) || null }));
  useEffect(() => {
    if (sessionCache.has(reference)) { setState({ loading: false, data: sessionCache.get(reference) }); return undefined; }
    let alive = true;
    operations.getSession(reference)
      .then(data => { sessionCache.set(reference, data); if (alive) setState({ loading: false, data }); })
      .catch(() => { if (alive) setState({ loading: false, data: null }); });
    return () => { alive = false; };
  }, [reference]);
  if (state.loading) return <small className="muted">Loading…</small>;
  const session = state.data;
  if (!session) return <small className="muted" title={reference}>Session {String(reference).slice(0, 8)}…</small>;
  const time = value => (value ? new Date(value).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', hour12: false }) : '—');
  return (
    <span className="cr-session" title={reference}>
      <strong>{session.workshopDate || '—'}</strong> {time(session.scheduledStartAt)}–{time(session.scheduledEndAt)}
      {session.locationName ? <small className="muted">{session.locationName}</small> : null}
    </span>
  );
}

function SessionList({ references, operations }) {
  if (!references || references.length === 0) return <>—</>;
  return <>{references.map(reference => <div key={reference}><SessionTime reference={reference} operations={operations} /></div>)}</>;
}
import { ProblemNotice, ReasonDialog, asList, money, when } from '../ui';


function RequestDetails({ request, operations }) {
  const rows = [
    ['Request', request.requestReference],
    ['Ticket', request.ticketCode],
    ['Group', request.groupCode || '—'],
    ['Type', request.type],
    ['Status', request.status],
    ['Current session', <SessionList key="from" references={request.fromSessionReferences} operations={operations} />],
    ['Requested session', <SessionList key="to" references={request.targetSessionReferences} operations={operations} />],
    ['Blocked by', (request.blockedRules || []).join(', ') || '—'],
    ['Price increase', `${money(request.priceIncreaseAmount)}${request.buyerConfirmedIncrease ? ' (buyer confirmed)' : ''}`],
    ['Requested by', [request.requesterRole, request.channel].filter(Boolean).join(' · ') || '—'],
    ['Reason', request.reason || '—'],
    ['Created', when(request.createdAt)],
    ['Expires', when(request.expiresAt)],
    ['Decided', request.decidedAt ? when(request.decidedAt) : '—'],
    ['Decision reason', request.decisionReason || '—'],
    ['Applied change', request.appliedChangeReference || '—'],
  ];
  return (
    <dl className="cr-details">
      {rows.map(([label, value]) => <div key={label}><dt>{label}</dt><dd>{value}</dd></div>)}
    </dl>
  );
}
/** Queue of changes a Staff member or a Manager may allow (FR-045a, T117; owner decision 2026-11-17). An Admin has no part in it. */
export default function ChangeRequestsPage() {
  const http = useHttp();
  const api = createSettlementApi(http);
  const operations = useMemo(() => createOperationsApi(http), [http]);
  const role = useRole();
  const isAdmin = role === 'ADMIN';
  const canDecide = role === 'STAFF' || role === 'MANAGER';
  const [{ status }, setUrlFilters] = useUrlFilters({ status: 'PENDING' });
  const setStatus = value => setUrlFilters({ status: value });
  const [openDetails, setOpenDetails] = useState(null); // requestReference whose detail row is open
  const [deciding, setDeciding] = useState(null); // { request, decision }
  const [message, setMessage] = useState('');
  const command = useCommand();
  // Nothing is read until the role is known to be Staff or Manager (an Admin never reads this queue).
  const queue = useResource(() => (canDecide ? api.listChangeRequests({ status }) : Promise.resolve([])), [status, canDecide]);
  const requests = useMemo(() => asList(queue.data), [queue.data]);

  const decide = async reason => {
    const { request, decision } = deciding;
    const out = await command.run(key => api.decideChangeRequest(request.requestReference, { decision, reason }, key));
    if (out.ok) { setDeciding(null); setMessage(decision === 'APPROVE' ? 'Change approved and applied.' : 'Change rejected.'); queue.reload(); }
  };

  if (isAdmin) {
    return (
      <OpsPage eyebrow="TICKET CHANGES" title="Change requests" intro="">
        <div className="empty-state" role="status"><p>Admin accounts have no part in ticket changes. Staff and Managers handle this queue.</p></div>
      </OpsPage>
    );
  }

  return (
    <OpsPage eyebrow="TICKET CHANGES" title="Change requests" intro="Changes that only fail on a rule a Staff member or a Manager may lift. Approval applies one counted change now.">
      <SuccessNotice>{message}</SuccessNotice>
      <ProblemNotice error={queue.error || command.error} onRetry={command.retry} onReload={queue.reload} />
      <section className="ota-panel">
        <div className="ota-inline-form cr-filter">
          <label>Status
            <select value={status} onChange={event => setStatus(event.target.value)}>
              {['PENDING', 'APPROVED', 'REJECTED', 'EXPIRED'].map(value => <option key={value} value={value}>{value}</option>)}
            </select>
          </label>
          <button className="button secondary" type="button" onClick={() => queue.reload()}>Refresh</button>
        </div>
        {queue.loading ? <Loading /> : queue.error ? null : requests.length === 0 ? <div className="empty-state"><p>No change requests found.</p></div> : (
          <div className="ops-table-wrap"><table className="ops-table cr-table">
            <thead><tr><th>Ticket / request</th><th>Type</th><th>Current session</th><th>Requested session</th><th>Blocked by</th><th>Price increase</th><th>Expires</th><th>Status</th><th>Decision</th></tr></thead>
            <tbody>
              {requests.map(request => (
                <Fragment key={request.requestReference}>
                <tr>
                  <td className="cr-wrap" title={request.requestReference}>
                    {request.ticketCode}
                    <small className="cr-ref muted">{String(request.requestReference || '').slice(0, 16)}{String(request.requestReference || '').length > 16 ? '…' : ''}</small>
                  </td>
                  <td>{request.type}</td>
                  <td><SessionList references={request.fromSessionReferences} operations={operations} /></td>
                  <td><SessionList references={request.targetSessionReferences} operations={operations} /></td>
                  <td className="cr-wrap">{(request.blockedRules || []).join(', ')}</td>
                  <td>{money(request.priceIncreaseAmount)}{request.buyerConfirmedIncrease ? ' (buyer confirmed)' : ''}</td>
                  <td>{when(request.expiresAt)}</td>
                  <td><span className="ops-status">{request.status}</span></td>
                  <td>
                    <div className="cr-actions">
                      <button type="button" className="button outline" aria-expanded={openDetails === request.requestReference} onClick={() => setOpenDetails(v => (v === request.requestReference ? null : request.requestReference))}>{openDetails === request.requestReference ? "Hide details" : "Details"}</button>
                      {request.status === 'PENDING' && canDecide && (<>
                      <button type="button" className="button primary" onClick={() => setDeciding({ request, decision: 'APPROVE' })}>Approve</button>
                      <button type="button" className="button danger" onClick={() => setDeciding({ request, decision: 'REJECT' })}>Reject</button>
                      </>)}
                    </div>
                    {request.status !== 'PENDING' && request.decisionReason ? <small>{request.decisionReason}</small> : null}
                  </td>
                </tr>
                {openDetails === request.requestReference && (
                  <tr className="cr-detail-row"><td colSpan={9}><RequestDetails request={request} operations={operations} /></td></tr>
                )}
              </Fragment>
              ))}
            </tbody>
          </table></div>
        )}
      </section>
      <ReasonDialog open={Boolean(deciding)} title={deciding?.decision === 'APPROVE' ? 'Approve this change' : 'Reject this change'}
        confirmLabel={deciding?.decision === 'APPROVE' ? 'Approve' : 'Reject'} pending={command.pending} onCancel={() => setDeciding(null)} onConfirm={decide}>
        <p>Ticket {deciding?.request.ticketCode}. Approving applies the change now; the capacity and quota checks still run on the server.</p>
      </ReasonDialog>
    </OpsPage>
  );
}
