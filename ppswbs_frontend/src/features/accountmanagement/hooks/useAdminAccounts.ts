import { useState, useCallback } from 'react';
import {
  accountManagementApiClient,
  AccountDetailResponse,
  AccountSummary,
  PagedAccountsResponse,
  InternalAccountCreateRequest,
  InternalAccountSummaryResponse,
} from '../api/AccountManagementApiClient';
import { ApiError, newCommandId } from '../../../shared/api/http';

export function useAdminAccounts() {
  const [loading, setLoading] = useState<boolean>(false);
  const [error, setError] = useState<ApiError | Error | null>(null);
  const [pagedResult, setPagedResult] = useState<PagedAccountsResponse | null>(null);
  const [selectedAccount, setSelectedAccount] = useState<AccountDetailResponse | null>(null);

  // List accounts with filters & pagination
  const listAccounts = useCallback(
    async (params?: {
      q?: string;
      role?: string;
      status?: string;
      page?: number;
      size?: number;
    }) => {
      setLoading(true);
      setError(null);
      try {
        const result = await accountManagementApiClient.listAccounts(params);
        setPagedResult(result);
        return result;
      } catch (err: any) {
        setError(err);
        return null;
      } finally {
        setLoading(false);
      }
    },
    []
  );

  // Load account detail
  const loadDetail = useCallback(async (accountId: number | string) => {
    setLoading(true);
    setError(null);
    try {
      const detail = await accountManagementApiClient.getAccountDetail(accountId);
      setSelectedAccount(detail);
      return detail;
    } catch (err: any) {
      setError(err);
      return null;
    } finally {
      setLoading(false);
    }
  }, []);

  // Create internal account (STAFF / MANAGER) with Idempotency-Key
  const createInternalAccount = useCallback(
    async (
      payload: InternalAccountCreateRequest,
      idempotencyKey: string = newCommandId()
    ): Promise<InternalAccountSummaryResponse | null> => {
      setLoading(true);
      setError(null);
      try {
        const result = await accountManagementApiClient.createInternalAccount(
          payload,
          idempotencyKey
        );
        return result;
      } catch (err: any) {
        setError(err);
        return null;
      } finally {
        setLoading(false);
      }
    },
    []
  );

  // Resend invitation (for PENDING_SETUP accounts)
  const resendInvitation = useCallback(
    async (accountId: number | string): Promise<InternalAccountSummaryResponse | null> => {
      setLoading(true);
      setError(null);
      try {
        const result = await accountManagementApiClient.resendInvitation(accountId);
        return result;
      } catch (err: any) {
        setError(err);
        return null;
      } finally {
        setLoading(false);
      }
    },
    []
  );

  // Admin edit internal profile
  const updateProfile = useCallback(
    async (
      accountId: number | string,
      fullName: string,
      phone: string | undefined,
      version: number
    ): Promise<AccountDetailResponse | null> => {
      setLoading(true);
      setError(null);
      try {
        const updated = await accountManagementApiClient.updateInternalProfile(accountId, {
          fullName,
          phone,
          version,
        });
        setSelectedAccount(updated);
        return updated;
      } catch (err: any) {
        setError(err);
        if (err instanceof ApiError && err.status === 409) {
          // Version conflict; reload latest detail
          await loadDetail(accountId);
        }
        return null;
      } finally {
        setLoading(false);
      }
    },
    [loadDetail]
  );

  // Admin assign role & location
  const assignRoleLocation = useCallback(
    async (
      accountId: number | string,
      role: 'STAFF' | 'MANAGER',
      locationIds: number[],
      version: number
    ): Promise<AccountDetailResponse | null> => {
      setLoading(true);
      setError(null);
      try {
        const updated = await accountManagementApiClient.assignRoleLocation(accountId, {
          role,
          locationIds,
          version,
        });
        setSelectedAccount(updated);
        return updated;
      } catch (err: any) {
        setError(err);
        if (err instanceof ApiError && err.status === 409) {
          await loadDetail(accountId);
        }
        return null;
      } finally {
        setLoading(false);
      }
    },
    [loadDetail]
  );

  // Admin suspend account
  const suspendAccount = useCallback(
    async (
      accountId: number | string,
      reason: string,
      version: number
    ): Promise<AccountSummary | null> => {
      setLoading(true);
      setError(null);
      try {
        const result = await accountManagementApiClient.suspendAccount(accountId, {
          reason,
          version,
        });
        // Reload detail to reflect updated status
        await loadDetail(accountId);
        return result;
      } catch (err: any) {
        setError(err);
        if (err instanceof ApiError && err.status === 409) {
          await loadDetail(accountId);
        }
        return null;
      } finally {
        setLoading(false);
      }
    },
    [loadDetail]
  );

  // Admin restore account
  const restoreAccount = useCallback(
    async (accountId: number | string): Promise<AccountSummary | null> => {
      setLoading(true);
      setError(null);
      try {
        const result = await accountManagementApiClient.restoreAccount(accountId);
        await loadDetail(accountId);
        return result;
      } catch (err: any) {
        setError(err);
        return null;
      } finally {
        setLoading(false);
      }
    },
    [loadDetail]
  );

  return {
    loading,
    error,
    pagedResult,
    selectedAccount,
    listAccounts,
    loadDetail,
    createInternalAccount,
    resendInvitation,
    updateProfile,
    assignRoleLocation,
    suspendAccount,
    restoreAccount,
  };
}

export default useAdminAccounts;

