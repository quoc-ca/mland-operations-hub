import React, { useEffect, useState } from 'react';
import { useT } from '../../../shared/i18n/I18nProvider';
import { sharedMessages } from '../../../shared/ui/messages';
import { notificationMessages } from '../messages';

/**
 * Extracts all {{placeholder}} variables from a string.
 */
function extractPlaceholders(text) {
  if (!text) return [];
  const matches = text.match(/\{\{([a-zA-Z0-9_]+)\}\}/g);
  if (!matches) return [];
  return Array.from(new Set(matches.map((m) => m.replace(/\{\{|\}\}/g, '').trim())));
}

export function TemplateVersionEditorModal({
  isOpen,
  onClose,
  template,
  initialVersion = null,
  onSave,
}) {
  const t = useT(notificationMessages);
  const ts = useT(sharedMessages);
  const [subjectTemplate, setSubjectTemplate] = useState('');
  const [htmlTemplate, setHtmlTemplate] = useState('');
  const [plainTextTemplate, setPlainTextTemplate] = useState('');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState(null);

  const isEdit = Boolean(initialVersion);

  useEffect(() => {
    if (initialVersion) {
      setSubjectTemplate(initialVersion.subjectTemplate || '');
      setHtmlTemplate(initialVersion.htmlTemplate || '');
      setPlainTextTemplate(initialVersion.plainTextTemplate || '');
    } else {
      setSubjectTemplate('');
      setHtmlTemplate('');
      setPlainTextTemplate('');
    }
    setError(null);
  }, [initialVersion, isOpen]);

  useEffect(() => {
    function handleKeyDown(e) {
      if (e.key === 'Escape' && isOpen && !saving) {
        onClose();
      }
    }
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [isOpen, saving, onClose]);

  if (!isOpen) return null;

  const allowed = Array.isArray(template?.allowedPlaceholders)
    ? template.allowedPlaceholders
    : [];

  const used = Array.from(
    new Set([
      ...extractPlaceholders(subjectTemplate),
      ...extractPlaceholders(htmlTemplate),
      ...extractPlaceholders(plainTextTemplate),
    ])
  );

  const invalidPlaceholders = used.filter((p) => !allowed.includes(p));

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!subjectTemplate.trim()) {
      setError(t('editor.subjectRequired'));
      return;
    }
    if (!htmlTemplate.trim() && !plainTextTemplate.trim()) {
      setError(t('editor.bodyRequired'));
      return;
    }
    if (invalidPlaceholders.length > 0) {
      setError(t('editor.invalidPlaceholders', { list: invalidPlaceholders.join(', ') }));
      return;
    }

    setSaving(true);
    setError(null);
    try {
      await onSave({
        subjectTemplate: subjectTemplate.trim(),
        htmlTemplate: htmlTemplate.trim(),
        plainTextTemplate: plainTextTemplate.trim(),
      });
      onClose();
    } catch (err) {
      setError(err?.message || t('editor.saveFailed'));
    } finally {
      setSaving(false);
    }
  };

  return (
    <div
      className="nm-modal-backdrop"
      role="dialog"
      aria-modal="true"
      aria-labelledby="editor-modal-title"
    >
      <div className="nm-modal nm-modal-lg">
        <div className="nm-modal-header">
          <h3 id="editor-modal-title">
            {isEdit
              ? t('editor.titleEdit', { version: initialVersion.versionNumber, name: template?.name })
              : t('editor.titleNew', { name: template?.name })}
          </h3>
          <button
            type="button"
            className="nm-modal-close"
            onClick={onClose}
            disabled={saving}
            aria-label={ts('close')}
          >
            &times;
          </button>
        </div>

        <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', flex: 1, overflow: 'hidden' }}>
          <div className="nm-modal-body">
            {error && <div className="nm-alert nm-alert-danger" role="alert">{error}</div>}

            <div style={{ marginBottom: '1rem' }}>
              <label style={{ fontSize: '0.8125rem', fontWeight: 600, color: '#475569' }}>
                {t('editor.allowed')}
              </label>
              <div className="nm-tag-list">
                {allowed.map((ph) => (
                  <span key={ph} className="nm-tag">
                    {`{{${ph}}}`}
                  </span>
                ))}
              </div>
            </div>

            {invalidPlaceholders.length > 0 && (
              <div className="nm-alert nm-alert-warning" role="alert">
                {t('editor.invalidWarning')}
                <strong> {invalidPlaceholders.map((p) => `{{${p}}}`).join(', ')}</strong>
              </div>
            )}

            <div style={{ marginBottom: '1rem' }}>
              <label
                htmlFor="subjectTemplate"
                style={{ display: 'block', fontSize: '0.875rem', fontWeight: 600, marginBottom: '0.25rem' }}
              >
                {t('editor.subject')}
              </label>
              <input
                id="subjectTemplate"
                type="text"
                className="nm-input"
                style={{ width: '100%' }}
                value={subjectTemplate}
                onChange={(e) => setSubjectTemplate(e.target.value)}
                placeholder={t('editor.subjectPlaceholder')}
                disabled={saving}
                required
              />
            </div>

            <div style={{ marginBottom: '1rem' }}>
              <label
                htmlFor="htmlTemplate"
                style={{ display: 'block', fontSize: '0.875rem', fontWeight: 600, marginBottom: '0.25rem' }}
              >
                {t('editor.html')}
              </label>
              <textarea
                id="htmlTemplate"
                className="nm-textarea"
                style={{ width: '100%', height: '140px' }}
                value={htmlTemplate}
                onChange={(e) => setHtmlTemplate(e.target.value)}
                placeholder={t('editor.htmlPlaceholder')}
                disabled={saving}
              />
            </div>

            <div style={{ marginBottom: '0.5rem' }}>
              <label
                htmlFor="plainTextTemplate"
                style={{ display: 'block', fontSize: '0.875rem', fontWeight: 600, marginBottom: '0.25rem' }}
              >
                {t('editor.plain')}
              </label>
              <textarea
                id="plainTextTemplate"
                className="nm-textarea"
                style={{ width: '100%', height: '100px' }}
                value={plainTextTemplate}
                onChange={(e) => setPlainTextTemplate(e.target.value)}
                placeholder={t('editor.plainPlaceholder')}
                disabled={saving}
              />
            </div>
          </div>

          <div className="nm-modal-footer">
            <button
              type="button"
              className="nm-btn"
              onClick={onClose}
              disabled={saving}
            >
              {ts('cancel')}
            </button>
            <button
              type="submit"
              className="nm-btn nm-btn-primary"
              disabled={saving || invalidPlaceholders.length > 0}
            >
              {saving ? t('editor.saving') : isEdit ? t('editor.update') : t('editor.save')}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}

export default TemplateVersionEditorModal;

