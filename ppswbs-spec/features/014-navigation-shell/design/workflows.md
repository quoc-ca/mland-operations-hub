# Workflow Design: Manager Home

> **Status 2026-11-25:** describes the Manager Home wave-1 slice of the former `013-manager-home`. The page of cards, revenue card and shortcuts are superseded (2026-11-22); the shell and badges belong to `014-navigation-shell`, and today's operations and recent events to `010-manager-dashboard`. Kept as the history of the wave-1 design; not rewritten. The frontend slice is now `features/manager-dashboard/operations`.
**Date created**: 2026-11-18
**Date updated**: 2026-11-18
**Author**: Hồ Hoàng Quốc
**Last updated by**: Hồ Hoàng Quốc

**Feature**: [spec.md](../spec.md) | **Architecture**: [architecture.md](architecture.md) | **Reads**: [contracts/reads.md](../contracts/reads.md)

**Trạng thái**: `DRAFT/PROPOSED`. Chưa có `approval.md`. Tên đơn vị mã dùng đúng tên trong architecture.md (đều `proposed`). Trang chỉ đọc; không workflow nào ghi dữ liệu nghiệp vụ (spec FR-012), nên không có transaction, idempotency hay xung đột ghi.

## Workflow index

| ID | Workflow | Trigger và actor | Yêu cầu nguồn | Data flow | Trạng thái |
| --- | --- | --- | --- | --- | --- |
| WF-01 | Mở trang chủ Manager | Manager vào `/manager/home` | FR-001–011, FR-013 | DF-1 | proposed |
| WF-02 | Làm mới thủ công | Manager bấm "Làm mới" | FR-013 | DF-1 | proposed |
| WF-03 | Một thẻ/section lỗi rồi thử lại | Manager bấm thử lại trên phần lỗi | FR-006, FR-014 | DF-2 | proposed |
| WF-04 | Mất phiên khi đang dùng trang | Token hết hạn/không hợp lệ (401) | FR-018 | DF-3 | proposed |
| WF-05 | Người không phải Manager mở địa chỉ Manager | Staff/Admin/Member/chưa đăng nhập vào `/manager/home` | FR-001 | DF-4 | proposed |
| WF-06 | Đổi chi nhánh của dải hôm nay | Manager chọn chi nhánh | FR-008 | DF-5 | proposed |
| WF-07 | Đi tới trang chủ sở hữu | Manager bấm thẻ, doanh thu, sự kiện hoặc lối tắt | FR-004, FR-009–011 | DF-6 | proposed |

## WF-01: Mở trang chủ Manager

- Trigger và actor: Manager vào `/manager/home` (đích sau đăng nhập, hoặc gõ địa chỉ).
- Tiền điều kiện và quyền: có phiên Firebase; vai trò do máy chủ trả ở hồ sơ tài khoản là MANAGER. Việc ẩn/hiện chỉ là điều hướng; máy chủ quyết định quyền của từng read.
- Data flow: DF-1.

1. Router khớp `managerHomeRoutes` và dựng `ManagerHomePage`.
2. `ManagerHomePage` đọc `useAuth().status`; đang `loading` thì hiện `Loading`; chưa đăng nhập thì hiện lời nhắc đăng nhập quay về `/manager/home` (FR-018). Sau đó gọi hồ sơ `accounts/me/profile` bằng `useResource`. Vai trò khác MANAGER chuyển sang WF-05; không dựng section nào.
3. Khi vai trò là MANAGER, `ManagerHomePage` gọi `useHomeCards({ branch: 'all' })`.
4. `useHomeCards` tính `todayInVietnam` và `hoursAgoInstant(24)` bằng `vietnamTime`, rồi chạy song song các tải độc lập, mỗi tải qua một hàm của `homeReads`: các hàng đợi trong `registeredQueues()` (wave 1: `fetchLatePayments`, `fetchChangeRequests`, `fetchDesignReviews`), `fetchSessionsOfDay` + `fetchLocations`, `fetchMonthlyRevenue`, `fetchOperationsDenials` + `fetchPackageAvailabilityChanges`. Hàng đợi không có read không được tải và không được trả về (thẻ ẩn hoàn toàn).
5. Mỗi hàm `homeReads` gọi qua `http` của `useHttp`, rồi ánh xạ phản hồi sang view model (số đếm = số phần tử danh sách mặc định; audit chỉ giữ giờ, người thực hiện, action).
6. Mỗi kết quả cập nhật đúng phần của nó khi về tới; trang không chờ đủ tất cả. `lastUpdatedAt` được đặt theo giờ Việt Nam khi lần tải bắt đầu hoặc hoàn tất (xem Mở O-7 dưới).
7. Kết quả hiển thị: thẻ có số (thẻ bằng 0 làm mờ và gom "không có việc tồn đọng"; tất cả bằng 0 thì hiện trạng thái duy nhất "Không có việc tồn đọng trong các hàng đợi đang hiển thị"), dải hôm nay, doanh thu, sự kiện gần đây, lối tắt.

### Failure, recovery, and integrity

1. Một read lỗi (mạng, 5xx, 503): `useHomeCards` đặt `unavailable` cho đúng phần đó; thẻ hiện thông báo + thử lại và không bao giờ hiện 0 (FR-006). Các phần khác giữ nguyên số (SC-003).
2. Một read bị 403: phần đó không hiện dữ liệu; xử lý truy cập bị từ chối chung của `useHttp` chạy (FR-014); không suy số từ nguồn khác.
3. Doanh thu `PARTIAL`/`UNAVAILABLE`/rỗng/503: `RevenueCard` hiện thông báo riêng, không hiện số đoán; `developmentDemo` hiện dấu demo (FR-009).
4. Sự kiện: một nguồn Operations lỗi thì dùng nguồn còn lại kèm ghi chú thiếu dữ liệu; cả hai lỗi thì section không khả dụng; nguồn Settlement chưa giao (R-5 chưa có) hiện "chưa bao gồm", khác với "lỗi đọc" (FR-010). Liên kết `/operations/audit` chỉ hiển thị khi đọc Operations audit thành công.
5. Retry/đồng thời: không tự thử lại, không polling (FR-013). Nếu người dùng làm mới khi một tải đang chạy, kết quả của lần tải cũ bị bỏ (bằng mã định danh lần tải) để không ghi đè dữ liệu mới. Số đếm có thể lệch với danh sách nếu dữ liệu đổi giữa hai thời điểm; danh sách là nguồn đúng, lần làm mới sau cập nhật thẻ (edge case).
6. Hậu điều kiện: không dữ liệu nào bị ghi (FR-012); bằng chứng: `readOnly.test.jsx`, `useHomeCards.test.jsx`, quickstart #1, #2, #4, #12.

## WF-02: Làm mới thủ công

- Trigger và actor: Manager bấm "Làm mới" ở `ManagerHomePage`.
- Tiền điều kiện: như WF-01; trang đã dựng.
- Data flow: DF-1.

1. `ManagerHomePage` gọi `useHomeCards.reloadAll`.
2. `useHomeCards` tính lại ngày hôm nay và mốc 24 giờ (qua `vietnamTime`) rồi chạy lại các tải như WF-01 bước 4–6, giữ nguyên chi nhánh đang chọn.
3. Số cũ vẫn hiển thị cho đến khi số mới về (không nhấp nháy về trạng thái rỗng); nếu một phần lỗi thì phần đó chuyển `unavailable`.
4. `lastUpdatedAt` được cập nhật theo giờ Việt Nam.

### Failure, recovery, and integrity

1. Lỗi từng phần như WF-01 mục 1–4.
2. Bấm liên tiếp: lần tải sau thay thế lần trước (WF-01 mục 5).
3. Không có refresh theo timer ở bất kỳ nhánh nào. Bằng chứng: `useHomeCards.test.jsx`.

## WF-03: Một thẻ hoặc section lỗi rồi thử lại

- Trigger và actor: Manager bấm "Thử lại" trên thẻ/section đang `unavailable`.
- Data flow: DF-2.

1. Component của phần đó (`WaitingWorkSection`, `TodayStrip`, `RevenueCard`, `RecentEvents`) gọi `useHomeCards.reloadCard(id)` hoặc `reloadCard` tương ứng.
2. `useHomeCards` đặt riêng phần đó về `loading`, gọi lại đúng hàm của `homeReads` cho phần đó.
3. Thành công: phần đó chuyển `ready` với số mới; các phần khác không đổi và `lastUpdatedAt` của trang không đổi (ghi nhận giờ tải của phần đó nếu thiết kế UI yêu cầu — xem O-7).

### Failure, recovery, and integrity

1. Lại lỗi: phần đó giữ `unavailable` với thử lại; không hiện 0.
2. Hai thẻ lỗi cùng lúc: mỗi thẻ có thử lại riêng; không có thông báo lỗi chung (edge case).
3. Thẻ đã đếm được nhưng danh sách bị từ chối với Manager này: thẻ hiện không khả dụng, không hiện số một mình (edge case; FR-014).

## WF-04: Mất phiên khi đang dùng trang

- Trigger và actor: một read trả 401 hoặc lấy token thất bại (phiên hết hạn).
- Data flow: DF-3.

1. `http`/`useHttp` nhận 401, gọi `onSessionExpired` → `AuthProvider.reportSessionLost`.
2. `SessionNavigationBoundary` chuyển Manager tới đăng nhập, ghi nhớ `returnTo` = `/manager/home` (đã qua `safeReturnTo`).
3. `privateStateCleanup` chạy; `useHomeCards` bỏ mọi giá trị đang giữ (không giữ số của phiên trước).
4. Sau đăng nhập, Manager quay lại `/manager/home` và WF-01 chạy lại.

### Failure, recovery, and integrity

1. Nếu người đăng nhập lại là tài khoản khác (vai trò khác): WF-05 áp dụng; không hiện số của tài khoản trước.
2. Cơ chế mất phiên là cơ chế chung của feature 001; Manager Home không tự chọn chính sách điều hướng. Bằng chứng: `access.test.jsx`, quickstart #10.

## WF-05: Người không phải Manager mở địa chỉ Manager

- Trigger và actor: Staff, Admin, Member hoặc khách chưa đăng nhập vào `/manager/home`.
- Data flow: DF-4.

1. `ManagerHomePage` đọc trạng thái đăng nhập. Chưa đăng nhập: hiện lời nhắc đăng nhập (không hiện số).
2. Đã đăng nhập: đọc hồ sơ tài khoản. Staff được chuyển tới `roleHomePath('STAFF')`; Admin/Member được chuyển tới `/`, theo đúng cách trang chủ vận hành hiện có xử lý (`features/operatorhome/OperatorHomePage.jsx`).
3. Không có read số liệu nào được gọi trước khi vai trò được xác nhận là MANAGER.

### Failure, recovery, and integrity

1. Nếu việc đọc hồ sơ lỗi: hiện `ErrorNotice` có thử lại; không dựng section nào.
2. Quyền thật vẫn do máy chủ: dù UI bị vượt, mỗi read trả từ chối với người không phải Manager (FR-001, SC-006). Bằng chứng: `access.test.jsx`, quickstart #9.

## WF-06: Đổi chi nhánh của dải hôm nay

- Trigger và actor: Manager chọn một chi nhánh (hoặc "tất cả") trong `TodayStrip`.
- Data flow: DF-5.

1. `TodayStrip.onBranchChange` cập nhật state chi nhánh của `ManagerHomePage` (state cục bộ, chỉ trong lần truy cập, không lưu thành ưa thích; chi nhánh giữ nguyên qua làm mới trong cùng lần truy cập).
2. `useHomeCards({ branch })` chỉ tải lại dải hôm nay qua `homeReads.fetchSessionsOfDay(day, locationId)`; các thẻ hàng đợi, doanh thu và sự kiện không bị tải lại (FR-008).
3. Danh sách chi nhánh đến từ `homeReads.fetchLocations`, chỉ gồm chi nhánh máy chủ trả cho Manager.

### Failure, recovery, and integrity

1. Lỗi tải dải: chỉ dải hiện không khả dụng + thử lại; lựa chọn chi nhánh được giữ.
2. Tải chi nhánh lỗi: bộ chọn không khả dụng, dải vẫn hiện số cho "tất cả".
3. Hai lần đổi liên tiếp: kết quả của lần chọn cũ bị bỏ (như WF-01 mục 5). Ghi chú: F5 (tải lại trang) đặt lại về "tất cả"; "giữ qua làm mới" trong spec được hiểu là nút làm mới của trang, xem O-7.

## WF-07: Đi tới trang chủ sở hữu

- Trigger và actor: Manager bấm thẻ hàng đợi, thẻ doanh thu, liên kết nhật ký audit, hoặc lối tắt.
- Data flow: DF-6.

1. Component (`WaitingWorkSection`, `RevenueCard`, `RecentEvents`, `Shortcuts`) hiển thị `Link` tới đường dẫn đích đã đăng ký: danh sách của thẻ mang sẵn bộ lọc "waiting" để số đếm bằng số mục (FR-004); doanh thu → `/manager/dashboard`; sự kiện → `/operations/audit`; lối tắt → bốn đường dẫn trong `Shortcuts`.
2. Router chuyển trang; trang đích tự kiểm tra quyền và tải dữ liệu của nó.

### Failure, recovery, and integrity

1. Không có lời gọi API ở bước này. Nếu đích bị từ chối, xử lý của trang đích/khung chung áp dụng.
2. Số thẻ có thể lệch danh sách nếu dữ liệu đổi giữa hai thời điểm: danh sách là nguồn đúng (edge case).
3. Liên kết `/operations/audit` không hiển thị khi môi trường chưa có màn hình đó/đọc nguồn lỗi (edge case). Bằng chứng: `waitingWork.test.jsx`, `shortcuts.test.jsx`, quickstart #2, #7.

## Mở còn lại của workflow

| # | Điểm | Tác động | Người quyết |
| --- | --- | --- | --- |
| O-7 | Giờ "cập nhật lần cuối" là thời điểm bắt đầu hay hoàn tất lần tải; thử lại một thẻ có đổi giờ của cả trang không; "kept for the visit" của chi nhánh có sống qua F5 không | Quan sát được trên UI; spec FR-013/FR-008 chưa nói rõ | Tech Lead / chủ sở hữu spec |
| O-8 | Đối chiếu bộ lọc "waiting" mà link thẻ mang (FR-004) với từng trang danh sách thật: `/operations/late-payments`, `/settlement/change-requests`, `/design-review-requests` có nhận bộ lọc qua URL hay chỉ mặc định đúng | Nếu danh sách mặc định đã là "waiting" thì không cần bộ lọc; nếu không, thẻ không được hiển thị | Tech Lead / chủ sở hữu danh sách |
