import { useMemo } from 'react';
import { useHttp } from '../../../shared/api/useHttp';
import { designReviewApi } from './designReviewApi';

/** Feature-004 hook bound to the current Firebase session. */
export function useDesignReviewApi() {
  const request = useHttp();
  return useMemo(() => designReviewApi(request), [request]);
}

