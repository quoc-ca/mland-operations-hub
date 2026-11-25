# Architecture Design: Manager Home

> **Status 2026-11-25:** describes the Manager Home wave-1 slice of the former `013-manager-home`. The page of cards, revenue card and shortcuts are superseded (2026-11-22); the shell and badges belong to `014-navigation-shell`, and today's operations and recent events to `010-manager-dashboard`. Kept as the history of the wave-1 design; not rewritten. The frontend slice is now `features/manager-dashboard/operations`.
**Date created**: 2026-11-18
**Date updated**: 2026-11-18
**Author**: Hồ Hoàng Quốc
**Last updated by**: Hồ Hoàng Quốc

**Feature**: [spec.md](../spec.md) | **Plan**: [plan.md](../plan.md) | **Research**: [research.md](../research.md) | **Reads**: [contracts/reads.md](../contracts/reads.md)

**Trạng thái**: `DRAFT/PROPOSED`. Chưa có `approval.md`; mọi nút `proposed` bên dưới chưa phải baseline và chặn code phụ thuộc cho đến khi Tech Lead duyệt.

Ghi chú nguồn: đường dẫn frontend tính từ `ppswbs_frontend/src/`; đường dẫn backend tính từ `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/`. Các nút `existing` đã được kiểm tra trong mã nhánh `develop` ngày 2026-11-18.

## Scope and source map

| Requirement / scenario | Authoritative source | Architecture response | Task | Verification |
| --- | --- | --- | --- | --- |
| FR-001 chỉ Manager thấy số liệu; Staff về home riêng | spec FR-001; `features/operatorhome/OperatorHomePage.jsx` (cổng vai trò hiện có) | `ManagerHomePage` kiểm tra vai trò từ hồ sơ tài khoản, chuyển hướng bằng `roleHomePath`; server vẫn quyết định quyền từng read | T007, T035 | `access.test.jsx`; quickstart #9 |
| FR-002 thứ tự section | spec FR-002 | `ManagerHomePage` ghép section theo thứ tự cố định | T007 | `ManagerHomePage` test; T033 |
| FR-003/004/005/006 thẻ hàng đợi, đếm bằng danh sách, tải độc lập | spec FR-003–006, Clarification 1; research D1, D2 | `queues.js` (registry), `useHomeCards` (tải độc lập), `WaitingWorkSection` (hiển thị) | T010–T012 | `waitingWork.test.jsx`; T013 thủ công |
| FR-007/008 dải vận hành hôm nay + chọn chi nhánh | spec FR-007–008; research D4, D7 | `TodayStrip`, `homeReads.fetchSessionsOfDay`, `fetchLocations`, `vietnamTime` | T021–T024 | `todayStrip.test.jsx` |
| FR-009 doanh thu bằng dashboard | spec FR-009; research D5; `features/manager-dashboard/ManagerDashboardPage.jsx` | `RevenueCard` gọi cùng read `executive-reports` | T025–T027 | `revenueCard.test.jsx`; T027 thủ công |
| FR-010 sự kiện gần đây | spec FR-010; research D6 | `RecentEvents`, hai read Operations audit, read Settlement khi R-5 có | T028–T031 | `recentEvents.test.jsx` |
| FR-011 lối tắt | spec FR-011 | `Shortcuts` (danh sách cố định) | T032 | `shortcuts.test.jsx` |
| FR-012 chỉ đọc | spec FR-012 | `homeReads` chỉ có hàm GET | T009 | `readOnly.test.jsx` |
| FR-013 không refresh theo timer; hiện giờ cập nhật | spec FR-013 | `useHomeCards.reloadAll`, `lastUpdatedAt` (giờ VN) | T005, T009 | `useHomeCards.test.jsx` |
| FR-014 server từ chối | spec FR-014; `shared/api/useHttp.js` (`onAccessDenied`) | Dùng xử lý 403 chung của `useHttp`; thẻ không hiện số | T012 | `waitingWork.test.jsx` |
| FR-015 dashboard giữ nguyên | spec FR-015 | Không sửa `features/manager-dashboard/` | — | review diff |
| FR-016/017 ngôn ngữ, 375 px, truy cập bàn phím | spec FR-016–017 | `messages.js`, CSS riêng của slice | T006, T033 | T033; quickstart #11 |
| FR-018 mất phiên | spec FR-018; `shared/auth` (`SessionNavigationBoundary`, `privateStateCleanup`) | Tái dùng cơ chế chung; `useHomeCards` xóa dữ liệu khi mất phiên | T005, T035 | `access.test.jsx` |

## Implementation tree and ownership

```text
ppswbs_frontend/src/
  app/router.jsx                                   [existing — đăng ký route ở composition root; sửa nhỏ ở T008, proposed change]
  features/
    managerhome/                                   [proposed — plan.md "Source code"; research D8]
      routes.jsx                                   [proposed]
      ui/
        ManagerHomePage.jsx                        [proposed]
        WaitingWorkSection.jsx                     [proposed]
        TodayStrip.jsx                             [proposed]
        RevenueCard.jsx                            [proposed]
        RecentEvents.jsx                           [proposed]
        Shortcuts.jsx                              [proposed]
        messages.js                                [proposed]
        managerHome.css                            [proposed — T033, style riêng của slice]
      application/
        useHomeCards.js                            [proposed]
        queues.js                                  [proposed]
        vietnamTime.js                             [proposed]
      infrastructure/
        homeReads.js                               [proposed]
      tests/                                       [proposed]
    operatorhome/                                  [existing]
      routes.jsx                                   [existing — T008 gỡ mục /manager/home; Staff giữ nguyên]
      OperatorHomePage.jsx, operatorSections.js,
      operatorHome.test.jsx                        [existing — nhánh Manager bị gỡ ở T034]
    manager-dashboard/                             [existing — không đổi]
  shared/
    api/useHttp.js                                 [existing]
    auth/{AuthProvider.jsx, roleHome.js, ...}      [existing]
    i18n/I18nProvider.jsx                          [existing — useLanguage]
    ui/components.jsx                              [existing — Loading, ErrorNotice, useResource]
    ui/OperationsSidebar.jsx                       [existing — không đổi]

ppswbs_backend/.../features/                       [existing — chỉ chủ sở hữu thêm read; KHÔNG thuộc phạm vi phiên frontend này]
  workshopoperations/    R-1  day summary           [proposed — owner 003.2]
  workshopsettlement/    R-2, R-3, R-5              [proposed — owner 003.3]
  otaconnectivity/       R-4                        [proposed — owner 003.4]
```

**Quyền sở hữu và hướng phụ thuộc.** Slice `managerhome` sở hữu một trang và không sở hữu dữ liệu. Phụ thuộc một chiều: `ui → application → infrastructure → shared/api (useHttp)`. Slice chỉ import từ `shared/`; không import mã private của feature nào khác (constitution, frontend boundary). Các lối đi tới danh sách chủ sở hữu chỉ là đường dẫn (router link), không import. Route đăng ký ở `app/router.jsx` (composition root). Backend: không thêm module mới, mỗi chủ sở hữu thêm read trong package của mình ([plan.md](../plan.md) "Source code"). Việc nhận diện module cho Modulith do từng chủ sở hữu kiểm chứng bằng `ModulithArchitectureTests`; Manager Home không đổi ranh giới module nào.

## Production-unit blueprint

| Path | Type / status | Responsibility | Class / export | Draft public methods / props | Allowed dependencies | Source |
| --- | --- | --- | --- | --- | --- | --- |
| `features/managerhome/routes.jsx` | route list / proposed | Khai báo `/manager/home` | `managerHomeRoutes` | mảng route | `ManagerHomePage` | plan "Source code"; T007 |
| `features/managerhome/ui/ManagerHomePage.jsx` | component / proposed | Cổng vai trò, khung trang, nút làm mới, giờ cập nhật, thứ tự section | `ManagerHomePage` (default) | không có props | `useAuth`, `useHttp`, `useLanguage`, `useResource`, `roleHomePath`, các section, `useHomeCards`, `OperationsSidebar` | spec FR-001, FR-002, FR-013, FR-018 |
| `ui/WaitingWorkSection.jsx` | component / proposed | Thẻ hàng đợi, làm mờ khi 0, trạng thái "không có việc tồn đọng", retry từng thẻ | `WaitingWorkSection` | props: `cards`, `language` | `messages`, router `Link` | spec FR-003–006, FR-017 |
| `ui/TodayStrip.jsx` | component / proposed | Số ca hôm nay, ca hủy, (tiến độ check-in khi R-1), chọn chi nhánh | `TodayStrip` | props: `strip`, `locations`, `branch`, `onBranchChange`, `onRetry` | `messages` | spec FR-007, FR-008 |
| `ui/RevenueCard.jsx` | component / proposed | Doanh thu tháng, trạng thái partial/unavailable/demo, link dashboard | `RevenueCard` | props: `revenue`, `onRetry` | `messages`, router `Link` | spec FR-009; research D5 |
| `ui/RecentEvents.jsx` | component / proposed | ≤5 sự kiện, ghi chú nguồn thiếu/chưa có, link `/operations/audit` | `RecentEvents` | props: `events`, `onRetry` | `messages`, router `Link` | spec FR-010 |
| `ui/Shortcuts.jsx` | component / proposed | Hàng lối tắt cố định | `Shortcuts` | props: `language` | `messages`, router `Link` | spec FR-011 |
| `ui/messages.js` | messages / proposed | Nhãn vi/en | `managerHomeMessages` | object theo ngôn ngữ | — | spec FR-016 |
| `application/useHomeCards.js` | hook / proposed | Tải độc lập từng thẻ + strip + doanh thu + sự kiện; `lastUpdatedAt`; làm mới thủ công; bỏ dữ liệu khi mất phiên | `useHomeCards` | `useHomeCards({ branch })` → `{ cards, strip, revenue, events, lastUpdatedAt, reloadAll, reloadCard }` | `homeReads`, `queues`, `vietnamTime`, `useHttp` | spec FR-006, FR-013, FR-018 |
| `application/queues.js` | registry / proposed | Sáu hàng đợi: nhãn, `target` danh sách, hàm đếm (`null` khi chưa có read) | `QUEUES`, `registeredQueues` | `registeredQueues()` → chỉ hàng đợi có read | `homeReads` | spec Clarification 1; research D1–D3 |
| `application/vietnamTime.js` | util / proposed | "Hôm nay" `yyyy-MM-dd` và mốc 24 giờ trước theo UTC+7 | `todayInVietnam`, `hoursAgoInstant`, `formatVietnamTime` | `todayInVietnam(now)`, `hoursAgoInstant(now, hours)` | — | spec FR-007, FR-010; research D7 |
| `infrastructure/homeReads.js` | API client / proposed | Một hàm cho mỗi read hiện có ở contracts/reads.md mục A; ánh xạ DTO sang view model; bỏ trường lý do/metadata của audit | `homeReads` | `fetchLatePayments`, `fetchChangeRequests`, `fetchDesignReviews`, `fetchSessionsOfDay`, `fetchMonthlyRevenue`, `fetchOperationsDenials`, `fetchPackageAvailabilityChanges`, `fetchLocations` | `http` (từ `useHttp`) | contracts/reads.md A; T004 |

Các hàm của `homeReads` chỉ là GET; không có hàm ghi (FR-012). Các read R-1…R-5 chưa có hàm tương ứng cho đến khi chủ sở hữu giao (T017–T019, T024, T031).

## Libraries and integrations

| Layer | Library / version | Status | Purpose | Why | Source |
| --- | --- | --- | --- | --- | --- |
| FE | React 19, Vite | existing | UI | stack hiện có | plan "Technical Context" |
| FE | `react-router` | existing | route và link | dùng sẵn | plan |
| FE | `useHttp` (shared) | existing | gọi API có token, xử lý 401/403 chung | không `fetch` trực tiếp | `shared/api/useHttp.js` |
| FE | `useResource` (shared) | existing | tải + trạng thái loading/error | dùng sẵn | `shared/ui/components.jsx` |
| FE test | Vitest, Testing Library | existing | kiểm thử component | dùng sẵn | plan "Testing" |
| BE | Spring Boot / Modulith / JPA | existing | chỉ read của chủ sở hữu | không thêm | plan |

Không thêm thư viện mới. Tích hợp: 8 read hiện có của các feature khác (contracts/reads.md mục A); Manager Home không gọi bên thứ ba.

## Data flows

Mỗi luồng liên kết với workflow trong [workflows.md](workflows.md).

**DF-1 (WF-01, WF-02) Mở trang / làm mới**
```text
ManagerHomePage → useHomeCards.reloadAll → homeReads.{fetchLatePayments, fetchChangeRequests, fetchDesignReviews,
  fetchSessionsOfDay, fetchLocations, fetchMonthlyRevenue, fetchOperationsDenials, fetchPackageAvailabilityChanges}
→ http (useHttp) → owner controllers → response → mapper → view model → WaitingWorkSection / TodayStrip / RevenueCard / RecentEvents
error (một read): homeReads rethrow → useHomeCards ghi state "unavailable" cho đúng phần đó → component hiện thông báo + retry
```

**DF-2 (WF-03) Một thẻ lỗi rồi thử lại**
```text
WaitingWorkSection (nút retry) → useHomeCards.reloadCard(queueId) → homeReads.<fetch…> → http → owner controller → view model → thẻ
error: state "unavailable" của thẻ đó; các thẻ khác không đổi
```

**DF-3 (WF-04) Mất phiên**
```text
homeReads → http → 401 → useHttp.onSessionExpired → AuthProvider.reportSessionLost → SessionNavigationBoundary (sign-in, returnTo)
→ privateStateCleanup → useHomeCards bỏ giá trị → sau đăng nhập quay lại /manager/home → DF-1
```

**DF-4 (WF-05) Không phải Manager mở địa chỉ Manager**
```text
router → ManagerHomePage → useResource(accounts/me/profile) → vai trò ≠ MANAGER → Navigate(roleHomePath(vai trò)) / "/"
(không gọi bất kỳ read số liệu nào)
```

**DF-5 (WF-06) Đổi chi nhánh của dải hôm nay**
```text
TodayStrip.onBranchChange → ManagerHomePage (state branch) → useHomeCards({ branch }) → homeReads.fetchSessionsOfDay(day, locationId)
→ view model strip (các thẻ hàng đợi không tải lại)
```

**DF-6 (WF-07) Mở thẻ / doanh thu / sự kiện / lối tắt**
```text
WaitingWorkSection | RevenueCard | RecentEvents | Shortcuts → router Link → trang của chủ sở hữu (không gọi API)
```

## Gate review (D01–D20, tóm tắt)

| Gate | Kết quả | Chỗ trả lời / lý do |
| --- | --- | --- |
| D01 | MET | spec.md (User Stories, FR, SC) |
| D02 | NOT MET | Toàn bộ thiết kế là `proposed`; chưa có `approval.md` |
| D03 | MET | mục "Implementation tree and ownership" |
| D04 | MET | workflows.md WF-01…WF-07 |
| D05 | MET (phía FE) / phụ thuộc owner (R-x) | spec FR-001/014; workflows WF-05 |
| D06 | NOT MET cho R-1…R-5 | contracts/reads.md là proposal, chủ sở hữu chưa đồng ý (T001) |
| D07 | NOT APPLICABLE | data-model.md: không bảng/cột/migration |
| D08 | NOT APPLICABLE | trang không có state nghiệp vụ; chỉ state tải từng section (data-model "State changes") |
| D09 | MET | state cục bộ trong trang, bỏ khi mất phiên (spec FR-013/018; WF-04) |
| D10 | NOT APPLICABLE | không ghi dữ liệu, không transaction (spec FR-012) |
| D11 | MET | không retry tự động, không polling; thẻ độc lập (workflows WF-03) |
| D12 | NOT APPLICABLE | không tích hợp bên thứ ba |
| D13 | MET | data-model "Sensitive data": bỏ lý do/metadata audit |
| D14 | MET (mức tài liệu) | spec FR-005/006/016–018; chưa có `ui-flow.md` và chưa có thiết kế UI/Figma được duyệt (xem mở dưới) |
| D15 | NOT APPLICABLE | không đổi schema |
| D16 | NOT APPLICABLE | không đổi origin/CORS/token; dùng `useHttp` hiện có |
| D17 | NOT APPLICABLE | không đổi deployment; rollout là bật/tắt thẻ theo read của chủ sở hữu |
| D18 | MET | tasks.md |
| D19 | MET | plan "Verification plan"; quickstart |
| D20 | NOT MET | Còn quyết định mở bên dưới |

## Open decisions and dependencies

| # | Quyết định | Phương án / tác động | Người quyết | Chặn bàn giao? |
| --- | --- | --- | --- | --- |
| O-1 | Tech Lead duyệt thiết kế và ghi `approval.md` | Không thể tự cấp | Tech Lead (tên cần cung cấp) | Có — chặn mọi task code (T002 trở đi) |
| O-2 | Chủ sở hữu 003.2/003.3/003.4/010 đồng ý read R-1…R-5 và định nghĩa "waiting" | Chặn T014–T016, T023, T030 và các thẻ phụ thuộc (T017–T019, T024, T031); không chặn wave 1 | Chủ sở hữu từng module | Chỉ chặn phần phụ thuộc |
| O-3 | Chưa có thiết kế UI (Figma/`ui-flow.md`) được duyệt cho trang | Giao diện trong code chỉ dựa trên spec và kiểu trang hiện có; Tech Lead quyết định có cần Figma/`ui-flow.md` trước khi code UI | Tech Lead | Có thể — cần xác nhận khi duyệt |
| O-4 | Các giá trị đề xuất trong spec: tối đa 5 sự kiện, bộ lối tắt, định nghĩa "expected" | Là đề xuất chờ chủ sở hữu | Chủ sở hữu tương ứng | Không với wave 1 |
| O-5 | Đường dẫn danh sách đích trong `queues.js` (`/operations/late-payments`, `/settlement/change-requests`, `/design-review-requests`) lấy từ tasks.md T010; cần đối chiếu với router thật và với việc danh sách có mở đúng bộ lọc "waiting" (FR-004) | Nếu lệch, thẻ không được hiển thị | Tech Lead / chủ sở hữu danh sách | Có đối với wave 1 nếu lệch |
| O-6 | Quan hệ với blocker FE-DESIGN-0006 (403 bị `SessionNavigationBoundary` đưa về trang chủ): thẻ bị 403 sẽ kích hoạt cùng hành vi chung | Hành vi 403 chung thuộc feature 001; Manager Home không né | Hồ Hoàng Quốc / Phan Sơn | Không chặn, ghi nhận |
