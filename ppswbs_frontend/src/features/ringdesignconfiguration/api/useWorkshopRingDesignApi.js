import { useMemo } from 'react';
import { useHttp } from '../../../shared/api/useHttp';
import { workshopRingDesignApi } from './workshopRingDesignApi';

export function useWorkshopRingDesignApi() {
  const request = useHttp();
  return useMemo(() => workshopRingDesignApi(request), [request]);
}

