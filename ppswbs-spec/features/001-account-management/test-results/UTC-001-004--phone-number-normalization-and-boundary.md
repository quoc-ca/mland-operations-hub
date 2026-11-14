---
id: UTC-001-004
feature: 001-account-management
test_level: unit
test_case_type: B
source_ref: spec.md#FR-040
source_class: edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.service.MemberAddressService
source_method: normalizePhoneNumber
test_class: edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.service.MemberAddressServiceTest
test_method: testPhoneNumberNormalization_BoundaryPatterns
---

# UTC-001-004: Chuẩn hóa Số điện thoại người nhận — normalizePhoneNumber xử lý định dạng quốc tế và ranh giới di động VN

**Date created**: 2026-11-14
**Date updated**: 2026-11-14
**Author**: Phan Sơn
**Last updated by**: Phan Sơn

## Test design

- Function code/name: MemberAddressService#normalizePhoneNumber — Chuẩn hóa số điện thoại người nhận sang 10 chữ số di động Việt Nam
- Test requirement: spec.md#FR-040 — Chuyển +84/84 thành 0, lọc ký tự phân cách, kiểm tra regex ^0[35789]\d{8}$, từ chối số bàn và ký tự lạ
- Case type: B

### Preconditions

- Không phụ thuộc mock repository (Pure logic function)

### Test data and actions

- Inputs:
  - Valid: "+84 912 345 678", "84912345678", "0912-345-678", "0912.345.678", "  0912345678  "
  - Invalid: "0241234567" (số bàn), "091234567" (9 số), "09123456789" (11 số), "0412345678", "091234567a", null
- Actions: Gọi `service.normalizePhoneNumber(input)` cho từng input

### Expected assertions

- Với toàn bộ các input hợp lệ: Kết quả trả về chính xác là "0912345678"
- Với toàn bộ các input không hợp lệ: assertThrows(InvalidPhoneNumberException.class)

### Exception and log expectation

- Expected InvalidPhoneNumberException (HTTP 400 Bad Request) khi số điện thoại không khớp chuẩn di động Việt Nam

## Execution

<!-- EXECUTION:START -->
- Latest status: PASS
- Executed at: 2026-11-14T06:45:39Z
- Executed by: Phan Sơn
- Command: `mvn -q -Dtest=edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.service.MemberAddressServiceTest#testPhoneNumberNormalization_BoundaryPatterns test`
- Surefire evidence: `target/surefire-reports/TEST-edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.service.MemberAddressServiceTest.xml` testcase `testPhoneNumberNormalization_BoundaryPatterns`
- Actual result: PASS
- Runs: 1
- PASS: 1
- FAIL: 0
- ERROR: 0
- SKIPPED: 0
<!-- EXECUTION:END -->
