import React, { useEffect, useRef, useState } from 'react';
import { initiateGroupPayment } from '../api/workshops';
import { classifyPaymentCommandError } from '../shared/payment/paymentRecoveryState';
import {
  AWAITING_RESULT_MESSAGE,
  countdownText,
  groupHoldPhase,
  vnd,
} from './groupDisplay';

// After the hold ends the server releases the group on its own sweep (every 30 s), so one refresh can still read
// PENDING_PAYMENT. Re-read every 5 s, at most 6 times; the panel unmounts once the group leaves PENDING_PAYMENT.
export const HOLD_ENDED_POLL_MS = 5000;
export const HOLD_ENDED_POLL_MAX = 6;

/**
 * The single booking-time payment of a held group (003.1 FR-032, FR-038). The customer sees only the 10-minute
 * countdown to `paymentDeadlineAt`; when it ends the panel says the result is being confirmed until the internal hold
 * ends, and then re-reads the group until the server settles it. The 12-minute hold and the 2-minute buffer are never shown. A retry reuses the
 * same command key, so it never opens a second attempt.
 */
export function GroupHoldPanel({ group, authorization, proof, onHoldEnded }) {
  const [phase, setPhase] = useState(() => groupHoldPhase(group));
  const [isPaying, setIsPaying] = useState(false);
  const [payError, setPayError] = useState(null);
  const requestKey = useRef(null);

  useEffect(() => {
    const tick = () => setPhase(groupHoldPhase(group));
    tick();
    const timer = setInterval(tick, 250); // the header notice ticks at the same rate, so both show the same second
    return () => clearInterval(timer);
  }, [group?.paymentDeadlineAt, group?.holdExpiresAt]);

  const onHoldEndedRef = useRef(onHoldEnded);
  onHoldEndedRef.current = onHoldEnded;
  const ended = phase.phase === 'ENDED';
  useEffect(() => {
    if (!ended) return undefined;
    onHoldEndedRef.current?.();
    let reads = 1;
    const timer = setInterval(() => {
      if (reads >= HOLD_ENDED_POLL_MAX) {
        clearInterval(timer);
        return;
      }
      reads += 1;
      onHoldEndedRef.current?.();
    }, HOLD_ENDED_POLL_MS);
    return () => clearInterval(timer);
  }, [ended]);

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
        contactEmail: !authorization ? proof?.contactEmail : undefined,
        contactPhone: !authorization ? proof?.contactPhone : undefined,
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
          Thời hạn giữ chỗ đã kết thúc. Hệ thống đang cập nhật kết quả cuối cùng của đơn; trang này tự cập nhật.
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
