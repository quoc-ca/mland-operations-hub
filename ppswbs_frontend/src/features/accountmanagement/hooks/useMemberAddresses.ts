import { useState, useCallback, useEffect } from 'react';
import {
  addressApiClient,
  AddressResponse,
  AddressCreateRequest,
  AddressUpdateRequest,
  AdministrativeDivisionResponse,
} from '../api/AddressApiClient';
import { ApiError } from '../../../shared/api/http';

export function normalizePhoneNumber(input: string): string {
  let cleaned = input.trim().replace(/[\s.\-()]/g, '');
  if (cleaned.startsWith('+84')) {
    cleaned = '0' + cleaned.slice(3);
  } else if (cleaned.startsWith('84') && cleaned.length === 11) {
    cleaned = '0' + cleaned.slice(2);
  }
  return cleaned;
}

export function validatePhoneNumber(phone: string): boolean {
  const normalized = normalizePhoneNumber(phone);
  return /^0[35789]\d{8}$/.test(normalized);
}

export function validateAddressInput(
  data: Partial<AddressCreateRequest | AddressUpdateRequest>
): Record<string, string> {
  const errors: Record<string, string> = {};

  if (!data.recipientName || !data.recipientName.trim()) {
    errors.recipientName = 'Họ và tên người nhận là bắt buộc';
  } else if (data.recipientName.length > 255) {
    errors.recipientName = 'Họ và tên không được vượt quá 255 ký tự';
  }

  if (!data.phoneNumber || !data.phoneNumber.trim()) {
    errors.phoneNumber = 'Số điện thoại người nhận là bắt buộc';
  } else if (!validatePhoneNumber(data.phoneNumber)) {
    errors.phoneNumber = 'Số điện thoại không hợp lệ (phải là số di động Việt Nam 10 chữ số, ví dụ: 0912345678)';
  }

  if (!data.provinceName || !data.provinceName.trim()) {
    errors.provinceName = 'Vui lòng chọn Tỉnh/Thành phố';
  }

  if (!data.districtName || !data.districtName.trim()) {
    errors.districtName = 'Vui lòng chọn Quận/Huyện';
  }

  if (!data.wardName || !data.wardName.trim()) {
    errors.wardName = 'Vui lòng chọn Phường/Xã';
  }

  if (!data.detailAddress || !data.detailAddress.trim()) {
    errors.detailAddress = 'Địa chỉ chi tiết (số nhà, tên đường...) là bắt buộc';
  } else if (data.detailAddress.length > 255) {
    errors.detailAddress = 'Địa chỉ chi tiết không được vượt quá 255 ký tự';
  }

  return errors;
}

export interface UseMemberAddressesOptions {
  autoLoad?: boolean;
}

export function useMemberAddresses(options: UseMemberAddressesOptions = { autoLoad: true }) {
  const [addresses, setAddresses] = useState<AddressResponse[]>([]);
  const [loading, setLoading] = useState<boolean>(false);
  const [saving, setSaving] = useState<boolean>(false);
  const [deletingId, setDeletingId] = useState<number | null>(null);
  const [settingDefaultId, setSettingDefaultId] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [successNotice, setSuccessNotice] = useState<string | null>(null);

  const loadAddresses = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await addressApiClient.getOwnAddresses();
      setAddresses(data);
    } catch (err: any) {
      setError(err?.message || 'Không thể tải danh sách địa chỉ nhận hàng.');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (options.autoLoad) {
      loadAddresses();
    }
  }, [options.autoLoad, loadAddresses]);

  const createAddress = useCallback(
    async (raw: AddressCreateRequest): Promise<AddressResponse> => {
      setSaving(true);
      setError(null);
      setSuccessNotice(null);

      const normalizedPhone = normalizePhoneNumber(raw.phoneNumber);
      const isFirst = addresses.length === 0;
      const payload: AddressCreateRequest = {
        ...raw,
        recipientName: raw.recipientName.trim(),
        phoneNumber: normalizedPhone,
        detailAddress: raw.detailAddress.trim(),
        isDefault: isFirst ? true : Boolean(raw.isDefault),
      };

      try {
        const created = await addressApiClient.createOwnAddress(payload);
        await loadAddresses();
        setSuccessNotice('Thêm địa chỉ nhận hàng thành công.');
        return created;
      } catch (err: any) {
        if (err instanceof ApiError && err.status === 409) {
          setError('Bạn đã đạt giới hạn tối đa 10 địa chỉ nhận hàng.');
        } else {
          setError(err?.message || 'Không thể tạo mới địa chỉ.');
        }
        throw err;
      } finally {
        setSaving(false);
      }
    },
    [addresses.length, loadAddresses]
  );

  const updateAddress = useCallback(
    async (id: number, raw: AddressUpdateRequest): Promise<AddressResponse> => {
      setSaving(true);
      setError(null);
      setSuccessNotice(null);

      const normalizedPhone = normalizePhoneNumber(raw.phoneNumber);
      const payload: AddressUpdateRequest = {
        ...raw,
        recipientName: raw.recipientName.trim(),
        phoneNumber: normalizedPhone,
        detailAddress: raw.detailAddress.trim(),
      };

      try {
        const updated = await addressApiClient.updateOwnAddress(id, payload);
        await loadAddresses();
        setSuccessNotice('Cập nhật địa chỉ thành công.');
        return updated;
      } catch (err: any) {
        setError(err?.message || 'Không thể cập nhật địa chỉ.');
        throw err;
      } finally {
        setSaving(false);
      }
    },
    [loadAddresses]
  );

  const deleteAddress = useCallback(
    async (id: number): Promise<void> => {
      setError(null);
      setSuccessNotice(null);

      // Check if address is default and multiple addresses exist
      const target = addresses.find(a => a.id === id);
      if (target?.isDefault && addresses.length > 1) {
        const msg = 'Không thể xóa địa chỉ mặc định khi còn các địa chỉ khác. Vui lòng thiết lập địa chỉ khác làm mặc định trước.';
        setError(msg);
        throw new Error(msg);
      }

      setDeletingId(id);
      try {
        await addressApiClient.deleteOwnAddress(id);
        await loadAddresses();
        setSuccessNotice('Đã xóa địa chỉ thành công.');
      } catch (err: any) {
        setError(err?.message || 'Không thể xóa địa chỉ.');
        throw err;
      } finally {
        setDeletingId(null);
      }
    },
    [addresses, loadAddresses]
  );

  const setDefaultAddress = useCallback(
    async (id: number): Promise<AddressResponse> => {
      setSettingDefaultId(id);
      setError(null);
      setSuccessNotice(null);
      try {
        const res = await addressApiClient.setDefaultAddress(id);
        await loadAddresses();
        setSuccessNotice('Đã thiết lập địa chỉ mặc định.');
        return res;
      } catch (err: any) {
        setError(err?.message || 'Không thể thiết lập địa chỉ mặc định.');
        throw err;
      } finally {
        setSettingDefaultId(null);
      }
    },
    [loadAddresses]
  );

  const isQuotaReached = addresses.length >= 10;
  const defaultAddress = addresses.find(a => a.isDefault) || null;

  return {
    addresses,
    defaultAddress,
    isQuotaReached,
    loading,
    saving,
    deletingId,
    settingDefaultId,
    error,
    successNotice,
    loadAddresses,
    createAddress,
    updateAddress,
    deleteAddress,
    setDefaultAddress,
    setError,
    setSuccessNotice,
  };
}

export function useAdministrativeDivisions(selectedProvinceId?: string, selectedDistrictId?: string) {
  const [provinces, setProvinces] = useState<AdministrativeDivisionResponse[]>([]);
  const [districts, setDistricts] = useState<AdministrativeDivisionResponse[]>([]);
  const [wards, setWards] = useState<AdministrativeDivisionResponse[]>([]);
  const [loadingProvinces, setLoadingProvinces] = useState<boolean>(false);
  const [loadingDistricts, setLoadingDistricts] = useState<boolean>(false);
  const [loadingWards, setLoadingWards] = useState<boolean>(false);

  // Load provinces on mount
  useEffect(() => {
    let active = true;
    setLoadingProvinces(true);
    addressApiClient
      .listProvinces()
      .then(data => {
        if (active) setProvinces(data);
      })
      .catch(() => {
        if (active) setProvinces([]);
      })
      .finally(() => {
        if (active) setLoadingProvinces(false);
      });
    return () => {
      active = false;
    };
  }, []);

  // Load districts when provinceId changes
  useEffect(() => {
    if (!selectedProvinceId) {
      setDistricts([]);
      setWards([]);
      return;
    }
    let active = true;
    setLoadingDistricts(true);
    addressApiClient
      .listDistricts(selectedProvinceId)
      .then(data => {
        if (active) setDistricts(data);
      })
      .catch(() => {
        if (active) setDistricts([]);
      })
      .finally(() => {
        if (active) setLoadingDistricts(false);
      });
    return () => {
      active = false;
    };
  }, [selectedProvinceId]);

  // Load wards when districtId changes
  useEffect(() => {
    if (!selectedDistrictId) {
      setWards([]);
      return;
    }
    let active = true;
    setLoadingWards(true);
    addressApiClient
      .listWards(selectedDistrictId)
      .then(data => {
        if (active) setWards(data);
      })
      .catch(() => {
        if (active) setWards([]);
      })
      .finally(() => {
        if (active) setLoadingWards(false);
      });
    return () => {
      active = false;
    };
  }, [selectedDistrictId]);

  return {
    provinces,
    districts,
    wards,
    loadingProvinces,
    loadingDistricts,
    loadingWards,
  };
}

