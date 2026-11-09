import { useCallback, useEffect, useState } from 'react';
import { useNotificationApi } from './useNotificationApi';

export function useNotificationRequests({ initialAutoRefresh = false } = {}) {
  const api = useNotificationApi();
  const [filters, setFilters] = useState({
    templateCode: '',
    deliveryState: '',
    businessEventReference: '',
    from: '',
    to: '',
  });
  const [page, setPage] = useState(0);
  const [pageSize] = useState(20);
  const [data, setData] = useState({ content: [], totalElements: 0, totalPages: 0 });
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [autoRefresh, setAutoRefresh] = useState(initialAutoRefresh);

  const fetchRequests = useCallback(
    async (showLoading = true) => {
      if (showLoading) setLoading(true);
      setError(null);
      try {
        const query = { page, size: pageSize };
        if (filters.templateCode) query.templateCode = filters.templateCode;
        if (filters.deliveryState) query.deliveryState = filters.deliveryState;
        if (filters.businessEventReference) query.businessEventReference = filters.businessEventReference;
        if (filters.from) query.from = filters.from;
        if (filters.to) query.to = filters.to;

        const response = await api.listNotificationRequests(query);
        setData({
          content: Array.isArray(response?.content) ? response.content : [],
          totalElements: response?.totalElements ?? 0,
          totalPages: response?.totalPages ?? 0,
        });
      } catch (err) {
        setError(err?.message || 'Không thể tải nhật ký gửi thông báo');
      } finally {
        if (showLoading) setLoading(false);
      }
    },
    [api, page, pageSize, filters]
  );

  useEffect(() => {
    fetchRequests(true);
  }, [fetchRequests]);

  // Auto-refresh interval (30s)
  useEffect(() => {
    if (!autoRefresh) return undefined;
    const interval = setInterval(() => {
      fetchRequests(false);
    }, 30000);
    return () => clearInterval(interval);
  }, [autoRefresh, fetchRequests]);

  const setFilter = useCallback((key, value) => {
    setFilters((prev) => ({ ...prev, [key]: value }));
    setPage(0); // Reset to first page on filter change
  }, []);

  const resetFilters = useCallback(() => {
    setFilters({
      templateCode: '',
      deliveryState: '',
      businessEventReference: '',
      from: '',
      to: '',
    });
    setPage(0);
  }, []);

  return {
    data,
    page,
    setPage,
    pageSize,
    filters,
    setFilter,
    resetFilters,
    autoRefresh,
    setAutoRefresh,
    loading,
    error,
    reload: () => fetchRequests(true),
  };
}

