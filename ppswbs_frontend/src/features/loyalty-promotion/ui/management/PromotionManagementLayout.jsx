import React from 'react';
import { NavLink, Outlet, useLocation } from 'react-router';
import { RequireSession } from '../../../../shared/auth/RequireSession';
import { useT } from '../../../../shared/i18n/I18nProvider';
import { OperationsSidebar } from '../../../../shared/ui/OperationsSidebar';
import { messages } from '../messages';

/**
 * Manager area of 006 per Figma "002-006" frames 006-09/09b/09c: dark side menu with the MLAND mark, a top bar with
 * the current place, and the page. The backend answers 403 to anyone without the Manager capability.
 */
export default function PromotionManagementLayout() {
  const t = useT(messages);
  const { pathname } = useLocation();
  const links = [
    ['/promotion-management/programs', t('nav.programs')],
    ['/promotion-management/programs/new', t('nav.newProgram')],
    ['/promotion-management/point-policy', t('nav.policy')],
  ];
  const current = links.find(([to]) => to === pathname)?.[1]
    || (pathname.startsWith('/promotion-management/programs/') ? t('nav.programDetail') : t('nav.label'));
  const onDetail = !links.some(([to]) => to === pathname) && pathname.startsWith('/promotion-management/programs/');

  return (
    <main className="app-shell lp-shell lp-theme">
      <OperationsSidebar />
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
