import React, { useEffect, useState } from 'react';
import { listWorkshopPackages } from '../api/workshops';

export function WorkshopListPage() {
  const [packages, setPackages] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

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
        <div className="catalogue-grid" style={{ marginTop: '32px' }}>
          {packages.map((pkg, idx) => (
            <article className="ring-card" key={pkg.packageId} style={{ display: 'flex', flexDirection: 'column', justifyContent: 'space-between' }}>
              <div>
                <div className={`ring-visual ${idx % 2 === 0 ? 'gold' : 'pair'}`} aria-label="Minh họa workshop">
                  <span />
                </div>
                <strong style={{ fontSize: '20px', marginTop: '12px', display: 'block' }}>{pkg.name}</strong>
                <p className="muted" style={{ minHeight: '44px', margin: '8px 0' }}>{pkg.description}</p>
                <p className="price" style={{ margin: '8px 0' }}>
                  {Number(pkg.publicPrice || 0).toLocaleString('vi-VN')} ₫ <small style={{ fontSize: '14px', fontWeight: 'normal', color: '#595959' }}>/ người</small>
                </p>
                <div style={{ fontSize: '13px', color: '#595959', display: 'flex', flexDirection: 'column', gap: '4px', margin: '12px 0' }}>
                  <span>⏱ Số buổi yêu cầu: <strong>{pkg.requiredSessionCount || 1} buổi</strong></span>
                  <span>👥 Số người: <strong>{pkg.minParticipants} - {pkg.maxParticipants} người</strong></span>
                  <span>📍 Chi nhánh: <strong>{pkg.sameLocationRequired ? 'Cùng chi nhánh' : 'Linh hoạt'}</strong></span>
                  {pkg.minimumSameDaySlotGap > 0 && (
                    <span>⏳ Cách tối thiểu: <strong>{pkg.minimumSameDaySlotGap} ca trong ngày</strong></span>
                  )}
                </div>
              </div>
              <div style={{ display: 'flex', gap: '10px', marginTop: '16px' }}>
                <a className="button outline" style={{ flex: 1, textAlign: 'center' }} href={`/workshops/${pkg.packageId}`}>
                  Xem lịch ca
                </a>
                <a className="button primary" style={{ flex: 1, textAlign: 'center' }} href={`/workshops/${pkg.packageId}/book`}>
                  Đặt ngay
                </a>
              </div>
            </article>
          ))}
        </div>
      )}
    </main>
  );
}

