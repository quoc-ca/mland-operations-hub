## 4. Design Request and Human Review (FE-04)

**Implementation status:** Human-review rebaseline; current implementation and contract alignment are still subject to verification.

Feature-004 is Member-only for protected actions. Guest may see the entry point and is redirected to sign in. The module accepts a description and/or up to five images, validates image quality and consent, and routes the request to Staff or Manager. AI does not analyze images, price, accept, reject or review the request.

The current ring-design direction is independent from workshop booking. After a Member saves a Feature-012 design, the Member may follow the server recommendation for a suitable workshop package or use the saved design idea as an optional immutable reference for a custom-manufacturing request. The recommendation is advisory; the request path is a separate human-reviewed manufacturing flow; neither path silently creates a booking, payment or order. A custom product can only be purchased after review, quote approval and full payment through the Custom Manufacturing boundary.

### 4.1 Class Diagram

<!-- Temporarily disabled: r4-cd-fe04-design-review. The approved Drive image will be reconnected after the shared asset update. -->

The boundary contains the request controller, validation service, image/consent boundary, human-review service, quote handoff facade and immutable audit records. Feature-012 is consumed only through its public snapshot facade.

### 4.2 Sequence Diagram — Submit Member Design Request

<!-- Temporarily disabled: r4-sd-fe04-submit-request. The approved Drive image will be reconnected after the shared asset update. -->

### 4.3 Sequence Diagram — Staff or Manager Reviews Request

<!-- Temporarily disabled: r4-sd-fe04-human-review. The approved Drive image will be reconnected after the shared asset update. -->

### 4.4 Sequence Diagram — Clarification, Quote Handoff and Member Payment

<!-- Temporarily disabled: r4-sd-fe04-clarification-quote. The approved Drive image will be reconnected after the shared asset update. -->

