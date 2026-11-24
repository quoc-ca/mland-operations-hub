import React, { useEffect, useState } from 'react';
import { useT } from '../../../shared/i18n/I18nProvider';
import { sharedMessages } from '../../../shared/ui/messages';
import { notificationMessages } from '../messages';

export function RedeliverConfirmationModal({
  isOpen,
  onClose,
  requestItem,
  onRedeliver,
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

  const handleSubmit = async (e) => {
    e.preventDefault();
    const trimmed = reason.trim();
    if (!trimmed) {
      setError(t('redeliver.reasonRequired'));
      return;
    }

    setSubmitting(true);
    setError(null);
    try {
      await onRedeliver(requestItem.id, trimmed);
      onClose();
    } catch (err) {
      setError(
        err?.message ||
          t('redeliver.failed')
      );
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div
      className="nm-modal-backdrop"
      role="dialog"
      aria-modal="true"
      aria-labelledby="redeliver-modal-title"
    >
      <div className="nm-modal">
        <div className="nm-modal-header">
          <h3 id="redeliver-modal-title">{t('redeliver.title', { id: requestItem.id })}</h3>
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
            </div>

            <div className="nm-alert nm-alert-info" role="status">
              {t('redeliver.info')}
            </div>

            <div style={{ marginBottom: '0.5rem' }}>
              <label
                htmlFor="redeliverReason"
                style={{ display: 'block', fontSize: '0.875rem', fontWeight: 600, marginBottom: '0.25rem' }}
              >
                {t('redeliver.reasonLabel')}
              </label>
              <textarea
                id="redeliverReason"
                className="nm-textarea"
                style={{ width: '100%', height: '80px' }}
                value={reason}
                onChange={(e) => setReason(e.target.value)}
                placeholder={t('redeliver.reasonPlaceholder')}
                disabled={submitting}
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
              disabled={submitting || !reason.trim()}
            >
              {submitting ? t('redeliver.submitting') : t('redeliver.submit')}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}

export default RedeliverConfirmationModal;

