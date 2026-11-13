import React, { useState } from 'react';
import { useFormat } from '../shared/i18n/I18nProvider';
import '../features/retail-order/ui/readyringsales.css';

const isWhole = value => /^(0|[1-9][0-9]*)$/.test(String(value ?? ''));

/** A whole-VND or whole-point figure as the server reported it, or null when it did not report one. */
const wholeOf = value => (isWhole(value) ? String(value) : null);

/**
 * Voucher and points for a cart quote (003.1 FR-033, FR-034). One voucher use covers one group confirmation and the
 * points are asked for the whole cart; Feature 006 decides what may be used and `POST /workshop-cart-quotes` reports
 * the limits (`maxPoints`, `minRedeemPoints`, `pointValueVnd`), its `reasons` and what was applied per ticket.
 * Nothing is computed here: the point switch offers exactly the `maxPoints` of the current quote, the balance comes
 * from the Loyalty account and every amount comes from the quote.
 *
 * Props: selection { choice: 'NONE'|'VOUCHER', voucherCode, requestedPoints }, onChange(selection),
 * savedVouchers [{ code, name, availability }], offers [{ code, name, discount }],
 * account { availablePoints, heldPoints, policy }, quote (the current cart quote, or null), pending.
 */
export function CartBenefitPicker({ selection, onChange, savedVouchers = [], offers = [], account, quote, pending }) {
  const format = useFormat();
  const [pickerOpen, setPickerOpen] = useState(false);
  const [typedCode, setTypedCode] = useState('');

  const usable = savedVouchers.filter(item => item.availability === 'ACTIVE');
  const benefitText = offer => {
    const discount = offer?.discount;
    if (!discount) return '';
    const cap = discount.maxDiscountAmount ? `, tối đa ${format.money(discount.maxDiscountAmount)}` : '';
    return discount.kind === 'PERCENT' ? `Giảm ${discount.percentage}%${cap}` : `Giảm ${format.money(discount.amount)}`;
  };
  const offerByCode = Object.fromEntries(offers.map(offer => [offer.code, offer]));
  const suggestions = [
    ...usable.map(item => ({ code: item.code, name: item.name, benefit: benefitText(offerByCode[item.code]), saved: true })),
    ...offers.filter(offer => !usable.some(item => item.code === offer.code))
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
  const reasons = Array.isArray(quote?.reasons) ? quote.reasons.filter(Boolean) : [];

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
    <section className="retail-benefits" aria-label="Ưu đãi và điểm thành viên">
      <button type="button" className="retail-benefit-row" onClick={() => setPickerOpen(true)}>
        <span className="retail-benefit-icon">▣</span><strong>Voucher MLAND</strong>
        <span className={voucherChosen ? 'benefit-applied' : 'muted'}>
          {voucherChosen
            ? (selectedVoucher?.name || suggestions.find(item => item.code === selection.voucherCode)?.name || selection.voucherCode)
            : 'Chọn hoặc nhập mã'}
        </span>
        <span>›</span>
      </button>

      <div className="retail-benefit-row point-balance" aria-label="Điểm của bạn">
        <span className="retail-benefit-icon">Ⓟ</span><strong>Điểm của bạn</strong>
        {available === null
          ? <span className="muted">Chưa tải được số điểm</span>
          : (
            <span className="point-balance-value">
              <b>{format.number(available)} điểm</b> khả dụng
              <small>
                {[
                  held ? `đang giữ ${format.number(held)} điểm cho đơn khác` : null,
                  pointValue ? `1 điểm = ${format.money(pointValue)}` : null,
                  minRedeem && minRedeem !== '0' ? `dùng tối thiểu ${format.number(minRedeem)} điểm` : null,
                ].filter(Boolean).join(' · ')}
              </small>
            </span>
          )}
        <span />
      </div>

      <label className="retail-benefit-row point-row">
        <span className="retail-benefit-icon" aria-hidden="true" />
        <strong>Dùng {format.number(limitKnown ? maxPoints : '0')} điểm cho giỏ này</strong>
        <span className="muted">
          {!limitKnown ? 'Báo giá giỏ để hệ thống tính số điểm tối đa được dùng…'
            : belowMinimum ? `Cần tối thiểu ${format.number(minRedeem)} điểm để sử dụng`
              : canUsePoints ? 'Điểm tối đa theo báo giá hiện tại' : 'Bạn chưa có điểm dùng được cho giỏ này'}
        </span>
        <input
          className="switch"
          type="checkbox"
          role="switch"
          aria-label="Dùng điểm thành viên"
          checked={pointsOn}
          disabled={pending || ((!canUsePoints) && !pointsOn)}
          onChange={event => onChange({ ...selection, requestedPoints: event.target.checked ? maxPoints : '0' })}
        />
      </label>

      {quote && (
        <div className="retail-benefit-quote breakdown" aria-live="polite">
          <dl>
            <div><dt>Phí gói workshop</dt><dd>{format.money(quote.grossVnd)}</dd></div>
            <div><dt>Giảm giá voucher</dt><dd>−{format.money(quote.voucherDiscountVnd)}</dd></div>
            <div><dt>Giảm bằng {format.number(quote.pointsUsed ?? '0')} điểm</dt><dd>−{format.money(quote.pointDiscountVnd)}</dd></div>
            <div><dt>Tiền workshop sau ưu đãi</dt><dd>{format.money(quote.finalTotalVnd)}</dd></div>
            <div className="total"><dt>Thanh toán khi đặt</dt><dd>{format.money(quote.groupCollectionVnd)}</dd></div>
            <div><dt>Còn lại thanh toán tại quầy</dt><dd>{format.money(quote.remainingAtCheckoutVnd)}</dd></div>
          </dl>
          {/* 006 says why a voucher or the points were not applied; the screen never guesses a reason. */}
          {reasons.length > 0 && <p className="notice error" role="status">{reasons.join(' ')}</p>}
        </div>
      )}

      {pickerOpen && (
        <div className="benefit-picker-backdrop" role="presentation">
          <section className="benefit-picker" role="dialog" aria-modal="true" aria-label="Chọn voucher">
            <div className="card-head">
              <h2>Chọn voucher MLAND</h2>
              <button type="button" className="text-link" onClick={() => setPickerOpen(false)}>Đóng</button>
            </div>
            <div className="voucher-code-entry">
              <input
                value={typedCode}
                maxLength="128"
                placeholder="Nhập mã voucher"
                onChange={event => setTypedCode(event.target.value)}
                onKeyDown={event => { if (event.key === 'Enter') { event.preventDefault(); applyVoucher(typedCode); } }}
              />
              <button type="button" className="button primary" onClick={() => applyVoucher(typedCode)}>Áp dụng</button>
            </div>
            <h3>Voucher đề xuất</h3>
            {suggestions.length === 0 && (
              <p className="muted">Hiện chưa có voucher nào áp dụng cho workshop này. Bạn vẫn có thể nhập mã ở trên.</p>
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
                  <small>{item.code}{item.benefit ? ` · ${item.benefit}` : ''}{item.saved ? ' · Trong Kho Voucher' : ''}</small>
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
