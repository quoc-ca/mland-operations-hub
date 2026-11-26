import { describe, expect, it } from 'vitest';
import { GROUPS, isShellPath, locate, pageActions, pageTitle, tabKey, tabLabel, visibleGroups, visibleTabs } from './navModel';

// Every link of the old sidebar (spec 014, decision of 2026-11-22: no menu item is dropped).
const OLD_MANAGER_LINKS = [
  '/manager/dashboard', '/catalogue-management/products', '/catalogue-management/categories',
  '/promotion-management/programs', '/promotion-management/point-policy',
  '/design-review-requests', '/operations/consultations', '/review-moderation',
  '/staff/retail-orders', '/manager/retail-payment-reconciliations',
  '/operations/sessions', '/operations/admin?tab=locations', '/operations/packages', '/operations/package-availability', '/operations/package-add-ons',
  '/settlement', '/settlement/cash-close', '/operations/late-payments', '/settlement/reconciliation', '/settlement/absorbed-undercharges',
  '/settlement/ticket-actions', '/settlement/change-requests', '/settlement/refunds', '/settlement/cases?tab=cases',
  
  '/admin/notifications/templates', '/admin/notifications/requests', '/operations/audit',
];
// Pages that became header buttons or sub-pages of a tab.
const FORMER_MENU_PAGES = [
  '/catalogue-management/products/new', '/catalogue-management/imports', '/promotion-management/programs/new',
  '/operations/locations', '/operations/slots', '/workshop-packages', '/staff/retail-shipments',
];

const links = role => visibleGroups(role).flatMap(entry => entry.tabs.map(tab => tab.to));
// The tabs that carry a waiting-work badge open the list filtered to the counted set (FR-004).
const FILTERED = {
  '/settlement/cash-close': '/settlement/cash-close?view=confirmation',
  '/settlement/change-requests': '/settlement/change-requests?status=PENDING',
  '/settlement/refunds': '/settlement/refunds?status=WAITING',
};
const filtered = link => FILTERED[link] || link;

describe('navigation model', () => {
  it('has the nine groups plus the two GHTK groups, each with at most five tabs, and unique ids', () => {
    expect(GROUPS.map(group => group.id)).toEqual(['home', 'products', 'promotions', 'customers', 'retail', 'workshop', 'collect', 'tickets', 'system', 'carrier-orders', 'carrier-stats']);
    GROUPS.forEach(group => expect(group.tabs.length).toBeLessThanOrEqual(5));
    const keys = GROUPS.flatMap(group => group.tabs.map(tab => tabKey(group, tab)));
    expect(new Set(keys).size).toBe(keys.length);
  });

  it('shows a Manager every old menu entry', () => {
    const shown = links('MANAGER');
    OLD_MANAGER_LINKS.forEach(link => expect(shown).toContain(filtered(link)));
  });

  it('keeps every former menu page inside some tab of the shell', () => {
    [...OLD_MANAGER_LINKS, ...FORMER_MENU_PAGES].forEach(link => expect(isShellPath(link.split('?')[0])).toBe(true));
  });

  it('shows a Staff the daily work and their own home, and hides the management areas', () => {
    const shown = links('STAFF');
    expect(shown).toContain('/staff/home');
    expect(shown).not.toContain('/manager/dashboard');
    ['/operations/sessions', '/catalogue-management/products', '/design-review-requests', '/staff/retail-orders', '/settlement', '/settlement/change-requests']
      .forEach(link => expect(shown).toContain(filtered(link)));
    ['/promotion-management/programs', '/operations/packages', '/operations/package-availability', '/operations/package-add-ons', '/operations/admin?tab=locations',
      '/settlement/cases?tab=cases', '/admin/notifications/templates', '/operations/audit', '/operations/late-payments']
      .forEach(link => expect(shown).not.toContain(link));
    const sessions = locate('/operations/sessions').tab;
    expect(tabLabel(sessions, 'vi', 'STAFF')).toBe('Ca của tôi');
    expect(tabLabel(sessions, 'vi', 'MANAGER')).toBe('Bảng điều phối ca');
  });

  it('shows an Admin account management as home and no workshop operations, with role catalogue removed (T113)', () => {
    const shown = links('ADMIN');
    expect(shown).toContain('/admin/accounts');
    expect(shown).not.toContain('/admin/roles');
    expect(shown).not.toContain('/home');
    expect(shown).not.toContain('/operations/sessions');
    expect(shown).not.toContain('/settlement/change-requests');
    ['/settlement', '/settlement/cash-close?view=confirmation', '/settlement/ticket-actions', '/staff/retail-orders', '/operations/audit']
      .forEach(link => expect(shown).not.toContain(link));
    expect(shown).toContain('/admin/notifications/templates');
    expect(shown).toContain('/admin/notifications/requests');
  });

  it('shows only the role-free entries while the role is unknown', () => {
    const shown = links(null);
    expect(shown).toContain('/home');
    expect(shown).not.toContain('/operations/package-availability');
    expect(shown).not.toContain('/catalogue-management/products');
  });

  it('shows a GHTK carrier only its home, shipments and statistics (Session 2026-11-23)', () => {
    expect(visibleGroups('CARRIER').map(entry => entry.group.id)).toEqual(['home', 'carrier-orders', 'carrier-stats']);
    expect(links('CARRIER')).toEqual(['/carrier', '/carrier/orders', '/carrier/stats']);
    ['/carrier', '/carrier/orders', '/carrier/stats'].forEach(path => expect(isShellPath(path)).toBe(true));
    expect(visibleGroups('MANAGER').map(entry => entry.group.id)).not.toContain('carrier-orders');
  });

  it('drops a group that has no tab for the role', () => {
    const staffGroups = visibleGroups('STAFF').map(entry => entry.group.id);
    expect(staffGroups).not.toContain('promotions');
    expect(staffGroups).not.toContain('system');
    expect(visibleTabs(GROUPS.find(group => group.id === 'system'), 'MANAGER')).toHaveLength(3);
  });

  it('puts create and detail pages in their tab with the right title and actions', () => {
    const created = locate('/catalogue-management/products/new');
    expect(created.group.id).toBe('products');
    expect(created.tab.id).toBe('list');
    expect(pageTitle(created, '/catalogue-management/products/new', 'vi', 'MANAGER')).toBe('Thêm sản phẩm');
    expect(pageActions(created, '/catalogue-management/products/new')).toEqual([]);

    const list = locate('/catalogue-management/products');
    expect(pageActions(list, '/catalogue-management/products').map(action => action.vi)).toEqual(['Import Excel', 'Thêm sản phẩm']);
    expect(pageActions(locate('/promotion-management/programs'), '/promotion-management/programs').map(action => action.vi)).toEqual(['Tạo chương trình']);
    expect(pageActions(locate('/operations/admin'), '/operations/admin').map(action => action.vi)).toEqual(['Thêm khung giờ']);
  });

  it('titles the administration Sessions tab as the session tab, not as locations and slots', () => {
    const locations = locate('/operations/admin', '?tab=locations');
    expect(pageTitle(locations, '/operations/admin', 'vi', 'MANAGER')).toBe('Chi nhánh và khung giờ');
    const slots = locate('/operations/admin', '?tab=slots');
    expect(pageTitle(slots, '/operations/admin', 'vi', 'MANAGER')).toBe('Chi nhánh và khung giờ');

    const sessions = locate('/operations/admin', '?tab=sessions');
    expect(sessions.group.id).toBe('workshop');
    expect(sessions.tab.id).toBe('sessions');
    expect(pageTitle(sessions, '/operations/admin', 'vi', 'MANAGER')).toBe('Bảng điều phối ca');
    // The "Thêm khung giờ" button belongs to locations and slots only.
    expect(pageActions(sessions, '/operations/admin')).toEqual([]);
  });

  it('does not take public or member pages into the shell', () => {
    ['/', '/products', '/member/cart', '/profile', '/login', '/reviews', '/design-requests/new'].forEach(path => expect(isShellPath(path)).toBe(false));
  });

  it('has both a Vietnamese and an English label on every group, tab and action', () => {
    GROUPS.forEach(group => {
      expect(group.vi && group.en).toBeTruthy();
      group.tabs.forEach(tab => {
        expect(tab.vi && tab.en).toBeTruthy();
        expect(Boolean(tab.viStaff)).toBe(Boolean(tab.enStaff));
        (tab.actions || []).forEach(action => expect(action.vi && action.en).toBeTruthy());
      });
    });
  });

  it('switches labels and page titles between vi and en', () => {
    const sessions = locate('/operations/sessions');
    expect(tabLabel(sessions.tab, 'en', 'STAFF')).toBe('My sessions');
    expect(tabLabel(sessions.tab, 'en', 'MANAGER')).toBe('Session board');
    const created = locate('/catalogue-management/products/new');
    expect(pageTitle(created, '/catalogue-management/products/new', 'en', 'MANAGER')).toBe('New product');
    expect(pageTitle(created, '/catalogue-management/products/new', 'vi', 'MANAGER')).toBe('Thêm sản phẩm');
  });
});
