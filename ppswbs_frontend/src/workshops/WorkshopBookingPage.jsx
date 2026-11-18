import React, { useEffect, useRef, useState } from 'react';
import { Link, useSearchParams } from 'react-router';
import {
  createBookingGroup,
  getWorkshopPackage,
  listWorkshopLocations,
  listWorkshopSessions,
  quoteWorkshopCart,
  WorkshopApiError,
} from '../api/workshops';
import { CartBenefitPicker } from './CartBenefitPicker';
import { contactForRequest, EMPTY_CONTACT, splitPhone, validateBookingContact } from './contactValidation';
import { CartQuotePanel } from './CartQuotePanel';
import { GroupDetailCard } from './GroupDetailCard';
import { useGroupCardIdentity } from './useGroupCardIdentity';
import { refreshBookingGroup } from './refreshGroup';
import { invalidatePendingPayments } from './pendingPayments';
import { parseSelection, selectionProblem } from './bookingSelection';
import { cartLineCount, clearCart, subscribeToCart } from './cartStorage';
import { failingLinesOf, formatSessionWindow, vnd } from './groupDisplay';
import { GroupResultHeading } from './GroupResultHeading';
import { useBookingIdentity } from './useBookingIdentity';
import { useHttp } from '../shared/api/useHttp';
import { getEffectiveParticipantMax } from './bookingCapacity';
import { offersForPackage, usePublicWorkshopPrograms, WorkshopOfferBadges, WorkshopOfferPrice } from '../features/loyalty-promotion/publicOffers';
import './figmaPublicShell.css';

function formatDate(date) {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

const BOOKING_ERROR_MESSAGE = {
  INSUFFICIENT_CAPACITY: 'Ca workshop bạn chọn không còn đủ chỗ. Hệ thống không giữ chỗ nào; vui lòng chọn ca khác.',
  CART_HOLD_REJECTED: 'Giỏ chưa được giữ chỗ vì có dòng không còn đủ chỗ. Vui lòng sửa lại rồi thử lại.',
  PACKAGE_SELECTION_INVALID: 'Lựa chọn ca không hợp lệ theo quy định của gói (số ca, địa điểm hoặc khoảng cách thời gian).',
  PACKAGE_NOT_PUBLISHED: 'Gói workshop này hiện chưa được mở bán hoặc đã tạm dừng.',
  // 003.1 FR-055 (003.2 FR-039): nothing was held; the typed fields are kept (proposed copy, Q7).
  PACKAGE_NOT_AVAILABLE_AT_LOCATION: 'Gói này hiện không còn bán tại chi nhánh bạn đã chọn. Hệ thống không giữ chỗ nào; thông tin bạn đã nhập vẫn được giữ. Vui lòng chọn chi nhánh khác ở trang gói.',
  QUOTE_EXPIRED: 'Báo giá đã hết hạn. Vui lòng báo giá lại.',
  DEPENDENCY_UNAVAILABLE: 'Hệ thống tạm thời chưa xử lý được yêu cầu. Giỏ và thông tin bạn đã nhập vẫn được giữ; vui lòng thử lại sau.',
  CART_BENEFITS_NEED_A_MEMBER: 'Voucher và điểm chỉ áp dụng cho tài khoản Member đang đăng nhập.',
  SALES_PAUSED: 'Hệ thống đang tạm dừng nhận đăng ký cho ca này.',
  IDEMPOTENCY_CONFLICT: 'Yêu cầu trùng lặp nhưng nội dung khác với lần gửi trước.',
};

const describeBookingError = error => ({
  code: error?.code || 'CLIENT_ERROR',
  message: BOOKING_ERROR_MESSAGE[error?.code] || error?.detail || error?.message
    || 'Không thể hoàn tất yêu cầu. Vui lòng thử lại.',
  correlationId: error?.correlationId || null,
});

/**
 * Checkout of "Đặt ngay" for one package (003.1 FR-045, decision 2026-11-13; screens split 2026-11-16). The package
 * detail page picks the sessions and ticket count and sends them here in the URL; this screen only reviews that
 * choice. A direct booking is only a booking group: the single line is quoted with `POST /workshop-cart-quotes` as soon as
 * the account is known, shown on this screen, then confirmed with `POST /workshop-booking-groups`. "Thêm vào giỏ"
 * lives on the detail page and the cart page confirms with the same two calls. There is no server cart and no second
 * booking path.
 */
export function WorkshopBookingPage({ packageId }) {
  const { authStatus, mode: bookingMode, profile, profileLoading, profileError, memberAuthorization } = useBookingIdentity();
  const http = useHttp();

  const [pkg, setPkg] = useState(null);
  const [loadingPkg, setLoadingPkg] = useState(true);
  const [pkgError, setPkgError] = useState(null);

  const [contact, setContact] = useState(EMPTY_CONTACT);
  const cardIdentity = useGroupCardIdentity({ mode: bookingMode, memberAuthorization, contact });
  const [benefitSelection, setBenefitSelection] = useState({ choice: 'NONE', voucherCode: '', requestedPoints: '0' });

  // The choice made on the package detail page; this screen never edits it.
  const [searchParams, setSearchParams] = useSearchParams();
  const selection = useRef(parseSelection(searchParams)).current;
  const { locationId, from: selectedFrom, to: selectedTo } = selection;
  // The sessions can be swapped on this screen ("Đổi ca"); the URL follows so a refresh keeps the swap.
  const [selectedSessionRefs, setSelectedSessionRefs] = useState(selection.sessionReferences);
  const [changingSessions, setChangingSessions] = useState(false);
  const skipRequote = useRef(true);
  const participantCount = selection.participantCount;
  const today = new Date();
  const nextTwoWeeks = new Date();
  nextTwoWeeks.setDate(today.getDate() + 14);
  const from = selectedFrom || formatDate(today);
  const to = selectedTo || formatDate(nextTwoWeeks);
  const [locations, setLocations] = useState([]);
  // FR-056: the branch list the server answered (only branches where the Package is sold); the URL branch is checked against it.
  const [locationsReady, setLocationsReady] = useState(false);
  const [branchConflict, setBranchConflict] = useState(false);

  const [sessions, setSessions] = useState([]);
  // False until the sessions of the choice were read (or there is nothing to read): the quote waits for it.
  const [sessionsReady, setSessionsReady] = useState(false);
  const loadingSessions = !sessionsReady;
  const [sessionsError, setSessionsError] = useState(null);
  const autoQuoted = useRef(false);
  const userPicked = useRef(false);

  // Quote ("Đặt ngay"), confirmation and the created group
  const [quote, setQuote] = useState(null);
  const [quoting, setQuoting] = useState(false);
  const [quoteError, setQuoteError] = useState(null);
  const [confirming, setConfirming] = useState(false);
  const [confirmError, setConfirmError] = useState(null);
  const [failingLines, setFailingLines] = useState([]);
  const [failingCode, setFailingCode] = useState(null);
  const [group, setGroup] = useState(null);
  const [validationErrors, setValidationErrors] = useState({});
  const [cartCount, setCartCount] = useState(() => cartLineCount());
  const confirmKey = useRef(null);

  useEffect(() => subscribeToCart(lines => setCartCount(lines.length)), []);

  useEffect(() => {
    if (authStatus !== 'signed-in') {
      // Authenticated contact data is not kept after sign-out.
      setContact(EMPTY_CONTACT);
      return;
    }
    if (profile) {
      setContact({ contactEmail: profile.email || '', ...splitPhone(profile.phone) });
    }
  }, [authStatus, profile]);

  // Kho Voucher and the running workshop vouchers for the picker; a failed load just leaves it empty.
  // `vouchersLoaded` tells the auto pick that both lists were answered (or failed), so it does not run on half a list.
  const [vouchersLoaded, setVouchersLoaded] = useState({ saved: false, offers: false });
  const [savedVouchers, setSavedVouchers] = useState([]);
  useEffect(() => {
    if (bookingMode !== 'MEMBER') { setSavedVouchers([]); return undefined; }
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
      .catch(() => { if (active) setSavedVouchers([]); })
      .finally(() => { if (active) setVouchersLoaded(current => ({ ...current, saved: true })); });
    return () => { active = false; };
  }, [bookingMode, http]);

  const [workshopOffers, setWorkshopOffers] = useState([]);
  useEffect(() => {
    if (bookingMode !== 'MEMBER') { setWorkshopOffers([]); return undefined; }
    let active = true;
    http('/api/v1/promotions?scope=WORKSHOP&limit=50')
      .then(result => {
        if (!active) return;
        const items = result?.items || [];
        setWorkshopOffers(items
          .filter(item => item.kind === 'VOUCHER' && item.code)
          .filter(item => !(item.targets?.packageIds?.length)
            || item.targets.packageIds.map(String).includes(String(packageId)))
          .map(item => ({ code: item.code, name: item.name || item.code, discount: item.discount })));
      })
      .catch(() => { if (active) setWorkshopOffers([]); })
      .finally(() => { if (active) setVouchersLoaded(current => ({ ...current, offers: true })); });
    return () => { active = false; };
  }, [bookingMode, http, packageId]);

  const [loyaltyAccount, setLoyaltyAccount] = useState(null);
  useEffect(() => {
    if (bookingMode !== 'MEMBER') { setLoyaltyAccount(null); return undefined; }
    let active = true;
    http('/api/v1/members/me/loyalty-accounts/current')
      .then(result => { if (active) setLoyaltyAccount(result || null); })
      .catch(() => { if (active) setLoyaltyAccount(null); });
    return () => { active = false; };
  }, [bookingMode, http]);

  // Display only (FR-052): the amount to pay is always the server quote.
  const publicPrograms = usePublicWorkshopPrograms();
  const packageOffers = pkg ? offersForPackage(pkg, publicPrograms) : null;

  const requiredSessions = Math.max(1, Number(pkg?.requiredSessionCount) || 1);
  const packageMinParticipants = Math.max(1, Number(pkg?.minParticipants) || 1);
  const sessionsOfChoice = selectedSessionRefs.map(reference => sessions.find(item => item.sessionReference === reference));
  const effectiveMaxParticipants = getEffectiveParticipantMax(pkg, sessions, selectedSessionRefs);

  // A quote stops matching the choices as soon as anything in the cart line changes.
  const invalidateQuote = () => {
    setQuote(null);
    setQuoteError(null);
    setConfirmError(null);
    setFailingLines([]);
    confirmKey.current = null;
  };

  useEffect(() => {
    if (!packageId) return;
    setLoadingPkg(true);
    setPkgError(null);
    getWorkshopPackage(packageId)
      .then(data => { setPkg(data); })
      .catch(error => setPkgError(error?.detail || error?.message || 'Không tìm thấy gói workshop.'))
      .finally(() => setLoadingPkg(false));
  }, [packageId]);

  // Branch name and the sessions' times for the review below; the references in the URL are not trusted for display.
  useEffect(() => {
    if (!packageId || !pkg) return undefined;
    let active = true;
    listWorkshopLocations(packageId, { from, to })
      .then(data => { if (active) { setLocations(Array.isArray(data) ? data : []); setLocationsReady(true); } })
      .catch(() => { if (active) { setLocations([]); setLocationsReady(false); } });
    return () => { active = false; };
  }, [packageId, pkg, from, to]);

  useEffect(() => {
    if (!locationId) { setSessions([]); setSessionsReady(true); return undefined; }
    if (!pkg) return undefined;
    let active = true;
    setSessionsError(null);
    listWorkshopSessions(packageId, { locationId, from, to })
      .then(data => { if (active) setSessions(Array.isArray(data) ? data : []); })
      .catch(error => {
        if (!active) return;
        // FR-056: a stale or typed URL for a branch that no longer sells this Package; no technical error, no retry.
        if (error?.code === 'PACKAGE_NOT_AVAILABLE_AT_LOCATION') { setSessions([]); setBranchConflict(true); return; }
        setSessionsError(error?.detail || error?.message || 'Không thể tải lịch ca workshop. Vui lòng thử lại.');
      })
      .finally(() => { if (active) setSessionsReady(true); });
    return () => { active = false; };
  }, [pkg, packageId, locationId, from, to]);

  const lineForCart = () => ({
    packageId: Number(packageId),
    sessionReferences: selectedSessionRefs,
    participantCount: Number(participantCount),
  });

  const choiceProblem = () => {
    if (selectedSessionRefs.length < requiredSessions) return 'Bạn chưa chọn ca workshop. Vui lòng quay lại trang chi tiết gói để chọn ca.';
    if (sessionsOfChoice.some(item => !item || !item.customerSelectable || Number(item.remainingCapacity) < 1)) {
      return 'Có ca bạn chọn không còn khả dụng. Vui lòng quay lại trang chi tiết gói để chọn ca khác.';
    }
    return selectionProblem({
      requiredSessions,
      selectedCount: selectedSessionRefs.length,
      effectiveMax: effectiveMaxParticipants,
      minParticipants: packageMinParticipants,
      participantCount,
    });
  };

  /** FR-045 "Đặt ngay": the cart is this one line, quoted and shown here; no cart page is involved. */
  const bookNow = async (selectionOverride) => {
    const problem = choiceProblem();
    if (problem) {
      setValidationErrors({ sessions: problem });
      return;
    }
    setValidationErrors({});
    setQuoting(true);
    setQuoteError(null);
    setConfirmError(null);
    setFailingLines([]);
    try {
      const authorization = await memberAuthorization();
      const result = await quoteWorkshopCart({
        lines: [lineForCart()],
        benefitSelection: bookingMode === 'MEMBER' ? (selectionOverride || benefitSelection) : undefined,
        authorization: authorization || undefined,
      });
      setQuote(result);
    } catch (error) {
      setQuote(null);
      setQuoteError(describeBookingError(error));
    } finally {
      setQuoting(false);
    }
  };

  /**
   * A new voucher or point choice changes every amount, so the quote on screen is replaced at once by a new one
   * (FR-033, FR-034). Before the first quote there is nothing to replace: the quote comes with "Đặt ngay".
   */
  const changeBenefitSelection = next => {
    const hadQuote = Boolean(quote);
    userPicked.current = true; // a Member's own choice always wins over the automatic pick
    setBenefitSelection(next);
    invalidateQuote();
    // A pick made while the automatic one is still running quotes too: the automatic run stands down on `userPicked`.
    if (hadQuote || quoting) bookNow(next);
  };

  // Arriving from "Đặt ngay": quote the one line as soon as the account is known (a Member's benefits need the token).
  const identityKnown = authStatus !== 'loading' && !profileLoading && bookingMode !== null;

  /**
   * Auto pick (Shopee-style, as on the cart page): quote the line with no voucher, then with each candidate voucher
   * (the Member's saved ones that fit this package, then the running workshop vouchers), and keep the one with the
   * largest `voucherDiscountVnd`. Every figure is 006's quote; nothing is computed here. A voucher the quote refuses
   * is simply not a candidate. Points stay off until the Member turns them on.
   */
  const autoPickAndQuote = async () => {
    const problem = choiceProblem();
    if (problem) {
      setValidationErrors({ sessions: problem });
      return;
    }
    setValidationErrors({});
    setQuoting(true);
    setQuoteError(null);
    setConfirmError(null);
    setFailingLines([]);
    try {
      const authorization = await memberAuthorization();
      const ask = benefits => quoteWorkshopCart({
        lines: [lineForCart()],
        benefitSelection: benefits,
        authorization: authorization || undefined,
      });
      const wholeBig = value => (/^\d+$/.test(String(value ?? '')) ? BigInt(value) : 0n);
      // A Guest sends no benefits at all; a Member starts from "no voucher" and tries the candidates.
      const base = bookingMode === 'MEMBER' ? benefitSelection : undefined;
      let best = { selection: base, result: await ask(base), discount: 0n };
      if (bookingMode === 'MEMBER') {
        const fits = item => item.availability === 'ACTIVE'
          && (!item.scope || item.scope === 'WORKSHOP')
          && ((item.packageIds || []).length === 0 || item.packageIds.map(String).includes(String(packageId)));
        const codes = [...new Set([
          ...savedVouchers.filter(fits).map(item => item.code),
          ...workshopOffers.map(item => item.code),
        ])].slice(0, 5);
        for (const code of codes) {
          if (userPicked.current) return;
          try {
            const candidate = { choice: 'VOUCHER', voucherCode: code, requestedPoints: '0' };
            const result = await ask(candidate);
            const discount = wholeBig(result.voucherDiscountVnd);
            if (discount > best.discount) best = { selection: candidate, result, discount };
          } catch { /* refused by the quote: not a candidate */ }
        }
      }
      if (userPicked.current) return;
      if (bookingMode === 'MEMBER') setBenefitSelection(best.selection);
      setQuote(best.result);
    } catch (error) {
      setQuote(null);
      setQuoteError(describeBookingError(error));
    } finally {
      setQuoting(false);
    }
  };

  const swapSession = (index, reference) => {
    if (!reference || reference === selectedSessionRefs[index]) { setChangingSessions(false); return; }
    const next = selectedSessionRefs.map((current, position) => (position === index ? reference : current));
    setSelectedSessionRefs(next);
    setChangingSessions(false);
    invalidateQuote();
    setValidationErrors({});
    const params = new URLSearchParams(searchParams);
    params.delete('session');
    next.forEach(item => params.append('session', item));
    setSearchParams(params, { replace: true });
  };

  // A swapped session changes the amounts, so the quote is asked again at once (the first quote is the automatic pick).
  useEffect(() => {
    if (skipRequote.current) { skipRequote.current = false; return; }
    if (autoQuoted.current) bookNow();
  }, [selectedSessionRefs]);

  const vouchersReady = bookingMode !== 'MEMBER' || (vouchersLoaded.saved && vouchersLoaded.offers);
  useEffect(() => {
    if (autoQuoted.current || !pkg || !identityKnown || loadingSessions || !vouchersReady) return;
    autoQuoted.current = true;
    autoPickAndQuote();
  }, [pkg, identityKnown, loadingSessions, vouchersReady]);

  const confirmQuote = async () => {
    const errors = validateBookingContact(contact);
    setValidationErrors(errors);
    if (Object.keys(errors).length > 0) return;

    setConfirming(true);
    setConfirmError(null);
    setFailingLines([]);
    try {
      if (!confirmKey.current) {
        confirmKey.current = globalThis.crypto?.randomUUID?.()
          || `group-create-${Date.now()}-${Math.random().toString(16).slice(2)}`;
      }
      const authorization = await memberAuthorization();
      const created = await createBookingGroup({
        quoteRef: quote.quoteRef,
        ...contactForRequest(contact),
        idempotencyKey: confirmKey.current,
        authorization: authorization || undefined,
      });
      setGroup(created);
      invalidatePendingPayments();
      setQuote(null);
      // The cart this booking came from is gone (FR-045).
      clearCart();
    } catch (error) {
      const lines = failingLinesOf(error);
      if (lines.length > 0) { setFailingLines(lines); setFailingCode(error?.code || null); }
      confirmKey.current = null;
      setConfirmError(describeBookingError(error));
    } finally {
      setConfirming(false);
    }
  };

  // "Đổi ca" goes back to the detail page with the same choice so nothing has to be picked again.
  const changeChoiceHref = `/workshops/${packageId}?${searchParams.toString()}`;

  if (loadingPkg) {
    return (
      <main className="public-page figma-public-page">
        <p className="eyebrow">WORKSHOP TẠI MLAND</p>
        <div className="empty-state"><p>Đang tải thông tin gói workshop...</p></div>
      </main>
    );
  }

  if (pkgError || !pkg) {
    return (
      <main className="public-page figma-public-page">
        <p className="eyebrow">WORKSHOP TẠI MLAND</p>
        <div className="empty-state">
          <p className="status" style={{ color: '#b00', borderColor: '#b00' }}>
            {pkgError || 'Gói workshop không tồn tại hoặc đã ngừng cung cấp.'}
          </p>
          <div style={{ marginTop: '20px' }}>
            <a href="/workshops" className="button outline">← Quay lại danh sách workshop</a>
          </div>
        </div>
      </main>
    );
  }

  // FR-056: no booking control when the Package is sold nowhere, or the branch of the URL is not sold any more.
  const soldNowhere = locationsReady && locations.length === 0;
  const branchNotSold = branchConflict
    || (locationsReady && locations.length > 0 && locationId && !locations.some(item => Number(item.id) === Number(locationId)));
  if (!group && (soldNowhere || branchNotSold)) {
    return (
      <main className="public-page figma-public-page">
        <p className="eyebrow">THANH TOÁN WORKSHOP</p>
        <h1>{pkg.name}</h1>
        <div className="empty-state">
          <p role="status">
            {soldNowhere
              ? 'Gói này hiện chưa được bán tại chi nhánh nào. Vui lòng quay lại sau.'
              : 'Gói này hiện không còn bán tại chi nhánh bạn đã chọn. Vui lòng chọn chi nhánh khác.'}
          </p>
          {!soldNowhere && (
            <p style={{ marginTop: '12px' }}><a className="button primary" href={`/workshops/${pkg.packageId ?? packageId}`}>Chọn chi nhánh khác</a></p>
          )}
        </div>
      </main>
    );
  }

  const refreshGroup = async () => {
    const next = await refreshBookingGroup({ groupCode: group?.groupCode, mode: bookingMode, memberAuthorization, contact });
    if (next) { setGroup(next); invalidatePendingPayments(); }
  };

  if (group) {
    return (
      <main className="public-page figma-public-page">
        <GroupResultHeading group={group} />
        <GroupDetailCard group={group} authorization={cardIdentity.authorization} proof={cardIdentity.proof} allowRecipientChange={false} onRefresh={refreshGroup} />
      </main>
    );
  }

  return (
    <main className="public-page figma-public-page">
      <div style={{ marginBottom: '16px', display: 'flex', justifyContent: 'space-between', gap: '12px', flexWrap: 'wrap' }}>
        <Link to={changeChoiceHref} className="text-link">← Quay lại chọn ca và số vé</Link>
        <Link className="text-link" to="/workshop-booking/cart">Giỏ workshop ({cartCount})</Link>
      </div>

      <p className="eyebrow">THANH TOÁN WORKSHOP</p>
      <h1>{pkg.name}</h1>
      <p className="lead">
        Kiểm tra ca đã chọn, áp dụng ưu đãi rồi xác nhận đặt chỗ. Mỗi người là một vé riêng với QR, điểm danh và hóa đơn
        riêng.
      </p>

      <div role="status" style={{ margin: '20px 0', padding: '12px 16px', border: '1px solid #dcdfe6', background: '#f8fafc', color: '#002967' }}>
        {authStatus === 'loading' || profileLoading
          ? 'Đang xác định thông tin tài khoản...'
          : bookingMode === 'MEMBER'
            ? 'Đang đặt với tư cách Hội viên MLAND (Member).'
            : bookingMode === 'GUEST'
              ? 'Đang đặt với tư cách Khách vãng lai (Guest).'
              : 'Không thể xác minh vai trò tài khoản.'}
        {profileError && authStatus === 'signed-in' && (
          <small style={{ display: 'block', color: '#8a4b00', marginTop: '4px' }}>
            Không tải được hồ sơ Member; luồng đặt chỗ đang tạm khóa để tránh dùng sai quyền tài khoản.
          </small>
        )}
      </div>

      {authStatus === 'signed-in' && bookingMode === null && (
        <div className="workspace-card" style={{ border: '2px solid #b00', background: '#fff5f5', marginBottom: '24px', padding: '20px' }}>
          <strong style={{ color: '#b00' }}>Không thể xác minh quyền đặt workshop</strong>
          <p style={{ margin: '8px 0 0' }}>Vui lòng đăng nhập lại hoặc liên hệ quản trị viên để cập nhật vai trò Member.</p>
        </div>
      )}

      <fieldset
        disabled={authStatus === 'loading' || profileLoading || bookingMode === null}
        style={{ border: 0, padding: 0, margin: 0, minInlineSize: 0 }}
      >
        <section id="workshop-sessions-section" className="workspace-card" style={{ scrollMarginTop: '32px' }}>
          <div className="card-head">
            <div>
              <h2>1. Ca workshop đã chọn</h2>
              <p className="muted" style={{ margin: '4px 0 0' }}>
                {locations.find(item => Number(item.id) === Number(locationId))?.name || ''}
              </p>
            </div>
            {selectedSessionRefs.length > 0 && sessions.length > 0 && (
              <button type="button" className="text-link" onClick={() => setChangingSessions(value => !value)}>
                {changingSessions ? 'Hủy' : 'Đổi ca'}
              </button>
            )}
          </div>

          {loadingSessions && <p className="muted">Đang tải lịch ca workshop...</p>}
          {sessionsError && !loadingSessions && <p role="alert" style={{ color: '#b00' }}>{sessionsError}</p>}
          {selectedSessionRefs.length === 0 && (
            <div className="empty-state">
              <p>Bạn chưa chọn ca workshop nào.</p>
              <p style={{ marginTop: '12px' }}><a className="button primary" href={`/workshops/${pkg.packageId}`}>Chọn ca</a></p>
            </div>
          )}
          {selectedSessionRefs.length > 0 && (
            <ul style={{ listStyle: 'none', margin: 0, padding: 0, display: 'flex', flexDirection: 'column', gap: '8px' }}>
              {selectedSessionRefs.map((reference, index) => {
                const session = sessionsOfChoice[index];
                return (
                  <li key={reference} style={{ padding: '12px 16px', border: '1px solid #dcdfe6', background: '#fafafa' }}>
                    {changingSessions ? (
                      <select
                        aria-label={`Chọn ca thay thế cho buổi ${index + 1}`}
                        value={reference}
                        onChange={event => swapSession(index, event.target.value)}
                        style={{ width: '100%' }}
                      >
                        {sessions
                          .filter(item => item.sessionReference === reference
                            || (item.customerSelectable && Number(item.remainingCapacity) >= 1
                              && !selectedSessionRefs.includes(item.sessionReference)))
                          .map(item => {
                            const fits = item.sessionReference === reference || Number(item.remainingCapacity) >= Number(participantCount);
                            return (
                              <option key={item.sessionReference} value={item.sessionReference} disabled={!fits}>
                                {formatSessionWindow(item.scheduledStart, item.scheduledEnd)} – còn {item.remainingCapacity} chỗ{fits ? '' : ' (không đủ cho số vé)'}
                              </option>
                            );
                          })}
                      </select>
                    ) : (
                      <>
                        {session?.locationName && <small style={{ display: 'block', color: '#002967' }}>{session.locationName}</small>}
                        <strong>{session ? formatSessionWindow(session.scheduledStart, session.scheduledEnd) : reference}</strong>
                      </>
                    )}
                  </li>
                );
              })}
            </ul>
          )}
          {validationErrors.sessions && (
            <p role="alert" style={{ color: '#b00', fontSize: '14px', margin: '12px 0 0' }}>
              {validationErrors.sessions} <Link className="text-link" to={changeChoiceHref}>Đổi ca</Link>
            </p>
          )}
        </section>

        {bookingMode === 'MEMBER' && (
          <section id="member-benefits-section" className="workspace-card" style={{ marginTop: '24px', scrollMarginTop: '32px' }}>
            <div className="card-head" style={{ marginBottom: '16px' }}>
              <div>
                <h2 style={{ margin: 0 }}>2. Ưu đãi &amp; Điểm thưởng</h2>
                <p className="muted" style={{ margin: '4px 0 0', fontSize: '13px' }}>
                  Một lần dùng voucher áp cho cả nhóm; hệ thống phân bổ ưu đãi cho từng vé khi báo giá.
                </p>
              </div>
            </div>
            <CartBenefitPicker
              selection={benefitSelection}
              onChange={changeBenefitSelection}
              savedVouchers={savedVouchers}
              offers={workshopOffers}
              packageIds={[packageId]}
              account={loyaltyAccount}
              quote={quote}
              pending={quoting || confirming}
            />
          </section>
        )}

        <section className="workspace-card" style={{ marginTop: '24px' }}>
          <h2 style={{ marginTop: 0 }}>{bookingMode === 'MEMBER' ? '3' : '2'}. Đặt chỗ</h2>
          <p style={{ margin: '0 0 6px' }}>
            Gói: <strong>{pkg.name}</strong> · Đơn giá:{' '}
            {/* FR-052: price after the best automatic discount from 006's public gate; the quote below stays the server's. */}
            <strong><WorkshopOfferPrice offers={packageOffers} fallback={vnd(pkg.publicPrice)} /> / vé</strong> · Số vé:{' '}
            <strong>{participantCount}</strong>
          </p>
          <WorkshopOfferBadges offers={packageOffers} />
          <p className="muted" style={{ fontSize: '13px' }}>
            Giá cuối, ưu đãi và số tiền phải trả khi đặt do hệ thống tính ở bước báo giá.
          </p>
          {quoteError && !quoting && (
            <div className="actions" style={{ marginTop: '12px' }}>
              <button type="button" className="button outline" onClick={() => bookNow()} disabled={confirming}>
                Báo giá lại
              </button>
            </div>
          )}
          {quoting && <p role="status" className="muted" style={{ marginTop: '12px' }}>Đang báo giá...</p>}
          {quoteError && (
            <div role="alert" style={{ marginTop: '12px', color: '#b00' }}>
              <strong>{quoteError.code}</strong>
              <p style={{ margin: '4px 0 0' }}>{quoteError.message}</p>
              {quoteError.correlationId && (
                <p className="muted" style={{ fontSize: '12px', margin: 0 }}>Mã đối soát: {quoteError.correlationId}</p>
              )}
            </div>
          )}
        </section>

        <CartQuotePanel
          quote={quote}
          contact={contact}
          onContactChange={setContact}
          onConfirm={confirmQuote}
          confirming={confirming}
          confirmError={confirmError}
          validationErrors={validationErrors}
          failingLines={failingLines}
          failingCode={failingCode}
          packageHrefOf={() => `/workshops/${pkg.packageId ?? packageId}`}
          title="Báo giá đặt ngay"
        />
      </fieldset>
    </main>
  );
}
