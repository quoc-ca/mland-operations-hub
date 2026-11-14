import React, { useState } from 'react';
import { Link, useParams } from 'react-router';
import { useTemplateVersions } from '../application/useTemplateVersions';
import TemplateVersionEditorModal from './TemplateVersionEditorModal';
import TemplatePreviewModal from './TemplatePreviewModal';
import OperationsSidebar from '../../../shared/ui/OperationsSidebar';
import './notification.css';

function versionStatusBadge(status) {
  switch (status) {
    case 'ACTIVE':
      return <span className="nm-badge nm-badge-active">Đang áp dụng</span>;
    case 'DRAFT':
      return <span className="nm-badge nm-badge-draft">Bản nháp</span>;
    case 'DEACTIVATED':
      return <span className="nm-badge nm-badge-deactivated">Lưu trữ</span>;
    default:
      return <span className="nm-badge">{status}</span>;
  }
}

export function TemplateDetailView() {
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
      setFeedback(`Đã cập nhật bản nháp v${selectedVersionForEdit.versionNumber}.`);
    } else {
      const res = await createDraft(body);
      setFeedback(`Đã tạo bản nháp mới v${res?.versionNumber || ''}.`);
    }
  };

  const handleConfirmActivate = async () => {
    if (!activationTarget) return;
    try {
      await activateVersion(activationTarget.versionNumber);
      setFeedback(`Đã kích hoạt phiên bản v${activationTarget.versionNumber} thành công.`);
      setActivationTarget(null);
    } catch (err) {
      // Error handled by hook
    }
  };

  const activeVersion = template?.versions?.find((v) => v.status === 'ACTIVE');

  return (
    <div className="ota-workspace">
      <OperationsSidebar />
      <main className="ota-main">
        <div className="nm-container">
          <div style={{ marginBottom: '1rem' }}>
            <Link to="/admin/notifications/templates" className="nm-btn nm-btn-sm">
              &larr; Quay lại danh sách mẫu thư
            </Link>
          </div>

          {loading ? (
            <div style={{ padding: '3rem 0', textAlign: 'center', color: '#64748b' }} role="status">
              Đang tải dữ liệu mẫu thư…
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
                Thử lại
              </button>
            </div>
          ) : template ? (
            <>
              <div className="nm-header">
                <div className="nm-title-group">
                  <h1>{template.name}</h1>
                  <p>Mã mẫu: <code>{template.code}</code> &bull; Phân hệ: <strong>{template.owningModule}</strong></p>
                </div>
                <div className="nm-actions">
                  <button
                    type="button"
                    className="nm-btn nm-btn-primary"
                    onClick={handleOpenCreateDraft}
                    disabled={actionLoading}
                  >
                    + Tạo bản nháp mới
                  </button>
                  <button
                    type="button"
                    className="nm-btn"
                    onClick={reload}
                    disabled={loading || actionLoading}
                  >
                    Làm mới
                  </button>
                </div>
              </div>

              {feedback && <div className="nm-alert nm-alert-success" role="status">{feedback}</div>}
              {actionError && <div className="nm-alert nm-alert-danger" role="alert">{actionError}</div>}

              {/* Template Meta Card */}
              <div className="nm-card">
                <div className="nm-card-body">
                  <h3 style={{ margin: '0 0 0.5rem 0', fontSize: '1rem', fontWeight: 600 }}>
                    Mô tả nghiệp vụ & Biến cho phép
                  </h3>
                  <p style={{ margin: '0 0 1rem 0', color: '#475569', fontSize: '0.875rem' }}>
                    {template.description || 'Không có mô tả.'}
                  </p>

                  <div style={{ fontSize: '0.8125rem', fontWeight: 600, color: '#334155', marginBottom: '0.25rem' }}>
                    Danh sách biến được phép (Allowed Placeholders):
                  </div>
                  <div className="nm-tag-list">
                    {Array.isArray(template.allowedPlaceholders) && template.allowedPlaceholders.length > 0 ? (
                      template.allowedPlaceholders.map((ph) => (
                        <span key={ph} className="nm-tag">
                          {`{{${ph}}}`}
                        </span>
                      ))
                    ) : (
                      <span style={{ color: '#94a3b8', fontStyle: 'italic' }}>Không có biến nào</span>
                    )}
                  </div>
                </div>
              </div>

              {/* Version History Table */}
              <div className="nm-card">
                <div className="nm-card-body" style={{ paddingBottom: 0 }}>
                  <h3 style={{ margin: '0 0 0.5rem 0', fontSize: '1rem', fontWeight: 600 }}>
                    Lịch sử các phiên bản nội dung (Version History)
                  </h3>
                </div>
                <div className="nm-table-container">
                  <table className="nm-table">
                    <thead>
                      <tr>
                        <th>Phiên bản</th>
                        <th>Trạng thái</th>
                        <th>Tiêu đề email (Subject)</th>
                        <th>Tạo bởi / Ngày tạo</th>
                        <th>Kích hoạt lúc</th>
                        <th style={{ textAlign: 'right' }}>Thao tác</th>
                      </tr>
                    </thead>
                    <tbody>
                      {Array.isArray(template.versions) && template.versions.length > 0 ? (
                        template.versions.map((ver) => (
                          <tr key={ver.versionNumber}>
                            <td>
                              <strong>v{ver.versionNumber}</strong>
                            </td>
                            <td>{versionStatusBadge(ver.status)}</td>
                            <td style={{ maxWidth: '300px', wordBreak: 'break-word' }}>
                              {ver.subjectTemplate}
                            </td>
                            <td>
                              <div>{ver.createdBy || 'Hệ thống'}</div>
                              <small style={{ color: '#64748b' }}>
                                {ver.createdAt ? new Date(ver.createdAt).toLocaleDateString('vi-VN') : '—'}
                              </small>
                            </td>
                            <td>
                              {ver.activatedAt ? (
                                <div>
                                  <span style={{ fontSize: '0.8125rem' }}>
                                    {new Date(ver.activatedAt).toLocaleDateString('vi-VN')}
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
                                  Xem trước
                                </button>
                                {ver.status === 'DRAFT' && (
                                  <>
                                    <button
                                      type="button"
                                      className="nm-btn nm-btn-sm"
                                      onClick={() => handleOpenEditDraft(ver)}
                                      disabled={actionLoading}
                                    >
                                      Chỉnh sửa
                                    </button>
                                    <button
                                      type="button"
                                      className="nm-btn nm-btn-sm nm-btn-primary"
                                      onClick={() => setActivationTarget(ver)}
                                      disabled={actionLoading}
                                    >
                                      Kích hoạt
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
                            Chưa có phiên bản nội dung nào cho mẫu này.
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
              <h3 id="activate-modal-title">Xác nhận kích hoạt phiên bản v{activationTarget.versionNumber}</h3>
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
                Kích hoạt phiên bản <strong>v{activationTarget.versionNumber}</strong> sẽ thay thế phiên bản
                {activeVersion ? <strong> v{activeVersion.versionNumber} </strong> : ' hiện tại '}
                đang hoạt động. Tất cả email mới phát sinh từ các giao dịch sau thời điểm này sẽ lập tức áp dụng phiên bản mới này.
              </div>
              <p style={{ fontSize: '0.875rem', color: '#334155', margin: 0 }}>
                Bạn có chắc chắn muốn kích hoạt phiên bản này?
              </p>
            </div>
            <div className="nm-modal-footer">
              <button
                type="button"
                className="nm-btn"
                onClick={() => setActivationTarget(null)}
                disabled={actionLoading}
              >
                Hủy
              </button>
              <button
                type="button"
                className="nm-btn nm-btn-primary"
                onClick={handleConfirmActivate}
                disabled={actionLoading}
              >
                {actionLoading ? 'Đang kích hoạt…' : 'Xác nhận kích hoạt'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

export default TemplateDetailView;

