import React, { useState } from 'react';
import { Link, useParams } from 'react-router';
import { useTemplateVersions } from '../application/useTemplateVersions';
import TemplateVersionEditorModal from './TemplateVersionEditorModal';
import TemplatePreviewModal from './TemplatePreviewModal';
import { renderRich } from './richText';
import { useT, useEnumLabel, useFormat } from '../../../shared/i18n/I18nProvider';
import { sharedMessages } from '../../../shared/ui/messages';
import { notificationMessages } from '../messages';
import './notification.css';

function versionStatusBadge(status, label) {
  switch (status) {
    case 'ACTIVE':
      return <span className="nm-badge nm-badge-active">{label(status)}</span>;
    case 'DRAFT':
      return <span className="nm-badge nm-badge-draft">{label(status)}</span>;
    case 'DEACTIVATED':
      return <span className="nm-badge nm-badge-deactivated">{label(status)}</span>;
    default:
      return <span className="nm-badge">{status}</span>;
  }
}

export function TemplateDetailView() {
  const t = useT(notificationMessages);
  const ts = useT(sharedMessages);
  const statusLabel = useEnumLabel(notificationMessages, 'verStatus');
  const fmt = useFormat();
  const { code } = useParams();
  const {
    template,
    loading,
    error,
    actionLoading,
    actionError,
    createDraft,
    updateDraft,
    activateVersion,
    previewVersion,
    reload,
  } = useTemplateVersions(code);

  const [editorOpen, setEditorOpen] = useState(false);
  const [selectedVersionForEdit, setSelectedVersionForEdit] = useState(null);

  const [previewOpen, setPreviewOpen] = useState(false);
  const [previewVersionNumber, setPreviewVersionNumber] = useState(null);

  const [activationTarget, setActivationTarget] = useState(null);
  const [feedback, setFeedback] = useState(null);

  const handleOpenCreateDraft = () => {
    setSelectedVersionForEdit(null);
    setEditorOpen(true);
  };

  const handleOpenEditDraft = (version) => {
    setSelectedVersionForEdit(version);
    setEditorOpen(true);
  };

  const handleOpenPreview = (versionNumber) => {
    setPreviewVersionNumber(versionNumber);
    setPreviewOpen(true);
  };

  const handleSaveDraft = async (body) => {
    if (selectedVersionForEdit) {
      await updateDraft(selectedVersionForEdit.versionNumber, body);
      setFeedback(t('detail.draftUpdated', { version: selectedVersionForEdit.versionNumber }));
    } else {
      const res = await createDraft(body);
      setFeedback(t('detail.draftCreated', { version: res?.versionNumber || '' }));
    }
  };

  const handleConfirmActivate = async () => {
    if (!activationTarget) return;
    try {
      await activateVersion(activationTarget.versionNumber);
      setFeedback(t('detail.activated', { version: activationTarget.versionNumber }));
      setActivationTarget(null);
    } catch (err) {
      // Error handled by hook
    }
  };

  const activeVersion = template?.versions?.find((v) => v.status === 'ACTIVE');

  return (
    <div className="wsp-workspace">
      
      <main className="wsp-main">
        <div className="nm-container">
          <div style={{ marginBottom: '1rem' }}>
            <Link to="/admin/notifications/templates" className="nm-btn nm-btn-sm">
              {t('detail.back')}
            </Link>
          </div>

          {loading ? (
            <div style={{ padding: '3rem 0', textAlign: 'center', color: '#64748b' }} role="status">
              {t('detail.loading')}
            </div>
          ) : error ? (
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
          ) : template ? (
            <>
              <div className="nm-header">
                <div className="nm-title-group">
                  <h1>{template.name}</h1>
                  <p>{renderRich(t('detail.meta', { code: template.code, module: template.owningModule }))}</p>
                </div>
                <div className="nm-actions">
                  <button
                    type="button"
                    className="nm-btn nm-btn-primary"
                    onClick={handleOpenCreateDraft}
                    disabled={actionLoading}
                  >
                    {t('detail.createDraft')}
                  </button>
                  <button
                    type="button"
                    className="nm-btn"
                    onClick={reload}
                    disabled={loading || actionLoading}
                  >
                    {t('refresh')}
                  </button>
                </div>
              </div>

              {feedback && <div className="nm-alert nm-alert-success" role="status">{feedback}</div>}
              {actionError && <div className="nm-alert nm-alert-danger" role="alert">{actionError}</div>}

              {/* Template Meta Card */}
              <div className="nm-card">
                <div className="nm-card-body">
                  <h3 style={{ margin: '0 0 0.5rem 0', fontSize: '1rem', fontWeight: 600 }}>
                    {t('detail.descTitle')}
                  </h3>
                  <p style={{ margin: '0 0 1rem 0', color: '#475569', fontSize: '0.875rem' }}>
                    {template.description || t('detail.noDescription')}
                  </p>

                  <div style={{ fontSize: '0.8125rem', fontWeight: 600, color: '#334155', marginBottom: '0.25rem' }}>
                    {t('detail.allowedList')}
                  </div>
                  <div className="nm-tag-list">
                    {Array.isArray(template.allowedPlaceholders) && template.allowedPlaceholders.length > 0 ? (
                      template.allowedPlaceholders.map((ph) => (
                        <span key={ph} className="nm-tag">
                          {`{{${ph}}}`}
                        </span>
                      ))
                    ) : (
                      <span style={{ color: '#94a3b8', fontStyle: 'italic' }}>{t('detail.noPlaceholders')}</span>
                    )}
                  </div>
                </div>
              </div>

              {/* Version History Table */}
              <div className="nm-card">
                <div className="nm-card-body" style={{ paddingBottom: 0 }}>
                  <h3 style={{ margin: '0 0 0.5rem 0', fontSize: '1rem', fontWeight: 600 }}>
                    {t('detail.historyTitle')}
                  </h3>
                </div>
                <div className="nm-table-container">
                  <table className="nm-table">
                    <thead>
                      <tr>
                        <th>{t('detail.colVersion')}</th>
                        <th>{t('detail.colStatus')}</th>
                        <th>{t('detail.colSubject')}</th>
                        <th>{t('detail.colCreated')}</th>
                        <th>{t('detail.colActivated')}</th>
                        <th style={{ textAlign: 'right' }}>{t('col.actions')}</th>
                      </tr>
                    </thead>
                    <tbody>
                      {Array.isArray(template.versions) && template.versions.length > 0 ? (
                        template.versions.map((ver) => (
                          <tr key={ver.versionNumber}>
                            <td>
                              <strong>v{ver.versionNumber}</strong>
                            </td>
                            <td>{versionStatusBadge(ver.status, statusLabel)}</td>
                            <td style={{ maxWidth: '300px', wordBreak: 'break-word' }}>
                              {ver.subjectTemplate}
                            </td>
                            <td>
                              <div>{ver.createdBy || t('detail.system')}</div>
                              <small style={{ color: '#64748b' }}>
                                {fmt.dateTime(ver.createdAt)}
                              </small>
                            </td>
                            <td>
                              {ver.activatedAt ? (
                                <div>
                                  <span style={{ fontSize: '0.8125rem' }}>
                                    {fmt.dateTime(ver.activatedAt)}
                                  </span>
                                  <small style={{ display: 'block', color: '#64748b' }}>
                                    {ver.activatedBy}
                                  </small>
                                </div>
                              ) : (
                                <span style={{ color: '#94a3b8' }}>—</span>
                              )}
                            </td>
                            <td style={{ textAlign: 'right' }}>
                              <div style={{ display: 'inline-flex', gap: '0.5rem' }}>
                                <button
                                  type="button"
                                  className="nm-btn nm-btn-sm"
                                  onClick={() => handleOpenPreview(ver.versionNumber)}
                                  disabled={actionLoading}
                                >
                                  {t('detail.preview')}
                                </button>
                                {ver.status === 'DRAFT' && (
                                  <>
                                    <button
                                      type="button"
                                      className="nm-btn nm-btn-sm"
                                      onClick={() => handleOpenEditDraft(ver)}
                                      disabled={actionLoading}
                                    >
                                      {t('detail.edit')}
                                    </button>
                                    <button
                                      type="button"
                                      className="nm-btn nm-btn-sm nm-btn-primary"
                                      onClick={() => setActivationTarget(ver)}
                                      disabled={actionLoading}
                                    >
                                      {t('detail.activate')}
                                    </button>
                                  </>
                                )}
                              </div>
                            </td>
                          </tr>
                        ))
                      ) : (
                        <tr>
                          <td colSpan="6" style={{ textAlign: 'center', color: '#64748b', padding: '2rem 0' }}>
                            {t('detail.noVersions')}
                          </td>
                        </tr>
                      )}
                    </tbody>
                  </table>
                </div>
              </div>
            </>
          ) : null}
        </div>
      </main>

      {/* Editor Modal */}
      <TemplateVersionEditorModal
        isOpen={editorOpen}
        onClose={() => setEditorOpen(false)}
        template={template}
        initialVersion={selectedVersionForEdit}
        onSave={handleSaveDraft}
      />

      {/* Preview Modal */}
      <TemplatePreviewModal
        isOpen={previewOpen}
        onClose={() => setPreviewOpen(false)}
        template={template}
        versionNumber={previewVersionNumber}
        onPreview={previewVersion}
      />

      {/* Activation Confirmation Modal */}
      {activationTarget && (
        <div
          className="nm-modal-backdrop"
          role="dialog"
          aria-modal="true"
          aria-labelledby="activate-modal-title"
        >
          <div className="nm-modal">
            <div className="nm-modal-header">
              <h3 id="activate-modal-title">{t('detail.activateTitle', { version: activationTarget.versionNumber })}</h3>
              <button
                type="button"
                className="nm-modal-close"
                onClick={() => setActivationTarget(null)}
                disabled={actionLoading}
              >
                &times;
              </button>
            </div>
            <div className="nm-modal-body">
              <div className="nm-alert nm-alert-warning" role="alert">
                {renderRich(
                  activeVersion
                    ? t('detail.activateWarn', { next: activationTarget.versionNumber, current: activeVersion.versionNumber })
                    : t('detail.activateWarnNoCurrent', { next: activationTarget.versionNumber })
                )}
              </div>
              <p style={{ fontSize: '0.875rem', color: '#334155', margin: 0 }}>
                {t('detail.activateAsk')}
              </p>
            </div>
            <div className="nm-modal-footer">
              <button
                type="button"
                className="nm-btn"
                onClick={() => setActivationTarget(null)}
                disabled={actionLoading}
              >
                {ts('cancel')}
              </button>
              <button
                type="button"
                className="nm-btn nm-btn-primary"
                onClick={handleConfirmActivate}
                disabled={actionLoading}
              >
                {actionLoading ? t('detail.activating') : t('detail.activateConfirm')}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

export default TemplateDetailView;

