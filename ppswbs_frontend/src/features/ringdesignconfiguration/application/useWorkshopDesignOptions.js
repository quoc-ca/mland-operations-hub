import { useState, useEffect, useCallback } from 'react';
import { useWorkshopRingDesignApi } from '../api/useWorkshopRingDesignApi';
import { SUGGESTED_RING_MODELS } from '../domain/suggestedModels';

export function useWorkshopDesignOptions(packageReference, branchReference) {
  const api = useWorkshopRingDesignApi();
  const [options, setOptions] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const fetchOptions = useCallback(async () => {
    if (!packageReference) return;
    setLoading(true);
    setError(null);
    try {
      const data = await api.getDesignOptions(packageReference, branchReference);
      if (data) {
        const existingRefs = new Set((data.models || []).map((m) => m.modelReference));
        const additional = SUGGESTED_RING_MODELS.filter((m) => !existingRefs.has(m.modelReference));
        setOptions({
          ...data,
          models: [...additional, ...(data.models || [])],
        });
      } else {
        setOptions(data);
      }
    } catch (err) {
      setError(err);
    } finally {
      setLoading(false);
    }
  }, [api, packageReference, branchReference]);

  useEffect(() => {
    fetchOptions();
  }, [fetchOptions]);

  return { options, loading, error, refetch: fetchOptions };
}

