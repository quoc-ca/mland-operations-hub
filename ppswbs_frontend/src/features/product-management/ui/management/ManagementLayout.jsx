import React, { useMemo, useState } from 'react';
import { NavLink, Outlet, useLocation } from 'react-router';
import { useAuth } from '../../../../shared/auth/AuthProvider';
import { SignInLink } from '../../../../shared/auth/SignInLink';
import { useT } from '../../../../shared/i18n/I18nProvider';
import { Loading } from '../../../../shared/ui/components';
import { sharedMessages } from '../../../../shared/ui/messages';
import { messages } from '../messages';

/**
 * Shell of the catalogue management screens (prototype app-shell). Without a session it shows the sign-in
 * notice; the sign-in screen itself belongs to 001. The backend still decides every permission (401/403).
 */
export default function ManagementLayout() {
  const t = useT(messages);
  const tShared = useT(sharedMessages);
  const { status } = useAuth();
  const { pathname } = useLocation();
  const links = [
    ['/catalogue-management/products', t('nav.products'), t('list.title')],
    ['/catalogue-management/products/new', t('nav.newProduct'), t('create.title')],
    ['/catalogue-management/categories', t('nav.categories'), t('category.title')],
    ['/catalogue-management/imports', t('nav.import'), t('import.title')],
  ];
  // The detail page names its product here; until then the trail says "Product detail".
  const [crumb, setCrumb] = useState(null);
  const outlet = useMemo(() => ({ setCrumb }), []);
  const onDetail = !links.some(([to]) => to === pathname) && pathname.startsWith('/catalogue-management/products/');
  const trail = status !== 'signed-in' ? [t('nav.label')]
    : onDetail ? [t('nav.products'), crumb || t('nav.productDetail')]
      : [links.find(([to]) => to === pathname)?.[2] || t('nav.label')];
  const linkClass = to => ({ isActive }) => (isActive || (onDetail && to === links[0][0]) ? 'active' : undefined);
  return (
    <main className="app-shell pm-shell">
      <aside className="side-nav" aria-label={t('nav.label')}>
        <div className="pm-brand">
          <span className="pm-brand-mark" aria-hidden="true">M</span>
          <span><strong>MLAND</strong><small>operations hub</small></span>
        </div>
        <p className="eyebrow">{t('nav.group')}</p>
        {links.map(([to, label]) => <NavLink key={to} to={to} end className={linkClass(to)}>{label}</NavLink>)}
      </aside>
      <div className="pm-main">
        <nav className="pm-topbar" aria-label={t('nav.breadcrumb')}>
          <span>MLAND</span>
          {trail.map((label, index) => (
            <React.Fragment key={index}>
              <span aria-hidden="true">/</span>
              {index === 0 && trail.length > 1
                ? <NavLink to={links[0][0]} end>{label}</NavLink>
                : <span aria-current={index === trail.length - 1 ? 'page' : undefined}>{label}</span>}
            </React.Fragment>
          ))}
        </nav>
        <section className="workspace">
          {status === 'loading' && <Loading />}
          {status === 'unconfigured' && <SessionNotice text={tShared('session.unconfigured')} />}
          {status === 'signed-out' && <SessionNotice text={tShared('session.required')} />}
          {status === 'signed-in' && <Outlet context={outlet} />}
        </section>
      </div>
    </main>
  );
}

function SessionNotice({ text }) {
  const t = useT(sharedMessages);
  return (
    <div className="empty-state pm-session" role="status">
      <p>{text}</p>
      <SignInLink>{t('session.signIn')}</SignInLink>
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
