import React, { createContext, useContext, useState } from 'react';
import { createPortal } from 'react-dom';
import { Link, Outlet, useLocation } from 'react-router';
import { useAuth } from '../auth/AuthProvider';
import { useRole } from '../auth/useRole';
import { getFirebaseAuth } from '../auth/firebaseSession';
import { useLanguage, useT } from '../i18n/I18nProvider';
import { operationsShellMessages } from './operationsShellMessages';
import {
  groupLabel, locate, pageActions, pageTitle, tabKey, tabLabel, visibleGroups, visibleTabs,
} from '../nav/navModel';
import { useNavBadges } from '../nav/useNavBadges';
import '../../ota-connectivity/ota-connectivity.css';
import './operationsShell.css';

const ICONS = {
  dash: 'M4 20V10M10 20V4M16 20v-8M22 20H2',
  box: 'M21 8l-9-5-9 5v8l9 5 9-5zM3 8l9 5 9-5M12 13v8',
  tag: 'M3 12V4h8l10 10-8 8zM7.5 8.5h.01',
  chat: 'M21 12a8 8 0 0 1-11.6 7.1L4 20l1-4.6A8 8 0 1 1 21 12z',
  shop: 'M4 7h16l-1 13H5zM8 7a4 4 0 0 1 8 0',
  cal: 'M5 5h14a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2zM16 3v4M8 3v4M3 11h18',
  cash: 'M4 6h16a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2zM12 9.5a2.5 2.5 0 1 0 0 5 2.5 2.5 0 0 0 0-5z',
  ticket: 'M3 9V6h18v3a3 3 0 0 0 0 6v3H3v-3a3 3 0 0 0 0-6zM13 6v12',
  plug: 'M9 2v6M15 2v6M6 8h12v4a6 6 0 0 1-12 0zM12 18v4',
  gear: 'M12 9a3 3 0 1 0 0 6 3 3 0 0 0 0-6zM12 2v3M12 19v3M2 12h3M19 12h3M5 5l2 2M17 17l2 2M19 5l-2 2M7 17l-2 2',
  truck: 'M1 5h13v11H1zM14 9h4l4 4v3h-8zM5.5 18.5a1.5 1.5 0 1 0 0-.01M17.5 18.5a1.5 1.5 0 1 0 0-.01',
};

const COLLAPSE_KEY = 'ops-rail-collapsed';
function readCollapsed() {
  try { return window.localStorage.getItem(COLLAPSE_KEY) === '1'; } catch { return false; }
}

function Icon({ name }) {
  return (
    <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d={ICONS[name]} />
    </svg>
  );
}

// The header's action slot. A page with its own header button (one that opens a form in the page, say) wraps it in
// <HeaderActions> and it appears at the top of the page, beside the title; outside the shell it renders in place.
const ActionSlotContext = createContext(null);

export function HeaderActions({ children }) {
  const slot = useContext(ActionSlotContext);
  if (!slot) return <>{children}</>;
  return createPortal(children, slot);
}

function Badge({ count, label }) {
  // 0 and absent show nothing: a failed count never looks like "nothing waiting" because it simply has no badge.
  if (!Number.isInteger(count) || count <= 0) return null;
  return <span className="ops-badge" aria-label={label}>{count > 99 ? '99+' : count}</span>;
}

function RailFooter() {
  const t = useT(operationsShellMessages);
  const { status, signOut } = useAuth();
  const { language, setLanguage } = useLanguage();
  const [busy, setBusy] = useState(false);
  const [failed, setFailed] = useState(false);
  const { pathname } = useLocation();

  const authInstance = typeof getFirebaseAuth === 'function' ? getFirebaseAuth() : null;
  const currentUser = authInstance?.currentUser;
  const email = currentUser?.email || '';
  const initial = (currentUser?.displayName?.[0] || email?.[0] || 'M').toUpperCase();

  const handleSignOut = async () => {
    if (busy) return;
    setBusy(true);
    setFailed(false);
    try {
      await signOut();
      // Navigation to Home with its flash notice is handled solely by SessionNavigationBoundary.
    } catch {
      setFailed(true);
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="ops-rail-footer">
      <div className="ops-lang" role="group" aria-label={t('language')}>
        {['vi', 'en'].map(code => (
          <button key={code} type="button" className={language === code ? 'active' : ''} aria-pressed={language === code} onClick={() => setLanguage(code)}>
            {code.toUpperCase()}
          </button>
        ))}
      </div>
      {status === 'signed-in' ? (
        <>
          <Link to="/profile" className={`ops-rail-user${pathname === '/profile' ? ' active' : ''}`} title={email || t('profileTitle')}>
            <span className="ops-avatar" aria-hidden="true">{initial}</span>
            <span className="ops-label">{t('profile')}</span>
          </Link>
          <button type="button" className="ops-signout" onClick={handleSignOut} disabled={busy} aria-busy={busy} title={t('signOutTitle')}>
            <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="M9 4H5v16h4M16 8l4 4-4 4M20 12H9" /></svg>
            <span className="ops-label">{busy ? t('signingOut') : t('signOut')}</span>
          </button>
          {failed && <span className="ops-signout-error" role="alert">{t('signOutFailed')}</span>}
        </>
      ) : (
        <Link to="/login" className="ops-rail-user" aria-label={t('signInAccount')}><svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="M15 4h4v16h-4M8 8l-4 4 4 4M4 12h11" /></svg><span className="ops-label">{t('signIn')}</span></Link>
      )}
    </div>
  );
}

/**
 * GHTK partner portal (CARRIER, Session 2026-11-23): language and sign-out sit at the top right of the page, like most
 * web apps; a carrier has no Mland profile page, so there is no profile link.
 */
function CarrierTopRight() {
  const t = useT(operationsShellMessages);
  const { status, signOut } = useAuth();
  const { language, setLanguage } = useLanguage();
  const [busy, setBusy] = useState(false);
  const [failed, setFailed] = useState(false);
  const email = getFirebaseAuth?.()?.currentUser?.email || '';
  const handleSignOut = async () => {
    if (busy) return;
    setBusy(true);
    setFailed(false);
    try { await signOut(); } catch { setFailed(true); } finally { setBusy(false); }
  };
  return (
    <div className="ops-topright">
      {email && <span className="ops-topright-user" title={email}>{email}</span>}
      <div className="ops-lang" role="group" aria-label={t('language')}>
        {['vi', 'en'].map(code => (
          <button key={code} type="button" className={language === code ? 'active' : ''} aria-pressed={language === code} onClick={() => setLanguage(code)}>
            {code.toUpperCase()}
          </button>
        ))}
      </div>
      {status === 'signed-in' && (
        <button type="button" className="ops-topright-signout" onClick={handleSignOut} disabled={busy} aria-busy={busy}>
          <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="M9 4H5v16h4M16 8l4 4-4 4M20 12H9" /></svg>
          {busy ? t('signingOut') : t('signOut')}
        </button>
      )}
      {failed && <span className="ops-signout-error" role="alert">{t('signOutFailed')}</span>}
    </div>
  );
}

/**
 * Layout of every operations screen (spec 014, navigation decision of 2026-11-22): a rail of groups, and in the page
 * a header (breadcrumb, title, action buttons) with the tabs of the group. Navigation only; the backend decides
 * every permission.
 */
export default function OperationsShell() {
  const { pathname, search } = useLocation();
  const { language } = useLanguage();
  const t = useT(operationsShellMessages);
  const role = useRole();
  const badges = useNavBadges(role);
  const [slot, setSlot] = useState(null);
  const isVi = language === 'vi';

  const [collapsed, setCollapsed] = useState(readCollapsed);
  const toggle = () => setCollapsed(prev => {
    const next = !prev;
    try { window.localStorage.setItem(COLLAPSE_KEY, next ? '1' : '0'); } catch { /* preference is optional */ }
    return next;
  });

  const here = locate(pathname, search);
  const groups = visibleGroups(role);
  const tabs = here ? visibleTabs(here.group, role) : [];
  const actions = here ? pageActions(here, pathname) : [];
  const count = key => badges.counts[key];
  const isCarrier = role === 'CARRIER';

  return (
    <div className={`ops-shell${collapsed ? ' collapsed' : ''}${isCarrier ? ' carrier-theme' : ''}`}>
      <aside className="ops-rail" aria-label={t('railLabel')}>
        {isCarrier
          ? <Link to="/carrier" className="ops-logo ops-logo-ghtk" aria-label="GHTK">G</Link>
          : <Link to="/home" className="ops-logo" aria-label="MLAND">M</Link>}
        <button type="button" className="ops-toggle" onClick={toggle} aria-expanded={!collapsed} title={collapsed ? t('expandNav') : t('collapseNav')} aria-label={collapsed ? t('expandNav') : t('collapseNav')}>
          <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d={collapsed ? 'M9 6l6 6-6 6' : 'M15 6l-6 6 6 6'} /></svg>
        </button>
        <nav className="ops-rail-nav" aria-label={t('featureGroups')}>
          {groups.map(({ group, tabs: groupTabs }) => {
            const current = here?.group.id === group.id;
            const label = groupLabel(group, language, role);
            const total = count(group.id);
            return (
              <Link
                key={group.id}
                to={groupTabs[0].to}
                className={`ops-rail-item${current ? ' active' : ''}`}
                aria-current={current ? 'true' : undefined}
                aria-label={Number.isInteger(total) && total > 0 ? `${label}, ${total}` : label}
              >
                <Icon name={group.icon} />
                <span className="ops-label">{label}</span>
                <Badge count={total} label={t('waiting', { count: total })} />
              </Link>
            );
          })}
        </nav>
        {!isCarrier && <RailFooter />}
      </aside>

      <div className="ops-page">
        {here && (
          <header className="ops-header">
            {isCarrier && <CarrierTopRight />}
            <nav className="ops-crumb" aria-label={t('breadcrumb')}>
              <span>{isCarrier ? 'GHTK × MLAND' : 'MLAND'}</span>
              <span aria-hidden="true">/</span>
              <span>{groupLabel(here.group, language, role)}</span>
              {tabs.length > 1 && <><span aria-hidden="true">/</span><span>{tabLabel(here.tab, language, role)}</span></>}
            </nav>
            <div className="ops-titlebar">
              <h1>{pageTitle(here, pathname, language, role)}</h1>
              <div className="ops-actions">
                {actions.map(action => (
                  <Link key={action.id} to={action.to} className={`button${action.primary ? ' primary' : ' outline'}`}>{isVi ? action.vi : action.en}</Link>
                ))}
                <div className="ops-actions-slot" ref={setSlot} />
              </div>
            </div>
            {tabs.length > 1 && (
              <div className="ops-tabs" role="navigation" aria-label={t('groupPages')}>
                {tabs.map(tab => {
                  const active = tab.id === here.tab.id;
                  const total = count(tabKey(here.group, tab));
                  return (
                    <Link key={tab.id} to={tab.to} className={`ops-tab${active ? ' active' : ''}`} aria-current={active ? 'page' : undefined}>
                      {tabLabel(tab, language, role)}
                      <Badge count={total} label={t('waiting', { count: total })} />
                    </Link>
                  );
                })}
              </div>
            )}
          </header>
        )}
        <div className="ops-content">
          <ActionSlotContext.Provider value={slot}>
            <Outlet />
          </ActionSlotContext.Provider>
        </div>
      </div>
    </div>
  );
}
