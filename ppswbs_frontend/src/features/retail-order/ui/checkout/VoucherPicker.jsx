import React, { useMemo, useState } from 'react';
import { useFormat, useLanguage, useT } from '../../../../shared/i18n/I18nProvider';
import { messages as retailMessages } from '../../messages';
import { sharedMessages } from '../../../../shared/ui/messages';
import { reasonText } from './voucherOptions';

const toBig = value => (/^[0-9]+$/.test(String(value ?? '')) ? BigInt(value) : null);
/** Marketplace-style short money: 50000 -> "50k", 1000000 -> "1 triệu" ("1M" in English), otherwise the full amount. */
export function shortMoney(value, format, t) {
  const amount = toBig(value);
  if (amount == null) return '—';
  if (amount >= 1_000_000n && amount % 1_000_000n === 0n) return t('voucher.millions', { count: String(amount / 1_000_000n) });
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
  const discount = voucher.discount;
  if (!discount) return voucher.name || voucher.code;
  if (discount.kind === 'FIXED') return t('voucher.fixedOff', { amount: shortMoney(discount.amount, format, t) });
  const percent = Number(discount.percentage);
  const label = Number.isFinite(percent) ? `${Number(percent.toFixed(2))}%` : discount.percentage;
  const cap = discount.maxDiscountAmount ? shortMoney(discount.maxDiscountAmount, format, t) : null;
  return cap ? t('voucher.percentOffCapped', { label, cap }) : t('voucher.percentOff', { label });
}

const EXPIRY_LOCALES = { vi: 'vi-VN', en: 'en-GB' };
const expiryLabel = (value, t, language) => value
  ? `${t('voucher.expires')}: ${new Date(value).toLocaleDateString(EXPIRY_LOCALES[language] || EXPIRY_LOCALES.vi, { timeZone: 'Asia/Ho_Chi_Minh', day: '2-digit', month: '2-digit', year: 'numeric' }).replace(/\//g, '.')}`
  : '';

/**
 * Shopee-like voucher sheet (FR-08-035): pick one saved voucher with the radio on the right, then press "Đồng ý".
 * Vouchers that cannot apply to this order (minimum not reached, not active, or rejected by FE-06) are dimmed at the
 * bottom with the reason; the best saving is marked.
 */
/** FE-06 Kho Voucher availability in words (2026-11-23), instead of one "cannot be used now" for every case. */
const AVAILABILITY_CODES = ['SCHEDULED', 'PAUSED', 'EXPIRED', 'NO_PUBLIC_TARGET', 'NO_USES_LEFT', 'MEMBER_LIMIT_REACHED'];

export function VoucherPicker({ vouchers, selectedCode, subtotal, rejectedCodes, options = null, onConfirm, onClose }) {
  const format = useFormat();
  const t = useT(retailMessages);
  const ts = useT(sharedMessages);
  const { language } = useLanguage();
  const [draft, setDraft] = useState(selectedCode || null);
  const [typedCode, setTypedCode] = useState('');
  const [typedError, setTypedError] = useState('');
  const base = toBig(subtotal);

  const rows = useMemo(() => {
    const list = vouchers.map(voucher => {
      const minimum = toBig(voucher.minimumEligibleAmount) ?? 0n;
      let reason = null;
      if (voucher.availability && voucher.availability !== 'ACTIVE') reason = AVAILABILITY_CODES.includes(voucher.availability) ? t(`voucher.availability.${voucher.availability}`) : t('voucher.unavailable');
      else if (base != null && base < minimum) reason = t('voucher.spendMoreToUseIt', { amount: format.money((minimum - base).toString()) });
      else if (options?.has(voucher.code) ? !options.get(voucher.code).eligible : rejectedCodes?.has(voucher.code)) reason = (options?.get(voucher.code)?.reason ? reasonText(options.get(voucher.code).reason, t) : null) || t('voucher.doesNotApplyToThe');
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
    if (!code) { setTypedError(t('voucher.enterAVoucherCodeFirst')); return; }
    // Same rule as FE-06 promotion codes: 1-128 characters after trimming, no control characters.
    if ([...code].length > 128) { setTypedError(t('voucher.aVoucherCodeHasAt')); return; }
    if (/[\u0000-\u001F\u007F-\u009F]/.test(code)) { setTypedError(t('voucher.theVoucherCodeHasInvalid')); return; }
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
    {voucher.code === rows.bestCode && <span className="voucher-best">{t('voucher.bestChoice')}</span>}
    <span className="voucher-ticket-stamp"><strong>MLAND</strong><small>{t('voucher.productVoucher')}</small></span>
    <span className="voucher-ticket-body">
      <strong>{voucherTitle(voucher, format, t)}</strong>
      <span>{t('voucher.minSpend')} {shortMoney(voucher.minimumEligibleAmount || '0', format, t)}</span>
      {voucher.reason ? <small className="voucher-ticket-reason">{voucher.reason}</small> : <small>{[voucher.code, expiryLabel(voucher.endAt, t, language)].filter(Boolean).join(' · ')}</small>}
    </span>
    <span className="voucher-radio" aria-hidden="true" />
  </button>;

  return <div className="benefit-picker-backdrop" role="presentation" onClick={event => { if (event.target === event.currentTarget) onClose(); }}>
    <section className="voucher-sheet" role="dialog" aria-modal="true" aria-label={t('common.chooseAnMlandVoucher')}>
      <div className="voucher-sheet-head"><button type="button" className="voucher-sheet-back" aria-label={ts('close')} onClick={onClose}>←</button><h2>{t('common.chooseAnMlandVoucher')}</h2></div>
      <div className="voucher-sheet-entry">
        <span>{t('voucher.voucherCode')}</span>
        <input value={typedCode} maxLength="128" placeholder={t('common.enterAVoucherCode')} onChange={event => { setTypedCode(event.target.value); setTypedError(''); }} onKeyDown={event => { if (event.key === 'Enter') applyTyped(); }} />
        <button type="button" className="button outline" disabled={!typedCode.trim()} onClick={applyTyped}>{t('common.apply')}</button>
      </div>
      {typedError && <p className="voucher-sheet-error">{typedError}</p>}
      <div className="voucher-sheet-list" role="radiogroup" aria-label={t('voucher.yourVouchers')}>
        <h3>{t('voucher.productVouchers')}</h3>
        {typedIsNew && <button type="button" role="radio" aria-checked="true" className="voucher-ticket selected" onClick={() => toggle(draft)}>
          <span className="voucher-ticket-stamp"><strong>MLAND</strong><small>{t('voucher.enteredCode')}</small></span>
          <span className="voucher-ticket-body"><strong>{draft}</strong><small>{t('voucher.conditionsAreCheckedWhenApplied')}</small></span>
          <span className="voucher-radio" aria-hidden="true" />
        </button>}
        {rows.usable.length === 0 && rows.blocked.length === 0 && !typedIsNew && <p className="muted">{t('voucher.youHaveNoVouchersEnter')}</p>}
        {rows.usable.map(voucher => card(voucher, false))}
        {rows.blocked.length > 0 && <h3 className="voucher-sheet-subhead">{t('voucher.notEligibleForThisOrder')}</h3>}
        {rows.blocked.map(voucher => card(voucher, true))}
      </div>
      <div className="voucher-sheet-foot">
        <span>{draft ? <>{t('voucher.1VoucherSelected')}{chosenSaving ? <>{t('voucher.saves')} <b>{format.money(chosenSaving.toString())}</b></> : null}</> : t('voucher.noVoucherSelected')}</span>
        <button type="button" className="button primary" onClick={() => onConfirm(draft)}>{t('voucher.ok')}</button>
      </div>
    </section>
  </div>;
}
