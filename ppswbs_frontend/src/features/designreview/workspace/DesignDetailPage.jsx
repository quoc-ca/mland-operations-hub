import React from 'react';
import { Link, useParams } from 'react-router';
import { useT } from '../../../shared/i18n/I18nProvider';
import { ErrorNotice, Loading, useResource } from '../../../shared/ui/components';
import { useDesignReviewApi } from '../api/useDesignReviewApi';
import { designReviewMessages } from '../messages';
import { formatVnd } from '../rules';

export default function DesignDetailPage() {
  const { designRequestId } = useParams();
  const t = useT(designReviewMessages);
  const api = useDesignReviewApi();

  const { loading, error, data, reload } = useResource(
    () => api.getDesignRequest(designRequestId),
    [designRequestId]
  );

  if (loading) return <div className="design-container"><Loading /></div>;
  if (error) {
    return (
      <div className="design-container">
        <ErrorNotice error={error} messages={designReviewMessages} onReload={reload} />
      </div>
    );
  }
  if (!data) return null;

  const candidateFeatures = data.candidateFeatures || {};
  const imageQuality = data.imageQuality || {};
  const confidence = data.confidence || {};

  return (
    <div className="design-container">
      <header className="page-header">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <div>
            <h1>{t('detail.title')}</h1>
            <p className="muted">
              {t('detail.reference', { ref: data.requestReference || data.id })}
            </p>
          </div>
          <span className="badge-tag badge-status">
            {t('detail.version', { version: data.requestVersion || 1 })}
          </span>
        </div>
      </header>

      <div className="stack" style={{ gap: '1rem', marginBottom: '1.5rem' }}>
        <div style={{ display: 'flex', gap: '0.75rem', flexWrap: 'wrap' }}>
          <span className="badge-tag badge-status">
            {t('detail.statusLabel', { status: data.requestStatus || 'UNKNOWN' })}
          </span>
          <span className={`badge-tag ${data.analysisStatus === 'SUCCEEDED' ? 'badge-success' : data.analysisStatus === 'FAILED' ? 'badge-danger' : 'badge-warning'}`}>
            {t('detail.analysisStatusLabel', { status: data.analysisStatus || 'PENDING' })}
          </span>
          <span className={`badge-tag ${data.reviewStatus === 'ACCEPTED' ? 'badge-success' : data.reviewStatus === 'REJECTED' ? 'badge-danger' : 'badge-warning'}`}>
            {t('detail.reviewStatusLabel', { status: data.reviewStatus || 'NEED_REVIEW' })}
          </span>
          {data.nextAction && (
            <span className="badge-tag badge-status">
              {t('detail.nextActionLabel', { action: data.nextAction })}
            </span>
          )}
        </div>

        {data.safeFailureCode && (
          <div className="notice error">
            <p><strong>{t('detail.safeRetry')}</strong> (Mã: {data.safeFailureCode})</p>
          </div>
        )}
      </div>

      {data.description && (
        <section className="card" style={{ marginBottom: '1.5rem', padding: '1.25rem', border: '1px solid #e2e8f0', borderRadius: '6px' }}>
          <h3>{t('workspace.descriptionLabel')}</h3>
          <p style={{ whiteSpace: 'pre-wrap' }}>{data.description}</p>
          {data.catalogueReference && (
            <p className="muted small">
              {t('workspace.catalogueRefLabel')}: <code>{data.catalogueReference}</code>
            </p>
          )}
        </section>
      )}

      {/* Candidate features */}
      {Object.keys(candidateFeatures).length > 0 && (
        <section style={{ marginBottom: '1.5rem' }}>
          <h3>{t('detail.featuresTitle')}</h3>
          <div className="features-grid">
            {Object.entries(candidateFeatures).map(([key, val]) => (
              <div key={key} className="feature-card">
                <h4>{key}</h4>
                <p>
                  {key.toLowerCase().includes('value') || key.toLowerCase().includes('price')
                    ? formatVnd(val)
                    : typeof val === 'object'
                    ? JSON.stringify(val)
                    : String(val)}
                </p>
              </div>
            ))}
          </div>
        </section>
      )}

      {/* Image quality and model confidence */}
      {(Object.keys(imageQuality).length > 0 || Object.keys(confidence).length > 0) && (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))', gap: '1rem', marginBottom: '1.5rem' }}>
          {Object.keys(imageQuality).length > 0 && (
            <div className="card" style={{ padding: '1rem', border: '1px solid #e2e8f0', borderRadius: '6px' }}>
              <h4>{t('detail.imageQualityTitle')}</h4>
              <ul>
                {Object.entries(imageQuality).map(([k, v]) => (
                  <li key={k}><strong>{k}:</strong> {String(v)}</li>
                ))}
              </ul>
            </div>
          )}
          {Object.keys(confidence).length > 0 && (
            <div className="card" style={{ padding: '1rem', border: '1px solid #e2e8f0', borderRadius: '6px' }}>
              <h4>{t('detail.confidenceTitle')}</h4>
              <ul>
                {Object.entries(confidence).map(([k, v]) => (
                  <li key={k}><strong>{k}:</strong> {String(v)}</li>
                ))}
              </ul>
            </div>
          )}
        </div>
      )}

      {data.safeExplanation && (
        <section className="card" style={{ marginBottom: '1.5rem', padding: '1rem', border: '1px solid #e2e8f0', borderRadius: '6px' }}>
          <h4>{t('detail.explanationTitle')}</h4>
          <p className="muted">{data.safeExplanation}</p>
        </section>
      )}

      <div className="actions" style={{ marginTop: '2rem' }}>
        <Link to="/design-requests/new" className="button primary">
          {t('detail.createNew')}
        </Link>
      </div>
    </div>
  );
}

