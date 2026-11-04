import { useState, useEffect, useCallback } from 'react';
import { firebaseAuthClient } from '../auth/FirebaseAuthClient';
import {
  accountManagementApiClient,
  AccountResponse,
} from '../api/AccountManagementApiClient';
import { ApiError } from '../../../shared/api/http';

export type CurrentAccountStatus =
  | 'loading'
  | 'authenticated'
  | 'unauthenticated'
  | 'error';

export interface UseCurrentAccountResult {
  account: AccountResponse | null;
  status: CurrentAccountStatus;
  error: ApiError | Error | null;
  reloadAccount: () => Promise<AccountResponse | null>;
  signOut: () => Promise<void>;
  retry: () => Promise<void>;
}

export function useCurrentAccount(): UseCurrentAccountResult {
  const [account, setAccount] = useState<AccountResponse | null>(null);
  const [status, setStatus] = useState<CurrentAccountStatus>('loading');
  const [error, setError] = useState<ApiError | Error | null>(null);

  const fetchAccount = useCallback(async (isBootstrap: boolean = true) => {
    const user = firebaseAuthClient.getCurrentUser();
    if (!user) {
      setAccount(null);
      setStatus('unauthenticated');
      setError(null);
      return null;
    }

    setStatus('loading');
    setError(null);

    try {
      // Bootstrap resolves or idempotently provisions the Account record
      const acc = isBootstrap
        ? await accountManagementApiClient.bootstrapOwnAccount()
        : await accountManagementApiClient.getOwnAccount();
      setAccount(acc);
      setStatus('authenticated');
      return acc;
    } catch (err: any) {
      if (err instanceof ApiError) {
        if (err.status === 401) {
          setAccount(null);
          setStatus('unauthenticated');
          setError(err);
          return null;
        }
        if (err.status === 403) {
          setAccount(null);
          setStatus('error');
          setError(err);
          return null;
        }
      }
      setStatus('error');
      setError(err);
      return null;
    }
  }, []);

  useEffect(() => {
    const unsubscribe = firebaseAuthClient.onAuthStateChanged(user => {
      if (!user) {
        setAccount(null);
        setStatus('unauthenticated');
        setError(null);
      } else {
        fetchAccount(true);
      }
    });

    return () => unsubscribe();
  }, [fetchAccount]);

  const reloadAccount = useCallback(async () => {
    return fetchAccount(false);
  }, [fetchAccount]);

  const signOut = useCallback(async () => {
    await firebaseAuthClient.signOut();
    setAccount(null);
    setStatus('unauthenticated');
    setError(null);
  }, []);

  const retry = useCallback(async () => {
    await fetchAccount(true);
  }, [fetchAccount]);

  return {
    account,
    status,
    error,
    reloadAccount,
    signOut,
    retry,
  };
}

export default useCurrentAccount;

