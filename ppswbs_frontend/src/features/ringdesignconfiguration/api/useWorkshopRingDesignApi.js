import { useMemo } from 'react';
import { useHttp } from '../../../shared/api/useHttp';
import { workshopRingDesignApi, getStoredGuestSession, setStoredGuestSession, clearStoredGuestSession } from './workshopRingDesignApi';

export { getStoredGuestSession, setStoredGuestSession, clearStoredGuestSession };

export function useWorkshopRingDesignApi() {
  const request = useHttp();
  return useMemo(() => workshopRingDesignApi(request), [request]);
}

