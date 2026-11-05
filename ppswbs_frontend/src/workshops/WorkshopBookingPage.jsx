import React, { useEffect, useState } from 'react';
import { getWorkshopPackage, listWorkshopLocations, listWorkshopSessions, previewWorkshopBookingQuote, createWorkshopBooking, WorkshopApiError } from '../api/workshops';
import { BenefitSelector } from '../features/loyalty-promotion';
import { useAuth } from '../shared/auth/AuthProvider';
import { useHttp } from '../shared/api/useHttp';
import { getEffectiveParticipantMax } from './bookingCapacity';
import './figmaPublicShell.css';

function formatDate(date) {
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, '0');
  const d = String(date.getDate()).padStart(2, '0');
  return `${y}-${m}-${d}`;
}

function formatSessionDateTime(isoString) {
  if (!isoString) return '';
  const d = new Date(isoString);
  return d.toLocaleDateString('vi-VN', {
    weekday: 'short',
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
  });
}

function formatSessionTime(isoStart, isoEnd) {
  if (!isoStart || !isoEnd) return '';
  const start = new Date(isoStart).toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' });
  const end = new Date(isoEnd).toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' });
  return `${start} - ${end}`;
}

export function WorkshopBookingPage({ packageId }) {
  const { status: authStatus, getIdToken } = useAuth();
  const http = useHttp();
  // Package facts
  const [pkg, setPkg] = useState(null);
  const [loadingPkg, setLoadingPkg] = useState(true);
  const [pkgError, setPkgError] = useState(null);

  // Booking mode is derived from the authenticated account. The visitor must
  // not be able to switch identity/authorization mode from the booking form.
  const [accountProfile, setAccountProfile] = useState(null);
  const [profileLoading, setProfileLoading] = useState(false);
  const [profileError, setProfileError] = useState(null);
  const bookingMode = authStatus === 'signed-out' || authStatus === 'unconfigured'
    ? 'GUEST'
    : accountProfile?.role === 'MEMBER' ? 'MEMBER' : null;

  // Contact form state
  const [contactFullName, setContactFullName] = useState('');
  const [contactEmail, setContactEmail] = useState('');
  const [contactPhone, setContactPhone] = useState('');
  const [participantCount, setParticipantCount] = useState(1);
  const [benefitSelection, setBenefitSelection] = useState({ choice: 'NONE', voucherCode: '', requestedPoints: '0' });
  const [memberQuote, setMemberQuote] = useState(null);
  const [quoteMatches, setQuoteMatches] = useState(false);
  const [benefitsAcknowledged, setBenefitsAcknowledged] = useState(false);
  const [isQuoteLoading, setIsQuoteLoading] = useState(false);

  // Location & Session discovery
  const today = new Date();
  const nextTwoWeeks = new Date();
  nextTwoWeeks.setDate(today.getDate() + 14);

  const [locationId, setLocationId] = useState(null);
  const [locations, setLocations] = useState([]);
  const [loadingLocations, setLoadingLocations] = useState(false);
  const [locationsError, setLocationsError] = useState(null);
  const [from, setFrom] = useState(formatDate(today));
  const [to, setTo] = useState(formatDate(nextTwoWeeks));

  const [sessions, setSessions] = useState([]);
  const [loadingSessions, setLoadingSessions] = useState(false);
  const [sessionsError, setSessionsError] = useState(null);

  // Selected sessions: array of opaque UUID strings
  const [selectedSessionRefs, setSelectedSessionRefs] = useState([]);

  // Submission state
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [formValidationErrors, setFormValidationErrors] = useState({});
  const [serverError, setServerError] = useState(null);
  const [bookingOutcome, setBookingOutcome] = useState(null);

  useEffect(() => {
    let active = true;
    if (authStatus !== 'signed-in') {
      setAccountProfile(null);
      setProfileLoading(false);
      setProfileError(null);
      // Do not retain authenticated contact data after sign-out.
      setContactFullName('');
      setContactEmail('');
      setContactPhone('');
      return () => { active = false; };
    }

    setProfileLoading(true);
    setProfileError(null);
    http('/api/v1/accounts/me/profile')
      .then(profile => {
        if (!active) return;
        setAccountProfile(profile || null);
        setContactFullName(profile?.fullName || '');
        setContactEmail(profile?.email || '');
        setContactPhone(profile?.phone || '');
      })
      .catch(error => {
        if (!active) return;
        // A profile lookup failure must not be hidden as a Member profile.
        // A signed-in account without a canonical Member role is blocked
        // rather than silently downgraded to the Guest journey.
        setAccountProfile(null);
        setProfileError(error?.message || 'Không thể tải hồ sơ Member.');
      })
      .finally(() => {
        if (active) setProfileLoading(false);
      });

    return () => { active = false; };
  }, [authStatus, http]);

  // Kho Voucher for the benefit picker; a failed load leaves the picker empty (the code can still be typed).
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

  const requiredSessions = Math.max(1, Number(pkg?.requiredSessionCount) || 1);
  const packageMinParticipants = Math.max(1, Number(pkg?.minParticipants) || 1);
  const effectiveMaxParticipants = getEffectiveParticipantMax(pkg, sessions, selectedSessionRefs);
  const hasCompleteSessionSelection = selectedSessionRefs.length >= requiredSessions;
  const canChooseParticipantCount = hasCompleteSessionSelection
    && effectiveMaxParticipants >= packageMinParticipants;
  const inputMaxParticipants = canChooseParticipantCount ? effectiveMaxParticipants : 1;

  useEffect(() => {
    if (!hasCompleteSessionSelection) {
      if (participantCount !== 1) {
        setParticipantCount(1);
        if (bookingMode === 'MEMBER') {
          invalidateMemberQuote();
        }
      }
      return;
    }
    if (effectiveMaxParticipants < packageMinParticipants) {
      return;
    }
    if (participantCount > effectiveMaxParticipants) {
      setParticipantCount(effectiveMaxParticipants);
      if (bookingMode === 'MEMBER') {
        invalidateMemberQuote();
      }
    }
  }, [hasCompleteSessionSelection, effectiveMaxParticipants, packageMinParticipants, participantCount, bookingMode]);

  const invalidateMemberQuote = () => {
    setQuoteMatches(false);
    setBenefitsAcknowledged(false);
  };

  // Load package facts
  useEffect(() => {
    if (!packageId) return;
    setLoadingPkg(true);
    setPkgError(null);
    getWorkshopPackage(packageId)
      .then(data => {
        setPkg(data);
        // Keep the customer-facing quantity at one until a session is chosen.
        // The package minimum is validated when the selected-session quantity
        // control becomes active and again by the backend during quote/hold.
        setParticipantCount(1);
      })
      .catch(err => {
        setPkgError(err?.detail || err?.message || 'Không tìm thấy gói workshop.');
      })
      .finally(() => {
        setLoadingPkg(false);
      });
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
        setLocationId(current => next.some(item => Number(item.id) === Number(current)) ? current : (next[0]?.id ?? null));
      })
      .catch(err => {
        if (!active) return;
        setLocations([]);
        setLocationId(null);
        setLocationsError(err?.detail || err?.message || 'Không thể tải danh sách chi nhánh.');
      })
      .finally(() => { if (active) setLoadingLocations(false); });
    return () => { active = false; };
  }, [packageId, pkg, from, to]);

  // Load sessions for selected location and date range
  const loadSessions = () => {
    if (!packageId || !locationId) return;
    setLoadingSessions(true);
    setSessionsError(null);
    listWorkshopSessions(packageId, { locationId, from, to })
      .then(data => {
        setSessions(Array.isArray(data) ? data : []);
      })
      .catch(err => {
        setSessionsError(err?.detail || err?.message || 'Không thể tải lịch ca workshop. Vui lòng thử lại.');
      })
      .finally(() => {
        setLoadingSessions(false);
      });
  };

  useEffect(() => {
    if (pkg && locationId) {
      loadSessions();
    } else {
      setSessions([]);
    }
  }, [pkg, locationId, from, to]);

  useEffect(() => {
    setSelectedSessionRefs([]);
  }, [locationId, from, to]);

  // Toggle session selection (opaque UUID)
  const toggleSessionSelection = (sessionReference) => {
    setSelectedSessionRefs(prev => {
      if (prev.includes(sessionReference)) {
        return prev.filter(ref => ref !== sessionReference);
      } else {
        const requiredCount = pkg?.requiredSessionCount || 1;
        if (requiredCount === 1) {
          return [sessionReference];
        }
        if (prev.length >= requiredCount) {
          // Replace the oldest or allow user to toggle off
          return [...prev.slice(1), sessionReference];
        }
        return [...prev, sessionReference];
      }
    });
    // Clear previous errors when user changes selection
    setServerError(null);
    if (bookingMode === 'MEMBER') invalidateMemberQuote();
  };

  const handleBenefitChange = (nextValue) => {
    setBenefitSelection(nextValue);
    invalidateMemberQuote();
    setServerError(null);
    setFormValidationErrors(prev => {
      const next = { ...prev };
      delete next.memberBenefits;
      return next;
    });
  };

  const recalculateMemberQuote = async () => {
    setServerError(null);
    setFormValidationErrors(prev => {
      const next = { ...prev };
      delete next.memberBenefits;
      return next;
    });
    if (selectedSessionRefs.length < requiredSessions) {
      setFormValidationErrors(prev => ({
        ...prev,
        sessions: `Vui lòng chọn đủ ${requiredSessions} ca workshop trước khi tính ưu đãi.`,
      }));
      document.getElementById('workshop-sessions-section')?.scrollIntoView({ behavior: 'smooth' });
      return;
    }

    setIsQuoteLoading(true);
    try {
      let idToken = null;
      if (authStatus === 'signed-in') {
        try {
          idToken = await getIdToken(false);
        } catch (_) {}
      }

      const normalizedBenefitSelection = {
        choice: benefitSelection.choice,
        voucherCode: benefitSelection.choice === 'VOUCHER' && benefitSelection.voucherCode?.trim()
          ? benefitSelection.voucherCode.trim()
          : null,
        requestedPoints: String(benefitSelection.requestedPoints ?? '0').trim() || '0',
      };

      let quote;
      try {
        quote = await previewWorkshopBookingQuote({
          packageId,
          sessionReferences: selectedSessionRefs,
          participantCount,
          benefitSelection: normalizedBenefitSelection,
          authorization: idToken ? `Bearer ${idToken}` : undefined,
        });
      } catch (apiErr) {
        // If 401 / unauthorized, try force refresh token once
        if ((apiErr?.status === 401 || apiErr?.code === 'WORKSHOP_401' || apiErr?.message?.includes('401')) && authStatus === 'signed-in') {
          const freshToken = await getIdToken(true);
          if (freshToken) {
            quote = await previewWorkshopBookingQuote({
              packageId,
              sessionReferences: selectedSessionRefs,
              participantCount,
              benefitSelection: normalizedBenefitSelection,
              authorization: `Bearer ${freshToken}`,
            });
          } else {
            throw apiErr;
          }
        } else {
          throw apiErr;
        }
      }

      setMemberQuote(quote);
      setQuoteMatches(true);
      setBenefitsAcknowledged(false);
      setServerError(null);
      setFormValidationErrors(prev => {
        const next = { ...prev };
        delete next.memberBenefits;
        return next;
      });
    } catch (err) {
      const errMsg = err?.detail || err?.message || 'Không thể tính lại ưu đãi workshop. Vui lòng thử lại.';
      const errCode = err?.code || (err?.status ? `WORKSHOP_${err.status}` : 'CLIENT_ERROR');
      setServerError({
        code: errCode,
        message: errMsg,
        correlationId: err?.correlationId || null,
      });
      setFormValidationErrors(prev => ({
        ...prev,
        memberBenefits: `Lỗi tính ưu đãi (${errCode}): ${errMsg}`,
      }));
      setQuoteMatches(false);
    } finally {
      setIsQuoteLoading(false);
    }
  };

  // Validate form before submission
  const validateForm = () => {
    const errors = {};
    if (!contactEmail || !contactEmail.includes('@') || !contactEmail.includes('.')) {
      errors.contactEmail = 'Email không hợp lệ (ví dụ: name@example.com).';
    }
    const cleanPhone = contactPhone.replace(/\s+/g, '');
    if (!cleanPhone || cleanPhone.length < 7) {
      errors.contactPhone = 'Số điện thoại phải có ít nhất 7 chữ số.';
    }
    if (selectedSessionRefs.length < requiredSessions) {
      errors.sessions = `Vui lòng chọn đủ ${requiredSessions} ca workshop phù hợp.`;
    }
    if (hasCompleteSessionSelection && (!participantCount || participantCount < packageMinParticipants)) {
      errors.participantCount = `Số vé tối thiểu là ${packageMinParticipants}.`;
    } else if (hasCompleteSessionSelection && effectiveMaxParticipants < packageMinParticipants) {
      errors.participantCount = 'Các ca đã chọn không còn đủ chỗ cho số vé tối thiểu của gói. Vui lòng chọn ca khác.';
    } else if (hasCompleteSessionSelection && participantCount > effectiveMaxParticipants) {
      errors.participantCount = `Các ca đã chọn chỉ còn đủ ${effectiveMaxParticipants} vé. Vui lòng giảm số vé hoặc chọn ca khác.`;
    }
    if (bookingMode === 'MEMBER') {
      if (!memberQuote || !quoteMatches) {
        errors.memberBenefits = 'Vui lòng bấm nút "Tính lại" ở mục 3. Ưu đãi & Điểm thưởng để tính giá trước khi giữ chỗ.';
      } else if (!benefitsAcknowledged) {
        errors.memberBenefits = 'Vui lòng tích chọn ô "Tôi đã xem tổng tiền và ưu đãi ở trên" trước khi xác nhận.';
      }
    }
    return errors;
  };

  // Handle booking submission
  const handleSubmit = async (e) => {
    e?.preventDefault();
    setServerError(null);
    if (bookingMode === null) {
      setServerError({
        code: profileError ? 'PROFILE_UNAVAILABLE' : 'ACCOUNT_ROLE_UNAVAILABLE',
        message: profileError
          ? 'Không thể xác minh vai trò tài khoản. Vui lòng tải lại hoặc đăng nhập lại.'
          : 'Tài khoản hiện tại chưa được phép đặt workshop theo luồng Member.',
        correlationId: null,
      });
      return;
    }
    const errors = validateForm();
    if (Object.keys(errors).length > 0) {
      setFormValidationErrors(errors);
      if (errors.contactEmail || errors.contactPhone) {
        document.getElementById('contact-info-section')?.scrollIntoView({ behavior: 'smooth' });
      } else if (errors.sessions) {
        document.getElementById('workshop-sessions-section')?.scrollIntoView({ behavior: 'smooth' });
      } else if (errors.memberBenefits) {
        document.getElementById('member-benefits-section')?.scrollIntoView({ behavior: 'smooth' });
      }
      return;
    }
    setFormValidationErrors({});
    setIsSubmitting(true);

    try {
      const idempotencyKey = typeof crypto !== 'undefined' && crypto.randomUUID
        ? crypto.randomUUID()
        : `idemp-${Date.now()}-${Math.random().toString(36).slice(2, 9)}`;
      let idToken = null;
      if (authStatus === 'signed-in') {
        try {
          idToken = await getIdToken(false);
        } catch (_) {}
        if (!idToken) {
          try {
            idToken = await getIdToken(true);
          } catch (_) {}
        }
      }

      let payload;
      if (bookingMode === 'MEMBER') {
        payload = {
          quoteRef: memberQuote.quoteRef,
          contactEmail: contactEmail.trim(),
          contactPhone: contactPhone.trim(),
          idempotencyKey,
          authorization: idToken ? `Bearer ${idToken}` : undefined,
        };
      } else {
        payload = {
          packageId: Number(packageId),
          sessionReferences: selectedSessionRefs,
          participantCount: Number(participantCount),
          contactEmail: contactEmail.trim(),
          contactPhone: contactPhone.trim(),
          idempotencyKey,
        };
      }

      const outcome = await createWorkshopBooking(payload);
      setBookingOutcome(outcome);
    } catch (err) {
      if (err instanceof WorkshopApiError) {
        let userFriendlyMsg = err.detail || err.message;
        if (err.code === 'INSUFFICIENT_CAPACITY') {
          userFriendlyMsg = 'Ca workshop bạn chọn hiện đã hết chỗ hoặc không đủ số lượng người đăng ký. Vui lòng chọn ca khác.';
        } else if (err.code === 'PACKAGE_SELECTION_INVALID') {
          userFriendlyMsg = 'Lựa chọn ca workshop không hợp lệ theo quy định của gói (số ca, địa điểm hoặc khoảng cách thời gian). Vui lòng điều chỉnh lại.';
        } else if (err.code === 'PACKAGE_NOT_PUBLISHED') {
          userFriendlyMsg = 'Gói workshop này hiện chưa được mở bán hoặc đã tạm dừng.';
        } else if (err.code === 'QUOTE_EXPIRED') {
          userFriendlyMsg = 'Báo giá hội viên đã hết hạn. Vui lòng tạo lại báo giá mới.';
        } else if (err.code === 'SALES_PAUSED') {
          userFriendlyMsg = 'Hệ thống đang tạm dừng nhận đăng ký cho ca này.';
        } else if (err.code === 'IDEMPOTENCY_CONFLICT') {
          userFriendlyMsg = 'Yêu cầu trùng lặp nhưng nội dung khác với lần gửi trước.';
        }

        setServerError({
          code: err.code,
          message: userFriendlyMsg,
          correlationId: err.correlationId,
        });
      } else {
        setServerError({
          code: 'CLIENT_ERROR',
          message: err?.message || 'Có lỗi xảy ra khi tạo đặt chỗ. Vui lòng kiểm tra lại thông tin.',
          correlationId: null,
        });
      }
    } finally {
      setIsSubmitting(false);
    }
  };

  if (loadingPkg) {
    return (
      <main className="public-page figma-public-page">
        <p className="eyebrow">WORKSHOP TẠI MLAND</p>
        <div className="empty-state">
          <p>Đang tải thông tin gói workshop...</p>
        </div>
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
            <a href="/workshops" className="button outline">
              ← Quay lại danh sách workshop
            </a>
          </div>
        </div>
      </main>
    );
  }

  // If booking succeeded, display BookingSummary confirmation state
  if (bookingOutcome) {
    return (
      <main className="public-page figma-public-page">
        <p className="eyebrow">GIỮ CHỖ THÀNH CÔNG</p>
        <h1 style={{ color: '#002967' }}>Đã tạo đơn đặt chỗ workshop</h1>

        <div className="workspace-card" style={{ marginTop: '24px', padding: '32px', border: '2px solid #002967' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', borderBottom: '1px solid #eee', paddingBottom: '16px' }}>
            <div>
              <span className="muted" style={{ fontSize: '13px' }}>Mã đặt chỗ (Booking Code)</span>
              <strong style={{ display: 'block', fontSize: '24px', color: '#002967', letterSpacing: '0.05em' }}>
                {bookingOutcome.bookingCode}
              </strong>
            </div>
            <div style={{ textAlign: 'right' }}>
              <span className="status ok" style={{ fontSize: '14px', fontWeight: 'bold' }}>
                {bookingOutcome.bookingStatus === 'PENDING_PAYMENT' ? 'CHỜ ĐẶT CỌC' : bookingOutcome.bookingStatus}
              </span>
              <span className="muted" style={{ display: 'block', fontSize: '12px', marginTop: '4px' }}>
                Thanh toán: {bookingOutcome.paymentStatus === 'UNPAID' ? 'Chưa thanh toán' : bookingOutcome.paymentStatus}
              </span>
            </div>
          </div>

          <div style={{ marginTop: '20px', lineHeight: '1.8' }}>
            <p>
              Gói workshop: <strong>{bookingOutcome.packageName || pkg.name}</strong>
            </p>
            <p>
              Số vé: <strong>{bookingOutcome.participantCount}</strong>
            </p>
            {bookingOutcome.holdExpiresAt && (
              <p style={{ color: '#b00', fontWeight: '500' }}>
                ⏳ Thời hạn giữ chỗ: <strong>{new Date(bookingOutcome.holdExpiresAt).toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' })} ({new Date(bookingOutcome.holdExpiresAt).toLocaleDateString('vi-VN')})</strong>
                <small style={{ display: 'block', color: '#595959', fontWeight: 'normal', fontSize: '13px' }}>
                  * Chỗ của bạn sẽ được giữ trong tối đa 10 phút. Vui lòng thanh toán đặt cọc 50% để xác nhận chính thức.
                </small>
              </p>
            )}

            {Array.isArray(bookingOutcome.sessions) && bookingOutcome.sessions.length > 0 && (
              <div style={{ marginTop: '16px', background: '#f9f9f9', padding: '16px', border: '1px solid #eee' }}>
                <h4 style={{ margin: '0 0 10px', fontSize: '15px' }}>Các ca workshop đã giữ chỗ:</h4>
                <ul style={{ margin: 0, paddingLeft: '20px' }}>
                  {bookingOutcome.sessions.map((s, idx) => (
                    <li key={s.sessionReference || idx}>
                      Buổi {s.ordinal || (idx + 1)}: {formatSessionDateTime(s.scheduledStart)} lúc {formatSessionTime(s.scheduledStart, s.scheduledEnd)}
                    </li>
                  ))}
                </ul>
              </div>
            )}
          </div>

          <div className="actions" style={{ marginTop: '28px' }}>
            <a className="button primary" href="/workshop-booking/lookup">
              Tra cứu thông tin đặt chỗ
            </a>
            <a className="button outline" href="/">
              Về trang chủ
            </a>
          </div>
        </div>
      </main>
    );
  }

  const hasMemberQuote = bookingMode === 'MEMBER' && memberQuote && quoteMatches;
  const estimatedTotal = hasMemberQuote
    ? Number(memberQuote.grossAmount || 0)
    : (Number(pkg.publicPrice) || 0) * (Number(participantCount) || 1);
  const totalDiscount = hasMemberQuote
    ? (Number(memberQuote.programDiscount || 0) + Number(memberQuote.pointDiscount || 0))
    : 0;
  const netTotal = hasMemberQuote
    ? Number(memberQuote.postPromotionAmount || 0)
    : estimatedTotal;
  const estimatedDeposit = hasMemberQuote
    ? Number(memberQuote.depositAmount || 0)
    : Math.ceil(estimatedTotal / 2);
  const estimatedRemaining = hasMemberQuote
    ? Number(memberQuote.remainingAmount || 0)
    : (estimatedTotal - estimatedDeposit);

  return (
    <main className="public-page figma-public-page">
      <div style={{ marginBottom: '16px' }}>
        <a href={`/workshops/${pkg.packageId}`} className="text-link">← Xem lại chi tiết gói workshop</a>
      </div>

      <p className="eyebrow">ĐẶT WORKSHOP</p>
      <h1>{pkg.name}</h1>
      <p className="lead">
        Hoàn tất thông tin liên hệ và chọn {requiredSessions} ca workshop phù hợp để giữ chỗ.
      </p>

      <nav className="figma-booking-steps" aria-label="Booking progress">
        <span className="active">1 Choose package</span>
        <span>2 Choose schedule</span>
        <span>3 Contact details</span>
        <span>4 Confirm</span>
      </nav>

      <div
        role="status"
        style={{
          marginTop: '24px',
          marginBottom: '24px',
          padding: '12px 16px',
          border: '1px solid #dcdfe6',
          background: '#f8fafc',
          color: '#002967',
        }}
      >
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

      {serverError && (
        <div className="workspace-card" style={{ border: '2px solid #b00', background: '#fff5f5', marginBottom: '24px', padding: '20px' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '8px' }}>
            <span className="status" style={{ color: '#b00', borderColor: '#b00', fontWeight: 'bold' }}>
              {serverError.code}
            </span>
            <strong style={{ color: '#b00' }}>Không thể hoàn tất giữ chỗ</strong>
          </div>
          <p style={{ margin: '8px 0', color: '#333' }}>{serverError.message}</p>
          {serverError.correlationId && (
            <p className="muted" style={{ fontSize: '12px', margin: 0 }}>
              Mã đối soát (Correlation ID): {serverError.correlationId}
            </p>
          )}
        </div>
      )}

      <form onSubmit={handleSubmit}>
        <fieldset disabled={authStatus === 'loading' || profileLoading || bookingMode === null} style={{ border: 0, padding: 0, margin: 0, minInlineSize: 0 }}>
        <div className="figma-booking-layout" style={{ display: 'grid', gridTemplateColumns: 'minmax(0, 1fr) minmax(0, 420px)', gap: '32px', alignItems: 'start' }}>
          {/* Left Column: Form & Session Selection */}
          <div>
            <section id="contact-info-section" className="workspace-card" style={{ scrollMarginTop: '32px' }}>
              <h2>1. Thông tin người đặt</h2>

              <div className="form-grid">
                <label>
                  Họ và tên
                  <input
                    type="text"
                    placeholder="Nguyễn Văn A"
                    value={contactFullName}
                    onChange={e => setContactFullName(e.target.value)}
                    disabled={isSubmitting}
                  />
                </label>

                <label>
                  Email nhận vé & xác nhận *
                  <input
                    type="email"
                    placeholder="email@example.com"
                    value={contactEmail}
                    onChange={e => setContactEmail(e.target.value)}
                    disabled={isSubmitting}
                    required
                  />
                  {bookingMode === 'MEMBER' && (
                    <small style={{ display: 'block', color: '#595959' }}>
                      Tự điền từ hồ sơ Member; bạn có thể sửa nếu đặt chỗ cho người khác.
                    </small>
                  )}
                  {formValidationErrors.contactEmail && (
                    <small style={{ color: '#b00' }}>{formValidationErrors.contactEmail}</small>
                  )}
                </label>

                <label>
                  Số điện thoại liên hệ *
                  <input
                    type="tel"
                    placeholder="0901234567"
                    value={contactPhone}
                    onChange={e => setContactPhone(e.target.value)}
                    disabled={isSubmitting}
                    required
                  />
                  {formValidationErrors.contactPhone && (
                    <small style={{ color: '#b00' }}>{formValidationErrors.contactPhone}</small>
                  )}
                </label>
              </div>

            </section>

            <section id="workshop-sessions-section" className="workspace-card" style={{ marginTop: '24px', scrollMarginTop: '32px' }}>
                <div className="card-head">
                  <div>
                    <h2>2. Chọn {requiredSessions} ca workshop</h2>
                    <p className="muted" style={{ margin: '4px 0 0' }}>
                      Gói này yêu cầu chọn đúng {requiredSessions} ca (Đã chọn: {selectedSessionRefs.length}/{requiredSessions})
                    </p>
                  </div>
                </div>

                {formValidationErrors.sessions && (
                  <p style={{ color: '#b00', fontSize: '14px', marginBottom: '12px' }}>
                    {formValidationErrors.sessions}
                  </p>
                )}

                {/* Location selector */}
                <div className="filter-bar" style={{ margin: '0 0 16px', padding: '12px' }}>
                  <label style={{ flex: '1 1 200px' }}>
                    Chi nhánh
                    <select
                      value={locationId ?? ''}
                      disabled={isSubmitting || loadingLocations || locations.length === 0}
                      onChange={e => {
                        setLocationId(Number(e.target.value));
                        setSelectedSessionRefs([]);
                        if (bookingMode === 'MEMBER') invalidateMemberQuote();
                      }}
                    >
                      <option value="" disabled>{loadingLocations ? 'Đang tải chi nhánh...' : 'Chọn chi nhánh'}</option>
                      {locations.map(loc => (
                        <option key={loc.id} value={loc.id}>{loc.name}</option>
                      ))}
                    </select>
                  </label>
                  {locationsError && <p className="status" style={{ color: '#b00', borderColor: '#b00' }}>{locationsError}</p>}

                  <label style={{ flex: '1 1 120px' }}>
                    Từ ngày
                    <input
                      type="date"
                      value={from}
                      onChange={e => {
                        setFrom(e.target.value);
                        if (bookingMode === 'MEMBER') invalidateMemberQuote();
                      }}
                      disabled={isSubmitting}
                    />
                  </label>

                  <label style={{ flex: '1 1 120px' }}>
                    Đến ngày
                    <input
                      type="date"
                      value={to}
                      onChange={e => {
                        setTo(e.target.value);
                        if (bookingMode === 'MEMBER') invalidateMemberQuote();
                      }}
                      disabled={isSubmitting}
                    />
                  </label>
                </div>

                {loadingSessions && (
                  <div className="empty-state">
                    <p>Đang tìm các ca workshop khả dụng...</p>
                  </div>
                )}

                {sessionsError && !loadingSessions && (
                  <div className="empty-state">
                    <p className="status" style={{ color: '#b00' }}>{sessionsError}</p>
                  </div>
                )}

                {!loadingSessions && !sessionsError && sessions.length === 0 && (
                  <div className="empty-state">
                    <p>Không có ca nào phù hợp trong khoảng ngày này. Vui lòng chọn khoảng ngày khác.</p>
                  </div>
                )}

                {!loadingSessions && !sessionsError && sessions.length > 0 && (
                  <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
                    {sessions.map(s => {
                      const isSelected = selectedSessionRefs.includes(s.sessionReference);
                      const isSelectable = s.customerSelectable && (
                        isSelected || s.remainingCapacity >= packageMinParticipants
                      );

                      return (
                        <div
                          key={s.sessionReference}
                          onClick={() => {
                            if (isSelectable && !isSubmitting) {
                              toggleSessionSelection(s.sessionReference);
                            }
                          }}
                          style={{
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'space-between',
                            padding: '14px 18px',
                            border: isSelected ? '2px solid #002967' : '1px solid #dcdfe6',
                            background: isSelected ? '#edf3fc' : isSelectable ? '#fff' : '#fafafa',
                            cursor: isSelectable && !isSubmitting ? 'pointer' : 'not-allowed',
                            opacity: isSelectable ? 1 : 0.5,
                            transition: 'all 0.15s ease',
                          }}
                        >
                          <div style={{ display: 'flex', alignItems: 'center', gap: '14px' }}>
                            <input
                              type="checkbox"
                              checked={isSelected}
                              disabled={!isSelectable || isSubmitting}
                              readOnly
                              style={{ width: '18px', height: '18px', cursor: 'pointer' }}
                            />
                            <div>
                              <strong>{formatSessionDateTime(s.scheduledStart)}</strong>
                              <span style={{ display: 'block', fontSize: '13px', color: '#595959', marginTop: '2px' }}>
                                Thời gian: {formatSessionTime(s.scheduledStart, s.scheduledEnd)}
                              </span>
                            </div>
                          </div>

                          <div style={{ textAlign: 'right' }}>
                            <span className={isSelectable ? 'status ok' : 'status'} style={{ fontSize: '12px' }}>
                              {s.remainingCapacity > 0 ? `Còn ${s.remainingCapacity} chỗ` : 'Hết chỗ'}
                            </span>
                            {isSelected && (
                              <span style={{ display: 'block', color: '#002967', fontWeight: 'bold', fontSize: '12px', marginTop: '4px' }}>
                                ✓ Đã chọn
                              </span>
                            )}
                          </div>
                        </div>
                      );
                    })}
                  </div>
                )}

                <div style={{ marginTop: '20px', paddingTop: '16px', borderTop: '1px solid #eee' }}>
                  {!hasCompleteSessionSelection && (
                    <p className="muted" style={{ margin: 0 }}>
                      Chọn đủ {requiredSessions} ca workshop trước, sau đó mới chọn số vé.
                      Số vé tạm thời: 1.
                    </p>
                  )}
                  {hasCompleteSessionSelection && !canChooseParticipantCount && (
                    <p style={{ margin: 0, color: '#b00' }}>
                      Các ca đã chọn không còn đủ {packageMinParticipants} vé tối thiểu của gói.
                      Vui lòng chọn ca khác.
                    </p>
                  )}
                  {canChooseParticipantCount && <label>
                    Số vé / số người tham gia *
                    <input
                      type="number"
                      min={packageMinParticipants}
                      max={inputMaxParticipants}
                      value={participantCount}
                      onChange={e => {
                        const nextValue = Number(e.target.value);
                        if (!Number.isFinite(nextValue) || !hasCompleteSessionSelection) return;
                        setParticipantCount(Math.min(
                          Math.max(packageMinParticipants, nextValue),
                          inputMaxParticipants,
                        ));
                        if (bookingMode === 'MEMBER') invalidateMemberQuote();
                      }}
                      disabled={isSubmitting || !hasCompleteSessionSelection}
                    />
                    <small style={{ display: 'block', color: '#595959' }}>
                      Tối đa theo các ca đã chọn: {effectiveMaxParticipants} vé.
                      {' '}Hệ thống sẽ kiểm tra lại khi giữ chỗ.
                    </small>
                    {formValidationErrors.participantCount && (
                      <small style={{ color: '#b00' }}>{formValidationErrors.participantCount}</small>
                    )}
                  </label>}
                </div>
              </section>

            {bookingMode === 'MEMBER' && (
              <section
                id="member-benefits-section"
                className="workspace-card"
                style={{
                  marginTop: '24px',
                  scrollMarginTop: '32px',
                  border: formValidationErrors.memberBenefits ? '2px solid #b00' : undefined,
                  background: formValidationErrors.memberBenefits ? '#fffdfd' : undefined,
                  transition: 'all 0.3s ease',
                }}
              >
                <div className="card-head" style={{ marginBottom: '16px' }}>
                  <div>
                    <h2 style={{ margin: 0 }}>3. Ưu đãi &amp; Điểm thưởng</h2>
                    <p className="muted" style={{ margin: '4px 0 0', fontSize: '13px' }}>
                      Áp dụng voucher và điểm tích lũy của tài khoản Member.
                    </p>
                  </div>
                </div>

                {formValidationErrors.memberBenefits && (
                  <div
                    role="alert"
                    style={{
                      padding: '12px 16px',
                      background: '#fff2f0',
                      border: '1px solid #ffccc7',
                      borderRadius: '4px',
                      marginBottom: '16px',
                      color: '#cf1322',
                      display: 'flex',
                      alignItems: 'center',
                      gap: '8px',
                      fontWeight: 'bold',
                      fontSize: '14px',
                    }}
                  >
                    <span>⚠️</span>
                    <span>{formValidationErrors.memberBenefits}</span>
                  </div>
                )}

                {isQuoteLoading && (
                  <div style={{ padding: '10px 14px', background: '#f0f5ff', borderLeft: '4px solid #2f54eb', marginBottom: '16px', fontSize: '14px', color: '#1d39c4' }}>
                    ⏳ Đang kết nối máy chủ để tính toán ưu đãi và điểm thưởng...
                  </div>
                )}

                {(!memberQuote || !quoteMatches) && !isQuoteLoading && (
                  <div style={{ padding: '10px 14px', background: '#eef4ff', borderLeft: '4px solid #002967', marginBottom: '16px', fontSize: '14px', color: '#002967' }}>
                    <strong>Bước 1:</strong> Chọn hình thức ưu đãi (nếu có) và bấm nút <strong>&quot;Tính lại&quot;</strong> để hệ thống tính giá &amp; số tiền đặt cọc.
                  </div>
                )}

                {quoteMatches && !benefitsAcknowledged && !isQuoteLoading && (
                  <div style={{ padding: '10px 14px', background: '#fffbe6', borderLeft: '4px solid #faad14', marginBottom: '16px', fontSize: '14px', color: '#874d00' }}>
                    <strong>Bước 2:</strong> Vui lòng xem bảng phân bổ tiền bên dưới và tích chọn ô <strong>&quot;Tôi đã xem tổng tiền và ưu đãi ở trên&quot;</strong> để hoàn tất điều kiện giữ chỗ.
                  </div>
                )}

                <BenefitSelector
                  context="workshop"
                  value={benefitSelection}
                  onChange={handleBenefitChange}
                  quote={memberQuote && memberQuote.reasonDetails
                    ? { ...memberQuote, reasons: memberQuote.reasonDetails.map(detail => detail.message) }
                    : memberQuote}
                  savedVouchers={savedVouchers}
                  quoteMatches={quoteMatches}
                  acknowledged={benefitsAcknowledged}
                  onAcknowledge={val => {
                    setBenefitsAcknowledged(val);
                    if (val) {
                      setFormValidationErrors(prev => {
                        const next = { ...prev };
                        delete next.memberBenefits;
                        return next;
                      });
                    }
                  }}
                  onRecalculate={recalculateMemberQuote}
                  onConfirm={() => handleSubmit()}
                  pending={profileLoading || isSubmitting || isQuoteLoading}
                  showConfirm={false}
                  hideTitle={true}
                />
              </section>
            )}
          </div>

          {/* Right Column: Order Summary & Confirmation CTA */}
          <aside className="workspace-card" style={{ position: 'sticky', top: '24px' }}>
            <h2>Tóm tắt đặt chỗ</h2>

            <div style={{ borderBottom: '1px solid #eee', paddingBottom: '16px', marginBottom: '16px', lineHeight: '1.8' }}>
              <p style={{ margin: 0 }}>Gói: <strong>{pkg.name}</strong></p>
              <p style={{ margin: 0 }}>Số vé: <strong>{participantCount}</strong></p>
              <p style={{ margin: 0 }}>Đơn giá: <strong>{Number(pkg.publicPrice).toLocaleString('vi-VN')} ₫ / vé</strong></p>
              <p style={{ margin: 0 }}>
                Ca đã chọn: <strong>{selectedSessionRefs.length} / {requiredSessions} ca</strong>
              </p>
            </div>

            <div style={{ lineHeight: '2' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                <span>Tổng giá trị gói</span>
                <strong>{estimatedTotal.toLocaleString('vi-VN')} ₫</strong>
              </div>
              {hasMemberQuote && totalDiscount > 0 && (
                <div style={{ display: 'flex', justifyContent: 'space-between', color: '#2b8a3e', fontSize: '14px' }}>
                  <span>Ưu đãi &amp; Điểm giảm trừ</span>
                  <span>−{totalDiscount.toLocaleString('vi-VN')} ₫</span>
                </div>
              )}
              {hasMemberQuote && totalDiscount > 0 && (
                <div style={{ display: 'flex', justifyContent: 'space-between', fontWeight: 'bold' }}>
                  <span>Tổng sau ưu đãi</span>
                  <span>{netTotal.toLocaleString('vi-VN')} ₫</span>
                </div>
              )}
              <div style={{ display: 'flex', justifyContent: 'space-between', color: '#002967', fontSize: '16px', fontWeight: 'bold', borderTop: '1px solid #eee', paddingTop: '8px', marginTop: '8px' }}>
                <span>Tiền đặt cọc (50%)</span>
                <span>{estimatedDeposit.toLocaleString('vi-VN')} ₫</span>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between', color: '#595959', fontSize: '13px' }}>
                <span>Còn lại tại chi nhánh</span>
                <span>{estimatedRemaining.toLocaleString('vi-VN')} ₫</span>
              </div>
            </div>

            {bookingMode === 'MEMBER' && !hasMemberQuote && (
              <p className="muted" style={{ fontSize: '12px', margin: '8px 0 0', lineHeight: '1.4' }}>
                * Giá tạm tính trước ưu đãi. Vui lòng bấm &quot;Tính lại&quot; ở mục 3 để áp dụng ưu đãi Member.
              </p>
            )}

            <div style={{ marginTop: '24px' }}>
              <button
                type="submit"
                className="button primary"
                disabled={authStatus === 'loading' || profileLoading || bookingMode === null || isSubmitting}
                style={{
                  width: '100%',
                  minHeight: '48px',
                  fontSize: '16px',
                  cursor: authStatus === 'loading' || profileLoading || bookingMode === null || isSubmitting ? 'wait' : 'pointer',
                }}
              >
                {profileLoading ? 'Đang tải hồ sơ...' : isSubmitting ? 'Đang xử lý giữ chỗ...' : 'Xác nhận đặt chỗ & Giữ chỗ'}
              </button>

              {formValidationErrors.memberBenefits && (
                <div
                  style={{
                    marginTop: '12px',
                    padding: '10px 12px',
                    background: '#fff2f0',
                    border: '1px solid #ffccc7',
                    borderRadius: '4px',
                    color: '#cf1322',
                    fontSize: '13px',
                    lineHeight: '1.4',
                  }}
                >
                  <strong>⚠️ Chưa thể xác nhận:</strong>
                  <p style={{ margin: '4px 0 0' }}>{formValidationErrors.memberBenefits}</p>
                  <button
                    type="button"
                    onClick={() => document.getElementById('member-benefits-section')?.scrollIntoView({ behavior: 'smooth' })}
                    style={{
                      marginTop: '6px',
                      background: 'none',
                      border: 'none',
                      color: '#002967',
                      textDecoration: 'underline',
                      cursor: 'pointer',
                      padding: 0,
                      fontSize: '12px',
                      fontWeight: 'bold',
                    }}
                  >
                    ↓ Cuộn đến mục Ưu đãi
                  </button>
                </div>
              )}

              {formValidationErrors.sessions && (
                <div
                  style={{
                    marginTop: '12px',
                    padding: '10px 12px',
                    background: '#fff2f0',
                    border: '1px solid #ffccc7',
                    borderRadius: '4px',
                    color: '#cf1322',
                    fontSize: '13px',
                    lineHeight: '1.4',
                  }}
                >
                  <strong>⚠️ Chưa chọn đủ ca:</strong>
                  <p style={{ margin: '4px 0 0' }}>{formValidationErrors.sessions}</p>
                  <button
                    type="button"
                    onClick={() => document.getElementById('workshop-sessions-section')?.scrollIntoView({ behavior: 'smooth' })}
                    style={{
                      marginTop: '6px',
                      background: 'none',
                      border: 'none',
                      color: '#002967',
                      textDecoration: 'underline',
                      cursor: 'pointer',
                      padding: 0,
                      fontSize: '12px',
                      fontWeight: 'bold',
                    }}
                  >
                    ↓ Cuộn đến mục Chọn ca
                  </button>
                </div>
              )}
            </div>

            <p className="muted" style={{ fontSize: '12px', marginTop: '16px', lineHeight: '1.5' }}>
              * Khi bấm xác nhận, hệ thống sẽ tiến hành giữ chỗ tức thời qua Workshop Operations. Đơn đặt chỗ sẽ có hiệu lực giữ chỗ trong 10 phút để hoàn tất thủ tục đặt cọc.
            </p>
          </aside>
        </div>
        </fieldset>
      </form>
    </main>
  );
}

