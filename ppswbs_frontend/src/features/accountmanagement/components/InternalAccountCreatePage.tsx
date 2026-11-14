import React, { useState } from 'react';
import { Link, useNavigate } from 'react-router';
import { useAdminAccounts } from '../hooks/useAdminAccounts';
import { newCommandId } from '../../../shared/api/http';
import '../accountmanagement.css';

// Active Workshop Locations available in system
const AVAILABLE_LOCATIONS = [
  { id: 1, name: 'Cơ sở Quận 1 (Mland Center)' },
  { id: 2, name: 'Cơ sở Quận 3 (Mland Workshop)' },
  { id: 3, name: 'Cơ sở Thủ Đức (Mland Studio)' },
];

export const InternalAccountCreatePage: React.FC = () => {
  const navigate = useNavigate();
  const { createInternalAccount, loading, error } = useAdminAccounts();

  const [role, setRole] = useState<'STAFF' | 'MANAGER'>('STAFF');
  const [fullName, setFullName] = useState('');
  const [phone, setPhone] = useState('');
  const [selectedLocations, setSelectedLocations] = useState<number[]>([1]);
  const [sendNotification, setSendNotification] = useState<boolean>(true);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [createdSummary, setCreatedSummary] = useState<any | null>(null);

  // Preserve idempotency key across retries for the same submit action
  const [idempotencyKey, setIdempotencyKey] = useState<string>(() => newCommandId());

  const handleLocationToggle = (locationId: number) => {
    if (role === 'STAFF') {
      // Staff must have exactly 1 location
      setSelectedLocations([locationId]);
    } else {
      // Manager can have 1 or more
      if (selectedLocations.includes(locationId)) {
        if (selectedLocations.length > 1) {
          setSelectedLocations(selectedLocations.filter(id => id !== locationId));
        }
      } else {
        setSelectedLocations([...selectedLocations, locationId]);
      }
    }
  };

  const validate = () => {
    const errors: Record<string, string> = {};
    const trimmedName = fullName.trim();

    if (!trimmedName) {
      errors.fullName = 'Họ và tên là bắt buộc.';
    } else if (trimmedName.length > 100) {
      errors.fullName = 'Họ và tên không được vượt quá 100 ký tự.';
    }

    if (phone && phone.trim()) {
      const trimmedPhone = phone.trim();
      if (trimmedPhone.length < 7 || trimmedPhone.length > 30) {
        errors.phone = 'Số điện thoại phải từ 7 đến 30 ký tự.';
      }
    }

    if (selectedLocations.length === 0) {
      errors.locations = 'Phải chọn ít nhất một cơ sở làm việc.';
    } else if (role === 'STAFF' && selectedLocations.length !== 1) {
      errors.locations = 'Nhân viên (Staff) chỉ được phân công chính xác 1 cơ sở.';
    }

    setFieldErrors(errors);
    return Object.keys(errors).length === 0;
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!validate()) return;

    const result = await createInternalAccount(
      {
        role,
        fullName: fullName.trim(),
        phone: phone.trim() || undefined,
        locationIds: selectedLocations,
      },
      idempotencyKey
    );

    if (result) {
      setCreatedSummary(result);
    }
  };

  const handleResetForNew = () => {
    setFullName('');
    setPhone('');
    setSelectedLocations([1]);
    setRole('STAFF');
    setFieldErrors({});
    setCreatedSummary(null);
    setIdempotencyKey(newCommandId());
  };

  return (
    <div className="account-page-container">
      {/* Back button matching Figma */}
      <div>
        <Link to="/admin/accounts" className="account-back-btn">
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
        <h1 className="account-page-title">Cấp tài khoản nhân sự</h1>
        <p className="account-page-description">
          Chỉ tạo STAFF hoặc MANAGER. Nhân sự tự thiết lập mật khẩu qua thư mời.
        </p>
      </header>

      {createdSummary ? (
        <section className="account-card" aria-labelledby="created-title">
          <h2 id="created-title" className="account-card-title" style={{ color: '#15803d' }}>
            Khởi tạo thành công!
          </h2>

          <div className="account-alert account-alert-info">
            <div>
              <strong>Trạng thái: PENDING_SETUP (Chờ kích hoạt)</strong>
              <p style={{ margin: '0.25rem 0' }}>
                Tài khoản nội bộ đã được ghi nhận. Thư mời kích hoạt đã được gửi tới người nhận qua hệ thống thông báo.
              </p>
              <p style={{ margin: '0.25rem 0', fontSize: '0.85rem', color: '#4b5563' }}>
                * Quyền truy cập nội bộ chỉ có hiệu lực sau khi người được mời hoàn tất đăng ký với email được xác thực.
              </p>
            </div>
          </div>

          <table className="account-table" style={{ marginBottom: '1.5rem' }}>
            <tbody>
              <tr>
                <th style={{ width: '200px' }}>Mã tài khoản:</th>
                <td>{createdSummary.accountId}</td>
              </tr>
              <tr>
                <th>Vai trò:</th>
                <td>
                  <span className="account-badge account-badge-readonly">{createdSummary.role}</span>
                </td>
              </tr>
              <tr>
                <th>Email định danh:</th>
                <td>{createdSummary.loginEmail}</td>
              </tr>
              <tr>
                <th>Trạng thái tài khoản:</th>
                <td>
                  <span className="account-badge account-badge-pending">
                    {createdSummary.status}
                  </span>
                </td>
              </tr>
            </tbody>
          </table>

          <div style={{ display: 'flex', gap: '1rem', flexWrap: 'wrap' }}>
            <Link
              to={`/admin/accounts/${createdSummary.accountId}`}
              className="account-btn account-btn-primary"
            >
              Xem chi tiết tài khoản
            </Link>
            <button
              type="button"
              className="account-btn account-btn-secondary"
              onClick={handleResetForNew}
            >
              Tạo tài khoản khác
            </button>
            <Link to="/admin/accounts" className="account-btn account-btn-secondary">
              Danh sách tài khoản
            </Link>
          </div>
        </section>
      ) : (
        <div className="account-card">
          {error && (
            <div className="account-alert account-alert-error" role="alert">
              <span>{error.message || 'Không thể khởi tạo tài khoản nội bộ.'}</span>
            </div>
          )}

          <form className="account-form" onSubmit={handleSubmit} noValidate>
            <h3 style={{ fontSize: '1.05rem', fontWeight: 600, margin: '0 0 0.5rem 0', color: '#1a1a1a', borderBottom: '1px solid #eceef1', paddingBottom: '0.5rem' }}>
              Thông tin cá nhân
            </h3>

            <div className="account-field">
              <label className="account-label account-label-required" htmlFor="internal-name">
                Họ và tên
              </label>
              <input
                id="internal-name"
                type="text"
                className="account-input"
                placeholder="Nhập họ và tên nhân sự…"
                value={fullName}
                onChange={e => setFullName(e.target.value)}
                maxLength={100}
                aria-invalid={Boolean(fieldErrors.fullName)}
                disabled={loading}
              />
              {fieldErrors.fullName && (
                <span className="account-field-error" role="alert">{fieldErrors.fullName}</span>
              )}
            </div>

            <div className="account-field">
              <label className="account-label" htmlFor="internal-phone">
                Số điện thoại liên hệ
              </label>
              <input
                id="internal-phone"
                type="tel"
                className="account-input"
                placeholder="Ví dụ: 0901234567"
                value={phone}
                onChange={e => setPhone(e.target.value)}
                maxLength={30}
                aria-invalid={Boolean(fieldErrors.phone)}
                disabled={loading}
              />
              {fieldErrors.phone && (
                <span className="account-field-error" role="alert">{fieldErrors.phone}</span>
              )}
            </div>

            <h3 style={{ fontSize: '1.05rem', fontWeight: 600, margin: '1rem 0 0.5rem 0', color: '#1a1a1a', borderBottom: '1px solid #eceef1', paddingBottom: '0.5rem' }}>
              Vai trò & phạm vi
            </h3>

            <div className="account-field">
              <label className="account-label account-label-required" htmlFor="internal-role">
                Vai trò nội bộ
              </label>
              <select
                id="internal-role"
                className="account-select"
                value={role}
                onChange={e => {
                  const newRole = e.target.value as 'STAFF' | 'MANAGER';
                  setRole(newRole);
                  if (newRole === 'STAFF' && selectedLocations.length > 1) {
                    setSelectedLocations([selectedLocations[0]]);
                  }
                }}
                disabled={loading}
              >
                <option value="STAFF">Nhân viên cơ sở (STAFF)</option>
                <option value="MANAGER">Quản lý cơ sở (MANAGER)</option>
              </select>
              <span className="account-field-hint" data-testid="role-scope-hint">
                {role === 'MANAGER'
                  ? 'Phân công cơ sở hỗ trợ ghi nhận tổ chức; phạm vi vận hành cho các tác vụ của Quản lý (Manager) là toàn hệ thống. Quản lý phân công 1 hoặc nhiều cơ sở.'
                  : 'Phân công cơ sở giới hạn phạm vi truy cập vận hành của Nhân viên (Staff). Nhân viên phân công đúng 1 cơ sở.'}
              </span>
            </div>

            <div className="account-field">
              <span className="account-label account-label-required">
                {role === 'MANAGER' ? 'Cơ sở phân công tổ chức' : 'Cơ sở làm việc (giới hạn quyền)'}
              </span>
              <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem', marginTop: '0.25rem' }}>
                {AVAILABLE_LOCATIONS.map(loc => {
                  const isChecked = selectedLocations.includes(loc.id);
                  return (
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
                        type={role === 'STAFF' ? 'radio' : 'checkbox'}
                        name="locations"
                        checked={isChecked}
                        onChange={() => handleLocationToggle(loc.id)}
                        disabled={loading}
                      />
                      <span>{loc.name}</span>
                    </label>
                  );
                })}
              </div>
              {fieldErrors.locations && (
                <span className="account-field-error" role="alert">{fieldErrors.locations}</span>
              )}
            </div>

            <div className="account-field" style={{ marginTop: '0.5rem' }}>
              <label style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', fontSize: '0.875rem', cursor: 'pointer', color: '#1a1a1a' }}>
                <input
                  type="checkbox"
                  checked={sendNotification}
                  onChange={e => setSendNotification(e.target.checked)}
                />
                <span>Gửi thông báo hướng dẫn thiết lập tài khoản đến email công việc</span>
              </label>
            </div>

            <div style={{ display: 'flex', gap: '1rem', marginTop: '1rem' }}>
              <button
                type="submit"
                className="account-btn account-btn-primary"
                disabled={loading}
              >
                {loading ? 'Đang khởi tạo…' : 'Khởi tạo tài khoản'}
              </button>
              <Link to="/admin/accounts" className="account-btn account-btn-secondary">
                Hủy bỏ
              </Link>
            </div>
          </form>
        </div>
      )}
    </div>
  );
};

export default InternalAccountCreatePage;
