# I. High Level Design

This part describes the high level design of the system. It is a web system with three tiers: a React single-page front-end, a Spring Boot back-end built as a Spring Modulith modular monolith on Java 21, and a MySQL database whose schema is managed with Flyway. The front-end calls the back-end through a REST API with JSON, and the back-end reaches external services (identity, payment, media storage, email, AI, shipping fee quote and OTA platforms) through integration adapters. The repository provides Docker and Docker Compose configuration to build and run the system.

The following sections present the software architecture, the package diagrams of both applications and the database design.
