import React from 'react';
import { RequireSession } from '../../../shared/auth/RequireSession';
import { useRole } from '../../../shared/auth/useRole';
import { useAuth } from '../../../shared/auth/AuthProvider';
import OperationsSidebar from '../../../shared/ui/OperationsSidebar';

function GuardInner({ children }) {
  const { status } = useAuth();
  const role = useRole();

  if (status === 'loading') {
    return (
      <div style={{ padding: '3rem', textAlign: 'center', color: '#64748b' }}>
        Đang kiểm tra quyền truy cập…
      </div>
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

export function NotificationManagementGuard({ children }) {
  return (
    <RequireSession requiredText="Khu vực quản trị thông báo yêu cầu đăng nhập tài khoản Quản trị viên hoặc Quản lý.">
      <GuardInner>{children}</GuardInner>
    </RequireSession>
  );
}

export default NotificationManagementGuard;

