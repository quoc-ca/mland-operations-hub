import React, { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router';
import { useAdminAccounts } from '../hooks/useAdminAccounts';
import { useWorkshopLocations } from '../hooks/useWorkshopLocations';
import { newCommandId } from '../../../shared/api/http';
import { useT, useFormat } from '../../../shared/i18n/I18nProvider';
import { accountMessages } from '../messages';
import {
  accountManagementApiClient,
  EmailCollisionResponse,
  ReplacementReviewResponse,
} from '../api/AccountManagementApiClient';
import { MemberCollisionReviewModal } from './MemberCollisionReviewModal';
import { FirebaseOnlyCollisionModal } from './FirebaseOnlyCollisionModal';
import '../accountmanagement.css';

export const InternalAccountCreatePage: React.FC = () => {
  const t = useT(accountMessages);
  const fmt = useFormat();
  const navigate = useNavigate();
  const { createInternalAccount, loading, error } = useAdminAccounts();
  const { locations, error: locationsError } = useWorkshopLocations();
  // Only active workshop locations can receive a new assignment
  const availableLocations = locations.filter(loc => loc.active);

  const [role, setRole] = useState<'STAFF' | 'MANAGER' | 'CARRIER'>('STAFF');
  const [fullName, setFullName] = useState('');
  const [phone, setPhone] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [selectedLocations, setSelectedLocations] = useState<number[]>([]);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [createdSummary, setCreatedSummary] = useState<any | null>(null);

  // Collision state per R-INV
  const [checkingCollision, setCheckingCollision] = useState(false);
  const [collisionNotice, setCollisionNotice] = useState<{
    type: 'MAINTENANCE' | 'BLOCKED_ROLE' | 'ERROR';
    message: string;
    accountId?: number;
  } | null>(null);

  const [reviewModalOpen, setReviewModalOpen] = useState(false);
  const [memberReview, setMemberReview] = useState<ReplacementReviewResponse | null>(null);

  const [fbOnlyModalOpen, setFbOnlyModalOpen] = useState(false);
  const [fbCollisionEmail, setFbCollisionEmail] = useState('');

  // Preserve idempotency key across retries for the same submit action
  const [idempotencyKey, setIdempotencyKey] = useState<string>(() => newCommandId());

  // Preselect the first active location once the real list has loaded
  useEffect(() => {
    if (selectedLocations.length === 0 && availableLocations.length > 0) {
      setSelectedLocations([availableLocations[0].id]);
    }
  }, [availableLocations, selectedLocations.length]);

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
      errors.fullName = t('create.val.nameRequired');
    } else if (trimmedName.length > 100) {
      errors.fullName = t('create.val.nameLength');
    }

    // Email is required and normalized for all roles (R-INV FR-048)
    const trimmedEmail = email.trim();
    if (!trimmedEmail) {
      errors.email = t('create.val.carrierEmailRequired') || 'Email là bắt buộc.';
    } else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(trimmedEmail)) {
      errors.email = t('create.val.emailFormat');
    }

    if (role === 'CARRIER') {
      if (!password) {
        errors.password = t('create.val.carrierPasswordRequired');
      } else if (password.length < 6) {
        errors.password = t('create.val.passwordLength');
      }

      const trimmedPhone = phone.trim();
      if (!trimmedPhone) {
        errors.phone = t('create.val.carrierPhoneRequired');
      } else if (trimmedPhone.length < 7 || trimmedPhone.length > 30) {
        errors.phone = t('create.val.phoneLength');
      }
    } else {
      if (phone && phone.trim()) {
        const trimmedPhone = phone.trim();
        if (trimmedPhone.length < 7 || trimmedPhone.length > 30) {
          errors.phone = t('create.val.phoneLength');
        }
      }

      if (selectedLocations.length === 0) {
        errors.locations = t('create.val.locationRequired');
      } else if (role === 'STAFF' && selectedLocations.length !== 1) {
        errors.locations = t('create.val.staffOneLocation');
      }
    }

    setFieldErrors(errors);
    return Object.keys(errors).length === 0;
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setCollisionNotice(null);

    if (!validate()) return;

    const normalizedEmail = email.trim().toLowerCase();

    // For STAFF/MANAGER, perform email collision check per FR-049
    if (role !== 'CARRIER') {
      setCheckingCollision(true);
      try {
        const collision = await accountManagementApiClient.resolveEmailCollision(normalizedEmail);
        setCheckingCollision(false);

        if (collision.outcome === 'STAFF_MANAGER_MAINTENANCE') {
          setCollisionNotice({
            type: 'MAINTENANCE',
            message: `Email này đã thuộc về một tài khoản STAFF hoặc MANAGER hiện có (ACC-${collision.accountId}). Bạn có thể chuyển đến trang quản trị để xem và cập nhật.`,
            accountId: collision.accountId,
          });
          return;
        }

        if (collision.outcome === 'BLOCKED_ROLE') {
          setCollisionNotice({
            type: 'BLOCKED_ROLE',
            message: 'Email này đã được liên kết với vai trò ADMIN hoặc CARRIER và không thể thay thế tại đây.',
          });
          return;
        }

        if (collision.outcome === 'FIREBASE_ONLY') {
          setFbCollisionEmail(normalizedEmail);
          setFbOnlyModalOpen(true);
          return;
        }

        if (collision.outcome === 'MEMBER_REVIEW' && collision.accountId) {
          const rev = await accountManagementApiClient.reviewMemberReplacement(collision.accountId);
          setMemberReview(rev);
          setReviewModalOpen(true);
          return;
        }
      } catch (colErr: any) {
        setCheckingCollision(false);
        // If endpoint is not found in mock/test, fall through to creation
        if (colErr?.status !== 404) {
          setCollisionNotice({
            type: 'ERROR',
            message: colErr?.message || 'Không thể kiểm tra xung đột email.',
          });
          return;
        }
      }
    }

    // Proceed with account creation
    await executeCreation(normalizedEmail);
  };

  const executeCreation = async (normalizedEmail: string) => {
    const payload: any = {
      role,
      email: normalizedEmail,
      fullName: fullName.trim(),
      phone: phone.trim() || undefined,
    };

    if (role === 'CARRIER') {
      payload.password = password;
      // locationIds must be omitted for CARRIER (FR-011, FR-012)
    } else {
      payload.locationIds = selectedLocations;
    }

    // Ephemeral write-only password handling: clear input from memory
    setPassword('');

    const result = await createInternalAccount(payload, idempotencyKey);

    if (result) {
      setCreatedSummary(result);
    }
  };

  const handleResetForNew = () => {
    setFullName('');
    setPhone('');
    setEmail('');
    setPassword('');
    setSelectedLocations(availableLocations.length > 0 ? [availableLocations[0].id] : []);
    setRole('STAFF');
    setFieldErrors({});
    setCreatedSummary(null);
    setCollisionNotice(null);
    setIdempotencyKey(newCommandId());
  };

  return (
    <div className="account-page-container">
      {/* Back button */}
      <div>
        <Link to="/admin/accounts" className="account-back-btn">
          {t('create.back')}
        </Link>
      </div>

      {/* Admin Navigation Tabs — Role catalogue removed per T113 */}
      <nav className="admin-nav-tabs" aria-label={t('create.navAria')}>
        <Link to="/admin/accounts" className="admin-nav-tab active">
          {t('create.navAccounts')}
        </Link>
      </nav>

      <header className="account-page-header">
        <h1 className="account-page-title">{t('create.title')}</h1>
        <p className="account-page-description">
          {t('create.description')}
        </p>
      </header>

      {/* Collision Notice Banners */}
      {collisionNotice && (
        <div
          className={`account-alert ${
            collisionNotice.type === 'MAINTENANCE' ? 'account-alert-info' : 'account-alert-error'
          }`}
          role="alert"
          style={{ marginBottom: '1.5rem' }}
          data-testid="collision-notice-banner"
        >
          <div>
            <strong>{collisionNotice.message}</strong>
            {collisionNotice.accountId && (
              <div style={{ marginTop: '0.5rem' }}>
                <Link
                  to={`/admin/accounts/${collisionNotice.accountId}`}
                  className="account-btn account-btn-primary"
                  style={{ display: 'inline-block', fontSize: '0.85rem' }}
                >
                  Đi đến trang quản trị tài khoản ACC-{collisionNotice.accountId}
                </Link>
              </div>
            )}
          </div>
        </div>
      )}

      {createdSummary ? (
        <section className="account-card" aria-labelledby="created-title">
          <h2 id="created-title" className="account-card-title" style={{ color: '#15803d' }}>
            {t('create.successTitle')}
          </h2>

          <div className="account-alert account-alert-info">
            <div>
              <strong>
                {t('create.statusLabel')}{' '}
                {createdSummary.status === 'ACTIVE'
                  ? t('create.statusActive')
                  : t('create.statusPending')}
              </strong>
              <p style={{ margin: '0.25rem 0' }}>
                {createdSummary.role === 'CARRIER'
                  ? t('create.carrierDone')
                  : 'Tài khoản nhân sự đã được khởi tạo và liên kết định danh. Thư mời kích hoạt đã được đưa vào hàng đợi gửi qua hệ thống thông báo.'}
              </p>
              <p style={{ margin: '0.25rem 0', fontSize: '0.85rem', color: '#4b5563' }}>
                {createdSummary.role === 'CARRIER'
                  ? t('create.carrierNote')
                  : 'Liên kết thư mời có hiệu lực trong vòng 48 giờ từ thời điểm tạo. Người nhận hoàn tất thiết lập mật khẩu hoặc liên kết Google để kích hoạt tài khoản.'}
              </p>
            </div>
          </div>

          <table className="account-table" style={{ marginBottom: '1.5rem' }}>
            <tbody>
              <tr>
                <th style={{ width: '200px' }}>{t('create.accountId')}</th>
                <td>ACC-{createdSummary.accountId}</td>
              </tr>
              <tr>
                <th>{t('create.role')}</th>
                <td>
                  <span className="account-badge account-badge-readonly">{createdSummary.role}</span>
                </td>
              </tr>
              <tr>
                <th>{t('create.identityEmail')}</th>
                <td>{createdSummary.loginEmail}</td>
              </tr>
              <tr>
                <th>{t('create.accountStatus')}</th>
                <td>
                  <span
                    className={
                      createdSummary.status === 'ACTIVE'
                        ? 'account-badge account-badge-active'
                        : 'account-badge account-badge-pending'
                    }
                  >
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
              {t('create.viewDetail')}
            </Link>
            <button
              type="button"
              className="account-btn account-btn-secondary"
              onClick={handleResetForNew}
            >
              {t('create.another')}
            </button>
            <Link to="/admin/accounts" className="account-btn account-btn-secondary">
              {t('create.list')}
            </Link>
          </div>
        </section>
      ) : (
        <div className="account-card">
          {error && (
            <div className="account-alert account-alert-error" role="alert">
              <span>{error.message || t('create.error')}</span>
            </div>
          )}

          <form className="account-form" onSubmit={handleSubmit} noValidate>
            <h3 style={{ fontSize: '1.05rem', fontWeight: 600, margin: '0 0 0.5rem 0', color: '#1a1a1a', borderBottom: '1px solid #eceef1', paddingBottom: '0.5rem' }}>
              {t('create.sectionPersonal')}
            </h3>

            {/* Email Field is mandatory for ALL roles per R-INV FR-048 */}
            <div className="account-field">
              <label className="account-label account-label-required" htmlFor="create-account-email">
                {role === 'CARRIER' ? t('create.carrierEmail') : 'Email định danh & nhận thư mời (bắt buộc)'}
              </label>
              <input
                id="create-account-email"
                type="email"
                className="account-input"
                placeholder={role === 'CARRIER' ? t('create.carrierEmailPlaceholder') : 'Ví dụ: nhansu@mland.vn'}
                value={email}
                onChange={e => setEmail(e.target.value)}
                aria-invalid={Boolean(fieldErrors.email)}
                disabled={loading || checkingCollision}
                required
              />
              <span className="account-field-hint">
                {role === 'CARRIER'
                  ? 'Email đối tác đăng nhập trực tiếp (bất biến).'
                  : 'Email dùng để tạo liên kết thư mời kích hoạt có thời hạn 48 giờ.'}
              </span>
              {fieldErrors.email && (
                <span className="account-field-error" role="alert">{fieldErrors.email}</span>
              )}
            </div>

            {/* Password ONLY for CARRIER per R-CAR / R-INV (FR-048 rejects Staff/Manager password input) */}
            {role === 'CARRIER' && (
              <div className="account-field">
                <label className="account-label account-label-required" htmlFor="carrier-password">
                  {t('create.initialPassword')}
                </label>
                <input
                  id="carrier-password"
                  type="password"
                  className="account-input"
                  placeholder={t('create.passwordPlaceholder')}
                  value={password}
                  onChange={e => setPassword(e.target.value)}
                  aria-invalid={Boolean(fieldErrors.password)}
                  disabled={loading}
                />
                <span className="account-field-hint">
                  {t('create.passwordHint')}
                </span>
                {fieldErrors.password && (
                  <span className="account-field-error" role="alert">{fieldErrors.password}</span>
                )}
              </div>
            )}

            <div className="account-field">
              <label className="account-label account-label-required" htmlFor="internal-name">
                {role === 'CARRIER' ? t('create.nameCarrier') : t('create.nameStaff')}
              </label>
              <input
                id="internal-name"
                type="text"
                className="account-input"
                placeholder={role === 'CARRIER' ? t('create.namePlaceholderCarrier') : t('create.namePlaceholderStaff')}
                value={fullName}
                onChange={e => setFullName(e.target.value)}
                maxLength={100}
                aria-invalid={Boolean(fieldErrors.fullName)}
                disabled={loading || checkingCollision}
              />
              {fieldErrors.fullName && (
                <span className="account-field-error" role="alert">{fieldErrors.fullName}</span>
              )}
            </div>

            <div className="account-field">
              <label
                className={`account-label ${role === 'CARRIER' ? 'account-label-required' : ''}`}
                htmlFor="internal-phone"
              >
                {role === 'CARRIER' ? t('create.phoneCarrier') : t('create.phoneStaff')}
              </label>
              <input
                id="internal-phone"
                type="tel"
                className="account-input"
                placeholder={t('create.phonePlaceholder')}
                value={phone}
                onChange={e => setPhone(e.target.value)}
                maxLength={30}
                aria-invalid={Boolean(fieldErrors.phone)}
                disabled={loading || checkingCollision}
              />
              {fieldErrors.phone && (
                <span className="account-field-error" role="alert">{fieldErrors.phone}</span>
              )}
            </div>

            <h3 style={{ fontSize: '1.05rem', fontWeight: 600, margin: '1rem 0 0.5rem 0', color: '#1a1a1a', borderBottom: '1px solid #eceef1', paddingBottom: '0.5rem' }}>
              {t('create.sectionRole')}
            </h3>

            {/* Role guidance per T113 */}
            <div className="account-field">
              <label className="account-label account-label-required" htmlFor="internal-role">
                {t('create.internalRole')}
              </label>
              <select
                id="internal-role"
                className="account-select"
                value={role}
                onChange={e => {
                  const newRole = e.target.value as 'STAFF' | 'MANAGER' | 'CARRIER';
                  setRole(newRole);
                  if (newRole === 'STAFF' && selectedLocations.length > 1) {
                    setSelectedLocations([selectedLocations[0]]);
                  }
                }}
                disabled={loading || checkingCollision}
              >
                <option value="STAFF">{t('create.optStaff')}</option>
                <option value="MANAGER">{t('create.optManager')}</option>
                <option value="CARRIER">{t('create.optCarrier')}</option>
              </select>
              <span className="account-field-hint" data-testid="role-scope-hint">
                {role === 'CARRIER'
                  ? t('create.hintCarrier')
                  : role === 'MANAGER'
                  ? t('create.hintManager')
                  : t('create.hintStaff')}
              </span>
            </div>

            {/* Location selector (Staff: exactly 1, Manager: 1+) */}
            {role !== 'CARRIER' && (
              <div className="account-field">
                <span className="account-label account-label-required">
                  {role === 'MANAGER' ? t('create.locationsManager') : t('create.locationsStaff')}
                </span>
                <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem', marginTop: '0.25rem' }}>
                  {locationsError && (
                    <span className="account-field-error" role="alert">{locationsError}</span>
                  )}
                  {availableLocations.map(loc => {
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
                          disabled={loading || checkingCollision}
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
            )}

            <div style={{ display: 'flex', gap: '1rem', marginTop: '1.25rem' }}>
              <button
                type="submit"
                className="account-btn account-btn-primary"
                disabled={loading || checkingCollision}
              >
                {checkingCollision
                  ? 'Đang kiểm tra email…'
                  : loading
                  ? t('create.submitting')
                  : t('create.submit')}
              </button>
              <Link to="/admin/accounts" className="account-btn account-btn-secondary">
                {t('create.cancel')}
              </Link>
            </div>
          </form>
        </div>
      )}

      {/* Member Collision Review Modal */}
      <MemberCollisionReviewModal
        isOpen={reviewModalOpen}
        onClose={() => setReviewModalOpen(false)}
        review={memberReview}
        onRetireSuccess={() => {
          setReviewModalOpen(false);
          // Retry creation after retirement
          executeCreation(email.trim().toLowerCase());
        }}
      />

      {/* Firebase-only Collision Modal */}
      <FirebaseOnlyCollisionModal
        isOpen={fbOnlyModalOpen}
        onClose={() => setFbOnlyModalOpen(false)}
        email={fbCollisionEmail}
        onDeleteSuccess={() => {
          setFbOnlyModalOpen(false);
          // Proceed with creation after FB deletion
          executeCreation(fbCollisionEmail);
        }}
      />
    </div>
  );
};

export default InternalAccountCreatePage;
