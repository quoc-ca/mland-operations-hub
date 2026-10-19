# Member Profile Data Model
## MemberProfile
Owned by members/domain. member_id BIGINT is PK/FK to members.id, no cascade delete or independent identity. full_name VARCHAR(200) reserves space for 100 Unicode code points (up to 200 UTF-16 units in H2); application validation still enforces exactly the approved 100-code-point maximum. phone VARCHAR(16) nullable; created_at and updated_at are UTC timestamps. No version column, unique phone or email field.
Profile is created on first valid save; absence is rendered as null fields. A pessimistic write lock on Member serializes creation/update; last committed valid write replaces name/phone. GET never provisions or changes profile.
## Identity
Firebase UID is the unique local linkage. Email/verification originate in checked Firebase credentials; no UI/body email mutates identity. Local member status, current policy versions and acceptances determine entitlement. New local identity is PENDING_POLICY_ACCEPTANCE until required policies are accepted. Existing MemberDto/API fields remain unchanged.
## Audit and migration
Minimal profile audit joins the same transaction as the profile update. Only IDs, action, changed-field names, time and correlation are retained, not raw field values. V4__member_profiles.sql is append-only; existing V1–V3 remain byte-for-byte unchanged. Migration preserves existing members, policies and booking data.
