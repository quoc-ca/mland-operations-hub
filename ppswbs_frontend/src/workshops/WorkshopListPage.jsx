import React, { useEffect, useState } from 'react';
import './workshopList.css';
import { listWorkshopPackages } from '../api/workshops';
import { Paginator, usePaged } from '../shared/ui/Paginator';
import PackageReviewSummary from '../features/reviewmanagement/ui/PackageReviewSummary';
import { offersForPackage, usePublicWorkshopPrograms, WorkshopOfferBadges, WorkshopOfferPrice } from '../features/loyalty-promotion/publicOffers';

export function WorkshopListPage() {
  const programs = usePublicWorkshopPrograms();
  const [packages, setPackages] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const paged = usePaged(packages, 9, packages);

  const fetchPackages = async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await listWorkshopPackages();
      setPackages(Array.isArray(data) ? data : []);
    } catch (err) {
      setError(err?.detail || err?.message || 'Không thể tải danh sách workshop. Vui lòng thử lại sau.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchPackages();
  }, []);

  return (
    <main className="container public-page">
      <p className="eyebrow">WORKSHOP TẠI MLAND</p>
      <h1>Khám phá các gói Workshop</h1>
      <p className="lead">
        Tự tay chế tác chiếc nhẫn mang đậm dấu ấn cá nhân cùng sự hướng dẫn tận tâm từ nghệ nhân MLAND.
      </p>

      <div
        style={{
          margin: '20px 0 28px',
          padding: '14px 20px',
          backgroundColor: '#fefce8',
          border: '1px solid #fde047',
          borderRadius: '8px',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          flexWrap: 'wrap',
          gap: '12px',
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
          <span style={{ fontSize: '1.4rem' }}>💍</span>
          <span style={{ fontSize: '14px', color: '#1e293b' }}>
            Bạn muốn tùy chọn dáng nhẫn, chất liệu hoặc tự tay thiết kế mẫu trước khi đến lớp?
          </span>
        </div>
        <a
          href="/workshops/PKG-COUPLE-SILVER/design"
          className="button outline"
          style={{ height: '36px', fontSize: '13px', whiteSpace: 'nowrap' }}
        >
          Cấu hình mẫu nhẫn ngay →
        </a>
      </div>

      {loading && (
        <div className="empty-state">
          <p>Đang tải danh sách gói workshop...</p>
        </div>
      )}

      {error && !loading && (
        <div className="empty-state">
          <p className="status" style={{ color: '#b00', borderColor: '#b00' }}>
            {error}
          </p>
          <div style={{ marginTop: '16px' }}>
            <button type="button" className="button outline" onClick={fetchPackages}>
              Thử lại
            </button>
          </div>
        </div>
      )}

      {!loading && !error && packages.length === 0 && (
        <div className="empty-state">
          <p>Hiện chưa có gói workshop nào được mở bán.</p>
        </div>
      )}

      {!loading && !error && packages.length > 0 && (
        <div className="wk-grid">
          {paged.pageItems.map((pkg, idx) => {
            const sessionCount = pkg.requiredSessionCount || 1;
            const offers = offersForPackage(pkg, programs);
            return (
              <article className="wk-card" key={pkg.packageId}>
                <div className={`ring-visual ${idx % 2 === 0 ? 'gold' : 'pair'}`} aria-label="Minh họa workshop">
                  <span />
                </div>
                <div className="wk-body">
                  <h2 className="wk-name">{pkg.name}</h2>
                  <p className="wk-desc">{pkg.description}</p>
                  <div className="wk-chips">
                    <span className="wk-chip">⏱ {sessionCount} buổi</span>
                    <span className="wk-chip">👥 {pkg.minParticipants === pkg.maxParticipants ? `${pkg.maxParticipants} vé` : `${pkg.minParticipants}–${pkg.maxParticipants} vé`}</span>
                    {/* Only worth saying on the card when the package spans several sessions; the package page has the full rules. */}
                    {sessionCount > 1 && pkg.sameLocationRequired && <span className="wk-chip">📍 Cùng một chi nhánh</span>}
                  </div>
                  {/* FR-052: price after the best automatic discount, from 006's public gate; plain price when none. */}
                  <p className="wk-price">
                    <WorkshopOfferPrice
                      offers={offers}
                      fallback={`${Number(pkg.publicPrice || 0).toLocaleString('vi-VN')} ₫`}
                    /> <small>/ người</small>
                  </p>
                  <WorkshopOfferBadges offers={offers} />
                  <div className="wk-actions">
                    <a className="button outline" href={`/workshops/${pkg.packageId}`}>Xem lịch ca</a>
                    <a
                      className="button outline"
                      href={`/workshops/${pkg.packageReference || 'PKG-COUPLE-SILVER'}/design`}
                      title="Cấu hình mẫu nhẫn cho workshop này"
                    >
                      Cấu hình nhẫn
                    </a>
                    <a className="button primary" href={`/workshops/${pkg.packageId}/book`}>Đặt ngay</a>
                  </div>
                </div>
              </article>
            );
          })}
        </div>
      )}
      {!loading && !error && <Paginator page={paged.page} pageCount={paged.pageCount} onChange={paged.setPage} label="Phân trang gói workshop" />}
      <PackageReviewSummary allPackages />
    </main>
  );
}

