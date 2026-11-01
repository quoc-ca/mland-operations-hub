import { useMemo } from 'react';
import { useHttp } from '../../../shared/api/useHttp';
import { loyaltyApi } from './loyaltyApi';

export function useLoyaltyApi() {
  const request = useHttp();
  return useMemo(() => loyaltyApi(request), [request]);
}
