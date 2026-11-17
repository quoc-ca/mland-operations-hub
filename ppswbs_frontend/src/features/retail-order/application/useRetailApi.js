import { useMemo } from 'react';
import { useHttp } from '../../../shared/api/useHttp';
import { retailApi } from '../infrastructure/retailApi';

export function useRetailApi() {
  const request = useHttp();
  return useMemo(() => retailApi(request), [request]);
}
