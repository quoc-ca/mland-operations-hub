import React, { useEffect, useRef, useState } from 'react';
import { initiateGroupPayment } from '../api/workshops';
import { classifyPaymentCommandError } from '../shared/payment/paymentRecoveryState';
import {
  AWAITING_RESULT_MESSAGE,
  countdownText,
  groupHoldPhase,
  vnd,
} from './groupDisplay';

/**
 * The single booking-time payment of a held group (003.1 FR-032, FR-038). The customer sees only the 10-minute
 * countdown to `paymentDeadlineAt`; when it ends the panel says the result is being confirmed until the internal hold
 * ends, and then asks for a refresh. The 12-minute hold and the 2-minute buffer are never shown. A retry reuses the
 * same command key, so it never opens a second attempt.
 */
export function GroupHoldPanel({ group, authorization, onHoldEnded }) {
  const [phase, setPhase] = useState(() => groupHoldPhase(group));
  const [isPaying, setIsPaying] = useState(false);
  const [payError, setPayError] = useState(null);
  const requestKey = useRef(null);
  const endedNotified = useRef(false);

  useEffect(() => {
    endedNotified.current = false;
    const tick = () => setPhase(groupHoldPhase(group));
    tick();
    const timer = setInterval(tick, 1000);
    return () => clearInterval(timer);
  }, [group?.paymentDeadlineAt, group?.holdExpiresAt]);

  useEffect(() => {
    if (phase.phase === 'ENDED' && !endedNotified.current) {
      endedNotified.current = true;
      onHoldEnded?.();
    }
  }, [phase.phase, onHoldEnded]);

  const pay = async () => {
    if (!group?.groupCode || phase.phase !== 'PAYABLE') return;
    setIsPaying(true);
    setPayError(null);
    try {
      if (!requestKey.current) {
        requestKey.current = globalThis.crypto?.randomUUID?.()
          || `group-payment-${Date.now()}-${Math.random().toString(16).slice(2)}`;
      }
      const attempt = await initiateGroupPayment(group.groupCode, {
        idempotencyKey: requestKey.current,
        authorization,
      });
      requestKey.current = null;
      if (attempt?.paymentUrl) window.location.href = attempt.paymentUrl;
    } catch (error) {
      const classified = classifyPaymentCommandError(error);
      if (classified.kind !== 'UNKNOWN') requestKey.current = null;
      setPayError(classified);
    } finally {
      setIsPaying(false);
    }
  };

  return (
    <div className="hold-pay" style={{ marginTop: '18px', padding: '16px 18px', background: '#fffbe6', border: '1px solid #ffe58f' }}>
      {phase.phase === 'PAYABLE' && (
        <>
          <p className={`hold-pay-timer${phase.secondsLeft < 120 ? ' urgent' : ''}`} role="timer">
            ⏳ Thời hạn thanh toán còn <strong>{countdownText(phase.secondsLeft)}</strong>
          </p>
          <button
            type="button"
            className="button primary hold-pay-button"
            onClick={pay}
            disabled={isPaying || payError?.kind === 'EXPIRED'}
          >
            {isPaying
              ? (payError?.kind === 'UNKNOWN' ? 'Đang kiểm tra lại...' : 'Đang chuyển tới VNPay...')
              : (payError?.kind === 'UNKNOWN'
                ? 'Kiểm tra lại với cùng yêu cầu'
                : `Thanh toán ${vnd(group.groupCollectionVnd)} qua VNPay`)}
          </button>
          <p className="muted hold-pay-note">
            Thanh toán cho cả nhóm trong một lần. Hết thời hạn, chỗ giữ sẽ được trả lại.
          </p>
        </>
      )}

      {phase.phase === 'AWAITING_RESULT' && (
        <p role="status">{AWAITING_RESULT_MESSAGE}</p>
      )}

      {phase.phase === 'ENDED' && (
        <p role="status">
          Thời hạn thanh toán đã kết thúc. Hệ thống đang cập nhật kết quả cuối cùng của đơn.
        </p>
      )}

      {payError && (
        <div role="alert" style={{ color: '#b00', marginTop: '8px' }}>
          <strong>
            {payError.kind === 'UNKNOWN' ? 'Chưa xác định kết quả' : payError.kind === 'EXPIRED' ? 'Đã hết hạn' : 'Không thể thanh toán'}
          </strong>
          <p style={{ margin: '4px 0 0' }}>{payError.message}</p>
        </div>
      )}
    </div>
  );
}
