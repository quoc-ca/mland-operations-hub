import React, { useEffect, useState } from 'react';
import { getWorkshopPackage, listWorkshopSessions, previewWorkshopBookingQuote, createWorkshopBooking, WorkshopApiError } from '../api/workshops';
import { BenefitSelector } from '../features/loyaltypromotion/checkout/BenefitSelector';
import { useAuth } from '../shared/auth/AuthProvider';

const AVAILABLE_LOCATIONS = [
  { id: 1, name: 'MLAND Quận 1 — 123 Đồng Khởi, Bến Nghé, Quận 1, TP.HCM' },
  { id: 2, name: 'MLAND Thủ Đức — 45 Võ Văn Ngân, Linh Chiểu, TP. Thủ Đức' },
];

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
  // Package facts
  const [pkg, setPkg] = useState(null);
  const [loadingPkg, setLoadingPkg] = useState(true);
  const [pkgError, setPkgError] = useState(null);

  // Booking mode: GUEST or MEMBER
  const [bookingMode, setBookingMode] = useState('GUEST');

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

  const [locationId, setLocationId] = useState(1);
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
        if (data.minParticipants) {
          setParticipantCount(Math.max(1, data.minParticipants));
        }
      })
      .catch(err => {
        setPkgError(err?.detail || err?.message || 'Không tìm thấy gói workshop.');
      })
      .finally(() => {
        setLoadingPkg(false);
      });
  }, [packageId]);

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
    if (pkg) {
      loadSessions();
    }
  }, [pkg, locationId, from, to]);

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
  };

  const recalculateMemberQuote = async () => {
    setServerError(null);
    setFormValidationErrors({});
    if (selectedSessionRefs.length === 0) {
      setFormValidationErrors({ sessions: 'Vui lòng chọn đủ ca workshop trước khi tính ưu đãi.' });
      return;
    }

    setIsQuoteLoading(true);
    try {
      const idToken = authStatus === 'signed-in' ? await getIdToken() : null;
      const quote = await previewWorkshopBookingQuote({
        packageId,
        sessionReferences: selectedSessionRefs,
        participantCount,
        benefitSelection,
        authorization: idToken ? `Bearer ${idToken}` : undefined,
      });
      setMemberQuote(quote);
      setQuoteMatches(true);
      setBenefitsAcknowledged(false);
    } catch (err) {
      if (err instanceof WorkshopApiError) {
        setServerError({
          code: err.code,
          message: err.detail || err.message,
          correlationId: err.correlationId,
        });
      } else {
        setServerError({
          code: 'CLIENT_ERROR',
          message: err?.message || 'Không thể tính lại ưu đãi workshop. Vui lòng thử lại.',
          correlationId: null,
        });
      }
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
    if (!participantCount || participantCount < 1) {
      errors.participantCount = 'Số lượng người tham gia tối thiểu là 1.';
    }
    if (bookingMode === 'MEMBER' && (!memberQuote || !quoteMatches || !benefitsAcknowledged)) {
      errors.memberBenefits = 'Vui lòng tính lại và xác nhận ưu đãi trước khi đặt workshop.';
    }
    if (selectedSessionRefs.length === 0) {
      errors.sessions = `Vui lòng chọn ít nhất 1 ca workshop phù hợp.`;
    }
    return errors;
  };

  // Handle booking submission
  const handleSubmit = async (e) => {
    e?.preventDefault();
    setServerError(null);
    const errors = validateForm();
    if (Object.keys(errors).length > 0) {
      setFormValidationErrors(errors);
      return;
    }
    setFormValidationErrors({});
    setIsSubmitting(true);

    try {
      const idempotencyKey = typeof crypto !== 'undefined' && crypto.randomUUID
        ? crypto.randomUUID()
        : `idemp-${Date.now()}-${Math.random().toString(36).slice(2, 9)}`;
      const idToken = authStatus === 'signed-in' ? await getIdToken() : null;

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
      <main className="container public-page">
        <p className="eyebrow">WORKSHOP TẠI MLAND</p>
        <div className="empty-state">
          <p>Đang tải thông tin gói workshop...</p>
        </div>
      </main>
    );
  }

  if (pkgError || !pkg) {
    return (
      <main className="container public-page">
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
      <main className="container public-page">
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
              Số người tham gia: <strong>{bookingOutcome.participantCount} người</strong>
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

  const estimatedTotal = (Number(pkg.publicPrice) || 0) * (Number(participantCount) || 1);
  const estimatedDeposit = Math.ceil(estimatedTotal / 2);
  const requiredSessions = pkg.requiredSessionCount || 1;

  return (
    <main className="container public-page">
      <div style={{ marginBottom: '16px' }}>
        <a href={`/workshops/${pkg.packageId}`} className="text-link">← Xem lại chi tiết gói workshop</a>
      </div>

      <p className="eyebrow">ĐẶT WORKSHOP</p>
      <h1>{pkg.name}</h1>
      <p className="lead">
        Hoàn tất thông tin liên hệ và chọn {requiredSessions} ca workshop phù hợp để giữ chỗ.
      </p>

      {/* Mode selection: Guest vs Member */}
      <div style={{ display: 'flex', gap: '8px', marginTop: '24px', marginBottom: '24px' }}>
        <button
          type="button"
          className={`button ${bookingMode === 'GUEST' ? 'primary' : 'outline'}`}
          onClick={() => {
            setBookingMode('GUEST');
            setServerError(null);
            setFormValidationErrors({});
          }}
        >
          Khách vãng lai (Guest)
        </button>
        <button
          type="button"
          className={`button ${bookingMode === 'MEMBER' ? 'primary' : 'outline'}`}
          onClick={() => {
            setBookingMode('MEMBER');
            setServerError(null);
            setFormValidationErrors({});
            invalidateMemberQuote();
          }}
        >
          Hội viên MLAND (Member)
        </button>
      </div>

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
        <div style={{ display: 'grid', gridTemplateColumns: 'minmax(0, 1.2fr) minmax(0, 1fr)', gap: '32px', alignItems: 'start' }}>
          {/* Left Column: Form & Session Selection */}
          <div>
            <section className="workspace-card">
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
                  Số người tham gia *
                  <input
                    type="number"
                    min={pkg.minParticipants || 1}
                    max={pkg.maxParticipants || 10}
                    value={participantCount}
                    onChange={e => {
                      setParticipantCount(Math.max(1, Number(e.target.value)));
                      if (bookingMode === 'MEMBER') invalidateMemberQuote();
                    }}
                    disabled={isSubmitting}
                  />
                  {formValidationErrors.participantCount && (
                    <small style={{ color: '#b00' }}>{formValidationErrors.participantCount}</small>
                  )}
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

            <section className="workspace-card" style={{ marginTop: '24px' }}>
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
                      value={locationId}
                      onChange={e => {
                        setLocationId(Number(e.target.value));
                        setSelectedSessionRefs([]);
                        if (bookingMode === 'MEMBER') invalidateMemberQuote();
                      }}
                      disabled={isSubmitting}
                    >
                      {AVAILABLE_LOCATIONS.map(loc => (
                        <option key={loc.id} value={loc.id}>{loc.name}</option>
                      ))}
                    </select>
                  </label>

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
                      const isSelectable = s.customerSelectable && s.remainingCapacity >= participantCount;

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
              </section>

            {bookingMode === 'MEMBER' && (
              <div style={{ marginTop: '24px' }}>
                <BenefitSelector
                  context="workshop"
                  value={benefitSelection}
                  onChange={handleBenefitChange}
                  quote={memberQuote}
                  quoteMatches={quoteMatches}
                  acknowledged={benefitsAcknowledged}
                  onAcknowledge={setBenefitsAcknowledged}
                  onRecalculate={recalculateMemberQuote}
                  onConfirm={() => handleSubmit()}
                  pending={isSubmitting || isQuoteLoading}
                />
                {formValidationErrors.memberBenefits && (
                  <small style={{ display: 'block', color: '#b00', marginTop: '8px' }}>
                    {formValidationErrors.memberBenefits}
                  </small>
                )}
              </div>
            )}
          </div>

          {/* Right Column: Order Summary & Confirmation CTA */}
          <aside className="workspace-card" style={{ position: 'sticky', top: '24px' }}>
            <h2>Tóm tắt đặt chỗ</h2>

            <div style={{ borderBottom: '1px solid #eee', paddingBottom: '16px', marginBottom: '16px', lineHeight: '1.8' }}>
              <p style={{ margin: 0 }}>Gói: <strong>{pkg.name}</strong></p>
              <p style={{ margin: 0 }}>Số người: <strong>{participantCount}</strong></p>
              <p style={{ margin: 0 }}>Đơn giá: <strong>{Number(pkg.publicPrice).toLocaleString('vi-VN')} ₫ / người</strong></p>
              <p style={{ margin: 0 }}>
                Ca đã chọn: <strong>{selectedSessionRefs.length} / {requiredSessions} ca</strong>
              </p>
            </div>

            <div style={{ lineHeight: '2' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                <span>Tổng giá trị gói</span>
                <strong>{estimatedTotal.toLocaleString('vi-VN')} ₫</strong>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between', color: '#002967', fontSize: '16px', fontWeight: 'bold', borderTop: '1px solid #eee', paddingTop: '8px', marginTop: '8px' }}>
                <span>Tiền đặt cọc (50%)</span>
                <span>{estimatedDeposit.toLocaleString('vi-VN')} ₫</span>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between', color: '#595959', fontSize: '13px' }}>
                <span>Còn lại tại chi nhánh</span>
                <span>{(estimatedTotal - estimatedDeposit).toLocaleString('vi-VN')} ₫</span>
              </div>
            </div>

            {bookingMode === 'GUEST' && (
              <div style={{ marginTop: '24px' }}>
                <button
                  type="submit"
                  className="button primary"
                  disabled={isSubmitting}
                  style={{ width: '100%', minHeight: '48px', fontSize: '16px', cursor: isSubmitting ? 'wait' : 'pointer' }}
                >
                  {isSubmitting ? 'Đang xử lý giữ chỗ...' : 'Xác nhận đặt chỗ & Giữ chỗ'}
                </button>
              </div>
            )}

            <p className="muted" style={{ fontSize: '12px', marginTop: '16px', lineHeight: '1.5' }}>
              * Khi bấm xác nhận, hệ thống sẽ tiến hành giữ chỗ tức thời qua Workshop Operations. Đơn đặt chỗ sẽ có hiệu lực giữ chỗ trong 10 phút để hoàn tất thủ tục đặt cọc.
            </p>
          </aside>
        </div>
      </form>
    </main>
  );
}

