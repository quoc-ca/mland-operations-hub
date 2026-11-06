import React, { useState } from 'react';
import { Link } from 'react-router';
import { useAuth } from '../../shared/auth/AuthProvider';
import { useHttp } from '../../shared/api/useHttp';
import { ErrorNotice, Loading, SuccessNotice, useCommand, useResource } from '../../shared/ui/components';
import { OperationsSidebar } from '../../shared/ui/OperationsSidebar';
import { OperationsAuthWorkspace } from '../../shared/ui/OperationsAuthWorkspace';
import { classifyPaymentCommandError, finalPaymentRecoveryState } from '../../shared/payment/paymentRecoveryState';
import { createSettlementApi } from '../api/settlementApi';
import '../../ota-connectivity/ota-connectivity.css';
import '../workshop-settlement.css';

const amountOf = value => value?.amount ?? value ?? 0;

export default function SettlementWorkspacePage() {
  const { status } = useAuth();
  const api = createSettlementApi(useHttp());
  const [bookingCode, setBookingCode] = useState('');
  const [activeCode, setActiveCode] = useState('');
  const [description, setDescription] = useState('');
  const [amount, setAmount] = useState('');
  const [selectedAddOn, setSelectedAddOn] = useState('');
  const [customerAgreed, setCustomerAgreed] = useState(false);
  const [variableAgreed, setVariableAgreed] = useState(false);
  const [contactEmail, setContactEmail] = useState('');
  const [remedyReason, setRemedyReason] = useState('');
  const [remedyOutcome, setRemedyOutcome] = useState('UNAVAILABLE');
  const [message, setMessage] = useState('');
  const [attempt, setAttempt] = useState(null);
  const [paymentActionError, setPaymentActionError] = useState(null);
  const command = useCommand();
  const paymentCommand = useCommand();
  const resource = useResource(
    () => activeCode ? api.getFinalBill(activeCode) : Promise.resolve(null),
    [activeCode],
  );
  // Fixed-price catalogue add-ons are offered only while the folio is OPEN (a finalized folio accepts none).
  const billOpen = resource.data?.status === 'OPEN';
  const addOnResource = useResource(
    () => activeCode && billOpen ? api.listAddOns(activeCode) : Promise.resolve([]),
    [activeCode, billOpen],
  );
  const addOnOptions = addOnResource.data || [];

  if (status === 'loading') return <Loading />;
  if (status !== 'signed-in') {
    return (
      <OperationsAuthWorkspace
        topline="MLAND / WORKSHOP SETTLEMENT"
        message="Authentication required to access settlement."
      />
    );
  }

  const bill = resource.data;
  const paymentState = finalPaymentRecoveryState(attempt);
  const canActOnPayment = Number(amountOf(bill?.outstandingAmount)) > 0
    && !paymentState.actionDisabled
    && paymentActionError?.kind !== 'UNKNOWN'
    && paymentActionError?.kind !== 'EXPIRED'
    && !paymentCommand.pending;

  async function run(action, success, onSuccess) {
    setMessage('');
    const result = await command.run(key => action(key));
    if (result.ok) {
      setMessage(success);
      onSuccess?.(result.result);
      resource.reload();
    }
    return result;
  }

  async function runPaymentRecovery() {
    setPaymentActionError(null);
    setMessage('');
    const result = await paymentCommand.run(key => api.startPayment(activeCode, key));
    if (result.ok) {
      setAttempt(result.result);
      setMessage(attempt ? 'Payment attempt reconciled.' : 'Payment attempt created.');
      resource.reload();
      return;
    }
    setPaymentActionError(classifyPaymentCommandError(result.error));
  }

  async function retryPaymentStatusCheck() {
    setPaymentActionError(null);
    setMessage('');
    const result = await paymentCommand.retry();
    if (result.ok) {
      setAttempt(result.result);
      setMessage('Payment status checked again with the original request key.');
      resource.reload();
      return;
    }
    setPaymentActionError(classifyPaymentCommandError(result.error));
  }

  function openBill(event) {
    event.preventDefault();
    setActiveCode(bookingCode.trim());
    setAttempt(null);
    setPaymentActionError(null);
  }

  return (
    <div className="ota-workspace">
      <OperationsSidebar />
      <main className="ota-main">
        <div className="ota-topline">MLAND / WORKSHOP SETTLEMENT <span>● Backend-authorized</span></div>
        <header className="ota-page-heading">
          <div>
            <p className="ota-eyebrow">CUSTOMER BILLING</p>
            <h1>Final bill workspace</h1>
            <p>Review charges, finalize the bill, and safely recover an expired or unknown payment attempt.</p>
          </div>
        </header>
        <SuccessNotice>{message}</SuccessNotice>
        <ErrorNotice error={resource.error || command.error} onReload={resource.reload} />
        <section className="ota-panel">
          <form className="ota-inline-form" onSubmit={openBill}>
            <label>Booking code
              <input value={bookingCode} onChange={event => setBookingCode(event.target.value)} placeholder="BK-..." />
            </label>
            <button className="button primary" type="submit" disabled={!bookingCode.trim()}>Open bill</button>
          </form>
        </section>

        {resource.loading && activeCode ? <Loading /> : null}
        {activeCode && bill ? (
          <section className="settlement-grid">
            <article className="ota-panel settlement-summary">
              <div className="ota-panel-head">
                <h2>{bill.bookingCode || activeCode}</h2>
                <span className="ops-status">{bill.status || 'OPEN'}</span>
              </div>
              <div className="settlement-amount">
                <span>Outstanding</span>
                <strong>{amountOf(bill.outstandingAmount)} {bill.currency || 'VND'}</strong>
              </div>
              <div className="settlement-amounts">
                <div><span>Paid</span><strong>{amountOf(bill.paidAmount)} VND</strong></div>
                <div><span>Refunded</span><strong>{amountOf(bill.refundedAmount)} VND</strong></div>
              </div>
              <div className="ops-actions">
                <button className="button outline" disabled={command.pending} onClick={() => run(key => api.createFinalBill(activeCode, {}, key), 'Folio loaded.')}>Load folio</button>
                <button className="button primary" disabled={command.pending} onClick={() => run(key => api.finalize(activeCode, key), 'Bill finalized.')}>Finalize bill</button>
                <button className="button primary" disabled={!canActOnPayment} onClick={runPaymentRecovery}>
                  {paymentCommand.pending ? 'Checking payment status...' : paymentState.actionLabel}
                </button>
              </div>
              <div className={`settlement-payment-state ${paymentState.kind.toLowerCase()}`} role="status">
                <strong>{paymentState.title}</strong>
                <p>{paymentState.message}</p>
                <small>Payment links are never reissued. Recovery reconciles the original attempt before the backend may create a new one.</small>
              </div>
              {paymentActionError ? (
                <div className={`settlement-payment-state ${paymentActionError.kind.toLowerCase()}`} role="alert">
                  <strong>{paymentActionError.kind === 'UNKNOWN' ? 'Payment result unknown' : 'Payment action failed'}</strong>
                  <p>{paymentActionError.message}</p>
                  {paymentActionError.kind === 'UNKNOWN' ? (
                    <button className="button outline" type="button" disabled={paymentCommand.pending} onClick={retryPaymentStatusCheck}>
                      {paymentCommand.pending ? 'Checking...' : 'Retry status check with same request'}
                    </button>
                  ) : null}
                </div>
              ) : null}
              {attempt ? (
                <div className="settlement-payment-result">
                  <div className="ota-panel-head"><h3>Final payment result</h3><span className="ops-status">{attempt.status}</span></div>
                  <dl className="ops-facts">
                    <div><dt>Amount</dt><dd>{attempt.amountVnd} {attempt.currency}</dd></div>
                    <div><dt>Expires</dt><dd>{attempt.expiresAt ? new Date(attempt.expiresAt).toLocaleString() : 'Not provided'}</dd></div>
                    <div><dt>Attempt</dt><dd><code>{attempt.attemptReference}</code></dd></div>
                  </dl>
                  {attempt.paymentUrl && paymentState.kind === 'PENDING' ? (
                    <a className="button primary" href={attempt.paymentUrl} target="_blank" rel="noreferrer">Open active payment link</a>
                  ) : (
                    <p className="muted">No active link is available. Follow the recovery state above; do not create a duplicate provider transaction.</p>
                  )}
                </div>
              ) : null}
            </article>

            <article className="ota-panel">
              <div className="ota-panel-head"><h2>Charges</h2></div>
              {(bill.charges || []).length === 0 ? <p className="muted">No charges recorded.</p> : (
                <div className="ota-ledger">
                  {bill.charges.map((charge, index) => (
                    <div className="ota-ledger-row" key={charge.reference || index}>
                      <span>{charge.description || 'Charge'}</span>
                      <strong>{amountOf(charge.amount)} VND</strong>
                      <code>{charge.reference || '—'}</code>
                    </div>
                  ))}
                </div>
              )}
              {bill.status === 'OPEN' && (
                <form className="ota-form settlement-form" aria-label="Catalogue add-on" onSubmit={event => {
                  event.preventDefault();
                  run(key => api.addCharge(activeCode, { catalogueAddOnReference: selectedAddOn, customerConsentConfirmed: true }, key),
                    'Add-on added.', () => { setSelectedAddOn(''); setCustomerAgreed(false); });
                }}>
                  <h3>Add-on from catalogue</h3>
                  {addOnResource.loading ? <p className="muted">Loading add-ons…</p>
                    : addOnResource.error ? <p className="muted" role="status">Could not load the add-on catalogue for this booking.</p>
                    : addOnOptions.length === 0 ? <p className="muted">No add-on is available for this package at this location.</p>
                      : (
                        <fieldset className="settlement-addon-options">
                          <legend className="muted">Tick the add-on the customer agreed to at the workshop.</legend>
                          {addOnOptions.map(option => (
                            <label key={option.addOnReference} className="settlement-addon-option">
                              <input type="radio" name="catalogue-add-on" value={option.addOnReference}
                                checked={selectedAddOn === option.addOnReference}
                                onChange={() => setSelectedAddOn(option.addOnReference)} />
                              <span>{option.description}</span>
                              <strong>{option.amountVnd} VND</strong>
                            </label>
                          ))}
                        </fieldset>
                      )}
                  <label className="settlement-addon-consent">
                    <input type="checkbox" checked={customerAgreed} onChange={event => setCustomerAgreed(event.target.checked)} />
                    <span>Khách đã đồng ý (customer agreed)</span>
                  </label>
                  <p className="muted small">The system records who confirmed this and when. It evidences your confirmation only, not the customer&apos;s own consent.</p>
                  <button className="button primary" type="submit" disabled={command.pending || !selectedAddOn || !customerAgreed}>Add selected add-on</button>
                </form>
              )}
              <form className="ota-form settlement-form" aria-label="Variable charge" onSubmit={event => {
                event.preventDefault();
                run(key => api.addCharge(activeCode, {
                  description,
                  amount: Number(amount),
                  reason: 'WORKSHOP_FINAL_BILL',
                  customerConsentConfirmed: true,
                }, key), 'Charge added.', () => setVariableAgreed(false));
              }}>
                <h3>Variable charge</h3>
                <p className="muted small">Free-form charge confirmed by Staff/Manager, not a catalogue add-on.</p>
                <label>Description<input value={description} onChange={event => setDescription(event.target.value)} /></label>
                <label>Amount (VND)<input inputMode="decimal" value={amount} onChange={event => setAmount(event.target.value)} /></label>
                <label className="settlement-addon-consent">
                  <input type="checkbox" checked={variableAgreed} onChange={event => setVariableAgreed(event.target.checked)} />
                  <span>Khách đã đồng ý (customer agreed)</span>
                </label>
                <button className="button outline" type="submit" disabled={command.pending || !description.trim() || !amount || !variableAgreed}>Add charge</button>
              </form>
            </article>

            <article className="ota-panel">
              <div className="ota-panel-head"><h2>Customer cancellation</h2><span className="muted">No automatic refund</span></div>
              <p className="muted">Cancellation is recorded and handed to Operations. This screen never promises a refund.</p>
              <form className="ota-form" onSubmit={event => {
                event.preventDefault();
                run(key => api.openCancellation(activeCode, { contactEmail: contactEmail || undefined }, key), 'Cancellation case opened.');
              }}>
                <label>Contact email (if guest)<input type="email" value={contactEmail} onChange={event => setContactEmail(event.target.value)} /></label>
                <button className="button danger" type="submit" disabled={command.pending}>Open cancellation case</button>
              </form>
            </article>

            <article className="ota-panel">
              <div className="ota-panel-head"><h2>Mland-caused remedy</h2><span className="muted">Manager only</span></div>
              <p className="muted">Use only after Operations confirms an Mland-caused cancellation. This opens the remedy case.</p>
              <form className="ota-form" onSubmit={event => {
                event.preventDefault();
                if (remedyReason.trim()) {
                  run(key => api.openMlandRemedy(activeCode, {
                    reason: remedyReason,
                    replacementOutcome: remedyOutcome,
                    cancellationHandoffReference: `HANDOFF-${activeCode}`,
                  }, key), 'Mland remedy case opened.');
                }
              }}>
                <label>Reason<textarea value={remedyReason} onChange={event => setRemedyReason(event.target.value)} /></label>
                <label>Replacement outcome
                  <select value={remedyOutcome} onChange={event => setRemedyOutcome(event.target.value)}>
                    <option value="UNAVAILABLE">Unavailable</option>
                    <option value="DECLINED">Customer declined</option>
                  </select>
                </label>
                <button className="button danger" type="submit" disabled={command.pending || !remedyReason.trim()}>Open remedy case</button>
              </form>
            </article>
          </section>
        ) : null}
        {activeCode && !resource.loading && !bill ? <div className="empty-state"><p>No bill was found for this booking.</p></div> : null}
      </main>
    </div>
  );
}
