import React from 'react';
import { Link } from 'react-router';
import { OperationsSidebar } from './OperationsSidebar';
import { useLanguage } from '../i18n/I18nProvider';
import '../../ota-connectivity/ota-connectivity.css';

/**
 * Standard workspace shell rendered when an unauthenticated user visits
 * any Operations, OTA Connectivity, or Settlement manager route.
 * Keeps OperationsSidebar intact on the left and displays the styled login card.
 */
export function OperationsAuthWorkspace({
  topline = 'MLAND / OPERATIONS',
  message,
}) {
  const { language } = useLanguage();
  const isVi = language === 'vi';

  const defaultMessage = isVi
    ? 'Khu vực quản trị cần đăng nhập bằng tài khoản Staff hoặc Manager.'
    : 'Authentication required to access this workspace.';

  return (
    <div className="ota-workspace">
      <OperationsSidebar />
      <main className="ota-main">
        <div className="ota-topline">
          {topline} <span>● {isVi ? 'Nhân viên / Quản lý' : 'Staff / Manager'}</span>
        </div>
        <div className="empty-state pm-session" role="status">
          <p>{message || defaultMessage}</p>
          <Link className="button primary" to="/login">
            {isVi ? 'Đăng nhập' : 'Sign In'}
          </Link>
        </div>
      </main>
    </div>
  );
}

export default OperationsAuthWorkspace;

