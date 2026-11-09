import React from 'react';
import { Navigate, useLocation } from 'react-router';
import { useRole } from '../../../shared/auth/useRole';
import { useAuth } from '../../../shared/auth/AuthProvider';
import OperationsSidebar from '../../../shared/ui/OperationsSidebar';

export function NotificationManagementGuard({ children }) {
  const { status } = useAuth();
  const role = useRole();
  const location = useLocation();

  if (status === 'loading') {
    return (
      <div style={{ padding: '3rem', textAlign: 'center', color: '#64748b' }}>
        Đang kiểm tra quyền truy cập…
      </div>
    );
  }

  if (status === 'signed-out') {
    return (
      <Navigate
        to={`/login?redirect=${encodeURIComponent(location.pathname + location.search)}`}
        replace
      />
    );
  }

  // Allow MANAGER and ADMIN; if role is not yet loaded, show loading
  if (role === null && status === 'signed-in') {
    return (
      <div style={{ padding: '3rem', textAlign: 'center', color: '#64748b' }}>
        Đang xác thực thông tin tài khoản…
      </div>
    );
  }

  if (role && role !== 'ADMIN' && role !== 'MANAGER') {
    return (
      <div className="ota-workspace">
        <OperationsSidebar />
        <main className="ota-main">
          <div className="nm-container">
            <div className="nm-alert nm-alert-danger" role="alert" style={{ marginTop: '2rem' }}>
              <h3 style={{ margin: '0 0 0.5rem 0' }}>Truy cập bị từ chối</h3>
              <p style={{ margin: 0 }}>
                Yêu cầu quyền Quản trị viên (Admin) hoặc Quản lý (Manager) để truy cập phân hệ Quản lý thông báo email.
              </p>
            </div>
          </div>
        </main>
      </div>
    );
  }

  return children;
}

export default NotificationManagementGuard;

