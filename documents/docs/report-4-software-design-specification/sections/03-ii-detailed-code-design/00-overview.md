# II. Detailed Code Design

This section is organised by the approved V1 business features. Each feature subsection provides one class-diagram placeholder and three or four main sequence-diagram placeholders. The diagram assets are maintained in the project's Drive design pack and injected by the document generator; the placeholders are the stable report references.

The current implementation status is intentionally mixed: some modules have implemented backend/frontend slices, while other boundaries remain contract-aligned or verification-pending. A diagram placeholder does not by itself mean that a feature is production-complete. Feature-004 is the human-reviewed Member design-request flow; Basic AI text support is documented separately from it. Feature-012 is Member-persisted ring configuration with Guest public preview, and it no longer attaches a design to a workshop booking.

The current self-designed-ring journey is intentionally explicit in the detailed diagrams: after a Member saves and evaluates a design, the system presents an advisory workshop recommendation while keeping other workshop discovery actions visible. The Member may follow the recommended workshop path or use the design idea as a reference for a Feature-004 custom-manufacturing request. The second path continues through human review, quote approval and one full payment before the custom product is purchased. Saving or evaluating a design never creates a booking, ticket, quote, payment or order by itself.

The feature subsections are:

- FE-01 Account Management
- FE-02 Product Management
- FE-03 Workshop Management
- FE-04 Design Request and Human Review
- FE-05 Personalized Design and Custom Manufacturing
- FE-06 Loyalty and Promotion Management
- FE-07 Notification Management
- FE-08 Order Management and Fulfilment
- FE-09 Administrative Parameters
- FE-10 Manager Dashboard
- FE-11 Review Management
- FE-12 Workshop Ring Design Configuration
- FE-13 Staff Consultation Chat

### Diagram readability rules

All detailed diagrams must use a compact page width, larger text and clear orthogonal routing. A relationship must not pass through a node, label or unrelated entity. No more than two relationships may cross the same relationship line; if the limit is exceeded, the diagram must be reorganised or split into at most three diagrams. Cross-feature links should use a labelled facade or external-reference node rather than multiple long crossing arrows.


