import React, { useEffect, useRef, useState } from 'react';
import { initiateDepositPayment } from '../api/workshops';
import { classifyPaymentCommandError } from '../shared/payment/paymentRecoveryState';

/**
 * Shown right after a hold is created: the customer pays the deposit from here instead of going to the lookup page and
 * typing the booking code. Counts down the 10-minute hold and sends the customer to the payment page.
 */
export function HoldPaymentPanel({ bookingCode, holdExpiresAt }) {
  const expiresAtMs = holdExpiresAt ? new Date(holdExpiresAt).getTime() : null;
  const [secondsLeft, setSecondsLeft] = useState(() => (expiresAtMs ? Math.max(0, Math.floor((expiresAtMs - Date.now()) / 1000)) : null));
  const [isPaying, setIsPaying] = useState(false);
  const [payError, setPayError] = useState(null);
  const requestKey = useRef(null);

  useEffect(() => {
    if (!expiresAtMs) return undefined;
    const tick = () => setSecondsLeft(Math.max(0, Math.floor((expiresAtMs - Date.now()) / 1000)));
    tick();
    const timer = setInterval(tick, 1000);
    return () => clearInterval(timer);
  }, [expiresAtMs]);

  const expired = secondsLeft !== null && secondsLeft <= 0;

  const pay = async () => {
    if (!bookingCode || expired) return;
    setIsPaying(true);
    setPayError(null);
    try {
      if (!requestKey.current) {
        requestKey.current = globalThis.crypto?.randomUUID?.() || `deposit-${Date.now()}-${Math.random().toString(16).slice(2)}`;
      }
      const attempt = await initiateDepositPayment(bookingCode, { idempotencyKey: requestKey.current });
      requestKey.current = null;
      if (attempt.paymentUrl) window.location.href = attempt.paymentUrl;
    } catch (error) {
      const classified = classifyPaymentCommandError(error);
      if (classified.kind !== 'UNKNOWN') requestKey.current = null;
      setPayError(classified);
    } finally {
      setIsPaying(false);
    }
  };

  return (
    <div className="hold-pay">
      {secondsLeft !== null && (
        <p className={`hold-pay-timer${secondsLeft < 120 ? ' urgent' : ''}`} role="timer">
          {expired
            ? 'Thời hạn giữ chỗ đã kết thúc. Hệ thống tự động hủy giữ chỗ và trả lại chỗ; bạn có thể đặt lại.'
            : <>⏳ Chỗ được giữ thêm <strong>{Math.floor(secondsLeft / 60)} phút {String(secondsLeft % 60).padStart(2, '0')} giây</strong></>}
        </p>
      )}
      <button type="button" className="button primary hold-pay-button" onClick={pay} disabled={isPaying || expired || payError?.kind === 'EXPIRED'}>
        {isPaying
          ? (payError?.kind === 'UNKNOWN' ? 'Đang kiểm tra lại...' : 'Đang chuyển tới VNPay...')
          : (payError?.kind === 'UNKNOWN' ? 'Kiểm tra lại với cùng yêu cầu' : 'Thanh toán đặt cọc 50% qua VNPay')}
      </button>
      <p className="muted hold-pay-note">Bạn cần thanh toán đặt cọc trong thời gian giữ chỗ để xác nhận đơn. Hết hạn, chỗ sẽ được trả lại.</p>
      {payError && (
        <div role="alert" style={{ color: '#b00' }}>
          <strong>{payError.kind === 'UNKNOWN' ? 'Chưa xác định kết quả' : payError.kind === 'EXPIRED' ? 'Đã hết hạn' : 'Không thể thanh toán'}</strong>
          <p style={{ margin: '4px 0 0' }}>{payError.message}</p>
        </div>
      )}
    </div>
  );
}
