import React from 'react';
import { useFormat, useLanguage, useT } from '../shared/i18n/I18nProvider';
import { messages } from './messages';
import {
  nonRefundableNotice,
  collectionKindLabel,
  deferredNotice,
  formatSessionWindow,
  isDeferredGroup,
  isFreeGroup,
} from './groupDisplay';
import { EMPTY_CONTACT } from './contactValidation';
import { FIELD_STYLE, PhoneInput } from './PhoneInput';

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
  failingCode = null,
  packageHrefOf = null,
  title,
}) {
  const t = useT(messages);
  const { language } = useLanguage();
  const format = useFormat();
  const vnd = value => format.money(String(Math.round(Number(value || 0))));
  if (!quote) return null;
  const heading = title || t('quote.defaultTitle');
  const deferred = isDeferredGroup(quote.paymentMode);
  const free = isFreeGroup(quote.paymentMode);
  const contactValue = contact || EMPTY_CONTACT;

  return (
    <section className="workspace-card cart-quote" aria-label={heading} style={{ marginTop: '20px', borderTop: '4px solid #002967' }}>
      <h2 style={{ marginTop: 0 }}>{heading}</h2>

      {failingLines.length > 0 && failingCode === 'PACKAGE_NOT_AVAILABLE_AT_LOCATION' && (
        <div role="alert" style={{ color: '#b00' }}>
          <p style={{ margin: 0 }}>
            {t('quote.notAvailable', { lines: failingLines.map(index => t('quote.lineN', { n: Number(index) + 1 })).join(', ') })}
          </p>
          {packageHrefOf && failingLines.map(index => (
            <p key={index} style={{ margin: '4px 0 0' }}>
              <a className="text-link" href={packageHrefOf(index)}>{t('quote.pickOther', { n: Number(index) + 1 })}</a>
            </p>
          ))}
        </div>
      )}
      {failingLines.length > 0 && failingCode !== 'PACKAGE_NOT_AVAILABLE_AT_LOCATION' && (
        <p role="alert" style={{ color: '#b00' }}>
          {t('quote.noCapacity', { lines: failingLines.map(index => t('quote.lineLower', { n: Number(index) + 1 })).join(', ') })}
        </p>
      )}

      <div style={{ display: 'grid', gap: '14px' }}>
        {(quote.lines || []).map(line => (
          <article key={line.lineIndex} style={{ border: '1px solid #e3e3e3', padding: '14px 16px', background: '#fff' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', gap: '12px', flexWrap: 'wrap' }}>
              <strong style={{ color: '#002967' }}>{line.packageName || t('quote.package', { id: line.packageId })}</strong>
              <span className="muted">{t('quote.ticketsPrice', { count: line.participantCount, price: vnd(line.pricePerParticipantVnd) })}</span>
            </div>
            {Array.isArray(line.sessions) && line.sessions.length > 0 && (
              <ul className="muted" style={{ margin: '8px 0 0', paddingLeft: '20px', fontSize: '13px' }}>
                {line.sessions.map(session => (
                  <li key={session.sessionReference}>
                    {t('quote.session', { n: session.ordinal, window: formatSessionWindow(session.scheduledStart, session.scheduledEnd, language) })}
                  </li>
                ))}
              </ul>
            )}

            <table className="cart-quote-tickets" style={{ width: '100%', marginTop: '10px', fontSize: '14px', borderCollapse: 'collapse' }}>
              <thead>
                <tr style={{ textAlign: 'left' }}>
                  <th scope="col">{t('quote.th.ticket')}</th>
                  <th scope="col">{t('quote.th.price')}</th>
                  <th scope="col">{t('quote.th.payNow')}</th>
                  <th scope="col">{t('quote.th.remaining')}</th>
                  <th scope="col">{t('quote.th.kind')}</th>
                </tr>
              </thead>
              <tbody>
                {(line.tickets || []).map(ticket => (
                  <tr key={`${line.lineIndex}-${ticket.participantOrdinal}`} style={{ borderTop: '1px solid #f0f0f0' }}>
                    <td>{t('quote.person', { n: ticket.participantOrdinal })}</td>
                    <td>{vnd(ticket.finalPriceVnd)}</td>
                    <td>{vnd(ticket.collectedAtBookingVnd)}</td>
                    <td>{vnd(ticket.remainingVnd)}</td>
                    <td><span className="status">{collectionKindLabel(ticket.collectionKind, language)}</span></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </article>
        ))}
      </div>

      <div style={{ marginTop: '16px', lineHeight: '2' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between' }}>
          <span>{t('quote.gross')}</span><strong>{vnd(quote.grossVnd)}</strong>
        </div>
        {Number(quote.autoDiscountVnd || 0) > 0 && (
          <div style={{ display: 'flex', justifyContent: 'space-between', color: '#2b8a3e' }}>
            <span>{t('quote.auto')}</span><span>−{vnd(quote.autoDiscountVnd)}</span>
          </div>
        )}
        {Number(quote.voucherDiscountVnd || 0) > 0 && (
          <div style={{ display: 'flex', justifyContent: 'space-between', color: '#2b8a3e' }}>
            <span>{t('quote.voucher')}</span><span>−{vnd(quote.voucherDiscountVnd)}</span>
          </div>
        )}
        {Number(quote.pointDiscountVnd || 0) > 0 && (
          <div style={{ display: 'flex', justifyContent: 'space-between', color: '#2b8a3e' }}>
            <span>{t('quote.points', { count: format.number(String(quote.pointsUsed || 0)) })}</span>
            <span>−{vnd(quote.pointDiscountVnd)}</span>
          </div>
        )}
        <div style={{ display: 'flex', justifyContent: 'space-between', fontWeight: 'bold' }}>
          <span>{t('quote.finalTotal')}</span><span>{vnd(quote.finalTotalVnd)}</span>
        </div>
        <div style={{ display: 'flex', justifyContent: 'space-between', color: '#002967', fontWeight: 'bold', borderTop: '1px solid #eee', paddingTop: '8px' }}>
          <span>{t('quote.payNow')}</span><span>{vnd(quote.groupCollectionVnd)}</span>
        </div>
        <div style={{ display: 'flex', justifyContent: 'space-between', color: '#595959', fontSize: '13px' }}>
          <span>{t('quote.remaining')}</span><span>{vnd(quote.remainingAtCheckoutVnd)}</span>
        </div>
      </div>

      {deferred ? (
        <p role="note" style={{ marginTop: '12px', padding: '12px 14px', background: '#f0f5ff', borderLeft: '4px solid #002967' }}>
          {deferredNotice(quote.remainingAtCheckoutVnd, language)}
        </p>
      ) : (
        <p role="note" style={{ marginTop: '12px', padding: '12px 14px', background: '#fffbe6', borderLeft: '4px solid #faad14' }}>
          {free ? t('quote.free') : ''}{nonRefundableNotice(language)}
        </p>
      )}

      <div className="form-grid" style={{ marginTop: '16px', alignItems: 'start' }}>
        <label>
          {t('quote.email')}
          <input
            type="email"
            value={contactValue.contactEmail}
            onChange={event => onContactChange({ ...contactValue, contactEmail: event.target.value })}
            disabled={confirming}
            required
            style={{ ...FIELD_STYLE, width: '100%' }}
          />
          {validationErrors.contactEmail && <small style={{ color: '#b00' }}>{validationErrors.contactEmail}</small>}
        </label>
        {/* A div, not a label: a label around the country select would name it "contact phone number" too. */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: '7px', fontSize: '14px', fontWeight: 700 }}>
          <label htmlFor="booking-contact-phone">{t('quote.phone')}</label>
          <PhoneInput
            id="booking-contact-phone"
            country={contactValue.contactPhoneCountry}
            value={contactValue.contactPhone}
            onCountryChange={contactPhoneCountry => onContactChange({ ...contactValue, contactPhoneCountry })}
            onValueChange={contactPhone => onContactChange({ ...contactValue, contactPhone })}
            disabled={confirming}
            required
          />
          {validationErrors.contactPhone && <small style={{ color: '#b00' }}>{validationErrors.contactPhone}</small>}
        </div>
      </div>
      <p className="muted" style={{ fontSize: '13px' }}>
        {t('quote.companions')}
      </p>

      {confirmError && (
        <div role="alert" style={{ marginTop: '12px', color: '#b00' }}>
          <strong>{confirmError.code || t('quote.cannotConfirm')}</strong>
          <p style={{ margin: '4px 0 0' }}>{confirmError.messageKey ? t(confirmError.messageKey) : confirmError.message}</p>
          {confirmError.correlationId && (
            <p className="muted" style={{ fontSize: '12px', margin: 0 }}>{t('quote.correlation', { id: confirmError.correlationId })}</p>
          )}
        </div>
      )}

      <div className="actions" style={{ marginTop: '16px', display: 'flex', gap: '10px', flexWrap: 'wrap' }}>
        <button type="button" className="button primary" onClick={onConfirm} disabled={confirming}>
          {confirming ? t('quote.confirming') : t('quote.confirm')}
        </button>
        {onCancel && (
          <button type="button" className="button outline" onClick={onCancel} disabled={confirming}>
            {t('quote.reselect')}
          </button>
        )}
      </div>
    </section>
  );
}
