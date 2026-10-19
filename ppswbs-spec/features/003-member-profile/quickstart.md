# Member Profile ? planned verification

Status: specification only. The implementation and executable verification tooling were removed at the user's request on 2026-10-19. The routes below describe the intended contract, not available functionality.

After implementation, verify the authentication entry, Firebase ID-token exchange for an eight-hour session cookie, current Terms/Privacy acceptance, and GET/POST `/member/profile`. Profile forms must work without JavaScript after authentication. API clients use Bearer ID tokens for GET/PUT `/api/v1/members/me/profile`.

Run the verification scenarios in `spec.md`, `plan.md` and `tasks.md`: strict field validation, credential expiry/revocation, account and policy gates, CSRF, ownership, concurrent first saves, last-commit wins, atomic audit, MySQL fresh/upgrade migrations and Spring Modulith verification. Use Firebase Auth Emulator and an authorized disposable MySQL database; do not use production data or configuration.

Prepare concrete commands and test tooling when implementation is requested. Historical results in `verification.md` do not validate the current repository.
