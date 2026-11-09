import React, { useEffect, useState } from 'react';

export function ResendConfirmationModal({
  isOpen,
  onClose,
  requestItem,
  onResend,
  userRole = 'MANAGER',
}) {
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
      setError('Quản lý không có quyền gửi lại yêu cầu UNKNOWN_OUTCOME.');
      return;
    }
    const trimmed = reason.trim();
    if (trimmed.length < 10) {
      setError('Lý do gửi lại là bắt buộc và phải có tối thiểu 10 ký tự.');
      return;
    }
    if (isUnknownOutcome && !duplicateRiskAccepted) {
      setError('Bạn phải xác nhận chấp nhận rủi ro gửi thư trùng trước khi tiếp tục.');
      return;
    }

    setSubmitting(true);
    setError(null);
    try {
      await onResend(requestItem.id, trimmed, duplicateRiskAccepted);
      onClose();
    } catch (err) {
      setError(err?.message || 'Gửi lại yêu cầu thất bại.');
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
          <h3 id="resend-modal-title">Gửi lại email thông báo #{requestItem.id}</h3>
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

            {isUnknownOutcome && (
              <div
                className={`nm-alert ${isManagerForbidden ? 'nm-alert-danger' : 'nm-alert-warning'}`}
                role="alert"
              >
                {isManagerForbidden ? (
                  <div>
                    <strong>Từ chối quyền:</strong> Trạng thái thư này là <code>UNKNOWN_OUTCOME</code> (tiến trình bị gián đoạn khi đang kết nối máy chủ thư). Chỉ Quản trị viên (Admin) mới có quyền can thiệp gửi lại.
                  </div>
                ) : (
                  <div>
                    <strong>Cảnh báo quan trọng:</strong> Thư này có trạng thái <code>UNKNOWN_OUTCOME</code>. Máy chủ thư có thể đã gửi thư đi trước khi sự cố xảy ra. Gửi lại có thể khiến khách hàng nhận hai email cùng nội dung.
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
                  <span>Tôi đã xác nhận với dịch vụ mail và chấp nhận rủi ro khách hàng có thể nhận thư trùng.</span>
                </label>
              </div>
            )}

            <div style={{ marginBottom: '0.5rem' }}>
              <label
                htmlFor="resendReason"
                style={{ display: 'block', fontSize: '0.875rem', fontWeight: 600, marginBottom: '0.25rem' }}
              >
                Lý do gửi lại (Bắt buộc, tối thiểu 10 ký tự) *
              </label>
              <textarea
                id="resendReason"
                className="nm-textarea"
                style={{ width: '100%', height: '80px' }}
                value={reason}
                onChange={(e) => setReason(e.target.value)}
                placeholder="Ví dụ: Khách hàng báo chưa nhận được email xác nhận trong hộp thư đến"
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
              Hủy
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
              {submitting ? 'Đang xếp hàng…' : 'Xác nhận gửi lại'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}

export default ResendConfirmationModal;

