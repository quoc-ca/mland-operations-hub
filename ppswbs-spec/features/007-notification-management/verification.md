# Verification: FE-007 internal-account invitation delivery

**Date created**: 2026-11-27
**Date updated**: 2026-11-27
**Author**: Phan Sơn
**Last updated by**: Phan Sơn

**Status:** Focused test passed; integrated delivery journey T045 remains open.

| Date | Check | Result | Limit |
| --- | --- | --- | --- |
| 2026-11-27 | `mvnw.cmd -q -Dtest=InternalAccountInvitationNotificationIT test` | PASS | The fixture uses `/invitations/setup`; this does not verify a real email link, Firebase setup, outside delivery, or manual content in a configured browser journey. |

T045 requires fresh configured Firebase/mail-adapter evidence and Account-ID continuity evidence under FE-001 T116/T124. Do not infer `SENT` or completed setup from the focused test.
