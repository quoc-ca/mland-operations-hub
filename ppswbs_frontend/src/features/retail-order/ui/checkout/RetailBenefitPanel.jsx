import React, { useState } from 'react';
import { useFormat } from '../../../../shared/i18n/I18nProvider';
import { VoucherPicker } from './VoucherPicker';
import { reasonsInVietnamese } from './voucherOptions';
import { useText } from '../useText';

/**
 * FE-08 presentation for FE-06 data: a Shopee-like voucher picker and all-eligible-points switch.
 * With autoQuote the page recalculates totals by itself, so no recalculate hint or acknowledgement is shown.
 */
export function RetailBenefitPanel({ selection, onChange, savedVouchers, maxPoints, minRedeemPoints, quote, quoteMatches, acknowledged, onAcknowledge, onRecalculate, onConfirm, pending, hideActions = false, autoQuote = false, voucherSubtotal = null, rejectedCodes = null, voucherOptions = null }) {
  const format = useFormat();
  const t = useText();
  const [pickerOpen, setPickerOpen] = useState(false);
  const [typedCode, setTypedCode] = useState('');
  const selectedVoucher = savedVouchers.find(item => item.code === selection.voucherCode);
  const pointsEnabled = selection.requestedPoints !== '0';
  const validPoints = value => /^(0|[1-9][0-9]*)$/.test(String(value ?? ''));
  const limitsKnown = validPoints(maxPoints) && validPoints(minRedeemPoints);
  const belowMinimum = limitsKnown && BigInt(maxPoints) < BigInt(minRedeemPoints);
  const canUsePoints = limitsKnown && BigInt(maxPoints) > 0n && !belowMinimum;
  // FE-06 says the chosen voucher forbids points (BenefitPreview.pointsAllowed): say why the switch shows 0 points.
  const pointsBlocked = quote?.pointsAllowed === false && selection.choice === 'VOUCHER';
  const applyVoucher = code => {
    const clean = code.trim();
    if (!clean) return;
    const deselect = selection.choice === 'VOUCHER' && selection.voucherCode === clean;
    onChange({ ...selection, choice: deselect ? 'NONE' : 'VOUCHER', voucherCode: deselect ? '' : clean, requestedPoints: '0' });
    setPickerOpen(false);
  };
  const togglePoints = checked => onChange({ ...selection, requestedPoints: checked ? maxPoints : '0' });
  const canConfirm = Boolean(quote?.eligible && quoteMatches && acknowledged && !pending);

  return <section className="retail-benefits" aria-label={t('Ưu đãi và điểm thành viên', 'Discounts and member points')}>
    <button type="button" className="retail-benefit-row" disabled={pending} onClick={() => setPickerOpen(true)}>
      <span className="retail-benefit-icon">▣</span><strong>Voucher MLAND</strong>
      <span className={selectedVoucher || selection.voucherCode ? 'benefit-applied' : 'muted'}>{autoQuote && selection.choice === 'VOUCHER' && quoteMatches && (quote?.voucherDiscountAmount ?? quote?.discountAmount) && (quote.voucherDiscountAmount ?? quote.discountAmount) !== '0' ? `${selection.voucherCode} · −${format.money(quote.voucherDiscountAmount ?? quote.discountAmount)}` : selectedVoucher?.name || (selection.voucherCode ? selection.voucherCode : t('Chọn hoặc nhập mã', 'Select or enter a code'))}</span><span>›</span>
    </button>
    <label className="retail-benefit-row point-row">
      <span className="retail-benefit-icon">Ⓟ</span><strong>{t(`Dùng ${format.number(maxPoints ?? '0')} điểm thành viên`, `Use ${format.number(maxPoints ?? '0')} member points`)}</strong>
      <span className="muted">{pointsBlocked ? t(`Voucher ${quote?.appliedVoucherCode || 'đã chọn'} không dùng chung với điểm. Bỏ voucher để dùng điểm.`, `Voucher ${quote?.appliedVoucherCode || ''} cannot be combined with points. Remove the voucher to use points.`) : limitsKnown && belowMinimum ? t(`Cần tối thiểu ${format.number(minRedeemPoints)} điểm để sử dụng`, `At least ${format.number(minRedeemPoints)} points are needed`) : autoQuote ? '' : !limitsKnown ? t('Tính lại ưu đãi để xem điểm tối đa', 'Recalculate to see the maximum points') : t('Điểm tối đa theo báo giá', 'Maximum points from the quote')}</span>
      <input className="switch" type="checkbox" checked={pointsEnabled} disabled={pending || (!canUsePoints && !pointsEnabled)} onChange={event => togglePoints(event.target.checked)} />
    </label>
    {quote && !autoQuote && <div className="retail-benefit-quote">
      {!quoteMatches && !autoQuote && <p className="notice error">{t('Lựa chọn đã đổi. Hãy tính lại tổng tiền.', 'Your choice changed. Recalculate the total.')}</p>}
      {quote.reasons?.length > 0 && !(autoQuote && quote.eligible) && <p className={quote.eligible ? 'muted small' : 'notice error'}>{t(reasonsInVietnamese(quote.reasons).join(' '), (quote.reasons || []).join(' '))}</p>}
      {!autoQuote && <label className="check-row"><input type="checkbox" checked={acknowledged} disabled={!quoteMatches || !quote.eligible} onChange={event => onAcknowledge(event.target.checked)} /> {t('Tôi đã xem tổng tiền và ưu đãi.', 'I have checked the total and discounts.')}</label>}
    </div>}
    {!hideActions && <div className="retail-benefit-actions"><button type="button" className="button outline" disabled={pending} onClick={onRecalculate}>{t('Tính lại ưu đãi', 'Recalculate')}</button><button type="button" className="button primary" disabled={!canConfirm} onClick={onConfirm}>{t('Tạo đơn', 'Create order')}</button></div>}
    {pickerOpen && autoQuote && <VoucherPicker vouchers={savedVouchers.filter(item => item.availability !== 'EXPIRED')} selectedCode={selection.choice === 'VOUCHER' ? selection.voucherCode : null}
      subtotal={voucherSubtotal} rejectedCodes={rejectedCodes} options={voucherOptions} onClose={() => setPickerOpen(false)}
      onConfirm={code => { setPickerOpen(false); if ((code || '') !== (selection.choice === 'VOUCHER' ? selection.voucherCode : '')) onChange({ ...selection, choice: code ? 'VOUCHER' : 'NONE', voucherCode: code || '', requestedPoints: '0' }); }} />}
    {pickerOpen && !autoQuote && <div className="benefit-picker-backdrop" role="presentation"><section className="benefit-picker" role="dialog" aria-modal="true" aria-label={t('Chọn voucher', 'Choose a voucher')}>
      <div className="card-head"><h2>{t('Chọn voucher MLAND', 'Choose an MLAND voucher')}</h2><button type="button" className="text-link" onClick={() => setPickerOpen(false)}>{t('Đóng', 'Close')}</button></div>
      <div className="voucher-code-entry"><input value={typedCode} maxLength="128" placeholder={t('Nhập mã voucher', 'Enter a voucher code')} onChange={event => setTypedCode(event.target.value)} /><button type="button" className="button primary" onClick={() => applyVoucher(typedCode)}>{t('Áp dụng', 'Apply')}</button></div>
      <h3>{t('Voucher đề xuất', 'Suggested vouchers')}</h3>
      {selection.choice === 'VOUCHER' && !savedVouchers.some(item => item.availability === 'ACTIVE' && item.code === selection.voucherCode) && <button type="button" className="voucher-option selected" aria-pressed="true" onClick={() => applyVoucher(selection.voucherCode)}><span className="voucher-stamp">MLAND</span><span><strong>{selection.voucherCode}</strong></span><span>✓</span></button>}
      {savedVouchers.filter(item => item.availability === 'ACTIVE').length === 0 && <p className="muted">{t('Bạn chưa có voucher khả dụng.', 'You have no usable voucher.')}</p>}
      {savedVouchers.filter(item => item.availability === 'ACTIVE').map(item => <button type="button" className={`voucher-option${item.code === selection.voucherCode ? ' selected' : ''}`} aria-pressed={item.code === selection.voucherCode} key={item.code} onClick={() => applyVoucher(item.code)}><span className="voucher-stamp">MLAND</span><span><strong>{item.name}</strong><small>{item.code}</small></span><span>{item.code === selection.voucherCode ? '✓' : '○'}</span></button>)}
    </section></div>}
  </section>;
}
