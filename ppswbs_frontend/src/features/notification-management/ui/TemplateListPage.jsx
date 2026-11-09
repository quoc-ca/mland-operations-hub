import React from 'react';
import { Link } from 'react-router';
import { useTemplates } from '../application/useTemplates';
import OperationsSidebar from '../../../shared/ui/OperationsSidebar';
import './notification.css';

function templateStatusBadge(status) {
  switch (status) {
    case 'ACTIVE':
      return <span className="nm-badge nm-badge-active">Đang kích hoạt</span>;
    case 'DRAFT':
      return <span className="nm-badge nm-badge-draft">Bản nháp</span>;
    case 'EXTERNAL':
      return <span className="nm-badge nm-badge-external">Bên ngoài (Firebase)</span>;
    default:
      return <span className="nm-badge">{status}</span>;
  }
}

export function TemplateListPage() {
  const { templates, loading, error, reload } = useTemplates();

  return (
    <div className="ota-workspace">
      <OperationsSidebar />
      <main className="ota-main">
        <div className="nm-container">
          <div className="nm-header">
            <div className="nm-title-group">
              <h1>Quản lý mẫu thông báo email</h1>
              <p>Danh mục các mẫu thư giao dịch hệ thống và trạng thái phiên bản kích hoạt</p>
            </div>
            <div className="nm-actions">
              <button
                type="button"
                className="nm-btn"
                onClick={reload}
                disabled={loading}
                title="Tải lại danh sách"
              >
                {loading ? 'Đang tải…' : 'Làm mới'}
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
                Thử lại
              </button>
            </div>
          )}

          <div className="nm-card">
            {loading ? (
              <div style={{ padding: '3rem 0', textAlign: 'center', color: '#64748b' }} role="status">
                Đang tải danh sách mẫu thông báo…
              </div>
            ) : templates.length === 0 ? (
              <div style={{ padding: '3rem 0', textAlign: 'center', color: '#64748b' }} role="status">
                Chưa có mẫu thông báo nào được đăng ký.
              </div>
            ) : (
              <div className="nm-table-container">
                <table className="nm-table">
                  <thead>
                    <tr>
                      <th>Mã mẫu</th>
                      <th>Tên mẫu thư</th>
                      <th>Phân hệ</th>
                      <th>Trạng thái</th>
                      <th>Phiên bản hiện tại</th>
                      <th>Bản nháp mới nhất</th>
                      <th style={{ textAlign: 'right' }}>Thao tác</th>
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
                        <td>{templateStatusBadge(tpl.lifecycleStatus)}</td>
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
                            <span style={{ color: '#94a3b8' }}>Không có</span>
                          )}
                        </td>
                        <td style={{ textAlign: 'right' }}>
                          <Link
                            to={`/admin/notifications/templates/${tpl.code}`}
                            className="nm-btn nm-btn-sm"
                            aria-label={`Chi tiết ${tpl.code}`}
                          >
                            Xem chi tiết &rarr;
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

