import React, { useState, useEffect, useRef } from 'react';
import { useNavigate, Link } from 'react-router';
import { useProfile } from '../hooks/useProfile';
import { useVerifiedEmailGate } from '../hooks/useVerifiedEmailGate';
import { firebaseAuthClient } from '../auth/FirebaseAuthClient';
import '../accountmanagement.css';

export interface ProfilePageProps {
  initialMode?: 'overview' | 'edit' | 'password' | 'email';
  initialSection?: 'profile' | 'password' | 'email';
}

export const ProfilePage: React.FC<ProfilePageProps> = ({
  initialMode,
  initialSection,
}) => {
  const navigate = useNavigate();
  const {
    profile,
    fullName,
    setFullName,
    phone,
    setPhone,
    fieldErrors,
    loading: profileLoading,
    saving,
    error: profileError,
    successNotice: profileSuccess,
    saveProfile,
    resetForm,
  } = useProfile();

  const editSectionRef = useRef<HTMLDivElement>(null);
  const passwordSectionRef = useRef<HTMLDivElement>(null);
  const emailSectionRef = useRef<HTMLDivElement>(null);

  const handleSessionExpired = React.useCallback(() => {
    resetForm();
    navigate('/sign-in');
  }, [resetForm, navigate]);

  const {
    gateState,
    isVerified,
    error: gateError,
    resendCooldownSeconds,
    canResend,
    resending,
    checkGate,
    resendVerification,
  } = useVerifiedEmailGate({
    accountId: profile?.id,
    initialVerified: profile?.emailVerified,
    onSessionExpired: handleSessionExpired,
  });

  // Run gate check on initial Profile mount
  useEffect(() => {
    checkGate();
  }, [checkGate]);

  // Scroll to section if specified
  useEffect(() => {
    if (initialMode === 'edit') {
      editSectionRef.current?.scrollIntoView({ behavior: 'smooth' });
    } else if (initialMode === 'password' || initialSection === 'password') {
      passwordSectionRef.current?.scrollIntoView({ behavior: 'smooth' });
    } else if (initialMode === 'email' || initialSection === 'email') {
      emailSectionRef.current?.scrollIntoView({ behavior: 'smooth' });
    }
  }, [initialMode, initialSection]);

  // Password change state
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [passwordError, setPasswordError] = useState<string | null>(null);
  const [passwordSuccess, setPasswordSuccess] = useState<string | null>(null);
  const [passwordPending, setPasswordPending] = useState(false);

  // Email change state
  const [newEmail, setNewEmail] = useState('');
  const [emailChangeError, setEmailChangeError] = useState<string | null>(null);
  const [emailChangeSuccess, setEmailChangeSuccess] = useState<string | null>(null);
  const [emailChangePending, setEmailChangePending] = useState(false);

  const handleProfileSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!isVerified) return;
    await saveProfile();
  };

  const handlePasswordChange = async (e: React.FormEvent) => {
    e.preventDefault();
    setPasswordError(null);
    setPasswordSuccess(null);

    if (newPassword.length < 6) {
      setPasswordError('Mật khẩu mới phải chứa ít nhất 6 ký tự.');
      return;
    }
    if (newPassword !== confirmPassword) {
      setPasswordError('Mật khẩu xác nhận không khớp.');
      return;
    }

    setPasswordPending(true);
    try {
      await firebaseAuthClient.updatePassword(newPassword);
      setPasswordSuccess('Đổi mật khẩu thành công.');
    } catch (err: any) {
      if (err?.code === 'auth/requires-recent-login') {
        setPasswordError(
          'Thao tác yêu cầu xác thực gần đây. Vui lòng đăng nhập lại trước khi đổi mật khẩu.'
        );
      } else {
        setPasswordError(err?.message || 'Không thể đổi mật khẩu. Vui lòng thử lại.');
      }
    } finally {
      // Per AGENT.md & Plan: No password field value retained after submission
      setNewPassword('');
      setConfirmPassword('');
      setPasswordPending(false);
    }
  };

  const handleEmailChange = async (e: React.FormEvent) => {
    e.preventDefault();
    setEmailChangeError(null);
    setEmailChangeSuccess(null);

    const trimmed = newEmail.trim();
    if (!trimmed || !trimmed.includes('@')) {
      setEmailChangeError('Vui lòng nhập email hợp lệ.');
      return;
    }

    setEmailChangePending(true);
    try {
      // FR-006: verifyBeforeUpdateEmail sends a confirmation link to new email.
      // Current email remains active until user clicks verification link.
      await firebaseAuthClient.verifyBeforeUpdateEmail(trimmed);
      setEmailChangeSuccess(
        `Thư xác nhận đã được gửi đến ${trimmed}. Email hiện tại của bạn sẽ được giữ nguyên cho đến khi xác minh hoàn tất.`
      );
      setNewEmail('');
    } catch (err: any) {
      if (err?.code === 'auth/requires-recent-login') {
        setEmailChangeError(
          'Thao tác yêu cầu xác thực gần đây. Vui lòng đăng nhập lại trước khi đổi email.'
        );
      } else {
        setEmailChangeError(err?.message || 'Không thể gửi thư đổi email. Vui lòng thử lại.');
      }
    } finally {
      setEmailChangePending(false);
    }
  };

  if (profileLoading) {
    return (
      <div className="account-page-container">
        <div className="account-card">
          <p>Đang tải thông tin hồ sơ tài khoản…</p>
        </div>
      </div>
    );
  }

  // Not signed in handling
  const user = firebaseAuthClient.getCurrentUser();
  if (!user && !profile && !profileLoading) {
    return (
      <div className="account-page-container">
        <div className="account-card" style={{ textAlign: 'center', padding: '3rem 1rem' }}>
          <h2 style={{ marginBottom: '1rem' }}>Yêu cầu đăng nhập</h2>
          <p style={{ color: '#4b5563', marginBottom: '1.5rem' }}>
            Vui lòng đăng nhập vào tài khoản để xem và quản lý hồ sơ cá nhân của bạn.
          </p>
          <Link to="/login" className="account-btn account-btn-primary">
            Đăng nhập ngay
          </Link>
        </div>
      </div>
    );
  }

  const initialLetter = (profile?.fullName || profile?.email || 'M').charAt(0).toUpperCase();

  return (
    <div className="account-page-container">
      <header className="account-page-header">
        <h1 className="account-page-title">Hồ sơ cá nhân & Bảo mật</h1>
        <p className="account-page-description">
          Quản lý thông tin tài khoản thành viên, cập nhật liên hệ và các tùy chọn bảo mật.
        </p>
      </header>

      {/* Email Verification Banner (FR-004, FR-005, WF-08) */}
      {!isVerified && (
        <div
          className="account-alert account-alert-warning"
          role="region"
          aria-label="Xác thực email"
        >
          <div>
            <strong>Email của bạn chưa được xác thực!</strong>
            <p style={{ margin: '0.25rem 0 0.5rem 0' }}>
              Một số tính năng yêu cầu email đã được xác minh. Vui lòng kiểm tra hộp thư đến của bạn.
            </p>
            <div
              style={{
                display: 'flex',
                gap: '0.75rem',
                alignItems: 'center',
                marginTop: '0.5rem',
              }}
            >
              <button
                type="button"
                className="account-btn account-btn-secondary account-btn-sm"
                onClick={resendVerification}
                disabled={!canResend || resending}
              >
                {resending
                  ? 'Đang gửi…'
                  : resendCooldownSeconds > 0
                  ? `Gửi lại sau ${resendCooldownSeconds}s`
                  : 'Gửi lại email xác thực'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Gate state check box with accessible reload icon (WF-08 / T051) */}
      <div className="account-gate-box">
        <div>
          <span style={{ fontSize: '0.875rem', color: '#4b5563' }}>
            Trạng thái xác thực email:{' '}
            <strong style={{ color: isVerified ? '#15803d' : '#b45309' }}>
              {gateState === 'checking'
                ? 'Đang kiểm tra…'
                : isVerified
                ? 'Đã xác thực'
                : 'Chưa xác thực'}
            </strong>
          </span>
        </div>
        <button
          type="button"
          className="account-reload-btn"
          onClick={checkGate}
          disabled={gateState === 'checking'}
          aria-label="Kiểm tra lại trạng thái xác thực email"
          title="Tải lại trạng thái xác thực từ hệ thống"
        >
          <span aria-hidden="true">🔄</span>
          <span>{gateState === 'checking' ? 'Đang kiểm tra…' : 'Tải lại xác thực'}</span>
        </button>
      </div>

      {gateError && (
        <div className="account-alert account-alert-error" role="alert">
          <span>{gateError.message || 'Lỗi khi kiểm tra trạng thái xác thực email.'}</span>
        </div>
      )}

      {/* SECTION 1: Personal Profile Overview (Màn Hồ sơ cá nhân theo screen.md) */}
      <section className="account-card profile-overview-card" aria-labelledby="personal-profile-heading">
        <div className="profile-hero">
          <div className="profile-avatar" aria-hidden="true">
            {initialLetter}
          </div>
          <div className="profile-hero-info">
            <h2 id="personal-profile-heading">
              {profile?.fullName || 'Khách hàng thành viên'}
            </h2>
            <p>{profile?.email}</p>
            <div style={{ marginTop: '0.375rem' }}>
              <span className="account-badge account-badge-active">
                {profile?.role || 'MEMBER'}
              </span>
              <span
                className="account-badge"
                style={{
                  marginLeft: '0.5rem',
                  backgroundColor: isVerified ? '#dcfce7' : '#fef9c3',
                  color: isVerified ? '#166534' : '#854d0e',
                }}
              >
                {isVerified ? 'Email đã xác thực' : 'Email chưa xác thực'}
              </span>
            </div>
          </div>
        </div>

        <div className="profile-details-grid">
          <div className="profile-detail-item">
            <span className="profile-detail-label">Họ và tên</span>
            <span className="profile-detail-value">{profile?.fullName || '(Chưa cập nhật)'}</span>
          </div>

          <div className="profile-detail-item">
            <span className="profile-detail-label">Email đăng nhập</span>
            <span className="profile-detail-value">{profile?.email}</span>
          </div>

          <div className="profile-detail-item">
            <span className="profile-detail-label">Số điện thoại</span>
            <span className="profile-detail-value">{profile?.phone || '(Chưa cập nhật)'}</span>
          </div>

          <div className="profile-detail-item">
            <span className="profile-detail-label">Mã tài khoản (ID)</span>
            <span className="profile-detail-value">#{profile?.id}</span>
          </div>

          <div className="profile-detail-item">
            <span className="profile-detail-label">Trạng thái tài khoản</span>
            <span className="profile-detail-value">{profile?.status || 'ACTIVE'}</span>
          </div>

          <div className="profile-detail-item">
            <span className="profile-detail-label">Thời điểm tham gia</span>
            <span className="profile-detail-value">{profile?.createdAt || 'Hôm nay'}</span>
          </div>
        </div>

        <div className="profile-actions-bar">
          <button
            type="button"
            className="account-btn account-btn-primary"
            onClick={() => editSectionRef.current?.scrollIntoView({ behavior: 'smooth' })}
          >
            Chỉnh sửa hồ sơ
          </button>
          <button
            type="button"
            className="account-btn account-btn-secondary"
            onClick={() => passwordSectionRef.current?.scrollIntoView({ behavior: 'smooth' })}
          >
            Đổi mật khẩu
          </button>
          <button
            type="button"
            className="account-btn account-btn-secondary"
            onClick={() => emailSectionRef.current?.scrollIntoView({ behavior: 'smooth' })}
          >
            Thay đổi email
          </button>
          <Link
            to="/profile/addresses"
            className="account-btn account-btn-secondary"
          >
            Sổ địa chỉ nhận hàng
          </Link>
        </div>
      </section>

      {/* SECTION: Address Book Summary Card (WF-09 / US6) */}
      <section className="account-card" aria-labelledby="address-book-heading">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '1rem' }}>
          <div>
            <h2 id="address-book-heading" className="account-card-title" style={{ marginBottom: '0.25rem' }}>
              Sổ địa chỉ nhận hàng
            </h2>
            <p style={{ color: '#4b5563', margin: 0, fontSize: '0.95rem' }}>
              Quản lý danh sách địa chỉ giao nhận hàng của bạn (tối đa 10 địa chỉ), tương thích đơn vị vận chuyển GHTK.
            </p>
          </div>
          <Link to="/profile/addresses" className="account-btn account-btn-primary">
            Quản lý sổ địa chỉ →
          </Link>
        </div>
      </section>

      {/* SECTION 2: Edit Profile Form (screen.md: Edit Profile — form) */}
      <section
        ref={editSectionRef}
        className="account-card"
        aria-labelledby="profile-heading"
      >
        <h2 id="profile-heading" className="account-card-title">
          Chỉnh sửa thông tin hồ sơ
        </h2>

        {profileSuccess && (
          <div className="account-alert account-alert-success" role="status">
            <span>{profileSuccess}</span>
          </div>
        )}

        {profileError && (
          <div className="account-alert account-alert-error" role="alert">
            <span>{profileError.message || 'Không thể cập nhật hồ sơ.'}</span>
          </div>
        )}

        <form className="account-form" onSubmit={handleProfileSubmit} noValidate>
          <div className="account-field">
            <label className="account-label" htmlFor="profile-email">
              Địa chỉ đăng nhập
            </label>
            <input
              id="profile-email"
              type="text"
              className="account-input"
              value={profile?.email || ''}
              disabled
              style={{ backgroundColor: '#f3f4f6', cursor: 'not-allowed' }}
            />
            <span className="account-field-hint">
              Địa chỉ email được định danh bởi nhà cung cấp xác thực. Muốn đổi email hãy dùng phần bên dưới.
            </span>
          </div>

          <div className="account-field">
            <label className="account-label account-label-required" htmlFor="profile-name">
              Họ và tên
            </label>
            <input
              id="profile-name"
              type="text"
              className="account-input"
              value={fullName}
              onChange={e => setFullName(e.target.value)}
              maxLength={100}
              aria-invalid={Boolean(fieldErrors.fullName)}
              disabled={saving}
            />
            {fieldErrors.fullName && (
              <span className="account-field-error" role="alert">
                {fieldErrors.fullName}
              </span>
            )}
            <span className="account-field-hint">
              Tối đa 100 ký tự Unicode, không chứa ký tự điều khiển.
            </span>
          </div>

          <div className="account-field">
            <label className="account-label" htmlFor="profile-phone">
              Số điện thoại
            </label>
            <input
              id="profile-phone"
              type="tel"
              className="account-input"
              value={phone}
              onChange={e => setPhone(e.target.value)}
              maxLength={30}
              aria-invalid={Boolean(fieldErrors.phone)}
              disabled={saving}
            />
            {fieldErrors.phone && (
              <span className="account-field-error" role="alert">
                {fieldErrors.phone}
              </span>
            )}
            <span className="account-field-hint">Từ 7 đến 30 ký tự (không bắt buộc).</span>
          </div>

          <div style={{ display: 'flex', gap: '0.75rem', alignItems: 'center' }}>
            <button
              type="submit"
              className="account-btn account-btn-primary"
              disabled={!isVerified || saving}
              title={!isVerified ? 'Cần xác thực email trước khi lưu thay đổi' : undefined}
            >
              {saving ? 'Đang lưu…' : 'Lưu thay đổi'}
            </button>
            <button
              type="button"
              className="account-btn account-btn-secondary"
              onClick={resetForm}
              disabled={saving}
            >
              Khôi phục
            </button>
            {!isVerified && (
              <span style={{ fontSize: '0.8125rem', color: '#b45309' }}>
                * Bạn cần xác thực email trước khi có thể chỉnh sửa hồ sơ.
              </span>
            )}
          </div>
        </form>
      </section>

      {/* SECTION 3: Change Password Form (screen.md: Change Password — form) */}
      <section
        ref={passwordSectionRef}
        className="account-card"
        aria-labelledby="password-heading"
      >
        <h2 id="password-heading" className="account-card-title">
          Đổi mật khẩu
        </h2>

        {passwordSuccess && (
          <div className="account-alert account-alert-success" role="status">
            <span>{passwordSuccess}</span>
          </div>
        )}

        {passwordError && (
          <div className="account-alert account-alert-error" role="alert">
            <span>{passwordError}</span>
          </div>
        )}

        <form className="account-form" onSubmit={handlePasswordChange}>
          <div className="account-field">
            <label className="account-label account-label-required" htmlFor="new-password">
              Mật khẩu mới
            </label>
            <input
              id="new-password"
              type="password"
              className="account-input"
              value={newPassword}
              onChange={e => setNewPassword(e.target.value)}
              autoComplete="new-password"
              disabled={passwordPending}
            />
          </div>

          <div className="account-field">
            <label className="account-label account-label-required" htmlFor="confirm-new-password">
              Xác nhận mật khẩu mới
            </label>
            <input
              id="confirm-new-password"
              type="password"
              className="account-input"
              value={confirmPassword}
              onChange={e => setConfirmPassword(e.target.value)}
              autoComplete="new-password"
              disabled={passwordPending}
            />
          </div>

          <div>
            <button
              type="submit"
              className="account-btn account-btn-secondary"
              disabled={passwordPending || !newPassword}
            >
              {passwordPending ? 'Đang xử lý…' : 'Cập nhật mật khẩu'}
            </button>
          </div>
        </form>
      </section>

      {/* SECTION 4: Email Change Form */}
      <section
        ref={emailSectionRef}
        className="account-card"
        aria-labelledby="email-change-heading"
      >
        <h2 id="email-change-heading" className="account-card-title">
          Thay đổi địa chỉ email
        </h2>

        {emailChangeSuccess && (
          <div className="account-alert account-alert-success" role="status">
            <span>{emailChangeSuccess}</span>
          </div>
        )}

        {emailChangeError && (
          <div className="account-alert account-alert-error" role="alert">
            <span>{emailChangeError}</span>
          </div>
        )}

        <form className="account-form" onSubmit={handleEmailChange}>
          <div className="account-field">
            <label className="account-label account-label-required" htmlFor="new-email">
              Địa chỉ email mới
            </label>
            <input
              id="new-email"
              type="email"
              className="account-input"
              value={newEmail}
              onChange={e => setNewEmail(e.target.value)}
              placeholder="nhap.email.moi@example.com"
              disabled={emailChangePending}
            />
            <span className="account-field-hint">
              Hệ thống sẽ gửi email xác thực đến địa chỉ mới. Email đăng nhập chỉ thay đổi sau khi bạn xác minh.
            </span>
          </div>

          <div>
            <button
              type="submit"
              className="account-btn account-btn-secondary"
              disabled={emailChangePending || !newEmail.trim()}
            >
              {emailChangePending ? 'Đang gửi…' : 'Yêu cầu đổi email'}
            </button>
          </div>
        </form>
      </section>
    </div>
  );
};

export default ProfilePage;
