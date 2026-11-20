# Feature-004 Research — Human Review Rebaseline

Date created: 2026-11-20  
Date updated: 2026-11-20  
Author: Ngô Tiến Đạt  
Last updated by: Ngô Tiến Đạt  

## Facts confirmed

- The current directory and older artifacts are named for AI chatbox/analysis.
- Existing routes include Design Request creation, detail and Staff/Manager review.
- Existing Feature-004 design requests are independent from booking.
- Existing UI/API and E2E evidence still describe AI analysis and AI price suggestion.
- `ppswbs-testing/AGENT.md` requires explicit actor, isolation, cleanup, observable
  assertions, Browser MCP evidence and truthful PASS/FAIL status.

## New decisions

- Logical name: `004-design-request-and-human-review`.
- No AI capability belongs to Feature-004.
- Guest and Member may submit requests.
- A request may contain description only, images only or both.
- Maximum five images per request.
- Image quality is checked immediately in the UI and authoritatively by backend;
  configured thresholds are not hard-coded in frontend.
- Too-blurry images block submission.
- Guest may create five requests per calendar day; the sixth returns 429.
- Guest uses opaque session/tracking token/link.
- Staff and Manager have equal accept/reject authority.
- Every decision requires a mandatory explanation.
- V1 review outcomes are only `ACCEPTED` and `REJECTED`.

## Retired context

The following are obsolete for this feature: Gemini, Basic AI Support, candidate
features, confidence, AI price suggestions, automated analysis status and the old
3,000,000 VND reviewer threshold. Existing tests proving those behaviors remain
historical evidence and must not gate the new implementation.

## Dependencies and risks

- Guest token expiry and safe tracking-link delivery need an approved security baseline.
- Image quality threshold values must be supplied by backend configuration before deployment.
- Existing physical schema/migrations must be inventoried before a new migration.
- Figma revision must be approved before frontend coding.
- Feature-013 may consume only a safe Design Request projection and cannot make decisions.
