# Customer Auth — Figma-ready handoff

This handoff implements the approved Guest → Member journey in the static prototype. It is UI-only: it does not call Firebase, email, or an identity provider.

## Foundations

- Font: Roboto.
- Primary `#1A73E8`; hover `#1558B0`; surfaces `#FFFFFF` / `#F8F9FA`; text `#202124` / `#5F6368`; border `#DADCE0`.
- Focus: 2px primary-blue outline. Semantic state colors always include explanatory text.
- Focused forms cap at 640px; public shell is sticky and mobile-first.

## Create page: `Customer Auth`

Create each frame at desktop 1440px and mobile 390px. Also inspect desktop at 1366 × 768. Mobile uses a one-column stack.

| Frame | Required content |
| --- | --- |
| `01 Member access / ready ring` | “Đăng nhập để mua ready rings”, three approved benefits, primary auth CTA, safe public return. |
| `02 Member access / history` | Same component composition with the history context. |
| `03 Auth landing` | Context banner, Google as primary blue action, divider, email sign-in and registration; no tabs or phone. |
| `04 Email sign-in` | Persistent labels, generic non-enumerating error, reset, registration, Google fallback. |
| `05 Email registration` | Email, password, confirm-password; no phone; explain policy consent happens after identity verification. |
| `06 Policy acceptance` | Current Terms/Privacy version links, two unchecked acceptance controls, agree action and public decline route. |
| `07 Password recovery` | Request and generic confirmation; never state whether an account exists. |
| `08 Unverified Member banner` | Rights remain usable; historic Guest-booking import waits for verified email; resend/check actions. |
| `09 Recovery and security` | Provider cancellation/failure, session expiry, suspension and stale policy variants, each with one clear recovery route. |
| `10 JavaScript unavailable` | Public-support fallback only; no protected content. |

## Components

- Buttons: primary, secondary, quiet, text, pending.
- Input states: default, focus, invalid, disabled.
- Info/error alert, context banner, benefit item, consent checkbox, verification banner.

## Interaction notes

1. Public workshop booking and booking lookup never show an auth gate.
2. Protected ready-ring checkout/history goes: benefit page → auth landing → method → entitlement/policy check → original `returnTo` action. Use a toast, not a success page.
3. Consent appears only after verified identity and when missing/stale. Decline returns to a public destination.
4. Email/password Members receive Member rights immediately after consent; email verification affects only historic Guest-booking import.
5. Keep values after recoverable errors; do not expose whether an email, account, or provider method exists.

The collapsed development-state panel in the prototype is a review aid for Figma and QA. Do not ship it in production UI.
