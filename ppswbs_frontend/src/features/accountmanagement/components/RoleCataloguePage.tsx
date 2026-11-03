import React, { useState, useEffect } from 'react';
import {
  accountManagementApiClient,
  RoleCatalogueEntry,
} from '../api/AccountManagementApiClient';
import '../accountmanagement.css';

export const RoleCataloguePage: React.FC = () => {
  const [roles, setRoles] = useState<RoleCatalogueEntry[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    const fetchRoles = async () => {
      setLoading(true);
      setError(null);
      try {
        const resp = await accountManagementApiClient.listRoles();
        if (active) {
          setRoles(resp.roles);
        }
      } catch (err: any) {
        if (active) {
          setError(err?.message || 'Không thể tải danh mục vai trò hệ thống.');
        }
      } finally {
        if (active) {
          setLoading(false);
        }
      }
    };

    fetchRoles();
    return () => {
      active = false;
    };
  }, []);

  return (
    <div className="account-page-container">
      <header className="account-page-header">
        <h1 className="account-page-title">Danh mục vai trò hệ thống (Fixed Roles)</h1>
        <p className="account-page-description">
          Hệ thống Mland vận hành với 4 vai trò cố định. Phân quyền tuân thủ kiến trúc phân tầng, không hỗ trợ chỉnh sửa quyền động.
        </p>
      </header>

      {error && (
        <div className="account-alert account-alert-error" role="alert">
          <span>{error}</span>
        </div>
      )}

      {loading ? (
        <div className="account-card">
          <p>Đang tải danh mục vai trò…</p>
        </div>
      ) : (
        <div className="account-card">
          <table className="account-table">
            <thead>
              <tr>
                <th style={{ width: '180px' }}>Vai trò</th>
                <th>Mô tả phạm vi và quyền hạn</th>
              </tr>
            </thead>
            <tbody>
              {roles.map(r => (
                <tr key={r.role}>
                  <td>
                    <span className="account-badge account-badge-active" style={{ fontSize: '0.85rem' }}>
                      {r.role}
                    </span>
                  </td>
                  <td style={{ lineHeight: 1.6 }}>{r.description}</td>
                </tr>
              ))}
            </tbody>
          </table>

          <div style={{ marginTop: '1.5rem', padding: '1rem', backgroundColor: '#f9fafb', borderRadius: '4px', border: '1px solid #e5e7eb' }}>
            <h3 style={{ margin: '0 0 0.5rem 0', fontSize: '0.95rem', color: '#374151' }}>
              Quy tắc quản trị vai trò (Role Governance Rules):
            </h3>
            <ul style={{ margin: 0, paddingLeft: '1.25rem', fontSize: '0.875rem', color: '#4b5563' }}>
              <li>Vai trò được gán trực tiếp cho từng tài khoản; không áp dụng đa vai trò đồng thời.</li>
              <li>MEMBER được tạo tự động thông qua đăng ký cá nhân tự phục vụ.</li>
              <li>STAFF và MANAGER được khởi tạo bởi Quản trị viên (Admin) và gắn với cơ sở làm việc.</li>
              <li>ADMIN là vai trò quản trị hệ thống cao nhất, không thể bị tạm khóa hoặc sửa đổi từ giao diện này.</li>
            </ul>
          </div>
        </div>
      )}
    </div>
  );
};

export default RoleCataloguePage;

