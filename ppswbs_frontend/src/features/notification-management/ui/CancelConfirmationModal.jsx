import React, { useEffect, useState } from 'react';

export function CancelConfirmationModal({
  isOpen,
  onClose,
  requestItem,
  onCancel,
}) {
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
      setError('Chỉ có thể hủy yêu cầu đang ở trạng thái PENDING hoặc RETRY_SCHEDULED.');
      return;
    }
    const trimmed = reason.trim();
    if (!trimmed) {
      setError('Lý do hủy gửi là bắt buộc.');
      return;
    }

    setSubmitting(true);
    setError(null);
    try {
      await onCancel(requestItem.id, trimmed);
      onClose();
    } catch (err) {
      setError(err?.message || 'Hủy yêu cầu thông báo thất bại.');
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
          <h3 id="cancel-modal-title">Hủy gửi yêu cầu thông báo #{requestItem.id}</h3>
          <button
            type="button"
            className="nm-modal-close"
            onClick={onClose}
            disabled={submitting}
            aria-label="Đóng"
          >
            &times;
          </button>
        </div>

        <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', flex: 1 }}>
          <div className="nm-modal-body">
            {error && <div className="nm-alert nm-alert-danger" role="alert">{error}</div>}

            <div style={{ marginBottom: '1rem', fontSize: '0.875rem', color: '#334155' }}>
              <div><strong>Mã sự kiện:</strong> {requestItem.businessEventReference}</div>
              <div><strong>Mẫu thư:</strong> {requestItem.templateCode}</div>
              <div><strong>Người nhận:</strong> {requestItem.recipientAddressMasked}</div>
              <div><strong>Trạng thái hiện tại:</strong> {requestItem.deliveryState}</div>
            </div>

            {!isEligible ? (
              <div className="nm-alert nm-alert-danger" role="alert">
                Yêu cầu này đang ở trạng thái <code>{requestItem.deliveryState}</code> và không thể hủy bỏ. Chỉ yêu cầu đang chờ (PENDING) hoặc đang lên lịch gửi lại (RETRY_SCHEDULED) mới được phép hủy.
              </div>
            ) : (
              <div className="nm-alert nm-alert-warning" role="alert">
                Hành động này không thể hoàn tác. Khi hủy, hệ thống sẽ dừng gửi thư vĩnh viễn và ghi nhật ký kiểm toán hành động này.
              </div>
            )}

            <div style={{ marginBottom: '0.5rem' }}>
              <label
                htmlFor="cancelReason"
                style={{ display: 'block', fontSize: '0.875rem', fontWeight: 600, marginBottom: '0.25rem' }}
              >
                Lý do hủy gửi *
              </label>
              <textarea
                id="cancelReason"
                className="nm-textarea"
                style={{ width: '100%', height: '80px' }}
                value={reason}
                onChange={(e) => setReason(e.target.value)}
                placeholder="Ví dụ: Khách hàng yêu cầu hủy đơn, không cần gửi thư xác nhận nữa."
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
              Hủy bỏ
            </button>
            <button
              type="submit"
              className="nm-btn nm-btn-danger"
              disabled={submitting || !isEligible || !reason.trim()}
            >
              {submitting ? 'Đang hủy…' : 'Xác nhận hủy gửi'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}

export default CancelConfirmationModal;

