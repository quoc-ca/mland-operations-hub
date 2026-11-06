/**
 * What the customer sees about attendance for a CONFIRMED booking (display only). The booking stays CONFIRMED during the
 * 24-hour correction window after attendance is finalized, so a no-show is explained here without changing the status.
 * attendanceStatus comes from the booking projection: ATTENDED, NO_SHOW_PENDING, NO_SHOW, or empty before finalization.
 */
export const NO_SHOW_NOTE = 'Ca đã kết thúc và vé này được ghi nhận là vắng mặt nên không còn dùng để check-in. '
  + 'Kết quả được xác nhận chính thức sau 24 giờ kể từ khi chốt điểm danh; nếu có nhầm lẫn, hãy liên hệ nhân viên.';

export const isNoShow = booking => booking?.bookingStatus === 'CONFIRMED'
  && (booking.attendanceStatus === 'NO_SHOW_PENDING' || booking.attendanceStatus === 'NO_SHOW');

/** The status wording of a booking; null when the booking's own status should be used. */
export function attendanceStatusLabel(booking) {
  if (booking?.bookingStatus !== 'CONFIRMED') return null;
  if (booking.attendanceStatus === 'NO_SHOW_PENDING') return 'Vắng mặt (chờ xác nhận)';
  if (booking.attendanceStatus === 'NO_SHOW') return 'Vắng mặt';
  if (booking.attendanceStatus === 'ATTENDED') return 'Đã tham dự';
  return null;
}
