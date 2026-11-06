import React, { useState } from 'react';
import { useFormat } from '../shared/i18n/I18nProvider';
import '../features/readyringsales/readyringsales.css';

const isWhole = value => /^(0|[1-9][0-9]*)$/.test(String(value ?? ''));

/**
 * Workshop booking offers, built like the product checkout (RetailBenefitPanel): one "Voucher MLAND" row that opens a
 * picker (type a code or pick from Kho Voucher), and one switch that uses all the points the quote allows. A workshop has
 * no automatic discount. Nothing is sent from here: the booking page requests the quote by itself after every choice,
 * the Member reads the breakdown and acknowledges it. The deposit and the remaining amount both come from the post-promotion amount
 * (FE-06), so points are never deducted twice.
 *
 * Props: selection { choice: 'NONE'|'VOUCHER', voucherCode, requestedPoints }, onChange(selection), savedVouchers
 * [{ code, name, availability }], offers (running workshop vouchers from the public list: [{ code, name, discount }]), account { availablePoints, heldPoints, balancePoints, policy } (the Member's points, shown before
 * choosing), maxPoints (known only after a quote for the current voucher), quote, quoteMatches,
 * acknowledged / onAcknowledge, onRecalculate, pending.
 */
export function WorkshopBenefitPanel({ selection, onChange, savedVouchers = [], offers = [], account, maxPoints, quote, quoteMatches, acknowledged, onAcknowledge, onRecalculate, pending }) {
  const format = useFormat();
  const [pickerOpen, setPickerOpen] = useState(false);
  const [typedCode, setTypedCode] = useState('');
  const usable = savedVouchers.filter(item => item.availability === 'ACTIVE');
  // Saved vouchers first, then the running workshop vouchers the Member has not saved (a code can be used without saving it).
  const benefitText = offer => {
    const discount = offer.discount;
    if (!discount) return '';
    const cap = discount.maxDiscountAmount ? `, tối đa ${format.money(discount.maxDiscountAmount)}` : '';
    return discount.kind === 'PERCENT' ? `Giảm ${discount.percentage}%${cap}` : `Giảm ${format.money(discount.amount)}`;
  };
  const offerByCode = Object.fromEntries(offers.map(offer => [offer.code, offer]));
  const suggestions = [
    ...usable.map(item => ({ code: item.code, name: item.name, benefit: benefitText(offerByCode[item.code] || {}), saved: true })),
    ...offers.filter(offer => !usable.some(item => item.code === offer.code)).map(offer => ({ code: offer.code, name: offer.name, benefit: benefitText(offer), saved: false })),
  ];
  const selectedVoucher = savedVouchers.find(item => item.code === selection.voucherCode);
  const voucherChosen = selection.choice === 'VOUCHER' && Boolean(selection.voucherCode);
  const pointsOn = String(selection.requestedPoints ?? '0') !== '0';
  const limitKnown = isWhole(maxPoints);
  const canUsePoints = limitKnown && BigInt(maxPoints) > 0n;
  const eligible = Boolean(quote) && !(quote.reasons?.length);
  const available = isWhole(account?.availablePoints) ? account.availablePoints : null;
  const held = isWhole(account?.heldPoints) && account.heldPoints !== '0' ? account.heldPoints : null;
  const pointValue = isWhole(account?.policy?.redeemPointValue) ? account.policy.redeemPointValue : null;
  const minRedeem = isWhole(account?.policy?.minRedeemPoints) ? account.policy.minRedeemPoints : null;
  const belowMinimum = limitKnown && minRedeem !== null && BigInt(maxPoints) > 0n && BigInt(maxPoints) < BigInt(minRedeem);

  const applyVoucher = code => {
    const clean = code.trim();
    if (!clean) return;
    const deselect = voucherChosen && selection.voucherCode === clean;
    // A new voucher changes the amount the points are capped on, so points start again from 0 until recalculated.
    onChange({ ...selection, choice: deselect ? 'NONE' : 'VOUCHER', voucherCode: deselect ? '' : clean, requestedPoints: '0' });
    setTypedCode('');
    setPickerOpen(false);
  };
  const togglePoints = checked => onChange({ ...selection, requestedPoints: checked ? String(maxPoints) : '0' });

  return (
    <section className="retail-benefits" aria-label="Ưu đãi và điểm thành viên">
      <button type="button" className="retail-benefit-row" onClick={() => setPickerOpen(true)}>
        <span className="retail-benefit-icon">▣</span><strong>Voucher MLAND</strong>
        <span className={voucherChosen ? 'benefit-applied' : 'muted'}>
          {voucherChosen ? (selectedVoucher?.name || suggestions.find(item => item.code === selection.voucherCode)?.name || selection.voucherCode) : 'Chọn hoặc nhập mã'}
        </span>
        <span>›</span>
      </button>
      <div className="retail-benefit-row point-balance" aria-label="Điểm của bạn">
        <span className="retail-benefit-icon">Ⓟ</span><strong>Điểm của bạn</strong>
        {available === null
          ? <span className="muted">Chưa tải được số điểm</span>
          : <span className="point-balance-value"><b>{format.number(available)} điểm</b> khả dụng
            <small>{[held ? `đang giữ ${format.number(held)} điểm cho đơn khác` : null, pointValue ? `1 điểm = ${format.money(pointValue)}` : null, minRedeem && minRedeem !== '0' ? `dùng tối thiểu ${format.number(minRedeem)} điểm` : null].filter(Boolean).join(' · ')}</small></span>}
        <span />
      </div>
      <label className="retail-benefit-row point-row">
        <span className="retail-benefit-icon" aria-hidden="true" />
        <strong>Dùng {format.number(limitKnown ? maxPoints : '0')} điểm cho đặt chỗ này</strong>
        <span className="muted">{!limitKnown ? 'Đang tính số điểm tối đa được dùng…'
          : belowMinimum ? `Cần tối thiểu ${format.number(minRedeem)} điểm để sử dụng`
            : canUsePoints ? 'Điểm tối đa theo báo giá' : 'Bạn chưa có điểm dùng được cho đặt chỗ này'}</span>
        <input className="switch" type="checkbox" role="switch" aria-label="Dùng điểm thành viên" checked={pointsOn}
          disabled={pending || ((!canUsePoints || belowMinimum) && !pointsOn)} onChange={event => togglePoints(event.target.checked)} />
      </label>

      {quote && (
        <div className="retail-benefit-quote breakdown" aria-live="polite">
          {!quoteMatches && <p className="notice error">Lựa chọn đã đổi. Hãy tính lại tổng tiền.</p>}
          <dl>
            <div><dt>Phí gói workshop</dt><dd>{format.money(quote.grossAmount)}</dd></div>
            <div><dt>Giảm giá voucher</dt><dd>−{format.money(quote.programDiscount)}</dd></div>
            <div><dt>Giảm bằng {format.number(quote.pointsUsed)} điểm</dt><dd>−{format.money(quote.pointDiscount)}</dd></div>
            <div><dt>Tiền workshop sau ưu đãi</dt><dd>{format.money(quote.postPromotionAmount)}</dd></div>
            <div className="total"><dt>Tiền đặt cọc</dt><dd>{format.money(quote.depositAmount)}</dd></div>
            <div><dt>Còn lại thanh toán sau</dt><dd>{format.money(quote.remainingAmount)}</dd></div>
          </dl>
          <p className="muted small">Số tiền còn lại được tính từ tiền workshop sau ưu đãi, nên điểm không bị trừ hai lần.</p>
          {quote.reasons?.length > 0 && <p className="notice error">{quote.reasons.join(' ')}</p>}
          <label className="check-row">
            <input type="checkbox" checked={Boolean(acknowledged)} disabled={!quoteMatches || !eligible} onChange={event => onAcknowledge(event.target.checked)} /> Tôi đã xem tổng tiền và ưu đãi.
          </label>
        </div>
      )}
      {/* The quote is requested automatically after every choice; this only appears when the last quote failed or is out of date. */}
      {!quoteMatches && !pending && (
        <div className="retail-benefit-actions">
          <button type="button" className="button outline" onClick={onRecalculate}>Thử tính lại</button>
        </div>
      )}

      {pickerOpen && (
        <div className="benefit-picker-backdrop" role="presentation">
          <section className="benefit-picker" role="dialog" aria-modal="true" aria-label="Chọn voucher">
            <div className="card-head"><h2>Chọn voucher MLAND</h2><button type="button" className="text-link" onClick={() => setPickerOpen(false)}>Đóng</button></div>
            <div className="voucher-code-entry">
              <input value={typedCode} maxLength="128" placeholder="Nhập mã voucher" onChange={event => setTypedCode(event.target.value)}
                onKeyDown={event => { if (event.key === 'Enter') { event.preventDefault(); applyVoucher(typedCode); } }} />
              <button type="button" className="button primary" onClick={() => applyVoucher(typedCode)}>Áp dụng</button>
            </div>
            <h3>Voucher đề xuất</h3>
            {voucherChosen && !suggestions.some(item => item.code === selection.voucherCode) && (
              <button type="button" className="voucher-option selected" aria-pressed="true" onClick={() => applyVoucher(selection.voucherCode)}>
                <span className="voucher-stamp">MLAND</span><span><strong>{selection.voucherCode}</strong></span><span>✓</span>
              </button>
            )}
            {suggestions.length === 0 && <p className="muted">Hiện chưa có voucher nào áp dụng cho workshop này. Bạn vẫn có thể nhập mã ở trên.</p>}
            {suggestions.map(item => (
              <button type="button" key={item.code} className={`voucher-option${item.code === selection.voucherCode && voucherChosen ? ' selected' : ''}`}
                aria-pressed={item.code === selection.voucherCode && voucherChosen} onClick={() => applyVoucher(item.code)}>
                <span className="voucher-stamp">MLAND</span>
                <span><strong>{item.name}</strong><small>{item.code}{item.benefit ? ` · ${item.benefit}` : ''}{item.saved ? ' · Trong Kho Voucher' : ''}</small></span>
                <span>{item.code === selection.voucherCode && voucherChosen ? '✓' : '○'}</span>
              </button>
            ))}
          </section>
        </div>
      )}
    </section>
  );
}
