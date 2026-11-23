import React, { useMemo, useState } from 'react';
import { useFormat } from '../../../../shared/i18n/I18nProvider';
import { useText } from '../useText';

const toBig = value => (/^[0-9]+$/.test(String(value ?? '')) ? BigInt(value) : null);
/** Marketplace-style short money: 50000 -> "50k", 1000000 -> "1 triệu", otherwise the full amount. */
export function shortMoney(value, format, en = false) {
  const amount = toBig(value);
  if (amount == null) return '—';
  if (amount >= 1_000_000n && amount % 1_000_000n === 0n) return en ? `${amount / 1_000_000n}M` : `${amount / 1_000_000n} triệu`;
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

function voucherTitle(voucher, format, t) {
  const en = t('vi', 'en') === 'en';
  const discount = voucher.discount;
  if (!discount) return voucher.name || voucher.code;
  if (discount.kind === 'FIXED') return t(`Giảm ${shortMoney(discount.amount, format)}`, `${shortMoney(discount.amount, format, true)} off`);
  const percent = Number(discount.percentage);
  const label = Number.isFinite(percent) ? `${Number(percent.toFixed(2))}%` : discount.percentage;
  const cap = discount.maxDiscountAmount ? shortMoney(discount.maxDiscountAmount, format, en) : null;
  return t(`Giảm ${label}${cap ? ` · Giảm tối đa ${cap}` : ''}`, `${label} off${cap ? ` · up to ${cap}` : ''}`);
}

const expiryLabel = (value, t) => value
  ? `${t('HSD', 'Expires')}: ${new Date(value).toLocaleDateString('vi-VN', { timeZone: 'Asia/Ho_Chi_Minh', day: '2-digit', month: '2-digit', year: 'numeric' }).replace(/\//g, '.')}`
  : '';

/**
 * Shopee-like voucher sheet (FR-08-035): pick one saved voucher with the radio on the right, then press "Đồng ý".
 * Vouchers that cannot apply to this order (minimum not reached, not active, or rejected by FE-06) are dimmed at the
 * bottom with the reason; the best saving is marked.
 */
/** FE-06 Kho Voucher availability in words (2026-11-23), instead of one "cannot be used now" for every case. */
export const AVAILABILITY_EN = {
  SCHEDULED: 'Not started yet',
  PAUSED: 'This voucher is paused',
  EXPIRED: 'This voucher has expired',
  NO_PUBLIC_TARGET: 'No product is covered by this voucher any more',
  NO_USES_LEFT: 'This voucher has no uses left',
  MEMBER_LIMIT_REACHED: 'You have used up this voucher',
};
export const AVAILABILITY_VI = {
  SCHEDULED: 'Chưa đến thời gian sử dụng',
  PAUSED: 'Voucher đang tạm dừng',
  EXPIRED: 'Voucher đã hết hạn',
  NO_PUBLIC_TARGET: 'Voucher không còn sản phẩm nào được áp dụng',
  NO_USES_LEFT: 'Voucher đã hết lượt sử dụng',
  MEMBER_LIMIT_REACHED: 'Bạn đã dùng hết lượt của voucher này',
};

export function VoucherPicker({ vouchers, selectedCode, subtotal, rejectedCodes, options = null, onConfirm, onClose }) {
  const format = useFormat();
  const t = useText();
  const [draft, setDraft] = useState(selectedCode || null);
  const [typedCode, setTypedCode] = useState('');
  const [typedError, setTypedError] = useState('');
  const base = toBig(subtotal);

  const rows = useMemo(() => {
    const list = vouchers.map(voucher => {
      const minimum = toBig(voucher.minimumEligibleAmount) ?? 0n;
      let reason = null;
      if (voucher.availability && voucher.availability !== 'ACTIVE') reason = t(AVAILABILITY_VI[voucher.availability] || 'Voucher hiện không dùng được', AVAILABILITY_EN[voucher.availability] || 'This voucher cannot be used now');
      else if (base != null && base < minimum) reason = t(`Mua thêm ${format.money((minimum - base).toString())} để sử dụng`, `Spend ${format.money((minimum - base).toString())} more to use it`);
      else if (options?.has(voucher.code) ? !options.get(voucher.code).eligible : rejectedCodes?.has(voucher.code)) reason = (options?.get(voucher.code)?.reason ? t(options.get(voucher.code).reason, options.get(voucher.code).reasonEn || options.get(voucher.code).reason) : null) || t('Không áp dụng cho sản phẩm hoặc chi nhánh trong đơn này', 'Does not apply to the items or branches of this order');
      // The saving FE-06 computed for these lines; an estimate only while that answer is not back yet.
      const real = options?.get(voucher.code);
      return { ...voucher, minimum, reason, saving: reason ? null : real ? real.saving : voucherSaving(voucher, subtotal) };
    });
    const usable = list.filter(item => !item.reason).sort((a, b) => (b.saving ?? 0n) > (a.saving ?? 0n) ? 1 : (b.saving ?? 0n) < (a.saving ?? 0n) ? -1 : 0);
    const blocked = list.filter(item => item.reason);
    return { usable, blocked, bestCode: usable[0]?.saving ? usable[0].code : null };
  }, [vouchers, rejectedCodes, subtotal, options, t]); // eslint-disable-line react-hooks/exhaustive-deps

  const typedIsNew = draft && !vouchers.some(item => item.code === draft);
  const toggle = code => setDraft(current => (current === code ? null : code));
  const applyTyped = () => {
    const code = typedCode.trim().toUpperCase();
    if (!code) { setTypedError(t('Nhập mã voucher trước.', 'Enter a voucher code first.')); return; }
    // Same rule as FE-06 promotion codes: 1-128 characters after trimming, no control characters.
    if ([...code].length > 128) { setTypedError(t('Mã voucher tối đa 128 ký tự.', 'A voucher code has at most 128 characters.')); return; }
    if (/[\u0000-\u001F\u007F-\u009F]/.test(code)) { setTypedError(t('Mã voucher có ký tự không hợp lệ.', 'The voucher code has invalid characters.')); return; }
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
    {voucher.code === rows.bestCode && <span className="voucher-best">{t('Lựa chọn tốt nhất', 'Best choice')}</span>}
    <span className="voucher-ticket-stamp"><strong>MLAND</strong><small>{t('Voucher sản phẩm', 'Product voucher')}</small></span>
    <span className="voucher-ticket-body">
      <strong>{voucherTitle(voucher, format, t)}</strong>
      <span>{t('Đơn tối thiểu', 'Min. spend')} {shortMoney(voucher.minimumEligibleAmount || '0', format, t('vi', 'en') === 'en')}</span>
      {voucher.reason ? <small className="voucher-ticket-reason">{voucher.reason}</small> : <small>{[voucher.code, expiryLabel(voucher.endAt, t)].filter(Boolean).join(' · ')}</small>}
    </span>
    <span className="voucher-radio" aria-hidden="true" />
  </button>;

  return <div className="benefit-picker-backdrop" role="presentation" onClick={event => { if (event.target === event.currentTarget) onClose(); }}>
    <section className="voucher-sheet" role="dialog" aria-modal="true" aria-label={t('Chọn voucher MLAND', 'Choose an MLAND voucher')}>
      <div className="voucher-sheet-head"><button type="button" className="voucher-sheet-back" aria-label={t('Đóng', 'Close')} onClick={onClose}>←</button><h2>{t('Chọn voucher MLAND', 'Choose an MLAND voucher')}</h2></div>
      <div className="voucher-sheet-entry">
        <span>{t('Mã voucher', 'Voucher code')}</span>
        <input value={typedCode} maxLength="128" placeholder={t('Nhập mã voucher', 'Enter a voucher code')} onChange={event => { setTypedCode(event.target.value); setTypedError(''); }} onKeyDown={event => { if (event.key === 'Enter') applyTyped(); }} />
        <button type="button" className="button outline" disabled={!typedCode.trim()} onClick={applyTyped}>{t('Áp dụng', 'Apply')}</button>
      </div>
      {typedError && <p className="voucher-sheet-error">{typedError}</p>}
      <div className="voucher-sheet-list" role="radiogroup" aria-label={t('Voucher của bạn', 'Your vouchers')}>
        <h3>{t('Mã giảm giá sản phẩm', 'Product vouchers')}</h3>
        {typedIsNew && <button type="button" role="radio" aria-checked="true" className="voucher-ticket selected" onClick={() => toggle(draft)}>
          <span className="voucher-ticket-stamp"><strong>MLAND</strong><small>{t('Mã đã nhập', 'Entered code')}</small></span>
          <span className="voucher-ticket-body"><strong>{draft}</strong><small>{t('Kiểm tra điều kiện khi áp dụng', 'Conditions are checked when applied')}</small></span>
          <span className="voucher-radio" aria-hidden="true" />
        </button>}
        {rows.usable.length === 0 && rows.blocked.length === 0 && !typedIsNew && <p className="muted">{t('Bạn chưa có voucher nào. Nhập mã ở trên nếu có.', 'You have no vouchers. Enter a code above if you have one.')}</p>}
        {rows.usable.map(voucher => card(voucher, false))}
        {rows.blocked.length > 0 && <h3 className="voucher-sheet-subhead">{t('Chưa đủ điều kiện cho đơn này', 'Not eligible for this order')}</h3>}
        {rows.blocked.map(voucher => card(voucher, true))}
      </div>
      <div className="voucher-sheet-foot">
        <span>{draft ? <>{t('1 voucher đã chọn', '1 voucher selected')}{chosenSaving ? <>{t(', giảm', ', saves')} <b>{format.money(chosenSaving.toString())}</b></> : null}</> : t('Chưa chọn voucher', 'No voucher selected')}</span>
        <button type="button" className="button primary" onClick={() => onConfirm(draft)}>{t('Đồng ý', 'OK')}</button>
      </div>
    </section>
  </div>;
}
