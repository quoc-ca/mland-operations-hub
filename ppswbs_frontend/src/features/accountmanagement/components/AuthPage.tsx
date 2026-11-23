import React, { useState, useEffect } from 'react';
import { Link, useNavigate, useLocation, useSearchParams } from 'react-router';
import type { User } from 'firebase/auth';
import { afterSignInPath } from '../../../shared/auth/roleHome';
import { firebaseAuthClient } from '../auth/FirebaseAuthClient';
import { accountManagementApiClient } from '../api/AccountManagementApiClient';
import { ApiError } from '../../../shared/api/http';
import { useT } from '../../../shared/i18n/I18nProvider';
import { accountMessages } from '../messages';
import '../accountmanagement.css';

export interface AuthPageProps {
  mode?: 'signin' | 'register' | 'forgot';
}

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

const isGoogleRedirectPending = (): boolean => {
  if (typeof window === 'undefined') return false;
  try {
    return (
      sessionStorage.getItem('mland_google_redirect_pending') === 'true' ||
      localStorage.getItem('mland_google_redirect_pending') === 'true'
    );
  } catch {
    return false;
  }
};

const setGoogleRedirectPending = (): void => {
  if (typeof window === 'undefined') return;
  try {
    sessionStorage.setItem('mland_google_redirect_pending', 'true');
  } catch {}
  try {
    localStorage.setItem('mland_google_redirect_pending', 'true');
  } catch {}
};

const clearGoogleRedirectPending = (): void => {
  if (typeof window === 'undefined') return;
  try {
    sessionStorage.removeItem('mland_google_redirect_pending');
  } catch {}
  try {
    localStorage.removeItem('mland_google_redirect_pending');
  } catch {}
};

type Translate = (key: string) => string;

function mapFirebaseError(error: any, t: Translate): string {
  const code = error?.code || '';
  const msg = error?.message || '';

  if (
    code === 'auth/account-exists-with-different-credential' ||
    code === 'auth/credential-already-in-use' ||
    code === 'auth/email-already-in-use' ||
    code === 'auth/provider-already-linked' ||
    msg.includes('account-exists-with-different-credential') ||
    msg.includes('different-credential') ||
    msg.includes('provider-already-linked') ||
    msg.includes('EMAIL_EXISTS')
  ) {
    return t('auth.err.collision');
  }

  switch (code) {
    case 'auth/popup-closed-by-user':
    case 'auth/cancelled-popup-request':
      return t('auth.err.popupClosed');
    case 'auth/popup-blocked':
      return t('auth.err.popupBlocked');
    case 'auth/unauthorized-domain':
      return t('auth.err.unauthorizedDomain');
    case 'auth/invalid-credential':
    case 'auth/user-not-found':
    case 'auth/wrong-password':
      return t('auth.err.badCredential');
    case 'auth/weak-password':
      return t('auth.err.weakPassword');
    case 'auth/invalid-email':
      return t('auth.err.invalidEmailFormat');
    case 'auth/too-many-requests':
      return t('auth.err.tooMany');
    case 'auth/network-request-failed':
      return t('auth.err.network');
    case 'CONFIGURATION_REQUIRED':
      return t('auth.err.config');
    default:
      return t('auth.err.default');
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
  const t = useT(accountMessages);
  const navigate = useNavigate();
  const location = useLocation();
  const [searchParams] = useSearchParams();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [generalError, setGeneralError] = useState<string | null>(
    () => location.state?.sessionNotice || null
  );
  const [successNotice, setSuccessNotice] = useState<string | null>(null);
  const [pending, setPending] = useState(false);
  const [retryableError, setRetryableError] = useState(false);

  const isForgot = mode === 'forgot';
  const isRegister = mode === 'register';

  useEffect(() => {
    if (location.state?.sessionNotice) {
      setGeneralError(location.state.sessionNotice);
    }
  }, [location.state?.sessionNotice]);

  useEffect(() => {
    let isMounted = true;
    const processRedirectResult = async () => {
      if (!firebaseAuthClient.isConfigured()) return;
      const isGoogleRedirect = isGoogleRedirectPending();
      if (isGoogleRedirect) {
        setPending(true);
      }

      try {
        let user: User | null = null;
        try {
          user = await firebaseAuthClient.getRedirectAuthResult();
        } catch (redirectErr) {
          throw redirectErr;
        }

        // FR-047: When getRedirectResult() resolves null following a Google redirect (e.g. due to
        // Chrome 115+ storage partitioning on localhost/COOP), wait for Firebase auth state to inspect
        // the current Firebase user instead of immediately giving up and abandoning sign-in.
        if (!user && isGoogleRedirect) {
          user = firebaseAuthClient.getCurrentUser() || (await firebaseAuthClient.waitForAuthState(5000));
        }

        if (!isMounted) return;

        if (user) {
          // Collision defense (FR-001, FR-002, FR-022):
          // If the user already has a password provider, they were originally registered with password!
          const hasPassword =
            Array.isArray(user.providerData) &&
            user.providerData.some(p => p.providerId === 'password');

          if (hasPassword && isGoogleRedirect) {
            clearGoogleRedirectPending();
            if (typeof window !== 'undefined') {
              window.dispatchEvent(new CustomEvent('mland:auth-bootstrap-complete'));
            }
            await firebaseAuthClient.signOut();
            setGeneralError(t('auth.err.collision'));
            return;
          }

          setPending(true);
          const account = await accountManagementApiClient.bootstrapOwnAccount();
          clearGoogleRedirectPending();
          if (typeof window !== 'undefined') {
            window.dispatchEvent(new CustomEvent('mland:auth-bootstrap-complete', { detail: { userId: user.uid } }));
          }
          const returnParam =
            searchParams.get('returnTo') ||
            searchParams.get('redirect') ||
            new URLSearchParams(window.location.search).get('returnTo') ||
            new URLSearchParams(window.location.search).get('redirect');
          const target = afterSignInPath(account?.role, returnParam);
          navigate(target, { replace: true });
        } else if (isGoogleRedirect) {
          // Timed out waiting for auth state after redirect, clean up flag
          clearGoogleRedirectPending();
          if (typeof window !== 'undefined') {
            window.dispatchEvent(new CustomEvent('mland:auth-bootstrap-complete'));
          }
        }
      } catch (err: any) {
        clearGoogleRedirectPending();
        if (typeof window !== 'undefined') {
          window.dispatchEvent(new CustomEvent('mland:auth-bootstrap-complete'));
        }
        if (!isMounted) return;

        const isCollision =
          err?.code === 'auth/account-exists-with-different-credential' ||
          err?.code === 'auth/credential-already-in-use' ||
          err?.code === 'auth/email-already-in-use' ||
          err?.code === 'auth/provider-already-linked' ||
          err?.message?.includes('account-exists-with-different-credential') ||
          err?.message?.includes('different-credential') ||
          err?.message?.includes('provider-already-linked') ||
          err?.message?.includes('EMAIL_EXISTS');

        if (isCollision) {
          setGeneralError(t('auth.err.collision'));
        } else if (err instanceof ApiError) {
          if (err.status === 401) {
            setGeneralError(t('auth.err.401'));
          } else if (err.status === 403) {
            setGeneralError(t('auth.err.403'));
          } else if (err.retryable) {
            setGeneralError(t('auth.err.retryable'));
            setRetryableError(true);
          } else {
            setGeneralError(err.message || t('auth.err.bootstrap'));
          }
        } else {
          setGeneralError(mapFirebaseError(err, t));
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

  const getHeading = () => {
    if (isForgot) return t('auth.headingForgot');
    if (isRegister) return t('auth.headingRegister');
    return t('auth.headingSignIn');
  };

  const getFigmaTitle = () => {
    if (isForgot) return t('auth.titleForgot');
    if (isRegister) return t('auth.titleRegister');
    return t('auth.titleSignIn');
  };

  const getFigmaDescription = () => {
    if (isForgot) return t('auth.descForgot');
    if (isRegister) return t('auth.descRegister');
    return t('auth.descSignIn');
  };

  const validate = () => {
    const errors: Record<string, string> = {};
    const trimmedEmail = email.trim();

    if (!trimmedEmail) {
      errors.email = t('auth.val.emailRequired');
    } else if (!EMAIL_PATTERN.test(trimmedEmail)) {
      errors.email = t('auth.val.emailFormat');
    }

    if (!isForgot) {
      if (!password) {
        errors.password = t('auth.val.passwordRequired');
      } else if (password.length < 6) {
        errors.password = t('auth.val.passwordLength');
      }
    }

    if (isRegister) {
      if (password !== confirmPassword) {
        errors.confirmPassword = t('auth.val.confirmMismatch');
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
      if (typeof window !== 'undefined' && (window.location.hostname === '127.0.0.1' || window.location.hostname === '[::1]')) {
        const newUrl = window.location.href.replace('//' + window.location.hostname, '//localhost');
        window.location.href = newUrl;
        return;
      }

      if (firebaseAuthClient.getCurrentUser()) {
        try {
          await firebaseAuthClient.signOut();
        } catch {
          // ignore
        }
      }

      // In browser runtime on localhost, try signInWithGooglePopup first to avoid Chrome 115+ storage partitioning failure.
      // If popup is blocked or closed by user, seamlessly fallback to signInWithGoogleRedirect.
      const isTestEnv = typeof process !== 'undefined' && process.env?.NODE_ENV === 'test';
      if (!isTestEnv && typeof window !== 'undefined') {
        try {
          const user = await firebaseAuthClient.signInWithGooglePopup();
          if (user) {
            const hasPassword =
              Array.isArray(user.providerData) &&
              user.providerData.some(p => p.providerId === 'password');

            if (hasPassword) {
              await firebaseAuthClient.signOut();
              setGeneralError(t('auth.err.collision'));
              setPending(false);
              return;
            }

            const account = await accountManagementApiClient.bootstrapOwnAccount();
            const returnParam =
              searchParams.get('returnTo') ||
              searchParams.get('redirect') ||
              new URLSearchParams(window.location.search).get('returnTo') ||
              new URLSearchParams(window.location.search).get('redirect');
            const target = afterSignInPath(account?.role, returnParam);
            navigate(target, { replace: true });
            return;
          }
        } catch (popupErr: any) {
          const code = popupErr?.code || '';
          if (
            code === 'auth/popup-blocked' ||
            code === 'auth/cancelled-popup-request' ||
            code === 'auth/popup-closed-by-user'
          ) {
            console.warn('Popup closed or blocked, falling back to redirect:', code);
          } else {
            throw popupErr;
          }
        }
      }

      setGoogleRedirectPending();
      await firebaseAuthClient.signInWithGoogleRedirect();
    } catch (err: any) {
      console.error('[GoogleSignIn] error caught:', err);
      clearGoogleRedirectPending();
      setPending(false);
      const isCollision =
        err?.code === 'auth/account-exists-with-different-credential' ||
        err?.code === 'auth/credential-already-in-use' ||
        err?.code === 'auth/email-already-in-use' ||
        err?.code === 'auth/provider-already-linked' ||
        err?.message?.includes('account-exists-with-different-credential') ||
        err?.message?.includes('different-credential') ||
        err?.message?.includes('provider-already-linked') ||
        err?.message?.includes('EMAIL_EXISTS');

      if (isCollision) {
        setGeneralError(t('auth.err.collision'));
      } else if (err instanceof ApiError) {
        if (err.status === 401) {
          setGeneralError(t('auth.err.401'));
        } else if (err.status === 403) {
          setGeneralError(t('auth.err.403'));
        } else if (err.retryable) {
          setGeneralError(t('auth.err.retryable'));
          setRetryableError(true);
        } else {
          setGeneralError(err.message || t('auth.err.bootstrap'));
        }
      } else {
        setGeneralError(mapFirebaseError(err, t));
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
            setFieldErrors({ email: t('auth.val.emailInvalid') });
            setPending(false);
            return;
          }
        }
        setSuccessNotice(t('auth.resetSent'));
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
      const account = await accountManagementApiClient.bootstrapOwnAccount();
      const returnParam =
        searchParams.get('returnTo') ||
        searchParams.get('redirect') ||
        new URLSearchParams(window.location.search).get('returnTo') ||
        new URLSearchParams(window.location.search).get('redirect');
      navigate(afterSignInPath(account?.role, returnParam), { replace: true });
    } catch (err: any) {
      if (err instanceof ApiError) {
        if (err.status === 401) {
          setGeneralError(t('auth.err.401'));
        } else if (err.status === 403) {
          setGeneralError(t('auth.err.403'));
        } else if (err.retryable) {
          setGeneralError(t('auth.err.retryable'));
          setRetryableError(true);
        } else {
          setGeneralError(err.message || t('auth.err.bootstrap'));
        }
      } else {
        setGeneralError(mapFirebaseError(err, t));
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
      const account = await accountManagementApiClient.bootstrapOwnAccount();
      navigate(afterSignInPath(account?.role, searchParams.get('returnTo')), { replace: true });
    } catch (err: any) {
      if (err instanceof ApiError && err.retryable) {
        setGeneralError(t('auth.err.stillDown'));
        setRetryableError(true);
      } else {
        setGeneralError(err?.message || t('auth.err.server'));
      }
    } finally {
      setPending(false);
    }
  };

  return (
    <div className="account-page-container" style={{ maxWidth: '460px' }}>
      {/* Back button matching Figma 342:13 / 342:32 / 383:21361 */}
      <div>
        <Link to={isForgot ? '/sign-in' : '/'} className="account-back-btn">
          {t('auth.back')}
        </Link>
      </div>

      <div className="account-card" style={{ marginTop: '0.5rem' }}>
        <header className="account-page-header" style={{ marginBottom: '1.25rem' }}>
          <h1 className="account-page-title" style={{ fontSize: '1.35rem' }}>{getHeading()}</h1>
          <h2 style={{ fontSize: '1.15rem', fontWeight: 600, margin: '0.25rem 0', color: '#1a1a1a' }}>
            {getFigmaTitle()}
          </h2>
          <p className="account-page-description">{getFigmaDescription()}</p>
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
              {t('auth.retryConnection')}
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
              <span>{t('auth.google')}</span>
            </button>
            <div className="account-divider">
              <span>{t('auth.or')}</span>
            </div>
          </div>
        )}

        <form className="account-form" onSubmit={handleSubmit} noValidate>
          <div className="account-field">
            <label className="account-label account-label-required" htmlFor="auth-email">
              {t('auth.email')}
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
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <label className="account-label account-label-required" htmlFor="auth-password">
                  {t('auth.password')}
                </label>
                {mode === 'signin' && (
                  <Link
                    to="/forgot-password"
                    style={{ fontSize: '0.8125rem', color: '#2462eb', textDecoration: 'none' }}
                  >
                    {t('auth.forgotLink')}
                  </Link>
                )}
              </div>
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
                {t('auth.confirmPassword')}
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

          {isRegister && (
            <p style={{ fontSize: '0.8125rem', color: '#6b717f', margin: '0.25rem 0' }}>
              {t('auth.terms')}
            </p>
          )}

          <button
            type="submit"
            className="account-btn account-btn-primary"
            style={{ width: '100%', marginTop: '0.5rem' }}
            disabled={pending}
          >
            {pending ? t('auth.submitting') : isForgot ? t('auth.submitForgot') : isRegister ? t('auth.submitRegister') : t('auth.submitSignIn')}
          </button>
        </form>

        <footer style={{ marginTop: '1.5rem', fontSize: '0.875rem', display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
          {!isForgot && (
            <div>
              {isRegister ? t('auth.haveAccount') : t('auth.noAccount')}{' '}
              <Link
                to={isRegister ? '/sign-in' : '/register'}
                style={{ color: '#2462eb', fontWeight: 600, textDecoration: 'none' }}
              >
                {isRegister ? t('auth.signInNow') : t('auth.registerNow')}
              </Link>
            </div>
          )}

          {isForgot && (
            <div>
              <Link to="/sign-in" className="account-btn account-btn-secondary account-btn-sm" style={{ width: '100%' }}>
                {t('auth.toSignIn')}
              </Link>
            </div>
          )}
        </footer>
      </div>
    </div>
  );
};

export default AuthPage;
