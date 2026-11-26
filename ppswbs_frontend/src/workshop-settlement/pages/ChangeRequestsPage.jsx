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
  const t = useT(messages);
  const tShared = useT(sharedMessages);
  const [state, setState] = useState(() => ({ loading: !sessionCache.has(reference), data: sessionCache.get(reference) || null }));
  useEffect(() => {
    if (sessionCache.has(reference)) { setState({ loading: false, data: sessionCache.get(reference) }); return undefined; }
    let alive = true;
    operations.getSession(reference, { suppressAccessDenied: true })
      .then(data => { sessionCache.set(reference, data); if (alive) setState({ loading: false, data }); })
      .catch(() => { if (alive) setState({ loading: false, data: null }); });
    return () => { alive = false; };
  }, [reference]);
  if (state.loading) return <small className="muted">{tShared('loading')}</small>;
  const session = state.data;
  if (!session) return <small className="muted" title={reference}>{t('cr.session', { ref: String(reference).slice(0, 8) })}</small>;
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
import { useEnumLabel, useFormat, useT } from '../../shared/i18n/I18nProvider';
import { sharedMessages } from '../../shared/ui/messages';
import { messages } from '../messages';
import { ProblemNotice, ReasonDialog, asList } from '../ui';


function RequestDetails({ request, operations }) {
  const t = useT(messages);
  const fmt = useFormat();
  const statusLabel = useEnumLabel(messages, 'changeStatus');
  const typeLabel = useEnumLabel(messages, 'changeType');
  const roleLabel = useEnumLabel(messages, 'actorRole');
  const rows = [
    [t('cr.d.request'), request.requestReference],
    [t('cr.d.ticket'), request.ticketCode],
    [t('cr.d.group'), request.groupCode || '—'],
    [t('cr.d.type'), typeLabel(request.type)],
    [t('cr.d.status'), statusLabel(request.status)],
    [t('cr.d.current'), <SessionList key="from" references={request.fromSessionReferences} operations={operations} />],
    [t('cr.d.requested'), <SessionList key="to" references={request.targetSessionReferences} operations={operations} />],
    [t('cr.d.blocked'), (request.blockedRules || []).join(', ') || '—'],
    [t('cr.d.increase'), `${fmt.money(request.priceIncreaseAmount)}${request.buyerConfirmedIncrease ? t('cr.buyerConfirmed') : ''}`],
    [t('cr.d.requestedBy'), [request.requesterRole ? roleLabel(request.requesterRole) : '', request.channel].filter(Boolean).join(' · ') || '—'],
    [t('cr.d.reason'), request.reason || '—'],
    [t('cr.d.created'), fmt.dateTime(request.createdAt)],
    [t('cr.d.expires'), fmt.dateTime(request.expiresAt)],
    [t('cr.d.decided'), request.decidedAt ? fmt.dateTime(request.decidedAt) : '—'],
    [t('cr.d.decisionReason'), request.decisionReason || '—'],
    [t('cr.d.applied'), request.appliedChangeReference || '—'],
  ];
  return (
    <dl className="cr-details">
      {rows.map(([label, value]) => <div key={label}><dt>{label}</dt><dd>{value}</dd></div>)}
    </dl>
  );
}
/** Queue of changes a Staff member or a Manager may allow (FR-045a, T117; owner decision 2026-11-17). */
export default function ChangeRequestsPage() {
  const t = useT(messages);
  const fmt = useFormat();
  const statusLabel = useEnumLabel(messages, 'changeStatus');
  const typeLabel = useEnumLabel(messages, 'changeType');
  const http = useHttp();
  const api = createSettlementApi(http);
  const operations = useMemo(() => createOperationsApi(http), [http]);
  const role = useRole();
  const canDecide = role === 'STAFF' || role === 'MANAGER';
  const [{ status }, setUrlFilters] = useUrlFilters({ status: 'PENDING' });
  const setStatus = value => setUrlFilters({ status: value });
  const [openDetails, setOpenDetails] = useState(null); // requestReference whose detail row is open
  const [deciding, setDeciding] = useState(null); // { request, decision }
  const [message, setMessage] = useState('');
  const command = useCommand();
  // Nothing is read until the role is known to be Staff or Manager.
  const queue = useResource(() => (canDecide ? api.listChangeRequests({ status }) : Promise.resolve([])), [status, canDecide]);
  const requests = useMemo(() => asList(queue.data), [queue.data]);

  const decide = async reason => {
    const { request, decision } = deciding;
    const out = await command.run(key => api.decideChangeRequest(request.requestReference, { decision, reason }, key));
    if (out.ok) { setDeciding(null); setMessage(t(decision === 'APPROVE' ? 'cr.msg.approved' : 'cr.msg.rejected')); queue.reload(); }
  };

  return (
    <OpsPage title={t('cr.title')} intro={t('cr.intro')}>
      <SuccessNotice>{message}</SuccessNotice>
      <ProblemNotice error={queue.error || command.error} onRetry={command.retry} onReload={queue.reload} />
      <section className="wsp-panel">
        <div className="wsp-inline-form cr-filter">
          <label>{t('cr.status')}
            <select value={status} onChange={event => setStatus(event.target.value)}>
              {['PENDING', 'APPROVED', 'REJECTED', 'EXPIRED'].map(value => <option key={value} value={value}>{statusLabel(value)}</option>)}
            </select>
          </label>
          <button className="button secondary" type="button" onClick={() => queue.reload()}>{t('cr.refresh')}</button>
        </div>
        {queue.loading ? <Loading /> : queue.error ? null : requests.length === 0 ? <div className="empty-state"><p>{t('cr.empty')}</p></div> : (
          <div className="ops-table-wrap"><table className="ops-table cr-table">
            <thead><tr><th>{t('cr.col.ticket')}</th><th>{t('cr.col.type')}</th><th>{t('cr.col.current')}</th><th>{t('cr.col.requested')}</th><th>{t('cr.col.blocked')}</th><th>{t('cr.col.increase')}</th><th>{t('cr.col.expires')}</th><th>{t('cr.col.status')}</th><th>{t('cr.col.decision')}</th></tr></thead>
            <tbody>
              {requests.map(request => (
                <Fragment key={request.requestReference}>
                <tr>
                  <td className="cr-wrap" title={request.requestReference}>
                    {request.ticketCode}
                    <small className="cr-ref muted">{String(request.requestReference || '').slice(0, 16)}{String(request.requestReference || '').length > 16 ? '…' : ''}</small>
                  </td>
                  <td>{typeLabel(request.type)}</td>
                  <td><SessionList references={request.fromSessionReferences} operations={operations} /></td>
                  <td><SessionList references={request.targetSessionReferences} operations={operations} /></td>
                  <td className="cr-wrap">{(request.blockedRules || []).join(', ')}</td>
                  <td>{fmt.money(request.priceIncreaseAmount)}{request.buyerConfirmedIncrease ? t('cr.buyerConfirmed') : ''}</td>
                  <td>{fmt.dateTime(request.expiresAt)}</td>
                  <td><span className="ops-status">{statusLabel(request.status)}</span></td>
                  <td>
                    <div className="cr-actions">
                      <button type="button" className="button outline" aria-expanded={openDetails === request.requestReference} onClick={() => setOpenDetails(v => (v === request.requestReference ? null : request.requestReference))}>{openDetails === request.requestReference ? t('cr.hideDetails') : t('cr.details')}</button>
                      {request.status === 'PENDING' && canDecide && (<>
                      <button type="button" className="button primary" onClick={() => setDeciding({ request, decision: 'APPROVE' })}>{t('cr.approve')}</button>
                      <button type="button" className="button danger" onClick={() => setDeciding({ request, decision: 'REJECT' })}>{t('cr.reject')}</button>
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
      <ReasonDialog open={Boolean(deciding)} title={t(deciding?.decision === 'APPROVE' ? 'cr.approveTitle' : 'cr.rejectTitle')}
        confirmLabel={t(deciding?.decision === 'APPROVE' ? 'cr.approve' : 'cr.reject')} pending={command.pending} onCancel={() => setDeciding(null)} onConfirm={decide}>
        <p>{t(deciding?.decision === 'REJECT' ? 'cr.rejectBody' : 'cr.dialogBody', { code: deciding?.request.ticketCode })}</p>
      </ReasonDialog>
    </OpsPage>
  );
}
