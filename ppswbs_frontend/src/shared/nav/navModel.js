// Navigation model of the operations shell (spec 014, decision of 2026-11-22): 10 groups on a rail, each with at most
// five tabs. Group ids and tab ids are a contract: `useNavBadges` keys its counts by `groupId` and `groupId/tabId`.
// Visibility mirrors the old sidebar and is navigation only; the backend decides every permission. A tab that carries a
// waiting-work badge links to the list filtered to exactly the counted set (spec 014 FR-005).

// CARRIER (GHTK partner, Session 2026-11-23) sees only its own three pages, never the Mland operations groups.
const any = role => role !== 'CARRIER';
const manager = role => role === 'MANAGER';
const staffOrManager = role => role === 'MANAGER' || role === 'STAFF';
const notAdmin = role => role !== 'ADMIN' && role !== 'CARRIER';
const carrier = role => role === 'CARRIER';
const admin = role => role === 'ADMIN';

/**
 * `match`: exact path, or a prefix when it ends with `*`. `actions` are the header buttons of the tab's own page
 * (links; an in-page action registers itself with <HeaderActions>).
 */
export const GROUPS = [
  {
    id: 'home', icon: 'dash', vi: 'Dashboard', en: 'Dashboard',
    tabs: [
      { id: 'dashboard', vi: 'Dashboard', en: 'Dashboard', to: '/manager/dashboard', match: ['/manager/dashboard', '/manager/home'], visible: manager },
      { id: 'staff-home', vi: 'Trang chủ', en: 'Home', to: '/staff/home', match: ['/staff/home'], visible: role => role === 'STAFF' },
      { id: 'home', vi: 'Trang chủ', en: 'Home', to: '/home', match: ['/home'], visible: role => !staffOrManager(role) && !carrier(role) && !admin(role) },
      // ADMIN lands on account management (no Mland operations pages for Admin, spec 003.2 FR-012c).
      {
        id: 'admin-home', vi: 'Tài khoản', en: 'Accounts', to: '/admin/accounts', match: ['/admin/accounts*'], visible: admin,
        actions: [{ id: 'new-account', vi: 'Tạo tài khoản nội bộ', en: 'New internal account', to: '/admin/accounts/new', primary: true }],
      },
      { id: 'carrier-home', vi: 'Trang chủ', en: 'Home', to: '/carrier', match: ['/carrier'], visible: carrier },
    ],
  },
  {
    id: 'products', icon: 'box', vi: 'Sản phẩm', en: 'Products',
    tabs: [
      {
        id: 'list', vi: 'Danh sách sản phẩm', en: 'Product catalogue', to: '/catalogue-management/products', visible: staffOrManager,
        match: ['/catalogue-management/products', '/catalogue-management/products/*', '/catalogue-management/imports*'],
        actions: [
          { id: 'import', vi: 'Import Excel', en: 'Bulk import', to: '/catalogue-management/imports' },
          { id: 'new', vi: 'Thêm sản phẩm', en: 'New product', to: '/catalogue-management/products/new', primary: true },
        ],
      },
      { id: 'categories', vi: 'Danh mục', en: 'Categories', to: '/catalogue-management/categories', match: ['/catalogue-management/categories*'], visible: staffOrManager },
    ],
  },
  {
    id: 'promotions', icon: 'tag', vi: 'Ưu đãi', en: 'Promotions',
    tabs: [
      {
        id: 'programs', vi: 'Chương trình ưu đãi', en: 'Promotion programs', to: '/promotion-management/programs', visible: manager,
        match: ['/promotion-management/programs', '/promotion-management/programs/*'],
        actions: [{ id: 'new', vi: 'Tạo chương trình', en: 'New program', to: '/promotion-management/programs/new', primary: true }],
      },
      { id: 'point-policy', vi: 'Chính sách điểm', en: 'Point policy', to: '/promotion-management/point-policy', match: ['/promotion-management/point-policy*'], visible: manager },
    ],
  },
  {
    id: 'customers', icon: 'chat', vi: 'Khách hàng', en: 'Customers',
    tabs: [
      { id: 'design-review', vi: 'Duyệt thiết kế', en: 'Design review', to: '/design-review-requests', match: ['/design-review-requests*'], visible: staffOrManager },
      { id: 'custom-orders-ops', vi: 'Đơn gia công', en: 'Custom orders', to: '/staff/custom-orders', match: ['/staff/custom-orders*', '/manager/custom-orders*'], visible: staffOrManager },
      { id: 'consultations', vi: 'Tư vấn khách hàng', en: 'Customer consultation', to: '/operations/consultations', match: ['/operations/consultations*'], visible: staffOrManager },
      { id: 'reviews', vi: 'Đánh giá', en: 'Reviews', to: '/review-moderation', match: ['/review-moderation*'], visible: staffOrManager },
    ],
  },
  {
    id: 'retail', icon: 'shop', vi: 'Bán lẻ', en: 'Retail',
    tabs: [
      { id: 'orders', vi: 'Đơn bán lẻ chi nhánh', en: 'Branch retail orders', to: '/staff/retail-orders', match: ['/staff/retail-*'], visible: any },
      { id: 'reconciliation', vi: 'Đối soát thanh toán bán lẻ', en: 'Retail payment reconciliation', to: '/manager/retail-payment-reconciliations', match: ['/manager/retail-payment-reconciliations*'], visible: manager },
    ],
  },
  {
    id: 'workshop', icon: 'cal', vi: 'Workshop', en: 'Workshop',
    tabs: [
      {
        id: 'sessions', vi: 'Bảng điều phối ca', en: 'Session board', viStaff: 'Ca của tôi', enStaff: 'My sessions',
        to: '/operations/sessions', match: ['/operations/sessions*'], visible: notAdmin,
      },
      {
        id: 'locations', vi: 'Chi nhánh và khung giờ', en: 'Locations & slots', to: '/operations/admin?tab=locations', match: ['/operations/admin*', '/operations/locations', '/operations/slots'], visible: manager,
        actions: [{ id: 'new-slot', vi: 'Thêm khung giờ', en: 'Add slot', to: '/operations/admin?tab=slots', primary: true }],
      },
      {
        id: 'packages', vi: 'Gói workshop', en: 'Workshop packages', to: '/operations/packages', match: ['/operations/packages*', '/workshop-packages*'], visible: manager,
      },
      { id: 'availability', vi: 'Hiệu lực gói', en: 'Package availability', to: '/operations/package-availability', match: ['/operations/package-availability*'], visible: manager },
      { id: 'add-ons', vi: 'Add-on gói', en: 'Package add-ons', to: '/operations/package-add-ons', match: ['/operations/package-add-ons*'], visible: manager },
    ],
  },
  {
    id: 'collect', icon: 'cash', vi: 'Thu tiền', en: 'Collections',
    tabs: [
      { id: 'folio', vi: 'Folio vé', en: 'Ticket folio', to: '/settlement', match: ['/settlement'], visible: any },
      { id: 'cash-close', vi: 'Chốt sổ tiền mặt', en: 'Cash close', to: '/settlement/cash-close?view=confirmation', match: ['/settlement/cash-close*'], visible: any },
      { id: 'late-payments', vi: 'Thanh toán về trễ', en: 'Late payments', to: '/operations/late-payments', match: ['/operations/late-payments*'], visible: manager },
      { id: 'reconciliation', vi: 'Đối soát thu tiền', en: 'Reconciliation list', to: '/settlement/reconciliation', match: ['/settlement/reconciliation*'], visible: manager },
      { id: 'absorbed', vi: 'Khoản thiếu Mland chịu', en: 'Absorbed undercharges', to: '/settlement/absorbed-undercharges', match: ['/settlement/absorbed-undercharges*'], visible: manager },
    ],
  },
  {
    id: 'tickets', icon: 'ticket', vi: 'Vé và hoàn tiền', en: 'Tickets & refunds',
    tabs: [
      { id: 'ticket-actions', vi: 'Hủy và đổi vé', en: 'Cancel & change tickets', to: '/settlement/ticket-actions', match: ['/settlement/ticket-actions*'], visible: any },
      { id: 'change-requests', vi: 'Yêu cầu đổi vé', en: 'Change requests', to: '/settlement/change-requests?status=PENDING', match: ['/settlement/change-requests*'], visible: notAdmin },
      { id: 'refunds', vi: 'Hàng đợi hoàn tiền', en: 'Refund queue', to: '/settlement/refunds?status=WAITING', match: ['/settlement/refunds*'], visible: manager },
      { id: 'cases', vi: 'Sự vụ và bồi hoàn', en: 'Settlement cases', to: '/settlement/cases?tab=cases', match: ['/settlement/cases*'], visible: manager },
      { id: 'refund-key', vi: 'Thay khóa hoàn tiền', en: 'Refund key', to: '/settlement/refund-account-key', match: ['/settlement/refund-account-key*'], visible: manager },
    ],
  },
  {
    id: 'ota', icon: 'plug', vi: 'Kết nối OTA', en: 'OTA',
    tabs: [
      { id: 'channels', vi: 'Kênh đối tác', en: 'Channel mappings', to: '/ota-connectivity/channels', match: ['/ota-connectivity/channels*'], visible: manager },
      { id: 'reconciliation', vi: 'Đối soát đồng bộ', en: 'Reconciliation', to: '/ota-connectivity/reconciliation?status=NEEDS_ATTENTION', match: ['/ota-connectivity/reconciliation*'], visible: manager },
      { id: 'finance', vi: 'Bằng chứng tài chính', en: 'Finance evidence', to: '/ota-connectivity/finance', match: ['/ota-connectivity/finance*'], visible: manager },
      { id: 'add-ons', vi: 'Add-on tại xưởng', en: 'Shop add-ons', to: '/ota-connectivity/add-ons', match: ['/ota-connectivity/add-ons*'], visible: staffOrManager },
    ],
  },
  {
    id: 'system', icon: 'gear', vi: 'Hệ thống', en: 'System',
    tabs: [
      { id: 'roles', vi: 'Danh mục vai trò', en: 'Role catalogue', to: '/admin/roles', match: ['/admin/roles*'], visible: admin },
      { id: 'email-templates', vi: 'Mẫu thông báo email', en: 'Email templates', to: '/admin/notifications/templates', match: ['/admin/notifications/templates*'], visible: manager },
      { id: 'delivery-logs', vi: 'Nhật ký gửi thư', en: 'Delivery logs', to: '/admin/notifications/requests', match: ['/admin/notifications/requests*'], visible: manager },
      { id: 'audit', vi: 'Nhật ký audit', en: 'Audit log', to: '/operations/audit', match: ['/operations/audit*'], visible: manager },
    ],
  },
  // GHTK partner portal (CARRIER only, Session 2026-11-23): home is the "home" group's carrier tab.
  {
    id: 'carrier-orders', icon: 'truck', vi: 'Đơn hàng', en: 'Shipments',
    tabs: [{ id: 'orders', vi: 'Đơn hàng', en: 'Shipments', to: '/carrier/orders', match: ['/carrier/orders*'], visible: carrier }],
  },
  {
    id: 'carrier-stats', icon: 'dash', vi: 'Thống kê', en: 'Statistics',
    tabs: [{ id: 'stats', vi: 'Thống kê', en: 'Statistics', to: '/carrier/stats', match: ['/carrier/stats*'], visible: carrier }],
  },
];

const matches = (pattern, pathname) => (pattern.endsWith('*') ? pathname.startsWith(pattern.slice(0, -1)) : pathname === pattern);

/** The group and tab a path belongs to, whatever the role (a tab hidden from the role is still the place the page is in). */
export function locate(pathname, search = '') {
  // /operations/admin holds three in-page tabs. Locations and Slots belong to "Chi nhánh và khung giờ"; the Sessions one
  // (?tab=sessions) is session administration, so it takes the session tab of the workshop group, not the locations title.
  if (pathname.startsWith('/operations/admin') && new URLSearchParams(search).get('tab') === 'sessions') {
    const group = GROUPS.find(item => item.id === 'workshop');
    const tab = group?.tabs.find(item => item.id === 'sessions');
    if (group && tab) return { group, tab };
  }
  for (const group of GROUPS) {
    for (const tab of group.tabs) {
      if (tab.match.some(pattern => matches(pattern, pathname))) return { group, tab };
    }
  }
  return null;
}

/** Whether a path is served inside the operations shell. */
export const isShellPath = pathname => locate(pathname) !== null;

export const tabKey = (group, tab) => `${group.id}/${tab.id}`;

/** Tab label in the language, with the Staff wording where the tab has one. */
export function tabLabel(tab, language, role) {
  const vi = language === 'vi';
  if (role === 'STAFF' && tab.viStaff) return vi ? tab.viStaff : tab.enStaff;
  return vi ? tab.vi : tab.en;
}

export function groupLabel(group, language, role) {
  if (group.id === 'home' && role === 'STAFF') return language === 'vi' ? 'Trang chủ' : 'Home';
  if (group.id === 'home' && role !== 'MANAGER') return language === 'vi' ? 'Trang chủ' : 'Home';
  return language === 'vi' ? group.vi : group.en;
}

/** The tabs of a group this role sees; a group with none is not on the rail. */
export const visibleTabs = (group, role) => group.tabs.filter(tab => tab.visible(role));

export const visibleGroups = role => GROUPS
  .map(group => ({ group, tabs: visibleTabs(group, role) }))
  .filter(entry => entry.tabs.length > 0);

/** Header title: the tab's name, or the label of the action whose own page this is (e.g. "Thêm sản phẩm"). */
export function pageTitle({ tab }, pathname, language, role) {
  const action = (tab.actions || []).find(item => item.to === pathname);
  if (action) return language === 'vi' ? action.vi : action.en;
  return tabLabel(tab, language, role);
}

/** Header action links belong to the tab's own page only, not to its detail or create pages. */
export function pageActions({ tab }, pathname) {
  const own = tab.to.split('?')[0];
  return pathname === own ? (tab.actions || []) : [];
}
