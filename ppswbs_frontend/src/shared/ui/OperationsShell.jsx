import React, { createContext, useContext, useState } from 'react';
import { createPortal } from 'react-dom';
import { Link, Outlet, useLocation } from 'react-router';
import { useAuth } from '../auth/AuthProvider';
import { useRole } from '../auth/useRole';
import { getFirebaseAuth } from '../auth/firebaseSession';
import { useLanguage } from '../i18n/I18nProvider';
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
};

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

function RailFooter({ isVi }) {
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
      <div className="ops-lang" role="group" aria-label="Language">
        {['vi', 'en'].map(code => (
          <button key={code} type="button" className={language === code ? 'active' : ''} aria-pressed={language === code} onClick={() => setLanguage(code)}>
            {code.toUpperCase()}
          </button>
        ))}
      </div>
      {status === 'signed-in' ? (
        <>
          <Link to="/profile" className={`ops-rail-user${pathname === '/profile' ? ' active' : ''}`} title={email || (isVi ? 'Hồ sơ cá nhân' : 'Personal profile')}>
            <span className="ops-avatar" aria-hidden="true">{initial}</span>
            <span>{isVi ? 'Hồ sơ' : 'Profile'}</span>
          </Link>
          <button type="button" className="ops-signout" onClick={handleSignOut} disabled={busy} aria-busy={busy} title={isVi ? 'Đăng xuất khỏi hệ thống' : 'Sign out'}>
            {busy ? (isVi ? 'Đang thoát…' : 'Signing out…') : (isVi ? 'Đăng xuất' : 'Sign out')}
          </button>
          {failed && <span className="ops-signout-error" role="alert">{isVi ? 'Đăng xuất thất bại' : 'Sign out failed'}</span>}
        </>
      ) : (
        <Link to="/login" className="ops-rail-user" aria-label={isVi ? 'Đăng nhập tài khoản' : 'Sign in account'}>{isVi ? 'Đăng nhập' : 'Sign in'}</Link>
      )}
    </div>
  );
}

/**
 * Layout of every operations screen (spec 013, navigation decision of 2026-11-22): a rail of groups, and in the page
 * a header (breadcrumb, title, action buttons) with the tabs of the group. Navigation only; the backend decides
 * every permission.
 */
export default function OperationsShell() {
  const { pathname } = useLocation();
  const { language } = useLanguage();
  const role = useRole();
  const badges = useNavBadges(role);
  const [slot, setSlot] = useState(null);
  const isVi = language === 'vi';

  const here = locate(pathname);
  const groups = visibleGroups(role);
  const tabs = here ? visibleTabs(here.group, role) : [];
  const actions = here ? pageActions(here, pathname) : [];
  const count = key => badges.counts[key];

  return (
    <div className="ops-shell">
      <aside className="ops-rail" aria-label="Operations Navigation">
        <Link to="/home" className="ops-logo" aria-label="MLAND">M</Link>
        <nav className="ops-rail-nav" aria-label={isVi ? 'Nhóm chức năng' : 'Feature groups'}>
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
                <span>{label}</span>
                <Badge count={total} label={isVi ? `${total} việc chờ` : `${total} waiting`} />
              </Link>
            );
          })}
        </nav>
        <RailFooter isVi={isVi} />
      </aside>

      <div className="ops-page">
        {here && (
          <header className="ops-header">
            <nav className="ops-crumb" aria-label="Breadcrumb">
              <span>MLAND</span>
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
              <div className="ops-tabs" role="navigation" aria-label={isVi ? 'Trang trong nhóm' : 'Pages in this group'}>
                {tabs.map(tab => {
                  const active = tab.id === here.tab.id;
                  const total = count(tabKey(here.group, tab));
                  return (
                    <Link key={tab.id} to={tab.to} className={`ops-tab${active ? ' active' : ''}`} aria-current={active ? 'page' : undefined}>
                      {tabLabel(tab, language, role)}
                      <Badge count={total} label={isVi ? `${total} việc chờ` : `${total} waiting`} />
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
