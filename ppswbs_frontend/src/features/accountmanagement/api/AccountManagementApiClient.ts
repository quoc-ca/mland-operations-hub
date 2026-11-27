import { apiBaseUrl, ApiError, newCommandId } from '../../../shared/api/http';
import { firebaseAuthClient } from '../auth/FirebaseAuthClient';

export interface LocationRef {
  id: string;
  name: string;
}

export interface WorkshopLocation {
  id: number;
  code: string;
  name: string;
  address?: string | null;
  active: boolean;
}

export interface AccountResponse {
  id: string;
  email: string;
  emailVerified: boolean;
  role: 'MEMBER' | 'STAFF' | 'MANAGER' | 'CARRIER' | 'ADMIN';
  status: 'ACTIVE' | 'SUSPENDED' | 'PENDING_SETUP';
  locations: LocationRef[];
  version: number;
  createdAt: string;
  updatedAt: string;
}

export interface ProfileResponse extends AccountResponse {
  fullName: string | null;
  phone: string | null;
}

export interface ProfileUpdateRequest {
  fullName: string;
  phone?: string | null;
}

export interface AccountSummary {
  accountId: number;
  role: 'MEMBER' | 'STAFF' | 'MANAGER' | 'CARRIER' | 'ADMIN';
  status: 'ACTIVE' | 'SUSPENDED' | 'PENDING_SETUP';
  loginEmail: string;
  revocationStatus: 'NOT_APPLICABLE' | 'PENDING' | 'CONFIRMED';
  createdAt: string;
  updatedAt: string;
}

export interface PagedAccountsResponse {
  data: AccountSummary[];
  meta: {
    page: number;
    size: number;
    totalElements: number;
    totalPages: number;
  };
}

export interface LocationAssignment {
  locationId: number;
  assignedAt: string;
  active: boolean;
}

export interface AccountDetailResponse {
  accountId: number;
  role: 'MEMBER' | 'STAFF' | 'MANAGER' | 'CARRIER' | 'ADMIN';
  status: 'ACTIVE' | 'SUSPENDED' | 'PENDING_SETUP';
  loginEmail: string;
  revocationStatus: 'NOT_APPLICABLE' | 'PENDING' | 'CONFIRMED';
  createdAt: string;
  updatedAt: string;
  profile: {
    fullName: string | null;
    phone: string | null;
  } | null;
  locationAssignments: LocationAssignment[];
  version: number;
}

export interface InternalAccountCreateRequest {
  role: 'STAFF' | 'MANAGER' | 'CARRIER';
  email?: string;
  password?: string;
  fullName: string;
  phone?: string;
  locationIds?: number[];
}

export interface InternalAccountSummaryResponse {
  accountId: number;
  role: 'MEMBER' | 'STAFF' | 'MANAGER' | 'CARRIER' | 'ADMIN';
  status: 'ACTIVE' | 'SUSPENDED' | 'PENDING_SETUP';
  loginEmail: string;
  revocationStatus: 'NOT_APPLICABLE' | 'PENDING' | 'CONFIRMED';
  createdAt: string;
  updatedAt: string;
  provisioningStatus: string;
  invitationLastSentAt: string | null;
}

// R-INV Data Types per r-inv.openapi.yaml
export interface EmailCollisionResponse {
  normalizedEmail: string;
  outcome: 'NONE' | 'MEMBER_REVIEW' | 'STAFF_MANAGER_MAINTENANCE' | 'FIREBASE_ONLY' | 'BLOCKED_ROLE';
  accountId?: number;
}

export interface ObligationBlocker {
  reference: string;
  reasonCode: string;
  message: string;
}

export interface RetailCartLine {
  productReference: string;
  variantReference: string;
  displayName: string;
  quantity: number;
  updatedAt: string;
}

export interface RetailOrderSummary {
  orderId: string;
  displayCode: string;
  acceptedAt: string;
  paymentState: string;
  fulfilmentState: string;
  itemSummary: string;
}

export interface WorkshopBookingSummary {
  bookingId: string;
  displayCode: string;
  workshopDate: string;
  sessionTime: string;
  locationName: string;
  status: string;
  seatCount: number;
}

export interface ReplacementReviewResponse {
  accountId: number;
  loginEmail: string;
  fullName: string;
  phone?: string | null;
  status: string;
  dataOrigin: 'LIVE' | 'DEMO';
  obligationStatus: 'CLEAR' | 'BLOCKED' | 'UNAVAILABLE';
  reviewVersion: number;
  sourceVersion: string | null;
  generatedAt: string;
  addresses: Array<{
    id: number;
    receiverName: string;
    phone: string;
    detailAddress: string;
    isDefault: boolean;
  }>;
  serverStoredRetailCart: RetailCartLine[];
  retailPurchaseHistory: RetailOrderSummary[];
  workshopBookingHistory: WorkshopBookingSummary[];
  obligationBlockers?: ObligationBlocker[];
}

export interface RetirementCommand {
  reviewVersion: number;
  sourceVersion?: string | null;
  normalizedEmail: string;
  confirmedTargetAccountId: number;
}

export interface RetirementResultResponse {
  retiredAccountId: number;
  stage: string;
  message: string;
}

export interface FirebaseOnlyDeleteCommand {
  normalizedEmail: string;
  confirmation: boolean;
}

export interface FirebaseOnlyDeleteResultResponse {
  normalizedEmail: string;
  deleted: boolean;
  message: string;
}

export interface InvitationStatusResponse {
  invitationId: number;
  version: number;
  createdAt: string;
  expiresAt: string;
  serverNow: string;
  deliveryState: 'QUEUED' | 'PROCESSING' | 'SENT' | 'FAILED' | 'UNKNOWN_OUTCOME';
  attempts: number;
  manualContentAvailable: boolean;
  requestId?: number;
}

export interface ManualInvitationResponse {
  requestId: number;
  invitationVersion: number;
  subject: string;
  html: string;
  plainText: string;
  expiresAt: string;
  serverNow: string;
}

export interface InvitationExchangeCommand {
  secret: string;
}

export interface InvitationExchangeResultResponse {
  firebaseCustomToken: string;
  setupSession: string;
  expiresAt: string;
}

export interface InvitationCompletionCommand {
  setupSession: string;
  method: 'PASSWORD' | 'GOOGLE';
}

export interface InvitationCompletionResultResponse {
  accountId: number;
  role: 'STAFF' | 'MANAGER';
  homeRoute: string;
  status: 'ACTIVE';
}

export class AccountManagementApiClient {
  private baseUrl: string;
  private getToken: () => Promise<string | null>;
  public onSessionExpired?: () => void;
  public onAccessDenied?: () => void;

  constructor(
    baseUrl: string = apiBaseUrl,
    getToken: () => Promise<string | null> = () => firebaseAuthClient.getIdToken()
  ) {
    this.baseUrl = baseUrl.replace(/\/$/, '');
    this.getToken = getToken;
  }

  private async request<T>(
    path: string,
    options: {
      method?: string;
      body?: any;
      query?: Record<string, string | number | boolean | undefined | null>;
      idempotencyKey?: string;
      correlationId?: string;
      forceFreshToken?: boolean;
      publicRequest?: boolean;
    } = {}
  ): Promise<T> {
    const {
      method = 'GET',
      body,
      query,
      idempotencyKey,
      correlationId = newCommandId(),
      forceFreshToken = false,
      publicRequest = false,
    } = options;

    const origin = this.baseUrl || (typeof window !== 'undefined' ? window.location.origin : 'http://localhost:8080');
    const url = new URL(`${origin}${path}`);
    if (query) {
      Object.entries(query).forEach(([k, v]) => {
        if (v !== undefined && v !== null && v !== '') {
          url.searchParams.set(k, String(v));
        }
      });
    }

    const headers: Record<string, string> = {
      Accept: 'application/json',
      'ngrok-skip-browser-warning': '1',
      'X-Correlation-Id': correlationId,
    };

    const token = publicRequest ? null : await (forceFreshToken
      ? firebaseAuthClient.getIdToken(true)
      : this.getToken());

    if (token) {
      headers.Authorization = `Bearer ${token}`;
    } else if (!publicRequest) {
      throw new ApiError({
        status: 401,
        code: 'UNAUTHENTICATED',
        message: 'Phiên làm việc đã hết hạn hoặc chưa được xác thực. Vui lòng đăng nhập lại.',
        correlationId,
        method,
      });
    }

    if (idempotencyKey) {
      headers['Idempotency-Key'] = idempotencyKey;
    }

    if (body !== undefined) {
      headers['Content-Type'] = 'application/json';
    }

    let response: Response;
    try {
      response = await fetch(url.toString(), {
        method,
        headers,
        body: body !== undefined ? JSON.stringify(body) : undefined,
      });
    } catch (networkError: any) {
      throw new ApiError({
        status: 0,
        code: 'NETWORK_ERROR',
        message: networkError?.message || 'Không thể kết nối đến máy chủ',
        correlationId,
        method,
      });
    }

    if (!response.ok) {
      if (response.status === 401) {
        this.onSessionExpired?.();
      } else if (response.status === 403) {
        this.onAccessDenied?.();
      }
      const payload = await response.json().catch(() => ({}));
      throw new ApiError({
        status: response.status,
        code: payload.code || `HTTP_${response.status}`,
        message: payload.message || response.statusText,
        correlationId: payload.correlationId || response.headers.get('X-Correlation-Id') || correlationId,
        fieldErrors: Array.isArray(payload.fieldErrors) ? payload.fieldErrors : [],
        method,
      });
    }

    if (response.status === 204) {
      return undefined as unknown as T;
    }

    const text = await response.text();
    return text ? JSON.parse(text) : (undefined as unknown as T);
  }

  // Self-Service & Bootstrap routes
  public async bootstrapOwnAccount(correlationId?: string): Promise<AccountResponse> {
    return this.request<AccountResponse>('/api/v1/accounts/me', {
      method: 'PUT',
      correlationId,
    });
  }

  public async getOwnAccount(correlationId?: string): Promise<AccountResponse> {
    return this.request<AccountResponse>('/api/v1/accounts/me', {
      method: 'GET',
      correlationId,
    });
  }

  public async getOwnProfile(correlationId?: string): Promise<ProfileResponse> {
    return this.request<ProfileResponse>('/api/v1/accounts/me/profile', {
      method: 'GET',
      correlationId,
    });
  }

  public async updateOwnProfile(
    payload: ProfileUpdateRequest,
    correlationId?: string
  ): Promise<ProfileResponse> {
    return this.request<ProfileResponse>('/api/v1/accounts/me/profile', {
      method: 'PUT',
      body: payload,
      correlationId,
    });
  }

  /**
   * Bodyless email synchronization after fresh token reload.
   * Per OpenAPI & FR-006: Server inspects bearer token directly.
   */
  public async syncOwnEmail(correlationId?: string): Promise<AccountResponse> {
    try {
      return await this.request<AccountResponse>('/api/v1/accounts/me/email', {
        method: 'PUT',
        correlationId,
        forceFreshToken: true,
      });
    } catch (err: any) {
      if (err instanceof ApiError && err.status === 409) {
        return await this.getOwnAccount(correlationId);
      }
      throw err;
    }
  }

  // Admin Account routes
  public async listAccounts(
    params?: {
      q?: string;
      role?: string;
      status?: string;
      page?: number;
      size?: number;
    },
    correlationId?: string
  ): Promise<PagedAccountsResponse> {
    return this.request<PagedAccountsResponse>('/api/v1/admin/accounts', {
      method: 'GET',
      query: params,
      correlationId,
    });
  }

  public async getAccountDetail(
    accountId: number | string,
    correlationId?: string
  ): Promise<AccountDetailResponse> {
    return this.request<AccountDetailResponse>(`/api/v1/admin/accounts/${accountId}`, {
      method: 'GET',
      correlationId,
    });
  }

  public async createInternalAccount(
    payload: InternalAccountCreateRequest,
    idempotencyKey: string = newCommandId(),
    correlationId?: string
  ): Promise<InternalAccountSummaryResponse> {
    return this.request<InternalAccountSummaryResponse>('/api/v1/admin/accounts/internal', {
      method: 'POST',
      body: payload,
      idempotencyKey,
      correlationId,
    });
  }

  public async resendInvitation(
    accountId: number | string,
    correlationId?: string
  ): Promise<InternalAccountSummaryResponse> {
    return this.request<InternalAccountSummaryResponse>(`/api/v1/admin/accounts/${accountId}/invitations`, {
      method: 'POST',
      correlationId,
    });
  }

  public async updateInternalProfile(
    accountId: number | string,
    payload: { fullName: string; phone?: string; version: number },
    correlationId?: string
  ): Promise<AccountDetailResponse> {
    return this.request<AccountDetailResponse>(`/api/v1/admin/accounts/${accountId}/profile`, {
      method: 'PUT',
      body: payload,
      correlationId,
    });
  }

  public async assignRoleLocation(
    accountId: number | string,
    payload: { role: 'STAFF' | 'MANAGER'; locationIds: number[]; version: number },
    correlationId?: string
  ): Promise<AccountDetailResponse> {
    return this.request<AccountDetailResponse>(`/api/v1/admin/accounts/${accountId}/role-location`, {
      method: 'PUT',
      body: payload,
      correlationId,
    });
  }

  public async suspendAccount(
    accountId: number | string,
    payload: { reason: string; version: number },
    correlationId?: string
  ): Promise<AccountSummary> {
    return this.request<AccountSummary>(`/api/v1/admin/accounts/${accountId}/suspend`, {
      method: 'POST',
      body: payload,
      correlationId,
    });
  }

  public async restoreAccount(
    accountId: number | string,
    correlationId?: string
  ): Promise<AccountSummary> {
    return this.request<AccountSummary>(`/api/v1/admin/accounts/${accountId}/restore`, {
      method: 'POST',
      correlationId,
    });
  }

  // Workshop locations (003.2, read-only for Admin) used to assign Staff/Manager
  public async listWorkshopLocations(correlationId?: string): Promise<WorkshopLocation[]> {
    return this.request<WorkshopLocation[]>('/api/v1/workshop-locations', {
      method: 'GET',
      correlationId,
    });
  }

  // R-INV Admin Email Collision & Repair endpoints
  public async resolveEmailCollision(email: string, correlationId?: string): Promise<EmailCollisionResponse> {
    return this.request<EmailCollisionResponse>('/api/v1/admin/accounts/email-collisions', {
      method: 'GET',
      query: { email },
      correlationId,
    });
  }

  public async reviewMemberReplacement(accountId: number | string, correlationId?: string): Promise<ReplacementReviewResponse> {
    return this.request<ReplacementReviewResponse>(`/api/v1/admin/accounts/${accountId}/replacement-review`, {
      method: 'GET',
      correlationId,
    });
  }

  public async exportMemberReplacement(accountId: number | string, correlationId?: string): Promise<ReplacementReviewResponse> {
    return this.request<ReplacementReviewResponse>(`/api/v1/admin/accounts/${accountId}/replacement-export`, {
      method: 'GET',
      correlationId,
    });
  }

  public async retireMemberForReplacement(
    accountId: number | string,
    payload: RetirementCommand,
    idempotencyKey: string = newCommandId(),
    correlationId?: string
  ): Promise<RetirementResultResponse> {
    return this.request<RetirementResultResponse>(`/api/v1/admin/accounts/${accountId}/retire-for-replacement`, {
      method: 'POST',
      body: payload,
      idempotencyKey,
      correlationId,
    });
  }

  public async deleteFirebaseOnlyCollision(
    payload: FirebaseOnlyDeleteCommand,
    idempotencyKey: string = newCommandId(),
    correlationId?: string
  ): Promise<FirebaseOnlyDeleteResultResponse> {
    return this.request<FirebaseOnlyDeleteResultResponse>('/api/v1/admin/firebase-identities/email-collisions/delete', {
      method: 'POST',
      body: payload,
      idempotencyKey,
      correlationId,
    });
  }

  public async getCurrentInvitationStatus(accountId: number | string, correlationId?: string): Promise<InvitationStatusResponse> {
    return this.request<InvitationStatusResponse>(`/api/v1/admin/accounts/${accountId}/setup-invitations/current`, {
      method: 'GET',
      correlationId,
    });
  }

  public async getManualInvitation(requestId: number | string, correlationId?: string): Promise<ManualInvitationResponse> {
    return this.request<ManualInvitationResponse>(`/api/v1/admin/notification-requests/${requestId}/manual-invitation`, {
      method: 'GET',
      correlationId,
    });
  }

  // Public setup exchange (no bearer required)
  public async exchangeInternalInvitation(payload: InvitationExchangeCommand): Promise<InvitationExchangeResultResponse> {
    return this.request<InvitationExchangeResultResponse>('/api/v1/internal-account-invitations/exchanges', {
      method: 'POST',
      body: payload,
      publicRequest: true,
    });
  }

  // Authenticated setup completion (requires bearer from setup session)
  public async completeInternalInvitation(
    payload: InvitationCompletionCommand,
    correlationId?: string
  ): Promise<InvitationCompletionResultResponse> {
    return this.request<InvitationCompletionResultResponse>('/api/v1/internal-account-invitations/completions', {
      method: 'POST',
      body: payload,
      correlationId,
      forceFreshToken: true,
    });
  }
}

export const accountManagementApiClient = new AccountManagementApiClient();
export default accountManagementApiClient;

