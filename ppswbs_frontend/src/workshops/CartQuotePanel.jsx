import React from 'react';
import {
  NON_REFUNDABLE_NOTICE,
  collectionKindLabel,
  deferredNotice,
  formatSessionWindow,
  isDeferredGroup,
  isFreeGroup,
  vnd,
} from './groupDisplay';

/**
 * The quote of a cart, shown before confirmation (003.1 FR-030, FR-033–FR-037). Every amount is a server fact from
 * `POST /api/v1/workshop-cart-quotes`; this panel only labels it. Both ways into a booking use this panel: "Đặt ngay"
 * shows it on the package screen, and the cart page shows it for several lines (FR-045).
 */
export function CartQuotePanel({
  quote,
  contact,
  onContactChange,
  onConfirm,
  onCancel,
  confirming,
  confirmError,
  validationErrors = {},
  failingLines = [],
  title = 'Báo giá đặt chỗ',
}) {
  if (!quote) return null;
  const deferred = isDeferredGroup(quote.paymentMode);
  const free = isFreeGroup(quote.paymentMode);
  const contactValue = contact || { contactEmail: '', contactPhone: '' };

  return (
    <section className="workspace-card cart-quote" aria-label={title} style={{ marginTop: '20px', borderTop: '4px solid #002967' }}>
      <h2 style={{ marginTop: 0 }}>{title}</h2>

      {failingLines.length > 0 && (
        <p role="alert" style={{ color: '#b00' }}>
          Các dòng sau không còn đủ chỗ: {failingLines.map(index => `dòng ${Number(index) + 1}`).join(', ')}.
          Hệ thống không giữ chỗ nào; bạn có thể sửa lại giỏ rồi báo giá lại.
        </p>
      )}

      <div style={{ display: 'grid', gap: '14px' }}>
        {(quote.lines || []).map(line => (
          <article key={line.lineIndex} style={{ border: '1px solid #e3e3e3', padding: '14px 16px', background: '#fff' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', gap: '12px', flexWrap: 'wrap' }}>
              <strong style={{ color: '#002967' }}>{line.packageName || `Gói #${line.packageId}`}</strong>
              <span className="muted">{line.participantCount} vé · {vnd(line.pricePerParticipantVnd)} / vé</span>
            </div>
            {Array.isArray(line.sessions) && line.sessions.length > 0 && (
              <ul className="muted" style={{ margin: '8px 0 0', paddingLeft: '20px', fontSize: '13px' }}>
                {line.sessions.map(session => (
                  <li key={session.sessionReference}>
                    Buổi {session.ordinal}: {formatSessionWindow(session.scheduledStart, session.scheduledEnd)}
                  </li>
                ))}
              </ul>
            )}

            <table className="cart-quote-tickets" style={{ width: '100%', marginTop: '10px', fontSize: '14px', borderCollapse: 'collapse' }}>
              <thead>
                <tr style={{ textAlign: 'left' }}>
                  <th scope="col">Vé</th>
                  <th scope="col">Giá vé</th>
                  <th scope="col">Trả khi đặt</th>
                  <th scope="col">Còn lại tại quầy</th>
                  <th scope="col">Hình thức</th>
                </tr>
              </thead>
              <tbody>
                {(line.tickets || []).map(ticket => (
                  <tr key={`${line.lineIndex}-${ticket.participantOrdinal}`} style={{ borderTop: '1px solid #f0f0f0' }}>
                    <td>Người {ticket.participantOrdinal}</td>
                    <td>{vnd(ticket.finalPriceVnd)}</td>
                    <td>{vnd(ticket.collectedAtBookingVnd)}</td>
                    <td>{vnd(ticket.remainingVnd)}</td>
                    <td><span className="status">{collectionKindLabel(ticket.collectionKind)}</span></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </article>
        ))}
      </div>

      <div style={{ marginTop: '16px', lineHeight: '2' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between' }}>
          <span>Tổng giá gói</span><strong>{vnd(quote.grossVnd)}</strong>
        </div>
        {Number(quote.voucherDiscountVnd || 0) > 0 && (
          <div style={{ display: 'flex', justifyContent: 'space-between', color: '#2b8a3e' }}>
            <span>Voucher</span><span>−{vnd(quote.voucherDiscountVnd)}</span>
          </div>
        )}
        {Number(quote.pointDiscountVnd || 0) > 0 && (
          <div style={{ display: 'flex', justifyContent: 'space-between', color: '#2b8a3e' }}>
            <span>Điểm thưởng ({Number(quote.pointsUsed || 0).toLocaleString('vi-VN')} điểm)</span>
            <span>−{vnd(quote.pointDiscountVnd)}</span>
          </div>
        )}
        <div style={{ display: 'flex', justifyContent: 'space-between', fontWeight: 'bold' }}>
          <span>Tổng sau ưu đãi</span><span>{vnd(quote.finalTotalVnd)}</span>
        </div>
        <div style={{ display: 'flex', justifyContent: 'space-between', color: '#002967', fontWeight: 'bold', borderTop: '1px solid #eee', paddingTop: '8px' }}>
          <span>Thanh toán khi đặt</span><span>{vnd(quote.groupCollectionVnd)}</span>
        </div>
        <div style={{ display: 'flex', justifyContent: 'space-between', color: '#595959', fontSize: '13px' }}>
          <span>Còn lại thanh toán tại quầy</span><span>{vnd(quote.remainingAtCheckoutVnd)}</span>
        </div>
      </div>

      {deferred ? (
        <p role="note" style={{ marginTop: '12px', padding: '12px 14px', background: '#f0f5ff', borderLeft: '4px solid #002967' }}>
          {deferredNotice(quote.remainingAtCheckoutVnd)}
        </p>
      ) : (
        <p role="note" style={{ marginTop: '12px', padding: '12px 14px', background: '#fffbe6', borderLeft: '4px solid #faad14' }}>
          {free ? 'Ưu đãi đã trả toàn bộ giá vé nên không cần thanh toán online. ' : ''}{NON_REFUNDABLE_NOTICE}
        </p>
      )}

      <div className="form-grid" style={{ marginTop: '16px' }}>
        <label>
          Email nhận vé &amp; xác nhận *
          <input
            type="email"
            value={contactValue.contactEmail}
            onChange={event => onContactChange({ ...contactValue, contactEmail: event.target.value })}
            disabled={confirming}
            required
          />
          {validationErrors.contactEmail && <small style={{ color: '#b00' }}>{validationErrors.contactEmail}</small>}
        </label>
        <label>
          Số điện thoại liên hệ *
          <input
            type="tel"
            value={contactValue.contactPhone}
            onChange={event => onContactChange({ ...contactValue, contactPhone: event.target.value })}
            disabled={confirming}
            required
          />
          {validationErrors.contactPhone && <small style={{ color: '#b00' }}>{validationErrors.contactPhone}</small>}
        </label>
      </div>
      <p className="muted" style={{ fontSize: '13px' }}>
        Thông tin người đi cùng không cần khai lúc này; sau khi xác nhận bạn có thể gửi từng vé cho họ qua email.
      </p>

      {confirmError && (
        <div role="alert" style={{ marginTop: '12px', color: '#b00' }}>
          <strong>{confirmError.code || 'Không thể xác nhận'}</strong>
          <p style={{ margin: '4px 0 0' }}>{confirmError.message}</p>
          {confirmError.correlationId && (
            <p className="muted" style={{ fontSize: '12px', margin: 0 }}>Mã đối soát: {confirmError.correlationId}</p>
          )}
        </div>
      )}

      <div className="actions" style={{ marginTop: '16px', display: 'flex', gap: '10px', flexWrap: 'wrap' }}>
        <button type="button" className="button primary" onClick={onConfirm} disabled={confirming}>
          {confirming ? 'Đang giữ chỗ...' : 'Xác nhận và giữ chỗ'}
        </button>
        {onCancel && (
          <button type="button" className="button outline" onClick={onCancel} disabled={confirming}>
            Chọn lại
          </button>
        )}
      </div>
    </section>
  );
}
