import { useCallback, useEffect, useState } from 'react';
import { useNotificationApi } from './useNotificationApi';

export function useTemplateVersions(code) {
  const api = useNotificationApi();
  const [template, setTemplate] = useState(null);
  const [loading, setLoading] = useState(Boolean(code));
  const [error, setError] = useState(null);
  const [actionLoading, setActionLoading] = useState(false);
  const [actionError, setActionError] = useState(null);

  const reload = useCallback(async () => {
    if (!code) return;
    setLoading(true);
    setError(null);
    try {
      const data = await api.getTemplate(code);
      setTemplate(data);
    } catch (err) {
      setError(err?.message || 'Không thể tải thông tin mẫu thông báo');
    } finally {
      setLoading(false);
    }
  }, [api, code]);

  useEffect(() => {
    if (!code) {
      setTemplate(null);
      setLoading(false);
      return;
    }
    let active = true;
    setLoading(true);
    setError(null);
    api
      .getTemplate(code)
      .then((data) => {
        if (active) {
          setTemplate(data);
          setLoading(false);
        }
      })
      .catch((err) => {
        if (active) {
          setError(err?.message || 'Không thể tải thông tin mẫu thông báo');
          setLoading(false);
        }
      });
    return () => {
      active = false;
    };
  }, [api, code]);

  const createDraft = useCallback(
    async (body) => {
      setActionLoading(true);
      setActionError(null);
      try {
        const result = await api.createDraftVersion(code, body);
        await reload();
        return result;
      } catch (err) {
        setActionError(err?.message || 'Không thể tạo bản nháp mới');
        throw err;
      } finally {
        setActionLoading(false);
      }
    },
    [api, code, reload]
  );

  const updateDraft = useCallback(
    async (versionNumber, body) => {
      setActionLoading(true);
      setActionError(null);
      try {
        const result = await api.updateDraftVersion(code, versionNumber, body);
        await reload();
        return result;
      } catch (err) {
        setActionError(err?.message || 'Không thể cập nhật bản nháp');
        throw err;
      } finally {
        setActionLoading(false);
      }
    },
    [api, code, reload]
  );

  const activateVersion = useCallback(
    async (versionNumber) => {
      setActionLoading(true);
      setActionError(null);
      try {
        const result = await api.activateVersion(code, versionNumber);
        await reload();
        return result;
      } catch (err) {
        setActionError(err?.message || 'Không thể kích hoạt phiên bản');
        throw err;
      } finally {
        setActionLoading(false);
      }
    },
    [api, code, reload]
  );

  const previewVersion = useCallback(
    async (versionNumber, sampleData) => {
      setActionLoading(true);
      setActionError(null);
      try {
        return await api.previewTemplate(code, { versionNumber, sampleData });
      } catch (err) {
        setActionError(err?.message || 'Không thể xem trước nội dung');
        throw err;
      } finally {
        setActionLoading(false);
      }
    },
    [api, code]
  );

  return {
    template,
    loading,
    error,
    actionLoading,
    actionError,
    reload,
    createDraft,
    updateDraft,
    activateVersion,
    previewVersion,
  };
}

