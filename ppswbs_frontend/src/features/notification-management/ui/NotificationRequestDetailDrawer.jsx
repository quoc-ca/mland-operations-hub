import React, { useEffect, useState } from 'react';
import { useNotificationApi } from '../application/useNotificationApi';
import { useNotificationOperations } from '../application/useNotificationOperations';
import ResendConfirmationModal from './ResendConfirmationModal';
import CancelConfirmationModal from './CancelConfirmationModal';
import RedeliverConfirmationModal from './RedeliverConfirmationModal';
import { useT, useFormat } from '../../../shared/i18n/I18nProvider';
import { sharedMessages } from '../../../shared/ui/messages';
import { notificationMessages } from '../messages';

function stateBadgeClass(state) {
  if (!state) return 'nm-badge-pending';
  return `nm-badge-${state.toLowerCase()}`;
}

export function NotificationRequestDetailDrawer({
  isOpen,
  onClose,
  requestId,
  userRole = 'MANAGER',
  onActionSuccess,
}) {
  const t = useT(notificationMessages);
  const ts = useT(sharedMessages);
  const fmt = useFormat();
  const api = useNotificationApi();
  const { resend, cancel, redeliver } = useNotificationOperations();

  const [detail, setDetail] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [feedback, setFeedback] = useState(null);

  // Modals state
  const [resendOpen, setResendOpen] = useState(false);
  const [cancelOpen, setCancelOpen] = useState(false);
  const [redeliverOpen, setRedeliverOpen] = useState(false);

  const fetchDetail = async () => {
    if (!requestId) return;
    setLoading(true);
    setError(null);
    try {
      const data = await api.getNotificationRequest(requestId);
      setDetail(data);
    } catch (err) {
      setError(err?.message || t('drawer.loadError'));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (isOpen && requestId) {
      setFeedback(null);
      fetchDetail();
    } else {
      setDetail(null);
    }
  }, [isOpen, requestId]);

  useEffect(() => {
    function handleKeyDown(e) {
      if (e.key === 'Escape' && isOpen && !resendOpen && !cancelOpen && !redeliverOpen) {
        onClose();
      }
    }
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [isOpen, resendOpen, cancelOpen, redeliverOpen, onClose]);

  if (!isOpen) return null;

  const state = detail?.deliveryState;
  const isResendEligible =
    state === 'SENT' ||
    state === 'FAILED' ||
    state === 'UNKNOWN_OUTCOME';
  const isCancelEligible = state === 'PENDING' || state === 'RETRY_SCHEDULED';

  const handleResendSuccess = async (id, reason, duplicateRiskAccepted) => {
    const res = await resend(id, reason, duplicateRiskAccepted);
    setFeedback(t('drawer.resendQueued', { id: res.newRequestId || t('drawer.creating') }));
    await fetchDetail();
    if (onActionSuccess) onActionSuccess();
  };

  const handleCancelSuccess = async (id, reason) => {
    await cancel(id, reason);
    setFeedback(t('drawer.cancelled'));
    await fetchDetail();
    if (onActionSuccess) onActionSuccess();
  };

  const handleRedeliverSuccess = async (id, reason) => {
    const res = await redeliver(id, reason);
    setFeedback(t('drawer.redeliverQueued', { id: res.newRequestId || t('drawer.creating') }));
    await fetchDetail();
    if (onActionSuccess) onActionSuccess();
  };

  return (
    <>
      <div className="nm-drawer-backdrop" onClick={onClose} />
      <div
        className="nm-drawer"
        role="dialog"
        aria-modal="true"
        aria-labelledby="drawer-title"
      >
        <div className="nm-drawer-header">
          <h3 id="drawer-title" style={{ margin: 0, fontSize: '1.125rem', fontWeight: 600 }}>
            {t('drawer.title', { id: requestId })}
          </h3>
          <button
            type="button"
            className="nm-modal-close"
            onClick={onClose}
            aria-label={t('drawer.closeAria')}
          >
            &times;
          </button>
        </div>

        <div className="nm-drawer-body">
          {feedback && <div className="nm-alert nm-alert-success" role="status">{feedback}</div>}
          {error && <div className="nm-alert nm-alert-danger" role="alert">{error}</div>}

          {loading ? (
            <div style={{ padding: '2rem 0', textAlign: 'center', color: '#64748b' }} role="status">
              {t('drawer.loading')}
            </div>
          ) : detail ? (
            <div>
              {/* Overview Details */}
              <div className="nm-card" style={{ marginBottom: '1.25rem' }}>
                <div className="nm-card-body" style={{ display: 'grid', gridTemplateColumns: 'repeat(2, 1fr)', gap: '0.75rem', fontSize: '0.875rem' }}>
                  <div>
                    <span style={{ color: '#64748b', display: 'block', fontSize: '0.75rem' }}>{t('drawer.event')}</span>
                    <strong>{detail.businessEventReference}</strong>
                  </div>
                  <div>
                    <span style={{ color: '#64748b', display: 'block', fontSize: '0.75rem' }}>{t('drawer.deliveryState')}</span>
                    <span className={`nm-badge ${stateBadgeClass(detail.deliveryState)}`}>
                      {detail.deliveryState}
                    </span>
                  </div>
                  <div>
                    <span style={{ color: '#64748b', display: 'block', fontSize: '0.75rem' }}>{t('drawer.template')}</span>
                    <span>{detail.templateCode} (v{detail.templateVersionId})</span>
                  </div>
                  <div>
                    <span style={{ color: '#64748b', display: 'block', fontSize: '0.75rem' }}>{t('drawer.recipient')}</span>
                    <span>{detail.recipientAddressMasked}</span>
                  </div>
                  <div>
                    <span style={{ color: '#64748b', display: 'block', fontSize: '0.75rem' }}>{t('drawer.attemptCount')}</span>
                    <span>{detail.attemptCount}</span>
                  </div>
                  <div>
                    <span style={{ color: '#64748b', display: 'block', fontSize: '0.75rem' }}>{t('drawer.createdAt')}</span>
                    <span>{fmt.dateTime(detail.createdAt)}</span>
                  </div>
                  {detail.nextAttemptAt && (
                    <div>
                      <span style={{ color: '#64748b', display: 'block', fontSize: '0.75rem' }}>{t('drawer.nextAttempt')}</span>
                      <span>{fmt.dateTime(detail.nextAttemptAt)}</span>
                    </div>
                  )}
                  {detail.terminalAt && (
                    <div>
                      <span style={{ color: '#64748b', display: 'block', fontSize: '0.75rem' }}>{t('drawer.terminalAt')}</span>
                      <span>{fmt.dateTime(detail.terminalAt)}</span>
                    </div>
                  )}
                  {detail.originalRequestId && (
                    <div style={{ gridColumn: 'span 2' }}>
                      <span style={{ color: '#64748b', display: 'block', fontSize: '0.75rem' }}>{t('drawer.originalRequest')}</span>
                      <span>#{detail.originalRequestId}</span>
                    </div>
                  )}
                </div>
              </div>

              {/* Delivery Attempts Table */}
              <div style={{ marginBottom: '1.25rem' }}>
                <h4 style={{ margin: '0 0 0.5rem 0', fontSize: '0.9375rem', fontWeight: 600 }}>
                  {t('drawer.attemptsTitle')}
                </h4>
                {Array.isArray(detail.attempts) && detail.attempts.length > 0 ? (
                  <div className="nm-card">
                    <div className="nm-table-container">
                      <table className="nm-table">
                        <thead>
                          <tr>
                            <th>#</th>
                            <th>{t('drawer.colStarted')}</th>
                            <th>{t('drawer.colOutcome')}</th>
                            <th>{t('drawer.colMessage')}</th>
                          </tr>
                        </thead>
                        <tbody>
                          {detail.attempts.map((att) => (
                            <tr key={att.attemptNumber}>
                              <td>{att.attemptNumber}</td>
                              <td>{fmt.dateTime(att.startedAt)}</td>
                              <td>
                                <span className="nm-tag">{att.outcomeCategory}</span>
                              </td>
                              <td style={{ fontSize: '0.8125rem' }}>
                                {att.safeErrorSummary || att.providerMessageId || '—'}
                              </td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>
                  </div>
                ) : (
                  <div style={{ fontSize: '0.875rem', color: '#64748b', fontStyle: 'italic' }}>
                    {t('drawer.noAttempts')}
                  </div>
                )}
              </div>

              {/* Operational Audits Table */}
              <div>
                <h4 style={{ margin: '0 0 0.5rem 0', fontSize: '0.9375rem', fontWeight: 600 }}>
                  {t('drawer.auditsTitle')}
                </h4>
                {Array.isArray(detail.audits) && detail.audits.length > 0 ? (
                  <div className="nm-card">
                    <div className="nm-table-container">
                      <table className="nm-table">
                        <thead>
                          <tr>
                            <th>{t('drawer.colAction')}</th>
                            <th>{t('drawer.colPerformedBy')}</th>
                            <th>{t('drawer.colTime')}</th>
                            <th>{t('drawer.colReason')}</th>
                          </tr>
                        </thead>
                        <tbody>
                          {detail.audits.map((aud, idx) => (
                            <tr key={idx}>
                              <td><strong>{aud.actionType}</strong></td>
                              <td>{aud.performedBy}</td>
                              <td>{fmt.dateTime(aud.performedAt)}</td>
                              <td style={{ fontSize: '0.8125rem' }}>{aud.reason}</td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>
                  </div>
                ) : (
                  <div style={{ fontSize: '0.875rem', color: '#64748b', fontStyle: 'italic' }}>
                    {t('drawer.noAudits')}
                  </div>
                )}
              </div>
            </div>
          ) : null}
        </div>

        <div className="nm-drawer-footer">
          <button
            type="button"
            className="nm-btn nm-btn-primary"
            onClick={() => setResendOpen(true)}
            disabled={!isResendEligible || loading}
            title={
              !isResendEligible
                ? t('drawer.resendBlocked')
                : t('drawer.resendHint')
            }
          >
            {t('drawer.resend')}
          </button>

          <button
            type="button"
            className="nm-btn nm-btn-danger"
            onClick={() => setCancelOpen(true)}
            disabled={!isCancelEligible || loading}
            title={
              !isCancelEligible
                ? t('drawer.cancelBlocked')
                : t('drawer.cancelHint')
            }
          >
            {t('drawer.cancel')}
          </button>

          <button
            type="button"
            className="nm-btn"
            onClick={() => setRedeliverOpen(true)}
            disabled={loading}
            title={t('drawer.redeliverHint')}
          >
            {t('drawer.redeliver')}
          </button>

          <button
            type="button"
            className="nm-btn"
            style={{ marginLeft: 'auto' }}
            onClick={onClose}
          >
            {ts('close')}
          </button>
        </div>
      </div>

      {/* Confirmation Modals */}
      <ResendConfirmationModal
        isOpen={resendOpen}
        onClose={() => setResendOpen(false)}
        requestItem={detail}
        onResend={handleResendSuccess}
        userRole={userRole}
      />

      <CancelConfirmationModal
        isOpen={cancelOpen}
        onClose={() => setCancelOpen(false)}
        requestItem={detail}
        onCancel={handleCancelSuccess}
      />

      <RedeliverConfirmationModal
        isOpen={redeliverOpen}
        onClose={() => setRedeliverOpen(false)}
        requestItem={detail}
        onRedeliver={handleRedeliverSuccess}
      />
    </>
  );
}

export default NotificationRequestDetailDrawer;

