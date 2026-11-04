import React, { useState, useEffect } from 'react';
import { Link, useNavigate } from 'react-router';
import { firebaseAuthClient } from '../auth/FirebaseAuthClient';
import { accountManagementApiClient } from '../api/AccountManagementApiClient';
import { ApiError } from '../../../shared/api/http';
import '../accountmanagement.css';

export interface AuthPageProps {
  mode?: 'signin' | 'register' | 'forgot';
}

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

function mapFirebaseError(error: any): string {
  switch (error?.code) {
    case 'auth/account-exists-with-different-credential':
      return 'Email này đã được đăng ký bằng Mật khẩu. Vui lòng đăng nhập bằng Email và Mật khẩu gốc.';
    case 'auth/invalid-credential':
    case 'auth/user-not-found':
    case 'auth/wrong-password':
      return 'Email hoặc mật khẩu không chính xác.';
    case 'auth/email-already-in-use':
      return 'Địa chỉ email này đã được sử dụng.';
    case 'auth/weak-password':
      return 'Mật khẩu quá yếu. Vui lòng sử dụng ít nhất 6 ký tự.';
    case 'auth/invalid-email':
      return 'Định dạng email không hợp lệ.';
    case 'auth/too-many-requests':
      return 'Quá nhiều yêu cầu không hợp lệ. Vui lòng thử lại sau giây lát.';
    case 'auth/network-request-failed':
      return 'Lỗi mạng khi kết nối đến dịch vụ xác thực. Vui lòng thử lại.';
    case 'CONFIGURATION_REQUIRED':
      return 'Cấu hình xác thực hệ thống chưa sẵn sàng.';
    default:
      return 'Không thể hoàn tất xác thực. Vui lòng thử lại sau.';
  }
}

const GoogleIcon: React.FC = () => (
  <svg width="18" height="18" viewBox="0 0 18 18" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
    <path fill="#4285F4" d="M17.64 9.2c0-.637-.057-1.251-.164-1.84H9v3.481h4.844c-.209 1.125-.843 2.078-1.796 2.717v2.258h2.908c1.702-1.567 2.684-3.874 2.684-6.616z" />
    <path fill="#34A853" d="M9 18c2.43 0 4.467-.806 5.956-2.184l-2.908-2.258c-.806.54-1.837.86-3.048.86-2.344 0-4.328-1.584-5.036-3.711H.957v2.332A8.997 8.997 0 009 18z" />
    <path fill="#FBBC05" d="M3.964 10.707c-.18-.54-.282-1.117-.282-1.707s.102-1.167.282-1.707V4.961H.957A8.996 8.996 0 000 9c0 1.452.348 2.827.957 4.039l3.007-2.332z" />
    <path fill="#EA4335" d="M9 3.58c1.321 0 2.508.454 3.44 1.345l2.582-2.58C13.463.891 11.426 0 9 0A8.997 8.997 0 00.957 4.961L3.964 7.293C4.672 5.166 6.656 3.58 9 3.58z" />
  </svg>
);

export const AuthPage: React.FC<AuthPageProps> = ({ mode = 'signin' }) => {
  const navigate = useNavigate();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [generalError, setGeneralError] = useState<string | null>(null);
  const [successNotice, setSuccessNotice] = useState<string | null>(null);
  const [pending, setPending] = useState(false);
  const [retryableError, setRetryableError] = useState(false);

  const isForgot = mode === 'forgot';
  const isRegister = mode === 'register';

  useEffect(() => {
    let isMounted = true;
    const processRedirectResult = async () => {
      if (!firebaseAuthClient.isConfigured()) return;
      try {
        const user = await firebaseAuthClient.getRedirectAuthResult();
        if (user && isMounted) {
          setPending(true);
          await accountManagementApiClient.bootstrapOwnAccount();
          navigate('/');
        }
      } catch (err: any) {
        if (isMounted) {
          if (err?.code === 'auth/account-exists-with-different-credential') {
            setGeneralError('Email này đã được đăng ký bằng Mật khẩu. Vui lòng đăng nhập bằng Email và Mật khẩu gốc.');
          } else if (err instanceof ApiError) {
            if (err.status === 401) {
              setGeneralError('Thông tin đăng nhập không hợp lệ hoặc phiên đã hết hạn.');
            } else if (err.status === 403) {
              setGeneralError('Tài khoản của bạn hiện đang bị tạm khóa hoặc không đủ điều kiện.');
            } else if (err.retryable) {
              setGeneralError('Dịch vụ tài khoản tạm thời gián đoạn. Vui lòng bấm thử lại.');
              setRetryableError(true);
            } else {
              setGeneralError(err.message || 'Lỗi khởi tạo tài khoản hệ thống.');
            }
          } else {
            setGeneralError(mapFirebaseError(err));
          }
        }
      } finally {
        if (isMounted) {
          setPending(false);
        }
      }
    };

    processRedirectResult();
    return () => {
      isMounted = false;
    };
  }, [navigate]);

  const getTitle = () => {
    if (isForgot) return 'Khôi phục mật khẩu';
    if (isRegister) return 'Đăng ký tài khoản';
    return 'Đăng nhập hệ thống';
  };

  const getDescription = () => {
    if (isForgot) return 'Nhập email của bạn để nhận liên kết đặt lại mật khẩu.';
    if (isRegister) return 'Tạo tài khoản thành viên mới tại Mland.';
    return 'Đăng nhập vào tài khoản cá nhân của bạn.';
  };

  const validate = () => {
    const errors: Record<string, string> = {};
    const trimmedEmail = email.trim();

    if (!trimmedEmail) {
      errors.email = 'Vui lòng nhập địa chỉ email.';
    } else if (!EMAIL_PATTERN.test(trimmedEmail)) {
      errors.email = 'Địa chỉ email không đúng định dạng.';
    }

    if (!isForgot) {
      if (!password) {
        errors.password = 'Vui lòng nhập mật khẩu.';
      } else if (password.length < 6) {
        errors.password = 'Mật khẩu phải chứa ít nhất 6 ký tự.';
      }
    }

    if (isRegister) {
      if (password !== confirmPassword) {
        errors.confirmPassword = 'Mật khẩu xác nhận không khớp.';
      }
    }

    setFieldErrors(errors);
    return Object.keys(errors).length === 0;
  };

  const handleGoogleSignIn = async () => {
    setGeneralError(null);
    setSuccessNotice(null);
    setRetryableError(false);
    setPending(true);
    try {
      await firebaseAuthClient.signInWithGoogleRedirect();
    } catch (err: any) {
      setPending(false);
      if (err?.code === 'auth/account-exists-with-different-credential') {
        setGeneralError('Email này đã được đăng ký bằng Mật khẩu. Vui lòng đăng nhập bằng Email và Mật khẩu gốc.');
      } else {
        setGeneralError(mapFirebaseError(err));
      }
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setGeneralError(null);
    setSuccessNotice(null);
    setRetryableError(false);

    if (!validate()) return;

    setPending(true);
    try {
      if (isForgot) {
        // Recovery must not disclose whether the account exists (FR-008)
        try {
          await firebaseAuthClient.sendPasswordReset(email);
        } catch (err: any) {
          if (err?.code === 'auth/invalid-email') {
            setFieldErrors({ email: 'Địa chỉ email không hợp lệ.' });
            setPending(false);
            return;
          }
          // Intentionally safe for unknown email
        }
        setSuccessNotice('Nếu email tồn tại trên hệ thống, thư hướng dẫn đặt lại mật khẩu đã được gửi.');
        setPending(false);
        return;
      }

      if (isRegister) {
        await firebaseAuthClient.register(email, password);
        try {
          await firebaseAuthClient.sendVerificationEmail();
        } catch {
          // Best effort for verification email dispatch
        }
      } else {
        await firebaseAuthClient.signIn(email, password);
      }

      // Bootstrap Mland Account record
      await accountManagementApiClient.bootstrapOwnAccount();
      navigate('/');
    } catch (err: any) {
      if (err instanceof ApiError) {
        if (err.status === 401) {
          setGeneralError('Thông tin đăng nhập không hợp lệ hoặc phiên đã hết hạn.');
        } else if (err.status === 403) {
          setGeneralError('Tài khoản của bạn hiện đang bị tạm khóa hoặc không đủ điều kiện.');
        } else if (err.retryable) {
          setGeneralError('Dịch vụ tài khoản tạm thời gián đoạn. Vui lòng bấm thử lại.');
          setRetryableError(true);
        } else {
          setGeneralError(err.message || 'Lỗi khởi tạo tài khoản hệ thống.');
        }
      } else {
        setGeneralError(mapFirebaseError(err));
      }
    } finally {
      setPending(false);
    }
  };

  const handleRetry = async () => {
    setPending(true);
    setGeneralError(null);
    setRetryableError(false);
    try {
      await accountManagementApiClient.bootstrapOwnAccount();
      navigate('/');
    } catch (err: any) {
      if (err instanceof ApiError && err.retryable) {
        setGeneralError('Dịch vụ tài khoản vẫn chưa phản hồi. Vui lòng thử lại.');
        setRetryableError(true);
      } else {
        setGeneralError(err?.message || 'Không thể kết nối đến máy chủ.');
      }
    } finally {
      setPending(false);
    }
  };

  return (
    <div className="account-page-container" style={{ maxWidth: '440px' }}>
      <div className="account-card">
        <header className="account-page-header" style={{ marginBottom: '1.25rem' }}>
          <h1 className="account-page-title" style={{ fontSize: '1.5rem' }}>{getTitle()}</h1>
          <p className="account-page-description">{getDescription()}</p>
        </header>

        {generalError && (
          <div className="account-alert account-alert-error" role="alert">
            <span>{generalError}</span>
          </div>
        )}

        {successNotice && (
          <div className="account-alert account-alert-success" role="status">
            <span>{successNotice}</span>
          </div>
        )}

        {retryableError && (
          <div style={{ marginBottom: '1rem' }}>
            <button
              type="button"
              className="account-btn account-btn-secondary account-btn-sm"
              onClick={handleRetry}
              disabled={pending}
            >
              Thử lại kết nối
            </button>
          </div>
        )}

        {!isForgot && firebaseAuthClient.isConfigured() && (
          <div style={{ marginBottom: '1.25rem' }}>
            <button
              type="button"
              className="account-btn account-btn-secondary account-btn-google"
              onClick={handleGoogleSignIn}
              disabled={pending}
            >
              <GoogleIcon />
              <span>Đăng nhập với Google</span>
            </button>
            <div className="account-divider">
              <span>hoặc</span>
            </div>
          </div>
        )}

        <form className="account-form" onSubmit={handleSubmit} noValidate>
          <div className="account-field">
            <label className="account-label account-label-required" htmlFor="auth-email">
              Địa chỉ email
            </label>
            <input
              id="auth-email"
              type="email"
              className="account-input"
              value={email}
              onChange={e => setEmail(e.target.value)}
              autoComplete="email"
              autoFocus={!isForgot}
              aria-invalid={Boolean(fieldErrors.email)}
              disabled={pending}
            />
            {fieldErrors.email && (
              <span className="account-field-error" role="alert">{fieldErrors.email}</span>
            )}
          </div>

          {!isForgot && (
            <div className="account-field">
              <label className="account-label account-label-required" htmlFor="auth-password">
                Mật khẩu
              </label>
              <input
                id="auth-password"
                type="password"
                className="account-input"
                value={password}
                onChange={e => setPassword(e.target.value)}
                autoComplete={isRegister ? 'new-password' : 'current-password'}
                aria-invalid={Boolean(fieldErrors.password)}
                disabled={pending}
              />
              {fieldErrors.password && (
                <span className="account-field-error" role="alert">{fieldErrors.password}</span>
              )}
            </div>
          )}

          {isRegister && (
            <div className="account-field">
              <label className="account-label account-label-required" htmlFor="auth-confirm-password">
                Xác nhận mật khẩu
              </label>
              <input
                id="auth-confirm-password"
                type="password"
                className="account-input"
                value={confirmPassword}
                onChange={e => setConfirmPassword(e.target.value)}
                autoComplete="new-password"
                aria-invalid={Boolean(fieldErrors.confirmPassword)}
                disabled={pending}
              />
              {fieldErrors.confirmPassword && (
                <span className="account-field-error" role="alert">{fieldErrors.confirmPassword}</span>
              )}
            </div>
          )}

          <button
            type="submit"
            className="account-btn account-btn-primary"
            style={{ width: '100%', marginTop: '0.5rem' }}
            disabled={pending}
          >
            {pending ? 'Đang xử lý…' : isForgot ? 'Gửi liên kết đặt lại' : isRegister ? 'Đăng ký' : 'Đăng nhập'}
          </button>
        </form>

        <footer style={{ marginTop: '1.5rem', fontSize: '0.875rem', display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
          {mode === 'signin' && (
            <Link to="/forgot-password" style={{ color: '#2563eb', textDecoration: 'none' }}>
              Quên mật khẩu?
            </Link>
          )}

          {!isForgot && (
            <div>
              {isRegister ? 'Đã có tài khoản?' : 'Chưa có tài khoản?'}{' '}
              <Link
                to={isRegister ? '/sign-in' : '/register'}
                style={{ color: '#2563eb', fontWeight: 600, textDecoration: 'none' }}
              >
                {isRegister ? 'Đăng nhập ngay' : 'Đăng ký ngay'}
              </Link>
            </div>
          )}

          {isForgot && (
            <div>
              <Link to="/sign-in" style={{ color: '#2563eb', textDecoration: 'none' }}>
                Quay lại đăng nhập
              </Link>
            </div>
          )}
        </footer>
      </div>
    </div>
  );
};

export default AuthPage;
