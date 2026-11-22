# Feature Specification: Manager Home

**Date created**: 2026-11-18
**Date updated**: 2026-11-22
**Author**: Hồ Hoàng Quốc
**Last updated by**: Hồ Hoàng Quốc

**Feature Branch**: `013-manager-home` (spec only; no git branch was created for it)

**Status**: Draft

**Input**: User description: "Trang chủ Manager (Manager home) cho ppswbs_frontend: giữ nguyên Dashboard điều hành (feature 010) là trang riêng. Trang chủ là bảng 'việc cần xử lý hôm nay': (1) hàng đợi cần xử lý có số đếm, bấm vào mở đúng danh sách: thanh toán về trễ, hàng đợi hoàn tiền, yêu cầu đổi vé chờ duyệt, chênh lệch chốt sổ tiền mặt chờ xác nhận, đối soát OTA lỗi, thiết kế khách chờ duyệt, đánh giá chờ kiểm duyệt; thẻ bằng 0 thì mờ/gom vào 'không có việc tồn đọng'; mỗi thẻ tải riêng, lỗi riêng; (2) dải vận hành hôm nay gọn: số ca hôm nay và tiến độ check-in, có bộ chọn chi nhánh, KHÔNG lặp doanh thu/lấp đầy của dashboard; (3) một thẻ gọn 'Doanh thu tháng này' lấy số từ cùng API của dashboard 010 và bấm vào mở /manager/dashboard; (4) vài dòng gần đây cần chú ý (lượt bị từ chối quyền, thay đổi ma trận gói) kèm lối sang Nhật ký audit /operations/audit; (5) lối tắt gọn cho việc làm nhiều. Backend vẫn quyết định quyền; trang chủ chỉ hiển thị."

## Scope and source alignment

Manager Home is the page a signed-in Manager lands on. Today it is a fixed menu of
cards that repeats the sidebar and shows no information. This feature turns it into a
**"what needs me today"** page: it shows how much work is waiting in each operational
queue, a short view of today's workshop operations, one revenue figure that leads to the
dashboard, a few recent events worth a look, and a small set of shortcuts.

The page **only reads and links**. It owns no business data, changes nothing, and does
not replace any screen it links to.

**Relationship to other features**

| Feature | Relationship |
| --- | --- |
| 010 Manager Dashboard | Stays a separate page and is unchanged. Manager Home shows one revenue figure that must equal the dashboard's figure and links to it. 010 states that its V1 contains no pending-work cards or operational-list cards, which is the gap this page fills. |
| 003.1 / 003.2 / 003.3 / 003.4 | Own the queues and workshop data the home counts: late payments, refund queue, ticket change requests, cash-close differences, OTA reconciliation, and today's sessions. The home never owns or edits them. |
| 003.2 US7 Audit review | Source of the "recent events" rows and the link to the audit log. |
| 004 Customer design review | Owns the design-review queue that the home counts. Review moderation (011) is not a queue of the home (Clarifications 2026-11-18). |
| 001 Account management | Role and sign-in. Staff keep their own home. |

**Out of scope:** the Staff home (unchanged), the dashboard's own content, any change of
the queues themselves, push notifications or alerts outside the page, and any file export.

## Clarifications

### Session 2026-11-18

- Q: Must the first version show all six queue cards at once, or may cards appear as their counts become available? → A: Cards ship incrementally. A card appears only when its count matches the owning list (FR-004); a queue without a working count is hidden entirely, not shown as unavailable.
- Q: Does the home get a card for reviews awaiting moderation? → A: No. Feature 011 has no "awaiting moderation" concept, so that card is dropped from Manager Home (owner decision Hồ Hoàng Quốc, 2026-11-18); the home has six queues and does not wait for the 011 owner.
- Q: Are cancelled sessions counted in "sessions today" on the today strip? → A: Yes. All of today's sessions are counted, cancelled ones included, with the number of cancelled ones shown separately ("of which N cancelled"). Check-in progress is measured over the sessions that are not cancelled.
- Q: Does the recent-events section include refused attempts from the Settlement audit as well as the Operations audit? → A: Yes, both sources, merged newest first. If one source cannot be read, the other is still shown with a note that part of the data is missing.
- Q: Should a queue card be highlighted when an item has waited too long, or show the count only? → A: Count only in the first version. No "waiting too long" marker; it can be added later once each queue's owner decides a threshold.

### Session 2026-11-22 (navigation shell; owner directive, design approved on a prototype)

Decision of Hồ Hoàng Quốc: the long sidebar is replaced by a navigation shell shared by Manager and Staff, and Manager Home is merged with the Manager
Dashboard (010). Approved on the prototype `claude.ai/artifact/AB3ZMi6aiCwUnS4X6rS28B` (simulated data). This session **supersedes** the FRs named below.

- **Rail and tabs.** A narrow rail of 10 groups (icon and short label), shown by role with the same rules the old sidebar used (Manager only, Manager and Staff,
  everyone but Admin); the backend still decides every permission. Inside a group the pages are tabs, at most 5 per group, no horizontal scrolling.
  Groups and tabs (ids in brackets; they are a contract, see "Badges"): Dashboard [`home`: Manager "Dashboard", Staff "Trang chủ" `/staff/home`] |
  Sản phẩm [`products`: Danh sách sản phẩm `list`, Danh mục `categories`] | Ưu đãi [`promotions`: Chương trình ưu đãi `programs`, Chính sách điểm `point-policy`] |
  Khách hàng [`customers`: Duyệt thiết kế `design-review`, Tư vấn khách hàng `consultations`, Đánh giá `reviews`] |
  Bán lẻ [`retail`: Đơn bán lẻ chi nhánh `orders`, Đối soát thanh toán bán lẻ `reconciliation`] |
  Workshop [`workshop`: Bảng điều phối ca (Staff: Ca của tôi) `sessions`, Chi nhánh và khung giờ `locations`, Gói workshop `packages`, Hiệu lực gói `availability`, Add-on gói `add-ons`] |
  Thu tiền [`collect`: Folio vé `folio`, Chốt sổ tiền mặt `cash-close`, Thanh toán về trễ `late-payments`, Đối soát thu tiền `reconciliation`, Khoản thiếu Mland chịu `absorbed`] |
  Vé và hoàn tiền [`tickets`: Hủy và đổi vé `ticket-actions`, Yêu cầu đổi vé `change-requests`, Hàng đợi hoàn tiền `refunds`, Sự vụ và bồi hoàn `cases`] |
  Kết nối OTA [`ota`: Kênh đối tác `channels`, Đối soát đồng bộ `reconciliation`, Bằng chứng tài chính `finance`, Add-on tại xưởng `add-ons`] |
  Hệ thống [`system`: Mẫu thông báo email `email-templates`, Nhật ký gửi thư `delivery-logs`, Nhật ký audit `audit`]. No former menu entry is dropped; the entries
  "Thêm sản phẩm", "Import Excel", "Tạo chương trình" and "Thêm khung giờ" are no longer menu entries but buttons in the page header.
- **Rail width and collapse.** The rail is 112px wide (icon above a 13px label). A toggle at the top collapses it to 60px, icon only; labels stay available as tooltips and to screen readers, and the badge stays visible. The choice is kept per browser (`localStorage`, key `ops-rail-collapsed`); the toggle is hidden on phone width (640px and below), where the rail is a horizontal bar. *(decision of Hồ Hoàng Quốc, 2026-11-22)*
- **Page header.** Every operations page has a header with breadcrumb, title, action buttons and the group's tabs. The own title of about 25 pages is removed so it
  does not repeat the header; the content below is unchanged. A page with its own header button (for example "Create package") registers it into the header.
- **Badges (replace FR-003 to FR-006).** The waiting-work queue cards are replaced by count badges on the rail and on the tabs. Hook `useNavBadges(role)` returns
  `{ status: 'loading' | 'ready', counts: { [groupId]: number, [`${groupId}/${tabId}`]: number } }`. Manager: the existing count reads of `homeReads.js`
  (design review `customers/design-review`, change requests `tickets/change-requests`, refund queue `tickets/refunds`, cash-close differences
  `collect/cash-close`, late payments `collect/late-payments`, OTA reconciliation `ota/reconciliation`), read once on entry, cached 30 seconds, refreshed every 60 seconds
  while the page is visible. A group count is the sum of its tabs' counts and exists only when every one of them was read. A count that cannot be read has no key and no
  badge; the rail keeps working. Zero shows no badge. Staff: `counts` is empty for now (the Staff badges are a later change by another owner). The tab that carries a
  badge opens the list filtered to exactly the counted set, so FR-004 still holds.
- **Dashboard (replaces FR-002, FR-009, FR-011, FR-013, FR-015).** One page, `/manager/dashboard`; `/manager/home` redirects to it. Blocks, each loading and failing on
  its own: *Vận hành hôm nay* (FR-007, FR-008 unchanged), *Doanh thu và hiệu suất* (the whole 010 report: filters, KPI
  cards, trends, branch table; default period is the current month) and *Việc gần đây cần chú ý* (FR-010 unchanged). The queue-count table and the shortcuts are removed
  (badges replace the counts; the rail replaces the shortcuts). There is no timer and no refresh button: reloading the page refreshes (010 FR-023). The role check
  (FR-001, FR-018) is unchanged. The Staff home is unchanged.
- **No "needs attention now" block (dropped).** The prototype showed a block for overdue items. Decision of Hồ Hoàng Quốc, 2026-11-22: it is dropped from V1 and not built. "Overdue" has no
  definition and no owner read, and the waiting counts already show on the rail badges. It can be added later, once the queue owners define a threshold (as in Clarification 5).
- **Not in this change:** the Ctrl K search box, Staff badges, and dedicated count endpoints.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - See what is waiting for me (Priority: P1)

A Manager opens the home page and sees a card for each operational queue with the number
of items waiting. Opening a card goes straight to that queue's list. When nothing is
waiting, the page says so plainly instead of showing empty boxes.

**Why this priority**: This is the whole reason for the page. Without it the Manager has to open
every screen to find out whether anything needs attention.

**Independent Test**: Sign in as a Manager with items waiting in at least two queues and none in
another; open the home page; check each count against the matching list; open one card.

**Acceptance Scenarios**:

1. **Given** a Manager with 3 late payments and 2 refund requests waiting, **When** the home page opens,
   **Then** the late-payments card shows 3, the refund card shows 2, and each opens its own list.
2. **Given** a queue with nothing waiting, **When** the home page opens, **Then** its card appears dimmed
   and grouped as "no outstanding work" rather than as a number to act on.
3. **Given** every queue is empty, **When** the home page opens, **Then** the page shows one clear
   "no outstanding work" state.
4. **Given** one queue's data cannot be loaded, **When** the home page opens, **Then** only that card shows
   an unavailable state with a retry, and every other card still shows its count.
5. **Given** a card shows N items, **When** the Manager opens it, **Then** the list contains exactly those N items
   with no filter the Manager must clear first.

---

### User Story 2 - Check today's operations (Priority: P2)

A Manager sees how many workshop sessions run today and how far check-in has progressed,
for all branches or for one chosen branch. It shows **operations only**: no revenue and no utilization,
because the dashboard owns those.

**Why this priority**: It answers "is today running normally" in one glance, but the queues matter more.

**Independent Test**: With sessions scheduled today at two branches, view all branches, then pick one,
and compare with the session board.

**Acceptance Scenarios**:

1. **Given** 6 sessions today across two branches, **When** the Manager views all branches, **Then** the strip shows 6
   sessions and the combined check-in progress.
2. **Given** the Manager picks one branch, **When** the selection is applied, **Then** the strip shows only that
   branch's sessions and progress, and the queue cards are unaffected.
3. **Given** no session is scheduled today, **When** the home page opens, **Then** the strip says there are no sessions today.
4. **Given** 6 sessions today of which 1 is cancelled, **When** the home page opens, **Then** the strip shows 6 sessions with "of which 1 cancelled",
   and the check-in progress covers only the 5 sessions that are not cancelled.

---

### User Story 3 - See this month's revenue and open the dashboard (Priority: P2)

A Manager sees one compact figure, "this month's revenue", and can open the Manager Dashboard from it.
The figure is the same number the dashboard shows for the same period.

**Why this priority**: It keeps a single headline number on the landing page without duplicating the dashboard.

**Independent Test**: Compare the home figure with the dashboard's figure for the current month and all branches.

**Acceptance Scenarios**:

1. **Given** verified revenue exists for the current month, **When** the home page opens, **Then** the card shows the
   dashboard's current-month total in VND.
2. **Given** the Manager opens the card, **When** it is activated, **Then** the Manager Dashboard opens.
3. **Given** the revenue source is unavailable or partial, **When** the home page opens, **Then** the card says so and never
   shows a guessed or zero value; the rest of the page is unaffected.

---

### User Story 4 - Notice recent events (Priority: P3)

A Manager sees the few most recent events worth a look (access attempts that were refused, and package
availability changes) and can open the audit log for more.

**Why this priority**: It surfaces risk without hunting, but it is secondary to queues and depends on the audit screen.

**Independent Test**: Produce a refused access attempt and a package availability change; both appear, newest first; the audit link opens the audit log.

**Acceptance Scenarios**:

1. **Given** recent refused attempts and a package availability change, **When** the home page opens, **Then** the newest few
   appear with time, who, and what, and a link opens the audit log.
2. **Given** none in the period, **When** the home page opens, **Then** the section says nothing recent.
3. **Given** the audit data is unavailable, **When** the home page opens, **Then** the section shows an unavailable state without
   affecting other sections.
4. **Given** a refused attempt in the Settlement audit and a package availability change in the Operations audit, **When** the home page opens,
   **Then** both appear in one list, newest first.
5. **Given** the Settlement audit cannot be read but the Operations audit can, **When** the home page opens, **Then** the Operations events appear with a note
   that part of the data is missing.
6. **Given** the Settlement audit cannot yet be narrowed to refused attempts, **When** the home page opens, **Then** the Operations events appear and the section says
   the Settlement source is not yet included.

---

### User Story 5 - Reach frequent tasks quickly (Priority: P3)

A Manager has a short row of shortcuts to the screens used most.

**Why this priority**: Convenience only; the sidebar already reaches every screen.

**Independent Test**: Each shortcut opens its screen.

**Acceptance Scenarios**:

1. **Given** the home page, **When** a shortcut is activated, **Then** the matching screen opens.

---

### Edge Cases

- A queue has no working count yet: its card is not shown, and "no outstanding work" (FR-005) only claims the queues that are shown.
- Every shown queue is zero but some queues are not shipped yet: the page says there is no outstanding work in the queues it covers, not that nothing is waiting anywhere.
- A queue count is large (hundreds): the card shows the real number; opening it shows the owning list, which pages itself.
- A queue returns a count but the list is refused to this Manager: the card shows unavailable, never the number alone.
- The session expires while the page is open: the Manager is returned to sign-in and then to the home page.
- A non-Manager opens the Manager home address: no Manager figure is shown and they are sent to their own home.
- The Manager picks a branch and then refreshes: the branch choice is kept for the visit; it is not a saved preference.
- Two cards fail at once: each shows its own retry; the page does not show one general error.
- A count changes between the home view and opening its list: the list is authoritative; the next refresh updates the card.
- The audit screen is not yet available in an environment: the recent-events section is hidden or unavailable and no link points to a missing page.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001** *(access)*: Only an active Manager can see Manager Home content. Staff, Admin, Member and signed-out
  visitors see none of its figures; Staff are sent to their own home. The server decides access; hiding a menu entry is not the control.
- **FR-002** *(sections)*: The page shows, in this order: waiting work (FR-003 to FR-006), today's operations (FR-007), revenue (FR-009),
  recent events (FR-010), shortcuts (FR-011). *(superseded 2026-11-22: sections are Today, Revenue, Recent events; see Session 2026-11-22.)*
- **FR-003** *(queues)*: The page shows one card for each of these queues, each with the number of items waiting and a link to the
  owning list: late payments, refund queue, ticket change requests awaiting a decision, cash-close differences awaiting
  confirmation, OTA reconciliation items needing attention, customer designs awaiting review (six queues; review moderation is not one of them).
  The cards ship incrementally: a card is shown only once its queue has a count that satisfies FR-004. A queue without such a count is
  hidden entirely; it is neither shown as unavailable (FR-006) nor as zero (FR-005). Adding a queue's card later does not change the others. *(superseded 2026-11-22: queue counts are rail and tab badges; the count-equals-list rule FR-004 still applies to them.)*
- **FR-004** *(count equals list)*: A card's number MUST equal the number of items the linked list shows when it is opened from the
  card. The card's link carries the filter that defines "waiting" for that queue, so the Manager has no filter to set or clear. A card MUST NOT show a number it cannot back with that list.
- **FR-005** *(nothing waiting)*: A queue with zero items is shown dimmed and grouped as "no outstanding work". When every shown queue is
  zero, the page shows one clear state worded "No outstanding work in the queues shown here" (Vietnamese: "Không có việc tồn đọng trong các hàng đợi đang hiển thị"), so a queue that is not shipped yet is never implied to be empty. *(superseded 2026-11-22: a zero count shows no badge; there are no dimmed cards.)*
- **FR-006** *(independent cards)*: Each shown card loads on its own. A card whose data is loading shows a loading state; a card that
  fails at runtime shows an unavailable state with its own retry. An unavailable card MUST NOT display 0. This differs from a queue
  whose card is not shipped yet (FR-003), which is hidden, not unavailable. *(applies 2026-11-22 to the dashboard blocks; a failed count has no badge instead of an unavailable card.)*
- **FR-007** *(today's operations)*: The page shows the number of workshop sessions scheduled today, cancelled ones included, with the number of cancelled
  sessions shown separately, and the check-in progress (participants checked in out of participants expected, over the sessions that are not cancelled), for all branches or one
  branch chosen by the Manager. "Expected" means the participants who hold a valid ticket for those sessions; it is not the sessions' capacity, which is what the
  dashboard's utilization uses (proposed definition; the workshop operations owner confirms it). "Today" is the Vietnam calendar day (UTC+7). This
  section MUST NOT show revenue or workshop utilization.
- **FR-008** *(branch choice)*: The branch selection applies only to today's operations. It defaults to all branches, offers only branches the
  Manager may see, and is kept for the visit only.
- **FR-009** *(revenue card)*: The page shows one revenue figure for the current calendar month so far, all permitted branches, in VND. It MUST equal the Manager
  Dashboard's figure for the same period and scope, which is all the branches the Manager is assigned to ("all" in the dashboard's branch filter); a Manager assigned to fewer branches than exist sees the
  revenue of those branches only. Activating it opens the Manager Dashboard. When the figure is unavailable or partial, the card
  says so and MUST NOT show a guessed value. *(superseded 2026-11-22: the dashboard shows the full 010 revenue report instead of a card.)*
- **FR-010** *(recent events)*: The page shows up to five of the most recent events from the last 24 hours, newest first, each with time, who and what, limited
  to what the audit review already exposes (no personal data), and a link to the audit log. The events are refused access attempts from both the Operations audit and the
  Settlement audit, and package availability changes from the Operations audit, merged into one list. If one audit source cannot be read, the events of the other are still
  shown with a note that part of the data is missing; the section is unavailable only when neither source can be read. A source that has not been delivered yet (the
  Settlement audit cannot be narrowed to refused attempts until its owner adds that) is stated as "not yet included", which is different from a failed read.
- **FR-011** *(shortcuts)*: The page offers a short fixed row of shortcuts to the most used screens. The sidebar remains the full navigation. *(removed 2026-11-22: the rail replaces shortcuts.)*
- **FR-012** *(read-only)*: Nothing on the page creates, changes or deletes business data.
- **FR-013** *(refresh)*: The page loads its data when it opens and on an explicit refresh by the Manager; it MUST NOT refresh on a timer. It shows when the
  data was last updated, in Vietnam time (UTC+7). *(2026-11-22: the dashboard follows 010 FR-023 — loads on open, no refresh button, no timer. The navigation badges refresh every 60 seconds; they are not dashboard data.)*
- **FR-014** *(refusal)*: If the server refuses a card's data, the card shows no data from that source and the shared access-denied handling applies; no number is
  inferred from another source.
- **FR-015** *(separate dashboard)*: The Manager Dashboard remains its own page with its own entry in the sidebar; this feature does not change its content, filters or rules. *(superseded 2026-11-22: Manager Home and the Dashboard are one page at `/manager/dashboard`.)*
- **FR-016** *(language and layout)*: Labels appear in Vietnamese and English following the existing language choice. The page is usable at 375 px width with cards stacked and no horizontal
  scrolling.
- **FR-017** *(accessibility)*: Every figure has a text label and state is never conveyed by colour alone; cards are reachable and operable by keyboard.
- **FR-018** *(session)*: A lost session returns the Manager to sign-in and back to Manager Home.

### Key Entities *(include if feature involves data)*

- **Waiting-work card**: One queue's name, the count of items waiting, and the link to the owning list. The queue and its items belong to the owning feature; the home keeps no copy.
- **Today's operations summary**: Sessions scheduled today and check-in progress for the chosen scope. Owned by workshop operations.
- **Monthly revenue figure**: The current-month verified revenue total. Owned by the Manager Dashboard feature.
- **Recent event**: A refused access attempt or package availability change with time, who and what. Owned by the audit review.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A Manager can tell whether anything is waiting for them within 5 seconds of the page loading on a normal connection.
- **SC-002**: Opening any non-zero card shows the matching list in one step, and the card's number equals the list's count in 100% of checks.
- **SC-003**: When any one source fails, every other section still shows its data in 100% of checks, and no failed source is shown as 0.
- **SC-004**: The revenue figure equals the Manager Dashboard's figure for the same period and scope in 100% of checks.
- **SC-005**: The page works at 375 px width with no horizontal scrolling and all sections reachable.
- **SC-006**: No Staff, Admin, Member or signed-out visitor sees any Manager Home figure in any check.
- **SC-007**: A Manager reaches the list of each shown queue card from the home page in at most one click, and no card is shown for a queue whose count does not match its list.

## Assumptions

- The queues and today's session data already exist in their owning features; where a feature offers a list but no way to count it, adding a count is part of delivering that card and does not change the queue itself. Until it exists, that card is hidden (see Clarifications).
- A card's "waiting" means the items the owning feature defines as awaiting a Manager's action; the owner uses that one definition for the count and for the list the card opens (the card's link carries the matching filter where the owner's list also holds closed items).
- Manager authority is global across branches for Feature 003, so the queue cards and today's operations cover every branch their sources return. The revenue card follows the Manager Dashboard, which is limited to the branches assigned to the Manager (010 FR-006); for a Manager assigned to fewer branches than exist, the revenue card can therefore cover fewer branches than the other sections. The page does not hide this difference.
- The recent-events section relies on the audit review of 003.2 (US7), which is being delivered separately; until it is available in an environment the section is hidden or unavailable.
- Up to five recent events from the last 24 hours is a proposed default, not a decided limit; the owner may change it.
- The shortcut set (for example Workshop packages, Package availability, Locations and slots, Session board) is a proposal for the owner to confirm.
- The revenue card reuses the dashboard's own figure, so no second definition of revenue exists.
- The Staff home keeps its current fixed cards; any change to it is a separate decision.
- No new notification channel, export, or saved preference is part of V1. A queue card shows its count only; marking items that have waited too long is out of scope for V1 (see Clarifications).
- Users have the same connectivity and browser baseline as the rest of the staff area.

# Route masking synchronization (2026-11-20)

`/manager/home` has the exact browser-route allowlist `MANAGER`. Direct navigation by `MEMBER`, `STAFF`, `ADMIN`, or `CARRIER` is replaced to `/404` before any Manager-home card request. This does not infer that an authorized Manager has every linked operational action; every destination and API remains feature-authorized.
