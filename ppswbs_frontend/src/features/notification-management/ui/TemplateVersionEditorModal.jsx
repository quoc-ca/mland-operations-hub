import React, { useEffect, useState } from 'react';

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
      setError('Tiêu đề thư không được để trống.');
      return;
    }
    if (!htmlTemplate.trim() && !plainTextTemplate.trim()) {
      setError('Vui lòng nhập nội dung HTML hoặc Plain Text.');
      return;
    }
    if (invalidPlaceholders.length > 0) {
      setError(
        `Các biến sau không nằm trong danh sách cho phép: ${invalidPlaceholders.join(
          ', '
        )}`
      );
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
      setError(err?.message || 'Lưu bản nháp thất bại. Dữ liệu đã được giữ nguyên.');
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
              ? `Chỉnh sửa bản nháp v${initialVersion.versionNumber} (${template?.name})`
              : `Tạo bản nháp mới (${template?.name})`}
          </h3>
          <button
            type="button"
            className="nm-modal-close"
            onClick={onClose}
            disabled={saving}
            aria-label="Đóng"
          >
            &times;
          </button>
        </div>

        <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', flex: 1, overflow: 'hidden' }}>
          <div className="nm-modal-body">
            {error && <div className="nm-alert nm-alert-danger" role="alert">{error}</div>}

            <div style={{ marginBottom: '1rem' }}>
              <label style={{ fontSize: '0.8125rem', fontWeight: 600, color: '#475569' }}>
                Biến cho phép (Allowed Placeholders):
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
                Cảnh báo: Biến không hợp lệ sẽ bị máy chủ từ chối:
                <strong> {invalidPlaceholders.map((p) => `{{${p}}}`).join(', ')}</strong>
              </div>
            )}

            <div style={{ marginBottom: '1rem' }}>
              <label
                htmlFor="subjectTemplate"
                style={{ display: 'block', fontSize: '0.875rem', fontWeight: 600, marginBottom: '0.25rem' }}
              >
                Tiêu đề email (Subject) *
              </label>
              <input
                id="subjectTemplate"
                type="text"
                className="nm-input"
                style={{ width: '100%' }}
                value={subjectTemplate}
                onChange={(e) => setSubjectTemplate(e.target.value)}
                placeholder="Ví dụ: [Mland] Xác nhận đặt lịch #{{bookingCode}}"
                disabled={saving}
                required
              />
            </div>

            <div style={{ marginBottom: '1rem' }}>
              <label
                htmlFor="htmlTemplate"
                style={{ display: 'block', fontSize: '0.875rem', fontWeight: 600, marginBottom: '0.25rem' }}
              >
                Nội dung HTML (HTML Template)
              </label>
              <textarea
                id="htmlTemplate"
                className="nm-textarea"
                style={{ width: '100%', height: '140px' }}
                value={htmlTemplate}
                onChange={(e) => setHtmlTemplate(e.target.value)}
                placeholder="<p>Chào {{customerName}}, ...</p>"
                disabled={saving}
              />
            </div>

            <div style={{ marginBottom: '0.5rem' }}>
              <label
                htmlFor="plainTextTemplate"
                style={{ display: 'block', fontSize: '0.875rem', fontWeight: 600, marginBottom: '0.25rem' }}
              >
                Nội dung Plain Text (Fallback)
              </label>
              <textarea
                id="plainTextTemplate"
                className="nm-textarea"
                style={{ width: '100%', height: '100px' }}
                value={plainTextTemplate}
                onChange={(e) => setPlainTextTemplate(e.target.value)}
                placeholder="Chào {{customerName}}, ..."
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
              Hủy
            </button>
            <button
              type="submit"
              className="nm-btn nm-btn-primary"
              disabled={saving || invalidPlaceholders.length > 0}
            >
              {saving ? 'Đang lưu…' : isEdit ? 'Cập nhật bản nháp' : 'Lưu bản nháp'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}

export default TemplateVersionEditorModal;

