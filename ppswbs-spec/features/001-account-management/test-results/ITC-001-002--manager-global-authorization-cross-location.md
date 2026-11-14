---
id: ITC-001-002
feature: 001-account-management
test_level: integration
test_case_type: N
source_ref: quickstart.md#Scenario V-07,spec-general.md,tasks.md#T079
provider_class: edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor
consumer_module: edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement
consumer_class: edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.WorkshopSettlementAuthorizationService
consumer_method: requireManagerOrStaff
test_class: edu.fpt.sep490_g22.ppswbs_backend.workshopsettlement.WorkshopSettlementAuthorizationServiceTest
test_method: managerAssignedToLocationACanActOnLocationBAndWithUnavailableScope
---

# ITC-001-002: Tích hợp Quyết toán Workshop — Manager thao tác toàn cục xuyên cơ sở vs Staff bị giới hạn cơ sở trực thuộc

**Ngày tạo**: 2026-11-14  
**Ngày cập nhật**: 2026-11-14  
**Người phụ trách**: Phan Sơn  
**Người cập nhật cuối**: Phan Sơn  

## 1. Mục tiêu và Bối cảnh (Goal & Context)

Kiểm chứng hành vi phân quyền tiêu thụ của Feature 003 (Workshop Settlement) khi tiếp nhận `CurrentAccountActor` từ Feature 001:
- Theo Revision R-MGA và Task T079: Thẩm quyền của tài khoản `MANAGER` đối với các hành vi được phân quyền cho Manager là toàn cục, được quyết định trực tiếp từ vai trò và trạng thái của `CurrentAccountActor`, không bị thu hẹp bởi danh sách cơ sở phân công tổ chức (`activeLocationIds`).
- Ngược lại, tài khoản `STAFF` bắt buộc phải kiểm tra cơ sở tài nguyên (`bookingLocationId`) đối chiếu với danh sách cơ sở trực thuộc; nếu không khớp, hệ thống phải từ chối ngay lập tức.

## 2. Tiền điều kiện dữ liệu (Preconditions & Fixtures)

- Manager A: `accountId = 42L`, `role = ActorRole.MANAGER`, `status = ActorStatus.ACTIVE`, phân công tổ chức tại Cơ sở 101L (`activeLocationIds = [101L]`).
- Manager B (Scope rỗng/không khả dụng): `accountId = 42L`, `role = ActorRole.MANAGER`, `status = ActorStatus.ACTIVE`, `activeLocationIds = []`.
- Staff C: `accountId = 43L`, `role = ActorRole.STAFF`, `status = ActorStatus.ACTIVE`, cơ sở trực thuộc là 9L (`activeLocationIds = [9L]`).

## 3. Các bước thực hiện kiểm thử (Execution Steps)

1. Khởi tạo `WorkshopSettlementAuthorizationService` với các mock dependencies.
2. Kiểm tra kịch bản 1: Manager A (gán tại Cơ sở 101L) gọi thẩm quyền quyết toán trên đơn/ca thuộc Cơ sở 202L:
   ```java
   CurrentAccountActor result = service.requireManagerOrStaff(managerLocationA, 202L);
   ```
3. Kiểm tra kịch bản 2: Manager B (danh sách cơ sở rỗng hoặc dependency không khả dụng) gọi thẩm quyền trên Cơ sở 202L:
   ```java
   CurrentAccountActor resultUnavailable = service.requireManagerOrStaff(managerUnavailableScope, 202L);
   ```
4. Kiểm tra kịch bản 3: Staff C (gán tại Cơ sở 9L) gọi thẩm quyền trên Cơ sở 700L (khác cơ sở gán):
   ```java
   service.requireManagerOrStaff(staff, 700L);
   ```

## 4. Kết quả mong đợi (Expected Results)

- Kịch bản 1: `result` trả về chính `managerLocationA` (thao tác thành công, không bị chặn).
- Kịch bản 2: `resultUnavailable` trả về chính `managerUnavailableScope` (Manager thành công mà không phụ thuộc vào Location scope dependency).
- Kịch bản 3: Ném ngoại lệ `WorkshopSettlementAuthorizationException` với thông điệp `"LOCATION_SCOPE_DENIED"`.
- Ghi nhận audit nghiệp vụ an toàn (không ghi nhận raw token, không lộ PII nhạy cảm).

## 5. Bằng chứng thực thi (Execution Evidence)

<!-- EXECUTION:START -->
- Latest status: PASS
- Executed at: 2026-11-14 05:27:00
- Executed by: Phan Sơn
- Test suite: Maven Surefire — `WorkshopSettlementAuthorizationServiceTest`
- Command: `mvnw.cmd test -Dtest=WorkshopSettlementAuthorizationServiceTest#managerAssignedToLocationACanActOnLocationBAndWithUnavailableScope,WorkshopSettlementAuthorizationServiceTest#canonicalManagerIsGlobalWhileStaffRemainsLocationScoped`
- Actual result: 2/2 test method chạy thành công 100%. Manager thực hiện hợp lệ xuyên chi nhánh và khi scope rỗng; Staff bị chặn chính xác với mã lỗi LOCATION_SCOPE_DENIED.
<!-- EXECUTION:END -->
