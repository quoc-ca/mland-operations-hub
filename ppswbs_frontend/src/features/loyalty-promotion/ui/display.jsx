import React, { useMemo } from 'react';
import { useFormat, useLanguage, useT } from '../../../shared/i18n/I18nProvider';
import { messages } from './messages';

/**
 * Date before time, "3 thg 11, 2026, 12:45" (en "Nov 3, 2026, 12:45 PM"), as in frames 006-10…17. The shared
 * formatter puts the time first in Vietnamese; it stays as it is for the other features.
 */
export function useDateTime() {
  const { language } = useLanguage();
  return useMemo(() => {
    const locale = language === 'en' ? 'en-US' : 'vi-VN';
    const date = new Intl.DateTimeFormat(locale, { dateStyle: 'medium' });
    const time = new Intl.DateTimeFormat(locale, { timeStyle: 'short' });
    return value => {
      if (!value) return '—';
      const at = new Date(value);
      return Number.isNaN(at.getTime()) ? String(value) : `${date.format(at)}, ${time.format(at)}`;
    };
  }, [language]);
}

/** "10% (tối đa 50.000 ₫)" or "50.000 ₫ (tối đa …)". */
export function DiscountText({ discount }) {
  const t = useT(messages);
  const format = useFormat();
  if (!discount) return <>—</>;
  const value = discount.kind === 'PERCENT' ? `${discount.percentage}%` : format.money(discount.amount);
  return <>{t('discount.text', { value, max: format.money(discount.maxDiscountAmount) })}</>;
}

/** Workshop targets shown by name on cards and details (the create form still asks for their IDs). */
const NAMED_TARGETS = ['packageIds', 'branchIds'];

/** Conditions shown on cards and details: window, minimum, points, scope targets, limits. */
/** Terms of a programme; `labels` ({ productIds: {id: name}, ... }) names targets where the page could read them. */
export function Terms({ item, labels }) {
  const t = useT(messages);
  const format = useFormat();
  const dateTime = useDateTime();
  const targets = item.targets || {};
  const rows = [
    [t('field.window'), `${dateTime(item.startAt)} – ${dateTime(item.endAt)}`],
    [t('field.minimumEligibleAmount'), format.money(item.minimumEligibleAmount)],
    [t('field.allowPoints'), item.allowPoints ? t('yes') : t('no')],
  ];
  Object.entries(targets).forEach(([key, values]) => {
    if (!values?.length) return;
    // Packages and branches show their names; one that cannot be read (draft package, inactive branch, read error)
    // shows "Gói #2" / "Chi nhánh #1" rather than a bare number.
    const named = NAMED_TARGETS.includes(key);
    const text = value => labels?.[key]?.[value] || (named ? t(`targetUnknown.${key}`, { id: value }) : value);
    rows.push([t(named ? `targetName.${key}` : `target.${key}`), values.map(text).join('; '), 'wide']);
  });
  if (item.totalLimit) rows.push([t('field.totalLimit'), format.number(item.totalLimit)]);
  if (item.perMemberLimit) rows.push([t('field.perMemberLimit'), format.number(item.perMemberLimit)]);
  return (
    <dl className="facts terms">
      {rows.map(([label, value, wide]) => <div key={label} className={wide}><dt>{label}</dt><dd>{value}</dd></div>)}
    </dl>
  );
}

export function ScopeBadge({ scope }) {
  const t = useT(messages);
  return <span className="badge">{t(`scope.${scope}`)}</span>;
}
