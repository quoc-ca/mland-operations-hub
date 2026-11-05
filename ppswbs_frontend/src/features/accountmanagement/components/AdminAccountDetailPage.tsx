import React, { useState, useEffect } from 'react';
import { useParams, Link } from 'react-router';
import { useAdminAccounts } from '../hooks/useAdminAccounts';
import { ApiError } from '../../../shared/api/http';
import '../accountmanagement.css';

const AVAILABLE_LOCATIONS = [
  { id: 1, name: 'Cơ sở Quận 1 (Mland Center)' },
  { id: 2, name: 'Cơ sở Quận 3 (Mland Workshop)' },
  { id: 3, name: 'Cơ sở Thủ Đức (Mland Studio)' },
];

export const AdminAccountDetailPage: React.FC = () => {
  const { accountId } = useParams<{ accountId: string }>();
  const {
    selectedAccount,
    loading,
    error,
    loadDetail,
    updateProfile,
    assignRoleLocation,
    suspendAccount,
    restoreAccount,
    resendInvitation,
  } = useAdminAccounts();

  // Profile edit form
  const [editName, setEditName] = useState('');
  const [editPhone, setEditPhone] = useState('');
  const [profileActionError, setProfileActionError] = useState<string | null>(null);
  const [profileActionSuccess, setProfileActionSuccess] = useState<string | null>(null);
  const [savingProfile, setSavingProfile] = useState(false);

  // Role & location form
  const [editRole, setEditRole] = useState<'STAFF' | 'MANAGER'>('STAFF');
  const [editLocations, setEditLocations] = useState<number[]>([]);
  const [roleActionError, setRoleActionError] = useState<string | null>(null);
  const [roleActionSuccess, setRoleActionSuccess] = useState<string | null>(null);
  const [savingRole, setSavingRole] = useState(false);

  // Suspension modal / form
  const [showSuspendModal, setShowSuspendModal] = useState(false);
  const [suspendReason, setSuspendReason] = useState('');
  const [suspendError, setSuspendError] = useState<string | null>(null);
  const [statusActionSuccess, setStatusActionSuccess] = useState<string | null>(null);
  const [actionPending, setActionPending] = useState(false);

  // Invitation resend state
  const [resendNotice, setResendNotice] = useState<string | null>(null);

  useEffect(() => {
    if (accountId) {
      loadDetail(accountId);
    }
  }, [accountId, loadDetail]);

  useEffect(() => {
    if (selectedAccount) {
      setEditName(selectedAccount.profile?.fullName || '');
      setEditPhone(selectedAccount.profile?.phone || '');
      if (selectedAccount.role === 'STAFF' || selectedAccount.role === 'MANAGER') {
        setEditRole(selectedAccount.role);
      }
      setEditLocations(
        selectedAccount.locationAssignments?.filter(l => l.active).map(l => l.locationId) || []
      );
    }
  }, [selectedAccount]);

  if (loading && !selectedAccount) {
    return (
      <div className="account-page-container">
        <div className="account-card">
          <p style={{ color: '#6b717f' }}>Đang tải thông tin tài khoản #{accountId}…</p>
        </div>
      </div>
    );
  }

  if (!selectedAccount) {
    return (
      <div className="account-page-container">
        <div className="account-alert account-alert-error" role="alert">
          <span>{error?.message || `Không tìm thấy tài khoản #${accountId}.`}</span>
        </div>
        <Link to="/admin/accounts" className="account-btn account-btn-secondary">
          Quay lại danh sách
        </Link>
      </div>
    );
  }

  const isAdminAccount = selectedAccount.role === 'ADMIN';
  const isSuspended = selectedAccount.status === 'SUSPENDED';
  const isPendingSetup = selectedAccount.status === 'PENDING_SETUP';
  const isRevocationPending = selectedAccount.revocationStatus === 'PENDING';

  const handleProfileSave = async (e: React.FormEvent) => {
    e.preventDefault();
    setProfileActionError(null);
    setProfileActionSuccess(null);

    if (!editName.trim()) {
      setProfileActionError('Họ và tên là bắt buộc.');
      return;
    }

    setSavingProfile(true);
    try {
      const result = await updateProfile(
        selectedAccount.accountId,
        editName.trim(),
        editPhone.trim() || undefined,
        selectedAccount.version
      );
      if (result) {
        setProfileActionSuccess('Cập nhật hồ sơ tài khoản thành công.');
      }
    } catch (err: any) {
      if (err instanceof ApiError && err.status === 409) {
        setProfileActionError('Dữ liệu đã bị thay đổi bởi phiên khác. Đã tải lại thông tin mới nhất.');
      } else {
        setProfileActionError(err?.message || 'Không thể lưu thay đổi hồ sơ.');
      }
    } finally {
      setSavingProfile(false);
    }
  };

  const handleRoleLocationSave = async (e: React.FormEvent) => {
    e.preventDefault();
    setRoleActionError(null);
    setRoleActionSuccess(null);

    if (editLocations.length === 0) {
      setRoleActionError('Phải chọn ít nhất 1 cơ sở.');
      return;
    }
    if (editRole === 'STAFF' && editLocations.length !== 1) {
      setRoleActionError('Nhân viên (Staff) chỉ được phân công chính xác 1 cơ sở.');
      return;
    }

    setSavingRole(true);
    try {
      const result = await assignRoleLocation(
        selectedAccount.accountId,
        editRole,
        editLocations,
        selectedAccount.version
      );
      if (result) {
        setRoleActionSuccess('Phân quyền vai trò và cơ sở thành công.');
      }
    } catch (err: any) {
      if (err instanceof ApiError && err.status === 409) {
        setRoleActionError('Dữ liệu đã bị thay đổi bởi phiên khác. Đã tải lại thông tin mới nhất.');
      } else {
        setRoleActionError(err?.message || 'Không thể phân công vai trò/cơ sở.');
      }
    } finally {
      setSavingRole(false);
    }
  };

  const handleSuspendConfirm = async () => {
    if (!suspendReason.trim()) {
      setSuspendError('Lý do tạm khóa là bắt buộc.');
      return;
    }

    setSuspendError(null);
    setActionPending(true);
    try {
      const result = await suspendAccount(
        selectedAccount.accountId,
        suspendReason.trim(),
        selectedAccount.version
      );
      if (result) {
        setShowSuspendModal(false);
        setSuspendReason('');
        setStatusActionSuccess('Tài khoản đã được chuyển sang trạng thái tạm khóa (SUSPENDED).');
      }
    } catch (err: any) {
      if (err instanceof ApiError && err.status === 409) {
        setSuspendError('Xung đột phiên bản: thông tin tài khoản đã thay đổi. Vui lòng thử lại.');
      } else {
        setSuspendError(err?.message || 'Lỗi khi tạm khóa tài khoản.');
      }
    } finally {
      setActionPending(false);
    }
  };

  const handleRestore = async () => {
    setStatusActionSuccess(null);
    setActionPending(true);
    try {
      const result = await restoreAccount(selectedAccount.accountId, selectedAccount.version);
      if (result) {
        setStatusActionSuccess('Khôi phục tài khoản thành công. Người dùng cần đăng nhập lại.');
      }
    } catch (err: any) {
      if (err instanceof ApiError && err.status === 409) {
        setStatusActionSuccess(null);
        alert('Không thể khôi phục tài khoản trong khi lệnh thu hồi phiên Firebase chưa hoàn tất.');
      } else {
        alert(err?.message || 'Lỗi khi khôi phục tài khoản.');
      }
    } finally {
      setActionPending(false);
    }
  };

  const handleResendInvitation = async () => {
    setResendNotice(null);
    setActionPending(true);
    try {
      const result = await resendInvitation(selectedAccount.accountId);
      if (result) {
        setResendNotice('Thư mời thiết lập tài khoản đã được gửi lại thành công.');
      }
    } catch (err: any) {
      setResendNotice(err?.message || 'Không thể gửi lại thư mời. Vui lòng kiểm tra thời gian chờ.');
    } finally {
      setActionPending(false);
    }
  };

  return (
    <div className="account-page-container">
      {/* Back button matching Figma */}
      <div>
        <Link to="/admin/accounts" className="account-back-btn">
          ← Quay lại danh sách tài khoản
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
            <h1 className="account-page-title">
              {selectedAccount.profile?.fullName ? `${selectedAccount.profile.fullName} · ACC-${selectedAccount.accountId}` : `Tài khoản #ACC-${selectedAccount.accountId}`}
            </h1>
            <p className="account-page-description">
              Định danh đăng nhập: <strong>{selectedAccount.loginEmail}</strong>
            </p>
          </div>
          <div>
            <span
              className={`account-badge ${
                selectedAccount.status === 'ACTIVE'
                  ? 'account-badge-active'
                  : selectedAccount.status === 'PENDING_SETUP'
                  ? 'account-badge-pending'
                  : 'account-badge-suspended'
              }`}
            >
              {selectedAccount.status === 'ACTIVE' ? 'ACTIVE · Đang hoạt động' : selectedAccount.status === 'PENDING_SETUP' ? 'PENDING_SETUP · Chờ kích hoạt' : 'SUSPENDED · Đã đình chỉ'}
            </span>
          </div>
        </div>
      </header>

      {statusActionSuccess && (
        <div className="account-alert account-alert-success" role="status">
          <span>{statusActionSuccess}</span>
        </div>
      )}

      {/* Account Overview Table */}
      <section className="account-card" aria-labelledby="overview-heading">
        <h2 id="overview-heading" className="account-card-title">Tổng quan tài khoản</h2>
        <table className="account-table">
          <tbody>
            <tr>
              <th style={{ width: '220px' }}>Vai trò hệ thống:</th>
              <td><strong>{selectedAccount.role}</strong></td>
            </tr>
            <tr>
              <th>Trạng thái hoạt động:</th>
              <td>{selectedAccount.status}</td>
            </tr>
            <tr>
              <th>Thu hồi phiên Firebase:</th>
              <td>
                {selectedAccount.revocationStatus === 'PENDING' ? (
                  <span style={{ color: '#b45309', fontWeight: 600 }}>Đang chờ xử lý (Pending)</span>
                ) : selectedAccount.revocationStatus === 'CONFIRMED' ? (
                  <span style={{ color: '#15803d', fontWeight: 600 }}>Đã thu hồi (Confirmed)</span>
                ) : (
                  'Không áp dụng'
                )}
              </td>
            </tr>
            <tr>
              <th>Phiên bản ghi (Version):</th>
              <td>{selectedAccount.version}</td>
            </tr>
            <tr>
              <th>Ngày khởi tạo:</th>
              <td>{selectedAccount.createdAt}</td>
            </tr>
            <tr>
              <th>Cập nhật gần nhất:</th>
              <td>{selectedAccount.updatedAt}</td>
            </tr>
          </tbody>
        </table>

        {/* Action Controls for Status / Resend */}
        <div style={{ marginTop: '1.25rem', display: 'flex', gap: '0.75rem', flexWrap: 'wrap' }}>
          {isPendingSetup && (
            <button
              type="button"
              className="account-btn account-btn-secondary account-btn-sm"
              onClick={handleResendInvitation}
              disabled={actionPending}
            >
              Gửi lại thư mời (Resend Invitation)
            </button>
          )}

          {!isAdminAccount && !isSuspended && (
            <button
              type="button"
              className="account-btn account-btn-danger account-btn-sm"
              onClick={() => setShowSuspendModal(true)}
              disabled={actionPending}
            >
              Tạm khóa tài khoản (Suspend)
            </button>
          )}

          {!isAdminAccount && isSuspended && (
            <button
              type="button"
              className="account-btn account-btn-primary account-btn-sm"
              onClick={handleRestore}
              disabled={actionPending || isRevocationPending}
              title={isRevocationPending ? 'Chưa thể khôi phục trong khi Firebase đang xử lý thu hồi phiên' : undefined}
            >
              Khôi phục tài khoản
            </button>
          )}

          {isAdminAccount && (
            <span style={{ fontSize: '0.85rem', color: '#6b717f', alignSelf: 'center' }}>
              * Tài khoản ADMIN được bảo vệ, không thể tạm khóa hoặc chỉnh sửa trực tiếp.
            </span>
          )}
        </div>

        {resendNotice && (
          <div className="account-alert account-alert-info" style={{ marginTop: '1rem' }}>
            <span>{resendNotice}</span>
          </div>
        )}
      </section>

      {/* Edit Internal Profile */}
      {!isAdminAccount && (
        <section className="account-card" aria-labelledby="edit-profile-heading">
          <h2 id="edit-profile-heading" className="account-card-title">Chỉnh sửa thông tin hồ sơ</h2>

          {profileActionSuccess && (
            <div className="account-alert account-alert-success" role="status">
              <span>{profileActionSuccess}</span>
            </div>
          )}

          {profileActionError && (
            <div className="account-alert account-alert-error" role="alert">
              <span>{profileActionError}</span>
            </div>
          )}

          <form className="account-form" onSubmit={handleProfileSave}>
            <div className="account-field">
              <label className="account-label account-label-required" htmlFor="edit-name">
                Họ và tên
              </label>
              <input
                id="edit-name"
                type="text"
                className="account-input"
                value={editName}
                onChange={e => setEditName(e.target.value)}
                maxLength={100}
                disabled={savingProfile}
              />
            </div>

            <div className="account-field">
              <label className="account-label" htmlFor="edit-phone">
                Số điện thoại
              </label>
              <input
                id="edit-phone"
                type="tel"
                className="account-input"
                value={editPhone}
                onChange={e => setEditPhone(e.target.value)}
                maxLength={30}
                disabled={savingProfile}
              />
            </div>

            <div>
              <button
                type="submit"
                className="account-btn account-btn-primary account-btn-sm"
                disabled={savingProfile}
              >
                {savingProfile ? 'Đang lưu…' : 'Lưu hồ sơ'}
              </button>
            </div>
          </form>
        </section>
      )}

      {/* Assign Role and Location (STAFF / MANAGER only) */}
      {(selectedAccount.role === 'STAFF' || selectedAccount.role === 'MANAGER') && (
        <section className="account-card" aria-labelledby="assign-heading">
          <h2 id="assign-heading" className="account-card-title">Phân công vai trò & cơ sở</h2>

          {roleActionSuccess && (
            <div className="account-alert account-alert-success" role="status">
              <span>{roleActionSuccess}</span>
            </div>
          )}

          {roleActionError && (
            <div className="account-alert account-alert-error" role="alert">
              <span>{roleActionError}</span>
            </div>
          )}

          <form className="account-form" onSubmit={handleRoleLocationSave}>
            <div className="account-field">
              <label className="account-label account-label-required" htmlFor="edit-role-select">
                Vai trò nội bộ
              </label>
              <select
                id="edit-role-select"
                className="account-select"
                value={editRole}
                onChange={e => {
                  const r = e.target.value as 'STAFF' | 'MANAGER';
                  setEditRole(r);
                  if (r === 'STAFF' && editLocations.length > 1) {
                    setEditLocations([editLocations[0]]);
                  }
                }}
                disabled={savingRole}
              >
                <option value="STAFF">Nhân viên cơ sở (STAFF)</option>
                <option value="MANAGER">Quản lý cơ sở (MANAGER)</option>
              </select>
            </div>

            <div className="account-field">
              <span className="account-label account-label-required">Cơ sở được phân công</span>
              <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem', marginTop: '0.25rem' }}>
                {AVAILABLE_LOCATIONS.map(loc => (
                  <label
                    key={loc.id}
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      gap: '0.5rem',
                      fontSize: '0.9rem',
                      cursor: 'pointer',
                    }}
                  >
                    <input
                      type={editRole === 'STAFF' ? 'radio' : 'checkbox'}
                      name="edit-locs"
                      checked={editLocations.includes(loc.id)}
                      onChange={() => {
                        if (editRole === 'STAFF') {
                          setEditLocations([loc.id]);
                        } else {
                          if (editLocations.includes(loc.id)) {
                            if (editLocations.length > 1) {
                              setEditLocations(editLocations.filter(id => id !== loc.id));
                            }
                          } else {
                            setEditLocations([...editLocations, loc.id]);
                          }
                        }
                      }}
                      disabled={savingRole}
                    />
                    <span>{loc.name}</span>
                  </label>
                ))}
              </div>
            </div>

            <div>
              <button
                type="submit"
                className="account-btn account-btn-primary account-btn-sm"
                disabled={savingRole}
              >
                {savingRole ? 'Đang cập nhật…' : 'Cập nhật vai trò & cơ sở'}
              </button>
            </div>
          </form>
        </section>
      )}

      {/* Suspend Confirmation Modal */}
      {showSuspendModal && (
        <div
          role="dialog"
          aria-modal="true"
          aria-labelledby="suspend-modal-title"
          className="account-modal-overlay"
        >
          <div className="account-modal-container account-modal-sm">
            <div className="account-modal-header">
              <h3 id="suspend-modal-title" className="account-modal-title" style={{ color: '#dc2626' }}>
                Xác nhận tạm khóa tài khoản
              </h3>
              <button
                type="button"
                className="account-modal-close-btn"
                onClick={() => {
                  setShowSuspendModal(false);
                  setSuspendError(null);
                }}
              >
                ✕
              </button>
            </div>

            <div className="account-modal-body">
              <p style={{ fontSize: '0.9rem', color: '#6b717f', marginTop: 0 }}>
                Sau khi xác nhận, tài khoản chuyển sang SUSPENDED và bị thu hồi phiên đăng nhập. Người dùng sẽ không thể tiếp tục truy cập.
              </p>

              {suspendError && (
                <div className="account-alert account-alert-error" role="alert">
                  <span>{suspendError}</span>
                </div>
              )}

              <div className="account-field" style={{ marginBottom: '1rem' }}>
                <label className="account-label account-label-required" htmlFor="suspend-reason">
                  Lý do tạm khóa
                </label>
                <textarea
                  id="suspend-reason"
                  className="account-textarea"
                  rows={3}
                  value={suspendReason}
                  onChange={e => setSuspendReason(e.target.value)}
                  placeholder="Nhập lý do nghiệp vụ hoặc bảo mật…"
                />
              </div>
            </div>

            <div className="account-modal-footer">
              <button
                type="button"
                className="account-btn account-btn-secondary"
                onClick={() => {
                  setShowSuspendModal(false);
                  setSuspendError(null);
                }}
                disabled={actionPending}
              >
                Hủy bỏ
              </button>
              <button
                type="button"
                className="account-btn account-btn-danger"
                onClick={handleSuspendConfirm}
                disabled={actionPending}
              >
                {actionPending ? 'Đang xử lý…' : 'Xác nhận tạm khóa'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default AdminAccountDetailPage;
