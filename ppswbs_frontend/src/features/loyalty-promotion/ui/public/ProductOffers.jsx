import React from 'react';
import { Link } from 'react-router';
import { useAuth } from '../../../../shared/auth/AuthProvider';
import { SignInLink } from '../../../../shared/auth/SignInLink';
import { useFormat, useT } from '../../../../shared/i18n/I18nProvider';
import { messages } from '../messages';
import { needsSaving } from '../../domain/productOffers';
import { useLoyaltyApi } from '../../application/useLoyaltyApi';
import { usePublicBranchNames } from '../../application/useTargetLabels';
import './product-offers.css';

/**
 * Offer display for 002's public product card and detail page (TikTok-Shop-like): the price after the best
 * automatic discount in red with the original price struck through, and small badges for automatic discounts and
 * vouchers. Inputs come from offersForProduct (domain/productOffers.js); this file only draws them.
 */

function useValue() {
  const format = useFormat();
  return discount => (discount?.kind === 'PERCENT' ? `${discount.percentage}%` : format.money(discount?.amount));
}

const text = value => (value === null || value === undefined ? null : value.toString());

/** "Giá sau voucher MLHK12" or, for a voucher limited to branches, "… · tại Hoàn Kiếm" (Session 2026-11-23). */
function AfterVoucherTag({ voucher }) {
  const t = useT(messages);
  const api = useLoyaltyApi();
  const ids = (voucher.targets?.branchIds || []).map(String);
  const names = usePublicBranchNames(api, ids);
  const code = voucher.code || voucher.name;
  const known = ids.map(id => names[id]).filter(Boolean);
  const text = !ids.length ? t('wsOffer.afterVoucher', { code })
    : known.length === ids.length ? t('offer.afterVoucherAt', { code, branches: known.join(', ') })
      : t('offer.afterVoucherAtCount', { code, count: ids.length });
  return <span className="offer-voucher-tag" title={voucher.name}>{text}</span>;
}

function Range({ range, className }) {
  const format = useFormat();
  if (!range) return <span className={className}>—</span>;
  const low = format.money(text(range.minimum));
  const high = format.money(text(range.maximum));
  return <span className={className}>{low === high ? low : `${low} – ${high}`}</span>;
}

/**
 * Price of a product: the range after the automatic discount and the best voucher (FR-048) with the original struck
 * through and a "Giá sau voucher {mã}" tag when a voucher is counted; else the normal range.
 */
export function OfferPrice({ offers, fallback }) {
  const t = useT(messages);
  const format = useFormat();
  if (!offers?.discounted) return fallback;
  const original = offers.original.minimum === offers.original.maximum
    ? format.money(text(offers.original.minimum))
    : `${format.money(text(offers.original.minimum))} – ${format.money(text(offers.original.maximum))}`;
  const voucher = offers.bestVoucher;
  return (
    <span className="offer-price">
      <Range range={voucher ? offers.afterVoucher : offers.final} className="price offer-now" />
      <s className="offer-was" aria-label={t('offer.originalPrice', { price: original })}>{original}</s>
      {voucher && <AfterVoucherTag voucher={voucher} />}
    </span>
  );
}

/** " · size 18" / " · size 9, 18" / " · 3 size" when a programme covers only part of the sizes, else "". */
function useSizesSuffix() {
  const t = useT(messages);
  return coverage => {
    if (!coverage || coverage.all || !coverage.sizes.length) return '';
    return coverage.sizes.length <= 2 ? t('offer.sizesShort', { sizes: coverage.sizes.join(', ') }) : t('offer.sizesCount', { count: coverage.sizes.length });
  };
}

/** Badges: the automatic discount taken off, automatic discounts for a bigger order, and vouchers (best two). */
export function OfferBadges({ offers }) {
  const t = useT(messages);
  const format = useFormat();
  const value = useValue();
  const suffix = useSizesSuffix();
  const { status } = useAuth();
  if (!offers) return null;
  const of = program => suffix(offers.coverage?.[program.id]);
  const badges = [];
  if (offers.auto) badges.push(<span key="auto" className="offer-badge auto">{t('offer.auto', { value: value(offers.auto.discount) })}{of(offers.auto)}</span>);
  offers.autoForBiggerOrder.slice(0, offers.auto ? 0 : 1).forEach(program => badges.push(
    <span key={`from-${program.id}`} className="offer-badge auto">{t('offer.autoFrom', { value: value(program.discount), minimum: format.money(program.minimumEligibleAmount) })}{of(program)}</span>,
  ));
  offers.vouchers.slice(0, 2).forEach(program => badges.push(
    <span key={`v-${program.id}`} className="offer-badge voucher">{t(needsSaving(program) ? 'offer.claimVoucher' : 'offer.voucher', { value: value(program.discount) })}{of(program)}</span>,
  ));
  // FR-048: vouchers are Member-only, so a Guest is invited to sign in (plain text: the card itself is a link).
  if (offers.vouchers.length && status !== 'signed-in') badges.push(<span key="sign-in" className="offer-applies">{t('wsOffer.signInHint')}</span>);
  return badges.length ? <span className="offer-badges">{badges}</span> : null;
}

/** "Áp dụng cho size 18, 20." under an offer line when it covers only part of the sizes. */
function AppliesTo({ coverage }) {
  const t = useT(messages);
  if (!coverage || coverage.all || !coverage.sizes.length) return null;
  return <span className="offer-applies">{t('offer.appliesTo', { sizes: coverage.sizes.join(', ') })}</span>;
}

/** Price of one size on the detail page. */
export function SizeOfferPrice({ size, fallback }) {
  const t = useT(messages);
  const format = useFormat();
  if (!size || (size.discount <= 0n && !size.voucher)) return fallback;
  return (
    <span className="offer-price">
      <span className="offer-now">{format.money(text(size.voucher ? size.priceAfterVoucher : size.finalPrice))}</span>
      <s className="offer-was">{format.money(text(size.price))}</s>
      {size.voucher && <AfterVoucherTag voucher={size.voucher} />}
    </span>
  );
}

/** "Áp dụng tại: Hoàn Kiếm, Cầu Giấy." under a voucher limited to branches (Session 2026-11-09). */
function AtBranches({ program, names }) {
  const t = useT(messages);
  const ids = program.targets?.branchIds || [];
  if (!ids.length) return null;
  const known = ids.map(id => names[String(id)]).filter(Boolean);
  return (
    <span className="offer-applies">
      {known.length === ids.length ? t('offer.atBranches', { branches: known.join(', ') }) : t('offer.atBranchCount', { count: ids.length })}
    </span>
  );
}

/** Every offer of the product, with a link to each programme's public page (where a voucher can be saved). */
export function ProductOfferList({ offers }) {
  const t = useT(messages);
  const format = useFormat();
  const value = useValue();
  const api = useLoyaltyApi();
  const { status } = useAuth();
  const names = usePublicBranchNames(api, (offers?.vouchers || []).flatMap(program => program.targets?.branchIds || []));
  if (!offers || (!offers.auto && !offers.autoForBiggerOrder.length && !offers.vouchers.length)) return null;
  const link = program => <Link className="text-link" to={`/promotions/${encodeURIComponent(program.id)}`}>{t(needsSaving(program) ? 'offer.view' : 'offer.viewTerms')}</Link>;
  const minimum = program => (Number(program.minimumEligibleAmount || 0) > 0 ? t('offer.voucherMinimum', { minimum: format.money(program.minimumEligibleAmount) }) : '');
  return (
    <section className="offer-list" aria-label={t('offer.title')}>
      <h2 className="section-title">{t('offer.title')}</h2>
      <ul>
        {offers.auto && (
          <li><span className="offer-badge auto">{t('offer.auto', { value: value(offers.auto.discount) })}</span>
            <span>{t('offer.autoLine', { name: offers.auto.name, value: value(offers.auto.discount) })}</span>
            <AppliesTo coverage={offers.coverage?.[offers.auto.id]} /></li>
        )}
        {offers.autoForBiggerOrder.map(program => (
          <li key={program.id}><span className="offer-badge auto">{t('offer.auto', { value: value(program.discount) })}</span>
            <span>{t('offer.autoFromLine', { name: program.name, value: value(program.discount), minimum: format.money(program.minimumEligibleAmount) })}</span>
            <AppliesTo coverage={offers.coverage?.[program.id]} /></li>
        ))}
        {offers.vouchers.map(program => (
          <li key={program.id}><span className="offer-badge voucher">{t(needsSaving(program) ? 'offer.claimVoucher' : 'offer.voucher', { value: value(program.discount) })}</span>
            <span>{t('offer.voucherLine', { name: program.name, code: program.code, value: value(program.discount), minimum: minimum(program) })}</span>
            <AppliesTo coverage={offers.coverage?.[program.id]} />
            <AtBranches program={program} names={names} />
            {link(program)}</li>
        ))}
      </ul>
      {offers.vouchers.length > 0 && status !== 'signed-in' && <p className="small"><SignInLink className="text-link">{t('wsOffer.signIn')}</SignInLink></p>}
      <p className="muted small">{t('offer.notice')}</p>
    </section>
  );
}
