import React, { useState } from 'react';
import { useFormat, useT } from '../shared/i18n/I18nProvider';
import { messages } from '../features/loyalty-promotion/ui/messages';
import { messages as workshopMessages } from './messages';
import { reasonTexts } from '../features/loyalty-promotion/ui/checkout/BenefitSelector';
import '../features/retail-order/ui/readyringsales.css';

const isWhole = value => /^(0|[1-9][0-9]*)$/.test(String(value ?? ''));

/** A whole-VND or whole-point figure as the server reported it, or null when it did not report one. */
const wholeOf = value => (isWhole(value) ? String(value) : null);

/**
 * Running offers the Member can still use: not one with no use left in total, and not a saved one that 006 reports as
 * anything but ACTIVE (expired, paused, the Member's own limit reached).
 */
export const usableOffers = (offers, savedVouchers) => offers.filter(offer => offer.availability !== 'NO_USES_LEFT'
  && !savedVouchers.some(item => item.code === offer.code && item.availability && item.availability !== 'ACTIVE'));

/**
 * Voucher and points for a cart quote (003.1 FR-033, FR-034). One voucher use covers one group confirmation and the
 * points are asked for the whole cart; Feature 006 decides what may be used and `POST /workshop-cart-quotes` reports
 * the limits (`maxPoints`, `minRedeemPoints`, `pointValueVnd`), its `reasons` and what was applied per ticket.
 * Nothing is computed here: the point switch offers exactly the `maxPoints` of the current quote, the balance comes
 * from the Loyalty account and every amount comes from the quote.
 *
 * Props: selection { choice: 'NONE'|'VOUCHER', voucherCode, requestedPoints }, onChange(selection),
 * savedVouchers [{ code, name, availability, scope, packageIds }], offers [{ code, name, discount }],
 * packageIds (the packages in this cart),
 * account { availablePoints, heldPoints, policy }, quote (the current cart quote, or null), pending.
 */
export function CartBenefitPicker({ selection, onChange, savedVouchers = [], offers = [], packageIds = [], account, quote, pending }) {
  const format = useFormat();
  const t = useT(messages);
  const tw = useT(workshopMessages);
  const [pickerOpen, setPickerOpen] = useState(false);
  const [typedCode, setTypedCode] = useState('');

  // Only suggest what can apply here: a saved voucher must be active, of WORKSHOP scope and not limited to other
  // packages. 006 still decides at quote time (BUG-E2E-0009: Product vouchers and vouchers for another package were
  // suggested and then refused by the quote). A voucher that reports no scope or no package limit stays suggested.
  const cartPackages = packageIds.map(String);
  const appliesHere = item => {
    if (item.scope && item.scope !== 'WORKSHOP') return false;
    const limited = (item.packageIds || []).map(String);
    return limited.length === 0 || cartPackages.length === 0 || limited.some(id => cartPackages.includes(id));
  };
  const usable = savedVouchers.filter(item => item.availability === 'ACTIVE' && appliesHere(item));
  const benefitText = offer => {
    const discount = offer?.discount;
    if (!discount) return '';
    const cap = discount.maxDiscountAmount ? tw('benefit.discount.cap', { amount: format.money(discount.maxDiscountAmount) }) : '';
    return discount.kind === 'PERCENT' ? tw('benefit.discount.percent', { percent: discount.percentage, cap }) : tw('benefit.discount.amount', { amount: format.money(discount.amount) });
  };
  const offerByCode = Object.fromEntries(offers.map(offer => [offer.code, offer]));
  const suggestions = [
    ...usable.map(item => ({ code: item.code, name: item.name, benefit: benefitText(offerByCode[item.code]), saved: true })),
    ...usableOffers(offers, savedVouchers).filter(offer => !usable.some(item => item.code === offer.code))
      .map(offer => ({ code: offer.code, name: offer.name, benefit: benefitText(offer), saved: false })),
  ];
  const voucherChosen = selection.choice === 'VOUCHER' && Boolean(selection.voucherCode);
  const selectedVoucher = savedVouchers.find(item => item.code === selection.voucherCode);

  const available = wholeOf(account?.availablePoints);
  const held = wholeOf(account?.heldPoints) && account.heldPoints !== '0' ? String(account.heldPoints) : null;

  // The limits are quote facts (FR-034): 006 reports them per cart, and the evaluator refuses a requested amount
  // outside [minRedeemPoints, maxPoints] instead of trimming it, so the screen offers exactly maxPoints.
  const maxPoints = quote ? wholeOf(quote.maxPoints) : null;
  const minRedeem = quote ? wholeOf(quote.minRedeemPoints) : wholeOf(account?.policy?.minRedeemPoints);
  const pointValue = (quote ? wholeOf(quote.pointValueVnd) : null) || wholeOf(account?.policy?.redeemPointValue);
  const limitKnown = maxPoints !== null;
  const belowMinimum = limitKnown && minRedeem !== null && BigInt(maxPoints) > 0n && BigInt(maxPoints) < BigInt(minRedeem);
  const canUsePoints = limitKnown && BigInt(maxPoints) > 0n && !belowMinimum;

  const requested = String(selection.requestedPoints ?? '0');
  const pointsOn = requested !== '0';
  // 006's coded reasons are translated; a quote without them keeps the English text.
  const reasons = reasonTexts(quote, t, format).filter(Boolean);

  const applyVoucher = code => {
    const clean = String(code || '').trim();
    if (!clean) return;
    const deselect = voucherChosen && selection.voucherCode === clean;
    onChange({
      ...selection,
      choice: deselect ? 'NONE' : 'VOUCHER',
      voucherCode: deselect ? '' : clean,
      // The voucher changes the amount points apply to, so the request starts again.
      requestedPoints: '0',
    });
    setTypedCode('');
    setPickerOpen(false);
  };

  return (
    <section className="retail-benefits" aria-label={tw('benefit.aria')}>
      <button type="button" className="retail-benefit-row" onClick={() => setPickerOpen(true)}>
        <span className="retail-benefit-icon">▣</span><strong>{tw('benefit.voucher')}</strong>
        <span className={voucherChosen ? 'benefit-applied' : 'muted'}>
          {voucherChosen
            ? (selectedVoucher?.name || suggestions.find(item => item.code === selection.voucherCode)?.name || selection.voucherCode)
            : tw('benefit.chooseOrEnter')}
        </span>
        <span>›</span>
      </button>

      <div className="retail-benefit-row point-balance" aria-label={tw('benefit.pointsLabel')}>
        <span className="retail-benefit-icon">Ⓟ</span><strong>{tw('benefit.pointsLabel')}</strong>
        {available === null
          ? <span className="muted">{tw('benefit.pointsNotLoaded')}</span>
          : (
            <span className="point-balance-value">
              <b>{tw('benefit.pointsCount', { count: format.number(available) })}</b> {tw('benefit.available')}
              <small>
                {[
                  held ? tw('benefit.held', { count: format.number(held) }) : null,
                  pointValue ? tw('benefit.pointValue', { amount: format.money(pointValue) }) : null,
                  minRedeem && minRedeem !== '0' ? tw('benefit.minUse', { count: format.number(minRedeem) }) : null,
                ].filter(Boolean).join(' · ')}
              </small>
            </span>
          )}
        <span />
      </div>

      <label className="retail-benefit-row point-row">
        <span className="retail-benefit-icon" aria-hidden="true" />
        <strong>{tw('benefit.useForCart', { count: format.number(limitKnown ? maxPoints : '0') })}</strong>
        <span className="muted">
          {!limitKnown ? tw('benefit.hint.unknown')
            : belowMinimum ? tw('benefit.hint.belowMin', { count: format.number(minRedeem) })
              : canUsePoints ? tw('benefit.hint.max') : tw('benefit.hint.none')}
        </span>
        <input
          className="switch"
          type="checkbox"
          role="switch"
          aria-label={tw('benefit.switchAria')}
          checked={pointsOn}
          disabled={pending || ((!canUsePoints) && !pointsOn)}
          onChange={event => onChange({ ...selection, requestedPoints: event.target.checked ? maxPoints : '0' })}
        />
      </label>

      {quote && (
        <div className="retail-benefit-quote breakdown" aria-live="polite">
          <dl>
            <div><dt>{tw('benefit.fee')}</dt><dd>{format.money(quote.grossVnd)}</dd></div>
            <div><dt>{tw('benefit.auto')}</dt><dd>−{format.money(quote.autoDiscountVnd ?? 0)}</dd></div>
            <div><dt>{tw('benefit.voucherRow')}</dt><dd>−{format.money(quote.voucherDiscountVnd)}</dd></div>
            <div><dt>{tw('benefit.byPoints', { count: format.number(quote.pointsUsed ?? '0') })}</dt><dd>−{format.money(quote.pointDiscountVnd)}</dd></div>
            <div><dt>{tw('benefit.afterOffers')}</dt><dd>{format.money(quote.finalTotalVnd)}</dd></div>
            <div className="total"><dt>{tw('benefit.payNow')}</dt><dd>{format.money(quote.groupCollectionVnd)}</dd></div>
            <div><dt>{tw('benefit.remaining')}</dt><dd>{format.money(quote.remainingAtCheckoutVnd)}</dd></div>
          </dl>
          {/* 006 says why a voucher or the points were not applied; the screen never guesses a reason. */}
          {reasons.length > 0 && <p className="notice error" role="status">{reasons.join(' ')}</p>}
        </div>
      )}

      {pickerOpen && (
        <div className="benefit-picker-backdrop" role="presentation">
          <section className="benefit-picker" role="dialog" aria-modal="true" aria-label={tw('benefit.dialogAria')}>
            <div className="card-head">
              <h2>{tw('benefit.pickerTitle')}</h2>
              <button type="button" className="text-link" onClick={() => setPickerOpen(false)}>{tw('benefit.close')}</button>
            </div>
            <div className="voucher-code-entry">
              <input
                value={typedCode}
                maxLength="128"
                placeholder={tw('benefit.codePlaceholder')}
                onChange={event => setTypedCode(event.target.value)}
                onKeyDown={event => { if (event.key === 'Enter') { event.preventDefault(); applyVoucher(typedCode); } }}
              />
              <button type="button" className="button primary" onClick={() => applyVoucher(typedCode)}>{tw('benefit.apply')}</button>
            </div>
            <h3>{tw('benefit.suggested')}</h3>
            {suggestions.length === 0 && (
              <p className="muted">{tw('benefit.noneSuggested')}</p>
            )}
            {suggestions.map(item => (
              <button
                type="button"
                key={item.code}
                className={`voucher-option${item.code === selection.voucherCode && voucherChosen ? ' selected' : ''}`}
                aria-pressed={item.code === selection.voucherCode && voucherChosen}
                onClick={() => applyVoucher(item.code)}
              >
                <span className="voucher-stamp">MLAND</span>
                <span>
                  <strong>{item.name}</strong>
                  <small>{item.code}{item.benefit ? ` · ${item.benefit}` : ''}{item.saved ? ` · ${tw('benefit.savedTag')}` : ''}</small>
                </span>
                <span>{item.code === selection.voucherCode && voucherChosen ? '✓' : '○'}</span>
              </button>
            ))}
          </section>
        </div>
      )}
    </section>
  );
}
