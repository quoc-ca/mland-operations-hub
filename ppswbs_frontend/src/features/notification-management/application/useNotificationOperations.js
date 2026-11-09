import { useCallback, useState } from 'react';
import { useNotificationApi } from './useNotificationApi';

export function useNotificationOperations() {
  const api = useNotificationApi();
  const [operating, setOperating] = useState(false);
  const [operationError, setOperationError] = useState(null);

  const resend = useCallback(
    async (id, reason, duplicateRiskAccepted = false) => {
      setOperating(true);
      setOperationError(null);
      try {
        const result = await api.resendNotificationRequest(id, {
          reason,
          duplicateRiskAccepted,
        });
        return result;
      } catch (err) {
        setOperationError(err?.message || 'Gửi lại thông báo thất bại');
        throw err;
      } finally {
        setOperating(false);
      }
    },
    [api]
  );

  const cancel = useCallback(
    async (id, reason) => {
      setOperating(true);
      setOperationError(null);
      try {
        const result = await api.cancelNotificationRequest(id, { reason });
        return result;
      } catch (err) {
        setOperationError(err?.message || 'Hủy yêu cầu thông báo thất bại');
        throw err;
      } finally {
        setOperating(false);
      }
    },
    [api]
  );

  const redeliver = useCallback(
    async (id, reason) => {
      setOperating(true);
      setOperationError(null);
      try {
        const result = await api.redeliverNotificationRequest(id, { reason });
        return result;
      } catch (err) {
        setOperationError(err?.message || 'Gửi lại với liên kết mới thất bại');
        throw err;
      } finally {
        setOperating(false);
      }
    },
    [api]
  );

  return {
    operating,
    operationError,
    resend,
    cancel,
    redeliver,
  };
}

