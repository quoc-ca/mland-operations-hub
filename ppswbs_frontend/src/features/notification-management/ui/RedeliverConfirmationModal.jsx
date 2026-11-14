import React, { useEffect, useState } from 'react';

export function RedeliverConfirmationModal({
  isOpen,
  onClose,
  requestItem,
  onRedeliver,
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

  const handleSubmit = async (e) => {
    e.preventDefault();
    const trimmed = reason.trim();
    if (!trimmed) {
      setError('Vui lòng nhập lý do cấp và gửi lại liên kết/vé mới.');
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
          'Không thể gửi lại với chứng chỉ mới. Tính năng nguồn có thể đã từ chối yêu cầu cấp mới.'
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
          <h3 id="redeliver-modal-title">Cấp lại liên kết và gửi lại #{requestItem.id}</h3>
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
            </div>

            <div className="nm-alert nm-alert-info" role="status">
              Hệ thống sẽ liên hệ với tính năng nguồn (Booking/Retail) để xác thực và cấp mã vé/link mới an toàn mà không làm thay đổi bản chụp dữ liệu giao dịch gốc.
            </div>

            <div style={{ marginBottom: '0.5rem' }}>
              <label
                htmlFor="redeliverReason"
                style={{ display: 'block', fontSize: '0.875rem', fontWeight: 600, marginBottom: '0.25rem' }}
              >
                Lý do cấp lại liên kết và gửi *
              </label>
              <textarea
                id="redeliverReason"
                className="nm-textarea"
                style={{ width: '100%', height: '80px' }}
                value={reason}
                onChange={(e) => setReason(e.target.value)}
                placeholder="Ví dụ: Link vé QR của khách hàng đã hết hạn sau 24h, cấp link truy cập mới."
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
              Hủy
            </button>
            <button
              type="submit"
              className="nm-btn nm-btn-primary"
              disabled={submitting || !reason.trim()}
            >
              {submitting ? 'Đang xử lý…' : 'Xác nhận cấp lại và gửi'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}

export default RedeliverConfirmationModal;

