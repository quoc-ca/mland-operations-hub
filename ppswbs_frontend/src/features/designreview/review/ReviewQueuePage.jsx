import React, { useState } from 'react';
import { Link } from 'react-router';
import { useT } from '../../../shared/i18n/I18nProvider';
import { ErrorNotice, Loading, useResource } from '../../../shared/ui/components';
import { useDesignReviewApi } from '../api/useDesignReviewApi';
import { designReviewMessages } from '../messages';
import { formatDateTime } from '../rules';

export default function ReviewQueuePage() {
  const t = useT(designReviewMessages);
  const api = useDesignReviewApi();
  const [statusFilter, setStatusFilter] = useState('');

  const { loading, error, data, reload } = useResource(
    () => api.getReviewQueue(statusFilter || undefined),
    [statusFilter]
  );

  // Auto-polling every 4 seconds + instant sync on focus/visibility
  React.useEffect(() => {
    const interval = setInterval(() => {
      if (document.visibilityState === 'visible') {
        reload();
      }
    }, 4000);

    const handleFocus = () => reload();
    const handleVisibility = () => {
      if (document.visibilityState === 'visible') reload();
    };

    window.addEventListener('focus', handleFocus);
    document.addEventListener('visibilitychange', handleVisibility);

    return () => {
      clearInterval(interval);
      window.removeEventListener('focus', handleFocus);
      document.removeEventListener('visibilitychange', handleVisibility);
    };
  }, [reload]);

  const items = Array.isArray(data) ? data : (data?.items || []);

  const getStatusBadge = (status) => {
    switch (status) {
      case 'ACCEPTED':
        return <span className="badge-tag badge-success">{t('status.ACCEPTED')}</span>;
      case 'REJECTED':
        return <span className="badge-tag badge-danger" style={{ background: '#fee2e2', color: '#991b1b', border: '1px solid #f87171' }}>{t('status.REJECTED')}</span>;
      case 'CLARIFICATION_REQUIRED':
        return <span className="badge-tag badge-status">{t('status.CLARIFICATION_REQUIRED')}</span>;
      case 'NEED_REVIEW':
      default:
        return <span className="badge-tag badge-warning">{t('status.NEED_REVIEW')}</span>;
    }
  };

  return (
    <div className="design-container">
      <header className="page-header" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '1rem' }}>
        <div>
          <h2>Danh sách yêu cầu chế tác</h2>
          <p className="muted">{t('queue.subtitle')}</p>
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
          <label htmlFor="status-filter" style={{ fontSize: '0.875rem', fontWeight: 500, color: '#374151' }}>
            Lọc trạng thái:
          </label>
          <select
            id="status-filter"
            data-testid="status-filter"
            value={statusFilter}
            onChange={(e) => setStatusFilter(e.target.value)}
            style={{
              padding: '0.4rem 0.75rem',
              borderRadius: '6px',
              border: '1px solid #d1d5db',
              fontSize: '0.875rem',
              backgroundColor: '#fff',
            }}
          >
            <option value="">Tất cả trạng thái</option>
            <option value="NEED_REVIEW">Chờ thẩm duyệt</option>
            <option value="ACCEPTED">Đã chấp nhận thiết kế</option>
            <option value="REJECTED">Từ chối thiết kế</option>
            <option value="CLARIFICATION_REQUIRED">Cần làm rõ thêm</option>
          </select>
        </div>
      </header>

      {loading && <Loading />}
      <ErrorNotice error={error} messages={designReviewMessages} onReload={reload} />

      {!loading && !error && items.length === 0 && (
        <div className="empty-state" role="status">
          <p>{t('queue.empty')}</p>
        </div>
      )}

      {!loading && !error && items.length > 0 && (
        <div style={{ overflowX: 'auto' }}>
          <table className="table-queue" style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left' }}>
            <thead>
              <tr style={{ borderBottom: '2px solid #cbd5e1' }}>
                <th style={{ padding: '0.75rem' }}>{t('queue.colRef')}</th>
                <th style={{ padding: '0.75rem' }}>Thời gian gửi</th>
                <th style={{ padding: '0.75rem' }}>Người gửi</th>
                <th style={{ padding: '0.75rem' }}>Mô tả ý tưởng</th>
                <th style={{ padding: '0.75rem' }}>Số ảnh</th>
                <th style={{ padding: '0.75rem' }}>{t('queue.colStatus')}</th>
                <th style={{ padding: '0.75rem' }}>{t('queue.colAction')}</th>
              </tr>
            </thead>
            <tbody>
              {items.map(item => {
                const imgCount = Array.isArray(item.images) ? item.images.length : (item.imageCount || 0);
                return (
                  <tr key={item.id} style={{ borderBottom: '1px solid #e2e8f0' }}>
                    <td style={{ padding: '0.75rem' }}>
                      <strong>{item.requestReference || item.id}</strong>
                      {item.requestReference && item.id !== item.requestReference && (
                        <div><code style={{ fontSize: '0.75rem' }}>{item.id}</code></div>
                      )}
                    </td>
                    <td style={{ padding: '0.75rem', fontSize: '0.85rem' }} className="muted">
                      {formatDateTime(item.createdAt)}
                    </td>
                    <td style={{ padding: '0.75rem' }}>
                      {item.senderEmail ? (
                        <span style={{ fontSize: '0.875rem', fontWeight: 500, color: '#1e293b' }} title={item.senderEmail}>
                          {item.senderEmail}
                        </span>
                      ) : (
                        <span className="badge-tag badge-status">
                          Khách vãng lai
                        </span>
                      )}
                    </td>
                    <td style={{ padding: '0.75rem', maxWidth: '240px', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }} title={item.description}>
                      {item.description || '—'}
                    </td>
                    <td style={{ padding: '0.75rem' }}>
                      {imgCount > 0 ? `📷 ${imgCount} ảnh` : '—'}
                    </td>
                    <td style={{ padding: '0.75rem' }}>
                      {getStatusBadge(item.reviewStatus)}
                    </td>
                    <td style={{ padding: '0.75rem' }}>
                      <Link
                        to={`/design-review-requests/${encodeURIComponent(item.id)}`}
                        className="button outline small"
                      >
                        {item.reviewStatus === 'NEED_REVIEW' ? t('queue.reviewAction') : 'Xem chi tiết'}
                      </Link>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
