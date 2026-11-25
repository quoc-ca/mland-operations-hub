#### a. Token Design

|Aspect|Design Decision|
|---|---|
|Firebase ID token|The browser sends it as a bearer token. The shared backend gate verifies identity and revocation on protected requests. Provider claims do not grant a Mland business role.|
|Current Account|After token verification, the backend reads the current Account role, status, and email-verification state. A stale token or browser state cannot preserve revoked access.|
|Internal invitation link|The account service creates a single-use setup link valid for 48 hours. Resending rotates the link. The notification service retrieves the current URL when sending and does not store the raw secret.|
|Workshop Magic Link|A booking-specific bearer link grants read-only access to that booking. It remains valid until the required workshop ends or it is revoked; it does not authenticate a Member.|
|Workshop ticket QR|The QR contains a signed opaque ticket reference, not personal or payment data. The check-in service validates it; the QR alone does not record attendance.|
|Secret handling|Raw tokens, Magic URLs, setup secrets, and QR payloads are excluded from application logs and business audit. Expired or revoked delivery credentials are removed by the next cleanup sweep.|
