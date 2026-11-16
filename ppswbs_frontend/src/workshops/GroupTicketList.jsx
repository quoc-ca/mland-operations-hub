import React, { useCallback, useState } from 'react';
import {
  cancelTicket,
  changeTicketRecipient,
  changeTicketSessions,
  confirmBuyerVerification,
  getTicketChangeOptions,
  lookupTicketChangeOptions,
  requestBuyerVerification,
  WorkshopApiError,
} from '../api/workshops';
import { TicketSessionPicker } from '../shared/ui/TicketSessionPicker';
import { isValidEmail, normalizeEmail } from './contactValidation';
import {
  NON_REFUNDABLE_NOTICE,
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

const describeRecipientError = (error, fallback = 'Không thể gửi vé lúc này.') => (error instanceof WorkshopApiError
  ? (RECIPIENT_ERROR_MESSAGE[error.code] || error.detail || fallback)
  : (error?.message || fallback));

const CHANGE_ERROR_MESSAGE = {
  CHANGE_ALLOWANCE_EXHAUSTED: 'Vé này đã dùng hết một lần đổi lịch. Vui lòng liên hệ nhân viên.',
  CHANGE_REQUEST_PENDING: 'Vé này đang có một yêu cầu đổi lịch chờ quản lý duyệt.',
  ADVANCE_CHANGE_WINDOW_EXPIRED: 'Đã quá hạn đổi lịch trước buổi học (24 giờ).',
  RESCHEDULE_QUOTA_EXHAUSTED: 'Buổi bạn chọn đã hết hạn mức đổi lịch.',
  INSUFFICIENT_CAPACITY: 'Buổi bạn chọn vừa hết chỗ. Hãy chọn buổi khác.',
  INVALID_REPLACEMENT: 'Các buổi mới không phù hợp với quy định của gói. Hãy chọn lại.',
  TICKET_FOLIO_NOT_OPEN: 'Hóa đơn của vé đã đóng nên không thể đổi lịch.',
  OTA_TICKET_NOT_SUPPORTED: 'Vé đặt qua đại lý (OTA) đổi lịch theo chính sách của đại lý.',
  FROM_SESSION_NOT_OF_TICKET: 'Buổi cần đổi không thuộc vé này.',
  FROM_SESSIONS_MISMATCH: 'Lựa chọn đổi lịch không hợp lệ. Hãy chọn lại.',
};

const describeChangeError = (error, fallback = 'Không thể đổi lịch lúc này.') => (error instanceof WorkshopApiError
  ? (CHANGE_ERROR_MESSAGE[error.code] || RECIPIENT_ERROR_MESSAGE[error.code] || error.detail || fallback)
  : (error?.message || fallback));

const PICKER_LABELS = {
  loading: 'Đang tải các buổi có thể đổi…',
  loadFailed: 'Không tải được danh sách buổi.',
  retry: 'Thử lại',
  sessionToChange: 'Buổi cần đổi',
  bothSessions: 'Đổi cả hai buổi cùng lúc',
  sessionLabel: ordinal => `Buổi ${ordinal}`,
  notChangeable: 'không thể đổi',
  noOptions: 'Hiện chưa có buổi nào thay thế được buổi này.',
  noPairs: 'Hiện chưa có cặp buổi nào phù hợp với quy định của gói.',
  seatsLeft: count => `còn ${count} chỗ`,
  full: 'Hết chỗ',
  needsManager: 'Cần quản lý duyệt',
  priceIncrease: amount => `tăng giá ${amount}`,
  allowanceUsed: 'Vé này đã dùng hết lần đổi lịch; mọi lựa chọn cần quản lý duyệt.',
  windowClosed: 'Đã quá hạn đổi lịch trước buổi học (24 giờ); lựa chọn cần quản lý duyệt.',
  choose: 'Chọn buổi mới',
  rule: {
    CHANGE_ALLOWANCE_EXHAUSTED: 'đã dùng lần đổi của vé',
    ADVANCE_CHANGE_WINDOW_EXPIRED: 'còn dưới 24 giờ',
    RESCHEDULE_QUOTA_EXHAUSTED: 'buổi đã hết hạn mức đổi lịch',
  },
};

/**
 * Buyer verification of a Guest buyer before a recipient change (003.1 FR-041): the OTP always goes to the group's
 * original contact email, never to a recipient, and the answer never says whether the contact matched. The returned
 * reference is single-use and is kept only in memory.
 */
function BuyerVerification({ groupCode, proof, onVerified, purpose = 'gửi vé cho người khác' }) {
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
            Để {purpose}, hãy xác minh bạn là người đặt. Mã xác minh được gửi tới email đã dùng khi đặt.
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

const HOURS_24 = 24 * 60 * 60 * 1000;

/**
 * The buyer cancels one ticket (003.3 FR-041/FR-042). Only a hint is computed here: the server decides the 24-hour
 * outcome. Points come back only for a cancellation at least 24 hours before the first session; the booking-time payment
 * is never refunded and the group voucher returns only when every ticket of the group ended validly.
 */
function CancelTicketForm({ ticket, groupCode, authorization, proof, onUpdated }) {
  const [open, setOpen] = useState(false);
  const [reason, setReason] = useState('');
  const [reference, setReference] = useState(null);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState(null);
  const [result, setResult] = useState(null);
  const needsVerification = !authorization && !reference;
  const firstStart = (ticket.sessions || []).map(s => new Date(s.scheduledStart).getTime()).filter(Number.isFinite).sort((a, b) => a - b)[0];
  const started = firstStart !== undefined && firstStart <= Date.now();
  const valid = firstStart !== undefined && firstStart - Date.now() >= HOURS_24;

  if (result) {
    return (
      <div role="status" style={{ marginTop: '10px', padding: '12px', background: '#f0fdf4', border: '1px solid #bbf7d0', fontSize: '13px' }}>
        <strong>Đã hủy vé {result.ticketCode || ticket.ticketCode}.</strong>
        <p style={{ margin: '6px 0 0' }}>
          {result.benefitOutcome === 'VALID_CANCELLATION'
            ? `Hủy hợp lệ: đã trả lại ${Number(result.pointsReturned || 0).toLocaleString('vi-VN')} điểm.`
            : 'Hủy muộn: không được trả lại điểm.'}
          {' '}{NON_REFUNDABLE_NOTICE}
        </p>
        {(result.voucherRestoration?.restored ?? result.voucherRestored) && <p style={{ margin: '6px 0 0' }}>Voucher của nhóm đã được khôi phục.</p>}
        {result.voucherRestoration?.reason === 'EXPIRED' && (
          <p style={{ margin: '6px 0 0' }}>Voucher của nhóm đã hết hạn nên không được khôi phục.</p>
        )}
      </div>
    );
  }
  if (started) return null;

  if (!open) {
    return (
      <button type="button" className="button outline small" style={{ marginTop: '10px' }} onClick={() => setOpen(true)}>
        Hủy vé
      </button>
    );
  }

  const submit = async () => {
    setPending(true);
    setError(null);
    try {
      const outcome = await cancelTicket(ticket.ticketCode, {
        reason,
        buyerVerificationReference: reference || undefined,
        authorization: authorization || undefined,
      });
      setReference(null);
      setResult(outcome || {});
      onUpdated?.();
    } catch (err) {
      setReference(null);
      setError(describeRecipientError(err, 'Không thể hủy vé lúc này.'));
    } finally {
      setPending(false);
    }
  };

  return (
    <div style={{ marginTop: '10px', paddingTop: '10px', borderTop: '1px dashed #dcdfe6' }}>
      <p role="note" style={{ margin: '0 0 8px', fontSize: '13px', lineHeight: 1.5 }}>
        {NON_REFUNDABLE_NOTICE}{' '}
        {valid
          ? 'Hiện còn từ 24 giờ trở lên trước buổi đầu nên đây là hủy hợp lệ: điểm đã dùng của vé được trả lại.'
          : 'Còn dưới 24 giờ trước buổi đầu nên đây là hủy muộn: chỗ được nhả nhưng điểm không được trả lại.'}
        {' '}Voucher của nhóm chỉ được khôi phục khi mọi vé của nhóm đều hủy hợp lệ.
      </p>
      <label style={{ display: 'block' }}>
        Lý do hủy
        <input value={reason} onChange={event => setReason(event.target.value)} maxLength={1000} disabled={pending} />
      </label>
      {needsVerification && <BuyerVerification groupCode={groupCode} proof={proof} onVerified={setReference} purpose="hủy vé" />}
      <div style={{ display: 'flex', gap: '8px', marginTop: '10px', flexWrap: 'wrap' }}>
        <button type="button" className="button primary small" onClick={submit} disabled={pending || needsVerification || !reason.trim()}>
          {pending ? 'Đang hủy...' : 'Xác nhận hủy vé'}
        </button>
        <button type="button" className="button outline small" onClick={() => setOpen(false)} disabled={pending}>Đóng</button>
      </div>
      {error && <p role="alert" style={{ color: '#b00', marginTop: '8px', fontSize: '13px' }}>{error}</p>}
    </div>
  );
}

const CHANGE_TYPES = [
  { value: 'ADVANCE', label: 'Đổi lịch trước buổi học' },
  { value: 'LATE_ARRIVAL', label: 'Tôi đến muộn hoặc đã lỡ buổi' },
];

/**
 * The buyer changes the sessions of one ticket (003.3 FR-044, FR-044a, FR-045). The picker lists only the sessions the
 * server says could replace the chosen one, with the exact seats left; the server re-checks everything on confirmation.
 * A Member sends the bearer; a Guest first proves the group with one contact to see the list (no OTP) and with the OTP
 * to commit. A price increase is confirmed first and the call repeats with a new key; a change that only a Manager may
 * allow is queued (202) and reported as sent.
 */
function ChangeScheduleForm({ ticket, groupCode, authorization, proof, onUpdated }) {
  const [open, setOpen] = useState(false);
  const [type, setType] = useState('ADVANCE');
  const [selection, setSelection] = useState(null);
  const [reference, setReference] = useState(null);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState(null);
  const [increase, setIncrease] = useState(null);
  const [outcome, setOutcome] = useState(null);
  const needsVerification = !authorization && !reference;
  const code = ticket.ticketCode;

  const loadOptions = useCallback(from => (authorization
    ? getTicketChangeOptions(code, { type, fromSessionReferences: from, authorization })
    : lookupTicketChangeOptions(code, {
      contactEmail: proof?.contactEmail || undefined,
      contactPhone: proof?.contactPhone || undefined,
      type,
      fromSessionReferences: from,
    })), [code, type, authorization, proof?.contactEmail, proof?.contactPhone]);

  const submit = async confirmIncrease => {
    if (!selection) return;
    setPending(true);
    setError(null);
    try {
      const result = await changeTicketSessions(code, {
        type,
        fromSessionReferences: selection.fromSessionReferences,
        targetSessionReferences: selection.targetSessionReferences,
        confirmIncrease: confirmIncrease || undefined,
        buyerVerificationReference: reference || undefined,
        authorization: authorization || undefined,
      });
      if (result?.confirmIncreaseRequired && !result.applied) {
        // Nothing changed yet and the OTP is not used up: show the amount and ask the buyer to confirm.
        setIncrease(result.priceIncreaseAmount);
        return;
      }
      setIncrease(null);
      setReference(null);
      if (result?.changeRequestReference && !result.applied) {
        setOutcome({ queued: true, reference: result.changeRequestReference });
      } else {
        setOutcome({ queued: false, balanceDue: result?.balanceDue, increase: result?.priceIncreaseAmount });
        onUpdated?.(result);
      }
    } catch (err) {
      if (String(err?.code || '').startsWith('BUYER_VERIFICATION_')) setReference(null);
      setIncrease(null);
      setError(describeChangeError(err));
    } finally {
      setPending(false);
    }
  };

  if (outcome) {
    return (
      <div role="status" style={{ marginTop: '10px', padding: '12px', background: '#f0fdf4', border: '1px solid #bbf7d0', fontSize: '13px' }}>
        {outcome.queued ? (
          <>
            <strong>Đã gửi yêu cầu đổi lịch cho quản lý duyệt.</strong>
            <p style={{ margin: '6px 0 0' }}>Mã yêu cầu: <code>{outcome.reference}</code>. Chúng tôi sẽ báo kết quả qua email.</p>
          </>
        ) : (
          <>
            <strong>Đã đổi lịch cho vé {code}.</strong>
            {Number(outcome.increase) > 0 && <p style={{ margin: '6px 0 0' }}>Mức tăng giá {vnd(outcome.increase)} được cộng vào số còn phải trả khi check-in.</p>}
          </>
        )}
      </div>
    );
  }

  if (!open) {
    return (
      <button type="button" className="button outline small" style={{ marginTop: '10px' }} onClick={() => setOpen(true)}>
        Đổi lịch
      </button>
    );
  }

  return (
    <div style={{ marginTop: '10px', paddingTop: '10px', borderTop: '1px dashed #dcdfe6' }}>
      <label style={{ display: 'block', fontSize: '13px' }}>
        Loại đổi lịch
        <select value={type} onChange={event => { setType(event.target.value); setSelection(null); setIncrease(null); }} disabled={pending}>
          {CHANGE_TYPES.map(item => <option key={item.value} value={item.value}>{item.label}</option>)}
        </select>
      </label>
      <TicketSessionPicker
        ticketCode={code}
        type={type}
        loadOptions={loadOptions}
        onSelect={next => { setSelection(next); setIncrease(null); setError(null); }}
        labels={PICKER_LABELS}
        formatWindow={formatSessionWindow}
        formatMoney={vnd}
      />
      {selection?.requiresManager && (
        <p role="note" style={{ margin: '8px 0 0', fontSize: '13px', color: '#874d00' }}>
          Lựa chọn này cần quản lý duyệt: yêu cầu sẽ được gửi đi và vé chỉ đổi khi quản lý đồng ý.
        </p>
      )}
      {selection && needsVerification && (
        <BuyerVerification groupCode={groupCode} proof={proof} onVerified={setReference} purpose="đổi lịch" />
      )}
      {increase !== null && (
        <div role="status" style={{ marginTop: '8px', padding: '10px 12px', background: '#fff8e1', border: '1px solid #f0d58c', fontSize: '13px' }}>
          Đổi lịch này làm giá vé tăng <strong>{vnd(increase)}</strong> và được cộng vào số còn phải trả. Bạn có đồng ý không?
          <div style={{ marginTop: '8px' }}>
            <button type="button" className="button primary small" onClick={() => submit(true)} disabled={pending}>
              Đồng ý mức tăng giá
            </button>
          </div>
        </div>
      )}
      <div style={{ display: 'flex', gap: '8px', marginTop: '10px', flexWrap: 'wrap' }}>
        <button
          type="button"
          className="button primary small"
          onClick={() => submit(false)}
          disabled={pending || !selection || needsVerification || increase !== null}
        >
          {pending ? 'Đang gửi...' : (selection?.requiresManager ? 'Gửi yêu cầu đổi lịch' : 'Xác nhận đổi lịch')}
        </button>
        <button type="button" className="button outline small" onClick={() => setOpen(false)} disabled={pending}>Đóng</button>
      </div>
      {error && <p role="alert" style={{ color: '#b00', marginTop: '8px', fontSize: '13px' }}>{error}</p>}
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
        recipientEmail: normalizeEmail(recipientEmail),
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
          disabled={pending || limitReached || needsVerification || !isValidEmail(recipientEmail)}
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
export function GroupTicketList({ group, authorization, proof, onTicketUpdated, allowRecipientChange = true, allowCancel = true, allowChange = allowCancel }) {
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

            {ticket.mlandRemedyPending && (
              <p role="note" style={{ margin: '10px 0 0', padding: '10px 12px', background: '#fff8e1', border: '1px solid #f0d58c', fontSize: '13px' }}>
                Mland đã hủy hoặc dời một buổi của vé này và đề nghị buổi thay thế.{' '}
                <a className="text-link" href={`/workshop-tickets/${encodeURIComponent(ticket.ticketCode)}/remedy`}>Chọn buổi thay thế hoặc hoàn tiền</a>
              </p>
            )}

            {(ticket.ticketStatus === 'CONFIRMED' || ticket.ticketStatus === 'COMPLETED') && (
              <p style={{ margin: '10px 0 0' }}>
                <a className="text-link" href={`/workshop-tickets/${encodeURIComponent(ticket.ticketCode)}/folio`}>Xem hóa đơn thanh toán của vé</a>
              </p>
            )}

            {allowChange && ticket.ticketStatus === 'CONFIRMED' && !noShow && !ticket.attendanceStatus && (
              <ChangeScheduleForm
                ticket={ticket}
                groupCode={group.groupCode}
                authorization={authorization}
                proof={proof}
                onUpdated={onTicketUpdated}
              />
            )}

            {allowCancel && ticket.ticketStatus === 'CONFIRMED' && !noShow && !ticket.attendanceStatus && (
              <CancelTicketForm
                ticket={ticket}
                groupCode={group.groupCode}
                authorization={authorization}
                proof={proof}
                onUpdated={onTicketUpdated}
              />
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
