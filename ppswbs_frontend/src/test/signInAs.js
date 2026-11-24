import { vi } from 'vitest';
import * as currentAccount from '../features/accountmanagement/hooks/useCurrentAccount';

/** The 001 Account bootstrap read by RouteAccessGuard (no Firebase in tests): signed in with the given role. */
export function signInAs(role, status = 'ACTIVE') {
  return vi.spyOn(currentAccount, 'useCurrentAccount').mockReturnValue({
    account: { accountId: 10, role, status },
    status: 'authenticated', error: null, reloadAccount: vi.fn(), signOut: vi.fn(), retry: vi.fn(),
  });
}
