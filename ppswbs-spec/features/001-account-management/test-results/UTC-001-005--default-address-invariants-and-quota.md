---
id: UTC-001-005
feature: 001-account-management
test_level: unit
test_case_type: B
source_ref: spec.md#FR-039
source_class: edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.service.MemberAddressService
source_method: createAddress
test_class: edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.service.MemberAddressServiceTest
test_method: testAddressInvariants_DefaultAndQuota
---

# UTC-001-005: Bất biến Địa chỉ mặc định & Kiểm soát Quota — Tự động mặc định, hoán đổi nguyên tử và chặn xóa

**Date created**: 2026-11-14
**Date updated**: 2026-11-14
**Author**: Phan Sơn
**Last updated by**: Phan Sơn

## Test design

- Function code/name: MemberAddressService#createAddress và deleteAddress — Quản lý vòng đời và bất biến địa chỉ nhận hàng
- Test requirement: spec.md#FR-036, spec.md#FR-039 — Địa chỉ đầu tiên tự động thành default; chặn xóa default khi còn địa chỉ khác; quota tối đa 10 địa chỉ
- Case type: B

### Preconditions

- Mockito isolation:
  - MemberAddressRepository addressRepo
- Stub state:
  - accountId = 99L

### Test data and actions

- Action 1: Tạo địa chỉ đầu tiên khi countByAccountId(99L) == 0L
- Action 2: Tạo địa chỉ thứ 11 khi countByAccountId(99L) == 10L
- Action 3: Xóa địa chỉ default khi countByAccountId(99L) == 2L
- Action 4: Xóa địa chỉ default khi countByAccountId(99L) == 1L (duy nhất)

### Expected assertions

- Action 1: response.isDefault() == true
- Action 2: assertThrows(AddressLimitExceededException.class)
- Action 3: assertThrows(DefaultAddressDeletionException.class)
- Action 4: assertDoesNotThrow(() -> service.deleteAddress(99L, 2L)), verify(addressRepo).delete(any())

### Exception and log expectation

- Action 2: Expected AddressLimitExceededException (HTTP 400 Bad Request) do vượt quota 10
- Action 3: Expected DefaultAddressDeletionException (HTTP 400 Bad Request) do chặn xóa default còn địa chỉ khác

## Execution

<!-- EXECUTION:START -->
- Latest status: PASS
- Executed at: 2026-11-14T06:45:39Z
- Executed by: Phan Sơn
- Command: `mvn -q -Dtest=edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.service.MemberAddressServiceTest#testAddressInvariants_DefaultAndQuota test`
- Surefire evidence: `target/surefire-reports/TEST-edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.service.MemberAddressServiceTest.xml` testcase `testAddressInvariants_DefaultAndQuota`
- Actual result: PASS
- Runs: 1
- PASS: 1
- FAIL: 0
- ERROR: 0
- SKIPPED: 0
<!-- EXECUTION:END -->
