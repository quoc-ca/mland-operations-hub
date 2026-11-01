import React, { useEffect, useState } from 'react';
import { lookupGuestBooking, initiateDepositPayment, WorkshopApiError } from '../api/workshops';

export function WorkshopPaymentReturnPage({ bookingCode }) {
  const [params, setParams] = useState(() => new URLSearchParams(window.location.search));
  const vnpResponseCode = params.get('vnp_ResponseCode');
  const vnpTransactionNo = params.get('vnp_TransactionNo');

  // Contact proof for verification from backend
  const [contactProof, setContactProof] = useState('');
  const [proofType, setProofType] = useState('phone'); // 'phone' or 'email'

  // Booking verification state
  const [isVerifying, setIsVerifying] = useState(false);
  const [bookingData, setBookingData] = useState(null);
  const [verifyError, setVerifyError] = useState(null);

  // Retry state
  const [isRetrying, setIsRetrying] = useState(false);
  const [retryError, setRetryError] = useState(null);
  const [activePaymentAttempt, setActivePaymentAttempt] = useState(null);

  // Countdown timer for active hold
  const [countdownSeconds, setCountdownSeconds] = useState(null);

  // Update countdown
  useEffect(() => {
    if (!bookingData?.holdExpiresAt) {
      setCountdownSeconds(null);
      return;
    }
    const updateCountdown = () => {
      const remaining = Math.max(0, Math.floor((new Date(bookingData.holdExpiresAt).getTime() - Date.now()) / 1000));
      setCountdownSeconds(remaining);
    };
    updateCountdown();
    const interval = setInterval(updateCountdown, 1000);
    return () => clearInterval(interval);
  }, [bookingData?.holdExpiresAt]);

  const handleVerifyBackend = async (e) => {
    if (e) e.preventDefault();
    if (!bookingCode) return;
    if (!contactProof.trim()) {
      setVerifyError('Vui lòng nhập Email hoặc Số điện thoại người đặt để đồng bộ trạng thái đơn.');
      return;
    }

    setIsVerifying(true);
    setVerifyError(null);

    try {
      const payload = {
        bookingCode,
        contactEmail: proofType === 'email' ? contactProof.trim() : undefined,
        contactPhone: proofType === 'phone' ? contactProof.trim() : undefined,
      };
      const result = await lookupGuestBooking(payload);
      setBookingData(result);
    } catch (err) {
      if (err instanceof WorkshopApiError) {
        setVerifyError(err.detail || 'Không tìm thấy thông tin đặt chỗ phù hợp với thông tin liên hệ cung cấp.');
      } else {
        setVerifyError(err?.message || 'Có lỗi xảy ra khi kiểm tra trạng thái booking.');
      }
    } finally {
      setIsVerifying(false);
    }
  };

  const handleRetryPayment = async () => {
    // Invariant: Do not create a new attempt if the old attempt is still pending
    if (activePaymentAttempt && activePaymentAttempt.status === 'PENDING') {
      setRetryError('Giao dịch thanh toán hiện tại đang chờ xử lý. Vui lòng không tạo giao dịch mới.');
      return;
    }
    // Invariant: Cannot retry if hold has expired
    if (countdownSeconds !== null && countdownSeconds <= 0) {
      setRetryError('Thời hạn giữ chỗ 10 phút đã hết hạn. Không thể tạo phiên thanh toán mới.');
      return;
    }

    setIsRetrying(true);
    setRetryError(null);

    try {
      const attempt = await initiateDepositPayment(bookingCode);
      setActivePaymentAttempt(attempt);
      if (attempt.paymentUrl) {
        window.location.href = attempt.paymentUrl;
      }
    } catch (err) {
      if (err instanceof WorkshopApiError) {
        setRetryError(err.detail || 'Không thể khởi tạo phiên thanh toán mới. Vui lòng kiểm tra lại đơn đặt chỗ.');
      } else {
        setRetryError(err?.message || 'Lỗi khi khởi tạo thanh toán cọc.');
      }
    } finally {
      setIsRetrying(false);
    }
  };

  const isProviderSuccess = vnpResponseCode === '00';
  const isHoldExpired = countdownSeconds !== null && countdownSeconds <= 0;

  return (
    <main className="container public-page">
      <p className="eyebrow">WORKSHOP BOOKING PAYMENT</p>
      <h1>Kết quả chuyển hướng thanh toán</h1>

      {/* Gateway Return Display-Only Notice */}
      <div className="workspace-card" style={{ padding: '24px', borderLeft: '4px solid #002967', marginTop: '20px' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
          <span className={isProviderSuccess ? 'status ok' : 'status'}>
            Mã phản hồi cổng: {vnpResponseCode || 'N/A'}
          </span>
          <strong>{isProviderSuccess ? 'Cổng thanh toán phản hồi thành công' : 'Giao dịch chưa hoàn tất hoặc bị hủy'}</strong>
        </div>

        <p className="prototype-note" style={{ margin: '14px 0 0', lineHeight: '1.6' }}>
          <strong>Lưu ý quan trọng:</strong> Trình duyệt chuyển hướng (browser return) mang tính chất hiển thị.
          Theo nguyên tắc an toàn tài chính, booking <strong>chỉ được xác nhận chính thức</strong> sau khi hệ thống backend MLAND nhận và xác minh chữ ký IPN an toàn từ VNPay. Không có token hay dữ liệu bí mật nào được chuyển giao qua browser return.
        </p>
      </div>

      {/* Backend Status Check & Sync */}
      <section className="workspace-card" style={{ marginTop: '24px' }}>
        <h2>Kiểm tra trạng thái xác nhận từ MLAND</h2>
        <p className="muted" style={{ margin: '0 0 16px' }}>
          Mã đặt chỗ: <strong>{bookingCode || 'Chưa xác định'}</strong>
        </p>

        {!bookingData ? (
          <form onSubmit={handleVerifyBackend} style={{ maxWidth: '520px' }}>
            <p style={{ fontSize: '14px', lineHeight: '1.6', marginBottom: '14px' }}>
              Vui lòng nhập Email hoặc Số điện thoại đã dùng khi đặt chỗ để đồng bộ kết quả xác thực backend:
            </p>

            <div style={{ display: 'flex', gap: '12px', marginBottom: '12px' }}>
              <label style={{ display: 'flex', alignItems: 'center', gap: '6px', fontSize: '14px', cursor: 'pointer' }}>
                <input
                  type="radio"
                  name="proofType"
                  checked={proofType === 'phone'}
                  onChange={() => setProofType('phone')}
                />
                Số điện thoại
              </label>
              <label style={{ display: 'flex', alignItems: 'center', gap: '6px', fontSize: '14px', cursor: 'pointer' }}>
                <input
                  type="radio"
                  name="proofType"
                  checked={proofType === 'email'}
                  onChange={() => setProofType('email')}
                />
                Email
              </label>
            </div>

            <div style={{ display: 'flex', gap: '10px' }}>
              <input
                type={proofType === 'email' ? 'email' : 'tel'}
                placeholder={proofType === 'email' ? 'email@example.com' : '0901234567'}
                value={contactProof}
                onChange={e => setContactProof(e.target.value)}
                style={{ flex: 1, padding: '10px 14px', border: '1px solid #ccc' }}
                disabled={isVerifying}
                required
              />
              <button type="submit" className="button primary" disabled={isVerifying}>
                {isVerifying ? 'Đang kiểm tra...' : 'Xem trạng thái đơn'}
              </button>
            </div>

            {verifyError && (
              <p style={{ color: '#b00', fontSize: '13px', marginTop: '8px' }}>{verifyError}</p>
            )}
          </form>
        ) : (
          <div>
            {/* Status: CONFIRMED with tickets */}
            {bookingData.bookingStatus === 'CONFIRMED' && (
              <div style={{ background: '#f0f9f4', border: '1px solid #b7eb8f', padding: '24px', marginTop: '12px' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px' }}>
                  <div>
                    <span className="status ok" style={{ fontSize: '14px', fontWeight: 'bold' }}>
                      ✓ ĐÃ XÁC NHẬN THÀNH CÔNG
                    </span>
                    <h3 style={{ margin: '8px 0 0', color: '#002967' }}>
                      {bookingData.packageName} — {bookingData.participantCount} người tham gia
                    </h3>
                  </div>
                  <span className="badge" style={{ background: '#fff' }}>
                    Thanh toán: {bookingData.paymentStatus}
                  </span>
                </div>

                {Array.isArray(bookingData.tickets) && bookingData.tickets.length > 0 ? (
                  <div style={{ marginTop: '16px' }}>
                    <h4 style={{ margin: '0 0 10px' }}>Vé tham gia workshop:</h4>
                    <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(280px, 1fr))', gap: '12px' }}>
                      {bookingData.tickets.map(ticket => (
                        <div key={ticket.ticketCode} style={{ background: '#fff', border: '1px solid #d9d9d9', padding: '14px' }}>
                          <span className="muted" style={{ fontSize: '12px' }}>Mã vé</span>
                          <strong style={{ display: 'block', fontSize: '18px', color: '#002967' }}>{ticket.ticketCode}</strong>
                          <span className="muted" style={{ fontSize: '12px', marginTop: '6px', display: 'block' }}>Mã quét check-in</span>
                          <code style={{ fontSize: '12px', background: '#f5f5f5', padding: '2px 6px' }}>{ticket.qrReference}</code>
                        </div>
                      ))}
                    </div>
                  </div>
                ) : (
                  <p className="muted">Vé điện tử sẽ sớm được đồng bộ và gửi qua email/tin nhắn xác nhận của bạn.</p>
                )}

                <div className="actions" style={{ marginTop: '20px' }}>
                  <a className="button primary" href="#/workshop-booking/lookup">
                    Xem tại trang Tra cứu đơn đặt
                  </a>
                  <a className="button outline" href="#/home">
                    Về trang chủ
                  </a>
                </div>
              </div>
            )}

            {/* Status: PENDING_PAYMENT */}
            {bookingData.bookingStatus === 'PENDING_PAYMENT' && (
              <div style={{ background: '#fffbe6', border: '1px solid #ffe58f', padding: '24px', marginTop: '12px' }}>
                <span className="status" style={{ color: '#d46b08', borderColor: '#d46b08', fontWeight: 'bold' }}>
                  ⏳ CHỜ XÁC MINH THANH TOÁN
                </span>
                <h3 style={{ margin: '10px 0 6px', color: '#002967' }}>Đang chờ xác nhận giao dịch từ ngân hàng</h3>
                <p style={{ margin: '0 0 14px', fontSize: '14px', lineHeight: '1.6' }}>
                  Nếu bạn vừa thực hiện thanh toán, giao dịch có thể mất ít phút để ngân hàng truyền dữ liệu IPN an toàn tới hệ thống.
                </p>

                {countdownSeconds !== null && (
                  <div style={{ margin: '14px 0', padding: '12px', background: '#fff', border: '1px solid #ffe58f' }}>
                    <span>Thời hạn giữ chỗ còn lại: </span>
                    <strong style={{ color: countdownSeconds < 120 ? '#b00' : '#002967', fontSize: '18px' }}>
                      {Math.floor(countdownSeconds / 60)} phút {countdownSeconds % 60} giây
                    </strong>
                  </div>
                )}

                <div className="actions" style={{ marginTop: '16px', display: 'flex', gap: '10px' }}>
                  <button type="button" className="button outline" onClick={handleVerifyBackend} disabled={isVerifying}>
                    {isVerifying ? 'Đang kiểm tra...' : 'Kiểm tra lại trạng thái'}
                  </button>

                  {!isHoldExpired && (
                    <button
                      type="button"
                      className="button primary"
                      onClick={handleRetryPayment}
                      disabled={isRetrying || (activePaymentAttempt && activePaymentAttempt.status === 'PENDING')}
                    >
                      {isRetrying ? 'Đang tạo phiên mới...' : 'Thử lại thanh toán cọc VNPay'}
                    </button>
                  )}
                </div>

                {retryError && (
                  <p style={{ color: '#b00', fontSize: '13px', marginTop: '10px' }}>{retryError}</p>
                )}
              </div>
            )}

            {/* Status: EXPIRED */}
            {bookingData.bookingStatus === 'EXPIRED' && (
              <div style={{ background: '#fff1f0', border: '1px solid #ffa39e', padding: '24px', marginTop: '12px' }}>
                <span className="status" style={{ color: '#cf1322', borderColor: '#cf1322', fontWeight: 'bold' }}>
                  HẾT HẠN GIỮ CHỖ
                </span>
                <h3 style={{ margin: '10px 0 6px', color: '#cf1322' }}>Đơn đặt chỗ đã hết hạn 10 phút</h3>
                <p style={{ margin: '0 0 16px', fontSize: '14px', lineHeight: '1.6' }}>
                  Thời hạn giữ chỗ đã kết thúc do chưa nhận được thanh toán cọc hợp lệ trong thời gian quy định. Các ca giữ chỗ đã được giải phóng cho khách hàng khác.
                </p>
                <a className="button primary" href="#/workshops">
                  Đặt lại gói workshop khác
                </a>
              </div>
            )}

            {/* Status: CANCELLED */}
            {bookingData.bookingStatus === 'CANCELLED' && (
              <div style={{ background: '#f5f5f5', border: '1px solid #d9d9d9', padding: '24px', marginTop: '12px' }}>
                <span className="status" style={{ color: '#595959', fontWeight: 'bold' }}>
                  ĐÃ HỦY
                </span>
                <h3 style={{ margin: '10px 0 6px', color: '#333' }}>Đơn đặt chỗ đã được hủy</h3>
                <p style={{ margin: '0 0 16px', fontSize: '14px' }}>
                  Các ca giữ chỗ của đơn này đã được hoàn trả.
                </p>
                <a className="button outline" href="#/workshops">
                  Khám phá các workshop khác
                </a>
              </div>
            )}
          </div>
        )}
      </section>
    </main>
  );
}

