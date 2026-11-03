import React, { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router';
import { useAuth } from '../../shared/auth/AuthProvider';
import { useOtaConnectivityApi } from '../api/useOtaConnectivityApi';
import { Loading, ErrorNotice, SuccessNotice } from '../../shared/ui/components';
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

  const loadWorkItems = useCallback(async (status) => {
    try {
      setLoading(true);
      setError(null);
      const items = await api.listReconciliationWorkItems(status);
      setWorkItems(items || []);
    } catch (err) {
      setError(err);
    } finally {
      setLoading(false);
    }
  }, [api]);

  useEffect(() => {
    if (authStatus === 'signed-in') {
      loadWorkItems(selectedStatus);
    }
  }, [authStatus, selectedStatus, loadWorkItems]);

  const handleFilterChange = (status) => {
    setSelectedStatus(status);
  };

  const handleResolveDecision = async ({ resolution, reason }) => {
    if (!resolveModal.item) return;
    await api.resolveReconciliationWorkItem(resolveModal.item.workItemId, { resolution, reason });
    setSuccessMessage(`Resolution "${resolution}" recorded for item ${resolveModal.item.workItemId}.`);
    await loadWorkItems(selectedStatus);
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
      <div className="ota-container">
        <div className="empty-state" role="status">
          <p>Authentication required to access OTA Reconciliation Queue.</p>
          <a className="button primary" href="/login">Sign In</a>
        </div>
      </div>
    );
  }

  return (
    <div className="ota-container">
      <div className="ota-header">
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
      <ErrorNotice error={error} onRetry={() => loadWorkItems(selectedStatus)} onReload={() => loadWorkItems(selectedStatus)} />

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
      </div>

      {loading ? (
        <Loading />
      ) : workItems.length === 0 ? (
        <div className="empty-state" role="status">
          <p>No reconciliation work items found matching status "{selectedStatus}".</p>
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
    </div>
  );
}
