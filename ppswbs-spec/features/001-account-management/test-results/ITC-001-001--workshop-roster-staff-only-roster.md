---
id: ITC-001-001
feature: 001-account-management
test_level: integration
test_case_type: B
source_ref: spec.md#FR-011,contracts/interfaces.md,tasks.md#T078,research.md#D-23
provider_class: edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountFacadeImpl
provider_method: findEligibleRoster
consumer_module: edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations
test_class: edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.AccountFacadeTargetAccountIT
test_method: findEligibleRosterReturnsActiveStaffWithProfileNameAndExcludesManagers
---

# ITC-001-001: Tích hợp Roster nhân sự Workshop — findEligibleRoster chỉ lọc Staff, loại trừ 100% Manager

**Ngày tạo**: 2026-11-14  
**Ngày cập nhật**: 2026-11-14  
**Người phụ trách**: Phan Sơn  
**Người cập nhật cuối**: Phan Sơn  

## 1. Mục tiêu và Bối cảnh (Goal & Context)

Kiểm chứng hợp đồng ranh giới module Modulith giữa Feature 001 (Account Management) và Feature 003 (Workshop Operations):
- Phương thức `AccountFacade.findEligibleRoster(Long workshopLocationId)` cung cấp danh sách nhân sự phụ trách lớp học cho một cơ sở Workshop cụ thể.
- Theo quyết định D-23 (Revision R-MGA), hợp đồng này chỉ trả về các ứng viên `STAFF` đang hoạt động có phân công cơ sở tương ứng; tuyệt đối không coi Manager là ứng viên nhân sự phụ trách và không dùng Roster làm cơ chế danh bạ Manager.

## 2. Tiền điều kiện dữ liệu (Preconditions & Fixtures)

- Có một cơ sở workshop mang mã định danh `workshopLocationId = 600L`.
- Dữ liệu tài khoản đã được nạp:
  - Tài khoản 1: Vai trò `STAFF`, trạng thái `ACTIVE`, hồ sơ có `full_name = "Staff Bravo"`, được gán vào Cơ sở 600.
  - Tài khoản 2: Vai trò `MANAGER`, trạng thái `ACTIVE`, hồ sơ có `full_name = "Manager Charlie"`, được gán vào cùng Cơ sở 600.

## 3. Các bước thực hiện kiểm thử (Execution Steps)

1. Khởi tạo ngữ cảnh kiểm thử tích hợp Spring context với cơ sở dữ liệu test chuẩn.
2. Gọi phương thức Facade mới:
   ```java
   RosterOutcome outcome = accountFacade.findEligibleRoster(600L);
   ```
3. Gọi phương thức deprecated tương thích ngược:
   ```java
   RosterOutcome legacyManagerOutcome = accountFacade.findEligibleRoster(600L, AccountRole.MANAGER);
   ```
4. Kiểm tra thành phần đối tượng `RosterOutcome` và các thuộc tính trả về.

## 4. Kết quả mong đợi (Expected Results)

- `outcome.status()` bằng `RosterStatus.AVAILABLE`.
- Danh sách ứng viên `outcome.entries()` có đúng 1 phần tử là Staff Bravo (`role == ActorRole.STAFF`).
- Manager Charlie bị loại trừ hoàn toàn khỏi danh sách ứng viên (không xuất hiện bất kỳ phần tử nào có `role == ActorRole.MANAGER`).
- `legacyManagerOutcome.status()` bằng `RosterStatus.AVAILABLE` và chứa danh sách rỗng (không ném ngoại lệ).
- Các entry trả về chỉ chứa `AccountId` và `fullName` từ Profile; không chứa thông tin email, số điện thoại, Firebase UID, mật khẩu hay tham chiếu entity JPA.

## 5. Bằng chứng thực thi (Execution Evidence)

<!-- EXECUTION:START -->
- Latest status: PASS
- Executed at: 2026-11-14 05:27:00
- Executed by: Phan Sơn
- Test suite: Maven Surefire — `AccountFacadeTargetAccountIT`
- Command: `mvnw.cmd test -Dtest=AccountFacadeTargetAccountIT#findEligibleRosterReturnsActiveStaffWithProfileNameAndExcludesManagers`
- Actual result: Test case chạy thành công 100%. Roster chỉ trả về Staff Bravo; Manager Charlie bị loại trừ chính xác theo hợp đồng D-23; không rò rỉ bất kỳ thông tin nhạy cảm nào qua ranh giới facade.
<!-- EXECUTION:END -->
