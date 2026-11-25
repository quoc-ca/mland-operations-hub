# I. High Level Design

This section records the current high-level design baseline inferred from the approved feature specifications and the implemented repository structure. The product uses a React/Vite frontend and a Java 21/Spring Boot backend organised as a Spring Modulith modular monolith. Feature modules expose approved facades and do not access another module's private repositories or entities.

Deployment topology, production hosting, operational SLA, some physical schema details and external-provider go-live configuration remain `TBD` where the feature specifications do not approve them. Those items must not be inferred from the diagrams in this report. The detailed design below distinguishes implemented slices from proposed or verification-pending slices and uses Drive asset placeholders for diagrams.
