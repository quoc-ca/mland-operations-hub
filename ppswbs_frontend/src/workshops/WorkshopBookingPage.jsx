import React, { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router';
import {
  createBookingGroup,
  getWorkshopPackage,
  listWorkshopLocations,
  listWorkshopSessions,
  quoteWorkshopCart,
  WorkshopApiError,
} from '../api/workshops';
import { CartBenefitPicker } from './CartBenefitPicker';
import { CartQuotePanel } from './CartQuotePanel';
import { GroupDetailCard } from './GroupDetailCard';
import { addCartLine, cartLineCount, clearCart, subscribeToCart } from './cartStorage';
import { failingLinesOf, formatSessionWindow, vnd } from './groupDisplay';
import { useBookingIdentity } from './useBookingIdentity';
import { useHttp } from '../shared/api/useHttp';
import { getEffectiveParticipantMax } from './bookingCapacity';
import { Paginator, usePaged } from '../shared/ui/Paginator';
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
  QUOTE_EXPIRED: 'Báo giá đã hết hạn. Vui lòng báo giá lại.',
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
 * Booking one package (003.1 FR-045, decision 2026-11-13). A direct booking is only a booking group, so both actions
 * here go through the same two calls: "Đặt ngay" quotes this single line with `POST /workshop-cart-quotes` and shows
 * the quote on this screen, then confirms it with `POST /workshop-booking-groups`; "Thêm vào giỏ" stores the line in
 * this browser and the cart page confirms it with the same two calls. There is no server cart and no second booking
 * path.
 */
export function WorkshopBookingPage({ packageId }) {
  const { authStatus, mode: bookingMode, profile, profileLoading, profileError, memberAuthorization } = useBookingIdentity();
  const http = useHttp();

  const [pkg, setPkg] = useState(null);
  const [loadingPkg, setLoadingPkg] = useState(true);
  const [pkgError, setPkgError] = useState(null);

  const [contact, setContact] = useState({ contactEmail: '', contactPhone: '' });
  const [participantCount, setParticipantCount] = useState(1);
  const [benefitSelection, setBenefitSelection] = useState({ choice: 'NONE', voucherCode: '', requestedPoints: '0' });

  const today = new Date();
  const nextTwoWeeks = new Date();
  nextTwoWeeks.setDate(today.getDate() + 14);

  const urlParams = useRef(new URLSearchParams(typeof window !== 'undefined' ? window.location.search : ''));
  const pendingSessionRef = useRef(urlParams.current.get('session'));
  const [locationId, setLocationId] = useState(Number(urlParams.current.get('locationId')) || null);
  const [locations, setLocations] = useState([]);
  const [loadingLocations, setLoadingLocations] = useState(false);
  const [locationsError, setLocationsError] = useState(null);
  const [from, setFrom] = useState(urlParams.current.get('from') || formatDate(today));
  const [to, setTo] = useState(urlParams.current.get('to') || formatDate(nextTwoWeeks));

  const [sessions, setSessions] = useState([]);
  const [loadingSessions, setLoadingSessions] = useState(false);
  const [sessionsError, setSessionsError] = useState(null);
  const pagedSessions = usePaged(sessions, 8, sessions);
  const pendingPageRef = useRef(null);
  useEffect(() => {
    if (pendingPageRef.current != null) { pagedSessions.setPage(pendingPageRef.current); pendingPageRef.current = null; }
  }, [sessions]);

  const [selectedSessionRefs, setSelectedSessionRefs] = useState([]);

  // Quote ("Đặt ngay"), confirmation and the created group
  const [quote, setQuote] = useState(null);
  const [quoting, setQuoting] = useState(false);
  const [quoteError, setQuoteError] = useState(null);
  const [confirming, setConfirming] = useState(false);
  const [confirmError, setConfirmError] = useState(null);
  const [failingLines, setFailingLines] = useState([]);
  const [group, setGroup] = useState(null);
  const [validationErrors, setValidationErrors] = useState({});
  const [cartMessage, setCartMessage] = useState(null);
  const [cartCount, setCartCount] = useState(() => cartLineCount());
  const confirmKey = useRef(null);

  useEffect(() => subscribeToCart(lines => setCartCount(lines.length)), []);

  useEffect(() => {
    if (authStatus !== 'signed-in') {
      // Authenticated contact data is not kept after sign-out.
      setContact({ contactEmail: '', contactPhone: '' });
      return;
    }
    if (profile) {
      setContact({ contactEmail: profile.email || '', contactPhone: profile.phone || '' });
    }
  }, [authStatus, profile]);

  // Kho Voucher and the running workshop vouchers for the picker; a failed load just leaves it empty.
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
        })).filter(item => item.code));
      })
      .catch(() => { if (active) setSavedVouchers([]); });
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
      .catch(() => { if (active) setWorkshopOffers([]); });
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
  const effectiveMaxParticipants = getEffectiveParticipantMax(pkg, sessions, selectedSessionRefs);
  const hasCompleteSessionSelection = selectedSessionRefs.length >= requiredSessions;
  const canChooseParticipantCount = hasCompleteSessionSelection
    && effectiveMaxParticipants >= packageMinParticipants;
  const inputMaxParticipants = canChooseParticipantCount ? effectiveMaxParticipants : 1;

  // A quote stops matching the choices as soon as anything in the cart line changes.
  const invalidateQuote = () => {
    setQuote(null);
    setQuoteError(null);
    setConfirmError(null);
    setFailingLines([]);
    confirmKey.current = null;
  };

  useEffect(() => {
    if (!hasCompleteSessionSelection) {
      if (participantCount !== 1) setParticipantCount(1);
      return;
    }
    if (effectiveMaxParticipants < packageMinParticipants) return;
    if (participantCount > effectiveMaxParticipants) setParticipantCount(effectiveMaxParticipants);
  }, [hasCompleteSessionSelection, effectiveMaxParticipants, packageMinParticipants, participantCount]);

  useEffect(() => {
    if (!packageId) return;
    setLoadingPkg(true);
    setPkgError(null);
    getWorkshopPackage(packageId)
      .then(data => { setPkg(data); setParticipantCount(1); })
      .catch(error => setPkgError(error?.detail || error?.message || 'Không tìm thấy gói workshop.'))
      .finally(() => setLoadingPkg(false));
  }, [packageId]);

  useEffect(() => {
    if (!packageId || !pkg) return undefined;
    let active = true;
    setLoadingLocations(true);
    setLocationsError(null);
    listWorkshopLocations(packageId, { from, to })
      .then(data => {
        if (!active) return;
        const next = Array.isArray(data) ? data : [];
        setLocations(next);
        setLocationId(current => (next.some(item => Number(item.id) === Number(current)) ? current : (next[0]?.id ?? null)));
      })
      .catch(error => {
        if (!active) return;
        setLocations([]);
        setLocationId(null);
        setLocationsError(error?.detail || error?.message || 'Không thể tải danh sách chi nhánh.');
      })
      .finally(() => { if (active) setLoadingLocations(false); });
    return () => { active = false; };
  }, [packageId, pkg, from, to]);

  useEffect(() => {
    if (!pkg || !locationId) { setSessions([]); return; }
    setLoadingSessions(true);
    setSessionsError(null);
    listWorkshopSessions(packageId, { locationId, from, to })
      .then(data => {
        const list = Array.isArray(data) ? data : [];
        setSessions(list);
        const wanted = pendingSessionRef.current;
        if (wanted) {
          pendingSessionRef.current = null;
          const index = list.findIndex(item => item.sessionReference === wanted);
          if (index >= 0 && list[index].customerSelectable && list[index].remainingCapacity > 0) {
            setSelectedSessionRefs([wanted]);
            pendingPageRef.current = Math.floor(index / 8);
          }
        }
      })
      .catch(error => setSessionsError(error?.detail || error?.message || 'Không thể tải lịch ca workshop. Vui lòng thử lại.'))
      .finally(() => setLoadingSessions(false));
  }, [pkg, packageId, locationId, from, to]);

  useEffect(() => { setSelectedSessionRefs([]); invalidateQuote(); }, [locationId, from, to]);

  const toggleSessionSelection = sessionReference => {
    setSelectedSessionRefs(previous => {
      if (previous.includes(sessionReference)) return previous.filter(ref => ref !== sessionReference);
      if (requiredSessions === 1) return [sessionReference];
      if (previous.length >= requiredSessions) return [...previous.slice(1), sessionReference];
      return [...previous, sessionReference];
    });
    invalidateQuote();
  };

  const selectedSessionLabels = () => selectedSessionRefs.map(reference => {
    const session = sessions.find(item => item.sessionReference === reference);
    return session ? formatSessionWindow(session.scheduledStart, session.scheduledEnd) : reference;
  });

  const lineForCart = () => ({
    packageId: Number(packageId),
    sessionReferences: selectedSessionRefs,
    participantCount: Number(participantCount),
  });

  const selectionProblem = () => {
    if (selectedSessionRefs.length < requiredSessions) {
      return `Vui lòng chọn đủ ${requiredSessions} ca workshop.`;
    }
    if (effectiveMaxParticipants < packageMinParticipants) {
      return 'Các ca đã chọn không còn đủ chỗ cho số vé tối thiểu của gói. Vui lòng chọn ca khác.';
    }
    if (participantCount < packageMinParticipants) {
      return `Số vé tối thiểu là ${packageMinParticipants}.`;
    }
    if (participantCount > effectiveMaxParticipants) {
      return `Các ca đã chọn chỉ còn đủ ${effectiveMaxParticipants} vé.`;
    }
    return null;
  };

  /** FR-045 "Đặt ngay": the cart is this one line, quoted and shown here; no cart page is involved. */
  const bookNow = async (selectionOverride) => {
    const problem = selectionProblem();
    if (problem) {
      setValidationErrors({ sessions: problem });
      document.getElementById('workshop-sessions-section')?.scrollIntoView({ behavior: 'smooth' });
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
    setBenefitSelection(next);
    invalidateQuote();
    if (hadQuote) bookNow(next);
  };

  const addToCart = () => {
    const problem = selectionProblem();
    if (problem) {
      setValidationErrors({ sessions: problem });
      document.getElementById('workshop-sessions-section')?.scrollIntoView({ behavior: 'smooth' });
      return;
    }
    setValidationErrors({});
    addCartLine({
      ...lineForCart(),
      packageName: pkg?.name || '',
      pricePerParticipantVnd: Number(pkg?.publicPrice || 0),
      locationId,
      locationName: locations.find(item => Number(item.id) === Number(locationId))?.name || '',
      sessionLabels: selectedSessionLabels(),
      minParticipants: packageMinParticipants,
      maxParticipants: effectiveMaxParticipants,
    });
    setCartMessage('Đã thêm gói này vào giỏ. Bạn có thể chọn thêm gói khác rồi thanh toán một lần.');
  };

  const confirmQuote = async () => {
    const errors = {};
    if (!contact.contactEmail || !contact.contactEmail.includes('@')) {
      errors.contactEmail = 'Email không hợp lệ (ví dụ: name@example.com).';
    }
    if (!contact.contactPhone || contact.contactPhone.replace(/\s+/g, '').length < 7) {
      errors.contactPhone = 'Số điện thoại phải có ít nhất 7 chữ số.';
    }
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
        contactEmail: contact.contactEmail,
        contactPhone: contact.contactPhone,
        idempotencyKey: confirmKey.current,
        authorization: authorization || undefined,
      });
      setGroup(created);
      setQuote(null);
      // The cart this booking came from is gone (FR-045).
      clearCart();
    } catch (error) {
      const lines = failingLinesOf(error);
      if (lines.length > 0) setFailingLines(lines);
      confirmKey.current = null;
      setConfirmError(describeBookingError(error));
    } finally {
      setConfirming(false);
    }
  };

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

  if (group) {
    return (
      <main className="public-page figma-public-page">
        <p className="eyebrow">{group.groupStatus === 'CONFIRMED' ? 'ĐẶT CHỖ THÀNH CÔNG' : 'GIỮ CHỖ THÀNH CÔNG'}</p>
        <h1 style={{ color: '#002967' }}>
          {group.groupStatus === 'CONFIRMED' ? 'Nhóm đặt chỗ đã được xác nhận' : 'Đã giữ chỗ cho nhóm đặt chỗ'}
        </h1>
        <GroupDetailCard group={group} authorization={null} allowRecipientChange={false} />
        <div className="actions" style={{ marginTop: '20px' }}>
          <a className="button outline" href="/workshop-booking/lookup">Xem đặt chỗ của tôi</a>
          <a className="button outline" href="/">Về trang chủ</a>
        </div>
      </main>
    );
  }

  return (
    <main className="public-page figma-public-page">
      <div style={{ marginBottom: '16px', display: 'flex', justifyContent: 'space-between', gap: '12px', flexWrap: 'wrap' }}>
        <a href={`/workshops/${pkg.packageId}`} className="text-link">← Xem lại chi tiết gói workshop</a>
        <Link className="text-link" to="/workshop-booking/cart">Giỏ workshop ({cartCount})</Link>
      </div>

      <p className="eyebrow">ĐẶT WORKSHOP</p>
      <h1>{pkg.name}</h1>
      <p className="lead">
        Chọn {requiredSessions} ca workshop và số người tham gia. Mỗi người là một vé riêng với QR, điểm danh và hóa đơn
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
              <h2>1. Chọn {requiredSessions} ca workshop</h2>
              <p className="muted" style={{ margin: '4px 0 0' }}>
                Gói này yêu cầu đúng {requiredSessions} ca (đã chọn: {selectedSessionRefs.length}/{requiredSessions})
              </p>
            </div>
          </div>

          {validationErrors.sessions && (
            <p role="alert" style={{ color: '#b00', fontSize: '14px', marginBottom: '12px' }}>{validationErrors.sessions}</p>
          )}

          <div className="filter-bar" style={{ margin: '0 0 16px', padding: '12px' }}>
            <label style={{ flex: '1 1 200px' }}>
              Chi nhánh
              <select
                value={locationId ?? ''}
                disabled={loadingLocations || locations.length === 0}
                onChange={event => { setLocationId(Number(event.target.value)); setSelectedSessionRefs([]); invalidateQuote(); }}
              >
                <option value="" disabled>{loadingLocations ? 'Đang tải chi nhánh...' : 'Chọn chi nhánh'}</option>
                {locations.map(location => <option key={location.id} value={location.id}>{location.name}</option>)}
              </select>
            </label>
            {locationsError && <p className="status" style={{ color: '#b00', borderColor: '#b00' }}>{locationsError}</p>}
            <label style={{ flex: '1 1 120px' }}>
              Từ ngày
              <input type="date" value={from} onChange={event => setFrom(event.target.value)} />
            </label>
            <label style={{ flex: '1 1 120px' }}>
              Đến ngày
              <input type="date" value={to} onChange={event => setTo(event.target.value)} />
            </label>
          </div>

          {loadingSessions && <div className="empty-state"><p>Đang tìm các ca workshop khả dụng...</p></div>}
          {sessionsError && !loadingSessions && (
            <div className="empty-state"><p className="status" style={{ color: '#b00' }}>{sessionsError}</p></div>
          )}
          {!loadingSessions && !sessionsError && sessions.length === 0 && (
            <div className="empty-state"><p>Không có ca nào phù hợp trong khoảng ngày này. Vui lòng chọn khoảng ngày khác.</p></div>
          )}

          {!loadingSessions && !sessionsError && sessions.length > 0 && (
            <>
              <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
                {pagedSessions.pageItems.map(session => {
                  const isSelected = selectedSessionRefs.includes(session.sessionReference);
                  const isSelectable = session.customerSelectable
                    && (isSelected || session.remainingCapacity >= packageMinParticipants);
                  return (
                    <div
                      key={session.sessionReference}
                      onClick={() => { if (isSelectable) toggleSessionSelection(session.sessionReference); }}
                      style={{
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'space-between',
                        padding: '14px 18px',
                        border: isSelected ? '2px solid #002967' : '1px solid #dcdfe6',
                        background: isSelected ? '#edf3fc' : isSelectable ? '#fff' : '#fafafa',
                        cursor: isSelectable ? 'pointer' : 'not-allowed',
                        opacity: isSelectable ? 1 : 0.5,
                      }}
                    >
                      <div style={{ display: 'flex', alignItems: 'center', gap: '14px' }}>
                        <input type="checkbox" checked={isSelected} disabled={!isSelectable} readOnly style={{ width: '18px', height: '18px' }} />
                        <strong>{formatSessionWindow(session.scheduledStart, session.scheduledEnd)}</strong>
                      </div>
                      <span className={isSelectable ? 'status ok' : 'status'} style={{ fontSize: '12px' }}>
                        {session.remainingCapacity > 0 ? `Còn ${session.remainingCapacity} chỗ` : 'Hết chỗ'}
                      </span>
                    </div>
                  );
                })}
              </div>
              <Paginator page={pagedSessions.page} pageCount={pagedSessions.pageCount} onChange={pagedSessions.setPage} label="Phân trang các ca workshop" />
            </>
          )}

          <div style={{ marginTop: '20px', paddingTop: '16px', borderTop: '1px solid #eee' }}>
            {!hasCompleteSessionSelection && (
              <p className="muted" style={{ margin: 0 }}>
                Chọn đủ {requiredSessions} ca workshop trước, sau đó mới chọn số người. Số vé tạm thời: 1.
              </p>
            )}
            {hasCompleteSessionSelection && !canChooseParticipantCount && (
              <p style={{ margin: 0, color: '#b00' }}>
                Các ca đã chọn không còn đủ {packageMinParticipants} vé tối thiểu của gói. Vui lòng chọn ca khác.
              </p>
            )}
            {canChooseParticipantCount && (
              <div className="participant-field">
                <span className="participant-label" id="participant-label">Số người tham gia (mỗi người một vé) *</span>
                <div className="participant-stepper" role="group" aria-labelledby="participant-label">
                  <button
                    type="button"
                    aria-label="Giảm số vé"
                    disabled={participantCount <= packageMinParticipants}
                    onClick={() => { setParticipantCount(Math.max(packageMinParticipants, participantCount - 1)); invalidateQuote(); }}
                  >
                    −
                  </button>
                  <input
                    type="number"
                    min={packageMinParticipants}
                    max={inputMaxParticipants}
                    value={participantCount}
                    aria-labelledby="participant-label"
                    onChange={event => {
                      const next = Number(event.target.value);
                      if (!Number.isFinite(next)) return;
                      setParticipantCount(Math.min(Math.max(packageMinParticipants, next), inputMaxParticipants));
                      invalidateQuote();
                    }}
                  />
                  <button
                    type="button"
                    aria-label="Tăng số vé"
                    disabled={participantCount >= inputMaxParticipants}
                    onClick={() => { setParticipantCount(Math.min(inputMaxParticipants, participantCount + 1)); invalidateQuote(); }}
                  >
                    +
                  </button>
                </div>
                <small className="participant-hint">
                  Tối đa {effectiveMaxParticipants} vé theo các ca đã chọn. Hệ thống kiểm tra lại khi giữ chỗ.
                </small>
              </div>
            )}
          </div>
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
          <div className="actions" style={{ marginTop: '12px', display: 'flex', gap: '10px', flexWrap: 'wrap' }}>
            <button type="button" className="button primary" onClick={() => bookNow()} disabled={quoting || confirming}>
              {quoting ? 'Đang báo giá...' : 'Đặt ngay'}
            </button>
            <button type="button" className="button outline" onClick={addToCart} disabled={quoting || confirming}>
              Thêm vào giỏ
            </button>
          </div>
          {cartMessage && (
            <p role="status" style={{ marginTop: '10px' }}>
              {cartMessage} <Link className="text-link" to="/workshop-booking/cart">Mở giỏ workshop</Link>
            </p>
          )}
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
          onCancel={invalidateQuote}
          confirming={confirming}
          confirmError={confirmError}
          validationErrors={validationErrors}
          failingLines={failingLines}
          title="Báo giá đặt ngay"
        />
      </fieldset>
    </main>
  );
}
