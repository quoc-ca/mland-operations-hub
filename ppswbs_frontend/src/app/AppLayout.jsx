import React from 'react';
import { Outlet, useLocation } from 'react-router';
import MainTopbar from '../shared/ui/MainTopbar';

/** Header and footer for production-oriented feature routes. */
export default function AppLayout() {
  const location = useLocation();

  if (['/sign-in', '/login', '/register', '/forgot-password'].includes(location.pathname)) {
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
