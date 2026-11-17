---
id: UTC-001-001
feature: 001-account-management
test_level: unit
test_case_type: N
source_ref: spec.md#FR-011
source_class: edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountFacadeImpl
source_method: findEligibleRoster
test_class: edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountFacadeImplTest
test_method: testFindEligibleRoster_ExcludesManagers
---

# UTC-001-001: Lọc Roster nhân sự hợp lệ — Chỉ trả về Staff, loại trừ hoàn toàn Manager

**Date created**: 2026-11-14
**Date updated**: 2026-11-14
**Author**: Phan Sơn
**Last updated by**: Phan Sơn

## Test design

- Function code/name: AccountFacadeImpl#findEligibleRoster — Cung cấp danh sách nhân sự đủ điều kiện cho Workshop Operations
- Test requirement: spec.md#FR-011, D-23 — findEligibleRoster chỉ trả về ứng viên STAFF hoạt động; loại trừ nghiêm ngặt MANAGER
- Case type: N

### Preconditions

- Mockito isolation:
  - AccountRepository accountRepo
  - AccountLocationAssignmentRepository assignRepo
  - AccountProfileRepository profileRepo
- Stub state:
  - workshopLocationId = 101L
  - assignRepo.findActiveAccountIdsByWorkshopLocationId(101L) trả về [1L (Staff), 2L (Manager)]
  - Cả 2 tài khoản ở trạng thái ACTIVE
  - Staff (1L) có Profile full_name = "Le Van Staff"
  - Manager (2L) có Profile full_name = "Pham Thi Manager"

### Test data and actions

- Input: `workshopLocationId = 101L`
- Action: Gọi `facade.findEligibleRoster(101L)`

### Expected assertions

- `outcome.status()` == `RosterStatus.AVAILABLE`
- `outcome.entries().size()` == 1
- Entry duy nhất:
  - `accountId` == 1L
  - `role` == `ActorRole.STAFF`
  - `fullName` == "Le Van Staff"
- Không có phần tử nào chứa `accountId == 2L` hoặc `role == ActorRole.MANAGER`
- verify(profileRepo, never()).findByAccountId(2L) vì Manager bị lọc bỏ trước khi tra cứu Profile

### Exception and log expectation

- None — Phương thức trả về RosterOutcome.available thành công, không ném ngoại lệ và không ghi log audit

## Execution

<!-- EXECUTION:START -->
- Latest status: PASS
- Executed at: 2026-11-14T06:45:39Z
- Executed by: Phan Sơn
- Command: `mvn -q -Dtest=edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountFacadeImplTest#testFindEligibleRoster_ExcludesManagers test`
- Surefire evidence: `target/surefire-reports/TEST-edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountFacadeImplTest.xml` testcase `testFindEligibleRoster_ExcludesManagers`
- Actual result: PASS
- Runs: 1
- PASS: 1
- FAIL: 0
- ERROR: 0
- SKIPPED: 0
<!-- EXECUTION:END -->
