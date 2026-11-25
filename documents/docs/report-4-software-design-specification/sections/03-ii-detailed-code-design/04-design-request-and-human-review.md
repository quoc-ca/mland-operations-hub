## 4. Design Request and Human Review (FE-04)

**Implementation status:** Human-review rebaseline; current implementation and contract alignment are still subject to verification.

Feature-004 is Member-only for protected actions. Guest may see the entry point and is redirected to sign in. The module accepts a description and/or up to five images, validates image quality and consent, and routes the request to Staff or Manager. AI does not analyze images, price, accept, reject or review the request.

### 4.1 Class Diagram

{{r4-cd-fe04-design-review width=100% align=center}}

The boundary contains the request controller, validation service, image/consent boundary, human-review service, quote handoff facade and immutable audit records. Feature-012 is consumed only through its public snapshot facade.

### 4.2 Sequence Diagram — Submit Member Design Request

{{r4-sd-fe04-submit-request width=100% align=center}}

### 4.3 Sequence Diagram — Staff or Manager Reviews Request

{{r4-sd-fe04-human-review width=100% align=center}}

### 4.4 Sequence Diagram — Clarification, Quote Handoff and Member Payment

{{r4-sd-fe04-clarification-quote width=100% align=center}}

