import { newCommandId } from '../../shared/api/http';

export function createPackageAvailabilityApi(http) {
  return {
    readMatrix: () => http('/api/v1/workshop-package-availability'),
    setAvailability: (packageId, locationId, state, version, key) => http(
      `/api/v1/workshop-packages/${encodeURIComponent(packageId)}/locations/${encodeURIComponent(locationId)}/availability`,
      {
        method: 'PUT',
        body: { state, version },
        idempotencyKey: key || newCommandId(),
      },
    ),
  };
}
