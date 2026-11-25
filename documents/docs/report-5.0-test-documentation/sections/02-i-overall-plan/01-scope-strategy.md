## 1. Scope of Testing

The scope of testing covers the functional and non-functional requirements of PPSWBS. Testing checks that each module works correctly in isolation and when integrated with the rest of the system.

**Key areas of testing**

- **Web application modules.** All user-facing and back-office features of the web application, tested on a local deployment (Spring Boot backend, React/Vite frontend, MySQL database) in a desktop browser:
  - 001 Account Management — sign-in, profile, address book, internal and carrier account administration.
  - 002 Product Management — product creation, review, pricing, publication, Excel import and public catalogue.
  - 003 Workshop Management — 003.1 booking and VNPay deposit, 003.2 session operations and check-in, 003.3 settlement, refunds and ticket changes, 003.4 OTA connectivity.
  - 004 AI Chatbox Analysis and Suggestion — AI support, custom design request, human review and price proposal.
  - 006 Loyalty and Promotion — promotion programmes, vouchers, loyalty points and checkout benefits.
  - 007 Notification Management — notification templates, delivery worker and operations.
  - 008 Retail Order and Fulfillment — cart, checkout, VNPay payment, GHTK shipment and store pickup.
  - 010 Manager Dashboard — executive revenue reporting.
  - 011 Review Management — customer reviews, moderation and manager replies.
  - 012 Workshop Ring Design Configuration — ring configurator, design save and evaluation.
  - 013 Staff Consultation Chat — customer–staff consultation conversations.
- **Integrations with external services.** VNPay payment gateway (sandbox or payment simulator), GHTK shipping, Firebase Authentication and the OTA provider interface.
- **Non-functional testing.** Security (access control, data isolation, privacy of public data, idempotency) and performance or usability (page load, preview rendering, pagination, responsive layout).

014 Navigation Shell is not listed as a separate test module. Its only recorded evidence is frontend component tests; it has no integration or end-to-end test records.

## 2. Test Strategy

Testing follows the iterative development process of the project. Each feature is tested as it is built: unit and integration tests run with the backend build, and end-to-end journeys are executed in the browser after the feature is deployed locally. Defects found during execution are recorded with evidence and handed back to the owning feature.

### 2.1 Testing Types

| No | Test type | Purpose | Scope | Technique |
| --- | --- | --- | --- | --- |
| 1 | Unit Testing | Validate that individual service functions behave correctly in isolation. | Backend service methods of each feature: validation rules, state transitions, pricing and discount calculation, authorization checks. | White-box testing with JUnit 5; Mockito mocks for repositories and external ports; AssertJ assertions on return values and exceptions; normal, abnormal and boundary inputs. |
| 2 | Integration Testing | Ensure correct communication and data flow between components. | REST controllers with the security layer, services with the MySQL database, adapters for VNPay, GHTK, OTA and mail. | API testing with Spring MockMvc (status codes, payloads, error contracts); Spring Boot integration tests on MySQL; contract tests for adapters; frontend API-client tests with Vitest. |
| 3 | System Testing — Functional | Verify that complete business workflows meet the functional requirements. | End-to-end journeys such as workshop booking with VNPay deposit, retail checkout with GHTK handover, custom design request to full payment, review moderation. | End-to-end testing in a real Chrome browser against the local deployment, following the master E2E journeys; screenshots and observed results recorded as evidence. |
| 4 | System Testing — Non-functional | Verify security, performance and usability requirements. | Access control and data isolation per role, privacy of public data, idempotency, page load, preview rendering, responsive layout. | Role-based negative testing through UI and API; browser DevTools timing and device emulation; concurrent requests with k6 or manual clients. |
| 5 | Acceptance Testing | Confirm that the system supports the business processes of Mland. | Business scenarios per feature and one cross-feature scenario, plus exploratory sessions. | Business-language scripts executed by QA or business users; exploratory testing sessions. |
