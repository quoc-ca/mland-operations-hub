import React, { useEffect, useState } from 'react';
import { useSearchParams, useNavigate, Link } from 'react-router';
import { accountManagementApiClient, InvitationExchangeResultResponse, ProfileResponse } from '../api/AccountManagementApiClient';
import { firebaseAuthClient } from '../auth/FirebaseAuthClient';
import { useT } from '../../../shared/i18n/I18nProvider';
import { accountMessages } from '../messages';
import '../accountmanagement.css';

const GOOGLE_SETUP_PENDING = 'mland:invitation-google-setup-pending';

export const InternalAccountSetupPage: React.FC = () => {
  const t = useT(accountMessages);
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();

  const secret = searchParams.get('secret') || '';
  const [resumingGoogle] = useState(() => sessionStorage.getItem(GOOGLE_SETUP_PENDING) === '1');

  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [exchangeData, setExchangeData] = useState<InvitationExchangeResultResponse | null>(null);
  const [invitedProfile, setInvitedProfile] = useState<ProfileResponse | null>(null);

  // Setup form state
  const [setupMode, setSetupMode] = useState<'PASSWORD' | 'GOOGLE'>('PASSWORD');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);

  useEffect(() => {
    if (!secret) {
      setLoading(false);
      setError('Thiếu mã bí mật thư mời (secret). Vui lòng kiểm tra lại liên kết trong email được gửi.');
      return;
    }

    let live = true;
    setLoading(true);
    setError(null);

    const prepare = async () => {
      if (resumingGoogle) {
        const linkedUser = await firebaseAuthClient.getRedirectAuthResult()
          || firebaseAuthClient.getCurrentUser()
          || await firebaseAuthClient.waitForAuthState();
        if (!linkedUser) throw new Error('Không thể hoàn tất liên kết Google. Vui lòng mở lại thư mời và thử lại.');
      }

      const result = await accountManagementApiClient.exchangeInternalInvitation({ secret });
      if (!result.firebaseCustomToken) throw new Error('Không thể xác thực liên kết thư mời.');
      if (!resumingGoogle) await firebaseAuthClient.signInWithCustomToken(result.firebaseCustomToken);
      const profile = await accountManagementApiClient.getOwnProfile();
      if (!live) return;
      if (resumingGoogle) {
        const completed = await accountManagementApiClient.completeInternalInvitation({
          setupSession: result.setupSession,
          method: 'GOOGLE',
        });
        sessionStorage.removeItem(GOOGLE_SETUP_PENDING);
        navigate(completed.homeRoute, { replace: true });
      } else {
        setExchangeData(result);
        setInvitedProfile(profile);
      }
    };

    prepare().catch((err: any) => {
      if (resumingGoogle) sessionStorage.removeItem(GOOGLE_SETUP_PENDING);
      if (live) setError(err?.message || 'Không thể xác thực liên kết thư mời. Vui lòng liên hệ Quản trị viên để được hỗ trợ.');
    }).finally(() => {
      if (live) setLoading(false);
    });
    return () => { live = false; };
  }, [secret, navigate, resumingGoogle]);

  const handlePasswordSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!exchangeData) return;

    if (!password || password.length < 6) {
      setActionError('Mật khẩu phải chứa ít nhất 6 ký tự.');
      return;
    }

    if (password !== confirmPassword) {
      setActionError('Mật khẩu xác nhận không khớp.');
      return;
    }

    setSubmitting(true);
    setActionError(null);

    try {
      // 1. Update password on current Firebase user
      await firebaseAuthClient.updatePassword(password);
      if (!invitedProfile?.email) throw new Error('Không xác định được email thư mời. Vui lòng mở lại liên kết.');
      await firebaseAuthClient.signIn(invitedProfile.email, password);

      // 2. Complete setup on backend
      const res = await accountManagementApiClient.completeInternalInvitation({
        setupSession: exchangeData.setupSession,
        method: 'PASSWORD',
      });

      // 3. Navigate to role home
      const destination = res.homeRoute;
      navigate(destination, { replace: true });
    } catch (err: any) {
      setActionError(err?.message || 'Không thể hoàn tất thiết lập tài khoản.');
    } finally {
      setSubmitting(false);
    }
  };

  const handleGoogleLink = async () => {
    if (!exchangeData) return;

    setSubmitting(true);
    setActionError(null);

    try {
      sessionStorage.setItem(GOOGLE_SETUP_PENDING, '1');
      await firebaseAuthClient.linkGoogleWithRedirect();
    } catch (err: any) {
      sessionStorage.removeItem(GOOGLE_SETUP_PENDING);
      setActionError(err?.message || 'Không thể liên kết với tài khoản Google.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="account-container" style={{ maxWidth: '480px', margin: '3rem auto', padding: '0 1rem' }}>
      <div className="account-card" style={{ padding: '2rem' }}>
        <header style={{ textAlign: 'center', marginBottom: '1.5rem' }}>
          <h1 style={{ fontSize: '1.5rem', fontWeight: 700, margin: '0 0 0.5rem 0', color: '#0f172a' }}>
            Thiết lập tài khoản nội bộ
          </h1>
          <p style={{ margin: 0, fontSize: '0.875rem', color: '#64748b' }}>
            Chào mừng bạn gia nhập đội ngũ Mland Jewelry
          </p>
        </header>

        {loading && (
          <div style={{ textAlign: 'center', padding: '2rem 0', color: '#64748b' }}>
            Đang xác thực liên kết thư mời…
          </div>
        )}

        {error && (
          <div>
            <div
              className="account-alert account-alert-error"
              role="alert"
              style={{
                backgroundColor: '#fef2f2',
                border: '1px solid #fecaca',
                color: '#b91c1c',
                padding: '1rem',
                borderRadius: '8px',
                marginBottom: '1.5rem',
                fontSize: '0.9rem',
              }}
            >
              <strong>Liên kết không khả dụng:</strong>
              <p style={{ margin: '0.5rem 0 0 0' }}>{error}</p>
            </div>
            <div style={{ textAlign: 'center' }}>
              <Link to="/login" className="account-btn account-btn-primary" style={{ display: 'inline-block' }}>
                Đến trang đăng nhập
              </Link>
            </div>
          </div>
        )}

        {exchangeData && !loading && (
          <div>
            <div
              className="account-alert account-alert-info"
              style={{
                backgroundColor: '#f0f9ff',
                border: '1px solid #bae6fd',
                color: '#0369a1',
                padding: '0.75rem 1rem',
                borderRadius: '6px',
                marginBottom: '1.25rem',
                fontSize: '0.85rem',
              }}
            >
              <div>
                <strong>Email định danh:</strong> {invitedProfile?.email}
              </div>
              <div>
                <strong>Vai trò được cấp:</strong> {invitedProfile?.role}
              </div>
            </div>

            {actionError && (
              <div
                className="account-alert account-alert-error"
                role="alert"
                style={{
                  backgroundColor: '#fef2f2',
                  border: '1px solid #fecaca',
                  color: '#b91c1c',
                  padding: '0.75rem 1rem',
                  borderRadius: '6px',
                  marginBottom: '1rem',
                  fontSize: '0.85rem',
                }}
              >
                {actionError}
              </div>
            )}

            {/* Method Tabs */}
            <div style={{ display: 'flex', borderBottom: '1px solid #e2e8f0', marginBottom: '1.25rem' }}>
              <button
                type="button"
                onClick={() => setSetupMode('PASSWORD')}
                style={{
                  flex: 1,
                  padding: '0.5rem',
                  border: 'none',
                  background: 'none',
                  fontWeight: 600,
                  fontSize: '0.9rem',
                  cursor: 'pointer',
                  borderBottom: setupMode === 'PASSWORD' ? '2px solid #2563eb' : '2px solid transparent',
                  color: setupMode === 'PASSWORD' ? '#2563eb' : '#64748b',
                }}
              >
                Đặt mật khẩu
              </button>
              <button
                type="button"
                onClick={() => setSetupMode('GOOGLE')}
                style={{
                  flex: 1,
                  padding: '0.5rem',
                  border: 'none',
                  background: 'none',
                  fontWeight: 600,
                  fontSize: '0.9rem',
                  cursor: 'pointer',
                  borderBottom: setupMode === 'GOOGLE' ? '2px solid #2563eb' : '2px solid transparent',
                  color: setupMode === 'GOOGLE' ? '#2563eb' : '#64748b',
                }}
              >
                Liên kết Google
              </button>
            </div>

            {setupMode === 'PASSWORD' ? (
              <form onSubmit={handlePasswordSubmit} className="account-form">
                <div className="account-field" style={{ marginBottom: '1rem' }}>
                  <label className="account-label account-label-required" htmlFor="setup-new-password">
                    Mật khẩu mới (tối thiểu 6 ký tự)
                  </label>
                  <input
                    id="setup-new-password"
                    type="password"
                    className="account-input"
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                    placeholder="Nhập mật khẩu…"
                    disabled={submitting}
                    required
                  />
                </div>

                <div className="account-field" style={{ marginBottom: '1.25rem' }}>
                  <label className="account-label account-label-required" htmlFor="setup-confirm-password">
                    Xác nhận mật khẩu
                  </label>
                  <input
                    id="setup-confirm-password"
                    type="password"
                    className="account-input"
                    value={confirmPassword}
                    onChange={(e) => setConfirmPassword(e.target.value)}
                    placeholder="Nhập lại mật khẩu…"
                    disabled={submitting}
                    required
                  />
                </div>

                <button
                  type="submit"
                  className="account-btn account-btn-primary"
                  style={{ width: '100%', padding: '0.75rem' }}
                  disabled={submitting}
                  data-testid="submit-password-setup-btn"
                >
                  {submitting ? 'Đang hoàn tất…' : 'Hoàn tất thiết lập & Đăng nhập'}
                </button>
              </form>
            ) : (
              <div>
                <p style={{ fontSize: '0.875rem', color: '#475569', marginBottom: '1rem' }}>
                  Bạn có thể liên kết tài khoản Google có địa chỉ email <strong>{invitedProfile?.email}</strong> để đăng nhập nhanh chóng.
                </p>

                <button
                  type="button"
                  className="account-btn account-btn-secondary"
                  onClick={handleGoogleLink}
                  style={{ width: '100%', padding: '0.75rem', display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '0.5rem' }}
                  disabled={submitting}
                  data-testid="submit-google-setup-btn"
                >
                  <svg width="18" height="18" viewBox="0 0 18 18">
                    <path
                      fill="#4285F4"
                      d="M17.64 9.2c0-.637-.057-1.251-.164-1.84H9v3.481h4.844c-.209 1.125-.843 2.078-1.796 2.717v2.258h2.908c1.702-1.567 2.684-3.874 2.684-6.616z"
                    />
                    <path
                      fill="#34A853"
                      d="M9 18c2.43 0 4.467-.806 5.956-2.184l-2.908-2.258c-.806.54-1.837.86-3.048.86-2.344 0-4.328-1.584-5.036-3.711H.957v2.332A8.997 8.997 0 009 18z"
                    />
                    <path
                      fill="#FBBC05"
                      d="M3.964 10.707c-.18-.54-.282-1.117-.282-1.707s.102-1.167.282-1.707V4.961H.957A8.996 8.996 0 000 9c0 1.452.348 2.827.957 4.039l3.007-2.332z"
                    />
                    <path
                      fill="#EA4335"
                      d="M9 3.58c1.321 0 2.508.454 3.44 1.345l2.582-2.58C13.463.891 11.426 0 9 0A8.997 8.997 0 00.957 4.961L3.964 7.293C4.672 5.166 6.656 3.58 9 3.58z"
                    />
                  </svg>
                  <span>{submitting ? 'Đang liên kết…' : 'Đăng nhập & liên kết Google'}</span>
                </button>
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  );
};

export default InternalAccountSetupPage;
