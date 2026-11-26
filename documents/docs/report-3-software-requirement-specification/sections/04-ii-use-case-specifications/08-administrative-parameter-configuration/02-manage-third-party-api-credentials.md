### 8.2 Manage Third-Party API Credentials

| Use Case ID | UC-63 | Use Case Name | Manage Third-Party API Credentials |
| --- | --- | --- | --- |
| Created By |  | Created Date |  |
| Primary Actors | Admin | Secondary Actors | Payment Gateway; AI API; Mail Gateway; Cloud Storage Service |

#### Description

As an Admin, I want to maintain approved credentials, endpoints, and activation status for third-party integrations so that VNPay, Gemini, Mland SMTP, and cloud storage operate securely.

#### Trigger

An Admin opens third-party integration configuration.

#### Preconditions

1. The Admin is authenticated and authorized to manage integration credentials.
2. The target integration type is supported by the platform.
3. The Admin has received valid credentials and endpoint information through an approved operational channel.

#### Postconditions

1. Valid credentials and endpoints are stored securely for the selected integration.
2. Secrets are masked and excluded from logs.
3. The integration is enabled only after validation succeeds.

#### Normal Flow

1. Admin opens third-party integration configuration.
2. System displays supported integrations and masked current configuration.
3. Admin selects an integration and enters its endpoint, credentials, and active state.
4. System validates required fields and integration-specific format.
5. System performs a safe connection/credential test without creating a business transaction.
6. Admin confirms the change.
7. System encrypts and stores credentials, records the change, and displays updated masked status.

#### Alternative Flows

6a. Admin cancels → System discards the pending changes and preserves the existing configuration.

#### Exception Flows

3a. Unsupported integration or missing required field → System rejects the input with validation guidance and does not save.

5a. Credential or endpoint test fails → System blocks activation and displays the test failure without exposing secrets.

7a. Secure storage fails → System displays an error without logging secrets and preserves the previous configuration.

#### Business Rules

BR-63-01, BR-63-02, BR-63-03

#### Non-Functional Requirements

—

#### Priority

—

#### Messages

—

#### Business Rule Definitions

| ID | Rule Definition |
| --- | --- |
| BR-63-01 | Only an authorized Admin may create, update, activate, deactivate, or rotate third-party integration credentials. |
| BR-63-02 | Credentials must be stored securely, masked from ordinary display, and excluded from application logs and error messages. |
| BR-63-03 | An integration must not be activated when its required credentials or endpoint validation fails. |
