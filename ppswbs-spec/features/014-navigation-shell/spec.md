# Feature Specification: Navigation Shell

> **2026-11-25: OTA đã bị loại** (feature 003.4): rail còn 9 nhóm (bỏ `ota`), còn 5 hàng đợi có badge, read R-4 bị gỡ. ID giữ nguyên; phần còn nhắc OTA là lịch sử. Xem [quyết định loại OTA](../003-workshop-management/ota-removal-2026-11-25.md).

**Date created**: 2026-11-22 (decisions); split out of `013-manager-home` on 2026-11-25
**Date updated**: 2026-11-28
**Author**: Hồ Hoàng Quốc
**Last updated by**: Hồ Hoàng Quốc

**Feature Branch**: `014-navigation-shell` (spec only; no git branch was created for it)

**Status**: Draft

**Origin**: This feature was `013-manager-home` ("Manager Home"). On 2026-11-22 the owner merged the
Manager Home page into the Manager Dashboard (010) and replaced the long sidebar with the navigation shell
described here. On 2026-11-25 the folder was renamed so that number 013 belongs only to
`013-staff-consultation-chat`. The dashboard blocks that used to be part of Manager Home (today's operations,
recent events) moved into `010-manager-dashboard` (Session 2026-11-25 and FR-034 to FR-038). The history of
the earlier Manager Home design stays in this folder (`plan.md`, `tasks.md`, `design/`, `approval.md`), and it
is not rewritten.

## Scope and source alignment

The navigation shell is the frame around every operations page used by Manager and Staff. It replaces the
long sidebar with a narrow **rail** of 9 groups, **tabs** inside a group, a **page header** (breadcrumb, title,
action buttons, tabs) and **count badges** that show how much work is waiting in each operational queue.

The shell **only reads, links and arranges**. It owns no business data, changes nothing, and does not replace
any screen it links to. Each page, queue and count belongs to its owning feature.

**Relationship to other features**

| Feature | Relationship |
| --- | --- |
| 010 Manager Dashboard | Owns the page `/manager/dashboard` and its blocks. The shell's `home` group opens it for a Manager. `/manager/home` redirects there. |
| 003.1 / 003.2 / 003.3 | Own the queues the badges count: late payments, refund queue, ticket change requests, cash-close differences. The shell never owns or edits them. |
| 004 Customer design review | Owns the design-review queue that the `customers/design-review` badge counts, and the custom-order list that the `customers/custom-orders-ops` badge counts. |
| 013 Staff consultation chat | Owns the consultation inbox that the `customers/consultations` badge counts. |
| 011 Review management | Has no "awaiting moderation" queue, so the `customers/reviews` tab has no badge (decision 2026-11-18). |
| 001 Account management | Role and sign-in. Decides which groups a role sees together with the route access matrix. |
| All other features | Their pages appear as tabs of the groups below; the shell never changes their content. |

**Out of scope:** the content of any page, the Ctrl K search box, new dedicated count endpoints
(each is a later change by the owning feature), push notifications, and any file export.

## Clarifications

### Session 2026-11-18 (as Manager Home)

- Q: Must the first version show all queue counts at once, or may they appear as they become available? → A: They ship incrementally. A count appears only when it matches the owning list (FR-005); a queue without a working count has no badge.
- Q: Is there a count for reviews awaiting moderation? → A: No. Feature 011 has no such concept, so it is dropped (owner decision Hồ Hoàng Quốc, 2026-11-18). Six queues are counted and the shell does not wait for the 011 owner. *(Five since 2026-11-25: the OTA queue was removed.)*
- Q: Should a count be highlighted when an item has waited too long? → A: No, count only in the first version. A threshold can be added once each queue's owner defines it.

### Session 2026-11-22 (navigation shell; owner directive, design approved on a prototype)

Decision of Hồ Hoàng Quốc: the long sidebar is replaced by a navigation shell shared by Manager and Staff,
and Manager Home is merged with the Manager Dashboard (010). Approved on the prototype
`claude.ai/artifact/AB3ZMi6aiCwUnS4X6rS28B` (simulated data). The rail width and collapse decision was added the same day.

- **No "needs attention now" block (dropped).** The prototype showed a block for overdue items. Dropped from V1 and not built: "overdue" has no definition and no owner read, and the waiting counts already show on the badges.
- **Not in this change:** the Ctrl K search box, Staff badges, and dedicated count endpoints.

### Session 2026-11-25 (split)

- Split out of `013-manager-home` and renumbered 014, so that two folders no longer share number 013. The shell requirements below are the 2026-11-22 decisions, regrouped; no behavior was changed by the split.

### Session 2026-11-26 (browser access)

- Q: Which roles may open the Staff home `/staff/home`? → A: STAFF only. A signed-in MEMBER, MANAGER, ADMIN or CARRIER is replaced to `/404` before any request; a Guest is asked to sign in (owner decision Hồ Hoàng Quốc).

### Session 2026-11-27 (ticket tabs; owner Hồ Hoàng Quốc)

- Q: What is the ticket cancel-and-change tab called and where does it sit? → A: "Hỗ trợ đổi vé và hủy" (English "Change & cancellation support"), after "Yêu cầu đổi vé". Tab ids, routes, visibility and badges are unchanged (FR-002 table).

### Session 2026-11-27 (Khách hàng badges; owner Hồ Hoàng Quốc)

- Q: Which waiting work of the `customers` group gets a badge besides design review? → A: Two. `customers/custom-orders-ops` for Staff and Manager counts the custom orders paid and waiting for production (the "Chờ chế tác" set). `customers/consultations` is shown to the Manager too, with the Staff definition: open conversations nobody has claimed. Custom orders waiting for the Manager's quote approval are not counted.

### Session 2026-11-28 (tab names; owner Hồ Hoàng Quốc)

- Q: Which tabs are renamed? → A: `consultations` "Tư vấn"; `orders` "Đơn bán lẻ"; retail `reconciliation` "Đối soát thanh toán"; `sessions` "Điều phối ca" (Staff stays "Ca của tôi"); `locations` "Chi nhánh và lịch ca"; `availability` "Chi nhánh áp dụng gói"; `add-ons` "Dịch vụ thêm của gói"; `late-payments` "Thanh toán trễ"; `cases` "Hồ sơ quyết toán"; `delivery-logs` "Nhật ký gửi email"; `audit` "Lịch sử thao tác". Ids, routes, visibility and badges are unchanged.
- Q: Which tab is current on `/operations/admin?tab=sessions`? → A: `locations`, like the other two sections of that page. This replaces the earlier rule that moved it to the `sessions` tab.

- Q: Which other names change? → A: The Manager `home` group and `dashboard` tab read "Tổng quan" in Vietnamese; the catalogue header button reads "Nhập từ Excel" (English "Excel import"); the carrier group and tab read "Vận đơn"; the English name of the collections `reconciliation` tab is "Collection reconciliation". The same day, menu entries, home cards and page headings outside the shell were aligned with the tab names.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Find a page through the rail and tabs (Priority: P1)

A Manager or Staff member signs in and sees a narrow rail of groups that fits their role. Choosing a group
opens its first tab; the other pages of the group are tabs in the page header.

**Independent Test**: Sign in as Manager, then as Staff; check the groups shown; open every tab of one group.

**Acceptance Scenarios**:

1. **Given** a Manager, **When** any operations page opens, **Then** the rail shows every group allowed for Manager and the page header shows the group's tabs (at most five, no horizontal scrolling).
2. **Given** a Staff member, **When** a page opens, **Then** the rail shows only the groups allowed for Staff, with the Staff home under the `home` group.
3. **Given** a page that registers a header button (for example "Create package"), **When** it opens, **Then** the button appears in the page header and not as a menu entry.

---

### User Story 2 - See what is waiting through badges (Priority: P1)

A Manager sees a count badge on the rail and on the tab of each queue that has items waiting. Opening the
badge's tab shows exactly those items.

**Independent Test**: Sign in as a Manager with items waiting in at least two queues and none in another; check each badge against its list.

**Acceptance Scenarios**:

1. **Given** 3 late payments and 2 refund requests waiting, **When** a page opens, **Then** `collect/late-payments` shows 3 and `tickets/refunds` shows 2, and the `collect` and `tickets` groups show the sums of their tabs.
2. **Given** a queue with nothing waiting, **When** a page opens, **Then** its tab shows no badge.
3. **Given** one queue's count cannot be read, **When** a page opens, **Then** only that tab has no badge, the groups that include it show no sum, and the rail keeps working.
4. **Given** a tab shows a badge of N, **When** the Manager opens it, **Then** the list contains exactly those N items with no filter to clear first.

---

### User Story 3 - Collapse the rail (Priority: P3)

A user collapses the rail to icons only to gain room, and the choice is remembered on that browser.

**Independent Test**: Collapse the rail, reload; it stays collapsed. Resize to phone width; it becomes a horizontal bar.

**Acceptance Scenarios**:

1. **Given** the rail is 112 px wide, **When** the toggle is used, **Then** it becomes 60 px with icons only, labels stay as tooltips and for screen readers, and badges stay visible.
2. **Given** phone width (640 px and below), **When** a page opens, **Then** the rail is a horizontal bar and the toggle is hidden.

---

### Edge Cases

- A non-Manager opens a Manager-only address: no Manager figure is shown and the user goes to their own home (or `/404` where the route access matrix says so).
- A count is large (hundreds): the badge shows the real number; the list pages itself.
- A count is returned but the list is refused to this Manager: the badge is not shown, never the number alone.
- Two counts fail at once: each tab simply has no badge; no general error is shown.
- A count changes between the badge and opening its list: the list is authoritative; the next refresh updates the badge.
- The session expires while a page is open: the user is returned to sign-in and then to the page.
- A destination page is not available in an environment: no tab links to a missing page.

## Requirements *(mandatory)*

### Route masking synchronization (2026-11-20)

`/manager/home` has the exact browser-route allowlist `MANAGER`. Direct navigation by `MEMBER`, `STAFF`, `ADMIN`, or `CARRIER` is replaced to `/404` before any request. For a Manager, `/manager/home` redirects to `/manager/dashboard` (Session 2026-11-22). This does not infer that an authorized Manager has every linked operational action; every destination and API remains feature-authorized.

`/staff/home` has the exact browser-route allowlist `STAFF` (Session 2026-11-26). Direct navigation by `MEMBER`, `MANAGER`, `ADMIN`, or `CARRIER` is replaced to `/404` before any request; a Guest is asked to sign in.

### Functional Requirements

- **FR-001** *(access)*: The rail shows a group to a role by the same rules the old sidebar used (Manager only, Manager and Staff, everyone but Admin). The server decides every permission; hiding a menu entry is not the control. Staff, Admin, Member and signed-out visitors see no Manager figure.
- **FR-002** *(rail and tabs)*: The rail has the 9 groups below. Inside a group the pages are tabs, at most 5 per group, no horizontal scrolling. The group and tab ids are a contract used by the badges. No former menu entry is dropped; "Thêm sản phẩm", "Nhập từ Excel", "Tạo chương trình" and "Thêm khung giờ" are buttons in the page header, not menu entries.

  | Group (id) | Tabs (id) |
  | --- | --- |
  | Tổng quan (`home`) | Manager "Tổng quan"; Staff "Trang chủ" `/staff/home` |
  | Sản phẩm (`products`) | Danh sách sản phẩm `list`, Danh mục `categories` |
  | Ưu đãi (`promotions`) | Chương trình ưu đãi `programs`, Chính sách điểm `point-policy` |
  | Khách hàng (`customers`) | Duyệt thiết kế `design-review`, Đơn gia công `custom-orders-ops`, Tư vấn `consultations`, Đánh giá `reviews` |
  | Bán lẻ (`retail`) | Đơn bán lẻ `orders`, Đối soát thanh toán `reconciliation` |
  | Workshop (`workshop`) | Điều phối ca (Staff: Ca của tôi) `sessions`, Chi nhánh và lịch ca `locations`, Gói workshop `packages`, Chi nhánh áp dụng gói `availability`, Dịch vụ thêm của gói `add-ons` |
  | Thu tiền (`collect`) | Hóa đơn vé `folio`, Chốt sổ tiền mặt `cash-close`, Thanh toán trễ `late-payments`, Đối soát thu tiền `reconciliation`, Khoản thiếu Mland chịu `absorbed` |
  | Vé và hoàn tiền (`tickets`) | Yêu cầu đổi vé `change-requests`, Hỗ trợ đổi vé và hủy `ticket-actions`, Hàng đợi hoàn tiền `refunds`, Hồ sơ quyết toán `cases` |
  | Hệ thống (`system`) | Mẫu thông báo email `email-templates`, Nhật ký gửi email `delivery-logs`, Lịch sử thao tác `audit` |

- **FR-003** *(rail width and collapse)*: The rail is 112 px wide (icon above a 13 px label). A toggle at the top collapses it to 60 px, icon only; labels stay available as tooltips and to screen readers, and the badge stays visible. The choice is kept per browser (`localStorage`, key `ops-rail-collapsed`). The toggle is hidden at 640 px and below, where the rail is a horizontal bar. *(decision of Hồ Hoàng Quốc, 2026-11-22)*
- **FR-004** *(page header)*: Every operations page has a header with breadcrumb, title, action buttons and the group's tabs. The own title of about 25 pages is removed so it does not repeat the header; the content below is unchanged. A page with its own header button registers it into the header.
- **FR-005** *(badges and count equals list)*: The rail and tabs show count badges. Hook `useNavBadges(role)` returns `{ status: 'loading' | 'ready', counts: { [groupId]: number, [`${groupId}/${tabId}`]: number } }`. A badge's number MUST equal the number of items the linked list shows when opened from the badge, and the tab opens that list filtered to exactly the counted set. A badge MUST NOT show a number it cannot back with that list.
  Manager counts: design review `customers/design-review`, custom orders `customers/custom-orders-ops`, consultations `customers/consultations`, change requests `tickets/change-requests`, refund queue `tickets/refunds`, cash-close differences `collect/cash-close`, late payments `collect/late-payments`.
  Staff counts: workshop tasks `workshop/sessions`, open branch orders `retail/orders`, change requests `tickets/change-requests`, design review, custom orders and consultations. See `contracts/reads.md`.
  Custom orders are those in the paid, waiting-for-production stage; the tab opens on that stage. Consultations are the `OPEN` ones with no claimant, within what the inbox returns to the caller. The inbox list is paged without a total: at most 4 pages of 50 are read, and if pages remain the number is used only when it is at least 100 (shown as "99+"), otherwise there is no badge.
  The consultations tab opens `/operations/consultations?status=OPEN&unclaimed=true`, the inbox filtered to "Chưa tiếp nhận"; the user can change the filter there. The filter is a 013 change (query parameter `unclaimed`), agreed with its owner as confirmed by Hồ Hoàng Quốc on 2026-11-27. The inbox loads 20 conversations at a time, so a larger set is reached through its "load more".
- **FR-006** *(group sums and failures)*: A group count is the sum of its tabs' counts and exists only when every one of them was read. A count that cannot be read has no key and no badge, and the rail keeps working. Zero shows no badge. An unreadable count is never shown as 0.
- **FR-007** *(refresh)*: Counts are read once on entry, cached 30 seconds, and refreshed every 60 seconds while the page is visible. The badges are not dashboard data, so the dashboard's "no timer" rule (010 FR-023) does not apply to them.
- **FR-008** *(read-only)*: The shell creates, changes or deletes no business data.
- **FR-009** *(refusal)*: If the server refuses a count, that badge shows no data from that source and the shared access-denied handling applies; no number is inferred from another source.
- **FR-010** *(language and layout)*: Labels appear in Vietnamese and English following the existing language choice. The shell is usable at 375 px width with no horizontal scrolling.
- **FR-011** *(accessibility)*: Every figure has a text label, state is never conveyed by colour alone, and the rail, tabs and badges are reachable and operable by keyboard.
- **FR-012** *(session)*: A lost session returns the user to sign-in and then back to the page.

### Key Entities *(include if feature involves data)*

- **Navigation group and tab**: An id, a label, the destination route, the roles allowed to see it. Owned by this feature; the destination page belongs to its feature.
- **Count badge**: A queue name, the count of items waiting, and the link to the owning list. The queue and its items belong to the owning feature; the shell keeps no copy.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A Manager can tell whether anything is waiting within 5 seconds of a page loading on a normal connection.
- **SC-002**: Opening any badged tab shows the matching list in one step, and the badge number equals the list's count in 100% of checks.
- **SC-003**: When any one count fails, every other badge and the rail still work in 100% of checks, and no failed count is shown as 0.
- **SC-004**: The shell works at 375 px width with no horizontal scrolling and every group reachable.
- **SC-005**: No Staff, Admin, Member or signed-out visitor sees any Manager-only group or figure in any check.
- **SC-006**: Every former sidebar entry is reachable as a rail group, a tab or a header button.

## Assumptions

- The queues already exist in their owning features; where a feature offers a list but no way to count it, adding a count is part of delivering that badge and does not change the queue itself. Until it exists, that badge is hidden.
- "Waiting" means the items the owning feature defines as awaiting action from the signed-in Manager or Staff; the owner uses that one definition for the count and for the list the badge opens.
- Manager authority is global across branches for Feature 003, so the counts cover every branch their sources return.
- The count reads of 003 and 004 are listed in `contracts/reads.md`; the owner-side reads proposed there (R-1 to R-5) are delivered by their owning features.
- The Staff home keeps its current fixed cards; any change to it is a separate decision.
- No new notification channel, export, or saved preference is part of V1, except the per-browser rail collapse.
- Users have the same connectivity and browser baseline as the rest of the staff area.
