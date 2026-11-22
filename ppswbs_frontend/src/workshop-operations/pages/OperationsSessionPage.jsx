import React, { useState } from 'react';
import { useUrlFilters } from '../../shared/ui/useUrlFilters';
import { Link, useParams } from 'react-router';
import { useAuth } from '../../shared/auth/AuthProvider';
import { useRole } from '../../shared/auth/useRole';
import { useHttp } from '../../shared/api/useHttp';
import { ErrorNotice, Loading, SuccessNotice, useCommand, useResource } from '../../shared/ui/components';
import { OperationsAuthWorkspace } from '../../shared/ui/OperationsAuthWorkspace';
import { createOperationsApi } from '../api/operationsApi';
import { sessionTimeRange } from './OperationsDashboardPage';
import { useOtaConnectivityApi } from '../../ota-connectivity/api/useOtaConnectivityApi';
import { decideLatePaymentReview, listLatePaymentReviews, submitGroupMagicLinkIncident } from '../../api/workshops';
import StaffWorkshopAddonPanel from '../components/StaffWorkshopAddonPanel';
import { RescheduleQuotaPanel } from '../components/RescheduleQuotaPanel';
import '../../ota-connectivity/ota-connectivity.css';
import '../workshop-operations.css';

const CHECKIN_CORRECTION_REASONS = [
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

// T130: one message per STAFF_NOT_ELIGIBLE reason (the closed set the backend returns).
const STAFF_NOT_ELIGIBLE_MESSAGES = {
  ACCOUNT_NOT_FOUND: 'Không tìm thấy tài khoản này. Kiểm tra lại Account ID.',
  NOT_STAFF: 'Tài khoản này không phải Staff nên không thể phân công vào ca.',
  NOT_ACTIVE: 'Tài khoản Staff này không còn hoạt động (không ở trạng thái ACTIVE).',
  LOCATION_NOT_IN_SCOPE: 'Staff này không thuộc chi nhánh của ca nên không thể phân công.',
};
const staffNotEligibleText = reason => STAFF_NOT_ELIGIBLE_MESSAGES[reason] || 'Không phân công được Staff này cho ca.';

const ROSTER_LABELS = { CHECKED_IN: 'Đã check-in', NOT_CHECKED_IN: 'Chưa check-in', NO_SHOW: 'Vắng mặt' };

// While a session is IN_PROGRESS it can only go on to finalization or cancellation (spec 003.2): it never returns to a
// sales state. The scheduler (or an explicit early start) moves it to IN_PROGRESS; states it cannot reach are greyed out.
const SALES_STATES = ['PUBLISHED', 'SALES_PAUSED', 'IN_PROGRESS'];
const TRANSITION_FROM = { PUBLISHED: ['SALES_PAUSED', 'IN_PROGRESS'], SALES_PAUSED: ['PUBLISHED', 'IN_PROGRESS'] };
const transitionBlockedReason = (current, target) => {
  if (current === target) return 'Ca đang ở trạng thái này.';
  if ((TRANSITION_FROM[current] || []).includes(target)) return undefined;
  if (current === 'IN_PROGRESS') return 'Ca đã bắt đầu (IN PROGRESS) nên không quay lại trạng thái bán. Chỉ có thể chốt điểm danh hoặc hủy ca.';
  return 'Không thể chuyển ca từ trạng thái hiện tại sang trạng thái này.';
};
/**
 * Why a state change was refused, in words anyone can act on. The server's English detail is matched by what it says;
 * the code and correlation stay visible under the text for whoever investigates.
 */
export function transitionProblemText(error, target) {
  if (!error) return '';
  const detail = String(error.message || error.detail || '');
  if (error.code === 'LOCATION_SCOPE_DENIED' || /outside .*scope/i.test(detail)) {
    return 'Ca này thuộc chi nhánh ngoài phạm vi của bạn. Chỉ Staff của chi nhánh đó hoặc Manager đổi được trạng thái ca.';
  }
  if (error.status === 403) {
    if (/not assigned/i.test(detail)) {
      return 'Bạn chưa được gán vào ca này nên chưa đổi được trạng thái. Nhờ Manager phân công bạn vào ca (mục phân công nhân sự trong chi tiết ca) rồi thử lại.';
    }
    if (/only a manager/i.test(detail) || target === 'PUBLISHED' || target === 'SALES_PAUSED') {
      return 'Chỉ Manager được mở bán lại hoặc tạm dừng bán ca. Staff chỉ bắt đầu ca (IN PROGRESS).';
    }
    if (/staff member in the session location/i.test(detail) || target === 'IN_PROGRESS') {
      return 'Chỉ Manager, hoặc Staff của chi nhánh này đã được gán vào ca, mới bắt đầu được ca. Kiểm tra bạn có được gán vào ca này không.';
    }
    return 'Bạn không có quyền đổi trạng thái ca này.';
  }
  if (error.status === 409) {
    if (/already in state/i.test(detail)) return 'Ca đã ở trạng thái này rồi. Tải lại trang để xem trạng thái mới nhất.';
    return `Không chuyển được ca sang ${String(target || '').replaceAll('_', ' ')} từ trạng thái hiện tại. Tải lại trang để xem trạng thái mới nhất của ca.`;
  }
  return '';
}

const ROSTER_FILTERS = [
  { key: 'ALL', label: 'Tất cả' },
  { key: 'CHECKED_IN', label: 'Đã check-in' },
  { key: 'NOT_CHECKED_IN', label: 'Chưa check-in' },
  { key: 'NO_SHOW', label: 'Vắng mặt' },
];

const DEFAULT_STAFF_OPTIONS = [
  { accountId: 33, fullName: 'Local Test Staff', loginEmail: 'test.staff@mland.local' },
];

function ReasonFields({ ticketCode, setTicketCode, reasonCode, setReasonCode, reasonNote, setReasonNote, evidenceReference, setEvidenceReference }) {
  return (
    <div className="ota-form-grid">
      <label>
        Mã vé *
        <input
          value={ticketCode}
          onChange={e => setTicketCode(e.target.value)}
          placeholder="GRP-…-L0-T1"
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

/** Staff-facing text of the three resend refusals (spec FR-003c, T127, T139); null for any other failure. */
function resendRefusalText(error) {
  if (error?.status === 429 && error.code === 'REDELIVERY_THROTTLED') {
    const wait = error.retryAfterSeconds;
    return `Đã gửi lại vé tối đa 3 lần trong 15 phút cho đơn này${wait ? `; thử lại sau ${wait} giây` : ''}. `
      + 'Nếu khách vẫn chưa nhận được vé, dùng "Override check-in" với lý do TICKET_REDELIVERY_UNAVAILABLE.';
  }
  if (error?.status === 409 && error.code === 'INVALID_STATE' && error.reason === 'NO_ELIGIBLE_TICKET') {
    return 'Đơn này không có vé hợp lệ chưa sử dụng để gửi lại.';
  }
  if (error?.status === 409 && error.code === 'INVALID_STATE' && error.reason === 'REDELIVERY_REJECTED_BY_BOOKING') {
    return 'Hệ thống đặt chỗ từ chối gửi lại vé của đơn này. Chưa có vé nào được gửi.';
  }
  return null;
}

/** T126: staff-facing text of 409 CHECK_IN_NOT_OPEN ("check-in opens at ..."); null for any other failure. */
export function checkInNotOpenText(error) {
  if (error?.status !== 409 || error.code !== 'CHECK_IN_NOT_OPEN') return null;
  const opensAt = error.checkInOpensAt ? new Date(error.checkInOpensAt) : null;
  const when = opensAt && !Number.isNaN(opensAt.getTime()) ? opensAt.toLocaleString('vi-VN') : null;
  return `Check-in opens at ${when || 'a later time'}.`;
}

/**
 * The codes of a roster row as the customer knows them: the ticket code GRP-…-L1-T1 (line reference + person number)
 * and the booking code GRP-… (the group). A direct booking without a line suffix keeps its stored codes.
 */
export function rosterCodes(row) {
  const line = String(row?.bookingReference || '');
  const grouped = /^(.+)-L\d+$/.exec(line);
  if (!grouped || !row?.ticketSequence) return { ticket: row?.ticketCode, booking: line, internal: null };
  return { ticket: `${line}-T${row.ticketSequence}`, booking: grouped[1], internal: row.ticketCode };
}

/**
 * Payment badge of a roster row, next to the attendance state: "Đã thanh toán" once the ticket folio is FINALIZED
 * (or CLOSED), "Chưa thanh toán" once the session attendance is finalized and the folio is not, nothing before that.
 */
export function paymentBadge(folioStatus, sessionState) {
  if (folioStatus === 'FINALIZED' || folioStatus === 'CLOSED') return { key: 'paid', label: 'Đã thanh toán' };
  // An unknown folio (the read failed or the ticket has none) never claims "unpaid".
  if (folioStatus && sessionState === 'ATTENDANCE_FINALIZED') return { key: 'unpaid', label: 'Chưa thanh toán' };
  return null;
}

export default function OperationsSessionPage() {
  const { sessionId } = useParams();
  const { status, user } = useAuth();
  const http = useHttp();
  const api = createOperationsApi(http);
  const otaApi = useOtaConnectivityApi();
  const resource = useResource(() => api.getSession(sessionId), [sessionId]);
  const roster = useResource(() => api.attendanceRoster(sessionId), [sessionId]);
  // Folio lifecycle per ticket for the payment badge; a failed read only hides the badge.
  const folioCodes = (Array.isArray(roster.data) ? roster.data : []).map(row => rosterCodes(row).ticket).filter(Boolean);
  const folioCodesKey = folioCodes.join(',');
  const sessionState = resource.data?.state;
  const folioStatuses = useResource(
    () => (folioCodes.length ? api.folioStatuses(folioCodes).catch(() => ({ statuses: {} })) : Promise.resolve({ statuses: {} })),
    [folioCodesKey, sessionState]);
  // Staff assignment, booking recovery, session cancellation and folio correction are Manager actions (the backend
  // refuses a Staff there); the role is unknown until the profile arrives, so they appear only once it is known.
  const role = useRole();
  const isManager = role === 'MANAGER';
  const [{ roster: rosterFilter }, setUrlFilters] = useUrlFilters({ roster: 'ALL' });
  const setRosterFilter = value => setUrlFilters({ roster: value });
  const command = useCommand();
  const [message, setMessage] = useState('');
  const [ticketCode, setTicketCode] = useState('');
  const [qrToken, setQrToken] = useState('');
  const [lookupBookingCode, setLookupBookingCode] = useState('');
  const [resendBookingCode, setResendBookingCode] = useState('');
  const [resendError, setResendError] = useState(null);
  // T126: the refused normal check-in (409 CHECK_IN_NOT_OPEN) and whether the exceptions panel (override) is open.
  const [checkInNotOpen, setCheckInNotOpen] = useState(null);
  const [exceptionsOpen, setExceptionsOpen] = useState(false);
  const [lookup, setLookup] = useState(null);
  const [otaBooking, setOtaBooking] = useState(null);
  const [lookupError, setLookupError] = useState('');
  const [lookupLoading, setLookupLoading] = useState(false);
  const [reasonCode, setReasonCode] = useState('CUSTOMER_DEVICE_UNAVAILABLE');
  const [reasonNote, setReasonNote] = useState('');
  const [evidenceReference, setEvidenceReference] = useState('');
  const [cancelReasonCode, setCancelReasonCode] = useState('INSTRUCTOR_UNAVAILABLE');
  const [cancelReasonNote, setCancelReasonNote] = useState('');
  const [assignmentAccountId, setAssignmentAccountId] = useState('');
  const [staffOptions, setStaffOptions] = useState(DEFAULT_STAFF_OPTIONS);
  const [customAccountId, setCustomAccountId] = useState(false);
  // T115: the Staff whose removal needs the Manager's confirmation (the last assignment after check-in opened).
  const [pendingLastRemoval, setPendingLastRemoval] = useState(null);
  // T130: why the chosen Staff cannot be assigned (STAFF_NOT_ELIGIBLE reason), shown next to the form.
  const [assignmentError, setAssignmentError] = useState('');
  const [loadingStaff, setLoadingStaff] = useState(false);
  const [bookingCode, setBookingCode] = useState('');
  const [recoveryReasonCode, setRecoveryReasonCode] = useState('PROVIDER_CONFIRMED_LATE_DEPOSIT');
  const [incidentNote, setIncidentNote] = useState('');
  const [incidentResult, setIncidentResult] = useState(null);
  // Late payments that need a Manager are a queue of their own (003.1 FR-040a), not a per-booking follow-up command.
  const [lateReviews, setLateReviews] = useState(null);
  const [lateReviewsError, setLateReviewsError] = useState('');
  const [lateReviewsLoading, setLateReviewsLoading] = useState(false);
  const [transitionError, setTransitionError] = useState(null);

  // The account list is a Manager read (a Staff caller gets 403, which the app treats as "no access" and sends home), and
  // only the Manager's assignment panel uses it, so only a Manager asks for it.
  React.useEffect(() => {
    if (status !== 'signed-in' || !isManager) return undefined;
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
  }, [status, isManager]);

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
  const rosterRows = Array.isArray(roster.data) ? roster.data : [];
  const checkedInCount = rosterRows.filter(row => row.attendanceState === 'CHECKED_IN').length;
  const rosterCounts = {
    ALL: rosterRows.length,
    CHECKED_IN: checkedInCount,
    NOT_CHECKED_IN: rosterRows.filter(row => row.attendanceState === 'NOT_CHECKED_IN').length,
    NO_SHOW: rosterRows.filter(row => row.attendanceState === 'NO_SHOW').length,
  };
  const visibleRosterRows = rosterFilter === 'ALL' ? rosterRows : rosterRows.filter(row => row.attendanceState === rosterFilter);
  const assignedStaff = (session.assignedStaffAccountIds || []).map(accountId => {
    const staff = staffOptions.find(option => Number(option.accountId) === Number(accountId));
    return { accountId, label: staff ? (staff.fullName || staff.loginEmail || `ID ${accountId}`) : `ID ${accountId}` };
  });
  // A session can have several Staff, so the fact shows the count; the Staff assignment panel lists each one.
  const assignedStaffLabel = assignedStaff.length ? `${assignedStaff.length} Staff` : 'Unassigned';

  async function run(action, success) {
    setMessage('');
    const result = await command.run(key => action(key));
    if (result.ok) { setMessage(success); resource.reload(); roster.reload(); }
  }

  // A refused state change is explained here instead of the generic notice (and a 403 does not send the user home).
  async function runTransition(state) {
    setMessage('');
    setTransitionError(null);
    const result = await command.run(key => api.transition(sessionId, state, key));
    if (result.ok) { setMessage(`Session moved to ${state}.`); resource.reload(); roster.reload(); return; }
    const text = transitionProblemText(result.error, state);
    if (text) { setTransitionError({ error: result.error, text }); command.reset(); }
  }

  // T126: a check-in refused before checkInOpensAt is explained in the desk (opens-at time).
  async function runCheckIn(body, success) {
    setMessage('');
    setCheckInNotOpen(null);
    const result = await command.run(key => api.checkIn(sessionId, body, key));
    if (result.ok) { setMessage(success); resource.reload(); roster.reload(); return; }
    if (checkInNotOpenText(result.error)) { setCheckInNotOpen(result.error); command.reset(); }
  }

  // T127 / T139: a resend refusal is explained inside the Ticket delivery panel (wait time, no eligible ticket, Booking
  // refusal) instead of the generic notice; any other failure stays in the generic notice.
  async function runResend() {
    setMessage('');
    setResendError(null);
    const result = await command.run(key => api.resendTickets(sessionId, resendBookingCode.trim(), key));
    if (result.ok) { setMessage('Ticket resend requested.'); resource.reload(); roster.reload(); return; }
    if (resendRefusalText(result.error)) { setResendError(result.error); command.reset(); }
  }

  async function removeAssignedStaff(accountId, confirmLast = false) {
    setMessage('');
    setAssignmentError('');
    setPendingLastRemoval(null);
    // A confirmed repeat is a new command, so command.run issues a new Idempotency-Key (the refused one is spent).
    const result = await command.run(key => api.removeStaff(sessionId, accountId, key, confirmLast));
    if (result.ok) { setMessage('Staff assignment removed.'); resource.reload(); roster.reload(); return; }
    if (result.error?.code === 'LAST_STAFF_ASSIGNMENT_CONFIRMATION_REQUIRED') {
      command.reset();
      setPendingLastRemoval(accountId);
    }
  }

  async function assignSelectedStaff() {
    setMessage('');
    setAssignmentError('');
    const result = await command.run(key => api.assignStaff(sessionId, assignmentAccountId, key));
    if (result.ok) { setMessage('Staff assignment saved.'); resource.reload(); roster.reload(); return; }
    if (result.error?.code === 'STAFF_NOT_ELIGIBLE') {
      command.reset();
      setAssignmentError(staffNotEligibleText(result.error.reason));
    }
  }

  async function runBookingAction(action) {
    setMessage('');
    setIncidentResult(null);
    const result = await command.run(key => action(key));
    if (result.ok) setIncidentResult(result.data || { status: 'accepted' });
  }

  async function loadLateReviews() {
    setLateReviewsLoading(true);
    setLateReviewsError('');
    try {
      setLateReviews(await listLatePaymentReviews({ status: 'PENDING_MANAGER', authorization }));
    } catch (error) {
      setLateReviews(null);
      setLateReviewsError(error?.detail || error?.message || 'Không tải được hàng đợi thanh toán về trễ.');
    } finally {
      setLateReviewsLoading(false);
    }
  }

  async function decideLateReview(review, decision) {
    setMessage('');
    const result = await command.run(key => decideLatePaymentReview(review.groupCode, review.reviewId, {
      decision, reason: getFullIncidentReason(), authorization, idempotencyKey: key,
    }));
    if (result.ok) {
      setMessage(decision === 'CONFIRM' ? 'Late payment confirmed.' : 'Refund request opened.');
      loadLateReviews();
    }
  }

  const reasonPayload = () => ({ reasonCode: reasonCode.trim(), reasonNote: reasonNote.trim() || undefined, evidenceReference: evidenceReference.trim() || undefined });
  const getFullIncidentReason = () => {
    if (recoveryReasonCode === 'OTHER_REQUIRES_NOTE') return incidentNote.trim();
    return incidentNote.trim() ? `${recoveryReasonCode}: ${incidentNote.trim()}` : recoveryReasonCode;
  };

  return <div className="ota-workspace"><main className="ota-main">
    <div className="ota-topline">MLAND / SESSION DETAIL <span>· Backend-authorized</span></div>
    <header className="ota-page-heading session-hero">
      <div>
        <p className="ota-eyebrow">SESSION CONTROL</p>
        <h1>{session.workshopDate ? `Buổi workshop ngày ${session.workshopDate}${sessionTimeRange(session) ? `, ${sessionTimeRange(session)}` : ''}` : (session.slotId || session.id)}</h1>
        <p>Location {session.locationId || '—'} · <code>{session.id}</code></p>
      </div>
      <span className={`ops-status large session-state session-state-${String(session.state).toLowerCase()}`}>{session.state}</span>
    </header>
    <SuccessNotice>{message}</SuccessNotice><ErrorNotice error={command.error} onRetry={command.retry} onReload={resource.reload} />
    {isManager && session.missingStaff && <div className="notice error" role="alert">Ca này đã mở check-in nhưng chưa có Staff nào được phân công. Hãy phân công Staff cho ca (Manager vẫn check-in được).</div>}
    <section className="ops-detail-grid">
      <article className="ota-panel">
        <div className="ota-panel-head"><h2>Check-in desk</h2></div>
        <form
          className="ota-form"
          onSubmit={e => {
            e.preventDefault();
            if (ticketCode.trim()) runCheckIn({ ticketCode: ticketCode.trim() }, 'Check-in recorded.');
            else if (qrToken.trim()) runCheckIn({ qrToken: qrToken.trim() }, 'QR check-in recorded.');
          }}
        >
          <label>
            Mã vé
            <input
              value={ticketCode}
              onChange={e => setTicketCode(e.target.value)}
              placeholder="GRP-…-L0-T1"
            />
          </label>
          <label>
            hoặc Mã QR check-in
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
        {checkInNotOpen && (
          <div className="notice error" role="alert">
            <p>{checkInNotOpenText(checkInNotOpen)}</p>
          </div>
        )}

        <div style={{ marginTop: '20px', paddingTop: '16px', borderTop: '1px solid #e5e7eb' }}>
          <p style={{ margin: '0 0 8px', fontSize: '13px', fontWeight: 600, color: '#374151' }}>
            Tra cứu vé theo Mã đặt chỗ
          </p>
          <form
            className="ota-inline-form ops-lookup"
            onSubmit={async e => {
              e.preventDefault();
              if (!lookupBookingCode.trim()) return;
              setLookupLoading(true);
              setLookupError('');
              setLookup(null);
              setOtaBooking(null);
              try {
                const res = await api.lookupBooking(sessionId, lookupBookingCode.trim());
                setLookup(res);
                try {
                  const otaRes = await otaApi.lookupBookingByMlandReference(lookupBookingCode.trim());
                  setOtaBooking(otaRes);
                } catch {
                  // Not an OTA booking
                }
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
              placeholder="GRP-…"
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
              <div style={{ fontSize: '12px', fontWeight: 600, marginBottom: '8px', color: '#374151', display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                <span>
                  Tìm thấy {lookup.length} vé cho đơn {lookupBookingCode}:
                  {otaBooking && (
                    <span style={{ marginLeft: '8px', padding: '2px 8px', borderRadius: '4px', fontSize: '11px', fontWeight: 600, background: '#e0f2fe', color: '#0369a1' }}>
                      OTA · {otaBooking.providerCode}
                    </span>
                  )}
                </span>
                {otaBooking && (
                  <Link
                    to={`/ota-connectivity/add-ons?channelBookingId=${encodeURIComponent(otaBooking.channelBookingId)}`}
                    className="button primary"
                    style={{ padding: '3px 8px', fontSize: '11px', textDecoration: 'none' }}
                  >
                    Add-on
                  </Link>
                )}
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
      <article className="ota-panel"><div className="ota-panel-head"><h2>Session facts</h2></div><dl className="ops-facts"><div><dt>Capacity</dt><dd>{session.capacity}</dd></div><div><dt>Check-in opens</dt><dd>{session.checkInOpensAt ? new Date(session.checkInOpensAt).toLocaleString() : '—'}</dd></div><div><dt>Assigned Staff</dt><dd>{assignedStaffLabel}</dd></div></dl>{transitionError && (<div className="notice error" role="alert"><p>{transitionError.text}</p>{transitionError.error.code && <p className="muted small">Code: {transitionError.error.code}</p>}{transitionError.error.correlationId && <p className="muted small">Correlation: {transitionError.error.correlationId}</p>}<div className="notice-actions"><button type="button" className="button outline" onClick={() => { setTransitionError(null); resource.reload(); }}>Tải lại trạng thái ca</button></div></div>)}<div className="ops-actions">{/* ATTENDANCE_FINALIZED is not a plain state change: only "Finalize attendance" marks no-shows and records the finalization time. */SALES_STATES.map(state => <button key={state} className="button outline" disabled={Boolean(transitionBlockedReason(session.state, state)) || command.pending} title={transitionBlockedReason(session.state, state)} onClick={() => runTransition(state)}>{state.replaceAll('_', ' ')}</button>)}<button className="button primary" disabled={command.pending || session.state !== 'IN_PROGRESS'} title={session.state !== 'IN_PROGRESS' ? 'Attendance can be finalized only while the session is IN PROGRESS.' : undefined} onClick={() => run(key => api.finalizeAttendance(sessionId, key), 'Attendance finalized.')}>Finalize attendance</button></div></article>
      <article className="ota-panel roster-panel" aria-label="Attendance roster">
        <div className="ota-panel-head">
          <h2>Attendance roster</h2>
          <span className="muted">{rosterRows.length ? `${checkedInCount}/${rosterRows.length} đã check-in` : ''}</span>
        </div>
        {rosterRows.length > 0 && (
          <>
            <div className="roster-progress" role="progressbar" aria-label="Tiến độ check-in" aria-valuemin={0}
              aria-valuemax={rosterRows.length} aria-valuenow={checkedInCount}>
              <span style={{ width: `${Math.round((checkedInCount / rosterRows.length) * 100)}%` }} />
            </div>
            <div className="roster-tabs" role="group" aria-label="Lọc theo trạng thái">
              {ROSTER_FILTERS.map(filter => (
                <button key={filter.key} type="button" aria-pressed={rosterFilter === filter.key}
                  className={`roster-tab ${rosterFilter === filter.key ? 'active' : ''}`} onClick={() => setRosterFilter(filter.key)}>
                  {filter.label} ({rosterCounts[filter.key]})
                </button>
              ))}
            </div>
          </>
        )}
        {roster.error ? <p className="muted" role="status">Không tải được danh sách vé của buổi này ({roster.error.status || roster.error.code || 'lỗi'}). <button type="button" className="button outline small" onClick={roster.reload}>Thử lại</button></p>
          : rosterRows.length === 0 ? <p className="muted">{roster.loading ? 'Đang tải…' : 'Buổi này chưa có vé hợp lệ.'}</p>
            : visibleRosterRows.length === 0 ? <p className="muted">Không có vé nào ở trạng thái này.</p> : (
              <div className="ota-ledger roster-list">
                <div className="ota-ledger-row roster-row roster-head" aria-hidden="true">
                  <span>Mã vé</span><span>Mã đặt chỗ</span><span>Trạng thái</span><span>Giờ check-in</span><span>Thao tác</span>
                </div>
                {visibleRosterRows.map(row => {
                  const pay = paymentBadge(folioStatuses.data?.statuses?.[rosterCodes(row).ticket], session.state);
                  return (
                  <div className="ota-ledger-row roster-row" key={`${row.ticketCode}`}>
                    <strong title={rosterCodes(row).internal || undefined}>{rosterCodes(row).ticket}</strong>
                    <code>{rosterCodes(row).booking}</code>
                    <span className="roster-badges">
                      <span className={`roster-state roster-${String(row.attendanceState).toLowerCase()}`}>{ROSTER_LABELS[row.attendanceState] || row.attendanceState}</span>
                      {pay && <span className={`roster-state roster-pay-${pay.key}`}>{pay.label}</span>}
                    </span>
                    <time>{row.checkedInAt ? new Date(row.checkedInAt).toLocaleString() : '—'}</time>
                    <Link className="text-link roster-folio" to={`/settlement?ticket=${encodeURIComponent(rosterCodes(row).ticket)}`}>Folio / Add-on</Link>
                  </div>
                  );
                })}
              </div>
            )}
      </article>
      {isManager && (<>
      <RescheduleQuotaPanel api={api} session={session} onSaved={resource.reload} onReload={resource.reload} />
      <article className="ota-panel">
        <div className="ota-panel-head"><h2>Staff assignment</h2></div>
        <p className="muted">
          Phân công nhân viên (Staff) phụ trách ca workshop hoặc rút quyền phụ trách. Nhân viên phải thuộc chi nhánh của ca.
        </p>
        <section aria-label="Staff đang phụ trách" className="settlement-current-staff">
          <strong>Đang phụ trách ({assignedStaff.length}):</strong>
          {assignedStaff.length === 0 ? <p className="muted">Chưa phân công</p> : (
            <ul className="session-assigned-staff">
              {assignedStaff.map(item => (
                <li key={item.accountId}>
                  <span>{item.label} <small className="muted">(ID {item.accountId})</small></span>
                  <button type="button" className="button outline small" disabled={command.pending}
                    onClick={() => removeAssignedStaff(item.accountId)}>
                    Remove
                  </button>
                </li>
              ))}
            </ul>
          )}
        </section>
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
        {assignmentError && <p role="alert" className="field-error">{assignmentError}</p>}
        {pendingLastRemoval !== null && (
          <div role="alertdialog" aria-labelledby="last-staff-title" aria-describedby="last-staff-body" className="notice error">
            <strong id="last-staff-title">Gỡ Staff cuối cùng của ca?</strong>
            <p id="last-staff-body">Đây là Staff cuối cùng được phân công và check-in của ca đã mở. Sau khi gỡ, ca sẽ không có Staff phụ trách (Manager vẫn check-in được).</p>
            <div className="ops-actions">
              <button type="button" className="button danger" disabled={command.pending} onClick={() => removeAssignedStaff(pendingLastRemoval, true)}>Xác nhận gỡ</button>
              <button type="button" className="button outline" disabled={command.pending} onClick={() => setPendingLastRemoval(null)}>Hủy</button>
            </div>
          </div>
        )}
        <div className="ops-actions">
          <button
            className="button primary"
            disabled={command.pending || !assignmentAccountId.trim()}
            onClick={assignSelectedStaff}
          >
            Assign
          </button>
          <button
            className="button outline"
            disabled={command.pending || !assignmentAccountId.trim()}
            onClick={() => removeAssignedStaff(assignmentAccountId)}
          >
            Remove
          </button>
        </div>
      </article>
      </>)}
      <details className="ops-exceptions" open={exceptionsOpen} onToggle={e => setExceptionsOpen(e.currentTarget.open)}>
        <summary>Xử lý ngoại lệ <small>điều chỉnh check-in, gửi lại vé, khôi phục đặt chỗ, hủy ca, điều chỉnh folio</small></summary>
        <div className="ops-detail-grid">
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
            placeholder="GRP-… (Mã đặt chỗ)"
          />
          <button
            className="button outline"
            disabled={command.pending || !resendBookingCode.trim()}
            onClick={runResend}
          >
            Resend ticket
          </button>
        </div>
        {resendError && <div className="notice error" role="alert"><p>{resendRefusalText(resendError)}</p></div>}
      </article>
      {isManager && (<>
      <article className="ota-panel">
        <div className="ota-panel-head"><h2>Manager booking recovery</h2></div>
        <p className="muted">Thu hồi hoặc đổi magic link truy cập của một nhóm đặt chỗ (003.1 FR-048). Thanh toán về trễ được xử lý ở hàng đợi bên dưới.</p>
        <div className="ota-form-grid" style={{ marginBottom: '16px' }}>
          <label className="full-width">
            Mã đặt chỗ cần xử lý *
            <input
              value={bookingCode}
              onChange={e => setBookingCode(e.target.value)}
              placeholder="GRP-… (Mã đặt chỗ cần xử lý)"
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
            onClick={() => runBookingAction(key => submitGroupMagicLinkIncident(bookingCode, { action: 'REVOKE', reason: getFullIncidentReason(), authorization, idempotencyKey: key }))}
          >
            Revoke magic link
          </button>
          <button
            className="button outline"
            disabled={command.pending || !bookingCode.trim() || (recoveryReasonCode === 'OTHER_REQUIRES_NOTE' && !incidentNote.trim())}
            onClick={() => runBookingAction(key => submitGroupMagicLinkIncident(bookingCode, { action: 'ROTATE', reason: getFullIncidentReason(), authorization, idempotencyKey: key }))}
          >
            Rotate magic link
          </button>
        </div>
        {incidentResult && <pre className="ops-result">{JSON.stringify(incidentResult, null, 2)}</pre>}
      </article>
      <article className="ota-panel">
        <div className="ota-panel-head"><h2>Late payment reviews</h2></div>
        <p className="muted">
          Thanh toán về trễ không tự xác nhận lại sẽ chờ Manager (003.1 FR-040a). Hàng đợi xếp theo ca bắt đầu sớm nhất;
          mỗi quyết định dùng lý do đã chuẩn hóa ở trên. CONFIRM chỉ khả dụng khi điều kiện cho phép, còn lại là REFUND
          hoàn 100% qua 003.3.
        </p>
        <div className="ops-actions">
          <button className="button outline" disabled={lateReviewsLoading} onClick={loadLateReviews}>
            {lateReviewsLoading ? 'Đang tải...' : 'Tải hàng đợi'}
          </button>
        </div>
        {lateReviewsError && <p role="alert" style={{ color: '#b00' }}>{lateReviewsError}</p>}
        {Array.isArray(lateReviews) && lateReviews.length === 0 && (
          <p className="muted">Không có thanh toán về trễ nào đang chờ Manager.</p>
        )}
        {Array.isArray(lateReviews) && lateReviews.length > 0 && (
          <ul style={{ listStyle: 'none', margin: '12px 0 0', padding: 0, display: 'grid', gap: '10px' }}>
            {lateReviews.map(review => (
              <li key={review.reviewId} style={{ border: '1px solid #dcdfe6', padding: '12px' }}>
                <strong>{review.groupCode}</strong>
                <p className="muted" style={{ margin: '4px 0', fontSize: '13px' }}>
                  Số tiền: {review.amount} · Điều kiện không đạt: {(review.failedConditions || []).join(', ') || '—'}
                  {review.resultDueAt ? ` · Phải trả lời trước: ${new Date(review.resultDueAt).toLocaleString('vi-VN')}` : ''}
                </p>
                <div className="ops-actions">
                  <button
                    className="button outline"
                    disabled={command.pending || !review.confirmAllowed || (recoveryReasonCode === 'OTHER_REQUIRES_NOTE' && !incidentNote.trim())}
                    onClick={() => decideLateReview(review, 'CONFIRM')}
                  >
                    Confirm late payment
                  </button>
                  <button
                    className="button warning"
                    disabled={command.pending || (recoveryReasonCode === 'OTHER_REQUIRES_NOTE' && !incidentNote.trim())}
                    onClick={() => decideLateReview(review, 'REFUND')}
                  >
                    Open 100% refund
                  </button>
                </div>
              </li>
            ))}
          </ul>
        )}
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
      </>)}
        </div>
      </details>
    </section></main></div>;
}
