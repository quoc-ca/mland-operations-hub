import React, { useEffect, useState } from 'react';
import { Link } from 'react-router';
import { createBookingGroup, quoteWorkshopCart } from '../api/workshops';
import { CartBenefitPicker } from './CartBenefitPicker';
import { CartQuotePanel } from './CartQuotePanel';
import { GroupDetailCard } from './GroupDetailCard';
import {
  cartQuoteLines,
  clearCart,
  readCart,
  removeCartLine,
  updateCartLineParticipants,
} from './cartStorage';
import { failingLinesOf, vnd } from './groupDisplay';
import { useBookingIdentity } from './useBookingIdentity';
import { useHttp } from '../shared/api/useHttp';
import './figmaPublicShell.css';

const CART_ERROR_MESSAGE = {
  CART_HOLD_REJECTED: 'Giỏ chưa được giữ chỗ vì có dòng không còn đủ chỗ. Hệ thống không giữ chỗ nào; vui lòng sửa giỏ rồi báo giá lại.',
  INSUFFICIENT_CAPACITY: 'Có dòng trong giỏ không còn đủ chỗ. Hệ thống không giữ chỗ nào.',
  QUOTE_EXPIRED: 'Báo giá đã hết hạn. Vui lòng báo giá lại.',
  CART_BENEFITS_NEED_A_MEMBER: 'Voucher và điểm chỉ áp dụng cho tài khoản Member đang đăng nhập.',
};

const describe = error => ({
  code: error?.code || 'CLIENT_ERROR',
  message: CART_ERROR_MESSAGE[error?.code] || error?.detail || error?.message || 'Không thể hoàn tất yêu cầu.',
  correlationId: error?.correlationId || null,
});

/**
 * The workshop cart (003.1 FR-030, FR-031, FR-045). The unquoted cart lives in this browser; this page quotes it with
 * `POST /workshop-cart-quotes` and confirms it with `POST /workshop-booking-groups` — the same two calls as "Đặt ngay".
 * A capacity failure names the failing lines, holds nothing and leaves the cart editable.
 */
export function WorkshopCartPage() {
  const { authStatus, mode: bookingMode, profile, profileLoading, profileError, memberAuthorization } = useBookingIdentity();
  const http = useHttp();
  const [lines, setLines] = useState(() => readCart());
  const [benefitSelection, setBenefitSelection] = useState({ choice: 'NONE', voucherCode: '', requestedPoints: '0' });
  const [contact, setContact] = useState({ contactEmail: '', contactPhone: '' });
  const [quote, setQuote] = useState(null);
  const [quoting, setQuoting] = useState(false);
  const [quoteError, setQuoteError] = useState(null);
  const [confirming, setConfirming] = useState(false);
  const [confirmError, setConfirmError] = useState(null);
  const [failingLines, setFailingLines] = useState([]);
  const [validationErrors, setValidationErrors] = useState({});
  const [group, setGroup] = useState(null);
  const [confirmKey, setConfirmKey] = useState(null);

  useEffect(() => {
    if (authStatus !== 'signed-in') {
      setContact({ contactEmail: '', contactPhone: '' });
      return;
    }
    if (profile) setContact({ contactEmail: profile.email || '', contactPhone: profile.phone || '' });
  }, [authStatus, profile]);

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
        })).filter(item => item.code));
      })
      .catch(() => { if (active) setSavedVouchers([]); });
    // The cart holds several packages, so the offers are not filtered by one package here.
    http('/api/v1/promotions?scope=WORKSHOP&limit=50')
      .then(result => {
        if (!active) return;
        setWorkshopOffers((result?.items || [])
          .filter(item => item.kind === 'VOUCHER' && item.code)
          .map(item => ({ code: item.code, name: item.name || item.code, discount: item.discount })));
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

  const changeParticipants = (lineId, participantCount) => {
    setLines(updateCartLineParticipants(lineId, participantCount));
    invalidateQuote();
  };

  const remove = lineId => {
    setLines(removeCartLine(lineId));
    invalidateQuote();
  };

  const requestQuote = async (selectionOverride) => {
    if (lines.length === 0) return;
    setQuoting(true);
    setQuoteError(null);
    setConfirmError(null);
    setFailingLines([]);
    try {
      const authorization = await memberAuthorization();
      const result = await quoteWorkshopCart({
        lines: cartQuoteLines(lines),
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
    setBenefitSelection(next);
    invalidateQuote();
    if (hadQuote) requestQuote(next);
  };

  const confirm = async () => {
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
      const key = confirmKey
        || globalThis.crypto?.randomUUID?.()
        || `group-create-${Date.now()}-${Math.random().toString(16).slice(2)}`;
      setConfirmKey(key);
      const authorization = await memberAuthorization();
      const created = await createBookingGroup({
        quoteRef: quote.quoteRef,
        contactEmail: contact.contactEmail,
        contactPhone: contact.contactPhone,
        idempotencyKey: key,
        authorization: authorization || undefined,
      });
      setGroup(created);
      setQuote(null);
      clearCart();
      setLines([]);
    } catch (error) {
      const failing = failingLinesOf(error);
      if (failing.length > 0) setFailingLines(failing);
      setConfirmKey(null);
      setConfirmError(describe(error));
    } finally {
      setConfirming(false);
    }
  };

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
          <a className="button outline" href="/workshops">Đặt thêm workshop</a>
        </div>
      </main>
    );
  }

  return (
    <main className="public-page figma-public-page">
      <p className="eyebrow">GIỎ WORKSHOP</p>
      <h1>Giỏ đặt workshop</h1>
      <p className="lead">
        Mỗi dòng là một gói workshop với số người tham gia; mỗi người là một vé riêng. Cả giỏ được giữ chỗ và thanh toán
        một lần.
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

      {lines.length === 0 ? (
        <div className="empty-state">
          <p>Giỏ workshop của bạn đang trống.</p>
          <Link className="button primary" to="/workshops">Xem các gói workshop</Link>
        </div>
      ) : (
        <section className="workspace-card" aria-label="Các dòng trong giỏ">
          <ul style={{ listStyle: 'none', margin: 0, padding: 0, display: 'grid', gap: '12px' }}>
            {lines.map((line, index) => {
              const failed = failingLines.includes(index);
              return (
                <li
                  key={line.lineId}
                  style={{ border: failed ? '2px solid #b00' : '1px solid #e3e3e3', padding: '14px 16px', background: '#fff' }}
                >
                  <div style={{ display: 'flex', justifyContent: 'space-between', gap: '12px', flexWrap: 'wrap' }}>
                    <div>
                      <strong style={{ color: '#002967' }}>{line.packageName || `Gói #${line.packageId}`}</strong>
                      <p className="muted" style={{ margin: '4px 0 0', fontSize: '13px' }}>
                        Dòng {index + 1}
                        {line.locationName ? ` · ${line.locationName}` : ''}
                        {line.pricePerParticipantVnd ? ` · ${vnd(line.pricePerParticipantVnd)} / vé` : ''}
                      </p>
                      {line.sessionLabels?.length > 0 && (
                        <ul className="muted" style={{ margin: '6px 0 0', paddingLeft: '18px', fontSize: '12px' }}>
                          {line.sessionLabels.map((label, position) => (
                            <li key={`${line.lineId}-${position}`}>Buổi {position + 1}: {label}</li>
                          ))}
                        </ul>
                      )}
                    </div>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                      <div className="participant-stepper" role="group" aria-label={`Số người của dòng ${index + 1}`}>
                        <button
                          type="button"
                          aria-label={`Giảm số vé dòng ${index + 1}`}
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
                          aria-label={`Số vé dòng ${index + 1}`}
                          onChange={event => changeParticipants(line.lineId, Number(event.target.value))}
                          disabled={quoting || confirming}
                        />
                        <button
                          type="button"
                          aria-label={`Tăng số vé dòng ${index + 1}`}
                          disabled={Boolean(line.maxParticipants) && line.participantCount >= line.maxParticipants}
                          onClick={() => changeParticipants(line.lineId, line.participantCount + 1)}
                        >
                          +
                        </button>
                      </div>
                      <button type="button" className="button outline small" onClick={() => remove(line.lineId)} disabled={quoting || confirming}>
                        Xóa dòng
                      </button>
                    </div>
                  </div>
                  {failed && (
                    <p role="alert" style={{ color: '#b00', margin: '8px 0 0', fontSize: '13px' }}>
                      Dòng này không còn đủ chỗ. Hãy giảm số người hoặc chọn ca khác.
                    </p>
                  )}
                </li>
              );
            })}
          </ul>

          <div className="actions" style={{ marginTop: '16px', display: 'flex', gap: '10px', flexWrap: 'wrap' }}>
            <button
              type="button"
              className="button primary"
              onClick={() => requestQuote()}
              disabled={quoting || confirming || bookingMode === null || profileLoading}
            >
              {quoting ? 'Đang báo giá...' : 'Báo giá giỏ'}
            </button>
            <Link className="button outline" to="/workshops">Thêm gói khác</Link>
          </div>

          {quoteError && (
            <div role="alert" style={{ marginTop: '12px', color: '#b00' }}>
              <strong>{quoteError.code}</strong>
              <p style={{ margin: '4px 0 0' }}>{quoteError.message}</p>
            </div>
          )}
        </section>
      )}

      {bookingMode === 'MEMBER' && lines.length > 0 && (
        <section className="workspace-card" style={{ marginTop: '24px' }} aria-label="Ưu đãi và điểm thưởng">
          <div className="card-head" style={{ marginBottom: '16px' }}>
            <div>
              <h2 style={{ margin: 0 }}>Ưu đãi &amp; Điểm thưởng</h2>
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

      <CartQuotePanel
        quote={quote}
        contact={contact}
        onContactChange={setContact}
        onConfirm={confirm}
        onCancel={invalidateQuote}
        confirming={confirming}
        confirmError={confirmError}
        validationErrors={validationErrors}
        failingLines={failingLines}
        title="Báo giá giỏ workshop"
      />
    </main>
  );
}
