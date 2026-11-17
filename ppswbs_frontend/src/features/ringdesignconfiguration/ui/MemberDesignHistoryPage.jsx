import React, { useEffect, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router';
import { useWorkshopRingDesignApi } from '../api/useWorkshopRingDesignApi';
import { useDesignReviewApi } from '../../designreview/api/useDesignReviewApi';
import { useAuth } from '../../../shared/auth/AuthProvider';
import { MATERIAL_PALETTES, SHAPE_STROKES } from '../domain/designPreview';
import { messages } from '../messages';
import '../ring-design.css';

export default function MemberDesignHistoryPage() {
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const ringApi = useWorkshopRingDesignApi();
  const designReviewApi = useDesignReviewApi();
  const { status, userId } = useAuth();
  const isAuthenticated = status === 'signed-in' || Boolean(userId);

  const activeTab = searchParams.get('tab') === 'custom' ? 'custom' : 'workshop';

  const [designs, setDesigns] = useState([]);
  const [customRequests, setCustomRequests] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [lookupCode, setLookupCode] = useState('');
  const [lookupError, setLookupError] = useState('');

  const lang = 'vi';
  const t = messages[lang];

  useEffect(() => {
    let active = true;
    async function loadData() {
      setLoading(true);
      setError(null);
      try {
        if (activeTab === 'workshop') {
          const list = await ringApi.listMyDesigns(null, isAuthenticated);
          if (active) {
            setDesigns(Array.isArray(list) ? list : []);
          }
        } else {
          // Tab Custom Design Requests
          if (isAuthenticated) {
            const list = await designReviewApi.getMyDesignRequests();
            if (active) {
              setCustomRequests(Array.isArray(list) ? list : []);
            }
          }
        }
      } catch (err) {
        if (active) {
          setError(err.message || 'Không thể tải lịch sử thiết kế.');
        }
      } finally {
        if (active) setLoading(false);
      }
    }
    loadData();
    return () => {
      active = false;
    };
  }, [ringApi, designReviewApi, isAuthenticated, activeTab]);

  const handleTabChange = (newTab) => {
    setSearchParams(newTab === 'custom' ? { tab: 'custom' } : {});
  };

  const handleLookupSubmit = (e) => {
    e.preventDefault();
    const code = lookupCode.trim();
    if (!code) {
      setLookupError('Vui lòng nhập mã yêu cầu thiết kế để tra cứu.');
      return;
    }
    setLookupError('');
    navigate(`/design-requests/${encodeURIComponent(code)}`);
  };

  const getReviewStatusBadge = (reviewStatus) => {
    switch (reviewStatus) {
      case 'ACCEPTED':
        return { label: 'Đã được chấp thuận', className: 'ring-status-badge active' };
      case 'REJECTED':
        return { label: 'Đã từ chối', className: 'ring-status-badge archived' };
      case 'CLARIFICATION_REQUIRED':
        return { label: 'Cần làm rõ thêm', className: 'ring-status-badge draft' };
      case 'NEED_REVIEW':
      default:
        return { label: 'Đang đợi phản hồi của nhân viên', className: 'ring-status-badge validated' };
    }
  };

  return (
    <div className="ring-design-container" data-testid="member-design-history-page">
      <div className="ring-design-header">
        <h1 className="ring-design-title">{t.memberHistoryTitle}</h1>
        <p className="ring-design-subtitle">{t.memberHistorySubtitle}</p>
      </div>

      {/* Lookup Bar for Quick Access by Request Code (Guest & Member) */}
      <div
        className="ring-section-card"
        style={{
          marginBottom: '1.5rem',
          padding: '1.25rem',
          backgroundColor: '#f8fafc',
          border: '1px solid #e2e8f0',
        }}
      >
        <form onSubmit={handleLookupSubmit} style={{ display: 'flex', gap: '0.75rem', flexWrap: 'wrap', alignItems: 'center' }}>
          <label htmlFor="quick-lookup-input" style={{ fontWeight: 600, fontSize: '0.9rem', color: '#1e293b' }}>
            Tra cứu nhanh yêu cầu custom bằng mã:
          </label>
          <input
            id="quick-lookup-input"
            type="text"
            value={lookupCode}
            onChange={(e) => {
              setLookupCode(e.target.value);
              if (lookupError) setLookupError('');
            }}
            placeholder="Nhập mã yêu cầu (VD: REQ-... hoặc DR-...)"
            style={{
              flex: '1',
              minWidth: '220px',
              padding: '0.5rem 0.75rem',
              borderRadius: '6px',
              border: '1px solid #cbd5e1',
              fontSize: '0.9rem',
            }}
          />
          <button type="submit" className="ring-btn-secondary" style={{ padding: '0.5rem 1rem' }}>
            Tra cứu
          </button>
        </form>
        {lookupError && <div style={{ color: '#dc2626', fontSize: '0.85rem', marginTop: '0.5rem' }}>{lookupError}</div>}
      </div>

      {/* Tabs */}
      <div
        style={{
          display: 'flex',
          gap: '0.5rem',
          borderBottom: '2px solid #e2e8f0',
          marginBottom: '1.5rem',
        }}
      >
        <button
          type="button"
          onClick={() => handleTabChange('workshop')}
          style={{
            padding: '0.75rem 1.25rem',
            fontWeight: 600,
            fontSize: '1rem',
            border: 'none',
            background: 'none',
            cursor: 'pointer',
            borderBottom: activeTab === 'workshop' ? '3px solid #b45309' : '3px solid transparent',
            color: activeTab === 'workshop' ? '#b45309' : '#64748b',
            marginBottom: '-2px',
          }}
        >
          Mẫu nhẫn Workshop
        </button>
        <button
          type="button"
          onClick={() => handleTabChange('custom')}
          style={{
            padding: '0.75rem 1.25rem',
            fontWeight: 600,
            fontSize: '1rem',
            border: 'none',
            background: 'none',
            cursor: 'pointer',
            borderBottom: activeTab === 'custom' ? '3px solid #b45309' : '3px solid transparent',
            color: activeTab === 'custom' ? '#b45309' : '#64748b',
            marginBottom: '-2px',
          }}
        >
          Yêu cầu tùy chỉnh (Custom)
        </button>
      </div>

      {loading && (
        <div style={{ textAlign: 'center', padding: '3rem', color: '#64748b' }}>
          Đang tải dữ liệu thiết kế...
        </div>
      )}

      {error && (
        <div className="ring-error-card" role="alert">
          {error}
        </div>
      )}

      {/* Tab 1: Workshop Ring Designs */}
      {activeTab === 'workshop' && (
        <>
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
        </>
      )}

      {/* Tab 2: Custom Design Requests */}
      {activeTab === 'custom' && (
        <>
          {!loading && !error && customRequests.length === 0 && (
            <div style={{ textAlign: 'center', padding: '3rem', color: '#64748b' }}>
              <p>Bạn chưa gửi yêu cầu thiết kế tùy chỉnh nào.</p>
              <button
                type="button"
                className="ring-btn-primary"
                style={{ width: 'auto', marginTop: '1rem' }}
                onClick={() => navigate('/design-requests/new')}
              >
                Tạo yêu cầu thiết kế mới
              </button>
            </div>
          )}

          <div style={{ display: 'flex', flexDirection: 'column', gap: '1.25rem' }}>
            {customRequests.map((req) => {
              const statusInfo = getReviewStatusBadge(req.reviewStatus);
              return (
                <div
                  key={req.id}
                  className="ring-section-card"
                  style={{
                    display: 'flex',
                    justifyContent: 'space-between',
                    alignItems: 'center',
                    flexWrap: 'wrap',
                    gap: '1rem',
                  }}
                >
                  <div style={{ flex: '1', minWidth: '260px' }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem', marginBottom: '0.35rem', flexWrap: 'wrap' }}>
                      <span style={{ fontWeight: 700, fontSize: '1.1rem', color: '#0f172a' }}>
                        {req.requestReference || req.id}
                      </span>
                      <span className={statusInfo.className}>
                        {statusInfo.label}
                      </span>
                      {req.analysisStatus && (
                        <span style={{ fontSize: '0.85rem', color: '#64748b' }}>
                          (AI: {req.analysisStatus})
                        </span>
                      )}
                    </div>
                    {req.description && (
                      <p style={{ margin: '0.25rem 0', fontSize: '0.9rem', color: '#475569', fontStyle: 'italic' }}>
                        &ldquo;{req.description.length > 120 ? `${req.description.slice(0, 120)}…` : req.description}&rdquo;
                      </p>
                    )}
                  </div>

                  <div style={{ display: 'flex', gap: '0.75rem', alignItems: 'center' }}>
                    <button
                      type="button"
                      className="ring-btn-secondary"
                      onClick={() => navigate(`/design-requests/${encodeURIComponent(req.id)}`)}
                    >
                      Xem chi tiết →
                    </button>
                  </div>
                </div>
              );
            })}
          </div>
        </>
      )}
    </div>
  );
}

