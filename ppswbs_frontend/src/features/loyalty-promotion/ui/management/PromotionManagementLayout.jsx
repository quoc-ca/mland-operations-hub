import React from 'react';
import { NavLink, Outlet, useLocation } from 'react-router';
import { useAuth } from '../../../../shared/auth/AuthProvider';
import { RequireSession } from '../../../../shared/auth/RequireSession';
import { useT } from '../../../../shared/i18n/I18nProvider';
import { sharedMessages } from '../../../../shared/ui/messages';
import { messages } from '../messages';

/**
 * Manager area of 006 per Figma "002-006" frames 006-09/09b/09c: dark side menu with the MLAND mark, a top bar with
 * the current place, and the page. The backend answers 403 to anyone without the Manager capability.
 */
export default function PromotionManagementLayout() {
  const t = useT(messages);
  const tShared = useT(sharedMessages);
  const { status, signOut } = useAuth();
  const { pathname } = useLocation();
  const links = [
    ['/promotion-management/programs', t('nav.programs')],
    ['/promotion-management/programs/new', t('nav.newProgram')],
    ['/promotion-management/point-policy', t('nav.policy')],
  ];
  const current = links.find(([to]) => to === pathname)?.[1]
    || (pathname.startsWith('/promotion-management/programs/') ? t('nav.programDetail') : t('nav.label'));
  const onDetail = !links.some(([to]) => to === pathname) && pathname.startsWith('/promotion-management/programs/');
  const linkClass = to => ({ isActive }) => (isActive || (onDetail && to === links[0][0]) ? 'active' : undefined);
  return (
    <main className="app-shell lp-shell lp-theme">
      <aside className="side-nav" aria-label={t('nav.label')}>
        <div className="lp-brand">
          <span className="lp-brand-mark" aria-hidden="true">M</span>
          <span><strong>MLAND</strong><small>operations hub</small></span>
        </div>
        <p className="eyebrow">MANAGER</p>
        {links.map(([to, label]) => <NavLink key={to} to={to} end className={linkClass(to)}>{label}</NavLink>)}
        {status === 'signed-in' && <button type="button" className="side-signout" onClick={signOut}>{tShared('session.signOut')}</button>}
      </aside>
      <div className="lp-main">
        <nav className="lp-topbar" aria-label={t('nav.breadcrumb')}>
          <span>MLAND</span>
          <span aria-hidden="true">/</span>
          {onDetail && <><NavLink to={links[0][0]} end>{links[0][1]}</NavLink><span aria-hidden="true">/</span></>}
          <span aria-current="page">{current}</span>
        </nav>
        <section className="workspace">
          <RequireSession requiredText={t('session.manager')}><Outlet /></RequireSession>
        </section>
      </div>
    </main>
  );
}

export function PageTop({ title, eyebrow = 'MANAGER', children }) {
  return (
    <div className="workspace-top">
      <div><p className="eyebrow">{eyebrow}</p><h1>{title}</h1></div>
      {children && <div className="actions">{children}</div>}
    </div>
  );
}
