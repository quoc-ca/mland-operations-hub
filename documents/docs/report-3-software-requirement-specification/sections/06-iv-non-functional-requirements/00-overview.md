# IV. Non-functional Requirements

Security controls, performance targets, availability, accessibility acceptance criteria, data-retention periods, logging implementation, and deployment constraints are `TBD`. The current baseline only establishes that sensitive payment confirmation must be verified, consented reference images must not be used outside their approved purpose, and all business overrides must be auditable.

The approved V1 implementation convention is a Spring Modulith modular monolith. Business modules own their web, application, domain, infrastructure, and audit concerns; another module may use only an explicitly exposed facade. This convention does not resolve the outstanding security, payment-provider, retention, logging, or deployment `TBD` items. See [Architecture convention](../07-appendices/01-architecture-convention.md).
