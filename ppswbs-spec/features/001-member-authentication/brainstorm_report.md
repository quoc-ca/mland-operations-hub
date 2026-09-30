# Brainstorm Report: Member Registration and Sign-in

**Created**: 2026-09-29  
**Evidence basis**: Approved SRS UC-02, approved role boundary, the existing offline UX prototype, and `spec-general.md` working agreement.

## Problem-first analysis

### Solution-jumping diagnosis

“Add registration and login” is not itself the business goal. It is a signal that the system needs a trustworthy customer identity for Member-only ready-ring retail and permitted history, without forcing account creation on workshop Guests.

### Underlying problem

The business cannot safely decide whether a customer may view Member history or complete ready-ring checkout when “Member” is only a browser-local demo flag. At the same time, a compulsory account before workshop discovery or booking would add unnecessary friction.

### Assumptions challenged

| Assumption | Risk if wrong | Validation in this feature |
| --- | --- | --- |
| Email equality proves the same person | Historical booking/order data can be exposed to a different identity | Require email confirmation when the Guest booking is created, Member-email verification, and explicit Member import confirmation; never merge different external identities automatically. |
| Google alone serves all customers | Some customers cannot or do not want to use Google | Offer email/password as a supported alternative. |
| A valid provider account grants business access | Social identity could obtain internal roles or bypass suspension | Separate identity proof from Mland Member role/status authorization. |
| Guest booking must require membership | Workshop conversion falls due to unnecessary friction | Preserve public booking and lookup paths. |

### Alternative problem framings

1. **Entitlement only**: Gate ready-ring checkout without retaining a full Member identity. This is too weak because it cannot support durable history or audited account lifecycle.
2. **Mandatory account commerce**: Require every workshop Guest to create an account. This increases identity coverage but conflicts with the approved Guest booking boundary.
3. **Progressive Member identity (recommended)**: Keep public Guest journeys, introduce Member identity only at protected actions, and safely associate prior Guest data after verified proof of email control.

### Evidence status

**Medium.** The approved SRS already distinguishes Guest and Member privileges and requires an account/session/audit outcome. The product has no production authentication implementation yet, so conversion and recovery behaviour must be validated after an approved implementation is available.

## Agreed product direction

- Google is the prominent sign-in option; email/password is the supported fallback.
- A new email/password Member can use new Member privileges immediately.
- Email verification gates only automatic attachment of historical Guest bookings.
- Browser sign-in state persists until sign-out or invalidation.
- A same-email/different-identity conflict never auto-merges; the person authenticates the existing identity before linking another sign-in method.
- This feature covers Member only; employee/admin roles and MFA remain separate work.
- A Guest booking is temporary until its contact email is confirmed; confirmation is required before deposit payment, expires after 15 minutes, and cannot be resent.
- A verified Member explicitly confirms before importing every eligible Guest booking, including pending, confirmed, failed, and cancelled records.
- A policy version change ends active Member access until the Member signs in and accepts the current Terms and Privacy Policy.
- A suspended Member has no Member rights; Google-only account recovery remains with Google.

## Risks and validation priorities

- **Unauthorized history exposure**: Test direct protected-route access, unverified-email behaviour, and same-email/different-identity collisions.
- **Duplicate Member identity**: Test repeated and concurrent first sign-in.
- **Guest conversion friction**: Test the return-to-origin path from ready-ring checkout and history.
- **Recovery ambiguity**: Test neutral password-recovery feedback without account enumeration.
- **Accessibility and bilingual UX**: Test keyboard-only completion and Vietnamese/English feedback.
- **Temporary booking cleanup**: Test expiry, capacity/invoice release, payment blocking, and the no-resend rule.
- **Policy lifecycle**: Test forced loss of Member access and re-acceptance after a policy-version change.

## Implementation planning constraints

- Superseded: use MySQL 9.7.2 as the exact database compatibility baseline; see plan.md and research.md.
- Use Firebase Auth Emulator for automated tests and a separate Firebase development project for manual Google sign-in, redirect, and provider-linking checks. Production Firebase must not be used for testing.
- Validate the UI on the latest two releases of Chrome, Edge, Firefox, and Safari on desktop, plus Chrome on Android and Safari on iPhone. Accessibility remains a best-effort commitment rather than a formal conformance target.

## Draft stakeholder message

> We will add Member identity where it creates real value—protected retail and history—while leaving approved workshop Guest journeys public. The account flow will avoid auto-merging by email, and historical Guest data will be connected only after a customer proves control of the matching email.
