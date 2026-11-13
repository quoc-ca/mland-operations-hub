import React, { useState } from 'react';
import {
  releaseGroupPendingHold,
  requestGroupConfirmationDelivery,
  WorkshopApiError,
} from '../api/workshops';
import { GroupHoldPanel } from './GroupHoldPanel';
import { GroupTicketList } from './GroupTicketList';
import {
  GROUP_STATUS_LABEL,
  NON_REFUNDABLE_NOTICE,
  deferredNotice,
  formatSessionWindow,
  groupRemainingVnd,
  isDeferredGroup,
  isFreeGroup,
  needsOnlinePayment,
  vnd,
} from './groupDisplay';

/**
 * One booking group with its ticket bookings (003.1 FR-042, FR-048, FR-049). The same card serves the Member list, the
 * Guest lookup, the group Magic Link and the screen right after confirmation, because all four read the same group
 * projection. The group holds the contact, the single payment and the voucher use; each ticket keeps its own status,
 * folio figures, recipient and attendance.
 */
export function GroupDetailCard({
  group,
  authorization,
  proof,
  onRefresh,
  onTicketUpdated,
  allowRelease = true,
  allowResend = true,
  allowRecipientChange = true,
  renderTicketExtra,
}) {
  const [releaseOpen, setReleaseOpen] = useState(false);
  const [releaseReason, setReleaseReason] = useState('');
  const [releasing, setReleasing] = useState(false);
  const [releaseError, setReleaseError] = useState(null);
  const [resending, setResending] = useState(false);
  const [resendMessage, setResendMessage] = useState(null);
  const [resendError, setResendError] = useState(null);

  if (!group) return null;
  const pending = group.groupStatus === 'PENDING_PAYMENT';
  const confirmed = group.groupStatus === 'CONFIRMED';
  const remaining = groupRemainingVnd(group);

  const release = async () => {
    if (!releaseReason.trim()) {
      setReleaseError('Vui lòng nhập lý do hủy giữ chỗ.');
      return;
    }
    setReleasing(true);
    setReleaseError(null);
    try {
      await releaseGroupPendingHold(group.groupCode, {
        reason: releaseReason.trim(),
        contactEmail: !authorization ? proof?.contactEmail || undefined : undefined,
        contactPhone: !authorization ? proof?.contactPhone || undefined : undefined,
        authorization: authorization || undefined,
      });
      setReleaseOpen(false);
      setReleaseReason('');
      onRefresh?.();
    } catch (error) {
      if (error instanceof WorkshopApiError && error.code === 'PAYMENT_ATTEMPT_EXISTS') {
        setReleaseError('Đã có thanh toán đang thực hiện cho nhóm này nên không thể hủy giữ chỗ. '
          + 'Giữ chỗ sẽ tự kết thúc khi hết thời hạn.');
      } else {
        setReleaseError(error?.detail || error?.message || 'Không thể hủy giữ chỗ.');
      }
    } finally {
      setReleasing(false);
    }
  };

  const resend = async () => {
    setResending(true);
    setResendMessage(null);
    setResendError(null);
    try {
      await requestGroupConfirmationDelivery(group.groupCode, {
        contactEmail: !authorization ? proof?.contactEmail || undefined : undefined,
        contactPhone: !authorization ? proof?.contactPhone || undefined : undefined,
        authorization: authorization || undefined,
      });
      setResendMessage('Yêu cầu gửi lại email xác nhận đã được tiếp nhận. Email đi tới địa chỉ đã dùng khi đặt.');
    } catch (error) {
      if (error instanceof WorkshopApiError && error.status === 429) {
        setResendError('Bạn đã vượt quá giới hạn gửi lại trong giờ này. Vui lòng thử lại sau.');
      } else {
        setResendError(error?.detail || error?.message || 'Không thể gửi lại email xác nhận lúc này.');
      }
    } finally {
      setResending(false);
    }
  };

  return (
    <section className="workspace-card group-detail" style={{ marginTop: '24px', borderTop: '4px solid #002967' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', borderBottom: '1px solid #eee', paddingBottom: '16px', gap: '12px', flexWrap: 'wrap' }}>
        <div>
          <span className="muted" style={{ fontSize: '13px' }}>Mã nhóm đặt chỗ</span>
          <strong style={{ display: 'block', fontSize: '24px', color: '#002967', letterSpacing: '0.05em' }}>
            {group.groupCode}
          </strong>
        </div>
        <div style={{ textAlign: 'right' }}>
          <span className={`status ${confirmed ? 'ok' : ''}`} style={{ fontSize: '14px', fontWeight: 'bold' }}>
            {GROUP_STATUS_LABEL[group.groupStatus] || group.groupStatus}
          </span>
          <span className="muted" style={{ display: 'block', fontSize: '12px', marginTop: '4px' }}>
            {(group.tickets || []).length} vé · Thanh toán khi đặt: {vnd(group.groupCollectionVnd)}
          </span>
        </div>
      </div>

      {Array.isArray(group.lines) && group.lines.length > 0 && (
        <div style={{ marginTop: '16px' }}>
          {group.lines.map(line => (
            <p key={line.lineIndex} style={{ margin: '4px 0' }}>
              {line.packageName || `Gói #${line.packageId}`}: <strong>{line.ticketCount ?? line.participantCount} vé</strong>
              {' · '}{vnd(line.pricePerParticipantVnd)} / vé
            </p>
          ))}
        </div>
      )}

      {pending && needsOnlinePayment(group.paymentMode) && (
        <GroupHoldPanel group={group} authorization={authorization} onHoldEnded={onRefresh} />
      )}

      {isDeferredGroup(group.paymentMode) && (
        <p role="note" style={{ marginTop: '14px', padding: '12px 14px', background: '#f0f5ff', borderLeft: '4px solid #002967' }}>
          {deferredNotice(remaining)}
        </p>
      )}
      {isFreeGroup(group.paymentMode) && (
        <p role="note" style={{ marginTop: '14px', padding: '12px 14px', background: '#f0f9f4', borderLeft: '4px solid #2b8a3e' }}>
          Ưu đãi đã trả toàn bộ giá vé nên không cần thanh toán online. {NON_REFUNDABLE_NOTICE}
        </p>
      )}

      {group.groupStatus === 'EXPIRED' && (
        <div style={{ marginTop: '18px', padding: '16px', background: '#fff1f0', border: '1px solid #ffa39e' }}>
          <p style={{ margin: 0, color: '#cf1322' }}>
            Thời hạn thanh toán đã kết thúc trước khi hệ thống nhận được thanh toán hợp lệ, nên toàn bộ chỗ giữ của nhóm
            đã được trả lại.
          </p>
          <p style={{ margin: '8px 0 0', color: '#595959' }}>
            Liên kết thanh toán cũ không được cấp lại. Nếu tiền về trễ, hệ thống sẽ liên hệ bạn về cách xử lý.
          </p>
          <div style={{ marginTop: '12px' }}>
            <a className="button primary" href="/workshops">Đặt lại workshop</a>
          </div>
        </div>
      )}

      {group.groupStatus === 'CANCELLED' && (
        <p style={{ marginTop: '18px', padding: '16px', background: '#f5f5f5', border: '1px solid #d9d9d9', margin: '18px 0 0', color: '#595959' }}>
          Nhóm đặt chỗ này đã được hủy và toàn bộ chỗ giữ đã được trả lại.
        </p>
      )}

      <div style={{ marginTop: '20px' }}>
        <h3 style={{ margin: '0 0 10px', color: '#002967' }}>Vé của nhóm</h3>
        <GroupTicketList
          group={group}
          authorization={authorization}
          proof={proof}
          onTicketUpdated={onTicketUpdated}
          allowRecipientChange={allowRecipientChange && confirmed}
        />
        {renderTicketExtra ? renderTicketExtra(group) : null}
      </div>

      <div className="actions" style={{ marginTop: '18px', display: 'flex', gap: '10px', flexWrap: 'wrap' }}>
        {allowResend && confirmed && (
          <button type="button" className="button outline" onClick={resend} disabled={resending}>
            {resending ? 'Đang gửi...' : 'Gửi lại email xác nhận'}
          </button>
        )}
        {allowRelease && pending && (
          <button type="button" className="button outline" style={{ color: '#b00', borderColor: '#b00' }} onClick={() => setReleaseOpen(true)}>
            Hủy giữ chỗ
          </button>
        )}
        {onRefresh && (
          <button type="button" className="button outline" onClick={onRefresh}>Cập nhật trạng thái</button>
        )}
      </div>

      {resendMessage && <p role="status" style={{ color: '#0b5c34', marginTop: '8px' }}>{resendMessage}</p>}
      {resendError && <p role="alert" style={{ color: '#b00', marginTop: '8px' }}>{resendError}</p>}

      {releaseOpen && (
        <div style={{ marginTop: '16px', padding: '16px', background: '#fff', border: '2px solid #b00' }}>
          <h4 style={{ margin: '0 0 8px', color: '#b00' }}>Xác nhận hủy giữ chỗ của cả nhóm</h4>
          <p style={{ fontSize: '13px', margin: '0 0 10px' }}>
            Khi hủy, toàn bộ chỗ giữ của mọi vé trong nhóm được trả lại ngay cho khách khác.
          </p>
          <label style={{ display: 'block', marginBottom: '10px' }}>
            Lý do hủy *
            <input
              type="text"
              value={releaseReason}
              onChange={event => setReleaseReason(event.target.value)}
              style={{ width: '100%', marginTop: '4px' }}
              disabled={releasing}
            />
          </label>
          <div style={{ display: 'flex', gap: '10px' }}>
            <button type="button" className="button primary" style={{ background: '#b00', borderColor: '#b00' }} onClick={release} disabled={releasing}>
              {releasing ? 'Đang hủy...' : 'Đồng ý hủy'}
            </button>
            <button type="button" className="button outline" onClick={() => setReleaseOpen(false)} disabled={releasing}>
              Đóng
            </button>
          </div>
          {releaseError && <p role="alert" style={{ color: '#b00', marginTop: '8px' }}>{releaseError}</p>}
        </div>
      )}
    </section>
  );
}

/** The session schedule of a group, read from its tickets (every ticket of a line shares the line's sessions). */
export function groupSessionLabels(group) {
  const seen = new Map();
  (group?.tickets || []).forEach(ticket => {
    (ticket.sessions || []).forEach(session => {
      if (!seen.has(session.sessionReference)) {
        seen.set(session.sessionReference, formatSessionWindow(session.scheduledStart, session.scheduledEnd));
      }
    });
  });
  return [...seen.values()];
}
