# Feature-011 Architecture Design

Date created: 2026-11-03  
Date updated: 2026-11-03  
Author: Ngô Tiến Đạt  
Last updated by: Ngô Tiến Đạt

## Boundary

`reviewmanagement` is the feature owner. The following are proposed logical units and must be mapped to the project’s actual Spring/React conventions before code:

```text
Backend (proposed)
  reviewmanagement/
    ReviewController
    ReviewEligibilityController
    ReviewModerationController
    ReviewApplicationService
    ReviewEligibilityService
    ReviewModerationService
    ReviewPublicReadFacade
    ReviewEligibilityPort
    WorkshopAttendanceReviewPort
    RetailOrderReviewPort
    ReviewRepository
    ReviewRevisionRepository
    ReviewReplyRepository
    ReviewAuditRepository
    ReviewDto / PublicReviewDto / EligibilityDto

Frontend (proposed)
  features/reviewmanagement/
    api/reviewClient
    pages/EligibleReviewSources
    pages/CreateReview
    pages/EditReview
    pages/PublicReviewList
    pages/StaffReviewModeration
    pages/ManagerReviewModeration
    components/ReviewCard / RatingInput / ReplyEditor
```

The controller calls application services; services call feature-owned repositories and source ports; source ports call Workshop/Retail facades. No Review unit imports another feature’s repository. Public DTOs are projections, not persistence entities.

## Data flow

```text
Guest contact or Member session
  → eligibility endpoint
  → source facade verifies attendance/fulfilment and ownership
  → eligible source projection
  → create command with Idempotency-Key
  → input/ownership/source/unique validation
  → review + revision + audit transaction
  → PUBLIC projection/list response
```

Moderation and reply commands follow the same controller → authorization → service → optimistic version → transaction → audit path. The aggregate reader filters `PUBLIC` before returning data.

## Existing/proposed status

## Backend implementation boundary

```text
reviewmanagement.controller
  → ReviewApplicationService / ReviewModerationService / ReviewReplyService
  → domain validation and authorization
  → ReviewRepository / RevisionRepository / ReplyRepository / AuditRepository
  → WorkshopAttendanceReviewPort or RetailFulfilmentReviewPort
  → owning Workshop Operations / Fulfilment facade
```

`GuestEligibilityHandlePort` sits between Guest lookup and create. It returns
only an opaque proof and resolves it again inside the create transaction. The
public-read facade performs the realtime `PUBLIC` aggregate query and strips
contact/source transaction data before the controller response.

All units in this file are `proposed`; no code or approved physical package mapping exists yet. React/Vite and Spring conventions are inherited only after confirmation from the corresponding implementation baseline.
