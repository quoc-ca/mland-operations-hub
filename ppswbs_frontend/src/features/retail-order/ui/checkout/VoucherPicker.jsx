import React, { useMemo, useState } from 'react';
import { useFormat } from '../../../../shared/i18n/I18nProvider';

const toBig = value => (/^[0-9]+$/.test(String(value ?? '')) ? BigInt(value) : null);
/** Marketplace-style short money: 50000 -> "50k", 1000000 -> "1 triệu", otherwise the full amount. */
export function shortMoney(value, format) {
  const amount = toBig(value);
  if (amount == null) return '—';
  if (amount >= 1_000_000n && amount % 1_000_000n === 0n) return `${amount / 1_000_000n} triệu`;
  if (amount >= 1_000n && amount % 1_000n === 0n) return `${amount / 1_000n}k`;
  return format.money(amount.toString());
}

/** Estimated saving of a voucher on a subtotal, or null when its terms are unknown. */
export function voucherSaving(voucher, subtotal) {
  const base = toBig(subtotal);
  const discount = voucher.discount;
  if (base == null || !discount) return null;
  if (discount.kind === 'FIXED') {
    const fixed = toBig(discount.amount);
    return fixed == null ? null : (fixed < base ? fixed : base);
  }
  const percent = Number(discount.percentage);
  if (!Number.isFinite(percent)) return null;
  const raw = (base * BigInt(Math.round(percent * 1_000_000))) / 100_000_000n;
  const cap = toBig(discount.maxDiscountAmount);
  return cap != null && raw > cap ? cap : raw;
}

function voucherTitle(voucher, format) {
  const discount = voucher.discount;
  if (!discount) return voucher.name || voucher.code;
  if (discount.kind === 'FIXED') return `Giảm ${shortMoney(discount.amount, format)}`;
  const percent = Number(discount.percentage);
  const label = Number.isFinite(percent) ? `${Number(percent.toFixed(2))}%` : discount.percentage;
  return `Giảm ${label}${discount.maxDiscountAmount ? ` · Giảm tối đa ${shortMoney(discount.maxDiscountAmount, format)}` : ''}`;
}

const expiryLabel = value => value
  ? `HSD: ${new Date(value).toLocaleDateString('vi-VN', { timeZone: 'Asia/Ho_Chi_Minh', day: '2-digit', month: '2-digit', year: 'numeric' }).replace(/\//g, '.')}`
  : '';

/**
 * Shopee-like voucher sheet (FR-08-035): pick one saved voucher with the radio on the right, then press "Đồng ý".
 * Vouchers that cannot apply to this order (minimum not reached, not active, or rejected by FE-06) are dimmed at the
 * bottom with the reason; the best saving is marked.
 */
export function VoucherPicker({ vouchers, selectedCode, subtotal, rejectedCodes, onConfirm, onClose }) {
  const format = useFormat();
  const [draft, setDraft] = useState(selectedCode || null);
  const [typedCode, setTypedCode] = useState('');
  const [typedError, setTypedError] = useState('');
  const base = toBig(subtotal);

  const rows = useMemo(() => {
    const list = vouchers.map(voucher => {
      const minimum = toBig(voucher.minimumEligibleAmount) ?? 0n;
      let reason = null;
      if (voucher.availability && voucher.availability !== 'ACTIVE') reason = voucher.availability === 'SCHEDULED' ? 'Chưa đến thời gian sử dụng' : 'Voucher hiện không dùng được';
      else if (rejectedCodes?.has(voucher.code)) reason = 'Không áp dụng cho sản phẩm hoặc chi nhánh trong đơn này';
      else if (base != null && base < minimum) reason = `Mua thêm ${format.money((minimum - base).toString())} để sử dụng`;
      return { ...voucher, minimum, reason, saving: reason ? null : voucherSaving(voucher, subtotal) };
    });
    const usable = list.filter(item => !item.reason).sort((a, b) => (b.saving ?? 0n) > (a.saving ?? 0n) ? 1 : (b.saving ?? 0n) < (a.saving ?? 0n) ? -1 : 0);
    const blocked = list.filter(item => item.reason);
    return { usable, blocked, bestCode: usable[0]?.saving ? usable[0].code : null };
  }, [vouchers, rejectedCodes, subtotal]); // eslint-disable-line react-hooks/exhaustive-deps

  const typedIsNew = draft && !vouchers.some(item => item.code === draft);
  const toggle = code => setDraft(current => (current === code ? null : code));
  const applyTyped = () => {
    const code = typedCode.trim().toUpperCase();
    if (!code) { setTypedError('Nhập mã voucher trước.'); return; }
    const known = vouchers.find(item => item.code.toUpperCase() === code);
    setTypedError('');
    setDraft(known ? known.code : code);
    setTypedCode('');
  };
  const chosen = vouchers.find(item => item.code === draft);
  const chosenSaving = chosen ? voucherSaving(chosen, subtotal) : null;

  const card = (voucher, disabled) => <button type="button" key={voucher.code} role="radio" aria-checked={draft === voucher.code}
    className={`voucher-ticket${draft === voucher.code ? ' selected' : ''}${disabled ? ' disabled' : ''}`}
    disabled={disabled} onClick={() => toggle(voucher.code)}>
    {voucher.code === rows.bestCode && <span className="voucher-best">Lựa chọn tốt nhất</span>}
    <span className="voucher-ticket-stamp"><strong>MLAND</strong><small>Voucher sản phẩm</small></span>
    <span className="voucher-ticket-body">
      <strong>{voucherTitle(voucher, format)}</strong>
      <span>Đơn tối thiểu {shortMoney(voucher.minimumEligibleAmount || '0', format)}</span>
      {voucher.reason ? <small className="voucher-ticket-reason">{voucher.reason}</small> : <small>{[voucher.code, expiryLabel(voucher.endAt)].filter(Boolean).join(' · ')}</small>}
    </span>
    <span className="voucher-radio" aria-hidden="true" />
  </button>;

  return <div className="benefit-picker-backdrop" role="presentation" onClick={event => { if (event.target === event.currentTarget) onClose(); }}>
    <section className="voucher-sheet" role="dialog" aria-modal="true" aria-label="Chọn voucher MLAND">
      <div className="voucher-sheet-head"><button type="button" className="voucher-sheet-back" aria-label="Đóng" onClick={onClose}>←</button><h2>Chọn voucher MLAND</h2></div>
      <div className="voucher-sheet-entry">
        <span>Mã voucher</span>
        <input value={typedCode} maxLength="128" placeholder="Nhập mã voucher" onChange={event => { setTypedCode(event.target.value); setTypedError(''); }} onKeyDown={event => { if (event.key === 'Enter') applyTyped(); }} />
        <button type="button" className="button outline" disabled={!typedCode.trim()} onClick={applyTyped}>Áp dụng</button>
      </div>
      {typedError && <p className="voucher-sheet-error">{typedError}</p>}
      <div className="voucher-sheet-list" role="radiogroup" aria-label="Voucher của bạn">
        <h3>Mã giảm giá sản phẩm</h3>
        {typedIsNew && <button type="button" role="radio" aria-checked="true" className="voucher-ticket selected" onClick={() => toggle(draft)}>
          <span className="voucher-ticket-stamp"><strong>MLAND</strong><small>Mã đã nhập</small></span>
          <span className="voucher-ticket-body"><strong>{draft}</strong><small>Kiểm tra điều kiện khi áp dụng</small></span>
          <span className="voucher-radio" aria-hidden="true" />
        </button>}
        {rows.usable.length === 0 && rows.blocked.length === 0 && !typedIsNew && <p className="muted">Bạn chưa có voucher nào. Nhập mã ở trên nếu có.</p>}
        {rows.usable.map(voucher => card(voucher, false))}
        {rows.blocked.length > 0 && <h3 className="voucher-sheet-subhead">Chưa đủ điều kiện cho đơn này</h3>}
        {rows.blocked.map(voucher => card(voucher, true))}
      </div>
      <div className="voucher-sheet-foot">
        <span>{draft ? <>1 voucher đã chọn{chosenSaving ? <>, giảm <b>{format.money(chosenSaving.toString())}</b></> : null}</> : 'Chưa chọn voucher'}</span>
        <button type="button" className="button primary" onClick={() => onConfirm(draft)}>Đồng ý</button>
      </div>
    </section>
  </div>;
}
