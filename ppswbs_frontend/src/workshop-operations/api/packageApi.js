import { newCommandId } from '../../shared/api/http';

export function createWorkshopPackageApi(http) {
  const command = (path, method, body, key) => http(path, {
    method,
    body,
    idempotencyKey: key || newCommandId(),
  });

  return {
    listPublished: () => http('/api/v1/workshop-packages'),
    listManaged: (state) => http(`/api/v1/workshop-package-management/packages${state ? `?state=${encodeURIComponent(state)}` : ''}`),
    createDraft: (body, key) => command('/api/v1/workshop-packages', 'POST', body, key),
    update: (packageId, body, key) => command(`/api/v1/workshop-packages/${encodeURIComponent(packageId)}`, 'PATCH', body, key),
    // Cover image (003.2 FR-073): uploaded through the catalogue media store, then referenced by the package.
    uploadImage: (file, key) => {
      const form = new FormData();
      form.append('file', file);
      return http('/api/v1/catalogue-management/media-uploads', { method: 'POST', idempotencyKey: key, form });
    },
    previewImage: packageId => http(
      `/api/v1/workshop-package-management/packages/${encodeURIComponent(packageId)}/image`, { responseType: 'blob' }),
    transition: (packageId, targetState, key) => command(
      `/api/v1/workshop-packages/${encodeURIComponent(packageId)}/lifecycle-transitions`,
      'POST',
      { targetState },
      key,
    ),
  };
}
