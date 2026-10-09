### 1.10 Configure Role-Based Access Control

#### Primary Actors

Admin

#### Secondary Actors

None

#### Description

As an Admin, I want to configure role-based access-control policies so that each platform role can access only the functions required for its responsibilities.

#### Preconditions

1. The platform is available.
2. The Admin is authenticated and authorized to manage RBAC configuration.
3. The supported platform roles and permission definitions are available.
4. The requested policy changes can be validated without creating an authorization gap.

#### Normal Flow

**Configure Role-Based Access Control**

1. The Admin opens the Role-Based Access Control function.
2. The system displays the supported roles and their current permissions.
3. The Admin selects a role and reviews its permitted functions and restrictions.
4. The Admin adds, removes, or changes a permission within the supported policy model.
5. The system validates the policy for supported permissions, dependencies, and protected administrative access.
6. The Admin reviews and confirms the policy changes.
7. The system saves the RBAC policy, records the acting Admin and change time, and applies it to subsequent authorization checks.
8. The system displays the updated role policy.

#### Alternative Flows

**Step 3 — Role does not exist or is not configurable**

The system rejects the request and displays the supported role catalogue.

**Step 5 — Permission is unsupported or conflicts with a required dependency**

The system displays the conflict and does not save the invalid policy.

**Step 5 — Proposed change would remove all authorized administrative access**

The system rejects the change and preserves the current protected administrative policy.

**Step 6 — Admin cancels**

The system discards the unsaved policy changes.

**Step 7 — Policy save fails**

The system rolls back the incomplete update, displays an error, and preserves the previous RBAC policy.

#### Postconditions

- The confirmed RBAC policy is stored and used by subsequent authorization checks.
- Existing account credentials, account status, and identity mappings are unchanged.
- The system retains the previous policy, new policy, acting Admin, and change time as audit evidence.
- No policy change is applied when validation or save fails.

#### Business Rules

BR-10-01, BR-10-02, BR-10-03

#### Business Rule Definitions

| ID       | Rule Definition                                                                                                                        |
| -------- | -------------------------------------------------------------------------------------------------------------------------------------- |
| BR-10-01 | Only an authorized Admin may configure role-based access-control policies.                                                             |
| BR-10-02 | Every permission must be assigned through a supported role and permission definition.                                                  |
| BR-10-03 | The system must not save an RBAC change that removes all authorized administrative access or creates an invalid permission dependency. |
