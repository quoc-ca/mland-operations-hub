import React from 'react';
import { attendanceStatusLabel, isNoShow, NO_SHOW_NOTE } from './attendanceDisplay';
import { ReviewCta } from './ReviewCta';

const STATUS_LABEL = {
  PENDING_PAYMENT: 'Chờ thanh toán cọc',
  CONFIRMED: 'Đã xác nhận',
  CANCELLED: 'Đã hủy',
  COMPLETED: 'Đã hoàn thành',
  NO_SHOW: 'Vắng mặt',
  EXPIRED: 'Hết hạn giữ chỗ',
};

const when = value => {
  if (!value) return null;
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? null : date.toLocaleString('vi-VN', { dateStyle: 'short', timeStyle: 'short' });
};

/**
 * "My bookings" of a signed-in Member: every booking with its status, session times and ticket codes, so the Member sees
 * their tickets without typing a booking code. Choosing one opens the same detail view as a lookup by code.
 */
export function MyBookingsList({ bookings, loading, error, onRetry, onOpen, selectedCode, reviewSources, onReview }) {
  return (
    <section className="workspace-card my-bookings" aria-label="Đặt chỗ của tôi" style={{ marginBottom: '24px' }}>
      <div className="card-head" style={{ marginBottom: '12px' }}>
        <h2 style={{ margin: 0 }}>Đặt chỗ của tôi</h2>
        {Array.isArray(bookings) && bookings.length > 0 && <span className="muted">{bookings.length} đặt chỗ</span>}
      </div>

      {loading && <p className="muted" role="status">Đang tải đặt chỗ của bạn…</p>}
      {error && (
        <p role="alert" style={{ color: '#b00' }}>
          {error} <button type="button" className="button outline small" onClick={onRetry}>Thử lại</button>
        </p>
      )}
      {!loading && !error && Array.isArray(bookings) && bookings.length === 0 && (
        <p className="muted">Bạn chưa có đặt chỗ nào. Sau khi đặt và thanh toán cọc, vé của bạn sẽ hiện ở đây.</p>
      )}

      {Array.isArray(bookings) && bookings.length > 0 && (
        <ul style={{ listStyle: 'none', margin: 0, padding: 0, display: 'grid', gap: '12px' }}>
          {bookings.map(booking => {
            const sessions = (booking.sessions || []).map(session => when(session.scheduledStart)).filter(Boolean);
            const tickets = booking.tickets || [];
            const selected = selectedCode === booking.bookingCode;
            return (
              <li key={booking.bookingCode} style={{ border: selected ? '2px solid #002967' : '1px solid #e3e3e3', padding: '14px 16px', background: '#fff' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', gap: '12px', flexWrap: 'wrap' }}>
                  <div>
                    <strong style={{ color: '#002967', fontSize: '16px' }}>{booking.bookingCode}</strong>
                    <div>{booking.packageName} · {booking.participantCount} vé</div>
                  </div>
                  <span className={`status ${booking.bookingStatus === 'CONFIRMED' && !isNoShow(booking) ? 'ok' : ''}`} style={{ fontWeight: 'bold', alignSelf: 'start' }}>
                    {attendanceStatusLabel(booking) || STATUS_LABEL[booking.bookingStatus] || booking.bookingStatus}
                  </span>
                </div>
                {sessions.length > 0 && (
                  <p className="muted" style={{ margin: '8px 0 0', fontSize: '13px' }}>
                    {sessions.length > 1 ? 'Các buổi: ' : 'Buổi: '}{sessions.join(' · ')}
                  </p>
                )}
                {isNoShow(booking) && <p className="muted" role="note" style={{ margin: '8px 0 0', fontSize: '13px' }}>{NO_SHOW_NOTE}</p>}
                {tickets.length > 0 && !isNoShow(booking) && (
                  <p style={{ margin: '8px 0 0', fontSize: '13px' }}>
                    Mã vé: {tickets.map(ticket => <strong key={ticket.ticketCode} style={{ marginRight: '10px' }}>{ticket.ticketCode}</strong>)}
                  </p>
                )}
                <div style={{ marginTop: '10px', display: 'flex', gap: '8px', flexWrap: 'wrap' }}>
                  <button type="button" className="button outline small" onClick={() => onOpen(booking)} aria-label={`Xem chi tiết ${booking.bookingCode}`}>
                    {booking.bookingStatus === 'CONFIRMED' ? 'Xem vé và chi tiết' : 'Xem chi tiết'}
                  </button>
                  {onReview && <ReviewCta booking={booking} sources={reviewSources} onReview={onReview} />}
                </div>
              </li>
            );
          })}
        </ul>
      )}
    </section>
  );
}
