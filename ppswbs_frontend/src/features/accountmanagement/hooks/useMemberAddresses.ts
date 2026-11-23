import { useState, useCallback, useEffect } from 'react';
import {
  addressApiClient,
  AddressResponse,
  AddressCreateRequest,
  AddressUpdateRequest,
  AdministrativeDivisionResponse,
} from '../api/AddressApiClient';
import { ApiError } from '../../../shared/api/http';
import { useStableT, viT, Translate } from './useStableT';

export function removeDiacritics(str: string): string {
  if (!str) return '';
  return str.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase();
}

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
  data: Partial<AddressCreateRequest | AddressUpdateRequest>,
  t: Translate = viT
): Record<string, string> {
  const errors: Record<string, string> = {};

  const name = data.recipientName?.trim();
  if (!name) {
    errors.recipientName = t('hook.addr.nameRequired');
  } else if (name.length > 255) {
    errors.recipientName = t('hook.addr.nameLength');
  } else if (/[\u0000-\u001F\u007F-\u009F]/.test(name)) {
    errors.recipientName = t('hook.addr.nameControl');
  }

  const phone = data.phoneNumber?.trim();
  if (!phone) {
    errors.phoneNumber = t('hook.addr.phoneRequired');
  } else if (!validatePhoneNumber(phone)) {
    errors.phoneNumber = t('hook.addr.phoneInvalid');
  }

  const prov = (data as any).provinceCode || (data as any).provinceName || (data as any).provinceId;
  if (!prov || !String(prov).trim()) {
    errors.provinceCode = t('hook.addr.province');
    errors.provinceName = t('hook.addr.province');
  }

  const comm = (data as any).communeCode || (data as any).communeName || (data as any).wardName || (data as any).wardId;
  if (!comm || !String(comm).trim()) {
    errors.communeCode = t('hook.addr.commune');
    errors.communeName = t('hook.addr.commune');
    errors.wardName = t('hook.addr.ward');
  }

  if ('districtName' in data && !data.districtName?.trim()) {
    errors.districtName = t('hook.addr.district');
  }

  const detail = data.detailAddress?.trim();
  if (!detail) {
    errors.detailAddress = t('hook.addr.detailRequired');
  } else if (detail.length > 255) {
    errors.detailAddress = t('hook.addr.detailLength');
  } else if (/[\u0000-\u001F\u007F-\u009F]/.test(detail)) {
    errors.detailAddress = t('hook.addr.detailControl');
  }

  return errors;
}

export interface UseMemberAddressesOptions {
  autoLoad?: boolean;
}

export function useMemberAddresses(options: UseMemberAddressesOptions = { autoLoad: true }) {
  const t = useStableT();
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
      setError(err?.message || t('hook.addr.loadError'));
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
        setSuccessNotice(t('hook.addr.created'));
        return created;
      } catch (err: any) {
        if (err instanceof ApiError && err.status === 409) {
          setError(t('hook.addr.quota'));
        } else {
          setError(err?.message || t('hook.addr.createError'));
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
        setSuccessNotice(t('hook.addr.updated'));
        return updated;
      } catch (err: any) {
        setError(err?.message || t('hook.addr.updateError'));
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
        const msg = t('hook.addr.deleteDefault');
        setError(msg);
        throw new Error(msg);
      }

      setDeletingId(id);
      try {
        await addressApiClient.deleteOwnAddress(id);
        await loadAddresses();
        setSuccessNotice(t('hook.addr.deleted'));
      } catch (err: any) {
        setError(err?.message || t('hook.addr.deleteError'));
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
        setSuccessNotice(t('hook.addr.defaultSet'));
        return res;
      } catch (err: any) {
        setError(err?.message || t('hook.addr.defaultError'));
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
  const [communes, setCommunes] = useState<AdministrativeDivisionResponse[]>([]);
  const [districts, setDistricts] = useState<AdministrativeDivisionResponse[]>([]);
  const [wards, setWards] = useState<AdministrativeDivisionResponse[]>([]);
  const [loadingProvinces, setLoadingProvinces] = useState<boolean>(false);
  const [loadingCommunes, setLoadingCommunes] = useState<boolean>(false);
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

  // Load communes when province changes (2-level Shopee style per FR-041)
  useEffect(() => {
    if (!selectedProvinceId) {
      setCommunes([]);
      return;
    }
    let active = true;
    setLoadingCommunes(true);
    addressApiClient
      .listCommunes(selectedProvinceId)
      .then(data => {
        if (active) setCommunes(data);
      })
      .catch(() => {
        if (active) setCommunes([]);
      })
      .finally(() => {
        if (active) setLoadingCommunes(false);
      });
    return () => {
      active = false;
    };
  }, [selectedProvinceId]);

  // Compatibility: load districts when provinceId changes
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

  // Compatibility: load wards when districtId changes
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
    communes,
    districts,
    wards: communes.length > 0 ? communes : wards,
    loadingProvinces,
    loadingCommunes,
    loadingDistricts,
    loadingWards: loadingCommunes || loadingWards,
  };
}

