// Feature-013 Consultation Chat API Client
export function consultationChatApi(request) {
  const command = (path, method, body, key, version) => request(path, {
    method,
    body,
    idempotencyKey: key,
    headers: version === undefined ? undefined : { 'If-Match-Version': String(version) },
    auth: true,
  });

  return {
    getContextOptions() {
      return request(`/api/v1/consultations/context-options?_t=${Date.now()}`, { method: 'GET', auth: true });
    },

    createConversation(key, body) {
      return command('/api/v1/consultations', 'POST', body, key);
    },

    listConversations({ status, cursor, limit } = {}) {
      return request('/api/v1/consultations', {
        method: 'GET',
        query: { status, cursor, limit, _t: Date.now() },
        auth: true,
      });
    },

    hideForMember(key, version, conversationReference) {
      return command(`/api/v1/consultations/${encodeURIComponent(conversationReference)}`, 'DELETE', undefined, key, version);
    },

    getConversation(conversationReference) {
      return request(`/api/v1/consultations/${encodeURIComponent(conversationReference)}?_t=${Date.now()}`, {
        method: 'GET',
        auth: true,
      });
    },

    sendMessage(key, conversationReference, body) {
      return command(`/api/v1/consultations/${encodeURIComponent(conversationReference)}/messages`, 'POST', body, key);
    },

    claim(key, version, conversationReference) {
      return command(`/api/v1/consultations/${encodeURIComponent(conversationReference)}/claim`, 'POST', undefined, key, version);
    },

    release(key, version, conversationReference) {
      return command(`/api/v1/consultations/${encodeURIComponent(conversationReference)}/release`, 'POST', undefined, key, version);
    },

    assign(key, version, conversationReference, body) {
      return command(`/api/v1/consultations/${encodeURIComponent(conversationReference)}/assign`, 'POST', body, key, version);
    },

    recordRead(conversationReference, body) {
      return request(`/api/v1/consultations/${encodeURIComponent(conversationReference)}/read`, {
        method: 'POST',
        body,
        auth: true,
      });
    },

    close(key, version, conversationReference) {
      return command(`/api/v1/consultations/${encodeURIComponent(conversationReference)}/close`, 'POST', undefined, key, version);
    },

    reopen(key, version, conversationReference) {
      return command(`/api/v1/consultations/${encodeURIComponent(conversationReference)}/reopen`, 'POST', undefined, key, version);
    },
  };
}
