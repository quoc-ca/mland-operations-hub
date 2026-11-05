import React, { useState } from 'react';
import { Link, useParams } from 'react-router';
import { useAuth } from '../../shared/auth/AuthProvider';
import { useHttp } from '../../shared/api/useHttp';
import { ErrorNotice, Loading, SuccessNotice, useCommand, useResource } from '../../shared/ui/components';
import { OperationsSidebar } from '../../shared/ui/OperationsSidebar';
import { OperationsAuthWorkspace } from '../../shared/ui/OperationsAuthWorkspace';
import { createOperationsApi } from '../api/operationsApi';
import { submitLatePaymentFollowUp, submitMagicLinkIncident } from '../../api/workshops';
import StaffWorkshopAddonPanel from '../components/StaffWorkshopAddonPanel';
import '../../ota-connectivity/ota-connectivity.css';
import '../workshop-operations.css';

const CHECKIN_CORRECTION_REASONS = [
  { code: 'EARLY_CHECKIN', label: 'EARLY_CHECKIN — Khách đến check-in sớm (trước giờ mở quầy)' },
  { code: 'CUSTOMER_DEVICE_UNAVAILABLE', label: 'CUSTOMER_DEVICE_UNAVAILABLE — Khách hết pin / thiết bị không dùng được' },
  { code: 'RECORDING_ERROR', label: 'RECORDING_ERROR — Lỗi thao tác quét vé / ghi nhận nhầm' },
  { code: 'TICKET_REDELIVERY_UNAVAILABLE', label: 'TICKET_REDELIVERY_UNAVAILABLE — Không gửi lại được vé điện tử (yêu cầu note & ref)' },
  { code: 'OTHER_REQUIRES_NOTE', label: 'OTHER_REQUIRES_NOTE — Lý do đặc cách khác (yêu cầu giải trình ở Reason note)' },
];

const CANCELLATION_REASONS = [
  { code: 'INSTRUCTOR_UNAVAILABLE', label: 'INSTRUCTOR_UNAVAILABLE — Giảng viên / nghệ nhân vắng mặt đột xuất' },
  { code: 'FACILITY_ISSUE', label: 'FACILITY_ISSUE — Sự cố cơ sở vật chất / mất điện / thiết bị xưởng' },
  { code: 'WEATHER_EMERGENCY', label: 'WEATHER_EMERGENCY — Khẩn cấp do thời tiết / thiên tai' },
  { code: 'OTHER_REQUIRES_NOTE', label: 'OTHER_REQUIRES_NOTE — Lý do hủy khác (yêu cầu điền lý do ở ghi chú)' },
];

const RECOVERY_REASONS = [
  { code: 'PROVIDER_CONFIRMED_LATE_DEPOSIT', label: 'PROVIDER_CONFIRMED_LATE_DEPOSIT — Ngân hàng / VNPay đã ghi có tiền (ghi nhận tiền về trễ)' },
  { code: 'CUSTOMER_PRESENTED_RECEIPT', label: 'CUSTOMER_PRESENTED_RECEIPT — Khách xuất trình biên lai / ủy nhiệm chi giao dịch hợp lệ' },
  { code: 'LINK_COMPROMISED_SECURITY', label: 'LINK_COMPROMISED_SECURITY — Nghi ngờ lộ link / rò rỉ bảo mật (thu hồi / đổi link)' },
  { code: 'CUSTOMER_REQUESTED_NEW_LINK', label: 'CUSTOMER_REQUESTED_NEW_LINK — Khách hàng yêu cầu đổi / cấp lại link truy cập mới' },
  { code: 'DISPUTE_ESCALATION_FINANCE', label: 'DISPUTE_ESCALATION_FINANCE — Khiếu nại thanh toán / chuyển kế toán đối soát' },
  { code: 'OTHER_REQUIRES_NOTE', label: 'OTHER_REQUIRES_NOTE — Lý do khác (yêu cầu điền chi tiết ở ô ghi chú)' },
];

const DEFAULT_STAFF_OPTIONS = [
  { accountId: 33, fullName: 'Local Test Staff', loginEmail: 'test.staff@mland.local' },
];

function ReasonFields({ ticketCode, setTicketCode, reasonCode, setReasonCode, reasonNote, setReasonNote, evidenceReference, setEvidenceReference }) {
  return (
    <div className="ota-form-grid">
      <label>
        Ticket code *
        <input
          value={ticketCode}
          onChange={e => setTicketCode(e.target.value)}
          placeholder="TK-..."
          required
        />
      </label>
      <label>
        Evidence reference (tùy chọn)
        <input
          value={evidenceReference}
          onChange={e => setEvidenceReference(e.target.value)}
          placeholder="Mã đối soát / tham chiếu"
        />
      </label>
      <label className="full-width">
        Reason code (Lý do nghiệp vụ điểm danh) *
        <select value={reasonCode} onChange={e => setReasonCode(e.target.value)}>
          {CHECKIN_CORRECTION_REASONS.map(r => (
            <option key={r.code} value={r.code}>{r.label}</option>
          ))}
        </select>
      </label>
      <label className="full-width">
        Reason note (Ghi chú giải thích)
        <textarea
          value={reasonNote}
          onChange={e => setReasonNote(e.target.value)}
          placeholder="Nhập ghi chú giải thích lý do xử lý đặc cách..."
        />
      </label>
    </div>
  );
}

export default function OperationsSessionPage() {
  const { sessionId } = useParams();
  const { status, user } = useAuth();
  const http = useHttp();
  const api = createOperationsApi(http);
  const resource = useResource(() => api.getSession(sessionId), [sessionId]);
  const command = useCommand();
  const [message, setMessage] = useState('');
  const [ticketCode, setTicketCode] = useState('');
  const [qrToken, setQrToken] = useState('');
  const [lookupBookingCode, setLookupBookingCode] = useState('');
  const [resendBookingCode, setResendBookingCode] = useState('');
  const [lookup, setLookup] = useState(null);
  const [lookupError, setLookupError] = useState('');
  const [lookupLoading, setLookupLoading] = useState(false);
  const [reasonCode, setReasonCode] = useState('EARLY_CHECKIN');
  const [reasonNote, setReasonNote] = useState('');
  const [evidenceReference, setEvidenceReference] = useState('');
  const [cancelReasonCode, setCancelReasonCode] = useState('INSTRUCTOR_UNAVAILABLE');
  const [cancelReasonNote, setCancelReasonNote] = useState('');
  const [assignmentAccountId, setAssignmentAccountId] = useState('');
  const [staffOptions, setStaffOptions] = useState(DEFAULT_STAFF_OPTIONS);
  const [customAccountId, setCustomAccountId] = useState(false);
  const [loadingStaff, setLoadingStaff] = useState(false);
  const [bookingCode, setBookingCode] = useState('');
  const [recoveryReasonCode, setRecoveryReasonCode] = useState('PROVIDER_CONFIRMED_LATE_DEPOSIT');
  const [incidentNote, setIncidentNote] = useState('');
  const [incidentResult, setIncidentResult] = useState(null);

  React.useEffect(() => {
    if (status !== 'signed-in') return;
    let mounted = true;
    setLoadingStaff(true);
    http('/api/v1/accounts?role=STAFF&status=ACTIVE&size=100')
      .then(res => {
        if (!mounted) return;
        const list = res?.data || res?.content || (Array.isArray(res) ? res : []);
        if (Array.isArray(list) && list.length > 0) {
          setStaffOptions(list);
        }
      })
      .catch(err => {
        console.warn('Failed to fetch staff accounts, using fallback list:', err);
      })
      .finally(() => {
        if (mounted) setLoadingStaff(false);
      });
    return () => { mounted = false; };
  }, [status]);

  if (status === 'loading') return <Loading />;
  if (status !== 'signed-in') {
    return (
      <OperationsAuthWorkspace
        topline="MLAND / WORKSHOP OPERATIONS"
        message="Authentication required."
      />
    );
  }
  if (resource.loading) return <Loading />;
  if (resource.error) return <div className="ota-container"><ErrorNotice error={resource.error} onRetry={resource.reload} /></div>;
  const session = resource.data;
  const authorization = user?.token || user?.accessToken;

  async function run(action, success) {
    setMessage('');
    const result = await command.run(key => action(key));
    if (result.ok) { setMessage(success); resource.reload(); }
  }

  async function runBookingAction(action) {
    setMessage('');
    setIncidentResult(null);
    const result = await command.run(key => action(key));
    if (result.ok) setIncidentResult(result.data || { status: 'accepted' });
  }

  const reasonPayload = () => ({ reasonCode: reasonCode.trim(), reasonNote: reasonNote.trim() || undefined, evidenceReference: evidenceReference.trim() || undefined });
  const getFullIncidentReason = () => {
    if (recoveryReasonCode === 'OTHER_REQUIRES_NOTE') return incidentNote.trim();
    return incidentNote.trim() ? `${recoveryReasonCode}: ${incidentNote.trim()}` : recoveryReasonCode;
  };

  return <div className="ota-workspace"><OperationsSidebar /><main className="ota-main">
    <div className="ota-topline">MLAND / SESSION DETAIL <span>· Backend-authorized</span></div>
    <header className="ota-page-heading"><div><p className="ota-eyebrow">SESSION CONTROL</p><h1>{session.slotId || session.id}</h1><p>{session.workshopDate || 'Date not set'} · Location {session.locationId || '—'}</p></div><span className="ops-status large">{session.state}</span></header>
    <SuccessNotice>{message}</SuccessNotice><ErrorNotice error={command.error} onRetry={command.retry} onReload={resource.reload} />
    <section className="ops-detail-grid">
      <article className="ota-panel"><div className="ota-panel-head"><h2>Session facts</h2></div><dl className="ops-facts"><div><dt>Capacity</dt><dd>{session.capacity}</dd></div><div><dt>Check-in opens</dt><dd>{session.checkInOpensAt ? new Date(session.checkInOpensAt).toLocaleString() : '—'}</dd></div><div><dt>Operational lead</dt><dd>{session.operationalLeadAccountId || 'Unassigned'}</dd></div></dl><div className="ops-actions">{['PUBLISHED','SALES_PAUSED','IN_PROGRESS','ATTENDANCE_FINALIZED'].map(state => <button key={state} className="button outline" disabled={session.state === state || command.pending} onClick={() => run(key => api.transition(sessionId, state, key), `Session moved to ${state}.`)}>{state.replaceAll('_', ' ')}</button>)}<button className="button primary" disabled={command.pending} onClick={() => run(key => api.finalizeAttendance(sessionId, key), 'Attendance finalized.')}>Finalize attendance</button></div></article>
      <article className="ota-panel">
        <div className="ota-panel-head"><h2>Check-in desk</h2></div>
        <form
          className="ota-form"
          onSubmit={e => {
            e.preventDefault();
            if (ticketCode.trim()) run(key => api.checkIn(sessionId, { ticketCode: ticketCode.trim() }, key), 'Check-in recorded.');
            else if (qrToken.trim()) run(key => api.checkIn(sessionId, { qrToken: qrToken.trim() }, key), 'QR check-in recorded.');
          }}
        >
          <label>
            Ticket code
            <input
              value={ticketCode}
              onChange={e => setTicketCode(e.target.value)}
              placeholder="TK-..."
            />
          </label>
          <label>
            or QR token
            <input
              value={qrToken}
              onChange={e => setQrToken(e.target.value)}
              placeholder="Scan token"
            />
          </label>
          <button
            className="button primary"
            type="submit"
            disabled={command.pending || (!ticketCode.trim() && !qrToken.trim())}
          >
            Check in
          </button>
        </form>

        <div style={{ marginTop: '20px', paddingTop: '16px', borderTop: '1px solid #e5e7eb' }}>
          <p style={{ margin: '0 0 8px', fontSize: '13px', fontWeight: 600, color: '#374151' }}>
            Tra cứu vé theo Mã đặt chỗ (Booking code)
          </p>
          <form
            className="ota-inline-form ops-lookup"
            onSubmit={async e => {
              e.preventDefault();
              if (!lookupBookingCode.trim()) return;
              setLookupLoading(true);
              setLookupError('');
              setLookup(null);
              try {
                const res = await api.lookupBooking(sessionId, lookupBookingCode.trim());
                setLookup(res);
              } catch (err) {
                setLookupError(err?.message || 'Không tìm thấy vé hợp lệ cho mã đặt chỗ này trong ca hiện tại.');
              } finally {
                setLookupLoading(false);
              }
            }}
          >
            <input
              value={lookupBookingCode}
              onChange={e => setLookupBookingCode(e.target.value)}
              placeholder="BK-..."
            />
            <button className="button outline" type="submit" disabled={lookupLoading || !lookupBookingCode.trim()}>
              {lookupLoading ? 'Đang tra...' : 'Look up'}
            </button>
          </form>

          {lookupError && (
            <p style={{ color: '#b91c1c', background: '#fef2f2', border: '1px solid #fecaca', padding: '8px 12px', borderRadius: '6px', fontSize: '12px', marginTop: '10px' }}>
              {lookupError}
            </p>
          )}

          {lookup && Array.isArray(lookup) && (
            <div style={{ marginTop: '12px', border: '1px solid #e5e7eb', borderRadius: '8px', padding: '10px', background: '#f9fafb' }}>
              <div style={{ fontSize: '12px', fontWeight: 600, marginBottom: '8px', color: '#374151' }}>
                Tìm thấy {lookup.length} vé cho đơn {lookupBookingCode}:
              </div>
              <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
                {lookup.map((t, idx) => (
                  <div key={idx} style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '8px 12px', background: '#fff', borderRadius: '6px', border: '1px solid #e5e7eb', fontSize: '13px' }}>
                    <div>
                      <strong>{t.ticketCode}</strong>
                      <span style={{ marginLeft: '8px', padding: '2px 6px', borderRadius: '4px', fontSize: '11px', fontWeight: 600, background: t.attendanceState === 'CHECKED_IN' ? '#dcfce7' : '#fef3c7', color: t.attendanceState === 'CHECKED_IN' ? '#166534' : '#92400e' }}>
                        {t.attendanceState === 'CHECKED_IN' ? 'Đã check-in' : 'Chưa check-in'}
                      </span>
                      <span style={{ marginLeft: '6px', padding: '2px 6px', borderRadius: '4px', fontSize: '11px', background: '#f3f4f6', color: '#4b5563' }}>
                        {t.validityState}
                      </span>
                    </div>
                    <button
                      type="button"
                      className="button outline"
                      style={{ padding: '3px 8px', fontSize: '11px' }}
                      onClick={() => setTicketCode(t.ticketCode)}
                    >
                      Chọn vé này
                    </button>
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>
      </article>
      <article className="ota-panel">
        <div className="ota-panel-head"><h2>Check-in corrections</h2></div>
        <p className="muted" style={{ margin: '0 0 12px', fontSize: '13px' }}>
          Ghi đè check-in đặc cách (khách đến sớm, sự cố thiết bị) hoặc điều chỉnh điểm danh.
        </p>
        <ReasonFields {...{ ticketCode, setTicketCode, reasonCode, setReasonCode, reasonNote, setReasonNote, evidenceReference, setEvidenceReference }} />
        <div className="ops-actions">
          <button className="button primary" disabled={command.pending || !ticketCode.trim() || !reasonCode.trim()} onClick={() => run(key => api.overrideCheckIn(sessionId, { ticketCode: ticketCode.trim(), ...reasonPayload() }, key), 'Check-in override recorded.')}>Override check-in</button>
          <button className="button outline" disabled={command.pending || !ticketCode.trim() || !reasonCode.trim()} onClick={() => run(key => api.correctAttendance(sessionId, { ticketCode: ticketCode.trim(), correctedAttendanceState: 'CHECKED_IN', ...reasonPayload() }, key), 'Attendance correction recorded.')}>Mark checked-in</button>
          <button className="button danger" disabled={command.pending || !ticketCode.trim() || !reasonCode.trim()} onClick={() => run(key => api.reverseCheckIn(sessionId, { ticketCode: ticketCode.trim(), ...reasonPayload() }, key), 'Check-in reversal recorded.')}>Reverse check-in</button>
        </div>
      </article>
      <article className="ota-panel">
        <div className="ota-panel-head"><h2>Ticket delivery</h2></div>
        <p className="muted">Gửi lại toàn bộ vé điện tử qua email/kênh đăng ký của đơn đặt chỗ. Hệ thống áp dụng giới hạn tần suất gửi.</p>
        <div className="ota-inline-form">
          <input
            value={resendBookingCode}
            onChange={e => setResendBookingCode(e.target.value)}
            placeholder="BK-... (Mã đặt chỗ)"
          />
          <button
            className="button outline"
            disabled={command.pending || !resendBookingCode.trim()}
            onClick={() => run(key => api.resendTickets(sessionId, resendBookingCode.trim(), key), 'Ticket resend requested.')}
          >
            Resend ticket
          </button>
        </div>
      </article>
      <article className="ota-panel">
        <div className="ota-panel-head"><h2>Staff assignment</h2></div>
        <p className="muted">
          Phân công nhân viên (Staff) phụ trách ca workshop hoặc rút quyền phụ trách. Nhân viên phải thuộc chi nhánh của ca.
        </p>
        <div className="ota-form-grid" style={{ marginBottom: '16px' }}>
          <label className="full-width">
            Chọn nhân viên (Staff) *
            <select
              value={customAccountId ? '__OTHER__' : assignmentAccountId}
              onChange={e => {
                if (e.target.value === '__OTHER__') {
                  setCustomAccountId(true);
                  setAssignmentAccountId('');
                } else {
                  setCustomAccountId(false);
                  setAssignmentAccountId(e.target.value);
                }
              }}
            >
              <option value="">-- Chọn nhân viên (Staff) --</option>
              {staffOptions.map(s => (
                <option key={s.accountId} value={String(s.accountId)}>
                  {s.fullName ? `${s.fullName} (${s.loginEmail})` : s.loginEmail} — ID: {s.accountId}
                </option>
              ))}
              <option value="__OTHER__">+ Nhập Staff ID khác thủ công...</option>
            </select>
          </label>
          {customAccountId && (
            <label className="full-width">
              Nhập Staff account ID thủ công *
              <input
                value={assignmentAccountId}
                onChange={e => setAssignmentAccountId(e.target.value)}
                inputMode="numeric"
                placeholder="Nhập Account ID nhân viên (ví dụ: 33)"
                autoFocus
              />
            </label>
          )}
        </div>
        <div className="ops-actions">
          <button
            className="button primary"
            disabled={command.pending || !assignmentAccountId.trim()}
            onClick={() => run(key => api.assignStaff(sessionId, assignmentAccountId, key), 'Staff assignment saved.')}
          >
            Assign
          </button>
          <button
            className="button outline"
            disabled={command.pending || !assignmentAccountId.trim()}
            onClick={() => run(key => api.removeStaff(sessionId, assignmentAccountId, key), 'Staff assignment removed.')}
          >
            Remove
          </button>
        </div>
      </article>
      <article className="ota-panel">
        <div className="ota-panel-head"><h2>Manager booking recovery</h2></div>
        <p className="muted">Quy trình can thiệp sự cố đặc biệt của Manager (thu hồi/đổi magic link truy cập, ghi nhận thanh toán tiền về trễ từ ngân hàng).</p>
        <div className="ota-form-grid" style={{ marginBottom: '16px' }}>
          <label className="full-width">
            Recovery booking code *
            <input
              value={bookingCode}
              onChange={e => setBookingCode(e.target.value)}
              placeholder="BK-... (Mã đặt chỗ cần xử lý)"
              required
            />
          </label>
          <label className="full-width">
            Lý do xử lý sự cố (Chuẩn hóa) *
            <select
              value={recoveryReasonCode}
              onChange={e => setRecoveryReasonCode(e.target.value)}
            >
              {RECOVERY_REASONS.map(r => (
                <option key={r.code} value={r.code}>{r.label}</option>
              ))}
            </select>
          </label>
          <label className="full-width">
            Ghi chú chi tiết / Mã tham chiếu giao dịch {recoveryReasonCode === 'OTHER_REQUIRES_NOTE' ? '*' : '(tùy chọn)'}
            <textarea
              value={incidentNote}
              onChange={e => setIncidentNote(e.target.value)}
              placeholder="Ví dụ: Mã GD ngân hàng FT26..., hoặc giải trình lý do can thiệp..."
              required={recoveryReasonCode === 'OTHER_REQUIRES_NOTE'}
            />
          </label>
        </div>
        <div className="ops-actions">
          <button
            className="button outline"
            disabled={command.pending || !bookingCode.trim() || (recoveryReasonCode === 'OTHER_REQUIRES_NOTE' && !incidentNote.trim())}
            onClick={() => runBookingAction(key => submitMagicLinkIncident(bookingCode, { action: 'REVOKE', reason: getFullIncidentReason(), authorization, idempotencyKey: key }))}
          >
            Revoke magic link
          </button>
          <button
            className="button outline"
            disabled={command.pending || !bookingCode.trim() || (recoveryReasonCode === 'OTHER_REQUIRES_NOTE' && !incidentNote.trim())}
            onClick={() => runBookingAction(key => submitMagicLinkIncident(bookingCode, { action: 'ROTATE', reason: getFullIncidentReason(), authorization, idempotencyKey: key }))}
          >
            Rotate magic link
          </button>
          <button
            className="button outline"
            disabled={command.pending || !bookingCode.trim() || (recoveryReasonCode === 'OTHER_REQUIRES_NOTE' && !incidentNote.trim())}
            onClick={() => runBookingAction(key => submitLatePaymentFollowUp(bookingCode, { action: 'RECORD', reason: getFullIncidentReason(), authorization, idempotencyKey: key }))}
          >
            Record late payment
          </button>
          <button
            className="button warning"
            disabled={command.pending || !bookingCode.trim() || (recoveryReasonCode === 'OTHER_REQUIRES_NOTE' && !incidentNote.trim())}
            onClick={() => runBookingAction(key => submitLatePaymentFollowUp(bookingCode, { action: 'ESCALATE', reason: getFullIncidentReason(), authorization, idempotencyKey: key }))}
          >
            Escalate late payment
          </button>
        </div>
        {incidentResult && <pre className="ops-result">{JSON.stringify(incidentResult, null, 2)}</pre>}
      </article>
      <article className="ota-panel">
        <div className="ota-panel-head"><h2>Cancellation handoff</h2></div>
        <p className="muted">Dành cho Manager khi không thể tiếp tục ca workshop (giảng viên vắng mặt, sự cố cơ sở vật chất, thiên tai).</p>
        <div className="ota-form-grid" style={{ marginBottom: '12px' }}>
          <label className="full-width">
            Lý do hủy ca (Chuẩn hóa hệ thống) *
            <select value={cancelReasonCode} onChange={e => setCancelReasonCode(e.target.value)}>
              {CANCELLATION_REASONS.map(r => (
                <option key={r.code} value={r.code}>{r.label}</option>
              ))}
            </select>
          </label>
          <label className="full-width">
            Ghi chú giải trình hoặc tham chiếu sự cố *
            <textarea
              value={cancelReasonNote}
              onChange={e => setCancelReasonNote(e.target.value)}
              placeholder="Ghi chú chi tiết lý do hủy ca để chuyển tiếp settlement..."
            />
          </label>
        </div>
        <button
          className="button danger"
          disabled={command.pending || !cancelReasonNote.trim()}
          onClick={() => run(key => api.cancelSession(sessionId, { reasonCode: cancelReasonCode, reasonNote: cancelReasonNote.trim() }, key), 'Cancellation request submitted.')}
        >
          Request cancellation
        </button>
      </article>
      <StaffWorkshopAddonPanel http={http} />
    </section></main></div>;
}
