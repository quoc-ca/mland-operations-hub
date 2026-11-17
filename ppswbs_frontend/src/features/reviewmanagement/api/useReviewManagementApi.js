import { useMemo } from 'react';
import { useHttp } from '../../../shared/api/useHttp';
import { reviewManagementApi } from './reviewManagementApi';

export function useReviewManagementApi() {
  const request = useHttp();
  return useMemo(() => reviewManagementApi(request), [request]);
}
