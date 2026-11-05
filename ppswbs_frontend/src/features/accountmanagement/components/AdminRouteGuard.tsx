import React from 'react';
import { Navigate, Outlet, Link, useLocation } from 'react-router';
import { useCurrentAccount } from '../hooks/useCurrentAccount';
import '../accountmanagement.css';

export interface AdminRouteGuardProps {
  children?: React.ReactNode;
}

export const AdminRouteGuard: React.FC<AdminRouteGuardProps> = ({ children }) => {
  const { account, status } = useCurrentAccount();
  const location = useLocation();

  if (status === 'loading') {
    return (
      <div className="account-page-container">
        <div className="account-card" style={{ padding: '2rem', textAlign: 'center' }}>
          <p style={{ color: '#6b717f' }}>Đang xác thực quyền truy cập…</p>
        </div>
      </div>
    );
  }

  if (status === 'unauthenticated') {
    return <Navigate to="/login" replace state={{ from: location }} />;
  }

  const isAdmin = status === 'authenticated' && account?.role === 'ADMIN';

  if (!isAdmin) {
    return (
      <div className="account-page-container">
        {/* Safe exit back button */}
        <div>
          <Link to="/" className="account-back-btn">
            ← Quay lại
          </Link>
        </div>

        {/* Error alert matching QA adversarial criteria & accessibility */}
        <div className="account-alert account-alert-error" role="alert" style={{ marginTop: '1rem' }}>
          <span>Không thể tải danh sách tài khoản hoặc truy cập trang quản trị do quyền hạn không hợp lệ (403 Forbidden).</span>
        </div>

        {/* Dedicated 403 Forbidden Card */}
        <div className="account-card" style={{ marginTop: '1.5rem', textAlign: 'center', padding: '3rem 1.5rem' }}>
          <h1 style={{ fontSize: '1.5rem', fontWeight: 600, color: '#111827', marginBottom: '0.75rem' }}>
            Từ chối quyền truy cập (403 Forbidden)
          </h1>
          <p style={{ color: '#4b5563', maxWidth: '520px', margin: '0 auto 1.5rem', lineHeight: '1.5' }}>
            Bạn không có quyền truy cập vào khu vực quản trị nội bộ này. Trang này chỉ dành riêng cho tài khoản Quản trị viên hệ thống (ADMIN).
          </p>
          <div>
            <Link to="/" className="account-btn account-btn-primary" style={{ display: 'inline-block' }}>
              Về trang chủ
            </Link>
          </div>
        </div>
      </div>
    );
  }

  return children ? <>{children}</> : <Outlet />;
};

export default AdminRouteGuard;

