# Quickstart: Independent Feature-04 Verification Walkthrough

This is a verification inventory. It is not evidence that the feature is implemented.

| ID | Scenario | Expected result |
| --- | --- | --- |
| QS-01 | Guest asks a public Basic AI question | Safe response; no business mutation. |
| QS-02 | Guest submits an independent Design Request before Guest policy is approved | Typed policy response; no request or image is stored. |
| QS-03 | Approved Member submits a description-only request when text-only is enabled | Independent versioned request; no booking data. |
| QS-04 | Member submits an image without consent | `CONSENT_REQUIRED`; no storage/provider call. |
| QS-05 | Member submits an invalid image | Typed image error; no provider call; no permanent retention. |
| QS-06 | Fourth image attempt within five minutes | `429 IMAGE_RATE_LIMITED`, `Retry-After`; no storage/provider/request creation. |
| QS-07 | Valid image request is analyzed | Candidate result stored; image cleanup outcome recorded. |
| QS-08 | Provider output contains approval/price-publication instruction | `AI_OUTPUT_POLICY_VIOLATION`; no business mutation. |
| QS-09 | Request owner reads own result | Safe projection only; no raw payload or image. |
| QS-10 | Other owner reads or mutates request | Denied without resource enumeration. |
| QS-11 | Staff/Manager reviews according to approved policy | Decision and reason recorded atomically. |
| QS-12 | Material revision is submitted | New request version; previous decision is not reused. |
| QS-13 | Owner withdraws when policy allows | Request becomes withdrawn; no booking/payment mutation. |
| QS-14 | Manager requests price suggestion | Advisory result only; catalogue price remains unchanged until separate workflow. |
| QS-15 | Admin views technical evidence | No commercial price/explanation unless explicitly authorized. |
| QS-16 | Feature-04 action is traced for booking side effects | No booking, capacity, payment, ticket or custom-order call/state change. |

## Required commands after implementation

```text
./.specify/scripts/powershell/check-prerequisites.ps1 -Json -RequireSpec -RequireTasks -IncludeTasks
./mvnw test
./mvnw verify
```

Also run MySQL clean-install/upgrade migration tests, OpenAPI validation, Modulith
verification, authorization tests, provider-contract tests and frontend E2E tests.
Record actual command output; do not mark runtime evidence before execution.
