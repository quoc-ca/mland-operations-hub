/**
 * Display facts of a booking group and its ticket bookings (003.1 FR-032, FR-037, FR-042, FR-049).
 * Every amount comes from the server; nothing here recomputes a price, a discount or a collection.
 */

/** FR-037: how one ticket is paid at booking time. */
export const COLLECTION_KIND_LABEL = {
  FULL: 'Thanh toán toàn bộ',
  DEPOSIT: 'Cọc',
  FREE: 'Miễn phí',
  DEFERRED_BELOW_VNPAY_MINIMUM: 'Thanh toán tại quầy',
};

export const GROUP_STATUS_LABEL = {
  PENDING_PAYMENT: 'Chờ thanh toán',
  CONFIRMED: 'Đã xác nhận',
  CANCELLED: 'Đã hủy',
  EXPIRED: 'Hết hạn giữ chỗ',
};

export const TICKET_STATUS_LABEL = {
  PENDING_PAYMENT: 'Chờ thanh toán',
  CONFIRMED: 'Đã xác nhận',
  CANCELLED: 'Đã hủy',
  EXPIRED: 'Hết hạn giữ chỗ',
  COMPLETED: 'Đã hoàn thành',
};

/** FR-037: the booking-time payment is not refunded on customer self-cancellation or no-show. */
export const NON_REFUNDABLE_NOTICE = 'Khoản thanh toán lúc đặt không được hoàn lại nếu bạn tự hủy hoặc không tham dự.';

export const vnd = value => `${Number(value || 0).toLocaleString('vi-VN')} ₫`;

export const collectionKindLabel = kind => COLLECTION_KIND_LABEL[kind] || kind || '';

/**
 * FR-036/FR-037: a deferred group pays nothing online and shows no non-refundable notice. The amount shown is the
 * balance that moves to checkout, as the server reported it.
 */
export function deferredNotice(amountVnd) {
  return `Không cần thanh toán online, thanh toán ${vnd(amountVnd)} tại quầy khi checkout`;
}

export const isDeferredGroup = paymentMode => paymentMode === 'INTERNAL_DEFERRED';
export const isFreeGroup = paymentMode => paymentMode === 'INTERNAL_FREE';
export const needsOnlinePayment = paymentMode => paymentMode === 'VNPAY';

/**
 * FR-032: the customer sees only the 10-minute payment countdown (`paymentDeadlineAt`). When it ends the screen says
 * the result is being confirmed until the internal hold (`holdExpiresAt`, 12 minutes) ends; the 2-minute buffer and
 * the 12-minute figure are never shown. After the hold ends the outcome is whatever the server reports.
 */
export function groupHoldPhase({ paymentDeadlineAt, holdExpiresAt } = {}, now = Date.now()) {
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

export const AWAITING_RESULT_MESSAGE = 'Đang xác nhận kết quả thanh toán…';

export function countdownText(secondsLeft) {
  if (secondsLeft === null || secondsLeft === undefined) return '';
  const minutes = Math.floor(secondsLeft / 60);
  const seconds = String(secondsLeft % 60).padStart(2, '0');
  return `${minutes} phút ${seconds} giây`;
}

/** The balance the group still pays at checkout: the sum of its tickets' remaining amounts (FR-035). */
export function groupRemainingVnd(group) {
  return (group?.tickets || []).reduce((total, ticket) => total + Number(ticket.remainingVnd || 0), 0);
}

export const ATTENDANCE_LABEL = {
  ATTENDED: 'Đã tham dự',
  NO_SHOW_PENDING: 'Vắng mặt (chờ xác nhận)',
  NO_SHOW: 'Vắng mặt',
  CHECKED_IN: 'Đã check-in',
};

/** FR-050: attendance belongs to one ticket, never to the group. */
export const ticketAttendanceLabel = ticket => ATTENDANCE_LABEL[ticket?.attendanceStatus] || null;

export const isTicketNoShow = ticket => ticket?.attendanceStatus === 'NO_SHOW_PENDING'
  || ticket?.attendanceStatus === 'NO_SHOW';

export const TICKET_NO_SHOW_NOTE = 'Ca đã kết thúc và vé này được ghi nhận là vắng mặt nên không còn dùng để check-in. '
  + 'Kết quả được xác nhận chính thức sau 24 giờ kể từ khi chốt điểm danh; nếu có nhầm lẫn, hãy liên hệ nhân viên.';

export function formatSessionWindow(scheduledStart, scheduledEnd) {
  if (!scheduledStart) return '';
  const start = new Date(scheduledStart);
  if (Number.isNaN(start.getTime())) return '';
  const day = start.toLocaleDateString('vi-VN', { weekday: 'short', day: '2-digit', month: '2-digit', year: 'numeric' });
  const from = start.toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' });
  const end = scheduledEnd ? new Date(scheduledEnd) : null;
  const to = end && !Number.isNaN(end.getTime())
    ? end.toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' })
    : null;
  return to ? `${day} (${from} - ${to})` : `${day} (${from})`;
}

/** The cart positions a refused confirmation named (FR-031); nothing was held, so the cart stays editable. */
export function failingLinesOf(error) {
  const lines = error?.data?.failingLines;
  return Array.isArray(lines) ? lines : [];
}
