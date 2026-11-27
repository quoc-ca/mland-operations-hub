import React, { useState } from 'react';
import { useUrlFilters } from '../../shared/ui/useUrlFilters';
import { Link, useParams } from 'react-router';
import { useAuth } from '../../shared/auth/AuthProvider';
import { useRole } from '../../shared/auth/useRole';
import { useHttp } from '../../shared/api/useHttp';
import { enumLabel, formatDateTime, translate, useEnumLabel, useFormat, useLanguage, useT } from '../../shared/i18n/I18nProvider';
import { sharedMessages } from '../../shared/ui/messages';
import { messages } from '../messages';
import { ConfirmDialog, ErrorNotice, Loading, SuccessNotice, useCommand, useErrorText, useResource } from '../../shared/ui/components';
import { OperationsAuthWorkspace } from '../../shared/ui/OperationsAuthWorkspace';
import { createOperationsApi } from '../api/operationsApi';
import { sessionTimeRange } from './OperationsDashboardPage';
import { decideLatePaymentReview, listLatePaymentReviews, submitGroupMagicLinkIncident } from '../../api/workshops';
import StaffWorkshopAddonPanel from '../components/StaffWorkshopAddonPanel';
import { RescheduleQuotaPanel } from '../components/RescheduleQuotaPanel';
import { QrCheckInScanner } from '../components/QrCheckInScanner';
import { RosterTicketDialog, rosterChange } from '../components/RosterTicketDialog';
import '../../shared/ui/workspace.css';
import '../workshop-operations.css';

// The codes are the values sent to the API; their wording is in the dictionary (ses.reason.*).
// Reasons of an undo or a correction; the server accepts both only with a note (003.2 FR-007a).
const CHECKIN_CORRECTION_REASONS = ['RECORDING_ERROR', 'OTHER_REQUIRES_NOTE'];
const CANCELLATION_REASONS = ['INSTRUCTOR_UNAVAILABLE', 'FACILITY_ISSUE', 'WEATHER_EMERGENCY', 'OTHER_REQUIRES_NOTE'];
const RECOVERY_REASONS = [
  'PROVIDER_CONFIRMED_LATE_DEPOSIT', 'CUSTOMER_PRESENTED_RECEIPT', 'LINK_COMPROMISED_SECURITY',
  'CUSTOMER_REQUESTED_NEW_LINK', 'DISPUTE_ESCALATION_FINANCE', 'OTHER_REQUIRES_NOTE',
];

// While a session is IN_PROGRESS it can only go on to finalization or cancellation (spec 003.2): it never returns to a
// sales state. The scheduler (or an explicit early start) moves it to IN_PROGRESS; states it cannot reach are greyed out.
const SALES_STATES = ['PUBLISHED', 'SALES_PAUSED', 'IN_PROGRESS'];
const TRANSITION_FROM = { PUBLISHED: ['SALES_PAUSED', 'IN_PROGRESS'], SALES_PAUSED: ['PUBLISHED', 'IN_PROGRESS'] };
// The action behind each state button; the badge keeps the state's own name.
const TRANSITION_ACTIONS = { PUBLISHED: 'ses.action.PUBLISHED', SALES_PAUSED: 'ses.action.SALES_PAUSED', IN_PROGRESS: 'ses.action.IN_PROGRESS' };
// What each panel accepts, mirroring the server: check-in (FR-010b), Staff assignment (T130), cancellation.
const CHECK_IN_STATES = ['PUBLISHED', 'SALES_PAUSED', 'IN_PROGRESS'];
const ASSIGNABLE_STATES = ['DRAFT', 'PUBLISHED', 'SALES_PAUSED', 'IN_PROGRESS'];
const NOT_CANCELLABLE_STATES = ['ATTENDANCE_FINALIZED', 'CANCELLATION_REQUESTED', 'CANCELLED'];
const transitionBlockedReason = (current, target, t) => {
  if (current === target) return t('ses.trans.same');
  if ((TRANSITION_FROM[current] || []).includes(target)) return undefined;
  if (current === 'IN_PROGRESS') return t('ses.trans.started');
  return t('ses.trans.unreachable');
};
/**
 * Why a state change was refused, in words anyone can act on. The server's English detail is matched by what it says;
 * the code and correlation stay visible under the text for whoever investigates.
 */
export function transitionProblemText(error, target, language = 'vi') {
  if (!error) return '';
  const t = (key, params) => translate(messages, language, key, params);
  const detail = String(error.message || error.detail || '');
  if (error.code === 'LOCATION_SCOPE_DENIED' || /outside .*scope/i.test(detail)) {
    return t('ses.problem.locationScope');
  }
  if (error.status === 403) {
    if (/not assigned/i.test(detail)) return t('ses.problem.notAssigned');
    if (/only a manager/i.test(detail) || target === 'PUBLISHED' || target === 'SALES_PAUSED') return t('ses.problem.managerOnly');
    if (/staff member in the session location/i.test(detail) || target === 'IN_PROGRESS') return t('ses.problem.startOnly');
    return t('ses.problem.forbidden');
  }
  if (error.status === 409) {
    if (/already in state/i.test(detail)) return t('ses.problem.alreadyIn');
    return t('ses.problem.cannotMove', { state: enumLabel(messages, language, 'sessionState', target) });
  }
  return '';
}

const ROSTER_FILTERS = ['ALL', 'CHECKED_IN', 'NOT_CHECKED_IN', 'NO_SHOW'];

/** Staff-facing text of the three resend refusals (spec FR-003c, T127, T139); null for any other failure. */
function resendRefusalText(error, t) {
  if (error?.status === 429 && error.code === 'REDELIVERY_THROTTLED') {
    const wait = error.retryAfterSeconds;
    return t('ses.resend.throttled', { wait: wait ? t('ses.resend.wait', { seconds: wait }) : '' });
  }
  if (error?.status === 409 && error.code === 'INVALID_STATE' && error.reason === 'NO_ELIGIBLE_TICKET') {
    return t('ses.resend.noTicket');
  }
  if (error?.status === 409 && error.code === 'INVALID_STATE' && error.reason === 'REDELIVERY_REJECTED_BY_BOOKING') {
    return t('ses.resend.rejected');
  }
  return null;
}

/** T126: staff-facing text of 409 CHECK_IN_NOT_OPEN ("check-in opens at ..."); null for any other failure. */
export function checkInNotOpenText(error, language = 'vi') {
  if (error?.status !== 409 || error.code !== 'CHECK_IN_NOT_OPEN') return null;
  const opensAt = error.checkInOpensAt ? new Date(error.checkInOpensAt) : null;
  const when = opensAt && !Number.isNaN(opensAt.getTime()) ? formatDateTime(error.checkInOpensAt, language) : null;
  return translate(messages, language, 'ses.opensAt', { when: when || translate(messages, language, 'ses.laterTime') });
}

/** Why one check-in was refused, in words for the counter; null when the generic notice should explain it. */
export function checkInRefusalText(error, language = 'vi') {
  if (!error) return null;
  const notOpen = checkInNotOpenText(error, language);
  if (notOpen) return notOpen;
  const key = `ses.refused.${error.status === 404 ? 'RESOURCE_NOT_FOUND' : error.code}`;
  const text = translate(messages, language, key);
  return text === key ? null : text;
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
 * Payment badge of a roster row, next to the attendance state: "Đã thanh toán" once the ticket folio is FINALIZED,
 * "Hóa đơn đã đóng" once it is CLOSED (no-show or cancellation, which is not a payment), "Chưa thanh toán" once the
 * session attendance is finalized and the folio is neither, nothing before that.
 */
export function paymentBadge(folioStatus, sessionState, language = 'vi') {
  if (folioStatus === 'FINALIZED') return { key: 'paid', label: translate(messages, language, 'ses.paid') };
  if (folioStatus === 'CLOSED') return { key: 'closed', label: translate(messages, language, 'ses.folioClosed') };
  // An unknown folio (the read failed or the ticket has none) never claims "unpaid".
  if (folioStatus && sessionState === 'ATTENDANCE_FINALIZED') return { key: 'unpaid', label: translate(messages, language, 'ses.unpaid') };
  return null;
}

export default function OperationsSessionPage() {
  const t = useT(messages);
  const ts = useT(sharedMessages);
  const fmt = useFormat();
  const { language } = useLanguage();
  const stateLabel = useEnumLabel(messages, 'sessionState');
  const attendanceLabel = useEnumLabel(messages, 'attendance');
  const validityLabel = useEnumLabel(messages, 'ticketValidity');
  const conditionLabel = useEnumLabel(messages, 'lateCondition');
  const { sessionId } = useParams();
  const { status, user } = useAuth();
  const http = useHttp();
  const api = createOperationsApi(http);
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
  const errorText = useErrorText(messages);
  const [message, setMessage] = useState('');
  const [ticketCode, setTicketCode] = useState('');
  const [scanning, setScanning] = useState(false);
  // The ticket code (as the server stores it) of the roster row that is open.
  const [openTicket, setOpenTicket] = useState(null);
  const [lookupBookingCode, setLookupBookingCode] = useState('');
  const [resendBookingCode, setResendBookingCode] = useState('');
  const [resendError, setResendError] = useState(null);
  // T126: the refused normal check-in (409 CHECK_IN_NOT_OPEN) and whether the exceptions panel is open.
  const [checkInNotOpen, setCheckInNotOpen] = useState(null);
  const [exceptionsOpen, setExceptionsOpen] = useState(false);
  const [lookup, setLookup] = useState(null);
  const [lookupError, setLookupError] = useState('');
  const [lookupLoading, setLookupLoading] = useState(false);
  const [cancelReasonCode, setCancelReasonCode] = useState('INSTRUCTOR_UNAVAILABLE');
  const [cancelReasonNote, setCancelReasonNote] = useState('');
  const [assignmentAccountId, setAssignmentAccountId] = useState('');
  const [customAccountId, setCustomAccountId] = useState(false);
  // T115: the Staff whose removal needs the Manager's confirmation (the last assignment after check-in opened).
  const [pendingLastRemoval, setPendingLastRemoval] = useState(null);
  const [confirming, setConfirming] = useState(null); // 'finalize' | 'cancel' | 'REVOKE' | 'ROTATE'
  // T130: why the chosen Staff cannot be assigned (STAFF_NOT_ELIGIBLE reason), shown next to the form.
  const [assignmentError, setAssignmentError] = useState('');
  const [bookingCode, setBookingCode] = useState('');
  const [recoveryReasonCode, setRecoveryReasonCode] = useState('PROVIDER_CONFIRMED_LATE_DEPOSIT');
  const [incidentNote, setIncidentNote] = useState('');
  // Late payments that need a Manager are a queue of their own (003.1 FR-040a), not a per-booking follow-up command.
  const [lateReviews, setLateReviews] = useState(null);
  const [lateReviewsError, setLateReviewsError] = useState('');
  const [lateReviewsLoading, setLateReviewsLoading] = useState(false);
  const [transitionError, setTransitionError] = useState(null);

  // The account list is a Manager read (a Staff caller gets 403, which the app treats as "no access" and sends home), and
  // only the Manager's assignment panel uses it, so only a Manager asks for it.
  const staffList = useResource(
    () => (status === 'signed-in' && isManager
      ? http('/api/v1/accounts?role=STAFF&status=ACTIVE&size=100')
      : Promise.resolve(null)),
    [status, isManager]);
  const staffOptions = [staffList.data?.data, staffList.data?.content, staffList.data].find(Array.isArray) || [];

  if (status === 'loading') return <Loading />;
  if (status !== 'signed-in') {
    return (
      <OperationsAuthWorkspace
        topline={t('topline.operations')}
        message={t('ses.signIn')}
      />
    );
  }
  // Only the first read replaces the page: a reload after a command keeps the content (and the camera) on screen.
  if (resource.loading && !resource.data) return <Loading />;
  if (resource.error && !resource.data) return <div className="wsp-container"><ErrorNotice error={resource.error} onRetry={resource.reload} /></div>;
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
  const openRow = openTicket ? rosterRows.find(row => row.ticketCode === openTicket) : null;
  const visibleRosterRows = rosterFilter === 'ALL' ? rosterRows : rosterRows.filter(row => row.attendanceState === rosterFilter);
  const assignedStaff = (session.assignedStaffAccountIds || []).map(accountId => {
    const staff = staffOptions.find(option => Number(option.accountId) === Number(accountId));
    return { accountId, label: staff?.fullName || `ID ${accountId}` };
  });
  const assignableStaff = staffOptions.filter(option => !assignedStaff.some(item => Number(item.accountId) === Number(option.accountId)));
  const checkInOpen = CHECK_IN_STATES.includes(session.state);
  const canAssign = ASSIGNABLE_STATES.includes(session.state);
  // A Staff can only start a session; opening and pausing sales are Manager actions.
  const stateActions = isManager ? SALES_STATES : ['IN_PROGRESS'];
  // A session can have several Staff, so the fact shows the count; the Staff assignment panel lists each one.
  const assignedStaffLabel = assignedStaff.length ? t('ses.staffCount', { count: assignedStaff.length }) : t('ses.unassigned');

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
    if (result.ok) { setMessage(t('ses.ok.moved', { state: stateLabel(state) })); resource.reload(); roster.reload(); return; }
    const text = transitionProblemText(result.error, state, language);
    if (text) { setTransitionError({ error: result.error, text }); command.reset(); }
  }

  // T126: a check-in refused before checkInOpensAt is explained in the desk (opens-at time).
  async function runCheckIn(body, success) {
    setMessage('');
    setCheckInNotOpen(null);
    const result = await command.run(key => api.checkIn(sessionId, body, key));
    if (result.ok) {
      // A repeat scan answers 200 ALREADY_CHECKED_IN: say so, with the recorded check-in time only.
      const repeat = result.result?.checkInResult === 'ALREADY_CHECKED_IN';
      setMessage(repeat ? t('ses.alreadyCheckedIn', { when: formatDateTime(result.result.checkedInAt, language) }) : success);
      resource.reload(); roster.reload(); return result;
    }
    if (checkInNotOpenText(result.error, language)) { setCheckInNotOpen(result.error); command.reset(); }
    return result;
  }

  // FR-057: one scanned QR is one normal check-in; the scan dialog shows what happened.
  async function scanTicket(token) {
    const result = await runCheckIn({ qrToken: token }, t('ses.ok.qrCheckIn'));
    if (!result.ok) {
      const refusal = checkInRefusalText(result.error, language);
      command.reset();
      return { tone: 'error', title: t('scan.refused'), detail: refusal || errorText(result.error) };
    }
    const code = result.result?.ticketCode;
    const row = rosterRows.find(item => item.ticketCode === code);
    const ticket = row ? rosterCodes(row).ticket : code;
    if (result.result?.checkInResult === 'ALREADY_CHECKED_IN') {
      return { tone: 'repeat', title: t('scan.repeat'), detail: [ticket, t('ses.alreadyCheckedIn', { when: formatDateTime(result.result.checkedInAt, language) })].filter(Boolean).join(' · ') };
    }
    return { tone: 'success', title: t('scan.success'), detail: ticket };
  }

  // T127 / T139: a resend refusal is explained inside the Ticket delivery panel (wait time, no eligible ticket, Booking
  // refusal) instead of the generic notice; any other failure stays in the generic notice.
  async function runResend() {
    setMessage('');
    setResendError(null);
    const result = await command.run(key => api.resendTickets(sessionId, resendBookingCode.trim(), key));
    if (result.ok) { setMessage(t('ses.ok.resend')); resource.reload(); roster.reload(); return; }
    if (resendRefusalText(result.error, t)) { setResendError(result.error); command.reset(); }
  }

  async function removeAssignedStaff(accountId, confirmLast = false) {
    setMessage('');
    setAssignmentError('');
    setPendingLastRemoval(null);
    // A confirmed repeat is a new command, so command.run issues a new Idempotency-Key (the refused one is spent).
    const result = await command.run(key => api.removeStaff(sessionId, accountId, key, confirmLast));
    if (result.ok) { setMessage(t('ses.ok.staffRemoved')); resource.reload(); roster.reload(); return; }
    if (result.error?.code === 'LAST_STAFF_ASSIGNMENT_CONFIRMATION_REQUIRED') {
      command.reset();
      setPendingLastRemoval(accountId);
    }
  }

  async function assignSelectedStaff() {
    setMessage('');
    setAssignmentError('');
    const result = await command.run(key => api.assignStaff(sessionId, assignmentAccountId, key));
    if (result.ok) {
      setMessage(t('ses.ok.staffSaved'));
      setAssignmentAccountId('');
      setCustomAccountId(false);
      resource.reload(); roster.reload(); return;
    }
    if (result.error?.code === 'STAFF_NOT_ELIGIBLE') {
      command.reset();
      const reasonKey = `ses.notEligible.${result.error.reason}`;
      setAssignmentError(t(t(reasonKey) === reasonKey ? 'ses.notEligible.default' : reasonKey));
    }
  }

  async function runLinkIncident(action) {
    setMessage('');
    const code = bookingCode.trim();
    const result = await command.run(key => submitGroupMagicLinkIncident(code, {
      action, reason: getFullIncidentReason(), authorization, idempotencyKey: key,
    }));
    if (result.ok) setMessage(t(`ses.recovery.done.${action}`, { code: result.result?.groupCode || code }));
  }

  async function loadLateReviews() {
    setLateReviewsLoading(true);
    setLateReviewsError('');
    try {
      setLateReviews(await listLatePaymentReviews({ status: 'PENDING_MANAGER', authorization }));
    } catch (error) {
      setLateReviews(null);
      setLateReviewsError(error?.detail || error?.message || t('late.loadFailed'));
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
      setMessage(decision === 'CONFIRM' ? t('late.confirmed') : t('ses.late.refundOpened'));
      loadLateReviews();
    }
  }

  const getFullIncidentReason = () => {
    if (recoveryReasonCode === 'OTHER_REQUIRES_NOTE') return incidentNote.trim();
    return incidentNote.trim() ? `${recoveryReasonCode}: ${incidentNote.trim()}` : recoveryReasonCode;
  };

  return <div className="wsp-workspace"><main className="wsp-main">
    <div className="wsp-topline">{t('topline.session')} <span>· {t('authorized')}</span></div>
    <header className="wsp-page-heading session-hero">
      <div>
        <p className="wsp-eyebrow">{t('ses.eyebrow')}</p>
        <h1>{session.workshopDate ? t('ses.title', { date: session.workshopDate, time: sessionTimeRange(session) ? `, ${sessionTimeRange(session)}` : '' }) : (session.slotId || session.id)}</h1>
        <p>{session.locationName || '—'}</p>
      </div>
      <span className={`ops-status large session-state session-state-${String(session.state).toLowerCase()}`}>{stateLabel(session.state)}</span>
    </header>
    {/* Stays in view, so the result of an action far down the page is seen. */}
    <div className="ops-feedback">
      <SuccessNotice>{message}</SuccessNotice><ErrorNotice error={command.error} onRetry={command.retry} onReload={resource.reload} />
      <ErrorNotice error={resource.error} onRetry={resource.reload} />
    </div>
    {scanning && checkInOpen && <QrCheckInScanner onScan={scanTicket} onClose={() => setScanning(false)} />}
    {openRow && <RosterTicketDialog key={openRow.ticketCode} api={api} session={{ ...session, id: sessionId }} row={openRow}
      ticketCode={rosterCodes(openRow).ticket} role={role}
      folioStatus={folioStatuses.data?.statuses?.[rosterCodes(openRow).ticket]} reasons={CHECKIN_CORRECTION_REASONS}
      explain={error => checkInRefusalText(error, language)} onClose={() => setOpenTicket(null)}
      onDone={text => { setOpenTicket(null); command.reset(); setCheckInNotOpen(null); setMessage(text); resource.reload(); roster.reload(); }} />}
    <ConfirmDialog open={confirming === 'finalize'} title={t('ses.confirm.finalizeTitle')} confirmLabel={t('ses.confirm.finalize')}
      onCancel={() => setConfirming(null)}
      onConfirm={() => { setConfirming(null); run(key => api.finalizeAttendance(sessionId, key), t('ses.ok.finalized')); }}>
      <p>{t('ses.confirm.finalizeBody', { checked: checkedInCount, total: rosterRows.length, rest: rosterRows.length - checkedInCount })}</p>
    </ConfirmDialog>
    <ConfirmDialog open={confirming === 'cancel'} danger title={t('ses.confirm.cancelTitle')} confirmLabel={t('ses.confirm.cancel')}
      onCancel={() => setConfirming(null)}
      onConfirm={() => { setConfirming(null); run(key => api.cancelSession(sessionId, { reasonCode: cancelReasonCode, reasonNote: cancelReasonNote.trim() }, key), t('ses.ok.cancelRequested')); }}>
      <p>{t('ses.confirm.cancelBody', { total: rosterRows.length })}</p>
    </ConfirmDialog>
    {['REVOKE', 'ROTATE'].map(action => (
      <ConfirmDialog key={action} open={confirming === action} danger title={t(`ses.confirm.${action}.title`)}
        confirmLabel={t(`ses.confirm.${action}.ok`)} onCancel={() => setConfirming(null)}
        onConfirm={() => { setConfirming(null); runLinkIncident(action); }}>
        <p>{t(`ses.confirm.${action}.body`, { code: bookingCode.trim() })}</p>
      </ConfirmDialog>
    ))}
    {isManager && session.missingStaff && <div className="notice error" role="alert">{t('ses.missingStaff')}</div>}
    <section className="ops-detail-grid">
      <article className="wsp-panel">
        <div className="wsp-panel-head"><h2>{t('ses.desk')}</h2></div>
        {!checkInOpen && <p className="muted" role="note">{t(`ses.desk.closed.${session.state}`)}</p>}
        {checkInOpen && (<>
        <button type="button" className="button primary ops-scan-button" onClick={() => { setMessage(''); setCheckInNotOpen(null); command.reset(); setScanning(true); }}>
          {t('scan.open')}
        </button>
        <form
          className="wsp-form"
          onSubmit={e => {
            e.preventDefault();
            if (ticketCode.trim()) runCheckIn({ ticketCode: ticketCode.trim() }, t('ses.ok.checkIn'));
          }}
        >
          <label>
            {t('ses.ticketCode')}
            <input
              value={ticketCode}
              onChange={e => setTicketCode(e.target.value)}
              placeholder="GRP-…-L0-T1"
            />
          </label>
          <button
            className="button primary"
            type="submit"
            disabled={command.pending || !ticketCode.trim()}
          >
            {t('ses.checkIn')}
          </button>
        </form>
        {checkInNotOpen && (
          <div className="notice error" role="alert">
            <p>{checkInNotOpenText(checkInNotOpen, language)}</p>
          </div>
        )}
        </>)}

        <div style={{ marginTop: '20px', paddingTop: '16px', borderTop: '1px solid #e5e7eb' }}>
          <p style={{ margin: '0 0 8px', fontSize: '13px', fontWeight: 600, color: '#374151' }}>
            {t('ses.lookupTitle')}
          </p>
          <form
            className="wsp-inline-form ops-lookup"
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
                setLookupError(err?.message || t('ses.lookupNone'));
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
              {lookupLoading ? t('ses.lookingUp') : t('ses.lookupBtn')}
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
                {t('ses.lookupFound', { count: lookup.length, code: lookupBookingCode })}
              </div>
              <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
                {lookup.map((ticket, idx) => (
                  <div key={idx} style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '8px 12px', background: '#fff', borderRadius: '6px', border: '1px solid #e5e7eb', fontSize: '13px' }}>
                    <div>
                      <strong>{ticket.ticketCode}</strong>
                      <span className={`roster-state roster-${String(ticket.attendanceState).toLowerCase()}`} style={{ marginLeft: '8px' }}>
                        {attendanceLabel(ticket.attendanceState)}
                      </span>
                      <span style={{ marginLeft: '6px', padding: '2px 6px', borderRadius: '4px', fontSize: '11px', background: '#f3f4f6', color: '#4b5563' }}>
                        {validityLabel(ticket.validityState)}
                      </span>
                    </div>
                    {checkInOpen && (
                      <button
                        type="button"
                        className="button outline"
                        style={{ padding: '3px 8px', fontSize: '11px' }}
                        onClick={() => setTicketCode(ticket.ticketCode)}
                      >
                        {t('ses.pickTicket')}
                      </button>
                    )}
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>
      </article>
      <article className="wsp-panel"><div className="wsp-panel-head"><h2>{t('ses.facts')}</h2></div><dl className="ops-facts"><div><dt>{t('ses.capacity')}</dt><dd>{session.capacity}</dd></div><div><dt>{t('ses.checkInOpens')}</dt><dd>{session.checkInOpensAt ? fmt.dateTime(session.checkInOpensAt) : '—'}</dd></div><div><dt>{t('ses.assignedStaff')}</dt><dd>{assignedStaffLabel}</dd></div></dl>{transitionError && (<div className="notice error" role="alert"><p>{transitionError.text}</p>{transitionError.error.code && <p className="muted small">{t('ses.codeLine', { code: transitionError.error.code })}</p>}{transitionError.error.correlationId && <p className="muted small">{t('ses.correlation', { id: transitionError.error.correlationId })}</p>}<div className="notice-actions"><button type="button" className="button outline" onClick={() => { setTransitionError(null); resource.reload(); }}>{t('ses.reloadState')}</button></div></div>)}<div className="ops-actions">{/* ATTENDANCE_FINALIZED is not a plain state change: only "Finalize attendance" marks no-shows and records the finalization time. */stateActions.map(state => <button key={state} className="button outline" disabled={Boolean(transitionBlockedReason(session.state, state, t)) || command.pending} title={transitionBlockedReason(session.state, state, t)} onClick={() => runTransition(state)}>{t(TRANSITION_ACTIONS[state])}</button>)}<button className="button primary" disabled={command.pending || session.state !== 'IN_PROGRESS'} title={session.state !== 'IN_PROGRESS' ? t('ses.finalizeTitle') : undefined} onClick={() => setConfirming('finalize')}>{t('ses.finalize')}</button></div></article>
      <article className="wsp-panel roster-panel" aria-label={t('ses.roster')}>
        <div className="wsp-panel-head">
          <h2>{t('ses.roster')}</h2>
          <span className="muted">{rosterRows.length ? t('ses.rosterProgress', { checked: checkedInCount, total: rosterRows.length }) : ''}</span>
        </div>
        {rosterRows.length > 0 && (
          <>
            <div className="roster-progress" role="progressbar" aria-label={t('ses.rosterProgressAria')} aria-valuemin={0}
              aria-valuemax={rosterRows.length} aria-valuenow={checkedInCount}>
              <span style={{ width: `${Math.round((checkedInCount / rosterRows.length) * 100)}%` }} />
            </div>
            <div className="roster-tabs" role="group" aria-label={t('ses.rosterFilterAria')}>
              {ROSTER_FILTERS.map(key => (
                <button key={key} type="button" aria-pressed={rosterFilter === key}
                  className={`roster-tab ${rosterFilter === key ? 'active' : ''}`} onClick={() => setRosterFilter(key)}>
                  {key === 'ALL' ? t('ses.filter.ALL') : attendanceLabel(key)} ({rosterCounts[key]})
                </button>
              ))}
            </div>
          </>
        )}
        {roster.error ? <p className="muted" role="status">{t('ses.rosterError', { code: roster.error.status || roster.error.code || t('ses.errorWord') })} <button type="button" className="button outline small" onClick={roster.reload}>{ts('retry')}</button></p>
          : rosterRows.length === 0 ? <p className="muted">{roster.loading ? ts('loading') : t('ses.rosterEmpty')}</p>
            : visibleRosterRows.length === 0 ? <p className="muted">{t('ses.rosterNoMatch')}</p> : (
              <div className="wsp-ledger roster-list">
                <div className="wsp-ledger-row roster-row roster-head" aria-hidden="true">
                  <span>{t('ses.col.ticket')}</span><span>{t('ses.col.booking')}</span><span>{t('ses.col.status')}</span><span>{t('ses.col.checkedInAt')}</span><span>{t('ses.col.actions')}</span>
                </div>
                {visibleRosterRows.map(row => {
                  const pay = paymentBadge(folioStatuses.data?.statuses?.[rosterCodes(row).ticket], session.state, language);
                  const { change, expired } = rosterChange(role, session, row, folioStatuses.data?.statuses?.[rosterCodes(row).ticket]);
                  return (
                  // The ticket button is the keyboard way in; the rest of the row is a larger target for the same action.
                  <div className="wsp-ledger-row roster-row roster-clickable" key={`${row.ticketCode}`}
                    onClick={event => { if (!event.target.closest('a')) setOpenTicket(row.ticketCode); }}>
                    <strong title={rosterCodes(row).internal || undefined}><button type="button" className="roster-open">{rosterCodes(row).ticket}</button></strong>
                    <code>{rosterCodes(row).booking}</code>
                    <span className="roster-badges">
                      <span className={`roster-state roster-${String(row.attendanceState).toLowerCase()}`}>{attendanceLabel(row.attendanceState)}</span>
                      {pay && <span className={`roster-state roster-pay-${pay.key}`}>{pay.label}</span>}
                    </span>
                    <time>{row.checkedInAt ? fmt.dateTime(row.checkedInAt) : '—'}</time>
                    <span className="roster-actions">
                      {/* The click bubbles to the row, which opens the ticket. */}
                      {change && <button type="button" className="button outline small">{t(change === 'reverse' ? 'ses.row.undo' : 'ses.row.correct')}</button>}
                      {expired && <span className="muted small">{t('ses.row.expired')}</span>}
                      <Link className="text-link roster-folio" to={`/settlement?ticket=${encodeURIComponent(rosterCodes(row).ticket)}`}>{t('ses.folio')}</Link>
                    </span>
                  </div>
                  );
                })}
              </div>
            )}
      </article>
      {isManager && (<>
      <RescheduleQuotaPanel api={api} session={session} onSaved={resource.reload} onReload={resource.reload} />
      <article className="wsp-panel">
        <div className="wsp-panel-head"><h2>{t('ses.staff.title')}</h2></div>
        <p className="muted">
          {t('ses.staff.intro')}
        </p>
        <section aria-label={t('ses.staff.currentAria')} className="settlement-current-staff">
          <strong>{t('ses.staff.current', { count: assignedStaff.length })}</strong>
          {assignedStaff.length === 0 ? <p className="muted">{t('ses.staff.none')}</p> : (
            <ul className="session-assigned-staff">
              {assignedStaff.map(item => (
                <li key={item.accountId}>
                  <span>{item.label} <small className="muted">{t('ses.staff.id', { id: item.accountId })}</small></span>
                  <button type="button" className="button outline small" disabled={command.pending}
                    onClick={() => removeAssignedStaff(item.accountId)}>
                    {t('ses.staff.remove')}
                  </button>
                </li>
              ))}
            </ul>
          )}
        </section>
        {!canAssign && <p className="muted" role="note">{t('ses.staff.closed')}</p>}
        {staffList.error && (
          <div className="notice error" role="alert">
            <p>{t('ses.staff.loadError')}</p>
            <div className="notice-actions"><button type="button" className="button outline" onClick={staffList.reload}>{ts('retry')}</button></div>
          </div>
        )}
        {canAssign && (
          <div className="wsp-form-grid" style={{ marginBottom: '16px' }}>
            <label className="full-width">
              {t('ses.staff.pick')}
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
                <option value="">{t('ses.staff.pickOption')}</option>
                {assignableStaff.map(s => (
                  <option key={s.accountId} value={String(s.accountId)}>
                    {s.fullName ? `${s.fullName} — ` : ''}{t('ses.staff.optionId', { id: s.accountId })}
                  </option>
                ))}
                <option value="__OTHER__">{t('ses.staff.other')}</option>
              </select>
            </label>
            {customAccountId && (
              <label className="full-width">
                {t('ses.staff.customLabel')}
                <input
                  value={assignmentAccountId}
                  onChange={e => setAssignmentAccountId(e.target.value)}
                  inputMode="numeric"
                  placeholder={t('ses.staff.customPlaceholder')}
                  autoFocus
                />
              </label>
            )}
          </div>
        )}
        {assignmentError && <p role="alert" className="field-error">{assignmentError}</p>}
        {pendingLastRemoval !== null && (
          <div role="alertdialog" aria-labelledby="last-staff-title" aria-describedby="last-staff-body" className="notice error">
            <strong id="last-staff-title">{t('ses.staff.lastTitle')}</strong>
            <p id="last-staff-body">{t('ses.staff.lastBody')}</p>
            <div className="ops-actions">
              <button type="button" className="button danger" disabled={command.pending} onClick={() => removeAssignedStaff(pendingLastRemoval, true)}>{t('ses.staff.confirmRemove')}</button>
              <button type="button" className="button outline" disabled={command.pending} onClick={() => setPendingLastRemoval(null)}>{ts('cancel')}</button>
            </div>
          </div>
        )}
        {canAssign && (
          <div className="ops-actions">
            <button
              className="button primary"
              disabled={command.pending || !assignmentAccountId.trim()}
              onClick={assignSelectedStaff}
            >
              {t('ses.staff.assign')}
            </button>
          </div>
        )}
      </article>
      </>)}
      <details className="ops-exceptions" open={exceptionsOpen} onToggle={e => setExceptionsOpen(e.currentTarget.open)}>
        <summary>{t('ses.exceptions')} <small>{t('ses.exceptionsHint')}</small></summary>
        <div className="ops-detail-grid">
      <article className="wsp-panel">
        <div className="wsp-panel-head"><h2>{t('ses.delivery.title')}</h2></div>
        <p className="muted">{t('ses.delivery.intro')}</p>
        <div className="wsp-inline-form">
          <input
            value={resendBookingCode}
            onChange={e => setResendBookingCode(e.target.value)}
            placeholder={t('ses.delivery.placeholder')}
          />
          <button
            className="button outline"
            disabled={command.pending || !resendBookingCode.trim()}
            onClick={runResend}
          >
            {t('ses.delivery.resend')}
          </button>
        </div>
        {resendError && <div className="notice error" role="alert"><p>{resendRefusalText(resendError, t)}</p></div>}
      </article>
      {isManager && (<>
      <article className="wsp-panel">
        <div className="wsp-panel-head"><h2>{t('ses.recovery.title')}</h2></div>
        <p className="muted">{t('ses.recovery.intro')}</p>
        <div className="wsp-form-grid" style={{ marginBottom: '16px' }}>
          <label className="full-width">
            {t('ses.recovery.bookingCode')}
            <input
              value={bookingCode}
              onChange={e => setBookingCode(e.target.value)}
              placeholder={t('ses.recovery.bookingPlaceholder')}
              required
            />
          </label>
          <label className="full-width">
            {t('ses.recovery.reason')}
            <select
              value={recoveryReasonCode}
              onChange={e => setRecoveryReasonCode(e.target.value)}
            >
              {RECOVERY_REASONS.map(code => (
                <option key={code} value={code}>{t(`ses.reason.recovery.${code}`)}</option>
              ))}
            </select>
          </label>
          <label className="full-width">
            {t('ses.recovery.note', { mark: recoveryReasonCode === 'OTHER_REQUIRES_NOTE' ? '*' : t('late.noteOptional') })}
            <textarea
              value={incidentNote}
              onChange={e => setIncidentNote(e.target.value)}
              placeholder={t('ses.recovery.notePlaceholder')}
              required={recoveryReasonCode === 'OTHER_REQUIRES_NOTE'}
            />
          </label>
        </div>
        <div className="ops-actions">
          <button
            className="button outline"
            disabled={command.pending || !bookingCode.trim() || (recoveryReasonCode === 'OTHER_REQUIRES_NOTE' && !incidentNote.trim())}
            onClick={() => setConfirming('REVOKE')}
          >
            {t('ses.recovery.revoke')}
          </button>
          <button
            className="button outline"
            disabled={command.pending || !bookingCode.trim() || (recoveryReasonCode === 'OTHER_REQUIRES_NOTE' && !incidentNote.trim())}
            onClick={() => setConfirming('ROTATE')}
          >
            {t('ses.recovery.rotate')}
          </button>
        </div>
      </article>
      <article className="wsp-panel">
        <div className="wsp-panel-head"><h2>{t('ses.late.title')}</h2></div>
        <p className="muted">
          {t('ses.late.intro')}
        </p>
        <div className="ops-actions">
          <button className="button outline" disabled={lateReviewsLoading} onClick={loadLateReviews}>
            {lateReviewsLoading ? ts('loading') : t('ses.late.load')}
          </button>
        </div>
        {lateReviewsError && <p role="alert" style={{ color: '#b00' }}>{lateReviewsError}</p>}
        {Array.isArray(lateReviews) && lateReviews.length === 0 && (
          <p className="muted">{t('late.empty')}</p>
        )}
        {Array.isArray(lateReviews) && lateReviews.length > 0 && (
          <ul style={{ listStyle: 'none', margin: '12px 0 0', padding: 0, display: 'grid', gap: '10px' }}>
            {lateReviews.map(review => (
              <li key={review.reviewId} style={{ border: '1px solid #dcdfe6', padding: '12px' }}>
                <strong>{review.groupCode}</strong>
                <p className="muted" style={{ margin: '4px 0', fontSize: '13px' }}>
                  {t('late.amount', { amount: fmt.money(review.amount) })} · {t('late.failedConditions', { list: (review.failedConditions || []).map(conditionLabel).join(', ') || '—' })}
                  {review.resultDueAt ? ` · ${t('late.due', { time: fmt.dateTime(review.resultDueAt) })}` : ''}
                </p>
                <div className="ops-actions">
                  <button
                    className="button outline"
                    disabled={command.pending || !review.confirmAllowed || (recoveryReasonCode === 'OTHER_REQUIRES_NOTE' && !incidentNote.trim())}
                    onClick={() => decideLateReview(review, 'CONFIRM')}
                  >
                    {t('late.confirm')}
                  </button>
                  <button
                    className="button warning"
                    disabled={command.pending || (recoveryReasonCode === 'OTHER_REQUIRES_NOTE' && !incidentNote.trim())}
                    onClick={() => decideLateReview(review, 'REFUND')}
                  >
                    {t('late.refund')}
                  </button>
                </div>
              </li>
            ))}
          </ul>
        )}
      </article>
      {!NOT_CANCELLABLE_STATES.includes(session.state) && (
      <article className="wsp-panel">
        <div className="wsp-panel-head"><h2>{t('ses.cancel.title')}</h2></div>
        <p className="muted">{t('ses.cancel.intro')}</p>
        <div className="wsp-form-grid" style={{ marginBottom: '12px' }}>
          <label className="full-width">
            {t('ses.cancel.reason')}
            <select value={cancelReasonCode} onChange={e => setCancelReasonCode(e.target.value)}>
              {CANCELLATION_REASONS.map(code => (
                <option key={code} value={code}>{t(`ses.reason.cancel.${code}`)}</option>
              ))}
            </select>
          </label>
          <label className="full-width">
            {t('ses.cancel.note')}
            <textarea
              value={cancelReasonNote}
              onChange={e => setCancelReasonNote(e.target.value)}
              placeholder={t('ses.cancel.notePlaceholder')}
            />
          </label>
        </div>
        <button
          className="button danger"
          disabled={command.pending || !cancelReasonNote.trim()}
          onClick={() => setConfirming('cancel')}
        >
          {t('ses.cancel.request')}
        </button>
      </article>
      )}
      <StaffWorkshopAddonPanel http={http} />
      </>)}
        </div>
      </details>
    </section></main></div>;
}
