import { useMemo } from 'react';
import { useHttp } from '../../../shared/api/useHttp';
import { retailApi } from './retailApi';

export function useRetailApi() {
  const request = useHttp();
  return useMemo(() => retailApi(request), [request]);
}
