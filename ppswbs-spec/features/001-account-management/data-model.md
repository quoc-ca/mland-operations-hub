# Member Profile Data Model
## MemberProfile
Owned by members/domain. member_id BIGINT is PK/FK to members.id, no cascade delete or independent identity. full_name VARCHAR(200) reserves space for 100 Unicode code points (up to 200 UTF-16 units in H2); application validation still enforces exactly the approved 100-code-point maximum. phone VARCHAR(16) nullable; created_at and updated_at are UTC timestamps. No version column, unique phone or email field.
Profile is created on first valid save; absence is rendered as null fields. A pessimistic write lock on Member serializes creation/update; last committed valid write replaces name/phone. GET never provisions or changes profile.
## Identity
Firebase UID is the unique local linkage. Email/verification originate in backend-checked Firebase credentials; no UI/body email mutates identity. Apply the [spec navigation table](spec.md#authentication-and-navigation), not a fresh email-verification step on each sign-in.

Keep the existing MemberStatus enum and bootstrap convention: a new local Member may be PENDING_POLICY_ACCEPTANCE. For 003 only, ACTIVE and PENDING_POLICY_ACCEPTANCE permit session/profile access when email is verified; SUSPENDED and statuses outside that allowlist deny. Missing local Member is handled by the existing UID-based session/bootstrap flow, not a profile GET write or a new registration.

Policy records remain separately owned by MySQL and unchanged. Policy acceptance/version state does not gate 003 and does not cause account-status transitions here; no bulk conversion to ACTIVE, consent-history deletion or status migration is designed. This does not grant entitlement in other features. Existing MemberDto/API fields remain unchanged.
## Audit and migration
Minimal profile audit joins the same transaction as the profile update. Only IDs, action, changed-field names, time and correlation are retained, not raw field values. V4__member_profiles.sql is append-only; existing V1–V3 remain byte-for-byte unchanged. Migration preserves existing members, policies and booking data.
