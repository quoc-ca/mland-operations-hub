import React, { useEffect, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router';
import { useWorkshopRingDesignApi } from '../api/useWorkshopRingDesignApi';
import { useDesignReviewApi } from '../../designreview/api/useDesignReviewApi';
import { useWorkshopDesignCommand } from '../application/useWorkshopDesignCommand';
import { useAuth } from '../../../shared/auth/AuthProvider';
import { useT, useEnumLabel, useFormat } from '../../../shared/i18n/I18nProvider';
import { sharedMessages } from '../../../shared/ui/messages';
import { materialLabel, shapeLabel as shapeLabelOf } from '../domain/designPreview';
import { wholeMoney } from '../domain/designFormat';
import { messages } from '../messages';
import '../ring-design.css';

const ITEMS_PER_PAGE = 5;

export default function MemberDesignHistoryPage() {
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const ringApi = useWorkshopRingDesignApi();
  const designReviewApi = useDesignReviewApi();
  const { status, userId } = useAuth();
  const isAuthenticated = status === 'signed-in' || Boolean(userId);
  const t = useT(messages);
  const ts = useT(sharedMessages);
  const statusLabel = useEnumLabel(messages, 'status');
  const fmt = useFormat();

  const activeTab = searchParams.get('tab') === 'custom' ? 'custom' : 'workshop';

  const [designs, setDesigns] = useState([]);
  const [customRequests, setCustomRequests] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [lookupCode, setLookupCode] = useState('');
  const [lookupError, setLookupError] = useState('');

  // Pagination states
  const [workshopPage, setWorkshopPage] = useState(1);
  const [customPage, setCustomPage] = useState(1);

  // Soft delete state
  const [designToDelete, setDesignToDelete] = useState(null);
  const [deleting, setDeleting] = useState(false);
  const [deleteError, setDeleteError] = useState(null);
  const { executeArchive } = useWorkshopDesignCommand(isAuthenticated);

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
          setError(err.message || t('history.loadError'));
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

  const totalWorkshopPages = Math.max(1, Math.ceil(designs.length / ITEMS_PER_PAGE));
  const totalCustomPages = Math.max(1, Math.ceil(customRequests.length / ITEMS_PER_PAGE));

  useEffect(() => {
    if (workshopPage > totalWorkshopPages) {
      setWorkshopPage(totalWorkshopPages);
    }
  }, [designs.length, totalWorkshopPages, workshopPage]);

  useEffect(() => {
    if (customPage > totalCustomPages) {
      setCustomPage(totalCustomPages);
    }
  }, [customRequests.length, totalCustomPages, customPage]);

  const paginatedDesigns = designs.slice(
    (workshopPage - 1) * ITEMS_PER_PAGE,
    workshopPage * ITEMS_PER_PAGE
  );

  const paginatedCustomRequests = customRequests.slice(
    (customPage - 1) * ITEMS_PER_PAGE,
    customPage * ITEMS_PER_PAGE
  );

  const handleTabChange = (newTab) => {
    setSearchParams(newTab === 'custom' ? { tab: 'custom' } : {});
  };

  const handleLookupSubmit = (e) => {
    e.preventDefault();
    const code = lookupCode.trim();
    if (!code) {
      setLookupError(t('history.lookupRequired'));
      return;
    }
    setLookupError('');
    navigate(`/design-requests/${encodeURIComponent(code)}`);
  };

  const handleConfirmDelete = async () => {
    if (!designToDelete) return;
    setDeleting(true);
    setDeleteError(null);
    try {
      await executeArchive(designToDelete.designId, designToDelete.designVersion);
      setDesigns((prev) => prev.filter((d) => d.designId !== designToDelete.designId));
      setDesignToDelete(null);
    } catch (err) {
      setDeleteError(err?.message || t('history.deleteFailed'));
    } finally {
      setDeleting(false);
    }
  };

  const getReviewStatusBadge = (reviewStatus) => {
    switch (reviewStatus) {
      case 'ACCEPTED':
        return { label: t('review.ACCEPTED'), className: 'ring-status-badge active' };
      case 'REJECTED':
        return { label: t('review.REJECTED'), className: 'ring-status-badge archived' };
      case 'CLARIFICATION_REQUIRED':
        return { label: t('review.CLARIFICATION_REQUIRED'), className: 'ring-status-badge draft' };
      case 'NEED_REVIEW':
      default:
        return { label: t('review.NEED_REVIEW'), className: 'ring-status-badge validated' };
    }
  };

  const renderPagination = (currentPage, totalPages, totalItems, onPageChange, testPrefix) => {
    if (totalItems === 0) return null;
    const startIdx = (currentPage - 1) * ITEMS_PER_PAGE + 1;
    const endIdx = Math.min(currentPage * ITEMS_PER_PAGE, totalItems);

    return (
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          marginTop: '1.5rem',
          padding: '0.85rem 0',
          borderTop: '1px solid #e2e8f0',
          flexWrap: 'wrap',
          gap: '0.75rem',
        }}
        data-testid={`${testPrefix}-pagination`}
      >
        <div style={{ fontSize: '0.875rem', color: '#64748b' }} data-testid={`${testPrefix}-pagination-info`}>
          {t('history.pageInfo', { from: startIdx, to: endIdx, total: totalItems })}
        </div>

        <div style={{ display: 'flex', gap: '0.35rem', alignItems: 'center' }}>
          <button
            type="button"
            disabled={currentPage <= 1}
            onClick={() => onPageChange(currentPage - 1)}
            style={{
              padding: '0.4rem 0.75rem',
              borderRadius: '6px',
              border: '1px solid #cbd5e1',
              background: currentPage <= 1 ? '#f8fafc' : '#ffffff',
              color: currentPage <= 1 ? '#94a3b8' : '#334155',
              cursor: currentPage <= 1 ? 'not-allowed' : 'pointer',
              fontSize: '0.85rem',
              fontWeight: 500,
            }}
            data-testid={`${testPrefix}-pagination-prev`}
          >
            ‹ {t('history.prev')}
          </button>

          {Array.from({ length: totalPages }, (_, i) => i + 1).map((p) => (
            <button
              key={p}
              type="button"
              onClick={() => onPageChange(p)}
              style={{
                minWidth: '34px',
                height: '34px',
                padding: '0 0.5rem',
                borderRadius: '6px',
                border: p === currentPage ? '1px solid #b45309' : '1px solid #cbd5e1',
                background: p === currentPage ? '#b45309' : '#ffffff',
                color: p === currentPage ? '#ffffff' : '#334155',
                fontWeight: p === currentPage ? 700 : 500,
                fontSize: '0.85rem',
                cursor: 'pointer',
              }}
              data-testid={`${testPrefix}-pagination-page-${p}`}
            >
              {p}
            </button>
          ))}

          <button
            type="button"
            disabled={currentPage >= totalPages}
            onClick={() => onPageChange(currentPage + 1)}
            style={{
              padding: '0.4rem 0.75rem',
              borderRadius: '6px',
              border: '1px solid #cbd5e1',
              background: currentPage >= totalPages ? '#f8fafc' : '#ffffff',
              color: currentPage >= totalPages ? '#94a3b8' : '#334155',
              cursor: currentPage >= totalPages ? 'not-allowed' : 'pointer',
              fontSize: '0.85rem',
              fontWeight: 500,
            }}
            data-testid={`${testPrefix}-pagination-next`}
          >
            {t('history.next')} ›
          </button>
        </div>
      </div>
    );
  };

  return (
    <div className="ring-design-container" data-testid="member-design-history-page">
      <div className="ring-design-header">
        <h1 className="ring-design-title">{t('memberHistoryTitle')}</h1>
        <p className="ring-design-subtitle">{t('memberHistorySubtitle')}</p>
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
            🔍 {t('history.lookupLabel')}
          </label>
          <input
            id="quick-lookup-input"
            type="text"
            value={lookupCode}
            onChange={(e) => {
              setLookupCode(e.target.value);
              if (lookupError) setLookupError('');
            }}
            placeholder={t('history.lookupPlaceholder')}
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
            {t('history.lookupBtn')}
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
          💍 {t('history.tabWorkshop')}
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
          ✍️ {t('history.tabCustom')}
        </button>
      </div>

      {loading && (
        <div style={{ textAlign: 'center', padding: '3rem', color: '#64748b' }}>
          {t('history.loading')}
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
              <p>{t('noDesignsYet')}</p>
              <button
                type="button"
                className="ring-btn-primary"
                style={{ width: 'auto', marginTop: '1rem' }}
                onClick={() => navigate('/workshops/PKG-COUPLE-SILVER/design/configure')}
              >
                {t('createFirstDesign')}
              </button>
            </div>
          )}

          <div style={{ display: 'flex', flexDirection: 'column', gap: '1.25rem' }}>
            {paginatedDesigns.map((d) => {
              const snapshot = d.componentSnapshot || {};
              const matLabel = materialLabel(snapshot.materialReference || 'MAT-SILVER-925', t);
              const shapeLabel = shapeLabelOf(snapshot.shapeReference || 'SHP-CLASSIC-ROUND', t);
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
                    gap: '1.25rem',
                    minHeight: '88px',
                    boxSizing: 'border-box',
                    padding: '1.25rem',
                  }}
                  data-testid={`design-item-${d.designId}`}
                >
                  <div style={{ flex: '1 1 auto', minWidth: 0, overflow: 'hidden' }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem', marginBottom: '0.35rem' }}>
                      <span style={{ fontWeight: 700, fontSize: '1.1rem', color: '#0f172a' }}>{d.designId}</span>
                      <span className={`ring-status-badge ${d.status?.toLowerCase()}`}>{statusLabel(d.status)}</span>
                      <span style={{ fontSize: '0.85rem', color: '#64748b' }}>
                        {t('versionLabel')} {d.designVersion}
                      </span>
                    </div>
                    <div
                      style={{
                        fontSize: '0.9rem',
                        color: '#64748b',
                        lineHeight: '1.4',
                        maxHeight: '2.8em',
                        overflow: 'hidden',
                        textOverflow: 'ellipsis',
                        display: '-webkit-box',
                        WebkitLineClamp: 2,
                        WebkitBoxOrient: 'vertical',
                        wordBreak: 'break-word',
                      }}
                      title={`${t('history.package')}: ${d.packageReference} · ${t('materialSummary')}: ${matLabel} · ${t('history.shape')}: ${shapeLabel} · ${t('sizeSummary')}: ${sizeVal}${gemCount > 0 ? ` · ${t('history.gems')}: ${t('stoneCount', { count: gemCount })}` : ''}`}
                    >
                      {t('history.package')}: <strong>{d.packageReference}</strong> · {t('materialSummary')}: <strong>{matLabel}</strong> · {t('history.shape')}: <strong>{shapeLabel}</strong> · {t('sizeSummary')}: <strong>{sizeVal}</strong>
                      {gemCount > 0 && <span> · {t('history.gems')}: <strong>{t('stoneCount', { count: gemCount })}</strong></span>}
                    </div>
                  </div>

                  <div style={{ display: 'flex', alignItems: 'center', gap: '1.25rem', flexShrink: 0 }}>
                    <div style={{ textAlign: 'right', flexShrink: 0 }}>
                      <div style={{ fontSize: '0.8rem', color: '#64748b' }}>{t('estimatedPriceLabel')}</div>
                      <div style={{ fontSize: '1.1rem', fontWeight: 700, color: '#b45309', whiteSpace: 'nowrap' }}>
                        {wholeMoney(fmt, price)}
                      </div>
                    </div>

                    <div style={{ display: 'flex', gap: '0.5rem', alignItems: 'center', flexWrap: 'nowrap', flexShrink: 0 }}>
                      <button
                        type="button"
                        className="ring-btn-secondary"
                        style={{ whiteSpace: 'nowrap', flexShrink: 0 }}
                        onClick={() =>
                          navigate(`/workshops/${encodeURIComponent(d.packageReference)}/design/${encodeURIComponent(d.designId)}`)
                        }
                      >
                        {t('history.viewDetails')}
                      </button>
                      <button
                        type="button"
                        className="ring-btn-secondary"
                        style={{ whiteSpace: 'nowrap', flexShrink: 0 }}
                        onClick={() =>
                          navigate(
                            `/workshops/${encodeURIComponent(d.packageReference)}/design/configure?mode=EDIT&designId=${encodeURIComponent(
                              d.designId
                            )}`
                          )
                        }
                      >
                        {t('history.edit')}
                      </button>
                      <button
                        type="button"
                        className="ring-btn-secondary"
                        style={{ whiteSpace: 'nowrap', flexShrink: 0, color: '#0284c7', borderColor: '#bae6fd' }}
                        onClick={() => navigate(`/design-requests/new?designReference=${encodeURIComponent(d.designId)}`)}
                        data-testid={`craft-request-btn-${d.designId}`}
                        title={t('history.craftTitle')}
                      >
                        {t('history.craft')}
                      </button>
                      <button
                        type="button"
                        style={{
                          background: '#dc2626',
                          color: '#ffffff',
                          border: 'none',
                          borderRadius: '6px',
                          padding: '0.45rem 0.85rem',
                          fontSize: '0.85rem',
                          fontWeight: 600,
                          cursor: 'pointer',
                          display: 'inline-flex',
                          alignItems: 'center',
                          gap: '0.35rem',
                          whiteSpace: 'nowrap',
                          flexShrink: 0,
                          transition: 'background 0.15s ease',
                        }}
                        onMouseEnter={(e) => (e.currentTarget.style.background = '#b91c1c')}
                        onMouseLeave={(e) => (e.currentTarget.style.background = '#dc2626')}
                        onClick={() => setDesignToDelete(d)}
                        data-testid={`delete-design-btn-${d.designId}`}
                        title={t('history.deleteTitle')}
                      >
                        <span>🗑️</span>
                        <span>{t('history.delete')}</span>
                      </button>
                    </div>
                  </div>
                </div>
              );
            })}
          </div>

          {renderPagination(workshopPage, totalWorkshopPages, designs.length, setWorkshopPage, 'workshop')}
        </>
      )}

      {/* Tab 2: Custom Design Requests */}
      {activeTab === 'custom' && (
        <>
          {!loading && !error && customRequests.length === 0 && (
            <div style={{ textAlign: 'center', padding: '3rem', color: '#64748b' }}>
              <p>{t('history.noCustom')}</p>
              <button
                type="button"
                className="ring-btn-primary"
                style={{ width: 'auto', marginTop: '1rem' }}
                onClick={() => navigate('/design-requests/new')}
              >
                {t('history.newRequest')}
              </button>
            </div>
          )}

          <div style={{ display: 'flex', flexDirection: 'column', gap: '1.25rem' }}>
            {paginatedCustomRequests.map((req) => {
              const statusInfo = getReviewStatusBadge(req.reviewStatus);
              return (
                <div
                  key={req.id}
                  className="ring-section-card"
                  style={{
                    display: 'flex',
                    justifyContent: 'space-between',
                    alignItems: 'center',
                    gap: '1.25rem',
                    minHeight: '88px',
                    boxSizing: 'border-box',
                    padding: '1.25rem',
                  }}
                  data-testid={`custom-req-item-${req.id}`}
                >
                  <div style={{ flex: '1 1 auto', minWidth: 0, overflow: 'hidden' }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem', marginBottom: '0.35rem', flexWrap: 'wrap' }}>
                      <span style={{ fontWeight: 700, fontSize: '1.1rem', color: '#0f172a' }}>
                        {req.requestReference || req.id}
                      </span>
                      <span className={statusInfo.className}>
                        {statusInfo.label}
                      </span>
                      {req.decisionExplanation && (
                        <span style={{ fontSize: '0.85rem', color: '#0369a1', fontWeight: 500 }}>
                          {t('history.replied')}
                        </span>
                      )}
                    </div>
                    {req.description && (
                      <p
                        style={{
                          margin: '0.25rem 0 0 0',
                          fontSize: '0.9rem',
                          color: '#475569',
                          fontStyle: 'italic',
                          lineHeight: '1.4',
                          maxHeight: '2.8em',
                          overflow: 'hidden',
                          textOverflow: 'ellipsis',
                          display: '-webkit-box',
                          WebkitLineClamp: 2,
                          WebkitBoxOrient: 'vertical',
                          wordBreak: 'break-word',
                        }}
                      >
                        &ldquo;{req.description}&rdquo;
                      </p>
                    )}
                  </div>

                  <div style={{ display: 'flex', gap: '0.75rem', alignItems: 'center', flexShrink: 0 }}>
                    <button
                      type="button"
                      className="ring-btn-secondary"
                      style={{ whiteSpace: 'nowrap', flexShrink: 0 }}
                      onClick={() => navigate(`/design-requests/${encodeURIComponent(req.id)}`)}
                    >
                      {t('history.viewDetails')}
                    </button>
                  </div>
                </div>
              );
            })}
          </div>

          {renderPagination(customPage, totalCustomPages, customRequests.length, setCustomPage, 'custom')}
        </>
      )}

      {/* Soft Delete Warning Modal Dialog */}
      {designToDelete && (
        <div
          role="dialog"
          aria-modal="true"
          aria-labelledby="delete-dialog-title"
          style={{
            position: 'fixed',
            top: 0,
            left: 0,
            right: 0,
            bottom: 0,
            backgroundColor: 'rgba(15, 23, 42, 0.65)',
            backdropFilter: 'blur(3px)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            zIndex: 9999,
            padding: '1rem',
          }}
          data-testid="delete-confirm-modal"
        >
          <div
            style={{
              background: '#ffffff',
              borderRadius: '12px',
              maxWidth: '460px',
              width: '100%',
              padding: '1.75rem',
              boxShadow: '0 20px 25px -5px rgba(0, 0, 0, 0.2), 0 10px 10px -5px rgba(0, 0, 0, 0.08)',
            }}
          >
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem', marginBottom: '1rem' }}>
              <div
                style={{
                  width: '42px',
                  height: '42px',
                  borderRadius: '50%',
                  backgroundColor: '#fee2e2',
                  color: '#dc2626',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  fontSize: '1.35rem',
                  flexShrink: 0,
                }}
              >
                🗑️
              </div>
              <h3 id="delete-dialog-title" style={{ margin: 0, fontSize: '1.15rem', fontWeight: 700, color: '#1e293b' }}>
                {t('history.deleteDialogTitle')}
              </h3>
            </div>

            <p style={{ fontSize: '0.925rem', color: '#475569', lineHeight: 1.5, margin: '0 0 1.25rem 0' }}>
              {t('history.deleteWarning', { id: designToDelete.designId })}
            </p>

            {deleteError && (
              <div style={{ color: '#dc2626', fontSize: '0.875rem', marginBottom: '1rem', fontWeight: 500 }}>
                {deleteError}
              </div>
            )}

            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '0.75rem' }}>
              <button
                type="button"
                onClick={() => {
                  setDesignToDelete(null);
                  setDeleteError(null);
                }}
                disabled={deleting}
                style={{
                  padding: '0.5rem 1.1rem',
                  borderRadius: '6px',
                  border: '1px solid #cbd5e1',
                  background: '#ffffff',
                  color: '#475569',
                  fontWeight: 600,
                  fontSize: '0.875rem',
                  cursor: 'pointer',
                }}
                data-testid="cancel-delete-btn"
              >
                {ts('cancel')}
              </button>
              <button
                type="button"
                onClick={handleConfirmDelete}
                disabled={deleting}
                style={{
                  padding: '0.5rem 1.1rem',
                  borderRadius: '6px',
                  border: 'none',
                  background: '#dc2626',
                  color: '#ffffff',
                  fontWeight: 600,
                  fontSize: '0.875rem',
                  cursor: deleting ? 'not-allowed' : 'pointer',
                  opacity: deleting ? 0.7 : 1,
                  display: 'inline-flex',
                  alignItems: 'center',
                  gap: '0.35rem',
                }}
                data-testid="confirm-delete-btn"
              >
                <span>🗑️</span>
                <span>{deleting ? t('history.deleting') : t('history.deleteConfirm')}</span>
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
