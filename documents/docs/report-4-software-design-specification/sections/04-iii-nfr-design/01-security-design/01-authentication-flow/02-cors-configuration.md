#### b. CORS Configuration

|Environment|Allowed Origins|Allowed Methods|Allow Credentials|Max Age|
|---|---|---|---|---|
|Local development|`http://localhost:5173` only|Methods used by the application, plus preflight `OPTIONS`|No|To be configured|
|Staging and production|Exact origins to be set for each environment|Methods used by the application, plus preflight `OPTIONS`|No|To be configured|

Preflight permits only the headers needed by the application, including `Authorization`, `Content-Type`, `Idempotency-Key`, and `X-Correlation-ID` where used. CORS does not grant API access. The bearer-token flow uses no browser authentication cookie; a later cookie flow would need a separate CSRF design.
