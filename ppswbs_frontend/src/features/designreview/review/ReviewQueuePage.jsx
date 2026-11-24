import React from 'react';
import { Link } from 'react-router';
import { useFormat, useT } from '../../../shared/i18n/I18nProvider';
import { ErrorNotice, Loading, useResource } from '../../../shared/ui/components';
import { useDesignReviewApi } from '../api/useDesignReviewApi';
import { designReviewMessages } from '../messages';

export default function ReviewQueuePage() {
  const t = useT(designReviewMessages);
  const fmt = useFormat();
  const api = useDesignReviewApi();

  const { loading, error, data, reload } = useResource(
    () => api.getReviewQueue(),
    []
  );

  const items = Array.isArray(data) ? data : (data?.items || []);

  return (
    <div className="design-container">
      <header className="page-header">
        <p className="muted">{t('queue.subtitle')}</p>
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
                <th style={{ padding: '0.75rem' }}>{t('queue.colSubmittedAt')}</th>
                <th style={{ padding: '0.75rem' }}>{t('queue.colSubmitter')}</th>
                <th style={{ padding: '0.75rem' }}>{t('queue.colDescription')}</th>
                <th style={{ padding: '0.75rem' }}>{t('queue.colImages')}</th>
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
                      {fmt.dateTime(item.createdAt)}
                    </td>
                    <td style={{ padding: '0.75rem' }}>
                      <span className="badge-tag badge-status">
                        {item.ownershipType === 'GUEST' || item.guestSessionId ? t('queue.guest') : t('rv.member')}
                      </span>
                    </td>
                    <td style={{ padding: '0.75rem', maxWidth: '240px', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }} title={item.description}>
                      {item.description || '—'}
                    </td>
                    <td style={{ padding: '0.75rem' }}>
                      {imgCount > 0 ? `📷 ${t('queue.imageCount', { count: imgCount })}` : '—'}
                    </td>
                    <td style={{ padding: '0.75rem' }}>
                      <span className="badge-tag badge-warning">
                        {t('status.NEED_REVIEW')}
                      </span>
                    </td>
                    <td style={{ padding: '0.75rem' }}>
                      <Link
                        to={`/design-review-requests/${encodeURIComponent(item.id)}`}
                        className="button outline small"
                      >
                        {t('queue.reviewAction')}
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
