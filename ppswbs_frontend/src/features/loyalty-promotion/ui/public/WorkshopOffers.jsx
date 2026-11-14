import React from 'react';
import { Link } from 'react-router';
import { useAuth } from '../../../../shared/auth/AuthProvider';
import { SignInLink } from '../../../../shared/auth/SignInLink';
import { useFormat, useT } from '../../../../shared/i18n/I18nProvider';
import { messages } from '../messages';
import './product-offers.css';

/**
 * Offer display for 003.1's public workshop package surfaces (006 FR-045): the price of one ticket after the best
 * automatic discount, with the original struck through and no "possible discount" wording (it applies to Guest and
 * Member alike); badges for that discount, for one that needs a bigger order, and for at most two vouchers with an
 * invitation to sign in (vouchers are Member-only). Inputs come from offersForPackage (domain/workshopOffers.js);
 * this file only draws them, and draws nothing extra when there is no offer.
 */

const text = value => (value === null || value === undefined ? null : value.toString());

function useValue() {
  const format = useFormat();
  return discount => (discount?.kind === 'PERCENT' ? `${discount.percentage}%` : format.money(discount?.amount));
}

/** Price of one ticket: discounted + struck original when an automatic discount applies, else `fallback`. */
export function WorkshopOfferPrice({ offers, fallback }) {
  const t = useT(messages);
  const format = useFormat();
  if (!offers?.discounted) return fallback ?? null;
  const original = format.money(text(offers.price));
  return (
    <span className="offer-price">
      <span className="price offer-now">{format.money(text(offers.finalPrice))}</span>
      <s className="offer-was" aria-label={t('offer.originalPrice', { price: original })}>{original}</s>
    </span>
  );
}

/**
 * Badges for a card or a summary. The sign-in invitation is plain text here because a package card is often a link
 * itself; WorkshopOfferList carries the real sign-in link.
 */
export function WorkshopOfferBadges({ offers }) {
  const t = useT(messages);
  const format = useFormat();
  const value = useValue();
  const { status } = useAuth();
  if (!offers) return null;
  const badges = [];
  if (offers.auto) badges.push(<span key="auto" className="offer-badge auto">{t('offer.auto', { value: value(offers.auto.discount) })}</span>);
  offers.autoForBiggerOrder.slice(0, offers.auto ? 0 : 1).forEach(program => badges.push(
    <span key={`from-${program.id}`} className="offer-badge auto">{t('offer.autoFrom', { value: value(program.discount), minimum: format.money(program.minimumEligibleAmount) })}</span>,
  ));
  const vouchers = offers.vouchers.slice(0, 2);
  vouchers.forEach(program => badges.push(
    <span key={`v-${program.id}`} className="offer-badge voucher">{t('offer.voucher', { value: value(program.discount) })}</span>,
  ));
  if (vouchers.length && status !== 'signed-in') badges.push(<span key="sign-in" className="offer-applies">{t('wsOffer.signInHint')}</span>);
  return badges.length ? <span className="offer-badges">{badges}</span> : null;
}

/** Every offer of the package for its detail page, each voucher as a chip linking to its public page. */
export function WorkshopOfferList({ offers }) {
  const t = useT(messages);
  const format = useFormat();
  const value = useValue();
  const { status } = useAuth();
  if (!offers || (!offers.auto && !offers.autoForBiggerOrder.length && !offers.vouchers.length)) return null;
  const minimum = program => (Number(program.minimumEligibleAmount || 0) > 0 ? t('offer.voucherMinimum', { minimum: format.money(program.minimumEligibleAmount) }) : '');
  return (
    <section className="offer-list" aria-label={t('wsOffer.title')}>
      <h2 className="section-title">{t('wsOffer.title')}</h2>
      <ul>
        {offers.auto && (
          <li><span className="offer-badge auto">{t('offer.auto', { value: value(offers.auto.discount) })}</span>
            <span>{t('wsOffer.autoLine', { name: offers.auto.name, value: value(offers.auto.discount) })}</span></li>
        )}
        {offers.autoForBiggerOrder.map(program => (
          <li key={program.id}><span className="offer-badge auto">{t('offer.auto', { value: value(program.discount) })}</span>
            <span>{t('offer.autoFromLine', { name: program.name, value: value(program.discount), minimum: format.money(program.minimumEligibleAmount) })}</span></li>
        ))}
        {offers.vouchers.map(program => (
          <li key={program.id}><span className="offer-badge voucher">{t('offer.voucher', { value: value(program.discount) })}</span>
            <span>{t('wsOffer.voucherLine', { name: program.name, value: value(program.discount), minimum: minimum(program) })}</span>
            <Link className="text-link" to={`/promotions/${encodeURIComponent(program.id)}`}>{t('offer.view')}</Link></li>
        ))}
      </ul>
      {offers.vouchers.length > 0 && status !== 'signed-in' && (
        <p className="small"><SignInLink className="text-link">{t('wsOffer.signIn')}</SignInLink></p>
      )}
      <p className="muted small">{t('wsOffer.notice')}</p>
    </section>
  );
}
