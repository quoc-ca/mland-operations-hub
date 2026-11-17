import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router';
import { useWorkshopRingDesignApi } from '../api/useWorkshopRingDesignApi';
import { useAuth } from '../../../shared/auth/AuthProvider';
import { MATERIAL_PALETTES, SHAPE_STROKES } from '../domain/designPreview';
import { messages } from '../messages';
import '../ring-design.css';

export default function MemberDesignHistoryPage() {
  const navigate = useNavigate();
  const api = useWorkshopRingDesignApi();
  const { status, userId } = useAuth();
  const isAuthenticated = status === 'signed-in' || Boolean(userId);

  const [designs, setDesigns] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const lang = 'vi';
  const t = messages[lang];

  useEffect(() => {
    let active = true;
    async function loadHistory() {
      setLoading(true);
      setError(null);
      try {
        const list = await api.listMyDesigns(null, isAuthenticated);
        if (active) {
          setDesigns(Array.isArray(list) ? list : []);
        }
      } catch (err) {
        if (active) {
          setError(err.message || 'Không thể tải lịch sử thiết kế.');
        }
      } finally {
        if (active) setLoading(false);
      }
    }
    loadHistory();
  return () => {
    active = false;
  };
}, [api, isAuthenticated]);

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
          onClick={() => navigate('/workshops/PKG-COUPLE-SILVER/design/configure')}
        >
          {t.createFirstDesign}
        </button>
      </div>
    )}

    <div style={{ display: 'flex', flexDirection: 'column', gap: '1.25rem' }}>
              {designs.map((d) => {
                const snapshot = d.componentSnapshot || {};
                const matLabel = MATERIAL_PALETTES[snapshot.materialReference]?.label || snapshot.materialReference || 'Bạc Ý 925';
                const shapeLabel = SHAPE_STROKES[snapshot.shapeReference]?.label || snapshot.shapeReference || 'Tròn cổ điển';
                const sizeVal = snapshot.ringSize || '8';
                const price = d.evaluation?.estimatedPrice || d.estimatedPrice || '0.00';
                const gemCount = (snapshot.components || []).reduce((sum, c) => sum + (Number(c.quantity) || 1), 0);

                return (
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
                  <span className={`ring-status-badge ${d.status?.toLowerCase()}`}>{t[d.status] || d.status}</span>
                  <span style={{ fontSize: '0.85rem', color: '#64748b' }}>
                    {t.versionLabel} {d.designVersion}
                  </span>
                </div>
                <div style={{ fontSize: '0.9rem', color: '#64748b' }}>
                  Gói: <strong>{d.packageReference}</strong> · Chất liệu: <strong>{matLabel}</strong> · Dáng: <strong>{shapeLabel}</strong> · Size: <strong>{sizeVal}</strong>
                  {gemCount > 0 && <span> · Đá: <strong>{gemCount} viên</strong></span>}
                </div>
              </div>

              <div style={{ display: 'flex', alignItems: 'center', gap: '1rem' }}>
                <div style={{ textAlign: 'right' }}>
                  <div style={{ fontSize: '0.8rem', color: '#64748b' }}>{t.estimatedPriceLabel}</div>
                  <div style={{ fontSize: '1.1rem', fontWeight: 700, color: '#b45309' }}>
                    {Number(price).toLocaleString('vi-VN')} đ
                  </div>
                </div>

                <div style={{ display: 'flex', gap: '0.5rem' }}>
                  <button
                    type="button"
                    className="ring-btn-secondary"
                    onClick={() =>
                      navigate(`/workshops/${encodeURIComponent(d.packageReference)}/design/${encodeURIComponent(d.designId)}`)
                    }
                  >
                    Xem chi tiết →
                  </button>
                  <button
                    type="button"
                    className="ring-btn-secondary"
                    onClick={() =>
                      navigate(
                        `/workshops/${encodeURIComponent(d.packageReference)}/design/configure?mode=EDIT&designId=${encodeURIComponent(
                          d.designId
                        )}`
                      )
                    }
                  >
                    Chỉnh sửa
                  </button>
                </div>
              </div>
            </div>
          );
        })}
      </div>
    </div>
            );
}

