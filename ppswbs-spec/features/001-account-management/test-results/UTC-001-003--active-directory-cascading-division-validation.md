---
id: UTC-001-003
feature: 001-account-management
test_level: unit
test_case_type: A
source_ref: spec.md#FR-038
source_class: edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.service.MemberAddressService
source_method: validateAndResolveDivisions
test_class: edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.service.MemberAddressServiceTest
test_method: testDivisionResolution_RejectsMismatchedCommuneAndProvince
---

# UTC-001-003: Xác thực Phân cấp Địa giới — validateAndResolveDivisions từ chối mã Xã không trực thuộc Tỉnh hợp lệ

**Date created**: 2026-11-14
**Date updated**: 2026-11-14
**Author**: Phan Sơn
**Last updated by**: Phan Sơn

## Test design

- Function code/name: MemberAddressService#validateAndResolveDivisions — Đối chiếu mã Tỉnh và Xã với danh mục hành chính hiệu lực
- Test requirement: spec.md#FR-038 — Server phân giải tên từ danh mục hiệu lực và từ chối mã xã không thuộc về tỉnh đã chọn
- Case type: A

### Preconditions

- Mockito isolation:
  - AdministrativeDirectoryRepository directoryRepo
- Stub state:
  - Danh mục đang ACTIVE: Phiên bản VN-2026-V1
  - Tỉnh Hà Nội (provinceCode = "01"): chứa Xã Hàng Trống (communeCode = "00001")
  - Tỉnh TP.HCM (provinceCode = "79"): chứa Xã Bến Nghé (communeCode = "26734")

### Test data and actions

- Input: `provinceCode = "01"` (Hà Nội), `communeCode = "26734"` (Bến Nghé, thuộc TP.HCM)
- Action: Gọi `service.validateAndResolveDivisions("01", "26734")`

### Expected assertions

- assertThrows(InvalidAdministrativeDivisionException.class)
- Exception message/code: COMMUNE_NOT_IN_PROVINCE
- Không có bất kỳ entity MemberAddress nào được tạo hoặc cập nhật

### Exception and log expectation

- Expected InvalidAdministrativeDivisionException (HTTP 400 Bad Request) do sai quan hệ cha-con

## Execution

<!-- EXECUTION:START -->
- Latest status: NOT_RUN
- Executed at: NOT_RUN
- Executed by: NOT_RUN
- Command: NOT_RUN
- Surefire evidence: NOT_RUN
- Actual result: NOT_RUN
- Runs: 0
- PASS: 0
- FAIL: 0
- ERROR: 0
- SKIPPED: 0
<!-- EXECUTION:END -->
