import { useCallback, useEffect, useState } from 'react';
import { useNotificationApi } from './useNotificationApi';

export function useTemplates() {
  const api = useNotificationApi();
  const [templates, setTemplates] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const reload = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await api.listTemplates();
      setTemplates(Array.isArray(data) ? data : []);
    } catch (err) {
      setError(err?.message || 'Không thể tải danh sách mẫu thông báo');
    } finally {
      setLoading(false);
    }
  }, [api]);

  useEffect(() => {
    let active = true;
    setLoading(true);
    setError(null);
    api
      .listTemplates()
      .then((data) => {
        if (active) {
          setTemplates(Array.isArray(data) ? data : []);
          setLoading(false);
        }
      })
      .catch((err) => {
        if (active) {
          setError(err?.message || 'Không thể tải danh sách mẫu thông báo');
          setLoading(false);
        }
      });
    return () => {
      active = false;
    };
  }, [api]);

  return { templates, loading, error, reload };
}

