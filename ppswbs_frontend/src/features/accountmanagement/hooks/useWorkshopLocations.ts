import { useEffect, useState } from 'react';
import { useStableT } from './useStableT';
import {
  accountManagementApiClient,
  WorkshopLocation,
} from '../api/AccountManagementApiClient';

/**
 * Reads the real workshop locations (003.2 `GET /api/v1/workshop-locations`, Admin read-only)
 * so role/location assignment uses the same ids the backend stores.
 */
export function useWorkshopLocations() {
  const t = useStableT();
  const [locations, setLocations] = useState<WorkshopLocation[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    accountManagementApiClient
      .listWorkshopLocations()
      .then(result => {
        if (!cancelled) setLocations(Array.isArray(result) ? result : []);
      })
      .catch(err => {
        if (!cancelled) setError(err?.message || t('hook.locationsError'));
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  return { locations, loading, error };
}
