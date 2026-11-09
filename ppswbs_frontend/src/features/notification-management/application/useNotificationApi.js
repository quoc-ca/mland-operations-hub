import { useMemo } from 'react';
import { useHttp } from '../../../shared/api/useHttp';
import { notificationApiClient } from '../infrastructure/notificationApiClient';

export function useNotificationApi() {
  const request = useHttp();
  return useMemo(() => notificationApiClient(request), [request]);
}

