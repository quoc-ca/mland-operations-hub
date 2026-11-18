import { useMemo } from 'react';
import { useHttp } from '../../../shared/api/useHttp';
import { consultationChatApi } from './consultationChatApi';

export function useConsultationChatApi() {
  const request = useHttp();
  return useMemo(() => consultationChatApi(request), [request]);
}
