import React, { useEffect, useState } from 'react';
import { useNotificationApi } from '../application/useNotificationApi';
import { useNotificationOperations } from '../application/useNotificationOperations';
import ResendConfirmationModal from './ResendConfirmationModal';
import CancelConfirmationModal from './CancelConfirmationModal';
import RedeliverConfirmationModal from './RedeliverConfirmationModal';

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
      setError(err?.message || 'Không thể tải chi tiết yêu cầu thông báo');
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
    setFeedback(`Yêu cầu gửi lại đã được xếp hàng (Mã yêu cầu mới: #${res.newRequestId || 'đang tạo'}).`);
    await fetchDetail();
    if (onActionSuccess) onActionSuccess();
  };

  const handleCancelSuccess = async (id, reason) => {
    await cancel(id, reason);
    setFeedback('Đã hủy gửi yêu cầu thông báo thành công.');
    await fetchDetail();
    if (onActionSuccess) onActionSuccess();
  };

  const handleRedeliverSuccess = async (id, reason) => {
    const res = await redeliver(id, reason);
    setFeedback(`Yêu cầu gửi lại với liên kết mới đã được xếp hàng (Mã yêu cầu mới: #${res.newRequestId || 'đang tạo'}).`);
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
            Chi tiết yêu cầu gửi thư #{requestId}
          </h3>
          <button
            type="button"
            className="nm-modal-close"
            onClick={onClose}
            aria-label="Đóng ngăn chi tiết"
          >
            &times;
          </button>
        </div>

        <div className="nm-drawer-body">
          {feedback && <div className="nm-alert nm-alert-success" role="status">{feedback}</div>}
          {error && <div className="nm-alert nm-alert-danger" role="alert">{error}</div>}

          {loading ? (
            <div style={{ padding: '2rem 0', textAlign: 'center', color: '#64748b' }} role="status">
              Đang tải dữ liệu…
            </div>
          ) : detail ? (
            <div>
              {/* Overview Details */}
              <div className="nm-card" style={{ marginBottom: '1.25rem' }}>
                <div className="nm-card-body" style={{ display: 'grid', gridTemplateColumns: 'repeat(2, 1fr)', gap: '0.75rem', fontSize: '0.875rem' }}>
                  <div>
                    <span style={{ color: '#64748b', display: 'block', fontSize: '0.75rem' }}>Mã sự kiện:</span>
                    <strong>{detail.businessEventReference}</strong>
                  </div>
                  <div>
                    <span style={{ color: '#64748b', display: 'block', fontSize: '0.75rem' }}>Trạng thái giao thư:</span>
                    <span className={`nm-badge ${stateBadgeClass(detail.deliveryState)}`}>
                      {detail.deliveryState}
                    </span>
                  </div>
                  <div>
                    <span style={{ color: '#64748b', display: 'block', fontSize: '0.75rem' }}>Mẫu thư:</span>
                    <span>{detail.templateCode} (v{detail.templateVersionId})</span>
                  </div>
                  <div>
                    <span style={{ color: '#64748b', display: 'block', fontSize: '0.75rem' }}>Người nhận (đã che):</span>
                    <span>{detail.recipientAddressMasked}</span>
                  </div>
                  <div>
                    <span style={{ color: '#64748b', display: 'block', fontSize: '0.75rem' }}>Số lần thử:</span>
                    <span>{detail.attemptCount}</span>
                  </div>
                  <div>
                    <span style={{ color: '#64748b', display: 'block', fontSize: '0.75rem' }}>Thời điểm tạo:</span>
                    <span>{detail.createdAt ? new Date(detail.createdAt).toLocaleString('vi-VN') : '—'}</span>
                  </div>
                  {detail.nextAttemptAt && (
                    <div>
                      <span style={{ color: '#64748b', display: 'block', fontSize: '0.75rem' }}>Lần gửi tiếp theo:</span>
                      <span>{new Date(detail.nextAttemptAt).toLocaleString('vi-VN')}</span>
                    </div>
                  )}
                  {detail.terminalAt && (
                    <div>
                      <span style={{ color: '#64748b', display: 'block', fontSize: '0.75rem' }}>Kết thúc lúc:</span>
                      <span>{new Date(detail.terminalAt).toLocaleString('vi-VN')}</span>
                    </div>
                  )}
                  {detail.originalRequestId && (
                    <div style={{ gridColumn: 'span 2' }}>
                      <span style={{ color: '#64748b', display: 'block', fontSize: '0.75rem' }}>Gửi lại từ yêu cầu:</span>
                      <span>#{detail.originalRequestId}</span>
                    </div>
                  )}
                </div>
              </div>

              {/* Delivery Attempts Table */}
              <div style={{ marginBottom: '1.25rem' }}>
                <h4 style={{ margin: '0 0 0.5rem 0', fontSize: '0.9375rem', fontWeight: 600 }}>
                  Lịch sử các lần gửi thư (Attempts)
                </h4>
                {Array.isArray(detail.attempts) && detail.attempts.length > 0 ? (
                  <div className="nm-card">
                    <div className="nm-table-container">
                      <table className="nm-table">
                        <thead>
                          <tr>
                            <th>#</th>
                            <th>Bắt đầu</th>
                            <th>Kết quả</th>
                            <th>Thông điệp / Lỗi</th>
                          </tr>
                        </thead>
                        <tbody>
                          {detail.attempts.map((att) => (
                            <tr key={att.attemptNumber}>
                              <td>{att.attemptNumber}</td>
                              <td>{att.startedAt ? new Date(att.startedAt).toLocaleTimeString('vi-VN') : '—'}</td>
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
                    Chưa có lượt gửi nào được ghi nhận.
                  </div>
                )}
              </div>

              {/* Operational Audits Table */}
              <div>
                <h4 style={{ margin: '0 0 0.5rem 0', fontSize: '0.9375rem', fontWeight: 600 }}>
                  Lịch sử thao tác & kiểm toán (Audit Trail)
                </h4>
                {Array.isArray(detail.audits) && detail.audits.length > 0 ? (
                  <div className="nm-card">
                    <div className="nm-table-container">
                      <table className="nm-table">
                        <thead>
                          <tr>
                            <th>Hành động</th>
                            <th>Người thực hiện</th>
                            <th>Thời gian</th>
                            <th>Lý do</th>
                          </tr>
                        </thead>
                        <tbody>
                          {detail.audits.map((aud, idx) => (
                            <tr key={idx}>
                              <td><strong>{aud.actionType}</strong></td>
                              <td>{aud.performedBy}</td>
                              <td>{aud.performedAt ? new Date(aud.performedAt).toLocaleString('vi-VN') : '—'}</td>
                              <td style={{ fontSize: '0.8125rem' }}>{aud.reason}</td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>
                  </div>
                ) : (
                  <div style={{ fontSize: '0.875rem', color: '#64748b', fontStyle: 'italic' }}>
                    Chưa có tác vụ can thiệp thủ công nào.
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
                ? 'Chỉ có thể gửi lại yêu cầu đã gửi (SENT), gửi thất bại (FAILED) hoặc UNKNOWN_OUTCOME bởi Admin'
                : 'Gửi lại email thông báo'
            }
          >
            Gửi lại thư
          </button>

          <button
            type="button"
            className="nm-btn nm-btn-danger"
            onClick={() => setCancelOpen(true)}
            disabled={!isCancelEligible || loading}
            title={
              !isCancelEligible
                ? 'Chỉ có thể hủy yêu cầu đang ở trạng thái PENDING hoặc RETRY_SCHEDULED'
                : 'Hủy gửi yêu cầu'
            }
          >
            Hủy gửi
          </button>

          <button
            type="button"
            className="nm-btn"
            onClick={() => setRedeliverOpen(true)}
            disabled={loading}
            title="Cấp mới liên kết vé/truy cập và gửi lại"
          >
            Gửi lại với link/vé mới
          </button>

          <button
            type="button"
            className="nm-btn"
            style={{ marginLeft: 'auto' }}
            onClick={onClose}
          >
            Đóng
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

