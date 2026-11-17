---
id: UTC-001-006
feature: 001-account-management
test_level: unit
test_case_type: A
source_ref: spec.md#FR-043
source_class: edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.service.MemberAddressService
source_method: updateAddress
test_class: edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.service.MemberAddressServiceTest
test_method: testOptimisticLocking_VersionConflict
---

# UTC-001-006: Khóa lạc quan Địa chỉ — updateAddress từ chối lệnh mang version cũ với lỗi xung đột 409

**Date created**: 2026-11-14
**Date updated**: 2026-11-14
**Author**: Phan Sơn
**Last updated by**: Phan Sơn

## Test design

- Function code/name: MemberAddressService#updateAddress — Cập nhật địa chỉ nhận hàng với kiểm soát khóa lạc quan
- Test requirement: spec.md#FR-043 — Lệnh cập nhật/xóa/default mang stale version bị từ chối với OptimisticLockConflictException (HTTP 409)
- Case type: A

### Preconditions

- Mockito isolation:
  - MemberAddressRepository addressRepo
- Stub state:
  - addressId = 12L, accountId = 99L
  - existing entity trong DB có version = 3L
  - client request gửi version = 2L (stale version)

### Test data and actions

- Input: `AddressUpdateRequest` mang `version = 2L`
- Action: Gọi `service.updateAddress(99L, 12L, staleRequest)`

### Expected assertions

- assertThrows(OptimisticLockConflictException.class)
- verify(addressRepo, never()).save(any())
- Bản ghi existing trong bộ nhớ không bị thay đổi bất kỳ thuộc tính nào

### Exception and log expectation

- Expected OptimisticLockConflictException (HTTP 409 Conflict) do xung đột phiên bản đồng thời

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
