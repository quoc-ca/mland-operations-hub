import React from 'react';
import { Link, useLocation } from 'react-router';
import { usePendingPayments } from './pendingPayments';
import { useT } from '../shared/i18n/I18nProvider';
import { messages } from './messages';

const clock = seconds => `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, '0')}`;

/**
 * Header notice of 003.1 FR-058: a Member with a booking group that waits for payment inside its deadline sees how many
 * there are, a countdown to the nearest deadline, and one link back to that group's payment screen. Navigation only.
 */
export function PendingPaymentNotice({ enabled, userKey, getIdToken }) {
  const t = useT(messages);
  const location = useLocation();
  const { count, nearest, secondsLeft } = usePendingPayments({ enabled, userKey, getIdToken });
  if (!enabled || count === 0 || !nearest) return null;
  const target = `/workshop-booking/${encodeURIComponent(nearest)}/payment-return?resume=1`;
  if (location.pathname === `/workshop-booking/${encodeURIComponent(nearest)}/payment-return` || location.pathname === `/workshop-booking/${nearest}/payment-return`) return null;
  return (
    <div className="pending-payment-bar"><Link className="pending-payment-notice" to={target}
      aria-label={t('pending.aria', { count, time: clock(secondsLeft) })}>
      <span aria-hidden="true">⏳</span>
      <span className="pending-payment-text">{t('pending.text', { count })}</span>
      <strong className="pending-payment-time">{clock(secondsLeft)}</strong>
    </Link></div>
  );
}
