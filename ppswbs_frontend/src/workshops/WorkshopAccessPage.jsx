import React, { useEffect, useState } from 'react';
import { exchangeMagicLinkToken, WorkshopApiError } from '../api/workshops';

export function WorkshopAccessPage() {
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [bookingData, setBookingData] = useState(null);

  useEffect(() => {
    // Extract token strictly from fragment (hash) only
    const hash = window.location.hash || '';
    let extractedToken = null;

    // Support formats like #/workshop-booking/access#token=XYZ or #token=XYZ or #/workshop-booking/access?token=XYZ (fragment)
    const tokenMatch = hash.match(/(?:#|&|\?)token=([^&]+)/);
    if (tokenMatch) {
      extractedToken = decodeURIComponent(tokenMatch[1]);
    }

    // Sanitize browser history immediately: clear token from URL so it is never persisted or shared
    if (window.history && window.history.replaceState) {
      const cleanPath = window.location.pathname + '#/workshop-booking/access';
      window.history.replaceState(null, '', cleanPath);
    }

    if (!extractedToken) {
      setLoading(false);
      setError('Không tìm thấy token bảo mật trong liên kết Magic Link. Vui lòng kiểm tra lại liên kết trong email hoặc tin nhắn của bạn.');
      return;
    }

    // Exchange token with backend in memory-only state
    exchangeMagicLinkToken(extractedToken)
      .then(data => {
        setBookingData(data);
      })
      .catch(err => {
        if (err instanceof WorkshopApiError) {
          if (err.status === 404 || err.code === 'MAGIC_LINK_NOT_FOUND' || err.code === 'TOKEN_NOT_FOUND') {
            setError('Liên kết truy cập Magic Link không tồn tại hoặc đã hết hạn.');
          } else if (err.status === 401 || err.status === 403) {
            setError('Liên kết truy cập Magic Link đã bị thu hồi hoặc đã bị thay đổi.');
          } else {
            setError(err.detail || 'Không thể xác thực liên kết truy cập.');
          }
        } else {
          setError(err?.message || 'Có lỗi xảy ra khi xác thực liên kết Magic Link.');
        }
      })
      .finally(() => {
        setLoading(false);
      });
  }, []);

  return (
    <main className="container public-page">
      <p className="eyebrow">XÁC THỰC MAGIC LINK</p>
      <h1>Truy cập đơn đặt chỗ bảo mật</h1>

      {loading && (
        <div className="empty-state">
          <p>Đang xác thực liên kết truy cập an toàn...</p>
        </div>
      )}

      {error && !loading && (
        <div className="empty-state">
          <p className="status" style={{ color: '#b00', borderColor: '#b00', marginBottom: '16px' }}>
            {error}
          </p>
          <p className="muted" style={{ fontSize: '14px', lineHeight: '1.6' }}>
            Bạn có thể tra cứu đơn đặt chỗ bằng mã đặt chỗ (Booking Code) và số điện thoại tại trang Tra cứu.
          </p>
          <div style={{ marginTop: '20px' }}>
            <a className="button primary" href="#/workshop-booking/lookup">
              Chuyển tới trang Tra cứu đơn
            </a>
          </div>
        </div>
      )}

      {!loading && !error && bookingData && (
        <section className="workspace-card" style={{ marginTop: '24px', borderTop: '4px solid #002967' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', borderBottom: '1px solid #eee', paddingBottom: '16px' }}>
            <div>
              <span className="muted" style={{ fontSize: '13px' }}>Mã đặt chỗ (Booking Code)</span>
              <strong style={{ display: 'block', fontSize: '24px', color: '#002967' }}>{bookingData.bookingCode}</strong>
            </div>
            <div style={{ textAlign: 'right' }}>
              <span className={`status ${bookingData.bookingStatus === 'CONFIRMED' ? 'ok' : ''}`} style={{ fontSize: '14px', fontWeight: 'bold' }}>
                {bookingData.bookingStatus === 'CONFIRMED' ? 'ĐÃ XÁC NHẬN' : bookingData.bookingStatus}
              </span>
              <span className="muted" style={{ display: 'block', fontSize: '12px', marginTop: '4px' }}>
                Thanh toán: {bookingData.paymentStatus}
              </span>
            </div>
          </div>

          <div style={{ marginTop: '18px', lineHeight: '1.8' }}>
            <p>Gói workshop: <strong>{bookingData.packageName}</strong></p>
            <p>Số người tham gia: <strong>{bookingData.participantCount} người</strong></p>

            {Array.isArray(bookingData.sessions) && bookingData.sessions.length > 0 && (
              <div style={{ marginTop: '16px', background: '#f9f9f9', padding: '16px', border: '1px solid #eee' }}>
                <h4 style={{ margin: '0 0 10px', fontSize: '15px' }}>Lịch các ca workshop đã đăng ký:</h4>
                <ul style={{ margin: 0, paddingLeft: '20px' }}>
                  {bookingData.sessions.map((s, idx) => (
                    <li key={s.sessionReference || idx}>
                      Buổi {s.ordinal || (idx + 1)}: {new Date(s.scheduledStart).toLocaleDateString('vi-VN')} ({new Date(s.scheduledStart).toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' })} - {new Date(s.scheduledEnd).toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' })})
                    </li>
                  ))}
                </ul>
              </div>
            )}

            {Array.isArray(bookingData.tickets) && bookingData.tickets.length > 0 && (
              <div style={{ marginTop: '20px' }}>
                <h4 style={{ margin: '0 0 10px' }}>Vé tham gia workshop:</h4>
                <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(260px, 1fr))', gap: '12px' }}>
                  {bookingData.tickets.map(t => (
                    <div key={t.ticketCode} style={{ background: '#fff', border: '1px solid #d9d9d9', padding: '14px' }}>
                      <span className="muted" style={{ fontSize: '12px' }}>Mã vé</span>
                      <strong style={{ display: 'block', fontSize: '18px', color: '#002967' }}>{t.ticketCode}</strong>
                      <span className="muted" style={{ fontSize: '12px', marginTop: '6px', display: 'block' }}>Mã quét check-in</span>
                      <code style={{ fontSize: '12px', background: '#f5f5f5', padding: '2px 6px' }}>{t.qrReference}</code>
                    </div>
                  ))}
                </div>
              </div>
            )}
          </div>

          <div className="actions" style={{ marginTop: '24px' }}>
            <a className="button outline" href="#/workshop-booking/lookup">
              Quản lý tại trang Tra cứu
            </a>
            <a className="button primary" href="#/home">
              Về trang chủ
            </a>
          </div>
        </section>
      )}
    </main>
  );
}

