# Feature-013 UI Flow

**Date created**: 2026-11-18  
**Date updated**: 2026-11-18  
**Author**: Ngô Tiến Đạt  
**Last updated by**: Ngô Tiến Đạt

## Member

```text
Member design/request page
→ Consultation entry
→ Context selector
→ Context preview confirmation
→ Conversation detail
→ Send message / polling / close
→ Conversation list with unread badge
```

## Staff/Manager

```text
Operations navigation
→ Consultation inbox
→ Scoped conversation detail
→ Claim or Manager assign
→ Send reply / polling / close
→ Release or reopen
```

Each screen specifies loading, empty, validation, forbidden, not-found, stale,
rate-limited, closed, polling failure and retry states. Messages are rendered as
plain text. Vietnamese and English labels use the shared i18n mechanism; no raw
HTML is accepted from the API.

Accessibility requirements include labelled context controls, keyboard-send-safe
composer, visible focus, status announcements for new messages and a non-colour-only
indicator for unread/claimed/closed states.

The sidebar uses a stable desktop width and full-width loading skeleton, empty
state, error state and Load more control. Hidden Member conversations are not
rendered. The create modal includes a General consultation option with no design
preview. Member delete is available only for CLOSED conversations and requires a
confirmation dialog explaining that the store retains the transcript. Staff and
Manager conversation detail includes a close action within their authorization
scope.

