import React, { useState } from 'react';
import { Link, useNavigate } from 'react-router';
import { useAuth } from '../../shared/auth/AuthProvider';
import { signInWithPassword, registerWithPassword, sendPasswordReset, sendVerificationEmail } from '../../shared/auth/firebaseSession';
import { useHttp } from '../../shared/api/useHttp';
import { useCommand } from '../../shared/ui/components';
import { useT } from '../../shared/i18n/I18nProvider';
import { authMessages } from './authMessages';
import './auth.css';

const emailPattern = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

function firebaseErrorKey(error) {
  switch (error?.code) {
    case 'auth/invalid-credential':
    case 'auth/user-not-found':
    case 'auth/wrong-password': return 'error.invalidCredential';
    case 'auth/email-already-in-use': return 'error.emailInUse';
    case 'auth/weak-password': return 'error.weakPassword';
    case 'auth/invalid-email': return 'validation.email';
    case 'auth/operation-not-allowed': return 'error.providerDisabled';
    case 'auth/invalid-api-key':
    case 'auth/app-not-authorized': return 'error.configuration';
    case 'auth/too-many-requests': return 'error.tooManyRequests';
    case 'auth/network-request-failed': return 'error.network';
    case 'CONFIGURATION_REQUIRED': return 'error.configuration';
    default: return 'error.generic';
  }
}

function bootstrapErrorKey(error) {
  if (error?.status === 401) return 'error.bootstrapAuth';
  if (error?.status === 403) return 'error.bootstrapForbidden';
  if (error?.status === 409) return 'error.bootstrapConflict';
  return 'error.bootstrap';
}

export default function AuthPage({ mode = 'signin' }) {
  const t = useT(authMessages);
  const navigate = useNavigate();
  const { status } = useAuth();
  const http = useHttp();
  const bootstrap = useCommand();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [fieldErrors, setFieldErrors] = useState({});
  const [errorKey, setErrorKey] = useState(null);
  const [noticeKey, setNoticeKey] = useState(null);
  const [pending, setPending] = useState(false);

  const isForgot = mode === 'forgot';
  const isRegister = mode === 'register';
  const title = t(`${mode}.title`);
  const description = t(`${mode}.description`);

  const initializeAccount = async () => {
    const result = await bootstrap.run(key => http('/api/v1/accounts/me', {
      method: 'PUT',
      idempotencyKey: key,
    }));
    if (result.ok) {
      navigate('/');
      return true;
    }
    setErrorKey(bootstrapErrorKey(result.error));
    return false;
  };

  const retryBootstrap = async () => {
    const result = await bootstrap.retry();
    if (result?.ok) navigate('/');
  };

  const submit = async event => {
    event.preventDefault();
    setErrorKey(null);
    setNoticeKey(null);
    const nextErrors = {};
    if (!emailPattern.test(email.trim())) nextErrors.email = t('validation.email');
    if (!isForgot && password.length < 6) nextErrors.password = t('validation.password');
    if (isRegister && password !== confirmPassword) nextErrors.confirmPassword = t('validation.confirmPassword');
    setFieldErrors(nextErrors);
    if (Object.keys(nextErrors).length) return;

    setPending(true);
    try {
      if (isForgot) {
        try {
          await sendPasswordReset(email.trim());
        } catch (error) {
          if (error?.code === 'auth/invalid-email') {
            setFieldErrors({ email: t('validation.email') });
            return;
          }
          // Recovery must not reveal whether an account exists.
        }
        setNoticeKey('reset.sent');
        return;
      }

      if (isRegister) {
        await registerWithPassword(email.trim(), password);
        try {
          await sendVerificationEmail();
          setNoticeKey('verification.reminder');
        } catch {
          setNoticeKey('verification.failed');
        }
      } else {
        await signInWithPassword(email.trim(), password);
      }
      await initializeAccount();
    } catch (error) {
      setErrorKey(firebaseErrorKey(error));
    } finally {
      setPending(false);
    }
  };

  const buttonPending = pending || bootstrap.pending;
  return (
    <main className="auth-screen">
      <section className={`auth-card auth-card-${mode}`} aria-labelledby="auth-title">
        <div className="auth-heading">
          <h1 id="auth-title">{title}</h1>
          <p>{description}</p>
        </div>
        {status === 'unconfigured' && <div className="auth-notice auth-error" role="alert">{t('error.configuration')}</div>}
        {errorKey && <div className="auth-notice auth-error" role="alert">{t(errorKey)}</div>}
        {noticeKey && <div className="auth-notice auth-success" role="status">{t(noticeKey)}</div>}
        {bootstrap.error && bootstrap.error.retryable && (
          <button type="button" className="auth-retry" onClick={retryBootstrap} disabled={buttonPending}>Thử lại</button>
        )}
        <form onSubmit={submit} noValidate>
          <label className="auth-field">
            <span>{t('field.email')}</span>
            <input type="email" value={email} onChange={event => setEmail(event.target.value)} autoComplete="email" autoFocus={!isForgot} aria-invalid={Boolean(fieldErrors.email)} />
            {fieldErrors.email && <small>{fieldErrors.email}</small>}
          </label>
          {!isForgot && <label className="auth-field">
            <span>{t('field.password')}</span>
            <input type="password" value={password} onChange={event => setPassword(event.target.value)} autoComplete={isRegister ? 'new-password' : 'current-password'} aria-invalid={Boolean(fieldErrors.password)} />
            {fieldErrors.password && <small>{fieldErrors.password}</small>}
          </label>}
          {isRegister && <label className="auth-field">
            <span>{t('field.confirmPassword')}</span>
            <input type="password" value={confirmPassword} onChange={event => setConfirmPassword(event.target.value)} autoComplete="new-password" aria-invalid={Boolean(fieldErrors.confirmPassword)} />
            {fieldErrors.confirmPassword && <small>{fieldErrors.confirmPassword}</small>}
          </label>}
          <button className="auth-submit" type="submit" disabled={buttonPending}>
            {buttonPending ? 'Đang xử lý…' : t(`${mode}.submit`)}
          </button>
        </form>
        {mode === 'signin' && <Link className="auth-link auth-forgot" to="/forgot-password">{t('signin.forgot')}</Link>}
        {isRegister && <p className="auth-terms">{t('register.terms')}</p>}
        {!isForgot && <p className="auth-switch">{t(`${mode}.switch`)} <Link to={isRegister ? '/sign-in' : '/register'}>{t(`${mode}.switchAction`)}</Link></p>}
        {isForgot && <Link className="auth-link auth-back" to="/sign-in">{t('forgot.back')}</Link>}
      </section>
    </main>
  );
}
