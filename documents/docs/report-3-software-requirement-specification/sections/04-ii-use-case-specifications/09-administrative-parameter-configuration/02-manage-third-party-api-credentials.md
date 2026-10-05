### 5.2 Manage Third-Party API Credentials

| Attribute | Value |
|---|---|
| Primary Actors | Admin<br>**Secondary Actors:** Payment Gateway, External Registration System, AI API, Google Maps API, Mail Gateway, Cloud Storage Service |
| Description | Allows the Admin to maintain approved credentials, endpoints, and activation status for the system's third-party integrations, including VNPay, Klook, Gemini, Google Maps/Places, Mland SMTP, and cloud storage. |
| Preconditions | 1. The Admin is authenticated and authorized to manage integration credentials.<br>2. The target integration type is supported by the platform.<br>3. The Admin has received valid credentials and endpoint information through an approved operational channel. |
| Postconditions | - Valid credentials and endpoints are stored securely and associated with the selected integration.<br>- Secrets are masked in the user interface and are not exposed in logs.<br>- The integration is enabled only after validation or an explicit approved activation decision.<br>- Previous credentials remain recoverable through audit/rotation history according to retention policy. |
| Normal Sequence/Flow | Manage Third-Party API Credentials<br>1. The Admin opens third-party integration configuration.<br>2. The system displays supported integrations and masked current configuration.<br>3. The Admin selects an integration and enters or updates its endpoint, credentials, and active state.<br>4. The system validates the required fields and integration-specific format.<br>5. The system performs a safe connection or credential test when supported, without creating a business transaction.<br>6. The Admin confirms the change.<br>7. The system encrypts/stores the credentials, records the change, and displays the updated masked status. |
| Alternative Sequences/Flows | **Step 3 — Unsupported integration or missing required field**<br>The system rejects the configuration and identifies the missing/unsupported data.<br><br>**Step 5 — Credential or endpoint test fails**<br>The system reports the failure and does not activate the new configuration.<br><br>**Step 6 — Admin cancels**<br>No credential change is saved.<br><br>**Step 7 — Secure storage fails**<br>The system does not activate the credential and displays an operational error.<br><br>**Credential rotation**<br>The system replaces the active secret only after the new configuration is valid and retains an audit record of the rotation. |
| Business Rule | BR-55-01, BR-55-02, BR-55-03 |

| ID | Rule Definition |
|---|---|
| BR-55-01 | Only an authorized Admin may create, update, activate, deactivate, or rotate third-party integration credentials. |
| BR-55-02 | Credentials must be stored securely, masked from ordinary display, and excluded from application logs and error messages. |
| BR-55-03 | An integration must not be activated when its required credentials or endpoint validation fails. |
