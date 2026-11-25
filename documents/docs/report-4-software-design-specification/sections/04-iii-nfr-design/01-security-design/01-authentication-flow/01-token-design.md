#### a. Token Design

|Aspect|Design Decision|
|---|---|
|Firebase ID token|The browser sends it as a bearer token. The shared backend gate verifies identity and revocation on protected requests. Provider claims do not grant a Mland business role.|
|Current Account|After token verification, the backend reads the current Account role, status, and email-verification state. A stale token or browser state cannot preserve revoked access.|
|Internal invitation link|Account stores only a secret digest, key version, use state and 48-hour expiry for each invitation version. A resend after five minutes invalidates the previous version. Notification obtains the current URL only when sending; the same version reuses the same credential. Raw URL, secret and Firebase setup token are never persisted in notification data, logs or audit.|
|Workshop Magic Link|A booking-specific bearer link grants read-only access to that booking. It remains valid until the required workshop ends or it is revoked; it does not authenticate a Member.|
|Workshop ticket QR|The QR contains a signed opaque ticket reference, not personal or payment data. The check-in service validates it; the QR alone does not record attendance.|
|Secret handling|Raw tokens, Magic URLs, setup secrets, and QR payloads are excluded from application logs and business audit. Expired or revoked delivery credentials are removed by the next cleanup sweep.|
