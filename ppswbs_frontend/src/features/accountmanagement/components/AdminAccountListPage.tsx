import React, { useState, useEffect, useCallback } from 'react';
import { Link } from 'react-router';
import { useAdminAccounts } from '../hooks/useAdminAccounts';
import '../accountmanagement.css';

export const AdminAccountListPage: React.FC = () => {
  const { pagedResult, loading, error, listAccounts } = useAdminAccounts();

  const [searchTerm, setSearchTerm] = useState('');
  const [selectedRole, setSelectedRole] = useState<string>('');
  const [selectedStatus, setSelectedStatus] = useState<string>('');
  const [page, setPage] = useState<number>(0);

  const fetchList = useCallback(
    (pageIndex: number = page) => {
      listAccounts({
        q: searchTerm.trim() || undefined,
        role: selectedRole || undefined,
        status: selectedStatus || undefined,
        page: pageIndex,
        size: 20,
      });
    },
    [listAccounts, searchTerm, selectedRole, selectedStatus, page]
  );

  useEffect(() => {
    fetchList(0);
    setPage(0);
  }, [selectedRole, selectedStatus]);

  const handleSearchSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setPage(0);
    fetchList(0);
  };

  const handlePrevPage = () => {
    if (page > 0) {
      const next = page - 1;
      setPage(next);
      fetchList(next);
    }
  };

  const handleNextPage = () => {
    if (pagedResult && page < pagedResult.meta.totalPages - 1) {
      const next = page + 1;
      setPage(next);
      fetchList(next);
    }
  };

  if (error && ((error as any).status === 403 || (error as any).code === 'FORBIDDEN')) {
    return (
      <div className="account-page-container">
        <div>
          <Link to="/" className="account-back-btn">
            ← Quay lại
          </Link>
        </div>
        <div className="account-alert account-alert-error" role="alert" style={{ marginTop: '1rem' }}>
          <span>{error.message || 'Không thể tải danh sách tài khoản. Quyền hạn không hợp lệ (403 Forbidden).'}</span>
        </div>
        <div className="account-card" style={{ marginTop: '1.5rem', textAlign: 'center', padding: '3rem 1.5rem' }}>
          <h1 style={{ fontSize: '1.5rem', fontWeight: 600, color: '#111827', marginBottom: '0.75rem' }}>
            Từ chối quyền truy cập (403 Forbidden)
          </h1>
          <p style={{ color: '#4b5563', maxWidth: '520px', margin: '0 auto 1.5rem' }}>
            Bạn không có quyền truy cập vào danh sách tài khoản quản trị hệ thống.
          </p>
          <Link to="/" className="account-btn account-btn-primary" style={{ display: 'inline-block' }}>
            Về trang chủ
          </Link>
        </div>
      </div>
    );
  }

  return (
    <div className="account-page-container">
      {/* Back button matching Figma */}
      <div>
        <Link to="/" className="account-back-btn">
          ← Quay lại
        </Link>
      </div>

      {/* Admin Navigation Tabs (Figma F08-F11) */}
      <nav className="admin-nav-tabs" aria-label="Admin Navigation">
        <Link to="/admin/accounts" className="admin-nav-tab active">
          Tài khoản
        </Link>
        <Link to="/admin/roles" className="admin-nav-tab">
          Vai trò
        </Link>
      </nav>

      <header className="account-page-header">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '1rem' }}>
          <div>
            <h1 className="account-page-title">Quản lý tài khoản hệ thống</h1>
            <p className="account-page-description">
              Tìm kiếm, lọc danh sách tài khoản người dùng và quản trị phân quyền nội bộ.
            </p>
          </div>
          <div>
            <Link to="/admin/accounts/new" className="account-btn account-btn-primary">
              + Tạo tài khoản nội bộ
            </Link>
          </div>
        </div>
      </header>

      {error && (
        <div className="account-alert account-alert-error" role="alert">
          <span>{error.message || 'Lỗi khi tải danh sách tài khoản.'}</span>
        </div>
      )}

      {/* Filter and Search Bar */}
      <section className="account-card" aria-labelledby="filter-heading" style={{ padding: '1rem' }}>
        <h2 id="filter-heading" style={{ display: 'none' }}>Bộ lọc</h2>
        <form onSubmit={handleSearchSubmit} style={{ display: 'flex', gap: '0.75rem', flexWrap: 'wrap', alignItems: 'flex-end' }}>
          <div className="account-field" style={{ flex: '1 1 200px' }}>
            <label className="account-label" htmlFor="search-input">
              Tìm kiếm
            </label>
            <input
              id="search-input"
              type="text"
              className="account-input"
              placeholder="Email hoặc tên người dùng…"
              value={searchTerm}
              onChange={e => setSearchTerm(e.target.value)}
            />
          </div>

          <div className="account-field" style={{ flex: '0 1 150px' }}>
            <label className="account-label" htmlFor="role-filter">
              Vai trò
            </label>
            <select
              id="role-filter"
              className="account-select"
              value={selectedRole}
              onChange={e => setSelectedRole(e.target.value)}
            >
              <option value="">Tất cả vai trò</option>
              <option value="MEMBER">MEMBER</option>
              <option value="STAFF">STAFF</option>
              <option value="MANAGER">MANAGER</option>
              <option value="ADMIN">ADMIN</option>
            </select>
          </div>

          <div className="account-field" style={{ flex: '0 1 150px' }}>
            <label className="account-label" htmlFor="status-filter">
              Trạng thái
            </label>
            <select
              id="status-filter"
              className="account-select"
              value={selectedStatus}
              onChange={e => setSelectedStatus(e.target.value)}
            >
              <option value="">Tất cả trạng thái</option>
              <option value="ACTIVE">ACTIVE</option>
              <option value="PENDING_SETUP">PENDING_SETUP</option>
              <option value="SUSPENDED">SUSPENDED</option>
            </select>
          </div>

          <div>
            <button type="submit" className="account-btn account-btn-primary account-btn-sm" disabled={loading}>
              {loading ? 'Đang lọc…' : 'Tìm kiếm'}
            </button>
          </div>
        </form>
      </section>

      {/* Account Table */}
      <div className="account-table-wrapper">
        <table className="account-table">
          <thead>
            <tr>
              <th>ID</th>
              <th>Email đăng nhập</th>
              <th>Vai trò</th>
              <th>Trạng thái</th>
              <th>Cập nhật gần nhất</th>
              <th style={{ textAlign: 'right' }}>Thao tác</th>
            </tr>
          </thead>
          <tbody>
            {loading && (!pagedResult || pagedResult.data.length === 0) ? (
              <tr>
                <td colSpan={6} style={{ textAlign: 'center', padding: '2rem' }}>
                  Đang tải danh sách tài khoản…
                </td>
              </tr>
            ) : !pagedResult || pagedResult.data.length === 0 ? (
              <tr>
                <td colSpan={6} style={{ textAlign: 'center', padding: '2rem', color: '#6b717f' }}>
                  Không tìm thấy tài khoản nào khớp với điều kiện lọc.
                </td>
              </tr>
            ) : (
              pagedResult.data.map(account => (
                <tr key={account.accountId}>
                  <td><strong>#{account.accountId}</strong></td>
                  <td>{account.loginEmail}</td>
                  <td>
                    <span className="account-badge account-badge-readonly">{account.role}</span>
                  </td>
                  <td>
                    <span
                      className={`account-badge ${
                        account.status === 'ACTIVE'
                          ? 'account-badge-active'
                          : account.status === 'PENDING_SETUP'
                          ? 'account-badge-pending'
                          : 'account-badge-suspended'
                      }`}
                    >
                      {account.status}
                    </span>
                  </td>
                  <td style={{ color: '#6b717f', fontSize: '0.8125rem' }}>
                    {account.updatedAt || '—'}
                  </td>
                  <td style={{ textAlign: 'right' }}>
                    <Link
                      to={`/admin/accounts/${account.accountId}`}
                      className="account-btn account-btn-secondary account-btn-sm"
                    >
                      Chi tiết
                    </Link>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>

      {/* Pagination Bar */}
      {pagedResult && pagedResult.meta.totalPages > 1 && (
        <div className="account-pagination">
          <span>
            Trang <strong>{pagedResult.meta.page + 1}</strong> trên <strong>{pagedResult.meta.totalPages}</strong> ({pagedResult.meta.total} tài khoản)
          </span>
          <div className="account-pagination-actions">
            <button
              type="button"
              className="account-btn account-btn-secondary account-btn-sm"
              onClick={handlePrevPage}
              disabled={page === 0 || loading}
            >
              Trước
            </button>
            <button
              type="button"
              className="account-btn account-btn-secondary account-btn-sm"
              onClick={handleNextPage}
              disabled={page >= pagedResult.meta.totalPages - 1 || loading}
            >
              Sau
            </button>
          </div>
        </div>
      )}
    </div>
  );
};

export default AdminAccountListPage;
