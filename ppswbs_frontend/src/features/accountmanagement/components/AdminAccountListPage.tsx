import React, { useState, useEffect, useCallback } from 'react';
import { Link } from 'react-router';
import { useAdminAccounts } from '../hooks/useAdminAccounts';
import { useT } from '../../../shared/i18n/I18nProvider';
import { accountMessages } from '../messages';
import '../accountmanagement.css';

export const AdminAccountListPage: React.FC = () => {
  const t = useT(accountMessages);
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
            {t('list.back')}
          </Link>
        </div>
        <div className="account-alert account-alert-error" role="alert" style={{ marginTop: '1rem' }}>
          <span>{error.message || t('list.forbiddenFallback')}</span>
        </div>
        <div className="account-card" style={{ marginTop: '1.5rem', textAlign: 'center', padding: '3rem 1.5rem' }}>
          <h1 style={{ fontSize: '1.5rem', fontWeight: 600, color: '#111827', marginBottom: '0.75rem' }}>
            {t('list.forbiddenTitle')}
          </h1>
          <p style={{ color: '#4b5563', maxWidth: '520px', margin: '0 auto 1.5rem' }}>
            {t('list.forbiddenBody')}
          </p>
          <Link to="/" className="account-btn account-btn-primary" style={{ display: 'inline-block' }}>
            {t('list.home')}
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
          {t('list.back')}
        </Link>
      </div>

      {/* Admin Navigation Tabs (Figma F08-F11; role catalogue removed per T113) */}
      <nav className="admin-nav-tabs" aria-label={t('list.navAria')}>
        <Link to="/admin/accounts" className="admin-nav-tab active">
          {t('list.navAccounts')}
        </Link>
      </nav>

      <header className="account-page-header">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '1rem' }}>
          <div>
            <h1 className="account-page-title">{t('list.title')}</h1>
            <p className="account-page-description">
              {t('list.description')}
            </p>
          </div>
          <div>
            <Link to="/admin/accounts/new" className="account-btn account-btn-primary">
              {t('list.create')}
            </Link>
          </div>
        </div>
      </header>

      {error && (
        <div className="account-alert account-alert-error" role="alert">
          <span>{error.message || t('list.loadError')}</span>
        </div>
      )}

      {/* Filter and Search Bar */}
      <section className="account-card" aria-labelledby="filter-heading" style={{ padding: '1rem' }}>
        <h2 id="filter-heading" style={{ display: 'none' }}>{t('list.filters')}</h2>
        <form onSubmit={handleSearchSubmit} style={{ display: 'flex', gap: '0.75rem', flexWrap: 'wrap', alignItems: 'flex-end' }}>
          <div className="account-field" style={{ flex: '1 1 200px' }}>
            <label className="account-label" htmlFor="search-input">
              {t('list.search')}
            </label>
            <input
              id="search-input"
              type="text"
              className="account-input"
              placeholder={t('list.searchPlaceholder')}
              value={searchTerm}
              onChange={e => setSearchTerm(e.target.value)}
            />
          </div>

          <div className="account-field" style={{ flex: '0 1 150px' }}>
            <label className="account-label" htmlFor="role-filter">
              {t('list.role')}
            </label>
            <select
              id="role-filter"
              className="account-select"
              value={selectedRole}
              onChange={e => setSelectedRole(e.target.value)}
            >
              <option value="">{t('list.allRoles')}</option>
              <option value="MEMBER">MEMBER</option>
              <option value="STAFF">STAFF</option>
              <option value="MANAGER">MANAGER</option>
              <option value="CARRIER">CARRIER</option>
              <option value="ADMIN">ADMIN</option>
            </select>
          </div>

          <div className="account-field" style={{ flex: '0 1 150px' }}>
            <label className="account-label" htmlFor="status-filter">
              {t('list.status')}
            </label>
            <select
              id="status-filter"
              className="account-select"
              value={selectedStatus}
              onChange={e => setSelectedStatus(e.target.value)}
            >
              <option value="">{t('list.allStatuses')}</option>
              <option value="ACTIVE">ACTIVE</option>
              <option value="PENDING_SETUP">PENDING_SETUP</option>
              <option value="SUSPENDED">SUSPENDED</option>
            </select>
          </div>

          <div>
            <button type="submit" className="account-btn account-btn-primary account-btn-sm" disabled={loading}>
              {loading ? t('list.filtering') : t('list.search')}
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
              <th>{t('list.colEmail')}</th>
              <th>{t('list.role')}</th>
              <th>{t('list.status')}</th>
              <th>{t('list.colUpdated')}</th>
              <th style={{ textAlign: 'right' }}>{t('list.colActions')}</th>
            </tr>
          </thead>
          <tbody>
            {loading && (!pagedResult || pagedResult.data.length === 0) ? (
              <tr>
                <td colSpan={6} style={{ textAlign: 'center', padding: '2rem' }}>
                  {t('list.loading')}
                </td>
              </tr>
            ) : !pagedResult || pagedResult.data.length === 0 ? (
              <tr>
                <td colSpan={6} style={{ textAlign: 'center', padding: '2rem', color: '#6b717f' }}>
                  {t('list.empty')}
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
                      {t('list.detail')}
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
            {t('list.page')} <strong>{pagedResult.meta.page + 1}</strong> {t('list.pageOf')} <strong>{pagedResult.meta.totalPages}</strong> {t('list.totalAccounts', { total: pagedResult.meta.total })}
          </span>
          <div className="account-pagination-actions">
            <button
              type="button"
              className="account-btn account-btn-secondary account-btn-sm"
              onClick={handlePrevPage}
              disabled={page === 0 || loading}
            >
              {t('list.prev')}
            </button>
            <button
              type="button"
              className="account-btn account-btn-secondary account-btn-sm"
              onClick={handleNextPage}
              disabled={page >= pagedResult.meta.totalPages - 1 || loading}
            >
              {t('list.next')}
            </button>
          </div>
        </div>
      )}
    </div>
  );
};

export default AdminAccountListPage;
