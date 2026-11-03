// Public port of the account-management feature (FRONTEND-ARCHITECTURE §2): the only module other code may import.
export { accountManagementRoutes } from './routes';
export { firebaseAuthClient } from './auth/FirebaseAuthClient';
export { accountManagementApiClient } from './api/AccountManagementApiClient';
export { useCurrentAccount } from './hooks/useCurrentAccount';
export { useVerifiedEmailGate } from './hooks/useVerifiedEmailGate';
export { useProfile } from './hooks/useProfile';
export { useAdminAccounts } from './hooks/useAdminAccounts';
export { AuthPage } from './components/AuthPage';
export { ProfilePage } from './components/ProfilePage';
export { InternalAccountCreatePage } from './components/InternalAccountCreatePage';
export { AdminAccountDetailPage } from './components/AdminAccountDetailPage';
export { AdminAccountListPage } from './components/AdminAccountListPage';
export { RoleCataloguePage } from './components/RoleCataloguePage';
export type {
  AccountResponse,
  ProfileResponse,
  AccountSummary,
  AccountDetailResponse,
  InternalAccountSummaryResponse,
  RoleCatalogueEntry,
} from './api/AccountManagementApiClient';
export type { CurrentAccountStatus } from './hooks/useCurrentAccount';
export type { GateState } from './hooks/useVerifiedEmailGate';

