import React, { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router';
import { useAuth } from '../../shared/auth/AuthProvider';
import { useOtaConnectivityApi } from '../api/useOtaConnectivityApi';
import { Loading, ErrorNotice, SuccessNotice } from '../../shared/ui/components';
import { OperationsSidebar } from '../../shared/ui/OperationsSidebar';
import { OperationsAuthWorkspace } from '../../shared/ui/OperationsAuthWorkspace';
import { ResolveWorkItemModal } from '../components/ResolveWorkItemModal';
import '../ota-connectivity.css';

const FILTER_STATUSES = ['ALL', 'OPEN', 'IN_PROGRESS', 'RESOLVED', 'ESCALATED'];

export default function OtaReconciliationPage() {
  const { status: authStatus } = useAuth();
  const api = useOtaConnectivityApi();

  const [workItems, setWorkItems] = useState([]);
  const [selectedStatus, setSelectedStatus] = useState('ALL');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [successMessage, setSuccessMessage] = useState(null);

  const [resolveModal, setResolveModal] = useState({ open: false, item: null });

  // 003.4 T119: the applied category filter (sent as `category`) and what is typed before applying it.
  const [selectedCategory, setSelectedCategory] = useState('');
  const [categoryInput, setCategoryInput] = useState('');

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
    setSuccessMessage(`Resolution "${resolution}" recorded for item ${resolveModal.item.workItemId}.`);
    await loadWorkItems(selectedStatus, selectedCategory);
  };

  const getStatusBadge = (status) => {
    const map = {
      OPEN: 'badge-open',
      IN_PROGRESS: 'badge-in-progress',
      RESOLVED: 'badge-resolved',
      ESCALATED: 'badge-escalated',
    };
    return <span className={`badge ${map[status] || 'badge-open'}`}>{status}</span>;
  };

  if (authStatus === 'loading') {
    return <Loading />;
  }

  if (authStatus !== 'signed-in') {
    return (
      <OperationsAuthWorkspace
        topline="MLAND / OTA CONNECTIVITY"
        message="Authentication required to access OTA Reconciliation Queue."
      />
    );
  }

  return (
    <div className="ota-workspace">
      <OperationsSidebar />
      <main className="ota-main">
        <div className="ota-topline">MLAND / OTA CONNECTIVITY <span>● Discrepancy queue</span></div>
        <div className="ota-header" style={{ marginTop: '24px' }}>
        <div>
          <h1>OTA Reconciliation Queue</h1>
          <p className="small muted">
            Review and resolve channel mapping synchronization gaps, suspensions, and discrepancies.
          </p>
        </div>
      </div>

      <nav className="ota-nav-tabs" aria-label="OTA Navigation">
        <Link to="/ota-connectivity/channels" className="ota-nav-tab">
          Channel Mappings
        </Link>
        <Link to="/ota-connectivity/reconciliation" className="ota-nav-tab active">
          Reconciliation Queue
        </Link>
      </nav>

      {successMessage && <SuccessNotice>{successMessage}</SuccessNotice>}
      <ErrorNotice error={error} onRetry={() => loadWorkItems(selectedStatus, selectedCategory)} onReload={() => loadWorkItems(selectedStatus, selectedCategory)} />

      {/* Filter Bar */}
      <div className="filter-bar">
        <div className="filter-group">
          <span style={{ fontWeight: 600, fontSize: '13px' }}>Filter by Status:</span>
          {FILTER_STATUSES.map((status) => (
            <button
              key={status}
              type="button"
              className={`button small ${selectedStatus === status ? 'primary' : 'outline'}`}
              onClick={() => handleFilterChange(status)}
              disabled={loading}
            >
              {status}
            </button>
          ))}
        </div>
        {/* The contract does not list the categories yet (ERROR-LOG FE-DESIGN-0005), so the filter is free text plus
            the one value it names; no "category" column until the work item carries the field. */}
        <form
          className="filter-group"
          aria-label="Filter by category"
          onSubmit={(e) => { e.preventDefault(); applyCategory(categoryInput); }}
        >
          <label htmlFor="ota-work-item-category" style={{ fontWeight: 600, fontSize: '13px' }}>Category:</label>
          <input
            id="ota-work-item-category"
            value={categoryInput}
            onChange={(e) => setCategoryInput(e.target.value)}
            placeholder="e.g. INBOUND_EVENT"
            maxLength={32}
            disabled={loading}
          />
          <button type="submit" className="button small outline" disabled={loading}>Apply</button>
          <button
            type="button"
            className={`button small ${selectedCategory === 'INBOUND_EVENT' ? 'primary' : 'outline'}`}
            onClick={() => applyCategory('INBOUND_EVENT')}
            disabled={loading}
          >
            INBOUND_EVENT
          </button>
          {selectedCategory && (
            <button type="button" className="button small outline" onClick={() => applyCategory('')} disabled={loading}>
              All categories
            </button>
          )}
        </form>
      </div>

      {loading ? (
        <Loading />
      ) : workItems.length === 0 ? (
        <div className="empty-state" role="status">
          <p>
            No reconciliation work items found matching status "{selectedStatus}"
            {selectedCategory ? ` and category "${selectedCategory}"` : ''}.
          </p>
        </div>
      ) : (
        <div className="work-items-list">
          {workItems.map((item) => (
            <div key={item.workItemId} className="mapping-card">
              <div className="mapping-card-header">
                <div className="mapping-title">
                  <span className="mono" style={{ fontSize: '14px' }}>Item: {item.workItemId}</span>
                  {getStatusBadge(item.status)}
                  {item.safeSummary?.includes('SUSPENDED') && (
                    <span className="badge badge-suspended">MAPPING SUSPENDED</span>
                  )}
                  {item.safeSummary?.includes('SALES_CLOSED') && (
                    <span className="badge badge-closed">SALES CLOSED</span>
                  )}
                </div>
                <div className="mapping-actions">
                  {item.status !== 'RESOLVED' && (
                    <button
                      type="button"
                      className="button small primary"
                      onClick={() => setResolveModal({ open: true, item })}
                    >
                      Resolve Item
                    </button>
                  )}
                </div>
              </div>

              <div className="mapping-grid">
                <div className="mapping-field">
                  <span className="mapping-label">Linked Mapping ID</span>
                  <span className="mapping-value mono">{item.mappingId || 'None (Global)'}</span>
                </div>
                <div className="mapping-field">
                  <span className="mapping-label">Correlation ID</span>
                  <span className="mapping-value mono">{item.correlationId || '—'}</span>
                </div>
                <div className="mapping-field" style={{ gridColumn: 'span 2' }}>
                  <span className="mapping-label">Summary / Reason</span>
                  <span className="mapping-value">{item.safeSummary || 'No summary recorded'}</span>
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
