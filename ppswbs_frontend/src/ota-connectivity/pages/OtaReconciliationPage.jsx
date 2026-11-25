import React, { useCallback, useEffect, useState } from 'react';
import { useUrlFilters } from '../../shared/ui/useUrlFilters';
import { Link } from 'react-router';
import { useAuth } from '../../shared/auth/AuthProvider';
import { useOtaConnectivityApi } from '../api/useOtaConnectivityApi';
import { Loading, ErrorNotice, SuccessNotice } from '../../shared/ui/components';
import { OperationsAuthWorkspace } from '../../shared/ui/OperationsAuthWorkspace';
import { useT, useEnumLabel } from '../../shared/i18n/I18nProvider';
import { otaMessages } from '../messages';
import { ResolveWorkItemModal } from '../components/ResolveWorkItemModal';
import '../ota-connectivity.css';

const FILTER_STATUSES = ['ALL', 'NEEDS_ATTENTION', 'OPEN', 'IN_PROGRESS', 'RESOLVED', 'ESCALATED'];

export default function OtaReconciliationPage() {
  const { status: authStatus } = useAuth();
  const api = useOtaConnectivityApi();
  const t = useT(otaMessages);
  const workStatusLabel = useEnumLabel(otaMessages, 'workStatus');
  const categoryLabel = useEnumLabel(otaMessages, 'category');

  const [workItems, setWorkItems] = useState([]);
  const [{ status: selectedStatus, category: selectedCategory }, setUrlFilters] = useUrlFilters({ status: 'ALL', category: '' });
  const setSelectedStatus = value => setUrlFilters(previous => ({ ...previous, status: value }));
  const setSelectedCategory = value => setUrlFilters(previous => ({ ...previous, category: value }));
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [successMessage, setSuccessMessage] = useState(null);

  const [resolveModal, setResolveModal] = useState({ open: false, item: null });

  // 003.4 T119: the applied category filter (sent as `category`) and what is typed before applying it.
  const [categoryInput, setCategoryInput] = useState(selectedCategory);

  const loadWorkItems = useCallback(async (status, category) => {
    try {
      setLoading(true);
      setError(null);
      const items = await api.listReconciliationWorkItems(status, category);
      setWorkItems(items || []);
    } catch (err) {
      setError(err);
    } finally {
      setLoading(false);
    }
  }, [api]);

  useEffect(() => {
    if (authStatus === 'signed-in') {
      loadWorkItems(selectedStatus, selectedCategory);
    }
  }, [authStatus, selectedStatus, selectedCategory, loadWorkItems]);

  const applyCategory = (value) => {
    const next = value.trim().toUpperCase();
    setCategoryInput(next);
    setSelectedCategory(next);
  };

  const handleFilterChange = (status) => {
    setSelectedStatus(status);
  };

  const handleResolveDecision = async ({ resolution, reason }) => {
    if (!resolveModal.item) return;
    await api.resolveReconciliationWorkItem(resolveModal.item.workItemId, { resolution, reason });
    setSuccessMessage(t('recon.resolved', { resolution, id: resolveModal.item.workItemId }));
    await loadWorkItems(selectedStatus, selectedCategory);
  };

  const getStatusBadge = (status) => {
    const map = {
      OPEN: 'badge-open',
      IN_PROGRESS: 'badge-in-progress',
      RESOLVED: 'badge-resolved',
      ESCALATED: 'badge-escalated',
    };
    return <span className={`badge ${map[status] || 'badge-open'}`}>{workStatusLabel(status)}</span>;
  };

  if (authStatus === 'loading') {
    return <Loading />;
  }

  if (authStatus !== 'signed-in') {
    return (
      <OperationsAuthWorkspace
        topline="MLAND / OTA CONNECTIVITY"
        message={t('auth.reconciliation')}
      />
    );
  }

  return (
    <div className="ota-workspace">
      
      <main className="ota-main">
        <div className="ota-topline">MLAND / OTA CONNECTIVITY <span>● {t('recon.sub')}</span></div>
        <div className="ota-header" style={{ marginTop: '24px' }}>
        <div>
          <p className="small muted">
            {t('recon.intro')}
          </p>
        </div>
      </div>


      {successMessage && <SuccessNotice>{successMessage}</SuccessNotice>}
      <ErrorNotice error={error} onRetry={() => loadWorkItems(selectedStatus, selectedCategory)} onReload={() => loadWorkItems(selectedStatus, selectedCategory)} />

      {/* Filter Bar */}
      <div className="filter-bar">
        <div className="filter-group">
          <span style={{ fontWeight: 600, fontSize: '13px' }}>{t('recon.filterStatus')}</span>
          {FILTER_STATUSES.map((status) => (
            <button
              key={status}
              type="button"
              className={`button small ${selectedStatus === status ? 'primary' : 'outline'}`}
              onClick={() => handleFilterChange(status)}
              disabled={loading}
            >
              {workStatusLabel(status)}
            </button>
          ))}
        </div>
        {/* Each work item now carries category (contract enum). The filter stays free text plus the INBOUND_EVENT shortcut. */}
        <form
          className="filter-group"
          aria-label={t('recon.filterCategory')}
          onSubmit={(e) => { e.preventDefault(); applyCategory(categoryInput); }}
        >
          <label htmlFor="ota-work-item-category" style={{ fontWeight: 600, fontSize: '13px' }}>{t('recon.category')}</label>
          <input
            id="ota-work-item-category"
            value={categoryInput}
            onChange={(e) => setCategoryInput(e.target.value)}
            placeholder={t('field.example', { value: 'INBOUND_EVENT' })}
            maxLength={32}
            disabled={loading}
          />
          <button type="submit" className="button small outline" disabled={loading}>{t('recon.apply')}</button>
          <button
            type="button"
            className={`button small ${selectedCategory === 'INBOUND_EVENT' ? 'primary' : 'outline'}`}
            onClick={() => applyCategory('INBOUND_EVENT')}
            disabled={loading}
          >
            {categoryLabel('INBOUND_EVENT')}
          </button>
          {selectedCategory && (
            <button type="button" className="button small outline" onClick={() => applyCategory('')} disabled={loading}>
              {t('recon.allCategories')}
            </button>
          )}
        </form>
      </div>

      {loading ? (
        <Loading />
      ) : workItems.length === 0 ? (
        <div className="empty-state" role="status">
          <p>
            {t('recon.empty', {
              status: selectedStatus,
              category: selectedCategory ? t('recon.emptyCategory', { category: selectedCategory }) : '',
            })}
          </p>
        </div>
      ) : (
        <div className="work-items-list">
          {workItems.map((item) => (
            <div key={item.workItemId} className="mapping-card">
              <div className="mapping-card-header">
                <div className="mapping-title">
                  <span className="mono" style={{ fontSize: '14px' }}>{t('recon.item', { id: item.workItemId })}</span>
                  {getStatusBadge(item.status)}
                  {item.safeSummary?.includes('SUSPENDED') && (
                    <span className="badge badge-suspended">{t('recon.mappingSuspended')}</span>
                  )}
                  {item.safeSummary?.includes('SALES_CLOSED') && (
                    <span className="badge badge-closed">{t('recon.salesClosed')}</span>
                  )}
                </div>
                <div className="mapping-actions">
                  {item.status !== 'RESOLVED' && (
                    <button
                      type="button"
                      className="button small primary"
                      onClick={() => setResolveModal({ open: true, item })}
                    >
                      {t('recon.resolve')}
                    </button>
                  )}
                </div>
              </div>

              <div className="mapping-grid">
                <div className="mapping-field">
                  <span className="mapping-label">{t('recon.field.type')}</span>
                  <span className="mapping-value mono">{categoryLabel(item.category)}</span>
                </div>
                <div className="mapping-field">
                  <span className="mapping-label">{t('recon.field.mapping')}</span>
                  <span className="mapping-value mono">{item.mappingId || t('recon.global')}</span>
                </div>
                <div className="mapping-field">
                  <span className="mapping-label">{t('recon.field.correlation')}</span>
                  <span className="mapping-value mono">{item.correlationId || '—'}</span>
                </div>
                <div className="mapping-field" style={{ gridColumn: 'span 2' }}>
                  <span className="mapping-label">{t('recon.field.summary')}</span>
                  <span className="mapping-value">{item.safeSummary || t('recon.noSummary')}</span>
                </div>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* Resolve Modal */}
      {resolveModal.open && (
        <ResolveWorkItemModal
          open={resolveModal.open}
          workItem={resolveModal.item}
          onClose={() => setResolveModal({ open: false, item: null })}
          onResolve={handleResolveDecision}
        />
      )}
      </main>
    </div>
  );
}
