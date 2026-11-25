/**
 * Display facts of a booking group and its ticket bookings (003.1 FR-032, FR-037, FR-042, FR-049).
 * Every amount comes from the server; nothing here recomputes a price, a discount or a collection.
 */
import { formatMoney, translate } from '../shared/i18n/I18nProvider';
import { messages } from './messages';

// Every helper below keeps its Vietnamese result when called as before; pass `lang` ('vi' | 'en') for the other one.
const L = (lang, key, params) => translate(messages, lang || 'vi', key, params);
const LOCALES = { vi: 'vi-VN', en: 'en-US' };

/** FR-037: how one ticket is paid at booking time. */
export const COLLECTION_KIND_LABEL = Object.fromEntries(['FULL', 'DEPOSIT', 'FREE', 'DEFERRED_BELOW_VNPAY_MINIMUM']
  .map(kind => [kind, L('vi', `collection.${kind}`)]));

export const GROUP_STATUS_LABEL = Object.fromEntries(['PENDING_PAYMENT', 'CONFIRMED', 'CANCELLED', 'EXPIRED']
  .map(status => [status, L('vi', `groupStatus.${status}`)]));

export const TICKET_STATUS_LABEL = Object.fromEntries(['PENDING_PAYMENT', 'CONFIRMED', 'CANCELLED', 'EXPIRED', 'COMPLETED']
  .map(status => [status, L('vi', `ticketStatus.${status}`)]));

/** FR-037: the booking-time payment is not refunded on customer self-cancellation or no-show. */
export const NON_REFUNDABLE_NOTICE = L('vi', 'helper.nonRefundable');

export const vnd = (value, lang = 'vi') => (lang === 'en'
  ? formatMoney(String(Math.round(Number(value || 0))), 'en')
  : `${Number(value || 0).toLocaleString('vi-VN')}\u00a0₫`);

export const groupStatusLabel = (status, lang = 'vi') => (status ? L(lang, `groupStatus.${status}`) : '');
export const ticketStatusLabel = (status, lang = 'vi') => (status ? L(lang, `ticketStatus.${status}`) : '');
export const nonRefundableNotice = lang => L(lang, 'helper.nonRefundable');

export const collectionKindLabel = (kind, lang = 'vi') => (COLLECTION_KIND_LABEL[kind] ? L(lang, `collection.${kind}`) : kind || '');

/**
 * FR-036/FR-037: a deferred group pays nothing online and shows no non-refundable notice. The amount shown is the
 * balance that moves to checkout, as the server reported it.
 */
export function deferredNotice(amountVnd, lang = 'vi') {
  return L(lang, 'helper.deferredNotice', { amount: vnd(amountVnd, lang) });
}

export const isDeferredGroup = paymentMode => paymentMode === 'INTERNAL_DEFERRED';
export const isFreeGroup = paymentMode => paymentMode === 'INTERNAL_FREE';
export const needsOnlinePayment = paymentMode => paymentMode === 'VNPAY';

/**
 * FR-032: the customer sees only the 10-minute payment countdown (`paymentDeadlineAt`). When it ends the screen says
 * the result is being confirmed until the internal hold (`holdExpiresAt`, 12 minutes) ends; the 2-minute buffer and
 * the 12-minute figure are never shown. After the hold ends the outcome is whatever the server reports.
 */
export function groupHoldPhase(group, now = Date.now()) {
  const { paymentDeadlineAt, holdExpiresAt } = group || {};
  const deadline = paymentDeadlineAt ? new Date(paymentDeadlineAt).getTime() : null;
  const holdEnd = holdExpiresAt ? new Date(holdExpiresAt).getTime() : null;
  if (Number.isFinite(deadline) && now < deadline) {
    return { phase: 'PAYABLE', secondsLeft: Math.max(0, Math.floor((deadline - now) / 1000)) };
  }
  if (Number.isFinite(holdEnd) && now < holdEnd) {
    return { phase: 'AWAITING_RESULT', secondsLeft: null };
  }
  return { phase: 'ENDED', secondsLeft: null };
}

export const AWAITING_RESULT_MESSAGE = L('vi', 'helper.awaitingResult');

export const awaitingResultMessage = lang => L(lang, 'helper.awaitingResult');

export function countdownText(secondsLeft, lang = 'vi') {
  if (secondsLeft === null || secondsLeft === undefined) return '';
  const minutes = Math.floor(secondsLeft / 60);
  const seconds = String(secondsLeft % 60).padStart(2, '0');
  return L(lang, 'helper.countdown', { minutes, seconds });
}

/** The balance the group still pays at checkout: the sum of its tickets' remaining amounts (FR-035). */
export function groupRemainingVnd(group) {
  return (group?.tickets || []).reduce((total, ticket) => total + Number(ticket.remainingVnd || 0), 0);
}

/** True when the ticket's folio reported its money position (add-ons and later payments included). */
export const hasCurrentMoney = ticket => ticket?.currentBalanceDueVnd !== null
  && ticket?.currentBalanceDueVnd !== undefined;

export const ATTENDANCE_LABEL = Object.fromEntries(['ATTENDED', 'NO_SHOW_PENDING', 'NO_SHOW', 'CHECKED_IN']
  .map(status => [status, L('vi', `attendance.${status}`)]));

/** FR-050: attendance belongs to one ticket, never to the group. */
export const ticketAttendanceLabel = (ticket, lang = 'vi') => (ATTENDANCE_LABEL[ticket?.attendanceStatus]
  ? L(lang, `attendance.${ticket.attendanceStatus}`)
  : null);

export const ticketNoShowNote = lang => L(lang, 'helper.noShowNote');

export const isTicketNoShow = ticket => ticket?.attendanceStatus === 'NO_SHOW_PENDING'
  || ticket?.attendanceStatus === 'NO_SHOW';

export const TICKET_NO_SHOW_NOTE = L('vi', 'helper.noShowNote');

export function formatSessionWindow(scheduledStart, scheduledEnd, lang = 'vi') {
  const locale = LOCALES[lang] || LOCALES.vi;
  if (!scheduledStart) return '';
  const start = new Date(scheduledStart);
  if (Number.isNaN(start.getTime())) return '';
  const day = start.toLocaleDateString(locale, { weekday: 'short', day: '2-digit', month: '2-digit', year: 'numeric' });
  const from = start.toLocaleTimeString(locale, { hour: '2-digit', minute: '2-digit' });
  const end = scheduledEnd ? new Date(scheduledEnd) : null;
  const to = end && !Number.isNaN(end.getTime())
    ? end.toLocaleTimeString(locale, { hour: '2-digit', minute: '2-digit' })
    : null;
  return to ? `${day} (${from} - ${to})` : `${day} (${from})`;
}

/** The cart positions a refused confirmation named (FR-031); nothing was held, so the cart stays editable. */
export function failingLinesOf(error) {
  const lines = error?.data?.failingLines;
  return Array.isArray(lines) ? lines : [];
}

/** The heading of the result screen after "giữ chỗ", worded for the group's real status and ticket count. */
export function groupResultHeading(group, now = Date.now(), lang = 'vi') {
  const count = Array.isArray(group?.tickets) ? group.tickets.length : 0;
  switch (group?.groupStatus) {
    case 'CONFIRMED': return { eyebrow: L(lang, 'heading.confirmed.eyebrow'), title: L(lang, 'heading.confirmed.title') };
    case 'EXPIRED': return { eyebrow: L(lang, 'heading.expired.eyebrow'), title: L(lang, 'heading.expired.title') };
    case 'CANCELLED': return { eyebrow: L(lang, 'heading.cancelled.eyebrow'), title: L(lang, 'heading.cancelled.title') };
    case 'PENDING_PAYMENT':
      if (group.paymentDeadlineAt && Date.parse(group.paymentDeadlineAt) <= now) {
        return { eyebrow: L(lang, 'heading.awaiting.eyebrow'), title: L(lang, 'heading.awaiting.title') };
      }
      return { eyebrow: L(lang, 'heading.held.eyebrow'), title: count > 0 ? L(lang, 'heading.held.titleCount', { count }) : L(lang, 'heading.held.title') };
    default: return { eyebrow: L(lang, 'heading.default.eyebrow'), title: L(lang, 'heading.default.title') };
  }
}
