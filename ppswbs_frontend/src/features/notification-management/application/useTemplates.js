import { useCallback, useEffect, useRef, useState } from 'react';
import { useT } from '../../../shared/i18n/I18nProvider';
import { notificationMessages } from '../messages';
import { useNotificationApi } from './useNotificationApi';

export function useTemplates() {
  const api = useNotificationApi();
  const t = useT(notificationMessages);
  const tRef = useRef(t);
  tRef.current = t;
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
      setError(err?.message || tRef.current('tplList.loadError'));
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
          setError(err?.message || tRef.current('tplList.loadError'));
          setLoading(false);
        }
      });
    return () => {
      active = false;
    };
  }, [api]);

  return { templates, loading, error, reload };
}

