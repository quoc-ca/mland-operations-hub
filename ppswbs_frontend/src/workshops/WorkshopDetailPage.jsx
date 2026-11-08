import React, { useEffect, useRef, useState } from 'react';
import { getWorkshopPackage, listWorkshopLocations, listWorkshopSessions } from '../api/workshops';
import PackageReviewSummary from '../features/reviewmanagement/ui/PackageReviewSummary';

/** Sessions shown per page: two rows of three on a wide screen, so the page stays short. */
const SESSIONS_PER_PAGE = 6;

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

export function WorkshopDetailPage({ packageId }) {
  const [pkg, setPkg] = useState(null);
  const [loadingPkg, setLoadingPkg] = useState(true);
  const [pkgError, setPkgError] = useState(null);

  // Filters for session discovery
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
          <p className="price" style={{ fontSize: '32px', margin: '0 0 16px' }}>
            {Number(pkg.publicPrice || 0).toLocaleString('vi-VN')} ₫ <small style={{ fontSize: '16px', fontWeight: 'normal', color: '#595959' }}>/ người</small>
          </p>
          <p className="lead" style={{ fontSize: '16px', lineHeight: '1.6', marginBottom: '20px' }}>
            {pkg.description}
          </p>

          {/* Reviews are written by people who attended a session, so a session still open for booking has none. What helps
              someone choosing a package is the reviews of the package, gathered from the sessions already held. */}
          <p style={{ margin: '0 0 20px' }}>
            <button
              type="button"
              className="button outline"
              onClick={() => reviewRef.current?.scrollIntoView?.({ behavior: 'smooth', block: 'start' })}
            >
              Xem đánh giá của gói này ↓
            </button>
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

          <div style={{ marginTop: '24px' }}>
                <a className="button primary" aria-disabled={!locationId} style={{ width: '100%', textAlign: 'center', fontSize: '16px', padding: '14px 20px', opacity: locationId ? 1 : 0.55 }} href={locationId ? `/workshops/${pkg.packageId}/book?locationId=${locationId}` : undefined} onClick={event => { if (!locationId) event.preventDefault(); }}>
              Đặt gói workshop này ngay →
            </a>
          </div>
        </div>
      </div>

      {/* Discovery filter section */}
      <section style={{ marginTop: '56px' }}>
        <div className="card-head">
          <div>
            <p className="eyebrow">LỊCH CA KHẢ DỤNG</p>
            <h2>Tra cứu lịch & ca trống tại chi nhánh</h2>
          </div>
          <span className="muted" style={{ fontSize: '13px' }}>Dữ liệu thời gian thực từ Workshop Operations</span>
        </div>

        <div className="filter-bar" style={{ marginTop: '16px' }}>
          <label style={{ flex: '2 1 280px' }}>
            Chi nhánh
            <select value={locationId ?? ''} disabled={loadingLocations || locations.length === 0} onChange={e => setLocationId(Number(e.target.value))}>
              <option value="" disabled>{loadingLocations ? 'Đang tải chi nhánh...' : 'Chọn chi nhánh'}</option>
              {locations.map(loc => (
                <option key={loc.id} value={loc.id}>{loc.name}</option>
              ))}
            </select>
          </label>

          {locationsError && <p className="status" style={{ color: '#b00', borderColor: '#b00' }}>{locationsError}</p>}

          <label style={{ flex: '1 1 160px' }}>
            Từ ngày
            <input type="date" value={from} onChange={e => setFrom(e.target.value)} />
          </label>

          <label style={{ flex: '1 1 160px' }}>
            Đến ngày
            <input type="date" value={to} onChange={e => setTo(e.target.value)} />
          </label>

          <button type="button" className="button primary" onClick={fetchSessions} style={{ height: '45px' }}>
            Tìm ca
          </button>
        </div>

        {loadingSessions && (
          <div className="empty-state">
            <p>Đang tìm các ca workshop khả dụng...</p>
          </div>
        )}

        {sessionsError && !loadingSessions && (
          <div className="empty-state">
            <p className="status" style={{ color: '#b00', borderColor: '#b00' }}>
              {sessionsError}
            </p>
          </div>
        )}

        {!loadingSessions && !sessionsError && sessions.length === 0 && (
          <div className="empty-state">
            <p>Không có ca workshop nào phù hợp trong khoảng thời gian đã chọn tại chi nhánh này.</p>
            <p className="muted" style={{ marginTop: '8px' }}>Vui lòng chọn khoảng thời gian khác hoặc chi nhánh khác.</p>
          </div>
        )}

        {!loadingSessions && !sessionsError && sessions.length > 0 && (
          <div style={{ marginTop: '24px' }}>
            <p className="prototype-note" style={{ marginBottom: '16px' }}>
              * Lưu ý: Số chỗ khả dụng là thông tin tham khảo (snapshot); việc giữ chỗ thực tế được đảm bảo khi hoàn tất bước đặt chỗ.
            </p>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(310px, 1fr))', gap: '16px' }}>
              {pagedSessions.map(s => (
                <article
                  key={s.sessionReference}
                  className="workspace-card"
                  style={{
                    margin: 0,
                    border: '1px solid #e3e3e3',
                    opacity: s.customerSelectable && s.remainingCapacity > 0 ? 1 : 0.6,
                    display: 'flex',
                    flexDirection: 'column',
                    justifyContent: 'space-between',
                  }}
                >
                  <div>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '8px' }}>
                      <span className="muted" style={{ fontSize: '13px' }}>
                        {formatSessionDateTime(s.scheduledStart)}
                      </span>
                      <span className={s.customerSelectable && s.remainingCapacity > 0 ? 'status ok' : 'status'}>
                        {s.remainingCapacity > 0 ? 'Mở đăng ký' : 'Hết chỗ'}
                      </span>
                    </div>

                    <strong style={{ fontSize: '18px', color: '#002967', display: 'block', margin: '4px 0 10px' }}>
                      {formatSessionTime(s.scheduledStart, s.scheduledEnd)}
                    </strong>

                    <div style={{ fontSize: '13px', color: '#595959' }}>
                      <span>Chỗ còn lại: <strong>{s.remainingCapacity} chỗ</strong> (snapshot)</span>
                    </div>
                  </div>

                  <div style={{ marginTop: '16px', paddingTop: '12px', borderTop: '1px solid #eee' }}>
                    <a
                      className="button outline"
                      style={{
                        width: '100%',
                        textAlign: 'center',
                        pointerEvents: s.customerSelectable && s.remainingCapacity > 0 ? 'auto' : 'none',
                        opacity: s.customerSelectable && s.remainingCapacity > 0 ? 1 : 0.5,
                      }}
                      href={locationId ? `/workshops/${pkg.packageId}/book?locationId=${locationId}&session=${encodeURIComponent(s.sessionReference)}&from=${from}&to=${to}` : undefined}
                    >
                      {s.customerSelectable && s.remainingCapacity > 0 ? 'Chọn lịch này để đặt' : 'Ca không khả dụng'}
                    </a>
                  </div>
                </article>
              ))}
            </div>

            {pageCount > 1 && (
              <nav aria-label="Phân trang các ca workshop" style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', gap: '12px', marginTop: '20px', flexWrap: 'wrap' }}>
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
          </div>
        )}
      </section>

      <div ref={reviewRef} style={{ scrollMarginTop: '24px' }}>
        <PackageReviewSummary packageId={pkg.packageId ?? packageId} packageName={pkg.name} />
      </div>
    </main>
  );
}

