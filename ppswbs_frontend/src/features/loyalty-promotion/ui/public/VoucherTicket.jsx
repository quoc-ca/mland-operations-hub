import React, { useMemo } from 'react';
import { Link } from 'react-router';
import { useFormat, useLanguage, useT } from '../../../../shared/i18n/I18nProvider';
import { DiscountText } from '../display';
import { messages } from '../messages';
import './voucher-wallet.css';

// The wallet's states that mean the voucher cannot be used any more by this Member (US3.5, Session 2026-11-10).
const SOLD_OUT = { NO_USES_LEFT: 'ticket.soldOut', MEMBER_LIMIT_REACHED: 'ticket.memberLimit' };

function useShortDate() {
  const { language } = useLanguage();
  return useMemo(() => {
    const format = new Intl.DateTimeFormat(language === 'en' ? 'en-GB' : 'vi-VN', { day: '2-digit', month: '2-digit', year: 'numeric' });
    return value => {
      const at = new Date(value);
      return Number.isNaN(at.getTime()) ? String(value ?? '—') : format.format(at);
    };
  }, [language]);
}

/**
 * One voucher as a ticket (FR-011): scope stub, discount and cap, minimum, branches, expiry, points, code, and a
 * "Điều kiện" link to the detail with every target. A voucher with no use left in total (public `availability`) or
 * for this Member (`availability` from the wallet) is greyed out as "Đã hết mã"; `action` is the save button.
 */
export function VoucherTicket({ promotion, availability, labels, action, showConditions = true }) {
  const t = useT(messages);
  const format = useFormat();
  const shortDate = useShortDate();
  // The Member's own state from the wallet first (it also knows MEMBER_LIMIT_REACHED); otherwise the public card's
  // NO_USES_LEFT (Session 2026-11-14), which every visitor sees.
  const soldOut = SOLD_OUT[availability] || SOLD_OUT[promotion.availability === 'NO_USES_LEFT' ? 'NO_USES_LEFT' : undefined];
  const branchIds = promotion.targets?.branchIds || [];
  const branches = branchIds.map(id => labels?.branchIds?.[id] || t('targetUnknown.branchIds', { id })).join('; ');

  return (
    <article className={`voucher-ticket${soldOut ? ' is-out' : ''}`}>
      <div className="voucher-ticket-stub">
        <span className="voucher-ticket-brand">MLAND</span>
        <span className="voucher-ticket-scope">{t(`scope.${promotion.scope}`)}</span>
      </div>
      <div className="voucher-ticket-body">
        <p className="voucher-ticket-discount"><DiscountText discount={promotion.discount} /></p>
        <p className="voucher-ticket-line">{t('ticket.min', { value: format.money(promotion.minimumEligibleAmount) })}</p>
        {branches && <p className="voucher-ticket-line">{t('offer.atBranches', { branches })}</p>}
        <p className="voucher-ticket-name">{promotion.name}</p>
        <div className="voucher-ticket-meta">
          {promotion.code && <span className="voucher-ticket-code">{promotion.code}</span>}
          <span>{t('ticket.expires', { date: shortDate(promotion.endAt) })}</span>
          <span>{promotion.allowPoints ? t('ticket.points') : t('ticket.noPoints')}</span>
        </div>
        <div className="voucher-ticket-actions">
          {soldOut ? <span className="voucher-ticket-out" role="status">{t(soldOut)}</span> : action}
          {showConditions && <Link className="text-link voucher-ticket-conditions" to={`/promotions/${encodeURIComponent(promotion.id)}`}>{t('ticket.conditions')}</Link>}
        </div>
      </div>
      {soldOut && <span className="voucher-ticket-stamp" aria-hidden="true">{t(soldOut)}</span>}
    </article>
  );
}
