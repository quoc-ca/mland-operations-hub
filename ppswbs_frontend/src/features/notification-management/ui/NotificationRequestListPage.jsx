import React, { useState } from 'react';
import { useNotificationRequests } from '../application/useNotificationRequests';
import { useRole } from '../../../shared/auth/useRole';
import { useT, useEnumLabel, useFormat } from '../../../shared/i18n/I18nProvider';
import { sharedMessages } from '../../../shared/ui/messages';
import { notificationMessages } from '../messages';
import { renderRich } from './richText';
import NotificationRequestDetailDrawer from './NotificationRequestDetailDrawer';
import './notification.css';

const DELIVERY_STATES = ['PENDING', 'PROCESSING', 'RETRY_SCHEDULED', 'SENT', 'FAILED', 'UNKNOWN_OUTCOME', 'CANCELLED'];

function stateBadgeClass(state) {
  if (!state) return 'nm-badge-pending';
  return `nm-badge-${state.toLowerCase()}`;
}

export function NotificationRequestListPage() {
  const t = useT(notificationMessages);
  const ts = useT(sharedMessages);
  const stateLabel = useEnumLabel(notificationMessages, 'state');
  const fmt = useFormat();
  const userRole = useRole() || 'MANAGER';
  const {
    data,
    page,
    setPage,
    filters,
    setFilter,
    resetFilters,
    autoRefresh,
    setAutoRefresh,
    loading,
    error,
    reload,
  } = useNotificationRequests();

  const [selectedRequestId, setSelectedRequestId] = useState(null);
  const [drawerOpen, setDrawerOpen] = useState(false);

  const handleOpenDetail = (id) => {
    setSelectedRequestId(id);
    setDrawerOpen(true);
  };

  const handleCloseDetail = () => {
    setDrawerOpen(false);
    setSelectedRequestId(null);
  };

  return (
    <div className="wsp-workspace">
      
      <main className="wsp-main">
        <div className="nm-container">
          <div className="nm-header">
            <div className="nm-title-group">
              <p>{t('list.subtitle')}</p>
            </div>
            <div className="nm-actions">
              <label style={{ display: 'inline-flex', alignItems: 'center', gap: '0.4rem', fontSize: '0.8125rem', color: '#475569', cursor: 'pointer' }}>
                <input
                  type="checkbox"
                  checked={autoRefresh}
                  onChange={(e) => setAutoRefresh(e.target.checked)}
                />
                <span>{t('list.autoRefresh')}</span>
              </label>

              <button
                type="button"
                className="nm-btn"
                onClick={reload}
                disabled={loading}
              >
                {loading ? ts('loading') : t('refresh')}
              </button>
            </div>
          </div>

          {error && (
            <div className="nm-alert nm-alert-danger" role="alert">
              <span>{error}</span>
              <button
                type="button"
                className="nm-btn nm-btn-sm"
                style={{ marginLeft: '1rem' }}
                onClick={reload}
              >
                {ts('retry')}
              </button>
            </div>
          )}

          {/* Filter Bar */}
          <div className="nm-card" style={{ marginBottom: '1rem' }}>
            <div className="nm-filter-bar">
              <div className="nm-filter-item">
                <label htmlFor="filter-state">{t('list.filterState')}</label>
                <select
                  id="filter-state"
                  className="nm-select"
                  value={filters.deliveryState}
                  onChange={(e) => setFilter('deliveryState', e.target.value)}
                >
                  <option value="">{t('list.filterAll')}</option>
                  {DELIVERY_STATES.map((value) => (
                    <option key={value} value={value}>{stateLabel(value)}</option>
                  ))}
                </select>
              </div>

              <div className="nm-filter-item">
                <label htmlFor="filter-event">{t('list.filterEvent')}</label>
                <input
                  id="filter-event"
                  type="text"
                  className="nm-input"
                  placeholder={t('list.eventPlaceholder')}
                  value={filters.businessEventReference}
                  onChange={(e) => setFilter('businessEventReference', e.target.value)}
                />
              </div>

              <div className="nm-filter-item">
                <label htmlFor="filter-template">{t('list.filterTemplate')}</label>
                <input
                  id="filter-template"
                  type="text"
                  className="nm-input"
                  placeholder={t('list.templatePlaceholder')}
                  value={filters.templateCode}
                  onChange={(e) => setFilter('templateCode', e.target.value)}
                />
              </div>

              <div className="nm-filter-item" style={{ alignSelf: 'flex-end' }}>
                <button
                  type="button"
                  className="nm-btn nm-btn-sm"
                  onClick={resetFilters}
                >
                  {t('list.clearFilters')}
                </button>
              </div>
            </div>
          </div>

          {/* Table */}
          <div className="nm-card">
            {loading ? (
              <div style={{ padding: '3rem 0', textAlign: 'center', color: '#64748b' }} role="status">
                {t('list.loadingData')}
              </div>
            ) : data.content.length === 0 ? (
              <div style={{ padding: '3rem 0', textAlign: 'center', color: '#64748b' }} role="status">
                {t('list.empty')}
              </div>
            ) : (
              <div className="nm-table-container">
                <table className="nm-table">
                  <thead>
                    <tr>
                      <th>{t('list.colId')}</th>
                      <th>{t('list.colEvent')}</th>
                      <th>{t('list.colTemplate')}</th>
                      <th>{t('list.colRecipient')}</th>
                      <th>{t('list.colState')}</th>
                      <th>{t('list.colAttempts')}</th>
                      <th>{t('list.colCreatedAt')}</th>
                      <th style={{ textAlign: 'right' }}>{t('col.actions')}</th>
                    </tr>
                  </thead>
                  <tbody>
                    {data.content.map((item) => (
                      <tr
                        key={item.id}
                        style={{ cursor: 'pointer' }}
                        onClick={() => handleOpenDetail(item.id)}
                      >
                        <td>#{item.id}</td>
                        <td>
                          <strong>{item.businessEventReference}</strong>
                        </td>
                        <td>
                          <span className="nm-tag">{item.templateCode}</span>
                        </td>
                        <td>{item.recipientAddressMasked}</td>
                        <td>
                          <span className={`nm-badge ${stateBadgeClass(item.deliveryState)}`}>
                            {item.deliveryState}
                          </span>
                        </td>
                        <td>{item.attemptCount}</td>
                        <td>
                          {fmt.dateTime(item.createdAt)}
                        </td>
                        <td style={{ textAlign: 'right' }}>
                          <button
                            type="button"
                            className="nm-btn nm-btn-sm"
                            aria-label={t('list.viewDetailAria', { id: item.id })}
                            onClick={(e) => {
                              e.stopPropagation();
                              handleOpenDetail(item.id);
                            }}
                          >
                            {t('list.viewDetail')}
                          </button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}

            {/* Pagination Controls */}
            {data.totalPages > 1 && (
              <div
                style={{
                  display: 'flex',
                  justifyContent: 'space-between',
                  alignItems: 'center',
                  padding: '0.75rem 1rem',
                  borderTop: '1px solid #e2e8f0',
                  fontSize: '0.875rem',
                }}
              >
                <span style={{ color: '#64748b' }}>
                  {renderRich(t('list.total', { total: data.totalElements, page: page + 1, pages: data.totalPages }))}
                </span>
                <div style={{ display: 'inline-flex', gap: '0.5rem' }}>
                  <button
                    type="button"
                    className="nm-btn nm-btn-sm"
                    onClick={() => setPage((p) => Math.max(0, p - 1))}
                    disabled={page === 0}
                  >
                    {t('list.prev')}
                  </button>
                  <button
                    type="button"
                    className="nm-btn nm-btn-sm"
                    onClick={() => setPage((p) => Math.min(data.totalPages - 1, p + 1))}
                    disabled={page >= data.totalPages - 1}
                  >
                    {t('list.next')}
                  </button>
                </div>
              </div>
            )}
          </div>
        </div>
      </main>

      {/* Detail Drawer */}
      <NotificationRequestDetailDrawer
        isOpen={drawerOpen}
        onClose={handleCloseDetail}
        requestId={selectedRequestId}
        userRole={userRole}
        onActionSuccess={reload}
      />
    </div>
  );
}

export default NotificationRequestListPage;

