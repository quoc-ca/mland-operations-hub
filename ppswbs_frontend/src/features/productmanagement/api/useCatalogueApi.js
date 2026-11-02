import { useMemo } from 'react';
import { useHttp } from '../../../shared/api/useHttp';
import { catalogueApi } from './catalogueApi';

export function useCatalogueApi() {
  const request = useHttp();
  return useMemo(() => catalogueApi(request), [request]);
}
