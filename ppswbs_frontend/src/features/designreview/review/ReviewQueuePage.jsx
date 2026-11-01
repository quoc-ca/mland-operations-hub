import React from 'react';
import { Link } from 'react-router';
import { useT } from '../../../shared/i18n/I18nProvider';
import { ErrorNotice, Loading, useResource } from '../../../shared/ui/components';
import { useDesignReviewApi } from '../api/useDesignReviewApi';
import { designReviewMessages } from '../messages';

export default function ReviewQueuePage() {
  const t = useT(designReviewMessages);
  const api = useDesignReviewApi();

  const { loading, error, data, reload } = useResource(
    () => api.getReviewQueue(),
    []
  );

  return (
    <div className="design-container">
      <header className="page-header">
        <h1>{t('queue.title')}</h1>
        <p className="muted">{t('queue.subtitle')}</p>
      </header>

      {loading && <Loading />}
      <ErrorNotice error={error} messages={designReviewMessages} onReload={reload} />

      {!loading && !error && (!data || data.length === 0) && (
        <div className="empty-state" role="status">
          <p>{t('queue.empty')}</p>
        </div>
      )}

      {!loading && !error && data && data.length > 0 && (
        <div style={{ overflowX: 'auto' }}>
          <table className="table-queue">
            <thead>
              <tr>
                <th>{t('queue.colId')}</th>
                <th>{t('queue.colRef')}</th>
                <th>{t('queue.colVersion')}</th>
                <th>{t('queue.colStatus')}</th>
                <th>{t('queue.colCatalogue')}</th>
                <th>{t('queue.colAction')}</th>
              </tr>
            </thead>
            <tbody>
              {data.map(item => (
                <tr key={item.id}>
                  <td><code>{item.id}</code></td>
                  <td>{item.requestReference || '—'}</td>
                  <td>#{item.requestVersion || 1}</td>
                  <td>
                    <span className="badge-tag badge-warning">
                      {item.reviewStatus || 'NEED_REVIEW'}
                    </span>
                  </td>
                  <td>{item.catalogueReference || '—'}</td>
                  <td>
                    <Link
                      to={`/design-review-requests/${encodeURIComponent(item.id)}`}
                      className="button outline small"
                    >
                      {t('queue.reviewAction')}
                    </Link>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}

