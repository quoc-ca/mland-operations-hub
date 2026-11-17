import { useState, useEffect, useCallback, useRef } from 'react';
import { firebaseAuthClient } from '../auth/FirebaseAuthClient';
import { accountManagementApiClient } from '../api/AccountManagementApiClient';
import { ApiError } from '../../../shared/api/http';

export type GateState = 'idle' | 'checking' | 'verified' | 'unverified' | 'error';

export interface UseVerifiedEmailGateOptions {
  accountId?: string | number | null;
  initialVerified?: boolean;
  onSessionExpired?: () => void;
}

export interface UseVerifiedEmailGateResult {
  gateState: GateState;
  isVerified: boolean;
  error: ApiError | Error | null;
  resendCooldownSeconds: number;
  canResend: boolean;
  resending: boolean;
  checkGate: () => Promise<boolean>;
  resendVerification: () => Promise<boolean>;
}

const COOLDOWN_MS = 90 * 1000;

export function useVerifiedEmailGate({
  accountId,
  initialVerified = false,
  onSessionExpired,
}: UseVerifiedEmailGateOptions = {}): UseVerifiedEmailGateResult {
  const [gateState, setGateState] = useState<GateState>(
    initialVerified ? 'verified' : 'idle'
  );
  const [isVerified, setIsVerified] = useState<boolean>(initialVerified);
  const [error, setError] = useState<ApiError | Error | null>(null);
  const [resending, setResending] = useState<boolean>(false);
  const [cooldownRemaining, setCooldownRemaining] = useState<number>(0);

  const storageKey = accountId ? `account_resend_cooldown_${accountId}` : null;
  const timerRef = useRef<any>(null);
  const inFlightPromiseRef = useRef<Promise<boolean> | null>(null);
  const onSessionExpiredRef = useRef(onSessionExpired);

  useEffect(() => {
    onSessionExpiredRef.current = onSessionExpired;
  }, [onSessionExpired]);

  // Check stored cooldown on mount or accountId change
  const updateCooldownFromStorage = useCallback(() => {
    if (!storageKey) return;
    try {
      const stored = localStorage.getItem(storageKey);
      if (stored) {
        const expiresAt = parseInt(stored, 10);
        const remaining = Math.max(0, Math.ceil((expiresAt - Date.now()) / 1000));
        setCooldownRemaining(remaining);
      } else {
        setCooldownRemaining(0);
      }
    } catch {
      // LocalStorage access issues safely ignored
    }
  }, [storageKey]);

  useEffect(() => {
    updateCooldownFromStorage();
  }, [updateCooldownFromStorage]);

  // Tick cooldown timer
  useEffect(() => {
    if (cooldownRemaining <= 0) return;

    timerRef.current = setInterval(() => {
      setCooldownRemaining(prev => {
        if (prev <= 1) {
          clearInterval(timerRef.current);
          return 0;
        }
        return prev - 1;
      });
    }, 1000);

    return () => {
      if (timerRef.current) clearInterval(timerRef.current);
    };
  }, [cooldownRemaining]);

  /**
   * WF-08 Profile verification gate:
   * Explicit check on entry or reload icon. Never run on an interval/poll.
   * Uses inFlightPromiseRef to avoid concurrent duplicate requests during React mounts/re-renders.
   */
  const checkGate = useCallback(async (): Promise<boolean> => {
    if (inFlightPromiseRef.current) {
      return inFlightPromiseRef.current;
    }

    const executeCheck = async (): Promise<boolean> => {
      setGateState('checking');
      setError(null);

      try {
        // Step 1: Reload current user from Firebase identity provider
        await firebaseAuthClient.reloadUser();
        const user = firebaseAuthClient.getCurrentUser();

        if (!user) {
          setGateState('error');
          const sessionErr = new ApiError({
            status: 401,
            code: 'UNAUTHENTICATED',
            message: 'Phiên làm việc đã hết hạn. Vui lòng đăng nhập lại.',
          });
          setError(sessionErr);
          onSessionExpiredRef.current?.();
          return false;
        }

        if (user.emailVerified) {
          // Step 2: User is verified in Firebase; force fresh token and synchronize snapshot via bodyless PUT
          await accountManagementApiClient.syncOwnEmail();
          setIsVerified(true);
          setGateState('verified');
          return true;
        } else {
          // Still unverified
          setIsVerified(false);
          setGateState('unverified');
          return false;
        }
      } catch (err: any) {
        if (err instanceof ApiError && err.status === 401) {
          setGateState('error');
          setError(err);
          onSessionExpiredRef.current?.();
          return false;
        }
        if (
          err?.code === 'auth/invalid-user-token' ||
          err?.code === 'auth/user-token-expired' ||
          err?.code === 'auth/user-not-found' ||
          err?.message?.includes('400') ||
          err?.message?.includes('INVALID_REFRESH_TOKEN')
        ) {
          setGateState('error');
          const sessionErr = new ApiError({
            status: 401,
            code: 'UNAUTHENTICATED',
            message: 'Phiên làm việc đã hết hạn. Vui lòng đăng nhập lại.',
          });
          setError(sessionErr);
          onSessionExpiredRef.current?.();
          return false;
        }
        setGateState('error');
        setError(err);
        return false;
      } finally {
        inFlightPromiseRef.current = null;
      }
    };

    const promise = executeCheck();
    inFlightPromiseRef.current = promise;
    return promise;
  }, []);

  /**
   * Resend verification email via Firebase Web SDK.
   * On success: stores cooldown in localStorage for 90s keyed by accountId.
   */
  const resendVerification = useCallback(async (): Promise<boolean> => {
    if (cooldownRemaining > 0 || resending) return false;

    setResending(true);
    setError(null);

    try {
      await firebaseAuthClient.sendVerificationEmail();
      const expiresAt = Date.now() + COOLDOWN_MS;
      if (storageKey) {
        try {
          localStorage.setItem(storageKey, String(expiresAt));
        } catch {
          // ignore localStorage error
        }
      }
      setCooldownRemaining(90);
      return true;
    } catch (err: any) {
      setError(err);
      return false;
    } finally {
      setResending(false);
    }
  }, [cooldownRemaining, resending, storageKey]);

  return {
    gateState,
    isVerified,
    error,
    resendCooldownSeconds: cooldownRemaining,
    canResend: cooldownRemaining <= 0 && !resending,
    resending,
    checkGate,
    resendVerification,
  };
}

export default useVerifiedEmailGate;

