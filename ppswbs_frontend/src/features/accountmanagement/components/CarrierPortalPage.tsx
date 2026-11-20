import React from 'react';
import { Link } from 'react-router';
import { useCurrentAccount } from '../hooks/useCurrentAccount';
import { useAuth } from '../../../shared/auth/AuthProvider';
import '../accountmanagement.css';

export const CarrierPortalPage: React.FC = () => {
  const { account, status: accountStatus, error } = useCurrentAccount();
  const { signOut } = useAuth();

  if (accountStatus === 'loading') {
    return (
      <div className="account-page-container">
        <div className="account-card">
          <p style={{ color: '#6b717f' }}>Đang xác thực thông tin tài khoản đối tác vận chuyển…</p>
        </div>
      </div>
    );
  }

  // Access defense (FR-003, FR-026, FR-027): Ensure role is CARRIER (or ADMIN managing carrier)
  if (account && account.role !== 'CARRIER' && account.role !== 'ADMIN') {
    return (
      <div className="account-page-container">
        <div className="account-alert account-alert-error" role="alert">
          <span>Tài khoản của bạn không có quyền truy cập Cổng đối tác vận chuyển.</span>
        </div>
        <div className="account-card" style={{ marginTop: '1rem', textAlign: 'center', padding: '2rem' }}>
          <Link to="/" className="account-btn account-btn-primary">
            Quay lại trang chủ
          </Link>
        </div>
      </div>
    );
  }

  return (
    <div className="account-page-container">
      <header className="account-page-header">
        <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem', flexWrap: 'wrap' }}>
          <h1 className="account-page-title" style={{ margin: 0 }}>
            Cổng đối tác vận chuyển (GHTK)
          </h1>
          <span className="account-badge account-badge-active">
            CARRIER · {account?.status || 'ACTIVE'}
          </span>
        </div>
        <p className="account-page-description" style={{ marginTop: '0.5rem' }}>
          Hệ thống quản lý dành riêng cho Đối tác giao nhận vận chuyển GHTK. Quản trị phân luồng giao nhận và tiến độ vận đơn.
        </p>
      </header>

      {error && (
        <div className="account-alert account-alert-error" role="alert">
          <span>{error.message || 'Lỗi khi đồng bộ dữ liệu tài khoản đối tác.'}</span>
        </div>
      )}

      {account?.status === 'SUSPENDED' && (
        <div className="account-alert account-alert-error" role="alert">
          <strong>Tài khoản đang bị tạm khóa (SUSPENDED).</strong> Vui lòng liên hệ Quản trị viên để được hỗ trợ.
        </div>
      )}

      <div className="account-card" style={{ marginBottom: '1.5rem' }}>
        <h2 className="account-card-title">Thông tin định danh đối tác</h2>
        <table className="account-table">
          <tbody>
            <tr>
              <th style={{ width: '220px' }}>Tên đối tác / Đại diện:</th>
              <td><strong>{account?.email?.split('@')[0] || 'Đối tác vận chuyển (GHTK)'}</strong></td>
            </tr>
            <tr>
              <th>Email đăng nhập:</th>
              <td>{account?.email || 'ghtk.test@mland.vn'}</td>
            </tr>
            <tr>
              <th>Vai trò hệ thống:</th>
              <td>
                <span className="account-badge account-badge-readonly">CARRIER</span>
              </td>
            </tr>
            <tr>
              <th>Phạm vi cơ sở:</th>
              <td>Toàn quốc (Không giới hạn theo cơ sở đơn lẻ)</td>
            </tr>
          </tbody>
        </table>
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))', gap: '1.5rem' }}>
        <div className="account-card" style={{ display: 'flex', flexDirection: 'column', justifyContent: 'space-between' }}>
          <div>
            <h3 style={{ margin: '0 0 0.5rem 0', fontSize: '1.1rem', color: '#1a1a1a' }}>
              🚚 Đơn vận chuyển cần giao
            </h3>
            <p style={{ color: '#4b5563', fontSize: '0.9rem', lineHeight: 1.5, margin: 0 }}>
              Tiếp nhận các đơn hàng trang sức bán lẻ đã chuẩn bị sẵn sàng và bàn giao cho GHTK vận chuyển đến khách hàng.
            </p>
          </div>
          <div style={{ marginTop: '1.25rem' }}>
            <Link
              to="/carrier/orders"
              className="account-btn account-btn-primary"
              style={{ display: 'inline-block' }}
            >
              Xem danh sách vận đơn →
            </Link>
          </div>
        </div>

        <div className="account-card" style={{ display: 'flex', flexDirection: 'column', justifyContent: 'space-between' }}>
          <div>
            <h3 style={{ margin: '0 0 0.5rem 0', fontSize: '1.1rem', color: '#1a1a1a' }}>
              🔑 Bảo mật & Mật khẩu
            </h3>
            <p style={{ color: '#4b5563', fontSize: '0.9rem', lineHeight: 1.5, margin: 0 }}>
              Chủ động thay đổi mật khẩu định kỳ để bảo đảm an toàn truy cập tài khoản đối tác vận chuyển.
            </p>
          </div>
          <div style={{ marginTop: '1.25rem' }}>
            <Link
              to="/security/password"
              className="account-btn account-btn-secondary"
              style={{ display: 'inline-block' }}
            >
              Đổi mật khẩu tài khoản →
            </Link>
          </div>
        </div>
      </div>
    </div>
  );
};

export default CarrierPortalPage;
