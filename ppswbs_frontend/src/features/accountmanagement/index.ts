// Public port of the account-management feature (FRONTEND-ARCHITECTURE §2): the only module other code may import.
export { accountManagementRoutes } from './routes';
export { firebaseAuthClient } from './auth/FirebaseAuthClient';
export { accountManagementApiClient } from './api/AccountManagementApiClient';
export { addressApiClient, AddressApiClient } from './api/AddressApiClient';
export { useCurrentAccount } from './hooks/useCurrentAccount';
export { useVerifiedEmailGate } from './hooks/useVerifiedEmailGate';
export { useProfile } from './hooks/useProfile';
export { useAdminAccounts } from './hooks/useAdminAccounts';
export {
  useMemberAddresses,
  useAdministrativeDivisions,
  normalizePhoneNumber,
  validatePhoneNumber,
  validateAddressInput,
} from './hooks/useMemberAddresses';
export { AuthPage } from './components/AuthPage';
export { ProfilePage } from './components/ProfilePage';
export { AddressBookPage } from './components/AddressBookPage';
export { AddressModal } from './components/AddressModal';
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
export type {
  AddressResponse,
  AddressCreateRequest,
  AddressUpdateRequest,
  AdministrativeDivisionResponse,
} from './api/AddressApiClient';
export type { CurrentAccountStatus } from './hooks/useCurrentAccount';
export type { GateState } from './hooks/useVerifiedEmailGate';

