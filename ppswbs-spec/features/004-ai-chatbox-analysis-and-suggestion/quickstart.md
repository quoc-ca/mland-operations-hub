# Quickstart: Feature-04 Verification Walkthrough

This is a documentation and later runtime-test inventory. It does not claim that Feature-04 is implemented.

| ID | Scenario | Expected result |
| --- | --- | --- |
| QS-01 | Guest opens basic AI support and asks a public FAQ | Validated response; no protected context or business mutation. |
| QS-02 | Guest attempts booking-design image upload | `FORBIDDEN_ROLE`; no storage/provider call. |
| QS-03 | Member submits image without current consent | `CONSENT_REQUIRED`; no storage/provider call. |
| QS-04 | Member submits invalid file | Validation error; no provider call; input is not retained. |
| QS-05 | Member submits valid image after package/location/session selection | One versioned request, temporary storage, provider call and pending human review. |
| QS-06 | Gemini returns valid features | Result schema stored, image deleted, Staff/Manager route created. |
| QS-07 | Gemini times out | Failed analysis, image cleanup retry, no decision, safe retry available. |
| QS-08 | Estimated value is ≤ 3,000,000 VND | Staff review scope only, subject to branch/delegation authorization. |
| QS-09 | Estimated value is > 3,000,000 VND | Manager review scope only. |
| QS-10 | Reviewer records Clarification Required | Member sees reason; new design version is required before another decision. |
| QS-11 | Reviewer records Accepted | Member sees result; booking/payment remains owned by Workshop Booking. |
| QS-12 | Reviewer attempts to decide stale version | Conflict; existing decision remains unchanged. |
| QS-13 | Manager requests valid price suggestion | Advisory result only; applied price remains unchanged until Manager confirms through catalogue workflow. |
| QS-14 | Gemini price suggestion fails | Manager can retry or enter manual price; no invalid price is published. |
| QS-15 | AI receives request to accept a custom order | Safe refusal; no custom-order mutation. |
| QS-16 | Text interaction reaches retention deadline | Prompt/response is removed according to retention job; no raw token/PII retained. |

## Required commands after implementation

Commands depend on the approved feature plan and build environment. At minimum run:

```text
./.specify/scripts/powershell/check-prerequisites.ps1 -Json -RequireSpec -RequireTasks -IncludeTasks
./mvnw test
./mvnw verify
```

Also run MySQL migration/integration, OpenAPI validation and Spring Modulith verification when the feature implementation exists. Record actual commands and results; do not mark runtime evidence before execution.
