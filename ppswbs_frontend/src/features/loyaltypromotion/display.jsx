import React from 'react';
import { useFormat, useT } from '../../shared/i18n/I18nProvider';
import { messages } from './messages';

/** "10% (tối đa 50.000 ₫)" or "50.000 ₫ (tối đa …)". */
export function DiscountText({ discount }) {
  const t = useT(messages);
  const format = useFormat();
  if (!discount) return <>—</>;
  const value = discount.kind === 'PERCENT' ? `${discount.percentage}%` : format.money(discount.amount);
  return <>{t('discount.text', { value, max: format.money(discount.maxDiscountAmount) })}</>;
}

/** Conditions shown on cards and details: window, minimum, points, scope targets, limits. */
/** Terms of a programme; `labels` ({ productIds: {id: name}, ... }) names targets where the page could read them. */
export function Terms({ item, labels }) {
  const t = useT(messages);
  const format = useFormat();
  const targets = item.targets || {};
  const rows = [
    [t('field.window'), `${format.dateTime(item.startAt)} – ${format.dateTime(item.endAt)}`],
    [t('field.minimumEligibleAmount'), format.money(item.minimumEligibleAmount)],
    [t('field.allowPoints'), item.allowPoints ? t('yes') : t('no')],
  ];
  Object.entries(targets).forEach(([key, values]) => {
    if (values?.length) rows.push([t(`target.${key}`), values.map(value => labels?.[key]?.[value] || value).join('; ')]);
  });
  if (item.totalLimit) rows.push([t('field.totalLimit'), format.number(item.totalLimit)]);
  if (item.perMemberLimit) rows.push([t('field.perMemberLimit'), format.number(item.perMemberLimit)]);
  return (
    <dl className="facts">
      {rows.map(([label, value]) => <div key={label}><dt>{label}</dt><dd>{value}</dd></div>)}
    </dl>
  );
}

export function ScopeBadge({ scope }) {
  const t = useT(messages);
  return <span className="badge">{t(`scope.${scope}`)}</span>;
}
