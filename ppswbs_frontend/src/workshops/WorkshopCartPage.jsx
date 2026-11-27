import React, { useEffect, useMemo, useRef, useState } from 'react';
import { Link } from 'react-router';
import CartTabs from '../shared/ui/CartTabs';
import { createBookingGroup, listWorkshopLocations, quoteWorkshopCart } from '../api/workshops';
import { CartBenefitPicker, usableOffers } from './CartBenefitPicker';
import { contactForRequest, EMPTY_CONTACT, splitPhone, validateBookingContact } from './contactValidation';
import { CartQuotePanel } from './CartQuotePanel';
import { CartQuoteDialog } from './CartQuoteDialog';
import { GroupDetailCard } from './GroupDetailCard';
import { useGroupCardIdentity } from './useGroupCardIdentity';
import { refreshBookingGroup } from './refreshGroup';
import { GroupAutoRefresh } from './GroupAutoRefresh';
import { invalidatePendingPayments } from './pendingPayments';
import {
  cartQuoteLines,
  clearCart,
  readCart,
  removeCartLine,
  subscribeToCart,
  updateCartLineParticipants,
} from './cartStorage';
import { failingLinesOf } from './groupDisplay';
import { GroupResultHeading } from './GroupResultHeading';
import { useBookingIdentity } from './useBookingIdentity';
import { useHttp } from '../shared/api/useHttp';
import { useFormat, useLanguage, useT } from '../shared/i18n/I18nProvider';
import { messages } from './messages';
import { offersForPackage, usePublicWorkshopPrograms, WorkshopOfferBadges, WorkshopOfferPrice } from '../features/loyalty-promotion/publicOffers';
import './figmaPublicShell.css';
import './cartShopee.css';

const CART_ERROR_CODES = new Set(["PACKAGE_NOT_AVAILABLE_AT_LOCATION","CART_HOLD_REJECTED","INSUFFICIENT_CAPACITY","QUOTE_EXPIRED","DEPENDENCY_UNAVAILABLE","CART_BENEFITS_NEED_A_MEMBER"]);

// The message is a dictionary key (translated when shown) unless the server sent its own text.
const describe = error => {
  const known = CART_ERROR_CODES.has(error?.code);
  const text = known ? null : (error?.detail || error?.message || null);
  return {
    code: error?.code || 'CLIENT_ERROR',
    messageKey: known ? `cart.err.${error.code}` : (text ? null : 'cart.err.default'),
    message: text,
    correlationId: error?.correlationId || null,
  };
};

/**
 * The workshop cart (003.1 FR-030, FR-031, FR-045). The unquoted cart lives in this browser; this page quotes it with
 * `POST /workshop-cart-quotes` and confirms it with `POST /workshop-booking-groups` — the same two calls as "Đặt ngay".
 * A capacity failure names the failing lines, holds nothing and leaves the cart editable.
 */
export function WorkshopCartPage() {
  const t = useT(messages);
  const { language } = useLanguage();
  const format = useFormat();
  const vnd = value => format.money(String(Math.round(Number(value || 0))));
  const { authStatus, mode: bookingMode, profile, profileLoading, profileError, memberAuthorization } = useBookingIdentity();
  const http = useHttp();
  const [lines, setLines] = useState(() => readCart());
  // The stored cart belongs to the signed-in account, and Firebase restores the session after the first render (a reload):
  // follow the storage, or the page would keep the empty guest cart while the header shows the account's lines.
  useEffect(() => subscribeToCart(next => setLines(next)), []);
  // Shopee-style line selection: only ticked lines are quoted and booked; the rest stay in the cart.
  // Nothing is ticked when the cart opens: the customer picks the lines to book.
  const [selectedIds, setSelectedIds] = useState([]);
  const selectedLines = lines.filter(line => selectedIds.includes(line.lineId));
  const allSelected = lines.length > 0 && selectedLines.length === lines.length;
  const [preview, setPreview] = useState(null);
  const [autoPicking, setAutoPicking] = useState(false);
  const previewRun = useRef(0);
  const userPicked = useRef(false);
  const [benefitSelection, setBenefitSelection] = useState({ choice: 'NONE', voucherCode: '', requestedPoints: '0' });
  const [contact, setContact] = useState(EMPTY_CONTACT);
  const cardIdentity = useGroupCardIdentity({ mode: bookingMode, memberAuthorization, contact });
  const [quote, setQuote] = useState(null);
  const [quoting, setQuoting] = useState(false);
  const [quoteError, setQuoteError] = useState(null);
  const [confirming, setConfirming] = useState(false);
  const [confirmError, setConfirmError] = useState(null);
  const [failingLines, setFailingLines] = useState([]);
  const [failingCode, setFailingCode] = useState(null);
  // FR-057: the cart stores only the branch id; the display name is re-read from the branch list of each Package.
  const [locationNames, setLocationNames] = useState({});
  const packageIdsKey = [...new Set(lines.map(line => line.packageId))].join(',');
  useEffect(() => {
    let active = true;
    packageIdsKey.split(',').filter(Boolean).forEach(packageId => {
      listWorkshopLocations(packageId)
        .then(data => {
          if (!active || !Array.isArray(data)) return;
          setLocationNames(previous => ({
            ...previous,
            ...Object.fromEntries(data.map(item => [`${packageId}:${item.id}`, item.name])),
          }));
        })
        .catch(() => { /* no name is shown when the read fails */ });
    });
    return () => { active = false; };
  }, [packageIdsKey]);
  const [validationErrors, setValidationErrors] = useState({});
  const [group, setGroup] = useState(null);
  const [confirmKey, setConfirmKey] = useState(null);

  useEffect(() => {
    if (authStatus !== 'signed-in') {
      setContact(EMPTY_CONTACT);
      return;
    }
    if (profile) setContact({ contactEmail: profile.email || '', ...splitPhone(profile.phone) });
  }, [authStatus, profile]);

  // Display only (FR-052): each line's per-ticket price after the automatic discount; the quote is the server's.
  const publicPrograms = usePublicWorkshopPrograms();
  const lineOffers = line => offersForPackage({ packageId: line.packageId, publicPrice: line.pricePerParticipantVnd }, publicPrograms);

  // Kho Voucher, the running workshop vouchers and the Member's points, for the picker. A failed load leaves it
  // empty: the code can still be typed, and Feature 006 decides what applies.
  const [savedVouchers, setSavedVouchers] = useState([]);
  const [workshopOffers, setWorkshopOffers] = useState([]);
  const [loyaltyAccount, setLoyaltyAccount] = useState(null);
  useEffect(() => {
    if (bookingMode !== 'MEMBER') {
      setSavedVouchers([]);
      setWorkshopOffers([]);
      setLoyaltyAccount(null);
      return undefined;
    }
    let active = true;
    http('/api/v1/members/me/saved-vouchers?limit=100')
      .then(result => {
        if (!active) return;
        const items = result?.items || result || [];
        setSavedVouchers(items.map(item => ({
          code: item.promotion?.code || item.code,
          name: item.promotion?.name || item.name || item.promotion?.code || item.code,
          availability: item.availability,
          scope: item.promotion?.scope,
          packageIds: item.promotion?.targets?.packageIds || [],
        })).filter(item => item.code));
      })
      .catch(() => { if (active) setSavedVouchers([]); });
    // The cart holds several packages, so the offers are not filtered by one package here.
    http('/api/v1/promotions?scope=WORKSHOP&limit=50')
      .then(result => {
        if (!active) return;
        setWorkshopOffers((result?.items || [])
          .filter(item => item.kind === 'VOUCHER' && item.code)
          .map(item => ({ code: item.code, name: item.name || item.code, discount: item.discount, availability: item.availability })));
      })
      .catch(() => { if (active) setWorkshopOffers([]); });
    http('/api/v1/members/me/loyalty-accounts/current')
      .then(result => { if (active) setLoyaltyAccount(result || null); })
      .catch(() => { if (active) setLoyaltyAccount(null); });
    return () => { active = false; };
  }, [bookingMode, http]);

  const invalidateQuote = () => {
    setQuote(null);
    setQuoteError(null);
    setConfirmError(null);
    setFailingLines([]);
    setConfirmKey(null);
  };

  const lineUnit = line => Number(lineOffers(line)?.finalPrice ?? line.pricePerParticipantVnd ?? 0);
  const totalTickets = selectedLines.reduce((sum, line) => sum + Number(line.participantCount || 0), 0);
  const estimatedTotal = selectedLines.reduce((sum, line) => sum + lineUnit(line) * Number(line.participantCount || 0), 0);

  const wholeBig = value => (/^\d+$/.test(String(value ?? '')) ? BigInt(value) : 0n);
  const quoteFor = (selection, authorization) => quoteWorkshopCart({
    lines: cartQuoteLines(selectedLines),
    benefitSelection: selection,
    authorization: authorization || undefined,
  });
  const noBenefit = { choice: 'NONE', voucherCode: '', requestedPoints: '0' };

  // Candidate vouchers for the auto pick: the Member's saved ones that fit this cart, then the running workshop vouchers.
  const selectionKey = selectedLines.map(line => `${line.lineId}:${line.participantCount}`).join('|');
  const candidateCodes = useMemo(() => {
    const cartPackages = selectedLines.map(line => String(line.packageId));
    const fits = item => {
      if (item.scope && item.scope !== 'WORKSHOP') return false;
      const limited = (item.packageIds || []).map(String);
      return limited.length === 0 || limited.some(id => cartPackages.includes(id));
    };
    const codes = [
      ...savedVouchers.filter(item => item.availability === 'ACTIVE' && fits(item)).map(item => item.code),
      ...usableOffers(workshopOffers, savedVouchers).map(item => item.code),
    ];
    return [...new Set(codes)].slice(0, 5);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [savedVouchers, workshopOffers, selectionKey]);
  const candidateKey = candidateCodes.join('|');

  // Auto pick (Shopee-style): quote the cart with each candidate voucher, keep the one with the largest voucher
  // discount, then ask for the most points the quote allows. Every figure is 006's quote; nothing is computed here.
  useEffect(() => {
    if (bookingMode !== 'MEMBER' || selectedLines.length === 0) {
      previewRun.current += 1;
      setPreview(null);
      setAutoPicking(false);
      return undefined;
    }
    if (userPicked.current) return undefined;
    const run = ++previewRun.current;
    const current = () => run === previewRun.current;
    setAutoPicking(true);
    (async () => {
      try {
        const authorization = await memberAuthorization();
        let best = { selection: noBenefit, result: await quoteFor(noBenefit, authorization), discount: 0n };
        for (const code of candidateCodes) {
          if (!current()) return;
          try {
            const selection = { choice: 'VOUCHER', voucherCode: code, requestedPoints: '0' };
            const result = await quoteFor(selection, authorization);
            const discount = wholeBig(result.voucherDiscountVnd);
            if (discount > best.discount) best = { selection, result, discount };
          } catch { /* a voucher the quote refuses is simply not a candidate */ }
        }
        const max = wholeBig(best.result.maxPoints);
        const min = wholeBig(best.result.minRedeemPoints);
        let final = best;
        if (max > 0n && max >= min) {
          try {
            const selection = { ...best.selection, requestedPoints: String(max) };
            final = { selection, result: await quoteFor(selection, authorization) };
          } catch { /* keep the voucher-only quote */ }
        }
        if (!current()) return;
        setBenefitSelection(final.selection);
        setPreview(final.result);
      } catch {
        if (current()) setPreview(null);
      } finally {
        if (current()) setAutoPicking(false);
      }
    })();
    return () => { previewRun.current += 1; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [bookingMode, selectionKey, candidateKey]);

  const runPreview = async selection => {
    if (bookingMode !== 'MEMBER' || selectedLines.length === 0) return;
    const run = ++previewRun.current;
    try {
      const authorization = await memberAuthorization();
      const result = await quoteFor(selection, authorization);
      if (run === previewRun.current) setPreview(result);
    } catch {
      if (run === previewRun.current) setPreview(null);
    }
  };

  // The auto-picked preview is already 006's quote for exactly these lines and benefits, so while it is still valid
  // the button opens the confirmation at once; otherwise (Guest, expired, refused) it quotes as before.
  const proceed = () => {
    const usable = preview?.quoteRef && Date.parse(preview.expiresAt) - Date.now() > 60 * 1000;
    if (usable && bookingMode === 'MEMBER') {
      setQuoteError(null);
      setConfirmError(null);
      setQuote(preview);
      return;
    }
    requestQuote();
  };

  const toggleLine = lineId => {
    setSelectedIds(ids => (ids.includes(lineId) ? ids.filter(id => id !== lineId) : [...ids, lineId]));
    userPicked.current = false;
    setPreview(null);
    invalidateQuote();
  };
  const toggleAll = () => {
    setSelectedIds(allSelected ? [] : lines.map(line => line.lineId));
    userPicked.current = false;
    setPreview(null);
    invalidateQuote();
  };


  const changeParticipants = (lineId, participantCount) => {
    setLines(updateCartLineParticipants(lineId, participantCount));
    userPicked.current = false;
    setPreview(null);
    invalidateQuote();
  };

  const remove = lineId => {
    setLines(removeCartLine(lineId));
    userPicked.current = false;
    setPreview(null);
    invalidateQuote();
  };

  const requestQuote = async (selectionOverride) => {
    if (selectedLines.length === 0) return;
    setQuoting(true);
    setQuoteError(null);
    setConfirmError(null);
    setFailingLines([]);
    try {
      const authorization = await memberAuthorization();
      const result = await quoteWorkshopCart({
        lines: cartQuoteLines(selectedLines),
        benefitSelection: bookingMode === 'MEMBER' ? (selectionOverride || benefitSelection) : undefined,
        authorization: authorization || undefined,
      });
      setQuote(result);
    } catch (error) {
      setQuote(null);
      setQuoteError(describe(error));
    } finally {
      setQuoting(false);
    }
  };

  /** A new voucher or point choice changes every amount, so the quote on screen is replaced at once (FR-033, FR-034). */
  const changeBenefitSelection = next => {
    const hadQuote = Boolean(quote);
    userPicked.current = true;
    // The preview answers for the previous voucher: drop it until the new one arrives.
    if (next.voucherCode !== benefitSelection.voucherCode) setPreview(null);
    setBenefitSelection(next);
    invalidateQuote();
    runPreview(next);
    if (hadQuote) requestQuote(next);
  };

  const confirm = async () => {
    const errors = validateBookingContact(contact, language);
    setValidationErrors(errors);
    if (Object.keys(errors).length > 0) return;

    setConfirming(true);
    setConfirmError(null);
    setFailingLines([]);
    try {
      const key = confirmKey
        || globalThis.crypto?.randomUUID?.()
        || `group-create-${Date.now()}-${Math.random().toString(16).slice(2)}`;
      setConfirmKey(key);
      const authorization = await memberAuthorization();
      const created = await createBookingGroup({
        quoteRef: quote.quoteRef,
        ...contactForRequest(contact),
        idempotencyKey: key,
        authorization: authorization || undefined,
      });
      setGroup(created);
      invalidatePendingPayments();
      setQuote(null);
      selectedLines.forEach(line => removeCartLine(line.lineId));
      setLines(readCart());
      setSelectedIds([]);
    } catch (error) {
      const failing = failingLinesOf(error)
        .map(position => lines.findIndex(line => line.lineId === selectedLines[position]?.lineId))
        .filter(position => position >= 0);
      if (failing.length > 0) { setFailingLines(failing); setFailingCode(error?.code || null); }
      setConfirmKey(null);
      setConfirmError(describe(error));
    } finally {
      setConfirming(false);
    }
  };

  const refreshGroup = async () => {
    const next = await refreshBookingGroup({ groupCode: group?.groupCode, mode: bookingMode, memberAuthorization, contact });
    if (next) { setGroup(next); invalidatePendingPayments(); }
  };

  if (group) {
    return (
      <main className="public-page figma-public-page">
        <GroupResultHeading group={group} />
        <GroupDetailCard group={group} authorization={cardIdentity.authorization} proof={cardIdentity.proof} allowRecipientChange={false} onRefresh={refreshGroup} allowManualRefresh={false} />
        <GroupAutoRefresh onRefresh={refreshGroup} groupStatus={group.groupStatus} />
      </main>
    );
  }

  return (
    <>
    <CartTabs active="workshop" />
    <main className="public-page figma-public-page">
      <p className="eyebrow">{t('cart.eyebrow')}</p>
      <h1>{t('cart.title')}</h1>
      <p className="lead">
        {t('cart.lead')}
      </p>

      <div role="status" style={{ margin: '20px 0', padding: '12px 16px', border: '1px solid #dcdfe6', background: '#f8fafc', color: '#002967' }}>
        {authStatus === 'loading' || profileLoading
          ? t('booking.who.loading')
          : bookingMode === 'MEMBER'
            ? t('booking.who.member')
            : bookingMode === 'GUEST'
              ? t('booking.who.guest')
              : t('booking.who.unknown')}
        {profileError && authStatus === 'signed-in' && (
          <small style={{ display: 'block', color: '#8a4b00', marginTop: '4px' }}>
            {t('booking.profileError')}
          </small>
        )}
      </div>

      {lines.length === 0 ? (
        <div className="empty-state">
          <p>{t('cart.empty')}</p>
          <Link className="button primary" to="/workshops">{t('cart.viewPackages')}</Link>
        </div>
      ) : (
        <section className="workspace-card" aria-label={t('cart.linesAria')}>
          <div className="cart-head">
            <input type="checkbox" aria-label={t('cart.selectAllAria')} checked={allSelected} onChange={toggleAll} disabled={quoting || confirming} />
            <span>{t('cart.th.package')}</span><span>{t('cart.th.unit')}</span><span>{t('cart.th.qty')}</span><span>{t('cart.th.total')}</span><span>{t('cart.th.actions')}</span>
          </div>
          <ul className="cart-rows">
            {lines.map((line, index) => {
              const failed = failingLines.includes(index);
              const offers = lineOffers(line);
              const unit = Number(offers?.finalPrice ?? line.pricePerParticipantVnd ?? 0);
              // The server's own per-ticket result (006): what this line costs after the voucher and points picked.
              const shownQuote = quote || preview;
              const quotedLine = shownQuote?.lines?.find(item => item.lineIndex === selectedLines.findIndex(picked => picked.lineId === line.lineId));
              const sumOf = key => (quotedLine?.tickets || []).reduce((sum, ticket) => sum + Number(ticket[key] || 0), 0);
              const lineQuoted = quotedLine && quotedLine.tickets?.length && selectedIds.includes(line.lineId)
                ? { gross: sumOf('packagePriceVnd'), final: sumOf('finalPriceVnd'), auto: sumOf('autoDiscountVnd'), voucher: sumOf('voucherDiscountVnd'), points: sumOf('pointDiscountVnd') }
                : null;
              return (
                <li key={line.lineId} className={`cart-row${failed ? ' failed' : ''}`}>
                  <div className="cart-cell cart-check">
                    <input
                      type="checkbox"
                      aria-label={t('cart.selectLineAria', { n: index + 1 })}
                      checked={selectedIds.includes(line.lineId)}
                      onChange={() => toggleLine(line.lineId)}
                      disabled={quoting || confirming}
                    />
                  </div>
                  <div className="cart-cell cart-product">
                    <strong>{line.packageName || t('quote.package', { id: line.packageId })}</strong>
                    <p className="muted">
                      {t('cart.lineN', { n: index + 1 })}{locationNames[`${line.packageId}:${line.locationId}`] ? ` · ${locationNames[`${line.packageId}:${line.locationId}`]}` : ''}
                    </p>
                    {line.sessionLabels?.length > 0 && (
                      <ul className="muted cart-sessions">
                        {line.sessionLabels.map((label, position) => (
                          <li key={`${line.lineId}-${position}`}>{t('cart.session', { n: position + 1, label })}</li>
                        ))}
                      </ul>
                    )}
                    {line.pricePerParticipantVnd ? <WorkshopOfferBadges offers={offers} /> : null}
                  </div>
                  <div className="cart-cell cart-unit" data-label={t('cart.th.unit')}>
                    {line.pricePerParticipantVnd ? (
                      <WorkshopOfferPrice offers={offers} voucher={false} fallback={<span className="price">{vnd(line.pricePerParticipantVnd)}</span>} />
                    ) : '—'}
                  </div>
                  <div className="cart-cell" data-label={t('cart.th.qty')}>
                    <div className="participant-stepper" role="group" aria-label={t('cart.participantsAria', { n: index + 1 })}>
                      <button
                        type="button"
                        aria-label={t('cart.decreaseAria', { n: index + 1 })}
                        disabled={line.participantCount <= (line.minParticipants || 1) || quoting || confirming}
                        onClick={() => changeParticipants(line.lineId, line.participantCount - 1)}
                      >
                        −
                      </button>
                      <input
                        type="number"
                        min={line.minParticipants || 1}
                        max={line.maxParticipants || undefined}
                        value={line.participantCount}
                        aria-label={t('cart.qtyAria', { n: index + 1 })}
                        onChange={event => changeParticipants(line.lineId, Number(event.target.value))}
                        disabled={quoting || confirming}
                      />
                      <button
                        type="button"
                        aria-label={t('cart.increaseAria', { n: index + 1 })}
                        disabled={Boolean(line.maxParticipants) && line.participantCount >= line.maxParticipants}
                        onClick={() => changeParticipants(line.lineId, line.participantCount + 1)}
                      >
                        +
                      </button>
                    </div>
                  </div>
                  <div className="cart-cell cart-subtotal" data-label={t('cart.th.total')}>
                    {lineQuoted ? (
                      <>
                        {lineQuoted.final < lineQuoted.gross && <s className="cart-was">{vnd(lineQuoted.gross)}</s>}
                        <span>{vnd(lineQuoted.final)}</span>
                        {(lineQuoted.auto > 0 || lineQuoted.voucher > 0 || lineQuoted.points > 0) && (
                          <small className="cart-saved">
                            {lineQuoted.auto > 0 ? t('cart.saved.auto', { amount: vnd(lineQuoted.auto) }) : ''}
                            {lineQuoted.voucher > 0 ? `${lineQuoted.auto > 0 ? ' · ' : ''}${t('cart.saved.voucher', { amount: vnd(lineQuoted.voucher) })}` : ''}
                            {lineQuoted.points > 0 ? `${lineQuoted.auto > 0 || lineQuoted.voucher > 0 ? ' · ' : ''}${t('cart.saved.points', { amount: vnd(lineQuoted.points) })}` : ''}
                          </small>
                        )}
                      </>
                    ) : (line.pricePerParticipantVnd ? vnd(unit * line.participantCount) : '—')}
                  </div>
                  <div className="cart-cell">
                    <button type="button" className="cart-remove" onClick={() => remove(line.lineId)} disabled={quoting || confirming}>
                      {t('cart.remove')}
                    </button>
                  </div>
                  {failed && (
                    <p role="alert" className="cart-row-alert">
                      {failingCode === 'PACKAGE_NOT_AVAILABLE_AT_LOCATION'
                        ? <>{t('cart.rowNotAvailable')} <a className="text-link" href={`/workshops/${line.packageId}`}>{t('booking.otherBranch')}</a></>
                        : t('cart.rowNoCapacity')}
                    </p>
                  )}
                </li>
              );
            })}
          </ul>

          {quoteError && (
            <div role="alert" style={{ marginTop: '12px', color: '#b00' }}>
              <strong>{quoteError.code}</strong>
              <p style={{ margin: '4px 0 0' }}>{quoteError.messageKey ? t(quoteError.messageKey) : quoteError.message}</p>
            </div>
          )}

          <div className="cart-bar">
            <label className="cart-bar-all">
              <input type="checkbox" checked={allSelected} onChange={toggleAll} disabled={quoting || confirming} /> {t('cart.selectAll', { count: lines.length })}
            </label>
            <div className="cart-bar-total" aria-live="polite">
              {(quote || preview) ? (
                <>
                  <span className="muted">{t('cart.totalAfter', { count: totalTickets })}</span>
                  <strong>{vnd((quote || preview).finalTotalVnd)}</strong>
                  <small className="muted">
                    {Number((quote || preview).autoDiscountVnd || 0) > 0 ? `${t('cart.saved.auto', { amount: vnd((quote || preview).autoDiscountVnd) })} · ` : ''}
                    {Number((quote || preview).voucherDiscountVnd || 0) > 0 ? t('cart.saved.voucher', { amount: vnd((quote || preview).voucherDiscountVnd) }) : t('cart.noVoucher')}
                    {Number((quote || preview).pointDiscountVnd || 0) > 0 ? ` · ${t('cart.saved.pointsFull', { amount: vnd((quote || preview).pointDiscountVnd), points: format.number(String((quote || preview).pointsUsed || 0)) })}` : ''}
                  </small>
                </>
              ) : (
                <>
                  <span className="muted">{autoPicking ? t('cart.picking') : t('cart.estimate', { count: totalTickets })}</span>
                  <strong>{vnd(estimatedTotal)}</strong>
                  <small className="muted">{t('cart.estimateNote')}</small>
                </>
              )}
            </div>
            <button
              type="button"
              className="button primary"
              onClick={proceed}
              disabled={quoting || confirming || autoPicking || selectedLines.length === 0 || bookingMode === null || profileLoading}
            >
              {quoting ? t('booking.quoting') : t('cart.proceed')}
            </button>
          </div>
        </section>
      )}

      {bookingMode === 'MEMBER' && lines.length > 0 && (
        <section className="workspace-card" style={{ marginTop: '24px' }} aria-label={t('cart.benefitsAria')}>
          <div className="card-head" style={{ marginBottom: '16px' }}>
            <div>
              <h2 style={{ margin: 0 }}>{t('cart.benefitsTitle')}</h2>
              <p className="muted" style={{ margin: '4px 0 0', fontSize: '13px' }}>
                {t('booking.benefitsHint')}
              </p>
            </div>
          </div>
          <CartBenefitPicker
            selection={benefitSelection}
            onChange={changeBenefitSelection}
            savedVouchers={savedVouchers}
            offers={workshopOffers}
            packageIds={selectedLines.map(line => line.packageId)}
            account={loyaltyAccount}
            quote={quote || preview}
            pending={quoting || confirming || autoPicking}
          />
        </section>
      )}

      <CartQuoteDialog open={Boolean(quote)} title={t('cart.dialogTitle')} busy={confirming} onClose={invalidateQuote}>
      <CartQuotePanel
        quote={quote}
        contact={contact}
        onContactChange={setContact}
        onConfirm={confirm}
        confirming={confirming}
        confirmError={confirmError}
        validationErrors={validationErrors}
        failingLines={failingLines}
        failingCode={failingCode}
        onCancel={invalidateQuote}
        packageHrefOf={index => `/workshops/${lines[index]?.packageId}`}
        title={t('cart.dialogTitle')}
      />
      </CartQuoteDialog>
    </main>
    </>
  );
}
