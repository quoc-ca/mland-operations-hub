import { newCommandId } from '../../shared/api/http';

export function createWorkshopAddOnApi(http) {
  const command = (path, method, body, key) => http(path, {
    method,
    body,
    idempotencyKey: key || newCommandId(),
  });
  const base = (packageId, locationId) => `/api/v1/workshop-packages/${encodeURIComponent(packageId)}/locations/${encodeURIComponent(locationId)}/add-ons`;
  const managementBase = (packageId, locationId) => `/api/v1/workshop-package-management/packages/${encodeURIComponent(packageId)}/locations/${encodeURIComponent(locationId)}/add-ons`;
  return {
    list: (packageId, locationId) => http(base(packageId, locationId)),
    listManaged: (packageId, locationId) => http(managementBase(packageId, locationId)),
    create: (packageId, locationId, body, key) => command(base(packageId, locationId), 'POST', body, key),
    update: (packageId, locationId, reference, body, key) => command(`${base(packageId, locationId)}/${encodeURIComponent(reference)}`, 'PATCH', body, key),
    transition: (packageId, locationId, reference, targetState, key) => command(`${base(packageId, locationId)}/${encodeURIComponent(reference)}/lifecycle-transitions`, 'POST', { targetState }, key),
  };
}
