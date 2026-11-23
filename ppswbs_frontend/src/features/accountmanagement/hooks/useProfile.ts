import { useState, useCallback, useEffect } from 'react';
import {
  accountManagementApiClient,
  ProfileResponse,
} from '../api/AccountManagementApiClient';
import { ApiError } from '../../../shared/api/http';
import { useStableT, viT, Translate } from './useStableT';

export interface FieldErrors {
  fullName?: string;
  phone?: string;
  generic?: string;
}

const CONTROL_CHARS_REGEX = /[\u0000-\u001F\u007F-\u009F]/;

export function validateProfileInput(
  fullName: string,
  phone?: string | null,
  t: Translate = viT
): FieldErrors {
  const errors: FieldErrors = {};
  const trimmedName = fullName.trim();

  if (!trimmedName) {
    errors.fullName = t('hook.profile.nameRequired');
  } else if (trimmedName.length > 100) {
    errors.fullName = t('hook.profile.nameLength');
  } else if (CONTROL_CHARS_REGEX.test(trimmedName)) {
    errors.fullName = t('hook.profile.nameControl');
  }

  if (phone && phone.trim()) {
    const trimmedPhone = phone.trim();
    if (trimmedPhone.length < 7 || trimmedPhone.length > 30) {
      errors.phone = t('hook.profile.phoneLength');
    }
  }

  return errors;
}

export function useProfile() {
  const t = useStableT();
  const [profile, setProfile] = useState<ProfileResponse | null>(null);
  const [fullName, setFullName] = useState<string>('');
  const [phone, setPhone] = useState<string>('');
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});
  const [loading, setLoading] = useState<boolean>(true);
  const [saving, setSaving] = useState<boolean>(false);
  const [error, setError] = useState<ApiError | Error | null>(null);
  const [successNotice, setSuccessNotice] = useState<string | null>(null);

  const loadProfile = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await accountManagementApiClient.getOwnProfile();
      setProfile(data);
      setFullName(data.fullName || '');
      setPhone(data.phone || '');
      return data;
    } catch (err: any) {
      setError(err);
      return null;
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadProfile();
  }, [loadProfile]);

  const saveProfile = useCallback(async (): Promise<boolean> => {
    setSuccessNotice(null);
    const errors = validateProfileInput(fullName, phone, t);
    setFieldErrors(errors);

    if (Object.keys(errors).length > 0) {
      return false;
    }

    setSaving(true);
    setError(null);

    try {
      const updated = await accountManagementApiClient.updateOwnProfile({
        fullName: fullName.trim(),
        phone: phone ? phone.trim() : null,
      });
      setProfile(updated);
      setFullName(updated.fullName || '');
      setPhone(updated.phone || '');
      setSuccessNotice(t('hook.profile.saved'));
      return true;
    } catch (err: any) {
      setError(err);
      if (err instanceof ApiError) {
        if (err.fieldErrors && err.fieldErrors.length > 0) {
          const apiFieldErrors: FieldErrors = {};
          err.fieldErrors.forEach(item => {
            if (item.field === 'fullName') apiFieldErrors.fullName = item.message;
            if (item.field === 'phone') apiFieldErrors.phone = item.message;
          });
          setFieldErrors(apiFieldErrors);
        }
        // 409: Reconcile current values
        if (err.status === 409) {
          await loadProfile();
        }
      }
      return false;
    } finally {
      setSaving(false);
    }
  }, [fullName, phone, loadProfile]);

  const resetForm = useCallback(() => {
    if (profile) {
      setFullName(profile.fullName || '');
      setPhone(profile.phone || '');
      setFieldErrors({});
      setError(null);
      setSuccessNotice(null);
    }
  }, [profile]);

  return {
    profile,
    fullName,
    setFullName,
    phone,
    setPhone,
    fieldErrors,
    loading,
    saving,
    error,
    successNotice,
    loadProfile,
    saveProfile,
    resetForm,
  };
}

export default useProfile;

