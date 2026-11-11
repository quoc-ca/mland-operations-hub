import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router';
import { useWorkshopRingDesignApi } from '../api/useWorkshopRingDesignApi';
import { messages } from '../messages';
import '../ring-design.css';

export default function MemberDesignHistoryPage() {
  const navigate = useNavigate();
  const api = useWorkshopRingDesignApi();

  const [designs, setDesigns] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const lang = 'vi';
  const t = messages[lang];

  useEffect(() => {
    // In V1, list member's designs or mock recent drafts
    async function loadHistory() {
      setLoading(true);
      setError(null);
      try {
        // Mock load or retrieve recent design if stored
        setDesigns([
          {
            designId: 'DSG-DEMO-MEMBER-01',
            packageReference: 'PKG-COUPLE-SILVER',
            designVersion: 2,
            status: 'VALIDATED',
            material: 'Bạc Ý 925',
            estimatedPrice: '250000.00',
            createdAt: '2026-11-09T18:00:00Z',
          },
        ]);
      } catch (err) {
        setError(err.message || 'Không thể tải lịch sử thiết kế.');
      } finally {
        setLoading(false);
      }
    }
    loadHistory();
  }, [api]);

  return (
    <div className="ring-design-container" data-testid="member-design-history-page">
      <div className="ring-design-header">
        <h1 className="ring-design-title">{t.memberHistoryTitle}</h1>
        <p className="ring-design-subtitle">{t.memberHistorySubtitle}</p>
      </div>

      {loading && (
        <div style={{ textAlign: 'center', padding: '3rem', color: '#64748b' }}>
          Đang tải lịch sử thiết kế...
        </div>
      )}

      {error && (
        <div className="ring-error-card" role="alert">
          {error}
        </div>
      )}

      {!loading && !error && designs.length === 0 && (
        <div style={{ textAlign: 'center', padding: '3rem', color: '#64748b' }}>
          <p>{t.noDesignsYet}</p>
          <button
            type="button"
            className="ring-btn-primary"
            style={{ width: 'auto', marginTop: '1rem' }}
            onClick={() => navigate('/workshops')}
          >
            {t.createFirstDesign}
          </button>
        </div>
      )}

      <div style={{ display: 'flex', flexDirection: 'column', gap: '1.25rem' }}>
        {designs.map((d) => (
          <div
            key={d.designId}
            className="ring-section-card"
            style={{
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center',
              flexWrap: 'wrap',
              gap: '1rem',
            }}
          >
            <div>
              <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem', marginBottom: '0.35rem' }}>
                <span style={{ fontWeight: 700, fontSize: '1.1rem', color: '#0f172a' }}>{d.designId}</span>
                <span className={`ring-status-badge ${d.status.toLowerCase()}`}>{t[d.status] || d.status}</span>
                <span style={{ fontSize: '0.85rem', color: '#64748b' }}>
                  {t.versionLabel} {d.designVersion}
                </span>
              </div>
              <div style={{ fontSize: '0.9rem', color: '#64748b' }}>
                Gói: <strong>{d.packageReference}</strong> · Chất liệu: <strong>{d.material}</strong>
              </div>
            </div>

            <div style={{ display: 'flex', alignItems: 'center', gap: '1rem' }}>
              <div style={{ textAlign: 'right' }}>
                <div style={{ fontSize: '0.8rem', color: '#64748b' }}>{t.estimatedPriceLabel}</div>
                <div style={{ fontSize: '1.1rem', fontWeight: 700, color: '#b45309' }}>
                  {Number(d.estimatedPrice).toLocaleString('vi-VN')} đ
                </div>
              </div>

              <button
                type="button"
                className="ring-btn-secondary"
                onClick={() =>
                  navigate(`/workshops/${encodeURIComponent(d.packageReference)}/design/${encodeURIComponent(d.designId)}`)
                }
              >
                Xem chi tiết →
              </button>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}

