import { useState, useCallback, useRef } from 'react';
import { useWorkshopRingDesignApi } from '../api/useWorkshopRingDesignApi';
import { setStoredGuestSession, getStoredGuestSession, clearStoredGuestSession } from '../api/workshopRingDesignApi';
import { newCommandId } from '../../../shared/api/http';
import { useT } from '../../../shared/i18n/I18nProvider';
import { getErrorMessage } from '../domain/designErrors';
import { messages } from '../messages';

export function useWorkshopDesignCommand(isAuthenticated = false) {
  const api = useWorkshopRingDesignApi();
  const t = useT(messages);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const currentKeyRef = useRef(null);

  const getOrCreateCommandKey = useCallback(() => {
    if (!currentKeyRef.current) {
      currentKeyRef.current = newCommandId();
    }
    return currentKeyRef.current;
  }, []);

  const resetCommandKey = useCallback(() => {
    currentKeyRef.current = null;
  }, []);

  const executeCreate = useCallback(async (createPayload) => {
    setLoading(true);
    setError(null);
    const key = getOrCreateCommandKey();
    try {
      const guestSession = getStoredGuestSession();
      const res = await api.createDesign(key, createPayload, guestSession, isAuthenticated);
      if (res && res.guestSession) {
        setStoredGuestSession(res.guestSession);
      }
      resetCommandKey();
      return res;
    } catch (err) {
      resetCommandKey();
      if (createPayload?.startMode === 'PUBLISHED_MODEL' && (err?.code === 'DESIGN_OPTION_INVALID' || err?.data?.code === 'DESIGN_OPTION_INVALID' || err?.status === 422)) {
        try {
          const fallbackKey = newCommandId();
          const fallbackPayload = { ...createPayload, startMode: 'BLANK', sourceModelReference: null };
          const guestSession = getStoredGuestSession();
          const fallbackRes = await api.createDesign(fallbackKey, fallbackPayload, guestSession, isAuthenticated);
          if (fallbackRes && fallbackRes.guestSession) {
            setStoredGuestSession(fallbackRes.guestSession);
          }
          return fallbackRes;
        } catch (fallbackErr) {
          setError(getErrorMessage(fallbackErr, t));
          throw fallbackErr;
        }
      }
      if (err?.status === 401) {
        clearStoredGuestSession();
        // If guest session expired, retry once with fresh guest session
        if (!isAuthenticated) {
          try {
            const retryKey = newCommandId();
            const retryRes = await api.createDesign(retryKey, createPayload, null, false);
            if (retryRes && retryRes.guestSession) {
              setStoredGuestSession(retryRes.guestSession);
            }
            return retryRes;
          } catch (retryErr) {
            setError(getErrorMessage(retryErr, t));
            throw retryErr;
          }
        }
      }
      setError(getErrorMessage(err, t));
      throw err;
    } finally {
      setLoading(false);
    }
  }, [api, isAuthenticated, getOrCreateCommandKey, resetCommandKey, t]);

  const executeSave = useCallback(async (designId, savePayload, expectedVersion) => {
    setLoading(true);
    setError(null);
    const key = getOrCreateCommandKey();
    try {
      const guestSession = getStoredGuestSession();
      const res = await api.saveVersion(key, designId, savePayload, expectedVersion, guestSession, isAuthenticated);
      resetCommandKey();
      return res;
    } catch (err) {
      resetCommandKey();
      if (err?.status === 401) {
        clearStoredGuestSession();
      }
      setError(getErrorMessage(err, t));
      throw err;
    } finally {
      setLoading(false);
    }
  }, [api, isAuthenticated, getOrCreateCommandKey, resetCommandKey, t]);

  const executeEvaluate = useCallback(async (designId, savePayload, expectedVersion) => {
    setLoading(true);
    setError(null);
    const key = getOrCreateCommandKey();
    try {
      const guestSession = getStoredGuestSession();
      const res = await api.evaluateDesign(key, designId, savePayload, expectedVersion, guestSession, isAuthenticated);
      resetCommandKey();
      return res;
    } catch (err) {
      if (err?.status === 401) {
        clearStoredGuestSession();
      }
      setError(getErrorMessage(err, t));
      throw err;
    } finally {
      setLoading(false);
    }
  }, [api, isAuthenticated, getOrCreateCommandKey, resetCommandKey, t]);

  const executeArchive = useCallback(async (designId, expectedVersion) => {
    setLoading(true);
    setError(null);
    const key = getOrCreateCommandKey();
    try {
      const guestSession = getStoredGuestSession();
      const res = await api.archiveDesign(key, designId, expectedVersion, guestSession, isAuthenticated);
      resetCommandKey();
      return res;
    } catch (err) {
      if (err?.status === 401) {
        clearStoredGuestSession();
      }
      setError(getErrorMessage(err, t));
      throw err;
    } finally {
      setLoading(false);
    }
  }, [api, isAuthenticated, getOrCreateCommandKey, resetCommandKey, t]);

  const executeAttach = useCallback(async (designId, attachPayload, expectedVersion) => {
    setLoading(true);
    setError(null);
    const key = getOrCreateCommandKey();
    try {
      const guestSession = getStoredGuestSession();
      const res = await api.attachToBooking(key, designId, attachPayload, expectedVersion, guestSession, isAuthenticated);
      resetCommandKey();
      return res;
    } catch (err) {
      if (err?.status === 401) {
        clearStoredGuestSession();
      }
      setError(getErrorMessage(err, t));
      throw err;
    } finally {
      setLoading(false);
    }
  }, [api, isAuthenticated, getOrCreateCommandKey, resetCommandKey, t]);

  const executeRestore = useCallback(async (designId, expectedVersion) => {
    setLoading(true);
    setError(null);
    const key = getOrCreateCommandKey();
    try {
      const guestSession = getStoredGuestSession();
      const res = await api.restoreDesign(key, designId, expectedVersion, guestSession, isAuthenticated);
      resetCommandKey();
      return res;
    } catch (err) {
      if (err?.status === 401) {
        clearStoredGuestSession();
      }
      setError(getErrorMessage(err));
      throw err;
    } finally {
      setLoading(false);
    }
  }, [api, isAuthenticated, getOrCreateCommandKey, resetCommandKey]);

  return {
    loading,
    error,
    clearError: () => setError(null),
    executeCreate,
    executeSave,
    executeEvaluate,
    executeArchive,
    executeRestore,
    executeAttach,
  };
}
