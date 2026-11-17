import React, { useState } from 'react';
import { useFormat } from '../../../../shared/i18n/I18nProvider';
import { VoucherPicker } from './VoucherPicker';

/**
 * FE-08 presentation for FE-06 data: a Shopee-like voucher picker and all-eligible-points switch.
 * With autoQuote the page recalculates totals by itself, so no recalculate hint or acknowledgement is shown.
 */
export function RetailBenefitPanel({ selection, onChange, savedVouchers, maxPoints, minRedeemPoints, quote, quoteMatches, acknowledged, onAcknowledge, onRecalculate, onConfirm, pending, hideActions = false, autoQuote = false, voucherSubtotal = null, rejectedCodes = null, voucherOptions = null }) {
  const format = useFormat();
  const [pickerOpen, setPickerOpen] = useState(false);
  const [typedCode, setTypedCode] = useState('');
  const selectedVoucher = savedVouchers.find(item => item.code === selection.voucherCode);
  const pointsEnabled = selection.requestedPoints !== '0';
  const validPoints = value => /^(0|[1-9][0-9]*)$/.test(String(value ?? ''));
  const limitsKnown = validPoints(maxPoints) && validPoints(minRedeemPoints);
  const belowMinimum = limitsKnown && BigInt(maxPoints) < BigInt(minRedeemPoints);
  const canUsePoints = limitsKnown && BigInt(maxPoints) > 0n && !belowMinimum;
  const applyVoucher = code => {
    const clean = code.trim();
    if (!clean) return;
    const deselect = selection.choice === 'VOUCHER' && selection.voucherCode === clean;
    onChange({ ...selection, choice: deselect ? 'NONE' : 'VOUCHER', voucherCode: deselect ? '' : clean, requestedPoints: '0' });
    setPickerOpen(false);
  };
  const togglePoints = checked => onChange({ ...selection, requestedPoints: checked ? maxPoints : '0' });
  const canConfirm = Boolean(quote?.eligible && quoteMatches && acknowledged && !pending);

  return <section className="retail-benefits" aria-label="Ưu đãi và điểm thành viên">
    <button type="button" className="retail-benefit-row" disabled={pending} onClick={() => setPickerOpen(true)}>
      <span className="retail-benefit-icon">▣</span><strong>Voucher MLAND</strong>
      <span className={selectedVoucher || selection.voucherCode ? 'benefit-applied' : 'muted'}>{autoQuote && selection.choice === 'VOUCHER' && quoteMatches && quote?.discountAmount && quote.discountAmount !== '0' ? `${selection.voucherCode} · −${format.money(quote.discountAmount)}` : selectedVoucher?.name || (selection.voucherCode ? selection.voucherCode : 'Chọn hoặc nhập mã')}</span><span>›</span>
    </button>
    <label className="retail-benefit-row point-row">
      <span className="retail-benefit-icon">Ⓟ</span><strong>Dùng {format.number(maxPoints ?? '0')} điểm thành viên</strong>
      <span className="muted">{autoQuote ? '' : !limitsKnown ? 'Tính lại ưu đãi để xem điểm tối đa' : belowMinimum ? `Cần tối thiểu ${format.number(minRedeemPoints)} điểm để sử dụng` : 'Điểm tối đa theo báo giá'}</span>
      <input className="switch" type="checkbox" checked={pointsEnabled} disabled={pending || (!canUsePoints && !pointsEnabled)} onChange={event => togglePoints(event.target.checked)} />
    </label>
    {quote && !autoQuote && <div className="retail-benefit-quote">
      {!quoteMatches && !autoQuote && <p className="notice error">Lựa chọn đã đổi. Hãy tính lại tổng tiền.</p>}
      {quote.reasons?.length > 0 && !(autoQuote && quote.eligible) && <p className={quote.eligible ? 'muted small' : 'notice error'}>{quote.reasons.map(reason => reason === 'No automatic discount applies to this cart.' ? 'Giỏ hàng hiện không có ưu đãi tự động phù hợp.' : reason).join(' ')}</p>}
      {!autoQuote && <label className="check-row"><input type="checkbox" checked={acknowledged} disabled={!quoteMatches || !quote.eligible} onChange={event => onAcknowledge(event.target.checked)} /> Tôi đã xem tổng tiền và ưu đãi.</label>}
    </div>}
    {!hideActions && <div className="retail-benefit-actions"><button type="button" className="button outline" disabled={pending} onClick={onRecalculate}>Tính lại ưu đãi</button><button type="button" className="button primary" disabled={!canConfirm} onClick={onConfirm}>Tạo đơn</button></div>}
    {pickerOpen && autoQuote && <VoucherPicker vouchers={savedVouchers.filter(item => item.availability !== 'EXPIRED')} selectedCode={selection.choice === 'VOUCHER' ? selection.voucherCode : null}
      subtotal={voucherSubtotal} rejectedCodes={rejectedCodes} options={voucherOptions} onClose={() => setPickerOpen(false)}
      onConfirm={code => { setPickerOpen(false); if ((code || '') !== (selection.choice === 'VOUCHER' ? selection.voucherCode : '')) onChange({ ...selection, choice: code ? 'VOUCHER' : 'NONE', voucherCode: code || '', requestedPoints: '0' }); }} />}
    {pickerOpen && !autoQuote && <div className="benefit-picker-backdrop" role="presentation"><section className="benefit-picker" role="dialog" aria-modal="true" aria-label="Chọn voucher">
      <div className="card-head"><h2>Chọn voucher MLAND</h2><button type="button" className="text-link" onClick={() => setPickerOpen(false)}>Đóng</button></div>
      <div className="voucher-code-entry"><input value={typedCode} maxLength="128" placeholder="Nhập mã voucher" onChange={event => setTypedCode(event.target.value)} /><button type="button" className="button primary" onClick={() => applyVoucher(typedCode)}>Áp dụng</button></div>
      <h3>Voucher đề xuất</h3>
      {selection.choice === 'VOUCHER' && !savedVouchers.some(item => item.availability === 'ACTIVE' && item.code === selection.voucherCode) && <button type="button" className="voucher-option selected" aria-pressed="true" onClick={() => applyVoucher(selection.voucherCode)}><span className="voucher-stamp">MLAND</span><span><strong>{selection.voucherCode}</strong></span><span>✓</span></button>}
      {savedVouchers.filter(item => item.availability === 'ACTIVE').length === 0 && <p className="muted">Bạn chưa có voucher khả dụng.</p>}
      {savedVouchers.filter(item => item.availability === 'ACTIVE').map(item => <button type="button" className={`voucher-option${item.code === selection.voucherCode ? ' selected' : ''}`} aria-pressed={item.code === selection.voucherCode} key={item.code} onClick={() => applyVoucher(item.code)}><span className="voucher-stamp">MLAND</span><span><strong>{item.name}</strong><small>{item.code}</small></span><span>{item.code === selection.voucherCode ? '✓' : '○'}</span></button>)}
    </section></div>}
  </section>;
}
