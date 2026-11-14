import React, { useMemo, useState } from 'react';
import { useHttp } from '../../shared/api/useHttp';
import { useRole } from '../../shared/auth/useRole';
import { Loading, SuccessNotice, useCommand, useResource } from '../../shared/ui/components';
import { createSettlementApi } from '../api/settlementApi';
import { OpsPage } from '../OpsPage';
import { ProblemNotice, ReasonDialog, asList, money, when } from '../ui';

/** Queue of changes only a Manager may allow (FR-045a, T117). */
export default function ChangeRequestsPage() {
  const api = createSettlementApi(useHttp());
  const role = useRole();
  const isManager = role === 'MANAGER' || role === 'ADMIN';
  const [status, setStatus] = useState('PENDING');
  const [deciding, setDeciding] = useState(null); // { request, decision }
  const [message, setMessage] = useState('');
  const command = useCommand();
  const queue = useResource(() => api.listChangeRequests({ status }), [status]);
  const requests = useMemo(() => asList(queue.data), [queue.data]);

  const decide = async reason => {
    const { request, decision } = deciding;
    const out = await command.run(key => api.decideChangeRequest(request.requestReference, { decision, reason }, key));
    if (out.ok) { setDeciding(null); setMessage(decision === 'APPROVE' ? 'Change approved and applied.' : 'Change rejected.'); queue.reload(); }
  };

  return (
    <OpsPage eyebrow="TICKET CHANGES" title="Change requests" intro="Changes that only fail on a rule a Manager may lift. Approval applies one counted change now.">
      <SuccessNotice>{message}</SuccessNotice>
      <ProblemNotice error={queue.error || command.error} onRetry={command.retry} onReload={queue.reload} />
      <section className="ota-panel">
        <div className="ota-inline-form">
          <label>Status
            <select value={status} onChange={event => setStatus(event.target.value)}>
              {['PENDING', 'APPROVED', 'REJECTED', 'EXPIRED'].map(value => <option key={value} value={value}>{value}</option>)}
            </select>
          </label>
          <button className="button secondary" type="button" onClick={() => queue.reload()}>Refresh</button>
        </div>
        {queue.loading ? <Loading /> : queue.error ? null : requests.length === 0 ? <div className="empty-state"><p>No change requests found.</p></div> : (
          <table className="ops-table">
            <thead><tr><th>Request</th><th>Ticket</th><th>Type</th><th>Blocked by</th><th>Price increase</th><th>Expires</th><th>Status</th><th>Decision</th></tr></thead>
            <tbody>
              {requests.map(request => (
                <tr key={request.requestReference}>
                  <td>{request.requestReference}</td>
                  <td>{request.ticketCode}</td>
                  <td>{request.type}</td>
                  <td>{(request.blockedRules || []).join(', ')}</td>
                  <td>{money(request.priceIncreaseAmount)}{request.buyerConfirmedIncrease ? ' (buyer confirmed)' : ''}</td>
                  <td>{when(request.expiresAt)}</td>
                  <td><span className="ops-status">{request.status}</span></td>
                  <td>
                    {request.status === 'PENDING' && isManager && (<>
                      <button type="button" className="button primary" onClick={() => setDeciding({ request, decision: 'APPROVE' })}>Approve</button>
                      <button type="button" className="button danger" onClick={() => setDeciding({ request, decision: 'REJECT' })}>Reject</button>
                    </>)}
                    {request.status !== 'PENDING' && request.decisionReason ? <small>{request.decisionReason}</small> : null}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>
      <ReasonDialog open={Boolean(deciding)} title={deciding?.decision === 'APPROVE' ? 'Approve this change' : 'Reject this change'}
        confirmLabel={deciding?.decision === 'APPROVE' ? 'Approve' : 'Reject'} pending={command.pending} onCancel={() => setDeciding(null)} onConfirm={decide}>
        <p>Ticket {deciding?.request.ticketCode}. Approving applies the change now; the capacity and quota checks still run on the server.</p>
      </ReasonDialog>
    </OpsPage>
  );
}
