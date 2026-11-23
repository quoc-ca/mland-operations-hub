import React from 'react';
import { Link } from 'react-router';
import { useTemplates } from '../application/useTemplates';
import { useT, useEnumLabel } from '../../../shared/i18n/I18nProvider';
import { sharedMessages } from '../../../shared/ui/messages';
import { notificationMessages } from '../messages';
import './notification.css';

function templateStatusBadge(status, label) {
  switch (status) {
    case 'ACTIVE':
      return <span className="nm-badge nm-badge-active">{label(status)}</span>;
    case 'DRAFT':
      return <span className="nm-badge nm-badge-draft">{label(status)}</span>;
    case 'EXTERNAL':
      return <span className="nm-badge nm-badge-external">{label(status)}</span>;
    default:
      return <span className="nm-badge">{status}</span>;
  }
}

export function TemplateListPage() {
  const t = useT(notificationMessages);
  const ts = useT(sharedMessages);
  const statusLabel = useEnumLabel(notificationMessages, 'tplStatus');
  const { templates, loading, error, reload } = useTemplates();

  return (
    <div className="ota-workspace">
      
      <main className="ota-main">
        <div className="nm-container">
          <div className="nm-header">
            <div className="nm-title-group">
              <p>{t('tplList.subtitle')}</p>
            </div>
            <div className="nm-actions">
              <button
                type="button"
                className="nm-btn"
                onClick={reload}
                disabled={loading}
                title={t('tplList.refreshTitle')}
              >
                {loading ? ts('loading') : t('refresh')}
              </button>
            </div>
          </div>

          {error && (
            <div className="nm-alert nm-alert-danger" role="alert">
              <span>{error}</span>
              <button
                type="button"
                className="nm-btn nm-btn-sm"
                style={{ marginLeft: '1rem' }}
                onClick={reload}
              >
                {ts('retry')}
              </button>
            </div>
          )}

          <div className="nm-card">
            {loading ? (
              <div style={{ padding: '3rem 0', textAlign: 'center', color: '#64748b' }} role="status">
                {t('tplList.loading')}
              </div>
            ) : templates.length === 0 ? (
              <div style={{ padding: '3rem 0', textAlign: 'center', color: '#64748b' }} role="status">
                {t('tplList.empty')}
              </div>
            ) : (
              <div className="nm-table-container">
                <table className="nm-table">
                  <thead>
                    <tr>
                      <th>{t('tplList.colCode')}</th>
                      <th>{t('tplList.colName')}</th>
                      <th>{t('tplList.colModule')}</th>
                      <th>{t('tplList.colStatus')}</th>
                      <th>{t('tplList.colActive')}</th>
                      <th>{t('tplList.colDraft')}</th>
                      <th style={{ textAlign: 'right' }}>{t('col.actions')}</th>
                    </tr>
                  </thead>
                  <tbody>
                    {templates.map((tpl) => (
                      <tr key={tpl.code}>
                        <td>
                          <strong>{tpl.code}</strong>
                        </td>
                        <td>
                          <div>{tpl.name}</div>
                          <small style={{ color: '#64748b' }}>{tpl.description}</small>
                        </td>
                        <td>
                          <span className="nm-tag">{tpl.owningModule}</span>
                        </td>
                        <td>{templateStatusBadge(tpl.lifecycleStatus, statusLabel)}</td>
                        <td>
                          {tpl.activeVersion ? (
                            <span style={{ fontWeight: 600, color: '#15803d' }}>
                              v{tpl.activeVersion.versionNumber}
                            </span>
                          ) : (
                            <span style={{ color: '#94a3b8' }}>—</span>
                          )}
                        </td>
                        <td>
                          {tpl.latestDraftVersionNumber ? (
                            <span style={{ fontWeight: 600, color: '#b45309' }}>
                              v{tpl.latestDraftVersionNumber}
                            </span>
                          ) : (
                            <span style={{ color: '#94a3b8' }}>{t('tplList.noDraft')}</span>
                          )}
                        </td>
                        <td style={{ textAlign: 'right' }}>
                          <Link
                            to={`/admin/notifications/templates/${tpl.code}`}
                            className="nm-btn nm-btn-sm"
                            aria-label={t('tplList.detailAria', { code: tpl.code })}
                          >
                            {t('tplList.viewDetail')}
                          </Link>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        </div>
      </main>
    </div>
  );
}

export default TemplateListPage;

