import React from 'react';
import { NavLink, Outlet } from 'react-router';
import { RequireSession } from '../../../shared/auth/RequireSession';
import { useT } from '../../../shared/i18n/I18nProvider';
import { messages } from '../messages';

/** Manager area of 006 (prototype app-shell). The backend answers 403 to anyone without the Manager capability. */
export default function PromotionManagementLayout() {
  const t = useT(messages);
  const links = [
    ['/promotion-management/programs', t('nav.programs')],
    ['/promotion-management/programs/new', t('nav.newProgram')],
    ['/promotion-management/point-policy', t('nav.policy')],
  ];
  return (
    <main className="app-shell feature-shell">
      <aside className="side-nav" aria-label={t('nav.label')}>
        <p className="eyebrow">MANAGER</p>
        <strong>MLAND</strong>
        {links.map(([to, label]) => <NavLink key={to} to={to} end>{label}</NavLink>)}
      </aside>
      <section className="workspace">
        <RequireSession requiredText={t('session.manager')}><Outlet /></RequireSession>
      </section>
    </main>
  );
}

export function PageTop({ title, children }) {
  return (
    <div className="workspace-top">
      <div><p className="eyebrow">MANAGER</p><h1>{title}</h1></div>
      {children && <div className="actions">{children}</div>}
    </div>
  );
}
