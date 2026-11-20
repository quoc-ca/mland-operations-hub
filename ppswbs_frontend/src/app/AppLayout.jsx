import React from 'react';
import { Outlet, ScrollRestoration, useLocation } from 'react-router';
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
    location.pathname.startsWith('/carrier') ||
    location.pathname.startsWith('/admin/') ||
    location.pathname.startsWith('/promotion-management') ||
    location.pathname.startsWith('/catalogue-management') ||
    location.pathname === '/home';

  // Every new page opens at the top; Back and Forward return to where the reader was (2026-11-20: the checkout used to
  // open scrolled down because it kept the cart's scroll position).
  if (isAuthPage || isOperationsWorkspace) {
    return <><ScrollRestoration /><Outlet /></>;
  }

  return (
    <div className="app-layout-wrapper">
      <ScrollRestoration />
      <MainTopbar />
      <main className="app-layout-content">
        <Outlet />
      </main>
      <footer className="app-layout-footer">
        <div className="container footer-inner">
          <strong>MLAND</strong>
          <span>© MLAND Operations Hub</span>
        </div>
      </footer>
    </div>
  );
}
