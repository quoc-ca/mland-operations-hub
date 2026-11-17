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
  const [selectedBranch, setSelectedBranch] = useState('all');
  const paged = usePaged(packages, 9, packages);

  const fetchPackages = async () => {
    setLoading(true);
    setError(null);
    const isTest = (typeof process !== 'undefined' && process.env?.NODE_ENV === 'test') ||
                   (typeof import.meta !== 'undefined' && import.meta.env?.MODE === 'test');
    try {
      if (isTest) {
        const data = await listWorkshopPackages();
        setPackages(Array.isArray(data) ? data : []);
        return;
      }
      const delayPromise = new Promise(resolve => setTimeout(resolve, 3000));
      const [data] = await Promise.all([listWorkshopPackages(), delayPromise]);
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
    <main className="container public-page wk-catalogue-page" data-node-id="521:572">
      {/* Header Banner - Figma node 539:1716 */}
      <div className="wk-header-banner" data-node-id="539:1716">
        <h1 className="wk-banner-title" data-node-id="521:587">LỊCH TRẢI NGHIỆM WORKSHOP KIM HOÀN</h1>
        <p className="wk-banner-desc" data-node-id="521:588">
          Tự tay làm nên chiếc nhẫn bạc của riêng bạn dưới sự hướng dẫn 1-1 của nghệ nhân kim hoàn.
        </p>
      </div>

      {/* Branch Filters - Figma node 539:1717 */}
      <div className="wk-branch-filters" data-node-id="539:1717">
        <button
          type="button"
          className={`wk-branch-chip ${selectedBranch === 'all' ? 'active' : ''}`}
          onClick={() => setSelectedBranch('all')}
          data-node-id="521:589"
        >
          Tất cả chi nhánh
        </button>
        <button
          type="button"
          className={`wk-branch-chip ${selectedBranch === 'danang' ? 'active' : ''}`}
          onClick={() => setSelectedBranch('danang')}
          data-node-id="521:591"
        >
          Chi nhánh Đà Nẵng (45 Bạch Đằng)
        </button>
        <button
          type="button"
          className={`wk-branch-chip ${selectedBranch === 'hcm' ? 'active' : ''}`}
          onClick={() => setSelectedBranch('hcm')}
          data-node-id="521:593"
        >
          Chi nhánh TP.HCM (123 Nguyễn Huệ)
        </button>
      </div>

      {/* Interactive ring design callout */}
      <div className="wk-config-callout">
        <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
          <span aria-hidden="true" />
          <span style={{ fontSize: '13px', color: '#1e293b' }}>
            Bạn muốn tùy chọn dáng nhẫn, chất liệu hoặc tự tay thiết kế mẫu trước khi đến lớp?
          </span>
        </div>
        <a
          href="/workshops/PKG-COUPLE-SILVER/design"
          className="button outline"
          style={{ height: '34px', fontSize: '12px', whiteSpace: 'nowrap' }}
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
        <div className="wk-list-container" data-node-id="539:1719">
          {paged.pageItems.map((pkg, idx) => {
            const sessionCount = pkg.requiredSessionCount || 1;
            const offers = offersForPackage(pkg, programs);
            return (
              <article className="wk-card wk-horizontal-card" key={pkg.packageId} data-node-id={`card-${pkg.packageId}`}>
                <div className="wk-card-media" aria-label="Minh họa workshop">
                  {pkg.imageUrl ? (
                    <img src={pkg.imageUrl} alt={pkg.name} />
                  ) : (
                    <div className="wk-media-placeholder">
                      <p className="wk-media-tag">img-holder · {pkg.name}</p>
                    </div>
                  )}
                </div>
                <div className="wk-card-content wk-body">
                  <div className="wk-card-header">
                    <h2 className="wk-name wk-card-title">{pkg.name}</h2>
                    <div className="wk-price wk-card-price">
                      <WorkshopOfferPrice
                        offers={offers}
                        fallback={`${Number(pkg.publicPrice || 0).toLocaleString('vi-VN')} ₫`}
                      /> <small>/ người</small>
                    </div>
                  </div>
                  <p className="wk-desc wk-card-desc">{pkg.description}</p>
                  <div className="wk-card-bullets">
                    <p>• Bao gồm phôi bạc 925 &amp; bộ dụng cụ chế tác</p>
                    <p>• Nghệ nhân kim hoàn hướng dẫn kèm 1-1 ({sessionCount} buổi)</p>
                    <p>• Khắc laser kỷ niệm miễn phí &amp; bảo dưỡng trọn đời</p>
                  </div>
                  <div className="wk-chips">
                    <span className="wk-chip">⏱ {sessionCount} buổi</span>
                    <span className="wk-chip">{pkg.minParticipants === pkg.maxParticipants ? `${pkg.maxParticipants} vé` : `${pkg.minParticipants}–${pkg.maxParticipants} vé`}</span>
                    {sessionCount > 1 && pkg.sameLocationRequired && <span className="wk-chip">Cùng một chi nhánh</span>}
                  </div>
                  <WorkshopOfferBadges offers={offers} />
                  <div className="wk-actions wk-card-actions">
                    <a className="button outline" href={`/workshops/${pkg.packageId}`}>Xem lịch ca</a>
                    <a
                      className="button outline"
                      href={`/workshops/${pkg.packageReference || 'PKG-COUPLE-SILVER'}/design`}
                      title="Cấu hình mẫu nhẫn cho workshop này"
                    >
                      Cấu hình nhẫn
                    </a>
                    <a className="button primary wk-btn-book" href={`/workshops/${pkg.packageId}/book`}>Đặt ngay</a>
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

