### 5.2 Manage Third-Party API Credentials

#### Primary Actors

Admin

#### Secondary Actors

Payment Gateway, External Registration System, AI API, Google Maps API, Mail Gateway, Cloud Storage Service

#### Description

Allows the Admin to maintain approved credentials, endpoints, and activation status for the system's third-party integrations, including VNPay, Klook, Gemini, Google Maps/Places, Mland SMTP, and cloud storage.

#### Preconditions

1. The Admin is authenticated and authorized to manage integration credentials.
2. The target integration type is supported by the platform.
3. The Admin has received valid credentials and endpoint information through an approved operational channel.

#### Normal Flow

**Manage Third-Party API Credentials**

1. The Admin opens third-party integration configuration.
2. The system displays supported integrations and masked current configuration.
3. The Admin selects an integration and enters or updates its endpoint, credentials, and active state.
4. The system validates the required fields and integration-specific format.
5. The system performs a safe connection or credential test when supported, without creating a business transaction.
6. The Admin confirms the change.
7. The system encrypts/stores the credentials, records the change, and displays the updated masked status.

#### Alternative Flows

**Step 3 — Unsupported integration or missing required field**

**

**Step 5 — Credential or endpoint test fails**

**

**Step 6 — Admin cancels**

**

**Step 7 — Secure storage fails**

**

#### Postconditions

- Valid credentials and endpoints are stored securely and associated with the selected integration.
- Secrets are masked in the user interface and are not exposed in logs.
- The integration is enabled only after validation or an explicit approved activation decision.
- Previous credentials remain recoverable through audit/rotation history according to retention policy.

#### Business Rules

BR-55-01, BR-55-02, BR-55-03

#### Business Rule Definitions

| ID | Rule Definition |
|---|---|
| BR-55-01 | Only an authorized Admin may create, update, activate, deactivate, or rotate third-party integration credentials. |
| BR-55-02 | Credentials must be stored securely, masked from ordinary display, and excluded from application logs and error messages. |
| BR-55-03 | An integration must not be activated when its required credentials or endpoint validation fails. |
