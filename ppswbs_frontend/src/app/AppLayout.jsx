import React from 'react';
import { Outlet, useLocation } from 'react-router';
import MainTopbar from '../shared/ui/MainTopbar';

/** Header and footer for production-oriented feature routes. */
export default function AppLayout() {
  const location = useLocation();

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
    <>
      <MainTopbar />
      <Outlet />
      <footer>
        <div className="container footer-inner">
          <strong>MLAND</strong>
          <span>© MLAND Operations Hub</span>
        </div>
      </footer>
    </>
  );
}
