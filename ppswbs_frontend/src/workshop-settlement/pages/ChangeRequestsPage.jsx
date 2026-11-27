import React, { Fragment, useEffect, useMemo, useState } from 'react';
import { useUrlFilters } from '../../shared/ui/useUrlFilters';
import { useHttp } from '../../shared/api/useHttp';
import { useRole } from '../../shared/auth/useRole';
import { ConfirmDialog, Loading, SuccessNotice, useCommand, useResource } from '../../shared/ui/components';
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
  if (!session) return <small className="muted">{t('cr.sessionUnknown')}</small>;
  const time = value => (value ? new Date(value).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', hour12: false }) : '—');
  return (
    <span className="cr-session">
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


/** Who asked, by name (FR-045a, 2026-11-27): never the raw role/channel pair. */
function requesterText(request, t, roleLabel) {
  const { requesterRole: role, requesterName: name } = request;
  if (!role) return '—';
  if (role === 'GUEST') return t('cr.requester.guest');
  if (!name) return roleLabel(role);
  return role === 'STAFF' || role === 'MANAGER' ? `${name} (${t(`cr.requester.${role}`)})` : name;
}

/** Blocked rules as text; an unknown code keeps its code rather than being hidden. */
function blockedText(request, t) {
  return (request.blockedRules || []).map(rule => {
    const key = `picker.rule.${rule}`;
    return t(key) === key ? rule : t(key);
  }).join(', ');
}

function RequestDetails({ request, operations }) {
  const t = useT(messages);
  const fmt = useFormat();
  const statusLabel = useEnumLabel(messages, 'changeStatus');
  const typeLabel = useEnumLabel(messages, 'changeType');
  const roleLabel = useEnumLabel(messages, 'actorRole');
  const rows = [
    [t('cr.d.ticket'), request.ticketCode],
    [t('cr.d.group'), request.groupCode || '—'],
    [t('cr.d.type'), typeLabel(request.type)],
    [t('cr.d.status'), statusLabel(request.status)],
    [t('cr.d.current'), <SessionList key="from" references={request.fromSessionReferences} operations={operations} />],
    [t('cr.d.requested'), <SessionList key="to" references={request.targetSessionReferences} operations={operations} />],
    [t('cr.d.blocked'), blockedText(request, t) || '—'],
    [t('cr.d.increase'), `${fmt.money(request.priceIncreaseAmount)}${request.buyerConfirmedIncrease ? t('cr.buyerConfirmed') : ''}`],
    [t('cr.d.requestedBy'), requesterText(request, t, roleLabel)],
    [t('cr.d.reason'), request.reason || '—'],
    [t('cr.d.created'), fmt.dateTime(request.createdAt)],
    [t('cr.d.expires'), fmt.dateTime(request.expiresAt)],
    [t('cr.d.decided'), request.decidedAt ? fmt.dateTime(request.decidedAt) : '—'],
    [t('cr.d.decisionReason'), request.decisionReason || '—'],
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
    const body = reason ? { decision, reason } : { decision };
    const out = await command.run(key => api.decideChangeRequest(request.requestReference, body, key));
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
                  <td className="cr-wrap">{request.ticketCode}</td>
                  <td>{typeLabel(request.type)}</td>
                  <td><SessionList references={request.fromSessionReferences} operations={operations} /></td>
                  <td><SessionList references={request.targetSessionReferences} operations={operations} /></td>
                  <td className="cr-wrap">{blockedText(request, t)}</td>
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
      <ConfirmDialog open={deciding?.decision === 'APPROVE'} title={t('cr.approveTitle')} confirmLabel={t('cr.approve')}
        pending={command.pending} onCancel={() => setDeciding(null)} onConfirm={() => decide()}>
        <p>{t('cr.dialogBody', { code: deciding?.request.ticketCode })}</p>
      </ConfirmDialog>
      <ReasonDialog open={deciding?.decision === 'REJECT'} title={t('cr.rejectTitle')} confirmLabel={t('cr.reject')}
        pending={command.pending} onCancel={() => setDeciding(null)} onConfirm={decide}>
        <p>{t('cr.rejectBody', { code: deciding?.request.ticketCode })}</p>
      </ReasonDialog>
    </OpsPage>
  );
}
