import React, { useState } from 'react';
import {
  changeTicketRecipient,
  confirmBuyerVerification,
  requestBuyerVerification,
  WorkshopApiError,
} from '../api/workshops';
import {
  TICKET_NO_SHOW_NOTE,
  TICKET_STATUS_LABEL,
  collectionKindLabel,
  formatSessionWindow,
  isTicketNoShow,
  ticketAttendanceLabel,
  vnd,
} from './groupDisplay';

const RECIPIENT_ERROR_MESSAGE = {
  BUYER_VERIFICATION_REQUIRED: 'Cần xác minh người đặt trước khi gửi vé. Hãy lấy mã OTP gửi về email đã đặt.',
  BUYER_VERIFICATION_FAILED: 'Mã OTP không đúng. Bạn còn lượt thử.',
  BUYER_VERIFICATION_LOCKED: 'Đã nhập sai 5 lần. Vui lòng yêu cầu mã mới.',
  BUYER_VERIFICATION_EXPIRED: 'Mã xác minh đã hết hạn hoặc được thay bằng mã mới. Hãy lấy mã mới.',
  RECIPIENT_CHANGE_FORBIDDEN: 'Chỉ người đặt (hoặc nhân viên sau khi xác minh người đặt) mới được đổi người nhận vé.',
  TICKET_NOT_CONFIRMED: 'Vé chưa được xác nhận nên chưa thể gửi cho người khác.',
  TICKET_ALREADY_CHECKED_IN: 'Vé đã check-in nên không thể đổi người nhận.',
  RECIPIENT_CHANGE_LIMIT_REACHED: 'Vé này đã dùng hết 3 lần đổi người nhận online. Vui lòng liên hệ nhân viên tại cửa hàng.',
};

const describeRecipientError = error => (error instanceof WorkshopApiError
  ? (RECIPIENT_ERROR_MESSAGE[error.code] || error.detail || 'Không thể gửi vé lúc này.')
  : (error?.message || 'Không thể gửi vé lúc này.'));

/**
 * Buyer verification of a Guest buyer before a recipient change (003.1 FR-041): the OTP always goes to the group's
 * original contact email, never to a recipient, and the answer never says whether the contact matched. The returned
 * reference is single-use and is kept only in memory.
 */
function BuyerVerification({ groupCode, proof, onVerified }) {
  const [stage, setStage] = useState('IDLE');
  const [verificationId, setVerificationId] = useState(null);
  const [otp, setOtp] = useState('');
  const [error, setError] = useState(null);
  const [pending, setPending] = useState(false);

  const start = async () => {
    setPending(true);
    setError(null);
    try {
      const challenge = await requestBuyerVerification(groupCode, {
        contactEmail: proof?.contactEmail || undefined,
        contactPhone: proof?.contactPhone || undefined,
      });
      setVerificationId(challenge?.verificationId || null);
      setStage('OTP');
    } catch (err) {
      setError(describeRecipientError(err));
    } finally {
      setPending(false);
    }
  };

  const confirm = async () => {
    setPending(true);
    setError(null);
    try {
      const result = await confirmBuyerVerification(groupCode, verificationId, { otp });
      setOtp('');
      setStage('IDLE');
      onVerified(result?.buyerVerificationReference || null);
    } catch (err) {
      setError(describeRecipientError(err));
    } finally {
      setPending(false);
    }
  };

  return (
    <div style={{ marginTop: '10px', padding: '12px', background: '#f8fafc', border: '1px solid #dcdfe6' }}>
      {stage === 'IDLE' ? (
        <>
          <p className="muted" style={{ margin: '0 0 8px', fontSize: '13px' }}>
            Để gửi vé cho người khác, hãy xác minh bạn là người đặt. Mã xác minh được gửi tới email đã dùng khi đặt.
          </p>
          <button type="button" className="button outline small" onClick={start} disabled={pending}>
            {pending ? 'Đang gửi mã...' : 'Gửi mã xác minh'}
          </button>
        </>
      ) : (
        <>
          <label style={{ display: 'block' }}>
            Mã xác minh (6 số)
            <input
              inputMode="numeric"
              maxLength={6}
              value={otp}
              onChange={event => setOtp(event.target.value.replace(/\D/g, ''))}
              disabled={pending}
            />
          </label>
          <div style={{ display: 'flex', gap: '8px', marginTop: '8px', flexWrap: 'wrap' }}>
            <button type="button" className="button primary small" onClick={confirm} disabled={pending || otp.length !== 6}>
              {pending ? 'Đang xác minh...' : 'Xác minh'}
            </button>
            <button type="button" className="button outline small" onClick={start} disabled={pending}>
              Gửi lại mã
            </button>
          </div>
        </>
      )}
      {error && <p role="alert" style={{ color: '#b00', margin: '8px 0 0', fontSize: '13px' }}>{error}</p>}
    </div>
  );
}

/** The recipient form of one ticket (FR-041). The first send keeps the QR; a different email issues a new one. */
function RecipientForm({ ticket, groupCode, authorization, proof, onUpdated }) {
  const [open, setOpen] = useState(false);
  const [recipientEmail, setRecipientEmail] = useState('');
  const [reference, setReference] = useState(null);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState(null);
  const [success, setSuccess] = useState(null);

  const needsVerification = !authorization && !reference;
  const changesLeft = ticket.onlineRecipientChangesRemaining;
  const limitReached = changesLeft !== null && changesLeft !== undefined && Number(changesLeft) <= 0;

  const submit = async () => {
    setPending(true);
    setError(null);
    setSuccess(null);
    try {
      const updated = await changeTicketRecipient(ticket.ticketCode, {
        recipientEmail,
        buyerVerificationReference: reference || undefined,
        authorization: authorization || undefined,
      });
      // The reference is single-use: a further change needs a new verification.
      setReference(null);
      setSuccess('Đã gửi vé tới email người nhận.');
      setRecipientEmail('');
      onUpdated?.(updated);
    } catch (err) {
      setReference(null);
      setError(describeRecipientError(err));
    } finally {
      setPending(false);
    }
  };

  if (!open) {
    return (
      <button type="button" className="button outline small" style={{ marginTop: '10px' }} onClick={() => setOpen(true)}>
        Gửi vé cho người đi cùng
      </button>
    );
  }

  return (
    <div style={{ marginTop: '10px', paddingTop: '10px', borderTop: '1px dashed #dcdfe6' }}>
      <label style={{ display: 'block' }}>
        Email người nhận vé
        <input
          type="email"
          value={recipientEmail}
          onChange={event => setRecipientEmail(event.target.value)}
          disabled={pending || limitReached}
        />
      </label>
      {changesLeft !== null && changesLeft !== undefined && (
        <p className="muted" style={{ margin: '6px 0 0', fontSize: '12px' }}>
          Còn {changesLeft} lần đổi người nhận online cho vé này. Gửi lại cùng email không tính là một lần đổi.
        </p>
      )}
      {limitReached && (
        <p role="alert" style={{ color: '#b00', fontSize: '13px' }}>{RECIPIENT_ERROR_MESSAGE.RECIPIENT_CHANGE_LIMIT_REACHED}</p>
      )}

      {needsVerification && !limitReached && (
        <BuyerVerification groupCode={groupCode} proof={proof} onVerified={setReference} />
      )}

      <div style={{ display: 'flex', gap: '8px', marginTop: '10px', flexWrap: 'wrap' }}>
        <button
          type="button"
          className="button primary small"
          onClick={submit}
          disabled={pending || limitReached || needsVerification || !recipientEmail.includes('@')}
        >
          {pending ? 'Đang gửi...' : 'Gửi vé'}
        </button>
        <button type="button" className="button outline small" onClick={() => setOpen(false)} disabled={pending}>
          Đóng
        </button>
      </div>
      {success && <p role="status" style={{ color: '#0b5c34', marginTop: '8px', fontSize: '13px' }}>{success}</p>}
      {error && <p role="alert" style={{ color: '#b00', marginTop: '8px', fontSize: '13px' }}>{error}</p>}
    </div>
  );
}

/**
 * Every ticket booking of a group with its own facts (003.1 FR-042, FR-049): status, price, amount collected at
 * booking, balance left at checkout, collection kind, masked recipient, attendance and — for the buyer only — the QR
 * reference. An action on one ticket never touches another ticket of the group (FR-030).
 */
export function GroupTicketList({ group, authorization, proof, onTicketUpdated, allowRecipientChange = true }) {
  const tickets = group?.tickets || [];
  if (tickets.length === 0) {
    return <p className="muted">Nhóm này chưa có vé nào được phát hành.</p>;
  }

  return (
    <div className="group-tickets" style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(300px, 1fr))', gap: '12px' }}>
      {tickets.map(ticket => {
        const attendance = ticketAttendanceLabel(ticket);
        const noShow = isTicketNoShow(ticket);
        return (
          <article key={ticket.ticketCode} style={{ background: '#fff', border: '1px solid #d9d9d9', padding: '14px' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', gap: '8px', flexWrap: 'wrap' }}>
              <div>
                <span className="muted" style={{ fontSize: '12px' }}>Mã vé</span>
                <strong style={{ display: 'block', fontSize: '18px', color: '#002967' }}>{ticket.ticketCode}</strong>
              </div>
              <span className={`status ${ticket.ticketStatus === 'CONFIRMED' && !noShow ? 'ok' : ''}`}>
                {attendance || TICKET_STATUS_LABEL[ticket.ticketStatus] || ticket.ticketStatus}
              </span>
            </div>

            <p style={{ margin: '8px 0 0', fontSize: '13px' }}>
              {ticket.packageName || `Gói #${ticket.packageId}`} · Người {ticket.participantOrdinal}
            </p>

            {Array.isArray(ticket.sessions) && ticket.sessions.length > 0 && (
              <ul className="muted" style={{ margin: '6px 0 0', paddingLeft: '18px', fontSize: '12px' }}>
                {ticket.sessions.map(session => (
                  <li key={session.sessionReference}>
                    Buổi {session.ordinal}: {formatSessionWindow(session.scheduledStart, session.scheduledEnd)}
                  </li>
                ))}
              </ul>
            )}

            <dl style={{ margin: '10px 0 0', fontSize: '13px', display: 'grid', gridTemplateColumns: 'auto auto', gap: '2px 10px' }}>
              <dt>Giá vé</dt><dd style={{ margin: 0, textAlign: 'right' }}>{vnd(ticket.finalPriceVnd)}</dd>
              <dt>Đã trả khi đặt</dt><dd style={{ margin: 0, textAlign: 'right' }}>{vnd(ticket.collectedAtBookingVnd)}</dd>
              <dt>Còn phải trả</dt><dd style={{ margin: 0, textAlign: 'right' }}>{vnd(ticket.remainingVnd)}</dd>
              <dt>Hình thức</dt><dd style={{ margin: 0, textAlign: 'right' }}>{collectionKindLabel(ticket.collectionKind)}</dd>
            </dl>

            <p className="muted" style={{ margin: '8px 0 0', fontSize: '12px' }}>
              Người nhận vé: {ticket.recipientEmailMasked || 'Người đặt (bạn)'}
            </p>

            {ticket.qrReference && (
              <>
                <span className="muted" style={{ fontSize: '12px', marginTop: '6px', display: 'block' }}>Mã quét check-in</span>
                <code style={{ fontSize: '12px', background: '#f5f5f5', padding: '2px 6px' }}>{ticket.qrReference}</code>
              </>
            )}

            {noShow && (
              <p role="note" style={{ margin: '8px 0 0', fontSize: '12px', color: '#874d00' }}>{TICKET_NO_SHOW_NOTE}</p>
            )}

            {allowRecipientChange && ticket.ticketStatus === 'CONFIRMED' && !noShow && (
              <RecipientForm
                ticket={ticket}
                groupCode={group.groupCode}
                authorization={authorization}
                proof={proof}
                onUpdated={onTicketUpdated}
              />
            )}
          </article>
        );
      })}
    </div>
  );
}
