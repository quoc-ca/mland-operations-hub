---
id: UTC-001-002
feature: 001-account-management
test_level: unit
test_case_type: B
source_ref: spec.md#FR-011
source_class: edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountFacadeImpl
source_method: resolveActor
test_class: edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountFacadeImplTest
test_method: testManagerAuthorization_IndependentOfLocationScope
---

# UTC-001-002: Phân giải Thẩm quyền Manager — resolveActor thiết lập vai trò MANAGER độc lập với Location scope

**Date created**: 2026-11-14
**Date updated**: 2026-11-14
**Author**: Phan Sơn
**Last updated by**: Phan Sơn

## Test design

- Function code/name: AccountFacadeImpl#resolveActor — Phân giải định danh thành CurrentAccountActor tại IdentityGate
- Test requirement: spec.md#FR-011, D-23 — Manager có thẩm quyền toàn cục độc lập với danh sách cơ sở phân công tổ chức
- Case type: B

### Preconditions

- Mockito isolation:
  - AccountRepository accountRepo
  - AccountLocationAssignmentRepository assignRepo
- Stub state:
  - firebaseUid = "firebase-manager-uid-123"
  - accountRepo.findByFirebaseUid trả về Account với id = 55L, role = MANAGER, status = ACTIVE
  - assignRepo.findActiveWorkshopLocationIdsByAccountId(55L) trả về List.of() (rỗng)

### Test data and actions

- Input: `firebaseUid = "firebase-manager-uid-123"`, `emailVerified = true`
- Action: Gọi `facade.resolveActor("firebase-manager-uid-123", true)`

### Expected assertions

- `actorOpt.isPresent()` == true
- `actor.accountId()` == 55L
- `actor.role()` == `ActorRole.MANAGER`
- `actor.status()` == `ActorStatus.ACTIVE`
- `actor.isManager()` == true
- `actor.activeLocationIds().isEmpty()` == true, không làm mất vai trò hoặc ném ngoại lệ phân quyền

### Exception and log expectation

- None — Phân giải danh tính thành công mà không phụ thuộc vào Workshop Location

## Execution

<!-- EXECUTION:START -->
- Latest status: PASS
- Executed at: 2026-11-14T06:45:39Z
- Executed by: Phan Sơn
- Command: `mvn -q -Dtest=edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountFacadeImplTest#testManagerAuthorization_IndependentOfLocationScope test`
- Surefire evidence: `target/surefire-reports/TEST-edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountFacadeImplTest.xml` testcase `testManagerAuthorization_IndependentOfLocationScope`
- Actual result: PASS
- Runs: 1
- PASS: 1
- FAIL: 0
- ERROR: 0
- SKIPPED: 0
<!-- EXECUTION:END -->
