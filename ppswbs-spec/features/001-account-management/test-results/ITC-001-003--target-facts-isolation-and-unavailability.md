---
id: ITC-001-003
feature: 001-account-management
test_level: integration
test_case_type: A
source_ref: contracts/interfaces.md,quickstart.md#Scenario V-07,tasks.md#T030
provider_class: edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountFacadeImpl
provider_method: findTargetFacts
consumer_module: edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations
test_class: edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.AccountFacadeTargetAccountIT
test_method: facadeExposesZeroEntityOrCredentialLeakage
---

# ITC-001-003: Tích hợp Module Boundary — findTargetFacts cô lập dữ liệu nhạy cảm & Xử lý trạng thái UNAVAILABLE

**Ngày tạo**: 2026-11-14  
**Ngày cập nhật**: 2026-11-14  
**Người phụ trách**: Phan Sơn  
**Người cập nhật cuối**: Phan Sơn  

## 1. Mục tiêu và Bối cảnh (Goal & Context)

Kiểm chứng tính toàn vẹn và mức độ cô lập bảo mật giữa Feature 001 (Account Management) và Feature 003 (Workshop Operations):
- Phương thức `AccountFacade.findTargetFacts(AccountId)` là ranh giới đọc dữ liệu mục tiêu (target facts) cho Staff hoặc người dùng được kiểm tra.
- Hợp đồng ranh giới Modulith yêu cầu:
  1. `FOUND`: Chỉ chứa Account ID, Role cố định, Trạng thái tài khoản (Status) và danh sách cơ sở có hiệu lực `activeWorkshopLocationIds`.
  2. `NOT_FOUND`: Trả về khi tài khoản không tồn tại.
  3. `UNAVAILABLE`: Trả về khi cơ sở dữ liệu hoặc phụ thuộc Workshop Location không thể xác minh. Consumer Feature 003 bắt buộc phải ánh xạ sang mã lỗi `503 Service Unavailable`, giữ nguyên input an toàn và không commit dữ liệu lỗi.
  4. **Không rò rỉ (Zero Leakage):** Tuyệt đối không để lộ mật khẩu, mã hash, refresh token, Firebase UID, JPA Hibernate proxy hoặc entity gốc qua ranh giới facade.

## 2. Tiền điều kiện dữ liệu (Preconditions & Fixtures)

- Đã nạp tài khoản Staff mục tiêu trong DB với đầy đủ liên kết thực thể JPA (`Account`, `AccountProfile`, `AccountLocationAssignment`).
- Thiết lập kịch bản truy vấn đối với tài khoản tồn tại, tài khoản không tồn tại, và khi phụ thuộc xác thực cơ sở bị gián đoạn.

## 3. Các bước thực hiện kiểm thử (Execution Steps)

1. Gọi `accountFacade.findTargetFacts(AccountId.of(targetId))` đối với tài khoản tồn tại:
   ```java
   TargetFactsOutcome outcome = accountFacade.findTargetFacts(AccountId.of(targetId));
   ```
2. Gọi với ID tài khoản không tồn tại:
   ```java
   TargetFactsOutcome missingOutcome = accountFacade.findTargetFacts(AccountId.of(999999L));
   ```
3. Sử dụng cơ chế Java Reflection để quét toàn bộ các trường (fields) và kiểu trả về của các DTO/Record facade xuất khẩu sang Feat 003.

## 4. Kết quả mong đợi (Expected Results)

- Đối với tài khoản hợp lệ: `outcome.status()` bằng `TargetFactsStatus.FOUND`.
- Đối với tài khoản không tồn tại: `missingOutcome.status()` bằng `TargetFactsStatus.NOT_FOUND`.
- Khi scope không khả dụng: Trả về trạng thái `UNAVAILABLE`.
- Kiểm tra reflection:
  - 100% các class và record trả về từ Facade KHÔNG tham chiếu đến bất kỳ JPA Entity nào trong `edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity`.
  - Không có bất kỳ trường nào chứa từ khóa `password`, `hash`, `token`, `firebaseUid`, hoặc `credential`.

## 5. Bằng chứng thực thi (Execution Evidence)

<!-- EXECUTION:START -->
- Latest status: PASS
- Executed at: 2026-11-14 05:27:00
- Executed by: Phan Sơn
- Test suite: Maven Surefire — `AccountFacadeTargetAccountIT`
- Command: `mvnw.cmd test -Dtest=AccountFacadeTargetAccountIT#facadeExposesZeroEntityOrCredentialLeakage,AccountFacadeTargetAccountIT#targetFactsReturnsFoundForTargetAndExcludesEntityLeakage`
- Actual result: Test method kiểm tra reflection xác nhận tính cô lập tuyệt đối của Module Boundary: không có entity JPA, không có credential, bảo vệ toàn diện dữ liệu nhạy cảm theo Hiến chương Constitution §IV.
<!-- EXECUTION:END -->
