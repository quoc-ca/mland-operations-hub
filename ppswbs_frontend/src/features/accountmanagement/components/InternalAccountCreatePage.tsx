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
      <header className="account-page-header">
        <h1 className="account-page-title">Khởi tạo tài khoản nội bộ</h1>
        <p className="account-page-description">
          Khởi tạo tài khoản cho Nhân viên (Staff) hoặc Quản lý (Manager) cơ sở.
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
                  <span className="account-badge account-badge-active">{createdSummary.role}</span>
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

          <div style={{ display: 'flex', gap: '1rem' }}>
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
              <span className="account-field-hint">
                Staff phân công đúng 1 cơ sở; Manager phân công 1 hoặc nhiều cơ sở.
              </span>
            </div>

            <div className="account-field">
              <label className="account-label account-label-required" htmlFor="internal-name">
                Họ và tên
              </label>
              <input
                id="internal-name"
                type="text"
                className="account-input"
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

            <div className="account-field">
              <span className="account-label account-label-required">Cơ sở làm việc</span>
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

