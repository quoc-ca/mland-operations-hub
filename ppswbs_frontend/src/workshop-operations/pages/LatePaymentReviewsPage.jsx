import React, { useCallback, useEffect, useState } from 'react';
import { useAuth } from '../../shared/auth/AuthProvider';
import { useRole } from '../../shared/auth/useRole';
import { Loading, useCommand } from '../../shared/ui/components';
import { OperationsAuthWorkspace } from '../../shared/ui/OperationsAuthWorkspace';
import { decideLatePaymentReview, listLatePaymentReviews } from '../../api/workshops';
import '../../ota-connectivity/ota-connectivity.css';
import '../workshop-operations.css';

// Same standardized reasons as the session page recovery panel (003.1 FR-040a decisions need a reason).
const REASONS = [
  { code: 'PROVIDER_CONFIRMED_LATE_DEPOSIT', label: 'PROVIDER_CONFIRMED_LATE_DEPOSIT — Ngân hàng / VNPay đã ghi có tiền (ghi nhận tiền về trễ)' },
  { code: 'CUSTOMER_PRESENTED_RECEIPT', label: 'CUSTOMER_PRESENTED_RECEIPT — Khách xuất trình biên lai / ủy nhiệm chi giao dịch hợp lệ' },
  { code: 'DISPUTE_ESCALATION_FINANCE', label: 'DISPUTE_ESCALATION_FINANCE — Khiếu nại thanh toán / chuyển kế toán đối soát' },
  { code: 'OTHER_REQUIRES_NOTE', label: 'OTHER_REQUIRES_NOTE — Lý do khác (yêu cầu điền chi tiết ở ô ghi chú)' },
];

/** Manager queue of late payments waiting for a decision (003.1 FR-040a); the backend decides who may read or decide. */
export default function LatePaymentReviewsPage() {
  const { status, user } = useAuth();
  const role = useRole();
  const command = useCommand();
  const authorization = user?.token || user?.accessToken;
  const [reviews, setReviews] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');
  const [reasonCode, setReasonCode] = useState(REASONS[0].code);
  const [note, setNote] = useState('');

  const load = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      setReviews(await listLatePaymentReviews({ status: 'PENDING_MANAGER', authorization }));
    } catch (e) {
      setReviews(null);
      setError(e?.detail || e?.message || 'Không tải được hàng đợi thanh toán về trễ.');
    } finally {
      setLoading(false);
    }
  }, [authorization]);

  useEffect(() => {
    if (status === 'signed-in' && role === 'MANAGER') load();
  }, [status, role, load]);

  if (status === 'loading') return <Loading />;
  if (status !== 'signed-in') {
    return <OperationsAuthWorkspace topline="MLAND / WORKSHOP OPERATIONS" message="Authentication required." />;
  }

  const noteMissing = reasonCode === 'OTHER_REQUIRES_NOTE' && !note.trim();
  const fullReason = () => (reasonCode === 'OTHER_REQUIRES_NOTE' ? note.trim()
    : (note.trim() ? `${reasonCode}: ${note.trim()}` : reasonCode));

  async function decide(review, decision) {
    setMessage('');
    const result = await command.run(key => decideLatePaymentReview(review.groupCode, review.reviewId, {
      decision, reason: fullReason(), authorization, idempotencyKey: key,
    }));
    if (result.ok) {
      setMessage(decision === 'CONFIRM' ? 'Đã xác nhận thanh toán về trễ.' : 'Đã mở yêu cầu hoàn 100%.');
      load();
    }
  }

  return <div className="ota-workspace"><main className="ota-main">
    <div className="ota-topline">MLAND / LATE PAYMENTS <span>· Backend-authorized</span></div>
    <header className="ota-page-heading">
      <div>
        <p className="muted">
          Thanh toán về trễ không tự xác nhận lại sẽ chờ Manager (003.1 FR-040a). Hàng đợi xếp theo ca bắt đầu sớm nhất.
          CONFIRM chỉ khả dụng khi điều kiện cho phép, còn lại là REFUND hoàn 100% qua 003.3.
        </p>
      </div>
    </header>
    <article className="ota-panel">
      <div className="ota-form-grid" style={{ marginBottom: '12px' }}>
        <label className="full-width">
          Lý do quyết định (chuẩn hóa) *
          <select value={reasonCode} onChange={e => setReasonCode(e.target.value)}>
            {REASONS.map(r => <option key={r.code} value={r.code}>{r.label}</option>)}
          </select>
        </label>
        <label className="full-width">
          Ghi chú chi tiết / mã tham chiếu giao dịch {reasonCode === 'OTHER_REQUIRES_NOTE' ? '*' : '(tùy chọn)'}
          <textarea value={note} onChange={e => setNote(e.target.value)} />
        </label>
      </div>
      <div className="ops-actions">
        <button className="button outline" disabled={loading} onClick={load}>
          {loading ? 'Đang tải...' : 'Tải lại hàng đợi'}
        </button>
      </div>
      {message && <p role="status">{message}</p>}
      {error && <p role="alert" style={{ color: '#b00' }}>{error}</p>}
      {Array.isArray(reviews) && reviews.length === 0 && (
        <p className="muted">Không có thanh toán về trễ nào đang chờ Manager.</p>
      )}
      {Array.isArray(reviews) && reviews.length > 0 && (
        <ul style={{ listStyle: 'none', margin: '12px 0 0', padding: 0, display: 'grid', gap: '10px' }}>
          {reviews.map(review => (
            <li key={review.reviewId} style={{ border: '1px solid #dcdfe6', padding: '12px' }}>
              <strong>{review.groupCode}</strong>
              <p className="muted" style={{ margin: '4px 0', fontSize: '13px' }}>
                Số tiền: {review.amount} · Điều kiện không đạt: {(review.failedConditions || []).join(', ') || '—'}
                {review.resultDueAt ? ` · Phải trả lời trước: ${new Date(review.resultDueAt).toLocaleString('vi-VN')}` : ''}
              </p>
              <div className="ops-actions">
                <button className="button outline" disabled={command.pending || !review.confirmAllowed || noteMissing}
                  onClick={() => decide(review, 'CONFIRM')}>Confirm late payment</button>
                <button className="button warning" disabled={command.pending || noteMissing}
                  onClick={() => decide(review, 'REFUND')}>Open 100% refund</button>
              </div>
            </li>
          ))}
        </ul>
      )}
    </article>
  </main></div>;
}
