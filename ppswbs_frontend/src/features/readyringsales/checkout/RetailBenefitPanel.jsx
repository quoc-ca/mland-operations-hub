import React, { useState } from 'react';
import { useFormat } from '../../../shared/i18n/I18nProvider';

/** FE-08 presentation for FE-06 data: a Shopee-like voucher picker and all-eligible-points switch. */
export function RetailBenefitPanel({ selection, onChange, savedVouchers, maxPoints, quote, quoteMatches, acknowledged, onAcknowledge, onRecalculate, onConfirm, pending }) {
  const format = useFormat();
  const [pickerOpen, setPickerOpen] = useState(false);
  const [typedCode, setTypedCode] = useState('');
  const selectedVoucher = savedVouchers.find(item => item.code === selection.voucherCode);
  const pointsEnabled = selection.requestedPoints !== '0';
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
    <button type="button" className="retail-benefit-row" onClick={() => setPickerOpen(true)}>
      <span className="retail-benefit-icon">▣</span><strong>Voucher MLAND</strong>
      <span className={selectedVoucher ? 'benefit-applied' : 'muted'}>{selectedVoucher?.name || (selection.voucherCode ? selection.voucherCode : 'Chọn hoặc nhập mã')}</span><span>›</span>
    </button>
    <label className="retail-benefit-row point-row">
      <span className="retail-benefit-icon">Ⓟ</span><strong>Dùng {format.number(maxPoints ?? '0')} điểm thành viên</strong>
      <span className="muted">{maxPoints == null ? 'Tính lại ưu đãi để xem điểm tối đa' : 'Điểm tối đa theo báo giá'}</span>
      <input className="switch" type="checkbox" checked={pointsEnabled} disabled={pending || ((maxPoints == null || maxPoints === '0') && !pointsEnabled)} onChange={event => togglePoints(event.target.checked)} />
    </label>
    {quote && <div className="retail-benefit-quote">
      {!quoteMatches && <p className="notice error">Lựa chọn đã đổi. Hãy tính lại tổng tiền.</p>}
      {quote.reasons?.length > 0 && <p className="notice error">{quote.reasons.join(' ')}</p>}
      <label className="check-row"><input type="checkbox" checked={acknowledged} disabled={!quoteMatches || !quote.eligible} onChange={event => onAcknowledge(event.target.checked)} /> Tôi đã xem tổng tiền và ưu đãi.</label>
    </div>}
    <div className="retail-benefit-actions"><button type="button" className="button outline" disabled={pending} onClick={onRecalculate}>Tính lại ưu đãi</button><button type="button" className="button primary" disabled={!canConfirm} onClick={onConfirm}>Tạo đơn</button></div>
    {pickerOpen && <div className="benefit-picker-backdrop" role="presentation"><section className="benefit-picker" role="dialog" aria-modal="true" aria-label="Chọn voucher">
      <div className="card-head"><h2>Chọn voucher MLAND</h2><button type="button" className="text-link" onClick={() => setPickerOpen(false)}>Đóng</button></div>
      <div className="voucher-code-entry"><input value={typedCode} maxLength="128" placeholder="Nhập mã voucher" onChange={event => setTypedCode(event.target.value)} /><button type="button" className="button primary" onClick={() => applyVoucher(typedCode)}>Áp dụng</button></div>
      <h3>Voucher đề xuất</h3>
      {selection.choice === 'VOUCHER' && !savedVouchers.some(item => item.availability === 'ACTIVE' && item.code === selection.voucherCode) && <button type="button" className="voucher-option selected" aria-pressed="true" onClick={() => applyVoucher(selection.voucherCode)}><span className="voucher-stamp">MLAND</span><span><strong>{selection.voucherCode}</strong></span><span>✓</span></button>}
      {savedVouchers.filter(item => item.availability === 'ACTIVE').length === 0 && <p className="muted">Bạn chưa có voucher khả dụng.</p>}
      {savedVouchers.filter(item => item.availability === 'ACTIVE').map(item => <button type="button" className={`voucher-option${item.code === selection.voucherCode ? ' selected' : ''}`} aria-pressed={item.code === selection.voucherCode} key={item.code} onClick={() => applyVoucher(item.code)}><span className="voucher-stamp">MLAND</span><span><strong>{item.name}</strong><small>{item.code}</small></span><span>{item.code === selection.voucherCode ? '✓' : '○'}</span></button>)}
    </section></div>}
  </section>;
}
