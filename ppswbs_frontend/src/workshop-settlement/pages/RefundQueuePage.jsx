import React, { useMemo, useState } from 'react';
import { useHttp } from '../../shared/api/useHttp';
import { useRole } from '../../shared/auth/useRole';
import { Loading, SuccessNotice, useCommand, useResource } from '../../shared/ui/components';
import { createSettlementApi } from '../api/settlementApi';
import { OpsPage } from '../OpsPage';
import { ProblemNotice, ReasonDialog, asList, money } from '../ui';

const STATUSES = ['PENDING_ACCOUNT', 'PENDING_APPROVAL', 'PENDING_TRANSFER', 'PENDING_RECONCILIATION', 'REFUNDED', 'CLOSED_UNREACHABLE'];

/**
 * Bank refund queue and lifecycle (FR-038–FR-040). The status, amount and masked account are the server's; a step is only
 * offered for the status that allows it, and the server still refuses anything else. The full account number is shown on
 * request to a Manager, kept in memory only and never stored or logged.
 */
export default function RefundQueuePage() {
  const api = createSettlementApi(useHttp());
  const role = useRole();
  const isManager = role === 'MANAGER' || role === 'ADMIN';
  const [status, setStatus] = useState('');
  const [selected, setSelected] = useState(null);
  const [account, setAccount] = useState({ bankCode: '', accountNumber: '', holderName: '' });
  const [transfer, setTransfer] = useState({ bankTransactionReference: '', proofReference: '' });
  const [revealed, setRevealed] = useState(null);
  const [closing, setClosing] = useState(false);
  const [message, setMessage] = useState('');
  const command = useCommand();
  const queue = useResource(() => api.listRefunds(status ? { status } : undefined), [status]);
  const refunds = useMemo(() => asList(queue.data), [queue.data]);
  const refund = refunds.find(item => item.refundId === selected) || null;

  const act = async (send, success) => {
    setMessage('');
    const out = await command.run(send);
    if (out.ok) { setMessage(success); setRevealed(null); queue.reload(); }
    return out;
  };
  const transition = (body, success) => act(key => api.transitionRefund(refund.refundId, body, key), success);

  const enterAccount = async event => {
    event.preventDefault();
    const out = await act(key => api.putRefundAccount(refund.refundId, {
      bankCode: account.bankCode.trim(), accountNumber: account.accountNumber.trim(), holderName: account.holderName.trim(),
    }, key), 'Account recorded.');
    if (out.ok) setAccount({ bankCode: '', accountNumber: '', holderName: '' });
  };

  const reveal = async () => {
    const out = await command.run(() => api.getRefundAccountNumber(refund.refundId));
    if (out.ok) setRevealed(out.result);
  };

  const recordTransfer = async event => {
    event.preventDefault();
    const out = await transition({ action: 'RECORD_TRANSFER', bankTransactionReference: transfer.bankTransactionReference.trim(), proofReference: transfer.proofReference.trim() }, 'Transfer recorded.');
    if (out.ok) setTransfer({ bankTransactionReference: '', proofReference: '' });
  };

  const closeUnreachable = async reason => {
    const out = await transition({ action: 'CLOSE_UNREACHABLE', reason }, 'Refund closed as unreachable.');
    if (out.ok) setClosing(false);
  };

  return (
    <OpsPage eyebrow="BANK REFUNDS" title="Refund queue" intro="Every Mland refund is a bank transfer. A refund counts as done only when a Manager confirms REFUNDED.">
      <SuccessNotice>{message}</SuccessNotice>
      <ProblemNotice error={queue.error || command.error} onRetry={command.retry} onReload={queue.reload} />
      <section className="ota-panel">
        <div className="ota-inline-form">
          <label>Status
            <select value={status} onChange={event => { setStatus(event.target.value); setSelected(null); }}>
              <option value="">All</option>
              {STATUSES.map(value => <option key={value} value={value}>{value}</option>)}
            </select>
          </label>
          <button className="button secondary" type="button" onClick={() => queue.reload()}>Refresh</button>
        </div>
        {queue.loading ? <Loading /> : queue.error ? null : refunds.length === 0 ? <div className="empty-state"><p>No refund requests found.</p></div> : (
          <table className="ops-table">
            <thead><tr><th>Refund</th><th>Ticket / group</th><th>Reason</th><th>Amount</th><th>Status</th><th>Account</th></tr></thead>
            <tbody>
              {refunds.map(item => (
                <tr key={item.refundId} className={selected === item.refundId ? 'selected' : ''} style={{ cursor: 'pointer' }} onClick={() => { setSelected(item.refundId); setRevealed(null); }}>
                  <td>{item.refundId}</td>
                  <td>{item.ticketCode || item.groupCode || '—'}</td>
                  <td>{item.source}</td>
                  <td>{money(item.amount)}</td>
                  <td><span className="ops-status">{item.status}</span></td>
                  <td>{item.accountNumberMasked ? `${item.accountNumberMasked}${item.holderName ? ` · ${item.holderName}` : ''}` : '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>

      {refund && (
        <section className="ota-panel" aria-label="Refund actions">
          <div className="ota-panel-head"><h2>{refund.refundId}</h2><span className="ops-status">{refund.status}</span></div>
          {refund.status === 'PENDING_ACCOUNT' && (<>
            <p className="muted">Waiting for the payer’s account. The payer gets a single-use link by email (7 days), or Staff can enter the account the payer hands over at the counter.</p>
            <form className="ota-form" aria-label="Counter account" onSubmit={enterAccount}>
              <label>Bank code<input value={account.bankCode} maxLength={32} onChange={event => setAccount(v => ({ ...v, bankCode: event.target.value }))} /></label>
              <label>Account number<input inputMode="numeric" autoComplete="off" value={account.accountNumber} maxLength={32} onChange={event => setAccount(v => ({ ...v, accountNumber: event.target.value }))} /></label>
              <label>Holder name (as the bank shows it)<input autoComplete="off" value={account.holderName} maxLength={128} onChange={event => setAccount(v => ({ ...v, holderName: event.target.value }))} /></label>
              <button className="button primary" type="submit" disabled={command.pending || !account.bankCode.trim() || !account.accountNumber.trim() || !account.holderName.trim()}>Save account</button>
            </form>
            <div className="ops-actions">
              <button className="button outline" type="button" disabled={command.pending} onClick={() => act(key => api.resendAccountLink(refund.refundId, key), 'A fresh account link was sent; the previous link no longer works.')}>Resend account link</button>
              {isManager && <button className="button danger" type="button" onClick={() => setClosing(true)}>Close as unreachable</button>}
            </div>
          </>)}
          {refund.status !== 'PENDING_ACCOUNT' && refund.status !== 'REFUNDED' && refund.status !== 'CLOSED_UNREACHABLE' && isManager && (
            <div className="ops-actions">
              <button className="button outline" type="button" disabled={command.pending} onClick={reveal}>Show full account number</button>
              {refund.status === 'PENDING_APPROVAL' && <button className="button primary" type="button" disabled={command.pending} onClick={() => transition({ action: 'APPROVE' }, 'Refund approved.')}>Approve</button>}
              {refund.status === 'PENDING_RECONCILIATION' && <button className="button primary" type="button" disabled={command.pending} onClick={() => transition({ action: 'CONFIRM_REFUNDED' }, 'Refund confirmed as REFUNDED.')}>Confirm refunded</button>}
            </div>
          )}
          {revealed && (
            <dl className="ops-facts" aria-label="Recipient account">
              <div><dt>Bank</dt><dd>{revealed.bankCode || '—'}</dd></div>
              <div><dt>Account number</dt><dd>{revealed.accountNumber}</dd></div>
              <div><dt>Holder</dt><dd>{revealed.holderName || '—'}</dd></div>
              <div><dt /><dd><button type="button" className="button outline" onClick={() => setRevealed(null)}>Hide</button></dd></div>
            </dl>
          )}
          {refund.status === 'PENDING_TRANSFER' && isManager && (
            <form className="ota-form" aria-label="Record transfer" onSubmit={recordTransfer}>
              <p className="muted small">Compare the holder name the bank shows with the name entered and stop on a mismatch.</p>
              <label>Bank transaction reference<input value={transfer.bankTransactionReference} maxLength={128} onChange={event => setTransfer(v => ({ ...v, bankTransactionReference: event.target.value }))} /></label>
              <label>Proof reference<input value={transfer.proofReference} maxLength={256} onChange={event => setTransfer(v => ({ ...v, proofReference: event.target.value }))} /></label>
              <button className="button primary" type="submit" disabled={command.pending || !transfer.bankTransactionReference.trim() || !transfer.proofReference.trim()}>Record transfer</button>
            </form>
          )}
          {refund.status === 'CLOSED_UNREACHABLE' && isManager && (
            <button className="button outline" type="button" disabled={command.pending} onClick={() => transition({ action: 'REOPEN' }, 'Refund reopened with a fresh account link.')}>Reopen</button>
          )}
          {refund.status === 'REFUNDED' && <p className="muted">Refunded. Only the masked account remains on file.</p>}
        </section>
      )}
      <ReasonDialog open={closing} title="Close this refund as unreachable" confirmLabel="Close refund" pending={command.pending} onCancel={() => setClosing(false)} onConfirm={closeUnreachable}>
        <p>Allowed only after 30 days without an account. The refund can be reopened later.</p>
      </ReasonDialog>
    </OpsPage>
  );
}
