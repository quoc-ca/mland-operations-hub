import React, { useEffect } from 'react';
import { Outlet, useLocation } from 'react-router';
import MainTopbar from '../shared/ui/MainTopbar';
import MainFooter from '../shared/ui/MainFooter';
import './AppLayout.css';

/** Header and footer for production-oriented feature routes. */
export default function AppLayout() {
  const location = useLocation();

  // Scroll restoration: luôn đẩy scroll lên đầu trang khi chuyển tuyến đường
  useEffect(() => {
    const isTest = (typeof process !== 'undefined' && process.env?.NODE_ENV === 'test') ||
                   (typeof import.meta !== 'undefined' && import.meta.env?.MODE === 'test');
    if (!isTest && typeof window !== 'undefined' && typeof window.scrollTo === 'function') {
      try {
        window.scrollTo({ top: 0, left: 0, behavior: 'instant' });
      } catch {
        try { window.scrollTo(0, 0); } catch { /* ignore */ }
      }
    }
  }, [location.pathname]);

  const isAuthPage = ['/sign-in', '/login', '/register', '/forgot-password'].includes(location.pathname);
  const isOperationsWorkspace =
    location.pathname.startsWith('/operations') ||
    location.pathname.startsWith('/ota-connectivity') ||
    location.pathname.startsWith('/settlement') ||
    location.pathname.startsWith('/workshop-packages') ||
    location.pathname.startsWith('/staff/') ||
    location.pathname.startsWith('/manager/') ||
    location.pathname.startsWith('/admin/') ||
    location.pathname.startsWith('/promotion-management') ||
    location.pathname.startsWith('/catalogue-management') ||
    location.pathname === '/home';

  if (isAuthPage || isOperationsWorkspace) {
    return <Outlet />;
  }

  return (
    <div className="app-root-layout">
      <MainTopbar />
      <main className="app-main-content">
        <Outlet />
      </main>
      <MainFooter />
    </div>
  );
}
