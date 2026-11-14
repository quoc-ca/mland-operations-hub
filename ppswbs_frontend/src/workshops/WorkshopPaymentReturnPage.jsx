import React, { useEffect, useState } from 'react';
import { Link } from 'react-router';
import { getOwnedBookingGroup, lookupGuestBookingGroup, WorkshopApiError } from '../api/workshops';
import { GroupDetailCard } from './GroupDetailCard';
import { AWAITING_RESULT_MESSAGE, countdownText, groupHoldPhase } from './groupDisplay';
import { useBookingIdentity } from './useBookingIdentity';
import './figmaPublicShell.css';

/**
 * What the browser return from the gateway means (003.1 FR-038): nothing. A group is confirmed only on verified
 * evidence the backend receives, so this page classifies the return for display and then reads the group itself.
 */
export function classifyGroupPaymentOutcome({ responseCode, groupStatus, error } = {}) {
  if (groupStatus === 'EXPIRED' || error?.code === 'HOLD_EXPIRED' || error?.code === 'BOOKING_EXPIRED') {
    return {
      kind: 'EXPIRED',
      title: 'Thời hạn thanh toán đã kết thúc',
      message: 'Chỗ giữ của nhóm đã được trả lại. Nếu tiền về trễ, hệ thống sẽ liên hệ bạn về cách xử lý.',
    };
  }
  if (error && (error.code === 'NETWORK_ERROR' || error.code === 'NETWORK_TIMEOUT' || error.code === 'REQUEST_TIMEOUT'
    || Number(error.status || 0) === 0 || error.name === 'AbortError')) {
    return {
      kind: 'NETWORK_TIMEOUT',
      title: 'Chưa kiểm tra được trạng thái',
      message: 'Kết quả thanh toán chưa rõ. Đừng tạo thêm giao dịch; hãy kiểm tra lại trạng thái nhóm đặt chỗ.',
    };
  }
  if (error) {
    return {
      kind: 'LOOKUP_ERROR',
      title: 'Chưa đọc được nhóm đặt chỗ',
      message: error.detail || error.message || 'MLAND chưa xác minh được nhóm đặt chỗ này.',
    };
  }
  if (responseCode === '00') {
    return {
      kind: 'PROVIDER_SUCCESS_PENDING_VERIFICATION',
      title: 'Cổng thanh toán báo thành công',
      message: 'VNPay báo thành công. MLAND chỉ xác nhận nhóm đặt chỗ sau khi nhận được bằng chứng IPN đã xác minh.',
    };
  }
  if (responseCode) {
    return {
      kind: 'PROVIDER_ERROR',
      title: `Cổng thanh toán trả mã ${responseCode}`,
      message: 'Giao dịch chưa hoàn tất tại cổng. Không suy ra xác nhận nào từ lần chuyển hướng này.',
    };
  }
  return {
    kind: 'NO_PROVIDER_RESPONSE',
    title: 'Không nhận được phản hồi từ cổng',
    message: 'Hãy kiểm tra trạng thái nhóm đặt chỗ với MLAND trước khi thử lại.',
  };
}

/**
 * The payment result of a booking group (003.1 FR-032, FR-038). While the group is still held, the customer sees only
 * the 10-minute countdown; when it ends the page shows "Đang xác nhận kết quả thanh toán…" until the internal hold
 * ends and then the outcome the server reports. The 12-minute hold and the 2-minute buffer are never shown.
 */
export function WorkshopPaymentReturnPage({ groupCode }) {
  const { authStatus, mode: lookupMode, profileLoading, operatorRole, memberAuthorization } = useBookingIdentity();
  const params = new URLSearchParams(window.location.search);
  const vnpResponseCode = params.get('vnp_ResponseCode');

  const [contactProof, setContactProof] = useState('');
  const [proofType, setProofType] = useState('phone');
  const [isVerifying, setIsVerifying] = useState(false);
  const [group, setGroup] = useState(null);
  const [verifyError, setVerifyError] = useState(null);
  const [phase, setPhase] = useState(() => groupHoldPhase(null));
  const [authorization, setAuthorization] = useState(null);

  useEffect(() => {
    if (!group || group.groupStatus !== 'PENDING_PAYMENT') { setPhase(groupHoldPhase(null)); return undefined; }
    const tick = () => setPhase(groupHoldPhase(group));
    tick();
    const timer = setInterval(tick, 1000);
    return () => clearInterval(timer);
  }, [group?.groupStatus, group?.paymentDeadlineAt, group?.holdExpiresAt]);

  const readGroup = async event => {
    if (event) event.preventDefault();
    if (!groupCode) return;
    if (lookupMode === null) {
      setVerifyError({ title: 'Chưa xác minh được tài khoản', message: 'Vui lòng đăng nhập lại bằng tài khoản Member.' });
      return;
    }
    if (lookupMode === 'GUEST' && !contactProof.trim()) {
      setVerifyError({ title: 'Cần thông tin xác minh', message: 'Nhập email hoặc số điện thoại đã dùng khi đặt.' });
      return;
    }
    setIsVerifying(true);
    setVerifyError(null);
    try {
      let result;
      if (lookupMode === 'MEMBER') {
        const bearer = await memberAuthorization();
        if (!bearer) throw new Error('MEMBER_SESSION_EXPIRED');
        setAuthorization(bearer);
        result = await getOwnedBookingGroup(groupCode, { authorization: bearer });
      } else {
        result = await lookupGuestBookingGroup({
          groupCode,
          contactEmail: proofType === 'email' ? contactProof.trim() : undefined,
          contactPhone: proofType === 'phone' ? contactProof.trim() : undefined,
        });
      }
      setGroup(result);
    } catch (err) {
      setVerifyError(classifyGroupPaymentOutcome({ error: err instanceof WorkshopApiError ? err : err }));
    } finally {
      setIsVerifying(false);
    }
  };

  const guestProof = proofType === 'email'
    ? { contactEmail: contactProof.trim() || undefined }
    : { contactPhone: contactProof.trim() || undefined };
  const returnOutcome = classifyGroupPaymentOutcome({ responseCode: vnpResponseCode, groupStatus: group?.groupStatus });

  return (
    <main className="public-page figma-public-page">
      <p className="eyebrow">THANH TOÁN NHÓM ĐẶT CHỖ WORKSHOP</p>
      <h1>Kết quả chuyển hướng thanh toán</h1>

      <div className="workspace-card" style={{ padding: '24px', borderLeft: '4px solid #002967', marginTop: '20px' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '12px', flexWrap: 'wrap' }}>
          <span className={vnpResponseCode === '00' ? 'status ok' : 'status'}>
            Mã phản hồi cổng: {vnpResponseCode || 'N/A'}
          </span>
          <strong>{returnOutcome.title}</strong>
        </div>
        <p className="prototype-note" style={{ margin: '14px 0 0', lineHeight: '1.6' }}>
          <strong>{returnOutcome.message}</strong> Trang chuyển hướng chỉ để hiển thị: nhóm đặt chỗ chỉ được xác nhận
          sau khi backend MLAND nhận và xác minh bằng chứng IPN có chữ ký từ VNPay. Không có token hay dữ liệu bí mật
          nào đi qua lần chuyển hướng này.
        </p>
      </div>

      <section className="workspace-card" style={{ marginTop: '24px' }}>
        <h2>Kiểm tra trạng thái nhóm đặt chỗ</h2>
        <p className="muted" style={{ margin: '0 0 16px' }}>
          Mã nhóm: <strong>{groupCode || 'Chưa xác định'}</strong>
        </p>

        {!group && operatorRole ? (
          <div role="status" className="operator-payment-note" style={{ background: '#f8fafc', border: '1px solid #dcdfe6', padding: '16px 18px', lineHeight: '1.6' }}>
            <strong>Bạn đang đăng nhập bằng tài khoản nhân viên ({operatorRole}).</strong>
            <p style={{ margin: '8px 0 0' }}>
              Trang này dành cho khách kiểm tra đơn của mình nên không xác minh bằng tài khoản nhân viên. Hệ thống chỉ
              ghi nhận khi nhận IPN, thường sau vài giây.
            </p>
            <p style={{ margin: '8px 0 12px' }}>
              Hãy quay lại <strong>Hóa đơn quyết toán</strong>, bấm <strong>Load folio</strong> để xem số còn nợ đã cập
              nhật, rồi chốt hóa đơn khi còn nợ bằng 0.
            </p>
            <Link className="button primary" to="/settlement">Quay lại Hóa đơn quyết toán</Link>
          </div>
        ) : !group ? (
          <form className="payment-form" onSubmit={readGroup}>
            <p hidden={lookupMode !== 'GUEST'} style={{ fontSize: '14px', lineHeight: '1.6', marginBottom: '14px' }}>
              Vui lòng nhập email hoặc số điện thoại đã dùng khi đặt để xem trạng thái nhóm:
            </p>
            <p className="muted" hidden={lookupMode !== 'MEMBER'}>
              Hệ thống dùng tài khoản Member đang đăng nhập để xác minh quyền xem nhóm; không cần thông tin liên hệ.
            </p>

            <div hidden={lookupMode !== 'GUEST'} style={{ display: 'flex', gap: '12px', marginBottom: '12px' }}>
              <label style={{ display: 'flex', alignItems: 'center', gap: '6px', fontSize: '14px' }}>
                <input type="radio" name="proofType" checked={proofType === 'phone'} onChange={() => setProofType('phone')} />
                Số điện thoại
              </label>
              <label style={{ display: 'flex', alignItems: 'center', gap: '6px', fontSize: '14px' }}>
                <input type="radio" name="proofType" checked={proofType === 'email'} onChange={() => setProofType('email')} />
                Email
              </label>
            </div>

            <div hidden={lookupMode !== 'GUEST'} style={{ display: 'flex', gap: '10px' }}>
              <input
                type={proofType === 'email' ? 'email' : 'tel'}
                placeholder={proofType === 'email' ? 'email@example.com' : '0901234567'}
                value={contactProof}
                onChange={event => setContactProof(event.target.value)}
                style={{ flex: 1, padding: '10px 14px', border: '1px solid #ccc' }}
                disabled={isVerifying}
                required={lookupMode === 'GUEST'}
              />
              <button type="submit" className="button primary" disabled={isVerifying}>
                {isVerifying ? 'Đang kiểm tra...' : 'Xem trạng thái nhóm'}
              </button>
            </div>

            {lookupMode === 'MEMBER' && (
              <button type="submit" className="button primary" disabled={isVerifying}>
                {isVerifying ? 'Đang kiểm tra...' : 'Xem trạng thái nhóm'}
              </button>
            )}
            {profileLoading && <p className="muted">Đang kiểm tra tài khoản đang đăng nhập…</p>}
            {lookupMode === null && !profileLoading && authStatus === 'signed-in' && (
              <p className="muted">Đăng nhập lại bằng tài khoản Member, hoặc đăng xuất để tra cứu như khách.</p>
            )}
            {verifyError && (
              <div role="alert" style={{ color: '#b00', fontSize: '13px', marginTop: '8px' }}>
                <strong>{verifyError.title}</strong>
                <p style={{ margin: '4px 0 0' }}>{verifyError.message}</p>
              </div>
            )}
          </form>
        ) : (
          <>
            {group.groupStatus === 'PENDING_PAYMENT' && phase.phase === 'PAYABLE' && (
              <p role="timer" style={{ margin: '0 0 12px' }}>
                Thời hạn thanh toán còn <strong>{countdownText(phase.secondsLeft)}</strong>
              </p>
            )}
            {group.groupStatus === 'PENDING_PAYMENT' && phase.phase === 'AWAITING_RESULT' && (
              <p role="status" style={{ margin: '0 0 12px' }}>{AWAITING_RESULT_MESSAGE}</p>
            )}

            <GroupDetailCard
              group={group}
              authorization={lookupMode === 'MEMBER' ? authorization : null}
              proof={lookupMode === 'GUEST' ? guestProof : undefined}
              onRefresh={readGroup}
              onTicketUpdated={readGroup}
            />
          </>
        )}
      </section>
    </main>
  );
}
