import React, { useState, useEffect } from 'react';
import { Link } from 'react-router';
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
      {/* Back button matching Figma */}
      <div>
        <Link to="/admin/accounts" className="account-back-btn">
          ← Quay lại
        </Link>
      </div>

      {/* Admin Navigation Tabs (Figma F08-F11) */}
      <nav className="admin-nav-tabs" aria-label="Admin Navigation">
        <Link to="/admin/accounts" className="admin-nav-tab">
          Tài khoản
        </Link>
        <Link to="/admin/roles" className="admin-nav-tab active">
          Vai trò
        </Link>
      </nav>

      <header className="account-page-header">
        <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem', flexWrap: 'wrap' }}>
          <h1 className="account-page-title" style={{ margin: 0 }}>Danh mục vai trò hệ thống (Fixed Roles)</h1>
          <span className="account-badge account-badge-readonly">
            Chỉ đọc · 4 vai trò cố định
          </span>
        </div>
        <p className="account-page-description" style={{ marginTop: '0.5rem' }}>
          Danh mục vai trò hệ thống (Fixed Roles). Tra cứu trách nhiệm và phạm vi. Không thể thêm, đổi tên hoặc xóa vai trò tại đây.
        </p>
      </header>

      {error && (
        <div className="account-alert account-alert-error" role="alert">
          <span>{error}</span>
        </div>
      )}

      {loading ? (
        <div className="account-card">
          <p style={{ color: '#6b717f' }}>Đang tải danh mục vai trò…</p>
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
              {roles.map(r => {
                const roleCode = r.code || r.role;
                const roleDesc = r.displayName || r.description;
                return (
                  <tr key={roleCode}>
                    <td>
                      <span className="account-badge account-badge-readonly" style={{ fontSize: '0.85rem' }}>
                        {roleCode}
                      </span>
                    </td>
                    <td style={{ lineHeight: 1.6 }}>{roleDesc}</td>
                  </tr>
                );
              })}
            </tbody>
          </table>

          <div style={{ marginTop: '1.5rem', padding: '1rem', backgroundColor: '#f4f5f7', borderRadius: '4px', border: '1px solid #eceef1' }}>
            <p style={{ margin: '0 0 0.5rem 0', fontSize: '0.9rem', fontWeight: 600, color: '#1a1a1a' }}>
              Quy tắc quản trị vai trò (Role Governance Rules):
            </p>
            <ul style={{ margin: 0, paddingLeft: '1.25rem', fontSize: '0.85rem', color: '#6b717f', lineHeight: 1.6 }}>
              <li>Vai trò được gán trực tiếp cho từng tài khoản; không áp dụng đa vai trò đồng thời.</li>
              <li>MEMBER được tạo tự động thông qua đăng ký cá nhân tự phục vụ.</li>
              <li>STAFF được phân công đúng 1 cơ sở (giới hạn quyền truy cập vận hành trong cơ sở đó).</li>
              <li>MANAGER có một hoặc nhiều phân công cơ sở hỗ trợ tổ chức; phạm vi vận hành cho các tác vụ quản lý của Manager là toàn hệ thống.</li>
              <li>ADMIN là vai trò quản trị hệ thống cao nhất, quản lý tài khoản nội bộ và danh mục vai trò; không thể bị tạm khóa hoặc sửa đổi từ giao diện này.</li>
            </ul>
            <p style={{ margin: '0.75rem 0 0 0', fontSize: '0.85rem', color: '#1a1a1a', fontWeight: 500 }}>
              💡 Để phân công vai trò STAFF hoặc MANAGER, mở chi tiết tài khoản và chọn cơ sở phù hợp. Phân công cơ sở của Manager hỗ trợ tổ chức và không giới hạn quyền quản lý toàn hệ thống.
            </p>
          </div>
        </div>
      )}
    </div>
  );
};

export default RoleCataloguePage;
