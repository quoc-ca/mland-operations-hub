import React, { createContext, useContext, useEffect, useMemo, useRef, useState } from 'react';
import { currentIdToken, endSession, watchSession } from './firebaseSession';

// Session state for the whole app: 'loading' | 'unconfigured' | 'signed-out' | 'signed-in'.
// A route guard or hidden button never replaces the backend's authorization.
export const AuthContext = createContext(null);

const isGoogleRedirectPending = () => {
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

const firebaseSource = { watch: watchSession, getIdToken: currentIdToken, signOut: endSession };

export function AuthProvider({ children, source = firebaseSource }) {
  const [status, setStatus] = useState('loading');
  const [userId, setUserId] = useState(null);
  const sessionLostReportedRef = useRef(false);
  const isUserSigningOutRef = useRef(false);
  const terminalOutcomeRef = useRef(false);
  const listenersRef = useRef(new Set());
  const initialResolvedRef = useRef(false);
  const prevStatusRef = useRef('loading');
  const pendingGoogleUserIdRef = useRef(null);

  useEffect(() => {
    const handleBootstrapComplete = (e) => {
      const isStillPending = isGoogleRedirectPending();
      if (!isStillPending) {
        const uid = e?.detail?.userId || pendingGoogleUserIdRef.current;
        if (uid) {
          pendingGoogleUserIdRef.current = null;
          setStatus('signed-in');
          setUserId(uid);
          prevStatusRef.current = 'signed-in';
          sessionLostReportedRef.current = false;
          terminalOutcomeRef.current = false;
          initialResolvedRef.current = true;
        }
      }
    };
    if (typeof window !== 'undefined') {
      window.addEventListener('mland:auth-bootstrap-complete', handleBootstrapComplete);
      return () => window.removeEventListener('mland:auth-bootstrap-complete', handleBootstrapComplete);
    }
  }, []);

  const subscribeOutcome = useMemo(() => {
    return (listener) => {
      if (typeof listener === 'function') {
        listenersRef.current.add(listener);
      }
      return () => {
        listenersRef.current.delete(listener);
      };
    };
  }, []);

  const emitOutcome = (outcome) => {
    if (outcome.type === 'USER_SIGN_OUT' || outcome.type === 'SESSION_LOST') {
      terminalOutcomeRef.current = true;
    }
    listenersRef.current.forEach(listener => {
      try {
        listener(outcome);
      } catch (err) {
        console.error('Session outcome listener error:', err);
      }
    });
  };

  useEffect(() => {
    return source.watch(next => {
      const prev = prevStatusRef.current;
      prevStatusRef.current = next.status;
      setStatus(next.status);
      setUserId(next.userId ?? null);

      if (next.status === 'signed-in') {
        if (isGoogleRedirectPending()) {
          // FR-047: Google redirect sign-in MUST synchronize account bootstrap before global session activation
          pendingGoogleUserIdRef.current = next.userId ?? null;
          setStatus('loading');
          return;
        }
        sessionLostReportedRef.current = false;
        terminalOutcomeRef.current = false;
        initialResolvedRef.current = true;
      } else if (next.status === 'signed-out') {
        if (isGoogleRedirectPending()) {
          setStatus('loading');
          return;
        }
        // Firebase reports no user after having been signed-in
        if (initialResolvedRef.current && prev === 'signed-in') {
          // If explicit sign-out is in progress, USER_SIGN_OUT takes precedence
          if (!isUserSigningOutRef.current && !sessionLostReportedRef.current) {
            sessionLostReportedRef.current = true;
            emitOutcome({ type: 'SESSION_LOST' });
          }
        }
        initialResolvedRef.current = true;
      } else if (next.status === 'unconfigured') {
        initialResolvedRef.current = true;
      }
    });
  }, [source]);

  const signOut = async () => {
    isUserSigningOutRef.current = true;
    terminalOutcomeRef.current = true;
    try {
      await source.signOut();
      setStatus('signed-out');
      setUserId(null);
      prevStatusRef.current = 'signed-out';
      sessionLostReportedRef.current = false;
      emitOutcome({ type: 'USER_SIGN_OUT' });
    } catch (err) {
      // Non-terminal: sign-out failure remains retryable in place without navigation
      terminalOutcomeRef.current = false;
      throw err;
    } finally {
      isUserSigningOutRef.current = false;
    }
  };

  const reportSessionLost = (outcomeInfo = {}) => {
    if (isUserSigningOutRef.current) return;
    if (sessionLostReportedRef.current) return;
    sessionLostReportedRef.current = true;
    terminalOutcomeRef.current = true;
    setStatus('signed-out');
    setUserId(null);
    prevStatusRef.current = 'signed-out';
    emitOutcome({ type: 'SESSION_LOST', code: outcomeInfo?.code });
    try {
      source.signOut?.()?.catch?.(() => {});
    } catch {
      // ignore
    }
  };

  const reportAccessDenied = () => {
    // 403 authorization denial retains the valid session
    emitOutcome({ type: 'ACCESS_DENIED' });
  };

  const value = useMemo(() => ({
    status,
    userId,
    isTerminalSession: terminalOutcomeRef.current,
    getIdToken: source.getIdToken,
    signOut,
    reportSessionLost,
    reportAccessDenied,
    subscribeOutcome,
  }), [status, userId, source, subscribeOutcome]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const value = useContext(AuthContext);
  if (!value) throw new Error('useAuth must be used inside AuthProvider');
  return value;
}
