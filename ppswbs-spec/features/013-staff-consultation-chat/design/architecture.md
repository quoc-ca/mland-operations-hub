# Feature-013 Architecture Design

**Date created**: 2026-11-18  
**Date updated**: 2026-11-24
**Author**: Ngô Tiến Đạt  
**Last updated by**: Ngô Tiến Đạt

## Logical backend boundary

```text
consultationchat
├── web
│   ├── ConsultationController
│   └── ConsultationExceptionHandler
├── application
│   ├── ConsultationApplicationService
│   ├── ContextOptionService
│   ├── ConsultationAuthorizationService
│   ├── ConsultationRateLimitService
│   └── ConsultationAuditService
├── domain
│   ├── ConsultationConversation
│   ├── ConsultationMessage
│   ├── ConsultationReadState
│   ├── ConsultationClaimEvent
│   └── ConsultationContextPort
├── infrastructure
│   ├── repositories
│   ├── idempotency
│   └── retention
└── facade
    └── ConsultationFacade
```

The module consumes `AccountActorPort`, `WorkshopDesignContextPort` and
`IndependentDesignRequestContextPort`. It does not import another feature's entity,
repository, controller or private DTO. Public DTOs are projections, not JPA entities.

## Frontend boundary

```text
src/features/consultationchat/
├── api/consultationChatApi.js
├── application/useConsultationResources.js
├── application/useConsultationCommands.js
├── domain/chatRules.js
├── routes.jsx
├── ui/ContextSelectorPage.jsx
├── ui/ConversationListPage.jsx
├── ui/ConversationDetailPage.jsx
├── ui/OperationsInboxPage.jsx
└── tests/
```

Frontend uses shared Firebase auth, HTTP, i18n and UI modules. Backend remains the
authority for ownership, branch scope, context integrity and state.

## Data flow

```text
Context selector → GET context-options → owning facade → safe option projection
Create/send → command hook → Idempotency-Key → controller → service → repository/audit
Inbox/detail → GET → scoped projection → local state → visible-tab polling every 10s
Staff/Manager action → If-Match-Version → authorization → atomic state/audit commit
```

