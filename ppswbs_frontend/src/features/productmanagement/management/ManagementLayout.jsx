import React from 'react';
import { NavLink, Outlet } from 'react-router';
import { useAuth } from '../../../shared/auth/AuthProvider';
import { useT } from '../../../shared/i18n/I18nProvider';
import { Loading } from '../../../shared/ui/components';
import { sharedMessages } from '../../../shared/ui/messages';
import { messages } from '../messages';

/**
 * Shell of the catalogue management screens (prototype app-shell). Without a session it shows the sign-in
 * notice; the sign-in screen itself belongs to 001. The backend still decides every permission (401/403).
 */
export default function ManagementLayout() {
  const t = useT(messages);
  const tShared = useT(sharedMessages);
  const { status } = useAuth();
  const links = [
    ['/catalogue-management/products', t('nav.products')],
    ['/catalogue-management/products/new', t('nav.newProduct')],
    ['/catalogue-management/categories', t('nav.categories')],
    ['/catalogue-management/imports', t('nav.import')],
  ];
  return (
    <main className="app-shell pm-shell">
      <aside className="side-nav" aria-label={t('nav.label')}>
        <p className="eyebrow">{t('nav.group')}</p>
        <strong>MLAND</strong>
        {links.map(([to, label]) => <NavLink key={to} to={to} end>{label}</NavLink>)}
      </aside>
      <section className="workspace">
        {status === 'loading' && <Loading />}
        {status === 'unconfigured' && <SessionNotice text={tShared('session.unconfigured')} />}
        {status === 'signed-out' && <SessionNotice text={tShared('session.required')} />}
        {status === 'signed-in' && <Outlet />}
      </section>
    </main>
  );
}

function SessionNotice({ text }) {
  const t = useT(sharedMessages);
  return (
    <div className="empty-state" role="status">
      <p>{text}</p>
      <a className="button primary" href="/#/login">{t('session.signIn')}</a>
    </div>
  );
}

/** Page heading used by every management screen. */
export function PageTop({ eyebrow, title, children }) {
  return (
    <div className="workspace-top">
      <div>{eyebrow && <p className="eyebrow">{eyebrow}</p>}<h1>{title}</h1></div>
      {children && <div className="actions">{children}</div>}
    </div>
  );
}
