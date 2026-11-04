import { apiBaseUrl, ApiError, newCommandId } from '../../../shared/api/http';
import { firebaseAuthClient } from '../auth/FirebaseAuthClient';

export interface AdministrativeDivisionResponse {
  id: string;
  name: string;
  code?: string;
}

export interface AddressResponse {
  id: number;
  recipientName: string;
  phoneNumber: string;
  provinceId: string;
  provinceName: string;
  districtId: string;
  districtName: string;
  wardId: string;
  wardName: string;
  hamletId?: string | null;
  hamletName?: string | null;
  detailAddress: string;
  addressType: 'HOME' | 'OFFICE';
  isDefault: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface AddressCreateRequest {
  recipientName: string;
  phoneNumber: string;
  provinceId?: string | null;
  provinceName: string;
  districtId?: string | null;
  districtName: string;
  wardId?: string | null;
  wardName: string;
  hamletId?: string | null;
  hamletName?: string | null;
  detailAddress: string;
  addressType?: 'HOME' | 'OFFICE';
  isDefault?: boolean;
}

export interface AddressUpdateRequest {
  recipientName: string;
  phoneNumber: string;
  provinceId?: string | null;
  provinceName: string;
  districtId?: string | null;
  districtName: string;
  wardId?: string | null;
  wardName: string;
  hamletId?: string | null;
  hamletName?: string | null;
  detailAddress: string;
  addressType?: 'HOME' | 'OFFICE';
  isDefault?: boolean;
}

export class AddressApiClient {
  private baseUrl: string;
  private getToken: () => Promise<string | null>;

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
      requireAuth?: boolean;
    } = {}
  ): Promise<T> {
    const {
      method = 'GET',
      body,
      query,
      idempotencyKey,
      correlationId = newCommandId(),
      requireAuth = true,
    } = options;

    const url = new URL(`${this.baseUrl}${path}`);
    if (query) {
      Object.entries(query).forEach(([k, v]) => {
        if (v !== undefined && v !== null && v !== '') {
          url.searchParams.set(k, String(v));
        }
      });
    }

    const headers: Record<string, string> = {
      Accept: 'application/json',
      'X-Correlation-Id': correlationId,
    };

    const token = await this.getToken();
    if (token) {
      headers.Authorization = `Bearer ${token}`;
    } else if (requireAuth) {
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

  // Member Address endpoints
  public async getOwnAddresses(correlationId?: string): Promise<AddressResponse[]> {
    return this.request<AddressResponse[]>('/api/v1/accounts/me/addresses', {
      method: 'GET',
      correlationId,
      requireAuth: true,
    });
  }

  public async getDefaultAddress(correlationId?: string): Promise<AddressResponse> {
    return this.request<AddressResponse>('/api/v1/accounts/me/addresses/default', {
      method: 'GET',
      correlationId,
      requireAuth: true,
    });
  }

  public async createOwnAddress(
    payload: AddressCreateRequest,
    idempotencyKey?: string,
    correlationId?: string
  ): Promise<AddressResponse> {
    return this.request<AddressResponse>('/api/v1/accounts/me/addresses', {
      method: 'POST',
      body: payload,
      idempotencyKey,
      correlationId,
      requireAuth: true,
    });
  }

  public async updateOwnAddress(
    addressId: number,
    payload: AddressUpdateRequest,
    correlationId?: string
  ): Promise<AddressResponse> {
    return this.request<AddressResponse>(`/api/v1/accounts/me/addresses/${addressId}`, {
      method: 'PUT',
      body: payload,
      correlationId,
      requireAuth: true,
    });
  }

  public async deleteOwnAddress(addressId: number, correlationId?: string): Promise<void> {
    return this.request<void>(`/api/v1/accounts/me/addresses/${addressId}`, {
      method: 'DELETE',
      correlationId,
      requireAuth: true,
    });
  }

  public async setDefaultAddress(addressId: number, correlationId?: string): Promise<AddressResponse> {
    return this.request<AddressResponse>(`/api/v1/accounts/me/addresses/${addressId}/default`, {
      method: 'PATCH',
      correlationId,
      requireAuth: true,
    });
  }

  // Administrative Divisions (public lookup, compatible with GHTK)
  public async listProvinces(correlationId?: string): Promise<AdministrativeDivisionResponse[]> {
    return this.request<AdministrativeDivisionResponse[]>('/api/v1/addresses/provinces', {
      method: 'GET',
      correlationId,
      requireAuth: false,
    });
  }

  public async listDistricts(provinceId: string, correlationId?: string): Promise<AdministrativeDivisionResponse[]> {
    return this.request<AdministrativeDivisionResponse[]>('/api/v1/addresses/districts', {
      method: 'GET',
      query: { provinceId },
      correlationId,
      requireAuth: false,
    });
  }

  public async listWards(districtId: string, correlationId?: string): Promise<AdministrativeDivisionResponse[]> {
    return this.request<AdministrativeDivisionResponse[]>('/api/v1/addresses/wards', {
      method: 'GET',
      query: { districtId },
      correlationId,
      requireAuth: false,
    });
  }
}

export const addressApiClient = new AddressApiClient();
export default addressApiClient;

