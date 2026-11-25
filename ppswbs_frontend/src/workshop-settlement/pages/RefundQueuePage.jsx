import React, { useEffect, useMemo, useRef, useState } from 'react';
import { useUrlFilters } from '../../shared/ui/useUrlFilters';
import { newCommandId } from '../../shared/api/http';
import { useHttp } from '../../shared/api/useHttp';
import { useRole } from '../../shared/auth/useRole';
import { Loading, SuccessNotice, useCommand, useResource } from '../../shared/ui/components';
import { createSettlementApi } from '../api/settlementApi';
import { OpsPage } from '../OpsPage';
import { useEnumLabel, useFormat, useT } from '../../shared/i18n/I18nProvider';
import { messages } from '../messages';
import { AccountReviewStep, ProblemNotice, ReasonDialog, asList } from '../ui';

const RETURNABLE = ['PENDING_APPROVAL', 'PENDING_TRANSFER', 'PENDING_RECONCILIATION'];
const RECEIPT_TYPES = ['image/jpeg', 'image/png', 'image/webp'];
const RECEIPT_MAX_BYTES = 5000000;
const RECEIPT_PROBLEMS = ['RECEIPT_IMAGE_TYPE_UNSUPPORTED', 'RECEIPT_IMAGE_TOO_LARGE', 'RECEIPT_STORAGE_UNAVAILABLE'];
const STATUSES = ['PENDING_ACCOUNT', 'PENDING_APPROVAL', 'PENDING_TRANSFER', 'PENDING_RECONCILIATION', 'REFUNDED', 'CLOSED_UNREACHABLE'];

/**
 * Bank refund queue and lifecycle (FR-038–FR-040). The status, amount and masked account are the server's; a step is only
 * offered for the status that allows it, and the server still refuses anything else. The full account number is shown on
 * request to a Manager, kept in memory only and never stored or logged.
 */
export default function RefundQueuePage() {
  const t = useT(messages);
  const fmt = useFormat();
  const statusLabel = useEnumLabel(messages, 'refundStatus');
  const sourceLabel = useEnumLabel(messages, 'refundSource');
  const api = createSettlementApi(useHttp());
  const role = useRole();
  const isManager = role === 'MANAGER' || role === 'ADMIN';
  const isStaff = role === 'STAFF';
  const [{ status }, setUrlFilters] = useUrlFilters({ status: '' });
  const setStatus = value => setUrlFilters({ status: value });
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
  const queue = useResource(() => api.listRefunds(status === 'WAITING' ? { waiting: true } : status ? { status } : undefined), [status]);
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
    }, key), t('refund.msg.accountRecorded'));
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
    if (file && !RECEIPT_TYPES.includes(file.type)) { setReceiptFile(null); setReceiptError(t('refund.problem.RECEIPT_IMAGE_TYPE_UNSUPPORTED')); event.target.value = ''; return; }
    if (file && file.size > RECEIPT_MAX_BYTES) { setReceiptFile(null); setReceiptError(t('refund.problem.RECEIPT_IMAGE_TOO_LARGE')); event.target.value = ''; return; }
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
    }, t('refund.msg.transferRecorded'));
    if (out.ok) { setTransfer({ bankTransactionReference: '', proofReference: '' }); setReceiptFile(null); uploaded.current = null; }
  };

  const viewReceipt = async () => {
    const out = await command.run(() => api.getReceiptImage(refund.refundId));
    if (out.ok && out.result?.blob) setReceiptUrl(URL.createObjectURL(out.result.blob));
  };

  const returnToAccount = async reason => {
    const out = await transition({ action: 'RETURN_TO_PENDING_ACCOUNT', reason }, t('refund.msg.returned'));
    if (out.ok) setReturning(false);
  };

  const closeUnreachable = async reason => {
    const out = await transition({ action: 'CLOSE_UNREACHABLE', reason }, t('refund.msg.closed'));
    if (out.ok) setClosing(false);
  };

  return (
    <OpsPage title={t('refund.title')} intro={isStaff ? t('refund.staffIntro') : t('refund.intro')}>
      <SuccessNotice>{message}</SuccessNotice>
      {RECEIPT_PROBLEMS.includes(command.error?.code)
        ? <div className="notice error" role="alert"><p>{t(`refund.problem.${command.error.code}`)}</p>{command.error.retryable && <div className="notice-actions"><button type="button" className="button outline" onClick={command.retry}>{t('refund.retry')}</button></div>}</div>
        : <ProblemNotice error={queue.error || command.error} onRetry={command.retry} onReload={queue.reload} />}
      <section className="ota-panel">
        <div className="ota-inline-form">
          <label>{t('refund.status')}
            <select value={status} onChange={event => { setStatus(event.target.value); setSelected(null); }}>
              <option value="">{t('refund.all')}</option>
              <option value="WAITING">{t('refund.waiting')}</option>
              {STATUSES.map(value => <option key={value} value={value}>{statusLabel(value)}</option>)}
            </select>
          </label>
          <button className="button secondary" type="button" onClick={() => queue.reload()}>{t('refund.refresh')}</button>
        </div>
        {queue.loading ? <Loading /> : queue.error ? null : refunds.length === 0 ? <div className="empty-state"><p>{t('refund.empty')}</p></div> : (
          <table className="ops-table">
            <thead><tr><th>{t('refund.col.refund')}</th><th>{t('refund.col.ticket')}</th><th>{t('refund.col.reason')}</th><th>{t('refund.col.amount')}</th><th>{t('refund.col.status')}</th><th>{t('refund.col.account')}</th></tr></thead>
            <tbody>
              {refunds.map(item => (
                <tr key={item.refundId} className={selected === item.refundId ? 'selected' : ''} style={{ cursor: 'pointer' }} onClick={() => { setSelected(item.refundId); setRevealed(null); }}>
                  <td>{item.refundId}</td>
                  <td>{item.ticketCode || item.groupCode || '—'}</td>
                  <td>{sourceLabel(item.source)}</td>
                  <td>{fmt.money(item.amount)}</td>
                  <td><span className="ops-status">{statusLabel(item.status)}</span></td>
                  <td>{item.accountNumberMasked ? `${item.accountNumberMasked}${item.holderName ? ` · ${item.holderName}` : ''}` : '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>

      {refund && (
        <section className="ota-panel" aria-label={t('refund.actionsAria')}>
          <div className="ota-panel-head"><h2>{refund.refundId}</h2><span className="ops-status">{statusLabel(refund.status)}</span></div>
          {refund.status === 'PENDING_ACCOUNT' && (<>
            <p className="muted">{t('refund.pendingAccountNote')}</p>
            {reviewing ? (
              <AccountReviewStep details={account} confirmed={confirmed} onConfirmedChange={setConfirmed} onEdit={() => { setReviewing(false); setConfirmed(false); }}
                pending={command.pending} submitLabel={t('refund.saveAccount')} onSubmit={enterAccount} />
            ) : (
            <form className="ota-form" aria-label={t('refund.counterAccountAria')} onSubmit={reviewAccount}>
              <label>{t('refund.bankCode')}<input value={account.bankCode} maxLength={32} onChange={event => setAccount(v => ({ ...v, bankCode: event.target.value }))} /></label>
              <label>{t('refund.accountNumber')}<input inputMode="numeric" autoComplete="off" value={account.accountNumber} maxLength={32} onChange={event => setAccount(v => ({ ...v, accountNumber: event.target.value }))} /></label>
              <label>{t('refund.holderLabel')}<input autoComplete="off" value={account.holderName} maxLength={128} onChange={event => setAccount(v => ({ ...v, holderName: event.target.value }))} /></label>
              <button className="button primary" type="submit" disabled={command.pending || !account.bankCode.trim() || !account.accountNumber.trim() || !account.holderName.trim()}>{t('refund.reviewDetails')}</button>
            </form>
            )}
            <div className="ops-actions">
              <button className="button outline" type="button" disabled={command.pending} onClick={() => act(key => api.resendAccountLink(refund.refundId, key), t('refund.msg.linkSent'))}>{t('refund.resendLink')}</button>
              {isManager && <button className="button danger" type="button" onClick={() => setClosing(true)}>{t('refund.closeUnreachable')}</button>}
            </div>
          </>)}
          {refund.status !== 'PENDING_ACCOUNT' && refund.status !== 'REFUNDED' && refund.status !== 'CLOSED_UNREACHABLE' && isManager && (
            <div className="ops-actions">
              <button className="button outline" type="button" disabled={command.pending} onClick={reveal}>{t('refund.showAccount')}</button>
              {RETURNABLE.includes(refund.status) && <button className="button outline" type="button" disabled={command.pending} onClick={() => setReturning(true)}>{t('refund.returnToAccount')}</button>}
              {refund.status === 'PENDING_APPROVAL' && <button className="button primary" type="button" disabled={command.pending} onClick={() => transition({ action: 'APPROVE' }, t('refund.msg.approved'))}>{t('refund.approve')}</button>}
              {refund.status === 'PENDING_RECONCILIATION' && <button className="button primary" type="button" disabled={command.pending} onClick={() => transition({ action: 'CONFIRM_REFUNDED' }, t('refund.msg.refunded'))}>{t('refund.confirmRefunded')}</button>}
            </div>
          )}
          {revealed && (
            <dl className="ops-facts" aria-label={t('refund.recipientAria')}>
              <div><dt>{t('refund.bank')}</dt><dd>{revealed.bankCode || '—'}</dd></div>
              <div><dt>{t('refund.accountNumberFull')}</dt><dd>{revealed.accountNumber}</dd></div>
              <div><dt>{t('refund.holder')}</dt><dd>{revealed.holderName || '—'}</dd></div>
              <div><dt /><dd><button type="button" className="button outline" onClick={() => setRevealed(null)}>{t('refund.hide')}</button></dd></div>
            </dl>
          )}
          {refund.status === 'PENDING_TRANSFER' && isManager && (
            <form className="ota-form" aria-label={t('refund.recordTransferAria')} onSubmit={recordTransfer}>
              <label>{t('refund.bankTxnRef')}<input value={transfer.bankTransactionReference} maxLength={128} onChange={event => setTransfer(v => ({ ...v, bankTransactionReference: event.target.value }))} /></label>
              <label>{t('refund.noteOptional')}<input value={transfer.proofReference} maxLength={256} onChange={event => setTransfer(v => ({ ...v, proofReference: event.target.value }))} /></label>
              <label>{t('refund.receiptLabel')}
                <input type="file" accept="image/jpeg,image/png,image/webp" onChange={chooseReceipt} />
              </label>
              {receiptFile && <p className="muted small">{receiptFile.name} <button type="button" className="button outline" onClick={() => { setReceiptFile(null); uploaded.current = null; }}>{t('refund.removeImage')}</button></p>}
              {receiptError && <p className="field-error" role="alert">{receiptError}</p>}
              <button className="button primary" type="submit" disabled={command.pending || !transfer.bankTransactionReference.trim()}>{t('refund.recordTransfer')}</button>
            </form>
          )}
          {refund.status === 'CLOSED_UNREACHABLE' && isManager && (
            <button className="button outline" type="button" disabled={command.pending} onClick={() => transition({ action: 'REOPEN' }, t('refund.msg.reopened'))}>{t('refund.reopen')}</button>
          )}
          {refund.receiptImageAttached && isManager && (
            <div className="ops-actions">
              {receiptUrl
                ? <button type="button" className="button outline" onClick={hideReceipt}>{t('refund.hideReceipt')}</button>
                : <button type="button" className="button outline" disabled={command.pending} onClick={viewReceipt}>{t('refund.viewReceipt')}</button>}
            </div>
          )}
          {receiptUrl && <img className="settlement-receipt" src={receiptUrl} alt={t('refund.receiptAlt', { id: refund.refundId })} style={{ maxWidth: '100%', maxHeight: 480 }} />}
          {refund.status === 'REFUNDED' && <p className="muted">{t('refund.refundedNote')}</p>}
        </section>
      )}
      <ReasonDialog open={returning} title={t('refund.returnTitle')} confirmLabel={t('refund.returnToAccount')} pending={command.pending} onCancel={() => setReturning(false)} onConfirm={returnToAccount}>
        <p>{t('refund.returnBody')}</p>
      </ReasonDialog>
      <ReasonDialog open={closing} title={t('refund.closeTitle')} confirmLabel={t('refund.closeConfirm')} pending={command.pending} onCancel={() => setClosing(false)} onConfirm={closeUnreachable}>
        <p>{t('refund.closeBody')}</p>
      </ReasonDialog>
    </OpsPage>
  );
}
