import React, { useEffect, useRef, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router';
import { getWorkshopPackage, listWorkshopLocations, listWorkshopSessions } from '../api/workshops';
import { getEffectiveParticipantMax } from './bookingCapacity';
import { buildCheckoutPath, parseSelection, selectionProblem } from './bookingSelection';
import { addCartLine, cartLineCount, subscribeToCart } from './cartStorage';
import { formatSessionWindow } from './groupDisplay';
import './figmaPublicShell.css';
import PackageReviewSummary from '../features/reviewmanagement/ui/PackageReviewSummary';
import { offersForPackage, usePublicWorkshopPrograms, WorkshopOfferList, WorkshopOfferPrice } from '../features/loyalty-promotion/publicOffers';

/** Sessions shown per page: two rows of three on a wide screen, so the page stays short. */
const SESSIONS_PER_PAGE = 6;

function formatDate(date) {
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, '0');
  const d = String(date.getDate()).padStart(2, '0');
  return `${y}-${m}-${d}`;
}

export function WorkshopDetailPage({ packageId }) {
  const programs = usePublicWorkshopPrograms();
  const [pkg, setPkg] = useState(null);
  const [loadingPkg, setLoadingPkg] = useState(true);
  const [pkgError, setPkgError] = useState(null);

  // Filters for session discovery
  const today = new Date();
  const nextTwoWeeks = new Date();
  nextTwoWeeks.setDate(today.getDate() + 14);

  // A selection made earlier (back from checkout, or a shared link) comes back in the URL.
  const [searchParams] = useSearchParams();
  const initial = useRef(parseSelection(searchParams)).current;
  const navigate = useNavigate();

  const [locationId, setLocationId] = useState(initial.locationId);
  const [locations, setLocations] = useState([]);
  const [loadingLocations, setLoadingLocations] = useState(false);
  const [locationsError, setLocationsError] = useState(null);
  const [from, setFrom] = useState(initial.from || formatDate(today));
  const [to, setTo] = useState(initial.to || formatDate(nextTwoWeeks));

  // Shopee-style purchase panel: pick the required sessions and the ticket count, then "Thêm vào giỏ" or "Đặt ngay".
  const [selectedRefs, setSelectedRefs] = useState(initial.sessionReferences);
  const [participantCount, setParticipantCount] = useState(initial.participantCount);
  const [selectionError, setSelectionError] = useState(null);
  const [cartMessage, setCartMessage] = useState(null);
  const [cartCount, setCartCount] = useState(() => cartLineCount());
  useEffect(() => subscribeToCart(lines => setCartCount(lines.length)), []);

  const [sessions, setSessions] = useState([]);
  const [loadingSessions, setLoadingSessions] = useState(false);
  const [sessionsError, setSessionsError] = useState(null);

  // The session list is paged on the screen (the server returns every session of the chosen days).
  const [sessionPage, setSessionPage] = useState(0);
  const pageCount = Math.max(1, Math.ceil(sessions.length / SESSIONS_PER_PAGE));
  const currentPage = Math.min(sessionPage, pageCount - 1);
  const pagedSessions = sessions.slice(currentPage * SESSIONS_PER_PAGE, (currentPage + 1) * SESSIONS_PER_PAGE);
  useEffect(() => { setSessionPage(0); }, [sessions]);

  // The package's reviews are always shown at the bottom of this page; the button only scrolls down to them.
  const reviewRef = useRef(null);

  // Load package
  useEffect(() => {
    if (!packageId) return;
    setLoadingPkg(true);
    setPkgError(null);
    getWorkshopPackage(packageId)
      .then(data => {
        setPkg(data);
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

  // Load sessions when packageId, locationId, from, to change
  const fetchSessions = () => {
    if (!packageId || !locationId) return;
    setLoadingSessions(true);
    setSessionsError(null);
    listWorkshopSessions(packageId, { locationId, from, to })
      .then(data => {
        setSessions(Array.isArray(data) ? data : []);
      })
      .catch(err => {
        setSessionsError(err?.detail || err?.message || 'Không thể tải danh sách ca workshop. Vui lòng thử lại.');
      })
      .finally(() => {
        setLoadingSessions(false);
      });
  };

  useEffect(() => {
    if (pkg && locationId) {
      fetchSessions();
    } else {
      setSessions([]);
    }
  }, [pkg, locationId, from, to]);

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

  const offers = offersForPackage(pkg, programs);

  // Only sessions that are really on the list count; a stale reference from the URL is ignored.
  const sessionsKnown = sessions.length > 0 && !loadingSessions;
  const chosenRefs = sessionsKnown ? selectedRefs.filter(ref => sessions.some(item => item.sessionReference === ref)) : [];
  const requiredSessions = Math.max(1, Number(pkg.requiredSessionCount) || 1);
  const minParticipants = Math.max(1, Number(pkg.minParticipants) || 1);
  const sessionSelectionComplete = chosenRefs.length >= requiredSessions;
  const effectiveMax = getEffectiveParticipantMax(pkg, sessions, chosenRefs);
  const canChooseCount = sessionSelectionComplete && effectiveMax >= minParticipants;
  const ticketCount = canChooseCount ? Math.min(Math.max(minParticipants, participantCount), effectiveMax) : 1;
  const problem = selectionProblem({ requiredSessions, selectedCount: chosenRefs.length, effectiveMax, minParticipants, participantCount: ticketCount });

  const toggleSession = reference => {
    setSelectionError(null);
    setCartMessage(null);
    setSelectedRefs(previous => {
      const current = previous.filter(ref => sessions.some(item => item.sessionReference === ref));
      if (current.includes(reference)) return current.filter(ref => ref !== reference);
      if (requiredSessions === 1) return [reference];
      if (current.length >= requiredSessions) return [...current.slice(1), reference];
      return [...current, reference];
    });
  };

  const changeCount = next => {
    setSelectionError(null);
    setCartMessage(null);
    setParticipantCount(Math.min(Math.max(minParticipants, next), effectiveMax));
  };

  const resetSelection = () => { setSelectedRefs([]); setSelectionError(null); setCartMessage(null); };

  const buyNow = () => {
    if (problem) { setSelectionError(problem); return; }
    // The ticket count and sessions only travel to the checkout; its quote is the server's (FR-045).
    navigate(buildCheckoutPath(pkg.packageId ?? packageId, { locationId, sessionReferences: chosenRefs, participantCount: ticketCount, from, to }));
  };

  const addToCart = () => {
    if (problem) { setSelectionError(problem); return; }
    setSelectionError(null);
    addCartLine({
      packageId: Number(pkg.packageId ?? packageId),
      sessionReferences: chosenRefs,
      participantCount: ticketCount,
      packageName: pkg.name || '',
      pricePerParticipantVnd: Number(pkg.publicPrice || 0),
      locationId,
      locationName: locations.find(item => Number(item.id) === Number(locationId))?.name || '',
      sessionLabels: chosenRefs.map(ref => {
        const found = sessions.find(item => item.sessionReference === ref);
        return found ? formatSessionWindow(found.scheduledStart, found.scheduledEnd) : ref;
      }),
      minParticipants,
      maxParticipants: effectiveMax,
    });
    setCartMessage('Đã thêm gói này vào giỏ. Bạn có thể chọn thêm gói khác rồi thanh toán một lần.');
  };

  return (
    <main className="container public-page">
      <div style={{ marginBottom: '16px' }}>
        <a href="/workshops" className="text-link">← Quay lại danh sách workshop</a>
      </div>

      <p className="eyebrow">CHI TIẾT GÓI WORKSHOP</p>
      <h1>{pkg.name}</h1>

      <div className="detail-grid" style={{ alignItems: 'start', marginTop: '24px' }}>
        <div className="ring-visual gold" aria-label="Minh họa gói workshop" style={{ height: '320px' }}>
          <span />
        </div>

        <div>
          {/* FR-052: one ticket after the best automatic discount (006's public gate); plain price when none. */}
          <p className="price" style={{ fontSize: '32px', margin: '0 0 16px' }}>
            <WorkshopOfferPrice offers={offers} fallback={`${Number(pkg.publicPrice || 0).toLocaleString('vi-VN')} ₫`} /> <small style={{ fontSize: '16px', fontWeight: 'normal', color: '#595959' }}>/ vé</small>
          </p>
          <p className="muted" style={{ margin: '-8px 0 16px', fontSize: '13px' }}>
            Giá tham khảo đã áp mã giảm tốt nhất; giá cuối do hệ thống báo giá khi đặt.
          </p>
          <p className="lead" style={{ fontSize: '16px', lineHeight: '1.6', marginBottom: '20px' }}>
            {pkg.description}
          </p>
          <WorkshopOfferList offers={offers} />

          {/* Reviews are written by people who attended a session, so a session still open for booking has none. What helps
              someone choosing a package is the reviews of the package, gathered from the sessions already held. */}
          <p style={{ margin: '0 0 20px', display: 'flex', gap: '10px', flexWrap: 'wrap' }}>
            <button
              type="button"
              className="button outline"
              onClick={() => reviewRef.current?.scrollIntoView?.({ behavior: 'smooth', block: 'start' })}
            >
              Xem đánh giá của gói này ↓
            </button>
            <a
              href={`/workshops/${pkg.packageReference || 'PKG-COUPLE-SILVER'}/design`}
              className="button outline"
            >
              💍 Cấu hình mẫu nhẫn
            </a>
          </p>

          <div className="workspace-card" style={{ padding: '18px', backgroundColor: '#fafafa' }}>
            <h3 style={{ margin: '0 0 12px', fontSize: '16px' }}>Quy định của gói workshop</h3>
            <ul style={{ margin: 0, paddingLeft: '20px', lineHeight: '1.8', fontSize: '14px', color: '#333' }}>
              <li>Số buổi thực hiện: <strong>{pkg.requiredSessionCount || 1} buổi</strong></li>
              <li>Quy định số vé / booking: <strong>{pkg.minParticipants} - {pkg.maxParticipants} vé</strong></li>
              <li>Quy định địa điểm: <strong>{pkg.sameLocationRequired ? 'Bắt buộc chọn cùng một chi nhánh' : 'Có thể chọn linh hoạt giữa các chi nhánh'}</strong></li>
              {pkg.minimumSameDaySlotGap > 0 && (
                <li>Khoảng cách tối thiểu nếu học cùng ngày: <strong>cách ít nhất {pkg.minimumSameDaySlotGap} ca</strong></li>
              )}
              <li>Chính sách cọc: <strong>Đặt cọc 50%</strong> khi giữ chỗ, phần còn lại thanh toán tại chi nhánh.</li>
            </ul>
          </div>

          <section className="workspace-card workshop-buy-panel" aria-label="Chọn ca và số lượng" style={{ marginTop: '24px', padding: '18px' }}>
            <div className="filter-bar" style={{ margin: '0 0 14px', padding: '12px' }}>
              <label style={{ flex: '1 1 200px' }}>
                Chi nhánh
                <select value={locationId ?? ''} disabled={loadingLocations || locations.length === 0} onChange={e => { setLocationId(Number(e.target.value)); resetSelection(); }}>
                  <option value="" disabled>{loadingLocations ? 'Đang tải chi nhánh...' : 'Chọn chi nhánh'}</option>
                  {locations.map(loc => (
                    <option key={loc.id} value={loc.id}>{loc.name}</option>
                  ))}
                </select>
              </label>
              <label style={{ flex: '1 1 130px' }}>
                Từ ngày
                <input type="date" value={from} onChange={e => { setFrom(e.target.value); resetSelection(); }} />
              </label>
              <label style={{ flex: '1 1 130px' }}>
                Đến ngày
                <input type="date" value={to} onChange={e => { setTo(e.target.value); resetSelection(); }} />
              </label>
            </div>
            {locationsError && <p className="status" style={{ color: '#b00', borderColor: '#b00' }}>{locationsError}</p>}

            <h3 id="session-choice-label" style={{ margin: '0 0 4px', fontSize: '16px' }}>
              Chọn {requiredSessions} ca workshop <small className="muted">(đã chọn {chosenRefs.length}/{requiredSessions})</small>
            </h3>
            <p className="muted" style={{ margin: '0 0 10px', fontSize: '12px' }}>
              Số chỗ còn lại là thông tin tham khảo; việc giữ chỗ thực tế được đảm bảo khi hoàn tất bước đặt chỗ.
            </p>

            {loadingSessions && <p className="muted">Đang tìm các ca workshop khả dụng...</p>}
            {sessionsError && !loadingSessions && (
              <p className="status" role="alert" style={{ color: '#b00', borderColor: '#b00' }}>{sessionsError}</p>
            )}
            {!loadingSessions && !sessionsError && sessions.length === 0 && (
              <p className="muted">Không có ca workshop nào phù hợp trong khoảng thời gian đã chọn tại chi nhánh này. Vui lòng chọn khoảng thời gian hoặc chi nhánh khác.</p>
            )}

            {!loadingSessions && !sessionsError && sessions.length > 0 && (
              <>
                <div role="group" aria-labelledby="session-choice-label" style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(200px, 1fr))', gap: '10px' }}>
                  {pagedSessions.map(s => {
                    const isSelected = chosenRefs.includes(s.sessionReference);
                    const open = s.customerSelectable && s.remainingCapacity > 0;
                    const selectable = open && (isSelected || s.remainingCapacity >= minParticipants);
                    return (
                      <button
                        key={s.sessionReference}
                        type="button"
                        aria-pressed={isSelected}
                        disabled={!selectable}
                        onClick={() => toggleSession(s.sessionReference)}
                        style={{
                          textAlign: 'left',
                          padding: '10px 12px',
                          border: isSelected ? '2px solid #ee4d2d' : '1px solid #dcdfe6',
                          background: isSelected ? '#fff5f2' : '#fff',
                          color: '#1e1e1e',
                          cursor: selectable ? 'pointer' : 'not-allowed',
                          opacity: selectable ? 1 : 0.5,
                        }}
                      >
                        <strong style={{ display: 'block', fontSize: '14px' }}>{formatSessionWindow(s.scheduledStart, s.scheduledEnd)}</strong>
                        <span style={{ fontSize: '12px', color: '#595959' }}>{open ? `Còn ${s.remainingCapacity} chỗ` : 'Hết chỗ'}</span>
                      </button>
                    );
                  })}
                </div>

                {pageCount > 1 && (
                  <nav aria-label="Phân trang các ca workshop" style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', gap: '12px', marginTop: '14px', flexWrap: 'wrap' }}>
                    <button type="button" className="button outline small" disabled={currentPage === 0} onClick={() => setSessionPage(currentPage - 1)}>
                      ‹ Trước
                    </button>
                    <span style={{ display: 'inline-flex', flexDirection: 'row', alignItems: 'center', gap: '8px', whiteSpace: 'nowrap', margin: 0 }}>
                      Trang
                      <select value={currentPage} onChange={e => setSessionPage(Number(e.target.value))} aria-label="Chọn trang" style={{ width: 'auto', minWidth: '64px', margin: 0 }}>
                        {Array.from({ length: pageCount }, (_, index) => (
                          <option key={index} value={index}>{index + 1}</option>
                        ))}
                      </select>
                      / {pageCount}
                    </span>
                    <button type="button" className="button outline small" disabled={currentPage >= pageCount - 1} onClick={() => setSessionPage(currentPage + 1)}>
                      Sau ›
                    </button>
                  </nav>
                )}
              </>
            )}

            <div className="participant-field" style={{ marginTop: '16px' }}>
              <span className="participant-label" id="participant-label">Số lượng vé</span>
              <div className="participant-stepper" role="group" aria-labelledby="participant-label">
                <button type="button" aria-label="Giảm số vé" disabled={!canChooseCount || ticketCount <= minParticipants} onClick={() => changeCount(ticketCount - 1)}>−</button>
                <input
                  type="number"
                  min={minParticipants}
                  max={canChooseCount ? effectiveMax : 1}
                  value={ticketCount}
                  disabled={!canChooseCount}
                  aria-labelledby="participant-label"
                  onChange={e => { const next = Number(e.target.value); if (Number.isFinite(next)) changeCount(next); }}
                />
                <button type="button" aria-label="Tăng số vé" disabled={!canChooseCount || ticketCount >= effectiveMax} onClick={() => changeCount(ticketCount + 1)}>+</button>
              </div>
              <small className="participant-hint">
                {!sessionSelectionComplete
                  ? `Chọn đủ ${requiredSessions} ca trước, sau đó mới chọn số vé. Số vé tạm thời: 1.`
                  : canChooseCount
                    ? `Tối đa ${effectiveMax} vé theo các ca đã chọn (gói cho ${minParticipants} - ${pkg.maxParticipants} vé).`
                    : 'Các ca đã chọn không còn đủ số vé tối thiểu của gói. Vui lòng chọn ca khác.'}
              </small>
            </div>

            {selectionError && <p role="alert" style={{ color: '#b00', fontSize: '14px', margin: '8px 0 0' }}>{selectionError}</p>}

            <div className="actions" style={{ marginTop: '12px', display: 'flex', gap: '10px', flexWrap: 'wrap' }}>
              <button type="button" className="button outline" onClick={addToCart} style={{ flex: '1 1 160px', minHeight: '48px', borderColor: '#ee4d2d', color: '#ee4d2d' }}>
                Thêm vào giỏ
              </button>
              <button type="button" className="button primary" onClick={buyNow} style={{ flex: '1 1 160px', minHeight: '48px', background: '#ee4d2d', borderColor: '#ee4d2d' }}>
                Đặt ngay
              </button>
            </div>
            {cartMessage && (
              <p role="status" style={{ marginTop: '10px' }}>
                {cartMessage} <Link className="text-link" to="/workshop-booking/cart">Mở giỏ workshop ({cartCount})</Link>
              </p>
            )}
          </section>
        </div>
      </div>

      <div ref={reviewRef} style={{ scrollMarginTop: '24px' }}>
        <PackageReviewSummary packageId={pkg.packageId ?? packageId} packageName={pkg.name} />
      </div>
    </main>
  );
}

