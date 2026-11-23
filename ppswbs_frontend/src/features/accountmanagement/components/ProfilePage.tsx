import React, { useState, useEffect, useRef } from 'react';
import { useNavigate, Link } from 'react-router';
import { registerPrivateStateCleanup } from '../../../shared/auth/privateStateCleanup';
import type { User } from 'firebase/auth';
import { useProfile } from '../hooks/useProfile';
import { useVerifiedEmailGate } from '../hooks/useVerifiedEmailGate';
import { firebaseAuthClient } from '../auth/FirebaseAuthClient';
import { useT } from '../../../shared/i18n/I18nProvider';
import { accountMessages } from '../messages';
import '../accountmanagement.css';

export interface ProfilePageProps {
  initialMode?: 'overview' | 'edit' | 'password' | 'email';
  initialSection?: 'profile' | 'password' | 'email';
}

export const ProfilePage: React.FC<ProfilePageProps> = ({
  initialMode,
  initialSection,
}) => {
  const t = useT(accountMessages);
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

  const [currentUser, setCurrentUser] = useState<User | null>(() => firebaseAuthClient.getCurrentUser());
  const initialAuthCheckedRef = useRef(false);

  useEffect(() => {
    return registerPrivateStateCleanup(() => {
      resetForm();
    });
  }, [resetForm]);

  useEffect(() => {
    const unsub = firebaseAuthClient.onAuthStateChanged(u => {
      setCurrentUser(u);
      if (!initialAuthCheckedRef.current) {
        initialAuthCheckedRef.current = true;
      }
    });
    return () => {
      unsub();
    };
  }, []);

  const activeUser = currentUser || firebaseAuthClient.getCurrentUser();
  // Defensive provider check: hide password section and quick-action trigger if !user.providerData.some(p => p.providerId === 'password')
  const isOAuthOnly = Boolean(
    activeUser?.providerData &&
    activeUser.providerData.length > 0 &&
    !activeUser.providerData.some(p => p.providerId === 'password')
  );
  const showPasswordSection = !isOAuthOnly;

  const handleSessionExpired = React.useCallback(() => {
    resetForm();
  }, [resetForm]);

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

  const isCarrier = profile?.role === 'CARRIER';

  // CARRIER role navigation guard (T087 / FR-017 / WF-11):
  // CARRIER has no Profile or Email self-service; only the eligible password-owner flow is retained.
  // Direct any non-password mode to /carrier.
  useEffect(() => {
    if (isCarrier && initialMode !== 'password' && initialSection !== 'password') {
      navigate('/carrier', { replace: true });
    }
  }, [isCarrier, initialMode, initialSection, navigate]);

  // Password change state (T066)
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [passwordError, setPasswordError] = useState<string | null>(null);
  const [passwordSuccess, setPasswordSuccess] = useState<string | null>(null);
  const [passwordPending, setPasswordPending] = useState(false);

  // Email change state (T067)
  const [emailCurrentPassword, setEmailCurrentPassword] = useState('');
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

    if (!currentPassword) {
      setPasswordError(t('profile.pwCurrentRequired'));
      return;
    }
    if (newPassword.length < 6) {
      setPasswordError(t('profile.pwTooShort'));
      return;
    }
    if (newPassword !== confirmPassword) {
      setPasswordError(t('profile.pwMismatch'));
      return;
    }

    setPasswordPending(true);
    try {
      // In-place re-authentication via reauthenticateWithCredential before updatePassword (T066)
      await firebaseAuthClient.reauthenticateWithCredential(currentPassword);
      await firebaseAuthClient.updatePassword(newPassword);
      setPasswordSuccess(t('profile.pwChanged'));
      setCurrentPassword('');
      setNewPassword('');
      setConfirmPassword('');
    } catch (err: any) {
      // On re-auth failure (wrong current password), clear only currentPassword, show inline error, retain newPassword/confirmPassword
      setCurrentPassword('');
      if (err?.code === 'auth/wrong-password' || err?.code === 'auth/invalid-credential') {
        setPasswordError(t('profile.pwWrong'));
      } else if (err?.code === 'auth/user-token-expired' || err?.code === 'auth/user-disabled') {
        setNewPassword('');
        setConfirmPassword('');
        handleSessionExpired();
      } else {
        setPasswordError(err?.message || t('profile.pwFailed'));
      }
    } finally {
      setPasswordPending(false);
    }
  };

  const handleEmailChange = async (e: React.FormEvent) => {
    e.preventDefault();
    setEmailChangeError(null);
    setEmailChangeSuccess(null);

    const trimmed = newEmail.trim();
    if (!trimmed || !trimmed.includes('@')) {
      setEmailChangeError(t('profile.emailRequired'));
      return;
    }

    setEmailChangePending(true);
    try {
      // In-place re-authentication via reauthenticateWithCredential before verifyBeforeUpdateEmail (T067)
      if (emailCurrentPassword) {
        await firebaseAuthClient.reauthenticateWithCredential(emailCurrentPassword);
      }
      await firebaseAuthClient.verifyBeforeUpdateEmail(trimmed);
      setEmailChangeSuccess(
        t('profile.emailSent', { email: trimmed })
      );
      setEmailCurrentPassword('');
      setNewEmail('');
    } catch (err: any) {
      setEmailCurrentPassword('');
      if (err?.code === 'auth/wrong-password' || err?.code === 'auth/invalid-credential') {
        setEmailChangeError(t('profile.pwWrong'));
      } else if (err?.code === 'auth/user-token-expired' || err?.code === 'auth/user-disabled') {
        setNewEmail('');
        handleSessionExpired();
      } else if (err?.code === 'auth/email-already-in-use') {
        setEmailChangeError(t('profile.emailInUse'));
      } else if (err?.code === 'auth/invalid-email') {
        setEmailChangeError(t('profile.emailInvalid'));
      } else {
        setEmailChangeError(err?.message || t('profile.emailFailed'));
      }
    } finally {
      setEmailChangePending(false);
    }
  };

  if (profileLoading) {
    return (
      <div className="account-page-container">
        <div className="account-card">
          <p>{t('profile.loading')}</p>
        </div>
      </div>
    );
  }

  // Not signed in handling
  if (!activeUser && !profile && !profileLoading) {
    return (
      <div className="account-page-container">
        <div className="account-card" style={{ textAlign: 'center', padding: '3rem 1rem' }}>
          <h2 style={{ marginBottom: '1rem' }}>{t('profile.signInRequiredTitle')}</h2>
          <p style={{ color: '#4b5563', marginBottom: '1.5rem' }}>
            {t('profile.signInRequiredBody')}
          </p>
          <Link to="/login" className="account-btn account-btn-primary">
            {t('profile.signInNow')}
          </Link>
        </div>
      </div>
    );
  }

  if (isCarrier && (initialMode === 'password' || initialSection === 'password')) {
    return (
      <div className="account-page-container">
        <div>
          <Link to="/carrier" className="account-back-btn">
            {t('profile.backToCarrier')}
          </Link>
        </div>

        <header className="account-page-header">
          <h1 className="account-page-title">{t('profile.carrierPwTitle')}</h1>
          <p className="account-page-description">
            {t('profile.carrierPwDescription')}
          </p>
        </header>

        <section
          ref={passwordSectionRef}
          className="account-card"
          aria-labelledby="password-heading"
        >
          <h2 id="password-heading" className="account-card-title">
            {t('profile.changePassword')}
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
              <label className="account-label account-label-required" htmlFor="current-password">
                {t('profile.currentPassword')}
              </label>
              <input
                id="current-password"
                type="password"
                className="account-input"
                value={currentPassword}
                onChange={e => setCurrentPassword(e.target.value)}
                disabled={passwordPending}
                autoComplete="current-password"
              />
            </div>

            <div className="account-field">
              <label className="account-label account-label-required" htmlFor="new-password">
                {t('profile.newPassword')}
              </label>
              <input
                id="new-password"
                type="password"
                className="account-input"
                value={newPassword}
                onChange={e => setNewPassword(e.target.value)}
                minLength={6}
                disabled={passwordPending}
                autoComplete="new-password"
              />
              <span className="account-field-hint">{t('profile.min6')}</span>
            </div>

            <div className="account-field">
              <label className="account-label account-label-required" htmlFor="confirm-password">
                {t('profile.confirmNewPassword')}
              </label>
              <input
                id="confirm-password"
                type="password"
                className="account-input"
                value={confirmPassword}
                onChange={e => setConfirmPassword(e.target.value)}
                minLength={6}
                disabled={passwordPending}
                autoComplete="new-password"
              />
            </div>

            <div style={{ marginTop: '1rem' }}>
              <button
                type="submit"
                className="account-btn account-btn-primary"
                disabled={passwordPending}
              >
                {passwordPending ? t('profile.updating') : t('profile.updatePassword')}
              </button>
            </div>
          </form>
        </section>
      </div>
    );
  }

  const initialLetter = (profile?.fullName || profile?.email || 'M').charAt(0).toUpperCase();

  return (
    <div className="account-page-container">
      {/* Back button matching Figma */}
      <div>
        <Link to="/" className="account-back-btn">
          {t('profile.backHome')}
        </Link>
      </div>

      <header className="account-page-header">
        <h1 className="account-page-title">{t('profile.title')}</h1>
        <p className="account-page-description">
          {t('profile.description')}
        </p>
      </header>

      {/* Email Verification Banner (FR-004, FR-005, WF-08) */}
      {!isVerified && (
        <div
          className="account-alert account-alert-warning"
          role="region"
          aria-label={t('profile.verifyRegion')}
        >
          <div>
            <strong>{t('profile.unverifiedTitle')}</strong>
            <p style={{ margin: '0.25rem 0 0.5rem 0' }}>
              {t('profile.unverifiedBody')}
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
                  ? t('profile.sending')
                  : resendCooldownSeconds > 0
                  ? t('profile.resendAfter', { seconds: resendCooldownSeconds })
                  : t('profile.resend')}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Gate state check box with accessible reload icon (WF-08 / T051) */}
      <div className="account-gate-box">
        <div>
          <span style={{ fontSize: '0.875rem', color: '#4b5563' }}>
            {t('profile.gateStatus')}{' '}
            <strong style={{ color: isVerified ? '#15803d' : '#b45309' }}>
              {gateState === 'checking'
                ? t('profile.checking')
                : isVerified
                ? t('profile.verified')
                : t('profile.notVerified')}
            </strong>
          </span>
        </div>
        <button
          type="button"
          className="account-reload-btn"
          onClick={checkGate}
          disabled={gateState === 'checking'}
          aria-label={t('profile.recheckAria')}
          title={t('profile.reloadTitle')}
        >
          <span aria-hidden="true">🔄</span>
          <span>{gateState === 'checking' ? t('profile.checking') : t('profile.reload')}</span>
        </button>
      </div>

      {gateError && (
        <div className="account-alert account-alert-error" role="alert">
          <span>{gateError.message || t('profile.gateError')}</span>
        </div>
      )}

      {/* SECTION 1: Personal Profile Overview (profile overview screen per screen.md) */}
      <section className="account-card profile-overview-card" aria-labelledby="personal-profile-heading">
        <div className="profile-hero">
          <div className="profile-avatar" aria-hidden="true">
            {initialLetter}
          </div>
          <div className="profile-hero-info">
            <h2 id="personal-profile-heading">
              {profile?.fullName || t('profile.memberFallback')}
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
                {isVerified ? t('profile.emailVerifiedBadge') : t('profile.emailUnverifiedBadge')}
              </span>
            </div>
          </div>
        </div>

        <div className="profile-details-grid">
          <div className="profile-detail-item">
            <span className="profile-detail-label">{t('profile.fullName')}</span>
            <span className="profile-detail-value">{profile?.fullName || t('profile.notSet')}</span>
          </div>

          <div className="profile-detail-item">
            <span className="profile-detail-label">{t('profile.loginEmail')}</span>
            <span className="profile-detail-value">{profile?.email}</span>
          </div>

          <div className="profile-detail-item">
            <span className="profile-detail-label">{t('profile.phone')}</span>
            <span className="profile-detail-value">{profile?.phone || t('profile.notSet')}</span>
          </div>

          <div className="profile-detail-item">
            <span className="profile-detail-label">{t('profile.accountId')}</span>
            <span className="profile-detail-value">#{profile?.id}</span>
          </div>

          <div className="profile-detail-item">
            <span className="profile-detail-label">{t('profile.accountStatus')}</span>
            <span className="profile-detail-value">{profile?.status || 'ACTIVE'}</span>
          </div>

          <div className="profile-detail-item">
            <span className="profile-detail-label">{t('profile.joinedAt')}</span>
            <span className="profile-detail-value">{profile?.createdAt || t('profile.today')}</span>
          </div>
        </div>

        <div className="profile-actions-bar">
          <button
            type="button"
            className="account-btn account-btn-primary"
            onClick={() => editSectionRef.current?.scrollIntoView({ behavior: 'smooth' })}
          >
            {t('profile.editProfile')}
          </button>
          {showPasswordSection && (
            <button
              type="button"
              className="account-btn account-btn-secondary"
              onClick={() => passwordSectionRef.current?.scrollIntoView({ behavior: 'smooth' })}
            >
              {t('profile.changePassword')}
            </button>
          )}
          <button
            type="button"
            className="account-btn account-btn-secondary"
            onClick={() => emailSectionRef.current?.scrollIntoView({ behavior: 'smooth' })}
          >
            {t('profile.changeEmailAction')}
          </button>
          <Link
            to="/profile/addresses"
            className="account-btn account-btn-secondary"
          >
            {t('profile.addressBook')}
          </Link>
        </div>
      </section>

      {/* SECTION: Address Book Summary Card (WF-09 / US6) */}
      <section className="account-card" aria-labelledby="address-book-heading">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '1rem' }}>
          <div>
            <h2 id="address-book-heading" className="account-card-title" style={{ marginBottom: '0.25rem' }}>
              {t('profile.addressBook')}
            </h2>
            <p style={{ color: '#4b5563', margin: 0, fontSize: '0.95rem' }}>
              {t('profile.addressBookDescription')}
            </p>
          </div>
          <Link to="/profile/addresses" className="account-btn account-btn-primary">
            {t('profile.manageAddresses')}
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
          {t('profile.editHeading')}
        </h2>

        {profileSuccess && (
          <div className="account-alert account-alert-success" role="status">
            <span>{profileSuccess}</span>
          </div>
        )}

        {profileError && (
          <div className="account-alert account-alert-error" role="alert">
            <span>{profileError.message || t('profile.updateError')}</span>
          </div>
        )}

        <form className="account-form" onSubmit={handleProfileSubmit} noValidate>
          <div className="account-field">
            <label className="account-label" htmlFor="profile-email">
              {t('profile.loginAddress')}
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
              {t('profile.loginAddressHint')}
            </span>
          </div>

          <div className="account-field">
            <label className="account-label account-label-required" htmlFor="profile-name">
              {t('profile.fullName')}
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
              {t('profile.nameHint')}
            </span>
          </div>

          <div className="account-field">
            <label className="account-label" htmlFor="profile-phone">
              {t('profile.phone')}
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
            <span className="account-field-hint">{t('profile.phoneHint')}</span>
          </div>

          <div style={{ display: 'flex', gap: '0.75rem', alignItems: 'center' }}>
            <button
              type="submit"
              className="account-btn account-btn-primary"
              disabled={!isVerified || saving}
              title={!isVerified ? t('profile.verifyBeforeSave') : undefined}
            >
              {saving ? t('profile.saving') : t('profile.saveChanges')}
            </button>
            <button
              type="button"
              className="account-btn account-btn-secondary"
              onClick={resetForm}
              disabled={saving}
            >
              {t('profile.restore')}
            </button>
            {!isVerified && (
              <span style={{ fontSize: '0.8125rem', color: '#b45309' }}>
                {t('profile.verifyBeforeEdit')}
              </span>
            )}
          </div>
        </form>
      </section>

      {/* SECTION 3: Change Password Form (screen.md: Change Password — form) */}
      {showPasswordSection && (
        <section
          ref={passwordSectionRef}
          className="account-card"
          aria-labelledby="password-heading"
        >
          <h2 id="password-heading" className="account-card-title">
            {t('profile.changePassword')}
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
              <label className="account-label account-label-required" htmlFor="current-password">
                {t('profile.currentPassword')}
              </label>
              <input
                id="current-password"
                type="password"
                className="account-input"
                value={currentPassword}
                onChange={e => setCurrentPassword(e.target.value)}
                autoComplete="current-password"
                disabled={passwordPending}
              />
            </div>

            <div className="account-field">
              <label className="account-label account-label-required" htmlFor="new-password">
                {t('profile.newPassword')}
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
              <span className="account-field-hint">{t('profile.min6')}</span>
            </div>

            <div className="account-field">
              <label className="account-label account-label-required" htmlFor="confirm-new-password">
                {t('profile.confirmNewPassword')}
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
                disabled={passwordPending || !currentPassword || !newPassword || !confirmPassword}
              >
                {passwordPending ? t('profile.processing') : t('profile.updatePassword')}
              </button>
            </div>
          </form>
        </section>
      )}

      {/* SECTION 4: Email Change Form */}
      <section
        ref={emailSectionRef}
        className="account-card"
        aria-labelledby="email-change-heading"
      >
        <h2 id="email-change-heading" className="account-card-title">
          {t('profile.changeEmailHeading')}
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

        {isOAuthOnly ? (
          <div className="account-alert account-alert-info" role="status">
            <span>
              {t('profile.oauthNotice')}
            </span>
          </div>
        ) : (
          <form className="account-form" onSubmit={handleEmailChange}>
            <div className="account-field">
              <label className="account-label" htmlFor="email-current-password">
                {t('profile.currentPassword')}
              </label>
              <input
                id="email-current-password"
                type="password"
                className="account-input"
                value={emailCurrentPassword}
                onChange={e => setEmailCurrentPassword(e.target.value)}
                autoComplete="current-password"
                disabled={emailChangePending}
              />
            </div>

            <div className="account-field">
              <label className="account-label account-label-required" htmlFor="new-email">
                {t('profile.newEmail')}
              </label>
              <input
                id="new-email"
                type="email"
                className="account-input"
                value={newEmail}
                onChange={e => setNewEmail(e.target.value)}
                placeholder={t('profile.newEmailPlaceholder')}
                disabled={emailChangePending}
              />
              <span className="account-field-hint">
                {t('profile.newEmailHint')}
              </span>
            </div>

            <div>
              <button
                type="submit"
                className="account-btn account-btn-secondary"
                disabled={emailChangePending || !newEmail.trim()}
              >
                {emailChangePending ? t('profile.sending') : t('profile.requestEmailChange')}
              </button>
            </div>
          </form>
        )}
      </section>
    </div>
  );
};

export default ProfilePage;
