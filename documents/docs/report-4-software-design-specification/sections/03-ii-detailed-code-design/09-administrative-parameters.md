## 9. Administrative Parameter Configuration (FE-09)

**Implementation status:** Parameter and account administration responsibilities are split between Manager business authority and Admin technical/account authority.

### 9.1 Class Diagram

{{r4-cd-fe09-administrative-parameters width=100% align=center}}

Manager-owned business and financial parameters are separated from Admin-owned accounts, roles, integration credentials and technical audit projections. Admin cannot approve prices, quotes, reviews or payments.

### 9.2 Sequence Diagram — Manager Updates Business Parameter

{{r4-sd-fe09-manager-parameter width=100% align=center}}

### 9.3 Sequence Diagram — Admin Updates Account or Credential Configuration

{{r4-sd-fe09-admin-configuration width=100% align=center}}

### 9.4 Sequence Diagram — Deny Cross-Role Business Mutation

{{r4-sd-fe09-authorization-denial width=100% align=center}}

