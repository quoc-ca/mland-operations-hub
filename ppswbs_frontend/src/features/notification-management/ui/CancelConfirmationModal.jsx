import React, { useEffect, useState } from 'react';
import { useT } from '../../../shared/i18n/I18nProvider';
import { sharedMessages } from '../../../shared/ui/messages';
import { notificationMessages } from '../messages';
import { renderRich } from './richText';

export function CancelConfirmationModal({
  isOpen,
  onClose,
  requestItem,
  onCancel,
}) {
  const t = useT(notificationMessages);
  const ts = useT(sharedMessages);
  const [reason, setReason] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState(null);

  useEffect(() => {
    setReason('');
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

  const isEligible =
    requestItem.deliveryState === 'PENDING' ||
    requestItem.deliveryState === 'RETRY_SCHEDULED';

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!isEligible) {
      setError(t('cancel.notEligible'));
      return;
    }
    const trimmed = reason.trim();
    if (!trimmed) {
      setError(t('cancel.reasonRequired'));
      return;
    }

    setSubmitting(true);
    setError(null);
    try {
      await onCancel(requestItem.id, trimmed);
      onClose();
    } catch (err) {
      setError(err?.message || t('cancel.failed'));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div
      className="nm-modal-backdrop"
      role="dialog"
      aria-modal="true"
      aria-labelledby="cancel-modal-title"
    >
      <div className="nm-modal">
        <div className="nm-modal-header">
          <h3 id="cancel-modal-title">{t('cancel.title', { id: requestItem.id })}</h3>
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

            {!isEligible ? (
              <div className="nm-alert nm-alert-danger" role="alert">
                {renderRich(t('cancel.notEligibleBody', { state: requestItem.deliveryState }))}
              </div>
            ) : (
              <div className="nm-alert nm-alert-warning" role="alert">
                {t('cancel.warning')}
              </div>
            )}

            <div style={{ marginBottom: '0.5rem' }}>
              <label
                htmlFor="cancelReason"
                style={{ display: 'block', fontSize: '0.875rem', fontWeight: 600, marginBottom: '0.25rem' }}
              >
                {t('cancel.reasonLabel')}
              </label>
              <textarea
                id="cancelReason"
                className="nm-textarea"
                style={{ width: '100%', height: '80px' }}
                value={reason}
                onChange={(e) => setReason(e.target.value)}
                placeholder={t('cancel.reasonPlaceholder')}
                disabled={submitting || !isEligible}
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
              {t('cancel.back')}
            </button>
            <button
              type="submit"
              className="nm-btn nm-btn-danger"
              disabled={submitting || !isEligible || !reason.trim()}
            >
              {submitting ? t('cancel.submitting') : t('cancel.submit')}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}

export default CancelConfirmationModal;

