# IV. Non-functional Requirements

This section defines quality constraints for Mland's web-based ring workshop, Member retail, and custom-manufacturing workflows. It covers external interfaces, usability, timing and workload limits, security, privacy, and cross-cutting reliability. Requirements use stable `NFR-` identifiers and observable verification criteria; these criteria do not claim completed implementation or testing.

### Source and interpretation

| Source | Contribution |
| --- | --- |
| [Report 1: Project Purpose](../../../report-1-project-introduction/sections/03-i-project-overview/02-project-purpose.md) and [Product Background](../../../report-1-project-introduction/sections/04-ii-product-background/00-overview.md) | Bilingual customer access, guided ring customization, shared operational context and accountable human decisions. |
| [Report 2: Scope and Purpose](../../../report-2-project-management-plan/sections/02-i-project-overview/01-scope-purpose.md), [Assumptions and Constraints](../../../report-2-project-management-plan/sections/02-i-project-overview/02-assumptions-constraints.md) and [Evidences](../../../report-2-project-management-plan/sections/02-i-project-overview/07-evidences.md) | V1 providers, payment boundaries, retention, exclusions and stakeholder decisions of 05/10/2026. |
| Report 3: [Context](../03-i-overall-requirements/01-context-business-flow.md), [Actors](../03-i-overall-requirements/04-user-requirements/01-actors.md), [Permission Matrix](../03-i-overall-requirements/04-user-requirements/04-permission-matrix.md), use cases and [Business Rules](../07-v-requirement-appendix/01-business-rules.md) | Action/ownership/branch restrictions, frozen terms, payment recovery, media limits and workflow-specific acceptance conditions. |

Report 1 contains older scope statements, and its [conflict register](../../../report-1-project-introduction/sections/06-iv-proposed-solution/unresolved-conflicts.md) is not a decision record. For this section, explicit Report 2 decisions and detailed current Report 3 use cases govern providers, roles, loyalty, retail holds, Google review display and privacy. Use `Guest`, `Member`, `Staff`, `Manager` and `Admin`; older `Owner`/`Admin Technical` labels do not independently grant authority. Detailed product/order restrictions refine the broader permission matrix. The privacy notice follows Report 2's general Terms and Privacy Policy without a separate image-consent checkbox.

The tables restate sourced constraints and derive verification criteria from them. A derived criterion explains how to check an existing boundary; it is not an additional stakeholder approval, provider capability claim or production service-level agreement. No carrier API, notification centre, autonomous AI decision, inventory ledger, cancellation, refund, accounting or payment-reconciliation workflow is introduced.

### Targets requiring a separate baseline

The reports do not supply the following values. Close these decisions before the corresponding production acceptance assessment; they do not weaken the concrete payment, access, integrity or retention requirements below.

| ID | Unbaselined item | Evidence required |
| --- | --- | --- |
| NFR-OPEN-01 | Page/API latency, peak concurrency, throughput and representative data volumes. | Approved workload, environment, dataset, measurement points, percentile, duration and pass/fail thresholds. Report 2's team capacity table is not application-load evidence. |
| NFR-OPEN-02 | Availability, service hours, maintenance allowance, backup schedule, recovery time and permissible data loss. | Approved operating/recovery policy, demonstrated restore procedure and privacy-compliant backup retention. |
| NFR-OPEN-03 | Browser versions, device/viewport matrix, accessibility level and training/task-time targets. | Agreed user environments and usability/accessibility scenarios. Bilingual operation itself is required by Report 1. |
| NFR-OPEN-04 | Provider timeouts, retry budgets/backoff, quotas and alert thresholds beyond the retail QueryDR schedule. | Approved configuration and sandbox/UAT evidence; retries must preserve original business deadlines. |
| NFR-OPEN-05 | Password/session values, verification-token lifetime, abuse/rate-limit values and booking lookup protection beyond the reference-code flow. | Approved security policy and identity-provider configuration. UC19 currently specifies code-based lookup; additional contact checks are not assumed. |
| NFR-OPEN-06 | AI image type/size/quality limits and retention of data classes without a stated period below. | Approved image-analysis configuration and data inventory, including account/profile, booking-contact, consultation and backup copies. Product-media limits are not automatically AI limits. Finance/Legal must confirm the five-year evidence period before go-live under Report 2 A-04. |

The existing Spring Boot modular-monolith convention does not establish hosting, hardware sizing, database capacity or production availability. These remain design/operating decisions.
