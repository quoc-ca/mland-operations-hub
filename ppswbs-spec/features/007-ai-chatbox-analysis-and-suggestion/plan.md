# Implementation Plan: AI Chatbox Analysis and Suggestion

## Summary

Implement Feature-04 as a bounded capability around text support, Member booking-design analysis, and Manager advisory price suggestions. Keep booking, catalogue, review, payment and custom-manufacturing business state in their owning modules.

## Technical context

- Java 21, Spring Boot, Spring Modulith, Spring Data JPA, MySQL and Flyway.
- Thymeleaf server-rendered pages with htmx progressive enhancement.
- Firebase Admin SDK verifies protected identity; Mland authorization resolves role/status.
- Gemini is accessed through ports/adapters; provider calls are outside business locks.
- No raw SQL in application code and no direct cross-module repository/entity access.

## Module and boundary design

Create or extend the approved `designreview`/AI feature boundary with:

- `facade/`: `AiChatboxFacade`, `DesignAnalysisFacade`, `PriceSuggestionFacade`.
- `application/`: use-case orchestration, authorization, version/idempotency and transaction boundaries.
- `domain/`: request/version/status/value objects and invariants.
- `infrastructure/`: JPA repositories, Gemini adapter, storage adapter and provider mappers.
- `web/`: REST DTO/controller and Thymeleaf/htmx views with no-JS fallback.

The final module name and whether basic AI support is a subpackage of `designreview` must be confirmed against the existing Modulith package inventory before code. No other module may access its repositories or entities.

## Delivery slices

1. Ratify contract, role boundary, consent, retention and state model.
2. Add logical persistence mapping and versioned migration only after the mapping is approved.
3. Implement basic AI support with scope/rate/response validation and retention cleanup.
4. Implement Member booking-design submission, temporary storage, Gemini analysis, deletion and idempotent retry.
5. Implement Staff/Manager review list/detail/decision with threshold and branch/delegation authorization.
6. Implement Manager price suggestion and manual fallback; keep catalogue application/publication separate.
7. Add OpenAPI, server-rendered screens, jobs/cleanup registration and complete verification.

## External adapter contract

- `AiTextSupportPort`: sanitized public context in, validated text response or typed provider failure out.
- `ImageFeatureAnalysisPort`: consented temporary image reference/context in, versioned candidate feature result out.
- `AiPriceSuggestionPort`: complete package/product pricing input snapshot in, advisory suggestion out.
- `ReferenceImageStoragePort`: temporary put/get/delete with retry-safe deletion result.

Adapters must use correlation IDs, bounded timeouts, safe error mapping, no raw payload logging and provider-neutral domain results.

## Acceptance and rollout gates

- Spec, data model, OpenAPI, tasks and checklists are reviewed before code.
- Physical role-code mapping, provider credentials/model, file limits, rate limits and migration mapping are explicit gates.
- No production image processing or provider credentials are enabled by this feature artifact alone.
