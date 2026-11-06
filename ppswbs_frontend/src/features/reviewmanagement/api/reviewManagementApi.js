// Feature-011 API adapter. The OpenAPI contract is the source of truth; the
// backend remains authoritative for eligibility, ownership and permissions.
export function reviewManagementApi(request) {
  const command = (path, method, body, key, version) => request(path, {
    method,
    body,
    idempotencyKey: key,
    headers: version === undefined ? undefined : { 'If-Match-Version': String(version) },
    auth: true,
  });

  return {
    lookupGuestEligibility(key, body) {
      return request('/api/v1/review-eligibility/lookup', {
        method: 'POST', body, idempotencyKey: key, auth: false,
      });
    },
    listEligibleSources() {
      return request('/api/v1/reviews/eligible-sources', { method: 'GET', auth: true });
    },
    createReview(key, body, auth = false) {
      return request('/api/v1/reviews', { method: 'POST', body, idempotencyKey: key, auth });
    },
    listPublicReviews({ targetType, targetReference, packageId, rating, cursor, limit } = {}) {
      return request('/api/v1/reviews', {
        method: 'GET', query: { targetType, targetReference, packageId, rating, cursor, limit }, auth: false,
      });
    },
    getReview(reviewId, auth = false) {
      return request(`/api/v1/reviews/${encodeURIComponent(reviewId)}`, { method: 'GET', auth });
    },
    editReview(key, reviewId, body, version) {
      return command(`/api/v1/reviews/${encodeURIComponent(reviewId)}`, 'PATCH', body, key, version);
    },
    listModeration() {
      return request('/api/v1/review-moderation', { method: 'GET', auth: true });
    },
    changeVisibility(key, reviewId, body, version) {
      return command(`/api/v1/reviews/${encodeURIComponent(reviewId)}/visibility`, 'PATCH', body, key, version);
    },
    createReply(key, reviewId, body) {
      return command(`/api/v1/reviews/${encodeURIComponent(reviewId)}/reply`, 'POST', body, key);
    },
    editReply(key, reviewId, body, version) {
      return command(`/api/v1/reviews/${encodeURIComponent(reviewId)}/reply`, 'PATCH', body, key, version);
    },
  };
}
