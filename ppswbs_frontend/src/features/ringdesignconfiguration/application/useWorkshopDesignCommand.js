import { useState, useCallback, useRef } from 'react';
import { useWorkshopRingDesignApi } from '../api/useWorkshopRingDesignApi';
import { setStoredGuestSession, getStoredGuestSession } from '../api/workshopRingDesignApi';
import { newCommandId } from '../../../shared/api/http';
import { getErrorMessage } from '../domain/designErrors';

export function useWorkshopDesignCommand(isAuthenticated = false) {
  const api = useWorkshopRingDesignApi();
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
      resetCommandKey();
      return res;
    } catch (err) {
      setError(getErrorMessage(err));
      throw err;
    } finally {
      setLoading(false);
    }
  }, [api, isAuthenticated, getOrCreateCommandKey, resetCommandKey]);

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
      setError(getErrorMessage(err));
      throw err;
    } finally {
      setLoading(false);
    }
  }, [api, isAuthenticated, getOrCreateCommandKey, resetCommandKey]);

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
      setError(getErrorMessage(err));
      throw err;
    } finally {
      setLoading(false);
    }
  }, [api, isAuthenticated, getOrCreateCommandKey, resetCommandKey]);

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
      setError(getErrorMessage(err));
      throw err;
    } finally {
      setLoading(false);
    }
  }, [api, isAuthenticated, getOrCreateCommandKey, resetCommandKey]);

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
    executeAttach,
  };
}
