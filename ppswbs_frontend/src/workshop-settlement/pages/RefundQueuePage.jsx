import React, { useEffect, useMemo, useRef, useState } from 'react';
import { newCommandId } from '../../shared/api/http';
import { useHttp } from '../../shared/api/useHttp';
import { useRole } from '../../shared/auth/useRole';
import { Loading, SuccessNotice, useCommand, useResource } from '../../shared/ui/components';
import { createSettlementApi } from '../api/settlementApi';
import { OpsPage } from '../OpsPage';
import { AccountReviewStep, ProblemNotice, ReasonDialog, asList, money } from '../ui';

const RETURNABLE = ['PENDING_APPROVAL', 'PENDING_TRANSFER', 'PENDING_RECONCILIATION'];
const RECEIPT_TYPES = ['image/jpeg', 'image/png', 'image/webp'];
const RECEIPT_MAX_BYTES = 5000000;
const RECEIPT_PROBLEMS = {
  RECEIPT_IMAGE_TYPE_UNSUPPORTED: 'The receipt must be a JPEG, PNG or WebP image. Choose another file, or remove it and record the transfer with the bank reference only.',
  RECEIPT_IMAGE_TOO_LARGE: 'The receipt image is larger than 5,000,000 bytes. Choose a smaller image, or remove it and record the transfer with the bank reference only.',
  RECEIPT_STORAGE_UNAVAILABLE: 'Receipt image storage is not available. Remove the image to record the transfer with the bank reference only, or try again later.',
};
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
  const [receiptFile, setReceiptFile] = useState(null);
  const [receiptError, setReceiptError] = useState('');
  const uploaded = useRef(null); // { file, id, key }: the upload of this attempt, reused when only the transition failed
  const [reviewing, setReviewing] = useState(false);
  const [confirmed, setConfirmed] = useState(false);
  const [returning, setReturning] = useState(false);
  const [receiptUrl, setReceiptUrl] = useState(null);
  const [revealed, setRevealed] = useState(null);
  const [closing, setClosing] = useState(false);
  const [message, setMessage] = useState('');
  const command = useCommand();
  const queue = useResource(() => api.listRefunds(status ? { status } : undefined), [status]);
  const refunds = useMemo(() => asList(queue.data), [queue.data]);
  const refund = refunds.find(item => item.refundId === selected) || null;
  const hideReceipt = () => setReceiptUrl(current => { if (current) URL.revokeObjectURL(current); return null; });
  useEffect(() => () => { hideReceipt(); }, [selected]); // eslint-disable-line react-hooks/exhaustive-deps
  useEffect(() => { setReviewing(false); setConfirmed(false); setReceiptFile(null); setReceiptError(''); uploaded.current = null; }, [selected]);

  const act = async (send, success) => {
    setMessage('');
    const out = await command.run(send);
    if (out.ok) { setMessage(success); setRevealed(null); queue.reload(); }
    return out;
  };
  const transition = (body, success) => act(key => api.transitionRefund(refund.refundId, body, key), success);

  const reviewAccount = event => { event.preventDefault(); setConfirmed(false); setReviewing(true); };
  const enterAccount = async () => {
    const out = await act(key => api.putRefundAccount(refund.refundId, {
      bankCode: account.bankCode.trim(), accountNumber: account.accountNumber.trim(), holderName: account.holderName.trim(),
      accountDetailsConfirmed: confirmed,
    }, key), 'Account recorded.');
    if (out.ok) { setAccount({ bankCode: '', accountNumber: '', holderName: '' }); setReviewing(false); setConfirmed(false); }
  };

  const reveal = async () => {
    const out = await command.run(() => api.getRefundAccountNumber(refund.refundId));
    if (out.ok) setRevealed(out.result);
  };

  const chooseReceipt = event => {
    const file = event.target.files?.[0] || null;
    setReceiptError('');
    uploaded.current = null;
    if (file && !RECEIPT_TYPES.includes(file.type)) { setReceiptFile(null); setReceiptError(RECEIPT_PROBLEMS.RECEIPT_IMAGE_TYPE_UNSUPPORTED); event.target.value = ''; return; }
    if (file && file.size > RECEIPT_MAX_BYTES) { setReceiptFile(null); setReceiptError(RECEIPT_PROBLEMS.RECEIPT_IMAGE_TOO_LARGE); event.target.value = ''; return; }
    setReceiptFile(file);
  };
  // T154: the optional image is uploaded first (one key per chosen file), then attached by RECORD_TRANSFER; a retry after a
  // lost response reuses the finished upload and the transition key.
  const recordTransfer = async event => {
    event.preventDefault();
    const out = await act(async key => {
      let imageId = null;
      if (receiptFile) {
        if (!uploaded.current || uploaded.current.file !== receiptFile) uploaded.current = { file: receiptFile, id: null, key: newCommandId() };
        if (!uploaded.current.id) uploaded.current.id = (await api.uploadReceiptImage(refund.refundId, receiptFile, uploaded.current.key)).receiptImageId;
        imageId = uploaded.current.id;
      }
      const body = { action: 'RECORD_TRANSFER', bankTransactionReference: transfer.bankTransactionReference.trim() };
      if (transfer.proofReference.trim()) body.proofReference = transfer.proofReference.trim();
      if (imageId) body.proofImageReference = imageId;
      return api.transitionRefund(refund.refundId, body, key);
    }, 'Transfer recorded.');
    if (out.ok) { setTransfer({ bankTransactionReference: '', proofReference: '' }); setReceiptFile(null); uploaded.current = null; }
  };

  const viewReceipt = async () => {
    const out = await command.run(() => api.getReceiptImage(refund.refundId));
    if (out.ok && out.result?.blob) setReceiptUrl(URL.createObjectURL(out.result.blob));
  };

  const returnToAccount = async reason => {
    const out = await transition({ action: 'RETURN_TO_PENDING_ACCOUNT', reason }, 'Returned to account entry. The earlier approval no longer applies and a new account link was sent to the payer.');
    if (out.ok) setReturning(false);
  };

  const closeUnreachable = async reason => {
    const out = await transition({ action: 'CLOSE_UNREACHABLE', reason }, 'Refund closed as unreachable.');
    if (out.ok) setClosing(false);
  };

  return (
    <OpsPage eyebrow="BANK REFUNDS" title="Refund queue" intro="Every Mland refund is a bank transfer. A refund counts as done only when a Manager confirms REFUNDED.">
      <SuccessNotice>{message}</SuccessNotice>
      {RECEIPT_PROBLEMS[command.error?.code]
        ? <div className="notice error" role="alert"><p>{RECEIPT_PROBLEMS[command.error.code]}</p>{command.error.retryable && <div className="notice-actions"><button type="button" className="button outline" onClick={command.retry}>Try again</button></div>}</div>
        : <ProblemNotice error={queue.error || command.error} onRetry={command.retry} onReload={queue.reload} />}
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
            {reviewing ? (
              <AccountReviewStep details={account} confirmed={confirmed} onConfirmedChange={setConfirmed} onEdit={() => { setReviewing(false); setConfirmed(false); }}
                pending={command.pending} submitLabel="Save account" onSubmit={enterAccount} />
            ) : (
            <form className="ota-form" aria-label="Counter account" onSubmit={reviewAccount}>
              <label>Bank code<input value={account.bankCode} maxLength={32} onChange={event => setAccount(v => ({ ...v, bankCode: event.target.value }))} /></label>
              <label>Account number<input inputMode="numeric" autoComplete="off" value={account.accountNumber} maxLength={32} onChange={event => setAccount(v => ({ ...v, accountNumber: event.target.value }))} /></label>
              <label>Holder name (as the bank shows it)<input autoComplete="off" value={account.holderName} maxLength={128} onChange={event => setAccount(v => ({ ...v, holderName: event.target.value }))} /></label>
              <button className="button primary" type="submit" disabled={command.pending || !account.bankCode.trim() || !account.accountNumber.trim() || !account.holderName.trim()}>Review details</button>
            </form>
            )}
            <div className="ops-actions">
              <button className="button outline" type="button" disabled={command.pending} onClick={() => act(key => api.resendAccountLink(refund.refundId, key), 'A fresh account link was sent; the previous link no longer works.')}>Resend account link</button>
              {isManager && <button className="button danger" type="button" onClick={() => setClosing(true)}>Close as unreachable</button>}
            </div>
          </>)}
          {refund.status !== 'PENDING_ACCOUNT' && refund.status !== 'REFUNDED' && refund.status !== 'CLOSED_UNREACHABLE' && isManager && (
            <div className="ops-actions">
              <button className="button outline" type="button" disabled={command.pending} onClick={reveal}>Show full account number</button>
              {RETURNABLE.includes(refund.status) && <button className="button outline" type="button" disabled={command.pending} onClick={() => setReturning(true)}>Return to account entry</button>}
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
              <label>Bank transaction reference<input value={transfer.bankTransactionReference} maxLength={128} onChange={event => setTransfer(v => ({ ...v, bankTransactionReference: event.target.value }))} /></label>
              <label>Note (optional)<input value={transfer.proofReference} maxLength={256} onChange={event => setTransfer(v => ({ ...v, proofReference: event.target.value }))} /></label>
              <label>Receipt image (optional, JPEG, PNG or WebP up to 5,000,000 bytes)
                <input type="file" accept="image/jpeg,image/png,image/webp" onChange={chooseReceipt} />
              </label>
              {receiptFile && <p className="muted small">{receiptFile.name} <button type="button" className="button outline" onClick={() => { setReceiptFile(null); uploaded.current = null; }}>Remove image</button></p>}
              {receiptError && <p className="field-error" role="alert">{receiptError}</p>}
              <button className="button primary" type="submit" disabled={command.pending || !transfer.bankTransactionReference.trim()}>Record transfer</button>
            </form>
          )}
          {refund.status === 'CLOSED_UNREACHABLE' && isManager && (
            <button className="button outline" type="button" disabled={command.pending} onClick={() => transition({ action: 'REOPEN' }, 'Refund reopened with a fresh account link.')}>Reopen</button>
          )}
          {refund.receiptImageAttached && isManager && (
            <div className="ops-actions">
              {receiptUrl
                ? <button type="button" className="button outline" onClick={hideReceipt}>Hide receipt image</button>
                : <button type="button" className="button outline" disabled={command.pending} onClick={viewReceipt}>View receipt image</button>}
            </div>
          )}
          {receiptUrl && <img className="settlement-receipt" src={receiptUrl} alt={`Transfer receipt for ${refund.refundId}`} style={{ maxWidth: '100%', maxHeight: 480 }} />}
          {refund.status === 'REFUNDED' && <p className="muted">Refunded. Only the masked account remains on file.</p>}
        </section>
      )}
      <ReasonDialog open={returning} title="Return to account entry" confirmLabel="Return to account entry" pending={command.pending} onCancel={() => setReturning(false)} onConfirm={returnToAccount}>
        <p>The payer’s account is cleared (only the mask stays), the earlier approval no longer applies and a new account link is sent. A Manager approves again after the payer enters the account.</p>
      </ReasonDialog>
      <ReasonDialog open={closing} title="Close this refund as unreachable" confirmLabel="Close refund" pending={command.pending} onCancel={() => setClosing(false)} onConfirm={closeUnreachable}>
        <p>Allowed only after 30 days without an account. The refund can be reopened later.</p>
      </ReasonDialog>
    </OpsPage>
  );
}
