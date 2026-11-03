import { useMemo } from 'react';
import { apiBaseUrl } from '../../../shared/api/http';
import { useHttp } from '../../../shared/api/useHttp';
import { catalogueApi, mediaUrl } from '../infrastructure/catalogueApi';

/** Same-origin address of a public product image (the UI never builds backend URLs itself). */
export const publicImageUrl = url => mediaUrl(apiBaseUrl, url);

export function useCatalogueApi() {
  const request = useHttp();
  return useMemo(() => catalogueApi(request), [request]);
}
