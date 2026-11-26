## 1. Account Management (FE-01)

**Implementation status:** Existing backend/frontend slices; broader account scope remains governed by the approved Feature-001 contract.

### 1.1 Class Diagram

The design separates Firebase identity proof from Mland account status, role, profile and location assignment. Controllers use account application services; other modules consume the account facade rather than account repositories.

{{r4-cd-account-management width=100% align=center}}

### 1.2 Sequence Diagram — Register and Provision Member

{{r4-sd-register-and-provision-member width=100% align=center}}

### 1.3 Sequence Diagram — Sign In and Resolve Mland Role

{{r4-sd-sign-in-and-resolve-mland-role width=100% align=center}}

### 1.4 Sequence Diagram — Admin Creates or Locks Internal Account

{{r4-sd-admin-creates-or-locks-internal-account width=100% align=center}}

