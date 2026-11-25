import React, { useEffect, useState } from 'react';
import { useT } from '../../../shared/i18n/I18nProvider';
import { sharedMessages } from '../../../shared/ui/messages';
import { notificationMessages } from '../messages';
import { renderRich } from './richText';

export function ResendConfirmationModal({
  isOpen,
  onClose,
  requestItem,
  onResend,
  userRole = 'MANAGER',
}) {
  const t = useT(notificationMessages);
  const ts = useT(sharedMessages);
  const [reason, setReason] = useState('');
  const [duplicateRiskAccepted, setDuplicateRiskAccepted] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState(null);

  const isUnknownOutcome = requestItem?.deliveryState === 'UNKNOWN_OUTCOME';
  const isAdmin = userRole === 'ADMIN';
  const isManagerForbidden = isUnknownOutcome && !isAdmin;

  useEffect(() => {
    setReason('');
    setDuplicateRiskAccepted(false);
    setError(null);
  }, [isOpen, requestItem]);

  useEffect(() => {
    function handleKeyDown(e) {
      if (e.key === 'Escape' && isOpen && !submitting) {
        onClose();
      }
    }
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [isOpen, submitting, onClose]);

  if (!isOpen || !requestItem) return null;

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (isManagerForbidden) {
      setError(t('resend.notAllowed'));
      return;
    }
    const trimmed = reason.trim();
    if (trimmed.length < 10) {
      setError(t('resend.reasonTooShort'));
      return;
    }
    if (isUnknownOutcome && !duplicateRiskAccepted) {
      setError(t('resend.riskRequired'));
      return;
    }

    setSubmitting(true);
    setError(null);
    try {
      await onResend(requestItem.id, trimmed, duplicateRiskAccepted);
      onClose();
    } catch (err) {
      setError(err?.message || t('resend.failed'));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div
      className="nm-modal-backdrop"
      role="dialog"
      aria-modal="true"
      aria-labelledby="resend-modal-title"
    >
      <div className="nm-modal">
        <div className="nm-modal-header">
          <h3 id="resend-modal-title">{t('resend.title', { id: requestItem.id })}</h3>
          <button
            type="button"
            className="nm-modal-close"
            onClick={onClose}
            disabled={submitting}
            aria-label={ts('close')}
          >
            &times;
          </button>
        </div>

        <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', flex: 1 }}>
          <div className="nm-modal-body">
            {error && <div className="nm-alert nm-alert-danger" role="alert">{error}</div>}

            <div style={{ marginBottom: '1rem', fontSize: '0.875rem', color: '#334155' }}>
              <div><strong>{t('request.event')}</strong> {requestItem.businessEventReference}</div>
              <div><strong>{t('request.template')}</strong> {requestItem.templateCode}</div>
              <div><strong>{t('request.recipient')}</strong> {requestItem.recipientAddressMasked}</div>
              <div><strong>{t('request.currentState')}</strong> {requestItem.deliveryState}</div>
            </div>

            {isUnknownOutcome && (
              <div
                className={`nm-alert ${isManagerForbidden ? 'nm-alert-danger' : 'nm-alert-warning'}`}
                role="alert"
              >
                {isManagerForbidden ? (
                  <div>
                    {renderRich(t('resend.deniedTitle'))}
                    <p style={{ margin: '0.25rem 0 0 0', fontSize: '0.8125rem' }}>
                      {renderRich(t('resend.deniedBody'))}
                    </p>
                  </div>
                ) : (
                  <div>
                    {renderRich(t('resend.warning'))}
                  </div>
                )}
              </div>
            )}

            {isUnknownOutcome && isAdmin && (
              <div style={{ marginBottom: '1rem' }}>
                <label style={{ display: 'flex', alignItems: 'flex-start', gap: '0.5rem', cursor: 'pointer', fontSize: '0.875rem', color: '#991b1b', fontWeight: 600 }}>
                  <input
                    type="checkbox"
                    checked={duplicateRiskAccepted}
                    onChange={(e) => setDuplicateRiskAccepted(e.target.checked)}
                    disabled={submitting}
                    style={{ marginTop: '0.2rem' }}
                  />
                  <span>{t('resend.riskCheckbox')}</span>
                </label>
              </div>
            )}

            <div style={{ marginBottom: '0.5rem' }}>
              <label
                htmlFor="resendReason"
                style={{ display: 'block', fontSize: '0.875rem', fontWeight: 600, marginBottom: '0.25rem' }}
              >
                {t('resend.reasonLabel')}
              </label>
              <textarea
                id="resendReason"
                className="nm-textarea"
                style={{ width: '100%', height: '80px' }}
                value={reason}
                onChange={(e) => setReason(e.target.value)}
                placeholder={t('resend.reasonPlaceholder')}
                disabled={submitting || isManagerForbidden}
                required
              />
            </div>
          </div>

          <div className="nm-modal-footer">
            <button
              type="button"
              className="nm-btn"
              onClick={onClose}
              disabled={submitting}
            >
              {ts('cancel')}
            </button>
            <button
              type="submit"
              className="nm-btn nm-btn-primary"
              disabled={
                submitting ||
                isManagerForbidden ||
                reason.trim().length < 10 ||
                (isUnknownOutcome && !duplicateRiskAccepted)
              }
            >
              {submitting ? t('resend.submitting') : t('resend.submit')}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}

export default ResendConfirmationModal;

