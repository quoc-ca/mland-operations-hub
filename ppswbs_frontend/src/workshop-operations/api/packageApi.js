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
    transition: (packageId, targetState, key) => command(
      `/api/v1/workshop-packages/${encodeURIComponent(packageId)}/lifecycle-transitions`,
      'POST',
      { targetState },
      key,
    ),
  };
}
