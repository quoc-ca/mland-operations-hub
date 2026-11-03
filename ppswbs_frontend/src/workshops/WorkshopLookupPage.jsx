import React, { useEffect, useRef, useState } from 'react';
import {
  lookupGuestBooking,
  lookupMemberBooking,
  initiateDepositPayment,
  releasePendingHold,
  requestConfirmationDelivery,
  WorkshopApiError,
} from '../api/workshops';
import { useAuth } from '../shared/auth/AuthProvider';
import { useHttp } from '../shared/api/useHttp';
import { classifyPaymentCommandError } from '../shared/payment/paymentRecoveryState';

export function WorkshopLookupPage() {
  const { status: authStatus, getIdToken } = useAuth();
  const http = useHttp();
  const [accountProfile, setAccountProfile] = useState(null);
  const [profileLoading, setProfileLoading] = useState(false);
  const [profileError, setProfileError] = useState(null);
  const [bookingCode, setBookingCode] = useState('');
  const [contactEmail, setContactEmail] = useState('');
  const [contactPhone, setContactPhone] = useState('');
  const lookupMode = authStatus === 'signed-out' || authStatus === 'unconfigured'
    ? 'GUEST'
    : accountProfile?.role === 'MEMBER' ? 'MEMBER' : null;

  useEffect(() => {
    let active = true;
    if (authStatus !== 'signed-in') {
      setAccountProfile(null);
      setProfileLoading(false);
      setProfileError(null);
      return () => { active = false; };
    }
    setProfileLoading(true);
    setProfileError(null);
    http('/api/v1/accounts/me/profile')
      .then(profile => {
        if (active) setAccountProfile(profile || null);
      })
      .catch(error => {
        if (!active) return;
        setAccountProfile(null);
        setProfileError(error?.message || 'KhÃ´ng thá»ƒ táº£i há»“ sÆ¡ Member.');
      })
      .finally(() => {
        if (active) setProfileLoading(false);
      });
    return () => { active = false; };
  }, [authStatus, http]);

  // Loaded projection
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [bookingData, setBookingData] = useState(null);

  // Pending resume & release state
  const [isPaying, setIsPaying] = useState(false);
  const [payError, setPayError] = useState(null);
  const [showCancelModal, setShowCancelModal] = useState(false);
  const [cancelReason, setCancelReason] = useState('');
  const [isCancelling, setIsCancelling] = useState(false);
  const [cancelError, setCancelError] = useState(null);

  // Recovery & Resend delivery state
  const [isResending, setIsResending] = useState(false);
  const [resendSuccess, setResendSuccess] = useState(null);
  const [resendError, setResendError] = useState(null);
  const paymentRequestKey = useRef(null);

  // Countdown for pending hold
  const [countdownSeconds, setCountdownSeconds] = useState(null);

  useEffect(() => {
    if (!bookingData?.holdExpiresAt || bookingData?.bookingStatus !== 'PENDING_PAYMENT') {
      setCountdownSeconds(null);
      return;
    }
    const tick = () => {
      const remaining = Math.max(0, Math.floor((new Date(bookingData.holdExpiresAt).getTime() - Date.now()) / 1000));
      setCountdownSeconds(remaining);
    };
    tick();
    const interval = setInterval(tick, 1000);
    return () => clearInterval(interval);
  }, [bookingData?.holdExpiresAt, bookingData?.bookingStatus]);

  const handleLookup = async (e) => {
    e.preventDefault();
    setError(null);
    setResendSuccess(null);
    setResendError(null);
    setPayError(null);

    if (!bookingCode.trim()) {
      setError('Vui lòng nhập mã đặt chỗ (ví dụ: WS-...).');
      return;
    }

    setLoading(true);
    try {
      let result;
      if (lookupMode === null) {
        setError('KhÃ´ng thá»ƒ xÃ¡c minh vai trÃ² tÃ i khoáº£n Ä‘á»ƒ tra cá»©u booking.');
        return;
      }
      if (lookupMode === 'MEMBER') {
        const idToken = await getIdToken();
        if (!idToken) throw new Error('PhiÃªn Ä‘Äƒng nháº­p Ä‘Ã£ háº¿t háº¡n. Vui lÃ²ng Ä‘Äƒng nháº­p láº¡i.');
        result = await lookupMemberBooking(bookingCode.trim(), { authorization: `Bearer ${idToken}` });
      } else {
        const hasEmail = Boolean(contactEmail.trim());
        const hasPhone = Boolean(contactPhone.trim());
        if ((hasEmail && hasPhone) || (!hasEmail && !hasPhone)) {
          setError('Khách vãng lai vui lòng cung cấp đúng một trong hai: Email HOẶC Số điện thoại đã đặt.');
          setLoading(false);
          return;
        }
        result = await lookupGuestBooking({
          bookingCode: bookingCode.trim(),
          contactEmail: hasEmail ? contactEmail.trim() : undefined,
          contactPhone: hasPhone ? contactPhone.trim() : undefined,
        });
      }
      setBookingData(result);
    } catch (err) {
      setBookingData(null);
      if (err instanceof WorkshopApiError) {
        if (err.status === 404 || err.code === 'BOOKING_NOT_FOUND') {
          setError('Không tìm thấy thông tin đặt chỗ phù hợp với mã đặt chỗ và thông tin liên hệ đã cung cấp.');
        } else if (err.status === 401 || err.status === 403) {
          setError('Bạn không có quyền xem thông tin đơn đặt chỗ này.');
        } else {
          setError(err.detail || 'Lỗi tra cứu thông tin đặt chỗ.');
        }
      } else {
        setError(err?.message || 'Có lỗi xảy ra khi tra cứu thông tin.');
      }
    } finally {
      setLoading(false);
    }
  };

  const handleResumePayment = async () => {
    if (!bookingData?.bookingCode) return;
    if (countdownSeconds !== null && countdownSeconds <= 0) {
      setPayError('Thời hạn giữ chỗ 10 phút đã kết thúc. Không thể thanh toán cho đơn này.');
      return;
    }

    setIsPaying(true);
    setPayError(null);

    try {
      if (!paymentRequestKey.current) {
        paymentRequestKey.current = globalThis.crypto?.randomUUID?.()
          || `deposit-${Date.now()}-${Math.random().toString(16).slice(2)}`;
      }
      const attempt = await initiateDepositPayment(bookingData.bookingCode, {
        idempotencyKey: paymentRequestKey.current,
      });
      paymentRequestKey.current = null;
      if (attempt.paymentUrl) {
        window.location.href = attempt.paymentUrl;
      }
    } catch (err) {
      const classified = classifyPaymentCommandError(err);
      if (classified.kind !== 'UNKNOWN') paymentRequestKey.current = null;
      setPayError(classified);
    } finally {
      setIsPaying(false);
    }
  };

  const handleConfirmCancel = async (e) => {
    e.preventDefault();
    if (!cancelReason.trim()) {
      setCancelError('Vui lòng nhập lý do hủy giữ chỗ.');
      return;
    }

    setIsCancelling(true);
    setCancelError(null);

    try {
      await releasePendingHold(bookingData.bookingCode, {
        contactEmail: lookupMode === 'GUEST' && contactEmail.trim() ? contactEmail.trim() : undefined,
        contactPhone: lookupMode === 'GUEST' && contactPhone.trim() ? contactPhone.trim() : undefined,
        reason: cancelReason.trim(),
        authorization: lookupMode === 'MEMBER' ? `Bearer ${await getIdToken()}` : undefined,
      });

      // Update local projection to CANCELLED
      setBookingData(prev => ({
        ...prev,
        bookingStatus: 'CANCELLED',
      }));
      setShowCancelModal(false);
    } catch (err) {
      if (err instanceof WorkshopApiError) {
        setCancelError(err.detail || 'Không thể hủy giữ chỗ. Vui lòng kiểm tra lại trạng thái đơn.');
      } else {
        setCancelError(err?.message || 'Có lỗi khi hủy giữ chỗ.');
      }
    } finally {
      setIsCancelling(false);
    }
  };

  const handleResendConfirmation = async () => {
    if (!bookingData?.bookingCode) return;
    setIsResending(true);
    setResendSuccess(null);
    setResendError(null);

    try {
      await requestConfirmationDelivery(bookingData.bookingCode, {
        contactEmail: lookupMode === 'GUEST' && contactEmail.trim() ? contactEmail.trim() : undefined,
        contactPhone: lookupMode === 'GUEST' && contactPhone.trim() ? contactPhone.trim() : undefined,
        authorization: lookupMode === 'MEMBER' ? `Bearer ${await getIdToken()}` : undefined,
      });
      setResendSuccess('Yêu cầu gửi lại xác nhận vé đã được tiếp nhận thành công.');
    } catch (err) {
      if (err instanceof WorkshopApiError) {
        if (err.status === 429) {
          setResendError('Bạn đã vượt quá giới hạn gửi lại xác nhận trong giờ này. Vui lòng thử lại sau.');
        } else {
          setResendError(err.detail || 'Không thể gửi lại xác nhận vé lúc này.');
        }
      } else {
        setResendError(err?.message || 'Lỗi khi gửi yêu cầu.');
      }
    } finally {
      setIsResending(false);
    }
  };

  return (
    <main className="container public-page">
      <p className="eyebrow">TRA CỨU & QUẢN LÝ ĐẶT CHỖ</p>
      <h1>Tra cứu đơn đặt Workshop</h1>

      <div role="status" style={{ marginTop: '20px', marginBottom: '24px', padding: '12px 16px', border: '1px solid #dcdfe6', background: '#f8fafc', color: '#002967' }}>
        {authStatus === 'loading' || profileLoading
          ? 'Äang xÃ¡c Ä‘á»‹nh thÃ´ng tin tÃ i khoáº£n...'
          : lookupMode === 'MEMBER'
            ? 'Tra cá»©u theo booking thuá»™c tÃ i khoáº£n Member Ä‘ang Ä‘Äƒng nháº­p.'
            : lookupMode === 'GUEST'
              ? 'Tra cá»©u Guest báº±ng mÃ£ Ä‘áº·t chá»— vÃ  má»™t thÃ´ng tin liÃªn há»‡.'
              : 'KhÃ´ng thá»ƒ xÃ¡c minh vai trÃ² tÃ i khoáº£n.'}
      </div>
      {lookupMode === null && authStatus === 'signed-in' && (
        <p style={{ color: '#b00', marginBottom: '16px' }}>{profileError || 'TÃ i khoáº£n hiá»‡n táº¡i khÃ´ng cÃ³ vai trÃ² Member Ä‘á»ƒ tra cá»©u booking.'}</p>
      )}

      {/* Lookup Form */}
      <section className="workspace-card" style={{ maxWidth: '640px' }}>
        <form onSubmit={handleLookup}>
          <fieldset disabled={authStatus === 'loading' || profileLoading || lookupMode === null} style={{ border: 0, padding: 0, margin: 0, minInlineSize: 0 }}>
          <label style={{ display: 'block', marginBottom: '14px' }}>
            Mã đặt chỗ (Booking Code) *
            <input
              type="text"
              placeholder="e.g. WS-20261101-ABCD"
              value={bookingCode}
              onChange={e => setBookingCode(e.target.value.toUpperCase())}
              disabled={loading}
              required
              style={{ width: '100%', marginTop: '6px' }}
            />
          </label>

          {lookupMode === 'GUEST' ? (
            <div style={{ padding: '14px', background: '#fafafa', border: '1px solid #e3e3e3', marginBottom: '16px' }}>
              <p style={{ margin: '0 0 10px', fontSize: '13px', fontWeight: 'bold' }}>
                Xác minh thông tin (Chỉ cần điền 1 trong 2 mục):
              </p>
              <div className="form-grid" style={{ margin: 0 }}>
                <label>
                  Email khi đặt
                  <input
                    type="email"
                    placeholder="email@example.com"
                    value={contactEmail}
                    onChange={e => {
                      setContactEmail(e.target.value);
                      if (e.target.value) setContactPhone('');
                    }}
                    disabled={loading}
                  />
                </label>
                <label>
                  Số điện thoại khi đặt
                  <input
                    type="tel"
                    placeholder="0901234567"
                    value={contactPhone}
                    onChange={e => {
                      setContactPhone(e.target.value);
                      if (e.target.value) setContactEmail('');
                    }}
                    disabled={loading}
                  />
                </label>
              </div>
            </div>
          ) : null}
          <button type="submit" className="button primary" disabled={loading} style={{ width: '100%' }}>
            {loading ? 'Đang tra cứu...' : 'Tra cứu đơn đặt chỗ'}
          </button>
          </fieldset>
        </form>

        {error && (
          <p style={{ color: '#b00', marginTop: '14px', fontSize: '14px' }}>{error}</p>
        )}
      </section>

      {/* Booking Details Card */}
      {bookingData && (
        <section className="workspace-card" style={{ marginTop: '28px', borderTop: '4px solid #002967' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', borderBottom: '1px solid #eee', paddingBottom: '16px' }}>
            <div>
              <span className="muted" style={{ fontSize: '13px' }}>Mã đặt chỗ</span>
              <strong style={{ display: 'block', fontSize: '24px', color: '#002967' }}>{bookingData.bookingCode}</strong>
            </div>

            <div style={{ textAlign: 'right' }}>
              <span className={`status ${bookingData.bookingStatus === 'CONFIRMED' ? 'ok' : ''}`} style={{ fontSize: '14px', fontWeight: 'bold' }}>
                {bookingData.bookingStatus === 'PENDING_PAYMENT' && 'CHỜ THANH TOÁN CỌC'}
                {bookingData.bookingStatus === 'CONFIRMED' && 'ĐÃ XÁC NHẬN'}
                {bookingData.bookingStatus === 'CANCELLED' && 'ĐÃ HỦY'}
                {bookingData.bookingStatus === 'EXPIRED' && 'HẾT HẠN GIỮ CHỖ'}
              </span>
              <span className="muted" style={{ display: 'block', fontSize: '12px', marginTop: '4px' }}>
                Thanh toán: {bookingData.paymentStatus}
              </span>
            </div>
          </div>

          <div style={{ marginTop: '18px', lineHeight: '1.8' }}>
            <p style={{ margin: '4px 0' }}>Gói workshop: <strong>{bookingData.packageName}</strong></p>
            <p style={{ margin: '4px 0' }}>Số vé: <strong>{bookingData.participantCount}</strong></p>

            {Array.isArray(bookingData.sessions) && bookingData.sessions.length > 0 && (
              <div style={{ marginTop: '14px', background: '#f9f9f9', padding: '16px', border: '1px solid #eee' }}>
                <h4 style={{ margin: '0 0 8px', fontSize: '14px' }}>Lịch các ca workshop:</h4>
                <ul style={{ margin: 0, paddingLeft: '20px', fontSize: '14px' }}>
                  {bookingData.sessions.map((s, idx) => (
                    <li key={s.sessionReference || idx}>
                      Buổi {s.ordinal || (idx + 1)}: {new Date(s.scheduledStart).toLocaleDateString('vi-VN')} ({new Date(s.scheduledStart).toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' })} - {new Date(s.scheduledEnd).toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' })})
                    </li>
                  ))}
                </ul>
              </div>
            )}
          </div>

          {/* STATE: PENDING_PAYMENT (Pending-resume) */}
          {bookingData.bookingStatus === 'PENDING_PAYMENT' && (
            <div style={{ marginTop: '20px', padding: '20px', background: '#fffbe6', border: '1px solid #ffe58f' }}>
              <h3 style={{ margin: '0 0 8px', color: '#002967' }}>Đơn đang trong thời hạn giữ chỗ 10 phút</h3>
              {countdownSeconds !== null && (
                <p style={{ margin: '0 0 14px', fontSize: '15px' }}>
                  Thời hạn còn lại: <strong style={{ color: countdownSeconds < 120 ? '#b00' : '#002967' }}>{Math.floor(countdownSeconds / 60)} phút {countdownSeconds % 60} giây</strong>
                </p>
              )}

              <div style={{ display: 'flex', gap: '12px', flexWrap: 'wrap' }}>
                <button
                  type="button"
                  className="button primary"
                  onClick={handleResumePayment}
                  disabled={isPaying || payError?.kind === 'EXPIRED' || (countdownSeconds !== null && countdownSeconds <= 0)}
                >
                  {isPaying
                    ? (payError?.kind === 'UNKNOWN' ? 'Đang kiểm tra lại...' : 'Đang tạo phiên...')
                    : (payError?.kind === 'UNKNOWN' ? 'Kiểm tra lại với cùng yêu cầu' : 'Tiến hành thanh toán cọc VNPay (50%)')}
                </button>

                <button
                  type="button"
                  className="button outline"
                  onClick={() => setShowCancelModal(true)}
                  style={{ color: '#b00', borderColor: '#b00' }}
                >
                  Hủy giữ chỗ
                </button>
              </div>

              {payError && (
                <div role="alert" style={{ color: '#b00', marginTop: '10px' }}>
                  <strong>{payError.kind === 'UNKNOWN' ? 'Chưa xác định kết quả' : payError.kind === 'EXPIRED' ? 'Đã hết hạn' : 'Không thể thanh toán'}</strong>
                  <p style={{ margin: '4px 0 0' }}>{payError.message}</p>
                </div>
              )}
            </div>
          )}

          {/* Cancel Modal / Section */}
          {showCancelModal && (
            <div style={{ marginTop: '16px', padding: '16px', background: '#fff', border: '2px solid #b00' }}>
              <h4 style={{ margin: '0 0 8px', color: '#b00' }}>Xác nhận hủy giữ chỗ</h4>
              <p style={{ fontSize: '13px', margin: '0 0 10px' }}>
                Khi hủy giữ chỗ, các ca workshop đã chọn sẽ được giải phóng ngay lập tức cho khách hàng khác.
              </p>
              <label style={{ display: 'block', marginBottom: '10px' }}>
                Lý do hủy *
                <input
                  type="text"
                  placeholder="Nhập lý do hủy giữ chỗ..."
                  value={cancelReason}
                  onChange={e => setCancelReason(e.target.value)}
                  style={{ width: '100%', marginTop: '4px' }}
                />
              </label>
              <div style={{ display: 'flex', gap: '10px' }}>
                <button
                  type="button"
                  className="button primary"
                  style={{ background: '#b00', borderColor: '#b00' }}
                  onClick={handleConfirmCancel}
                  disabled={isCancelling}
                >
                  {isCancelling ? 'Đang hủy...' : 'Đồng ý hủy'}
                </button>
                <button
                  type="button"
                  className="button outline"
                  onClick={() => setShowCancelModal(false)}
                >
                  Đóng
                </button>
              </div>
              {cancelError && <p style={{ color: '#b00', marginTop: '8px' }}>{cancelError}</p>}
            </div>
          )}

          {/* STATE: CONFIRMED (Confirmed-ticket) */}
          {bookingData.bookingStatus === 'CONFIRMED' && (
            <div style={{ marginTop: '20px', padding: '20px', background: '#f0f9f4', border: '1px solid #b7eb8f' }}>
              <h3 style={{ margin: '0 0 12px', color: '#0b5c34' }}>Vé tham gia workshop đã được phát hành</h3>

              {Array.isArray(bookingData.tickets) && bookingData.tickets.length > 0 ? (
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
              ) : (
                <p className="muted">Vé điện tử đang được xử lý.</p>
              )}

              <div style={{ marginTop: '16px' }}>
                <button
                  type="button"
                  className="button outline"
                  onClick={handleResendConfirmation}
                  disabled={isResending}
                >
                  {isResending ? 'Đang gửi...' : 'Gửi lại xác nhận vé qua Email/SMS'}
                </button>
              </div>

              {resendSuccess && <p role="status" style={{ color: '#0b5c34', marginTop: '8px' }}>{resendSuccess}</p>}
              {resendError && <p role="alert" style={{ color: '#b00', marginTop: '8px' }}>{resendError} Hệ thống không tự động gửi lại.</p>}
            </div>
          )}

          {/* STATE: CANCELLED */}
          {bookingData.bookingStatus === 'CANCELLED' && (
            <div style={{ marginTop: '20px', padding: '16px', background: '#f5f5f5', border: '1px solid #d9d9d9' }}>
              <p style={{ margin: 0, color: '#595959' }}>
                Đơn đặt chỗ này đã bị hủy. Các ca đã giữ chỗ đã được giải phóng.
              </p>
            </div>
          )}

          {/* STATE: EXPIRED */}
          {bookingData.bookingStatus === 'EXPIRED' && (
            <div style={{ marginTop: '20px', padding: '16px', background: '#fff1f0', border: '1px solid #ffa39e' }}>
              <p style={{ margin: 0, color: '#cf1322' }}>
                Đơn đặt chỗ đã hết thời hạn giữ chỗ 10 phút trước khi hoàn tất thanh toán cọc.
              </p>
              <p style={{ margin: '8px 0 0', color: '#595959' }}>
                Liên kết thanh toán cũ không được gửi lại hoặc cấp lại cho booking đã hết hạn.
              </p>
              <div style={{ marginTop: '12px' }}>
                <a className="button primary" href="/workshops">
                  Đặt lại gói workshop khác
                </a>
              </div>
            </div>
          )}
        </section>
      )}
    </main>
  );
}

