import { useCallback, useRef, useState } from 'react';
import { useT } from '../../../shared/i18n/I18nProvider';
import { notificationMessages } from '../messages';
import { useNotificationApi } from './useNotificationApi';

export function useNotificationOperations() {
  const api = useNotificationApi();
  const t = useT(notificationMessages);
  const tRef = useRef(t);
  tRef.current = t;
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
        setOperationError(err?.message || tRef.current('operation.resendFailed'));
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
        setOperationError(err?.message || tRef.current('operation.cancelFailed'));
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
        setOperationError(err?.message || tRef.current('operation.redeliverFailed'));
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

