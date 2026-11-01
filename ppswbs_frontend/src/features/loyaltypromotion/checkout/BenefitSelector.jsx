import React, { useId } from 'react';
import { useFormat, useT } from '../../../shared/i18n/I18nProvider';
import { messages } from '../messages';

/**
 * T055 offer picker and breakdown for the checkout screen of FE-08 (and later FE-03) to embed. It owns no route
 * and calls no API: the owner screen requests the quote from its own API, passes it in and sends the accept.
 * Changing a choice sends nothing; the customer presses Recalculate, reviews the breakdown, acknowledges it and
 * only then can Confirm.
 *
 * Props:
 * - value: { choice: 'NONE'|'AUTO'|'VOUCHER', voucherCode, requestedPoints } (the owner keeps this state)
 * - onChange(value)
 * - quote: owner quote mapped to { merchandiseSubtotal, discountAmount, pointsDiscountAmount, shippingTotal,
 *   shippingExcluded, payableAmount, maxPoints, eligible, reasons } or null before the first calculation
 * - quoteMatches: true when quote was calculated for the current value (otherwise Confirm stays disabled)
 * - savedVouchers: [{ code, name, availability }] from the Member's Kho Voucher (optional)
 * - acknowledged / onAcknowledge(boolean), onRecalculate(), onConfirm(), pending
 */
export function BenefitSelector({ value, onChange, quote, quoteMatches, savedVouchers = [], acknowledged, onAcknowledge, onRecalculate, onConfirm, pending }) {
  const t = useT(messages);
  const format = useFormat();
  const id = useId();
  const set = patch => onChange({ ...value, ...patch });
  const usable = savedVouchers.filter(voucher => voucher.availability === 'ACTIVE');
  const pointsInvalid = !/^(0|[1-9][0-9]{0,18})$/.test(String(value.requestedPoints ?? ''));
  const canConfirm = Boolean(quote && quoteMatches && quote.eligible && acknowledged && !pending);

  return (
    <section className="benefit-selector" aria-labelledby={`${id}-title`}>
      <h2 id={`${id}-title`}>{t('checkout.title')}</h2>
      <fieldset>
        <legend className="sr-only">{t('checkout.choice')}</legend>
        {['NONE', 'AUTO', 'VOUCHER'].map(choice => (
          <label className="check-row" key={choice}>
            <input type="radio" name={`${id}-choice`} checked={value.choice === choice} onChange={() => set({ choice })} /> {t(`checkout.choice.${choice}`)}
          </label>
        ))}
      </fieldset>
      {value.choice === 'VOUCHER' && (
        <div className="form-grid">
          <div className="field">
            <label htmlFor={`${id}-code`}>{t('checkout.code')}</label>
            <input id={`${id}-code`} value={value.voucherCode || ''} maxLength={128} onChange={event => set({ voucherCode: event.target.value })} />
          </div>
          {usable.length > 0 && (
            <div className="field">
              <label htmlFor={`${id}-saved`}>{t('checkout.saved')}</label>
              <select id={`${id}-saved`} value="" onChange={event => event.target.value && set({ voucherCode: event.target.value })}>
                <option value="">{t('checkout.pickSaved')}</option>
                {usable.map(voucher => <option key={voucher.code} value={voucher.code}>{voucher.name} ({voucher.code})</option>)}
              </select>
            </div>
          )}
        </div>
      )}
      <div className="field points-field">
        <label htmlFor={`${id}-points`}>{t('checkout.points')}</label>
        <input id={`${id}-points`} inputMode="numeric" value={value.requestedPoints ?? '0'} aria-invalid={pointsInvalid ? true : undefined}
          aria-describedby={`${id}-max`} onChange={event => set({ requestedPoints: event.target.value })} />
        <span className="muted small" id={`${id}-max`}>
          {quote?.maxPoints !== undefined && quote?.maxPoints !== null ? t('checkout.maxPoints', { points: format.number(quote.maxPoints) }) : t('checkout.maxPointsAfterCalc')}
        </span>
        {pointsInvalid && <span className="field-error">{t('validation.amount')}</span>}
      </div>
      <button type="button" className="button outline" disabled={pending || pointsInvalid} onClick={onRecalculate}>{t('checkout.recalculate')}</button>

      {quote && (
        <div className="breakdown" aria-live="polite">
          {!quoteMatches && <p className="notice error">{t('checkout.outdated')}</p>}
          <dl>
            <div><dt>{t('checkout.goods')}</dt><dd>{format.money(quote.merchandiseSubtotal)}</dd></div>
            <div><dt>{t('checkout.programDiscount')}</dt><dd>−{format.money(quote.discountAmount)}</dd></div>
            <div><dt>{t('checkout.pointsDiscount')}</dt><dd>−{format.money(quote.pointsDiscountAmount)}</dd></div>
            <div><dt>{t('checkout.shipping')}</dt><dd>{quote.shippingExcluded ? t('checkout.shippingLater') : format.money(quote.shippingTotal)}</dd></div>
            <div className="total"><dt>{t('checkout.payable')}</dt><dd>{format.money(quote.payableAmount)}</dd></div>
          </dl>
          {quote.reasons?.length > 0 && (
            <ul className="reasons" aria-label={t('checkout.reasons')}>
              {quote.reasons.map(reason => <li key={reason}>{reason}</li>)}
            </ul>
          )}
          <label className="check-row">
            <input type="checkbox" checked={Boolean(acknowledged)} disabled={!quoteMatches || !quote.eligible} onChange={event => onAcknowledge(event.target.checked)} /> {t('checkout.ack')}
          </label>
        </div>
      )}
      <button type="button" className="button primary" disabled={!canConfirm} onClick={onConfirm}>{t('checkout.confirm')}</button>
    </section>
  );
}
