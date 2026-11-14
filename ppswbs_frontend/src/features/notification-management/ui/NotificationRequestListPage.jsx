import React, { useState } from 'react';
import { useNotificationRequests } from '../application/useNotificationRequests';
import { useRole } from '../../../shared/auth/useRole';
import NotificationRequestDetailDrawer from './NotificationRequestDetailDrawer';
import OperationsSidebar from '../../../shared/ui/OperationsSidebar';
import './notification.css';

function stateBadgeClass(state) {
  if (!state) return 'nm-badge-pending';
  return `nm-badge-${state.toLowerCase()}`;
}

export function NotificationRequestListPage() {
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
    <div className="ota-workspace">
      <OperationsSidebar />
      <main className="ota-main">
        <div className="nm-container">
          <div className="nm-header">
            <div className="nm-title-group">
              <h1>Nhật ký gửi thông báo (Delivery Logs)</h1>
              <p>Chẩn đoán hàng đợi gửi email giao dịch, số lần thử và nhật ký kiểm toán</p>
            </div>
            <div className="nm-actions">
              <label style={{ display: 'inline-flex', alignItems: 'center', gap: '0.4rem', fontSize: '0.8125rem', color: '#475569', cursor: 'pointer' }}>
                <input
                  type="checkbox"
                  checked={autoRefresh}
                  onChange={(e) => setAutoRefresh(e.target.checked)}
                />
                <span>Tự động làm mới (30s)</span>
              </label>

              <button
                type="button"
                className="nm-btn"
                onClick={reload}
                disabled={loading}
              >
                {loading ? 'Đang tải…' : 'Làm mới'}
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
                Thử lại
              </button>
            </div>
          )}

          {/* Filter Bar */}
          <div className="nm-card" style={{ marginBottom: '1rem' }}>
            <div className="nm-filter-bar">
              <div className="nm-filter-item">
                <label htmlFor="filter-state">Trạng thái gửi</label>
                <select
                  id="filter-state"
                  className="nm-select"
                  value={filters.deliveryState}
                  onChange={(e) => setFilter('deliveryState', e.target.value)}
                >
                  <option value="">Tất cả trạng thái</option>
                  <option value="PENDING">Chờ xử lý (PENDING)</option>
                  <option value="PROCESSING">Đang gửi (PROCESSING)</option>
                  <option value="RETRY_SCHEDULED">Đang chờ thử lại (RETRY_SCHEDULED)</option>
                  <option value="SENT">Đã gửi thành công (SENT)</option>
                  <option value="FAILED">Gửi thất bại (FAILED)</option>
                  <option value="UNKNOWN_OUTCOME">Kết quả chưa xác định (UNKNOWN_OUTCOME)</option>
                  <option value="CANCELLED">Đã hủy (CANCELLED)</option>
                </select>
              </div>

              <div className="nm-filter-item">
                <label htmlFor="filter-event">Mã sự kiện / đơn / booking</label>
                <input
                  id="filter-event"
                  type="text"
                  className="nm-input"
                  placeholder="VD: BK-2026..., ORD-..."
                  value={filters.businessEventReference}
                  onChange={(e) => setFilter('businessEventReference', e.target.value)}
                />
              </div>

              <div className="nm-filter-item">
                <label htmlFor="filter-template">Mã mẫu thư</label>
                <input
                  id="filter-template"
                  type="text"
                  className="nm-input"
                  placeholder="BOOKING_CONFIRMED"
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
                  Xóa bộ lọc
                </button>
              </div>
            </div>
          </div>

          {/* Table */}
          <div className="nm-card">
            {loading ? (
              <div style={{ padding: '3rem 0', textAlign: 'center', color: '#64748b' }} role="status">
                Đang tải dữ liệu nhật ký gửi thư…
              </div>
            ) : data.content.length === 0 ? (
              <div style={{ padding: '3rem 0', textAlign: 'center', color: '#64748b' }} role="status">
                Không tìm thấy yêu cầu gửi thư nào khớp với bộ lọc.
              </div>
            ) : (
              <div className="nm-table-container">
                <table className="nm-table">
                  <thead>
                    <tr>
                      <th>ID</th>
                      <th>Mã sự kiện</th>
                      <th>Mẫu thư</th>
                      <th>Người nhận (đã che)</th>
                      <th>Trạng thái</th>
                      <th>Lần thử</th>
                      <th>Thời gian tạo</th>
                      <th style={{ textAlign: 'right' }}>Thao tác</th>
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
                          {item.createdAt ? new Date(item.createdAt).toLocaleString('vi-VN') : '—'}
                        </td>
                        <td style={{ textAlign: 'right' }}>
                          <button
                            type="button"
                            className="nm-btn nm-btn-sm"
                            aria-label={`Xem chi tiết #${item.id}`}
                            onClick={(e) => {
                              e.stopPropagation();
                              handleOpenDetail(item.id);
                            }}
                          >
                            Xem chi tiết
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
                  Tổng cộng <strong>{data.totalElements}</strong> yêu cầu &bull; Trang{' '}
                  <strong>{page + 1}</strong> / {data.totalPages}
                </span>
                <div style={{ display: 'inline-flex', gap: '0.5rem' }}>
                  <button
                    type="button"
                    className="nm-btn nm-btn-sm"
                    onClick={() => setPage((p) => Math.max(0, p - 1))}
                    disabled={page === 0}
                  >
                    Trang trước
                  </button>
                  <button
                    type="button"
                    className="nm-btn nm-btn-sm"
                    onClick={() => setPage((p) => Math.min(data.totalPages - 1, p + 1))}
                    disabled={page >= data.totalPages - 1}
                  >
                    Trang sau
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

